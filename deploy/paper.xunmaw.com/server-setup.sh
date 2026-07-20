#!/bin/bash
# 鍦ㄦ湇鍔″櫒 159.75.166.190 涓婁互 root 鎵ц锛堥娆￠儴缃诧級
set -euo pipefail

DEPLOY_DIR=/opt/paper
NGINX_CONF=/etc/nginx/conf.d/paper.xunmaw.com.conf
SSL_DIR=/etc/nginx/ssl

echo "==> 瀹夎渚濊禆..."
if command -v yum &>/dev/null; then
  yum install -y docker nginx curl || true
  systemctl enable docker nginx
  systemctl start docker nginx
elif command -v apt-get &>/dev/null; then
  apt-get update
  apt-get install -y docker.io docker-compose-plugin nginx curl || true
  systemctl enable docker nginx
  systemctl start docker nginx
fi

if ! docker compose version &>/dev/null; then
  echo "璇峰畨瑁?Docker Compose v2: https://docs.docker.com/compose/install/"
  exit 1
fi

echo "==> 鍒涘缓鐩綍..."
mkdir -p "$DEPLOY_DIR"/{web/dist,init-sql,backend}
mkdir -p "$SSL_DIR"

echo "==> 瀹夎 SSL 璇佷功锛堥渶宸蹭笂浼?pem/key 鍒?$DEPLOY_DIR/ssl/锛?.."
if [ -f "$DEPLOY_DIR/ssl/paper.xunmaw.com.pem" ]; then
  cp "$DEPLOY_DIR/ssl/paper.xunmaw.com.pem" "$SSL_DIR/"
  cp "$DEPLOY_DIR/ssl/paper.xunmaw.com.key" "$SSL_DIR/"
  chmod 600 "$SSL_DIR/paper.xunmaw.com.key"
else
  echo "璀﹀憡: 鏈壘鍒拌瘉涔︼紝璇蜂笂浼犲悗閲嶆柊杩愯鎴栨墜鍔ㄥ鍒跺埌 $SSL_DIR"
fi

echo "==> 鍒濆鍖?SQL锛堜粎棣栨锛孧ySQL 绌哄簱鏃剁敓鏁堬級..."
if [ -f "$DEPLOY_DIR/init-sql/01-schema.sql" ]; then
  echo "SQL 宸插氨缁?
fi

echo "==> 閰嶇疆 Nginx..."
cp "$DEPLOY_DIR/nginx.conf" "$NGINX_CONF"
nginx -t && systemctl reload nginx

echo "==> 鍚姩 Docker 鏈嶅姟..."
cd "$DEPLOY_DIR"
docker compose up -d

echo "==> 閮ㄧ讲瀹屾垚"
echo "  绔欑偣: https://paper.xunmaw.com"
echo "  鍚庣: http://127.0.0.1:6039"
echo "  鏃ュ織: docker logs -f paper-backend"
