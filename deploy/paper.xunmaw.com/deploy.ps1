# paper.xunmaw.com 一键部署
# 用法:
#   $env:DEPLOY_SSH_PASS='密码'; .\deploy.ps1
#   .\deploy.ps1 -Password '密码'
#   .\deploy.ps1 -Target frontend -Password '密码'
#   .\deploy.ps1 -Target backend  -SkipBuild -Password '密码'
param(
    [ValidateSet('all', 'frontend', 'backend', 'admin')]
    [string]$Target = 'all',
    [string]$Password = '',
    [string]$Host_ = '159.75.166.190',
    [string]$User = 'root',
    [switch]$SkipBuild,
    [switch]$ApplySql,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AiSchool = Resolve-Path (Join-Path $Root '..\..')
$AiSchoolWeb = Resolve-Path (Join-Path $AiSchool '..\AiSchoolWeb')
$AiSchoolAdminWeb = Resolve-Path (Join-Path $AiSchool '..\AiSchoolAdminWeb')
$AdminDist = Join-Path $AiSchoolAdminWeb 'apps\web-antd\dist'
$RemotePy = Join-Path $Root 'remote.py'

function Ensure-Password {
    if ($Password) {
        $env:DEPLOY_SSH_PASS = $Password
    }
    if (-not $env:DEPLOY_SSH_PASS) {
        $secure = Read-Host 'SSH Password (root@159.75.166.190)' -AsSecureString
        $env:DEPLOY_SSH_PASS = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        )
    }
    $env:DEPLOY_SSH_HOST = $Host_
    $env:DEPLOY_SSH_USER = $User
    $env:PYTHONIOENCODING = 'utf-8'
}

function Invoke-Remote {
    param([string]$Cmd)
    python $RemotePy run $Cmd
    if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Cmd" }
}

function Invoke-RemotePut {
    param([string]$Local, [string]$Remote)
    python $RemotePy put $Local $Remote
    if ($LASTEXITCODE -ne 0) { throw "Upload failed: $Local" }
}

function Invoke-RemoteUpload {
    param([string]$Local, [string]$Remote)
    python $RemotePy upload $Local $Remote
    if ($LASTEXITCODE -ne 0) { throw "Upload failed: $Local" }
}

function Build-UpdatesSql {
    $out = Join-Path $Root '02-updates.sql'
    Remove-Item $out -ErrorAction SilentlyContinue
    $updateDir = Join-Path $AiSchool 'docs/script/sql/update'
    if (-not (Test-Path $updateDir)) { return $null }
    Get-ChildItem $updateDir -Filter '*.sql' | Sort-Object Name | ForEach-Object {
        Add-Content $out "`n-- $($_.Name)`n"
        Get-Content $_.FullName | Add-Content $out
    }
    return $out
}

Ensure-Password

Write-Host "==> paper.xunmaw.com deploy [$Target]" -ForegroundColor Cyan

if (-not $SkipBuild) {
    if ($Target -in @('all', 'frontend')) {
        Write-Host '==> Build frontend (vite)...'
        Push-Location $AiSchoolWeb
        if (-not (Test-Path 'node_modules')) { pnpm install --frozen-lockfile }
        pnpm exec vite build
        Pop-Location
    }

    if ($Target -in @('all', 'admin')) {
        Write-Host '==> Build admin (AiSchoolAdminWeb)...'
        Push-Location $AiSchoolAdminWeb
        if (-not (Test-Path 'node_modules')) { pnpm install --frozen-lockfile }
        pnpm run build:antd
        Pop-Location
    }

    if ($Target -in @('all', 'backend')) {
        Write-Host '==> Build backend (maven prod)...'
        Push-Location $AiSchool
        mvn clean package -Pprod -DskipTests -pl ruoyi-admin -am -q
        Pop-Location
    }
}

if ($SkipUpload) {
    Write-Host '==> SkipUpload, done.' -ForegroundColor Green
    exit 0
}

Write-Host '==> Upload to server...'
Invoke-Remote 'mkdir -p /opt/paper/web/dist /opt/paper/admin/dist /opt/paper/backend /opt/paper/init-sql'

if ($Target -in @('all', 'admin')) {
    Write-Host '    admin dist...'
    if (-not (Test-Path $AdminDist)) { throw "Admin dist not found: $AdminDist (run build first)" }
    Invoke-RemoteUpload $AdminDist '/opt/paper/admin/dist'
    Invoke-RemotePut (Join-Path $Root 'admin.paper.xunmaw.com.conf') '/etc/nginx/config/admin_paper_xunmaw.conf'
    Invoke-Remote 'nginx -t && nginx -s reload'
}

if ($Target -in @('all', 'frontend')) {
    Write-Host '    frontend dist...'
    Invoke-RemoteUpload (Join-Path $AiSchoolWeb 'dist') '/opt/paper/web/dist'
}

if ($Target -in @('all', 'backend')) {
    Write-Host '    backend jar...'
    $jar = Join-Path $AiSchool 'ruoyi-admin\target\ruoyi-admin.jar'
    if (-not (Test-Path $jar)) { throw "JAR not found: $jar (run build first)" }
    Invoke-RemotePut $jar '/opt/paper/backend/ruoyi-admin.jar'

    Write-Host '    startup scripts...'
    Invoke-RemotePut (Join-Path $Root 'start-backend.sh') '/opt/paper/start-backend.sh'
    Invoke-RemotePut (Join-Path $Root 'paper-backend.service') '/etc/systemd/system/paper-backend.service'
    Invoke-Remote 'chmod +x /opt/paper/start-backend.sh && systemctl daemon-reload'
}

if ($ApplySql) {
    Write-Host '==> Apply SQL updates to ai_sc...'
    $sql = Build-UpdatesSql
    if ($sql) {
        Invoke-RemotePut $sql '/opt/paper/init-sql/02-updates.sql'
        Invoke-Remote "mysql -S /tmp/mysql.sock -uroot -p123QWER. ai_sc < /opt/paper/init-sql/02-updates.sql || true"
    }
}

if ($Target -in @('all', 'backend')) {
    Write-Host '==> Restart backend...'
    Invoke-Remote 'systemctl restart paper-backend && sleep 12 && systemctl is-active paper-backend'
}

Write-Host '==> Verify...'
Invoke-Remote @'
curl -sf http://127.0.0.1:6039/actuator/health >/dev/null && echo "backend: ok" || echo "backend: starting..."
curl -sf --resolve paper.xunmaw.com:443:127.0.0.1 https://paper.xunmaw.com/prod-api/auth/tenant/list | head -c 120 || true
curl -sfI --resolve admin.paper.xunmaw.com:443:127.0.0.1 https://admin.paper.xunmaw.com/ | head -3 || true
echo
'@

Write-Host '==> Done:' -ForegroundColor Green
Write-Host '    User:  https://paper.xunmaw.com'
Write-Host '    Admin: https://admin.paper.xunmaw.com'
