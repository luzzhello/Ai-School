# 按需文献爬取（大纲并行）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用户点「生成大纲」时并行生成 TOC，并按题目拆 3～5 词块调用 Python 知网爬虫按需入库约 40～80 条，前端双状态展示后再勾选文献。

**Architecture:** Java `PaperTocService` 仍同步生成大纲；新增 `LitOnDemandService` 异步起任务：拆词 → 子进程调用 `tools/cnki-crawler crawl-task`（默认无 Cookie）→ JSONL 导入 `lit_paper`。前端将流程改为「先大纲+文献获取，再打开文献弹窗检索勾选」，进度用短轮询 `GET /api/paper/lit-ondemand/{taskId}`。

**Tech Stack:** Vue 3 + Element Plus（AiSchoolWeb）、Spring Boot / ruoyi-chat（Ai-School）、Python cnki-crawler、MySQL `lit_paper`

**Spec:** `docs/superpowers/specs/2026-07-27-on-demand-lit-crawl-design.md`

**Note:** 本仓库约定不自动 `git commit`；各 Task 末「Commit」步骤仅在用户明确要求提交时执行。不使用 git worktree。

---

## File map

| 文件 | 职责 |
|------|------|
| `tools/cnki-crawler/src/config_loader.py` | `allow_empty_cookie` 支持无 Cookie |
| `tools/cnki-crawler/src/__main__.py` | 新增 `crawl-task` 子命令 |
| `tools/cnki-crawler/src/task_crawl.py` | 单次任务：关键词列表 + 每词上限 + 独立 JSONL |
| `tools/cnki-crawler/tests/test_task_crawl.py` | CLI/任务单测 |
| `ruoyi-chat/.../paper/TitleKeywordSplitter.java` | 题目拆词 3～5 |
| `ruoyi-chat/.../paper/LitOnDemandProperties.java` | `paper.lit.ondemand.*` |
| `ruoyi-chat/.../paper/LitOnDemandTask.java` | 内存任务状态 |
| `ruoyi-chat/.../paper/CnkiCrawlerProcessClient.java` | 调 Python 子进程 |
| `ruoyi-chat/.../paper/LitOnDemandService.java` | 编排拆词/爬取/导入/状态 |
| `ruoyi-chat/.../chat/PaperController.java` | TOC 触发 lit 任务 + 状态查询 |
| `ruoyi-chat/.../domain/vo/paper/LitOnDemandStatusVo.java` | 进度 VO |
| `ruoyi-admin` 或 chat 模块 `application*.yml` | 配置项 |
| `AiSchoolWeb/src/api/paper/index.ts` | API |
| `AiSchoolWeb/.../PaperGenerator.vue` | B1 流程 + 双状态 UI |
| `docs/superpowers/specs/2026-07-27-on-demand-lit-crawl-design.md` | 已定稿规格 |

---

### Task 1: Python — 允许无 Cookie + `crawl-task`

**Files:**
- Modify: `tools/cnki-crawler/src/config_loader.py`
- Modify: `tools/cnki-crawler/src/__main__.py`
- Create: `tools/cnki-crawler/src/task_crawl.py`
- Create: `tools/cnki-crawler/tests/test_task_crawl.py`
- Modify: `tools/cnki-crawler/config.example.yaml`（注明 cookie 可空）

- [ ] **Step 1: 写失败测试（无 Cookie 加载）**

```python
# tests/test_task_crawl.py
from pathlib import Path
from src.config_loader import load_config

def test_load_config_allows_empty_cookie_when_flag(tmp_path: Path):
    p = tmp_path / "c.yaml"
    p.write_text("cookie: ''\nallow_empty_cookie: true\n", encoding="utf-8")
    cfg = load_config(p)
    assert cfg["cookie"] == ""
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd tools/cnki-crawler && python -m pytest tests/test_task_crawl.py::test_load_config_allows_empty_cookie_when_flag -v`  
Expected: FAIL（尚不支持 `allow_empty_cookie`）

- [ ] **Step 3: 改 `load_config`**

```python
cookie = str(data.get("cookie") or "").strip()
data["cookie"] = cookie
allow_empty = bool(data.get("allow_empty_cookie"))
if (not cookie or cookie.startswith("REPLACE_WITH")) and not allow_empty:
    raise ConfigError("config.cookie must be set ...")
```

- [ ] **Step 4: 实现 `task_crawl.run_task`**

```python
# src/task_crawl.py
def run_task(
    client, checkpoint, keywords: list[str], *,
    max_per_keyword: int, output_jsonl: str,
    from_year, to_year, list_only: bool, search_lang: str,
) -> int:
    total = 0
    for kw in keywords:
        n = run_crawl(
            client, checkpoint, [kw],
            max_per_keyword=max_per_keyword,
            from_year=from_year, to_year=to_year,
            output_jsonl=output_jsonl,
            max_total=max_per_keyword,
            list_only=list_only,
            search_lang=search_lang,
        )
        total += n
    return total
```

（参数与现有 `run_crawl` 签名对齐；缺的用默认。）

- [ ] **Step 5: `__main__.py` 增加子命令**

```text
crawl-task
  --keywords "A,B,C"          # 必填，逗号分隔
  --max-per-keyword 20
  --output data/task_xxx.jsonl
  --checkpoint data/task_xxx_cp.json
  --list-only
  --search-lang chinese
```

`crawl-task` 路径下：`allow_empty_cookie` 默认视为 true（或 CLI `--allow-empty-cookie` 强制）。Cookie 空时 `CnkiHttpClient(cookie="")`。

- [ ] **Step 6: 单测 `parse_keywords` + 跑通 pytest**

```python
def test_parse_keywords_csv():
    from src.task_crawl import parse_keywords
    assert parse_keywords("SpringBoot, 学生选课 ,") == ["SpringBoot", "学生选课"]
```

Run: `python -m pytest tests/test_task_crawl.py -q`  
Expected: PASS

- [ ] **Step 7: 手工冒烟（可选）**

```powershell
cd tools/cnki-crawler
python -m src --config config.yaml crawl-task --keywords SpringBoot --max-per-keyword 2 --list-only --output data/task_smoke.jsonl --checkpoint data/task_smoke_cp.json
```

Expected: 生成 JSONL，无 Cookie 若被风控则记录现象到规格「开放问题」，不阻塞后续 Java 编排（可先 list-only）。

- [ ] **Step 8: Commit（仅用户要求时）**

```bash
git add tools/cnki-crawler
git commit -m "feat(cnki-crawler): add crawl-task CLI with optional empty cookie"
```

---

### Task 2: Java — 题目拆词器

**Files:**
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/TitleKeywordSplitter.java`
- Create: `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/paper/TitleKeywordSplitterTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void splitsSpringBootThesisTitle() {
    List<String> kws = TitleKeywordSplitter.split(
        "基于SpringBoot的学生选课系统设计与实现", 3, 5);
    assertTrue(kws.size() >= 3 && kws.size() <= 5);
    assertTrue(kws.stream().anyMatch(k -> k.equalsIgnoreCase("SpringBoot")));
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -pl ruoyi-modules/ruoyi-chat -Dtest=TitleKeywordSplitterTest test`  
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 `TitleKeywordSplitter`**

逻辑：
1. trim，去《》等；
2. 抽出连续 `[A-Za-z][A-Za-z0-9.+#-]*` 英文技术词；
3. 去掉停用词集合：`基于|面向|的|与|及|和|研究|设计|实现|系统|平台|分析|应用|开发|一种|基于`；
4. 剩余中文按标点/`的` 切段，保留长度 2～8 的片段；
5. 去重保序，截断到 `maxKeywords`；若 `< minKeywords` 用整题（截断 20 字）补足。

```java
public final class TitleKeywordSplitter {
  private TitleKeywordSplitter() {}
  public static List<String> split(String title, int minKeywords, int maxKeywords) { ... }
}
```

- [ ] **Step 4: 跑测试 PASS**

- [ ] **Step 5: Commit（仅用户要求时）**

---

### Task 3: Java — 配置与任务状态模型

**Files:**
- Create: `.../config/LitOnDemandProperties.java`（或扩写 `LitPaperProperties` 内嵌 `ondemand`）
- Create: `.../service/paper/LitOnDemandTask.java`
- Create: `.../domain/vo/paper/LitOnDemandStatusVo.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml`（或现网对应 yml）增加配置

推荐扩写：

```java
@ConfigurationProperties(prefix = "paper.lit")
public class LitPaperProperties {
    private int recentYears = 5;
    private OnDemand ondemand = new OnDemand();
    @Data
    public static class OnDemand {
        private boolean enabled = true;
        private int minKeywords = 3;
        private int maxKeywords = 5;
        private int maxPerKeyword = 20;
        private int taskTimeoutSec = 300;
        private boolean listOnly = true; // 首期默认 list-only 更稳
        private String pythonExecutable = "python";
        private String crawlerWorkDir = "tools/cnki-crawler"; // 相对仓库根或绝对路径
        private String configPath = "config.yaml";
    }
}
```

`LitOnDemandTask` 字段：`taskId, sessionId, title, outlineStatus, litStatus, keywords, fetchedCount, error, createdAt, updatedAt`。  
状态枚举字符串：`pending|running|done|partial|failed`。

- [ ] **Step 1: 添加类与 yml 默认值**
- [ ] **Step 2: 编译模块** `mvn -pl ruoyi-modules/ruoyi-chat -DskipTests compile`
- [ ] **Step 3: Commit（仅用户要求时）**

---

### Task 4: Java — 子进程客户端

**Files:**
- Create: `.../service/paper/CnkiCrawlerProcessClient.java`
- Create: `.../service/paper/CnkiCrawlerProcessClientTest.java`（可用临时假脚本）

- [ ] **Step 1: 接口约定**

```java
public record CrawlTaskResult(int exitCode, Path jsonlPath, String logTail) {}

public CrawlTaskResult runCrawlTask(
    List<String> keywords, int maxPerKeyword, boolean listOnly,
    Path outputJsonl, Path checkpoint, Duration timeout) throws Exception
```

命令示例：

```text
{python} -m src --config {config} crawl-task
  --keywords k1,k2,k3
  --max-per-keyword 20
  --output {jsonl}
  --checkpoint {cp}
  [--list-only]
```

`ProcessBuilder.directory(crawlerWorkDir)`，合并 stderr，超时 `destroyForcibly`。

- [ ] **Step 2: 单测用假 Python 脚本写一行 JSONL 并 exit 0**
- [ ] **Step 3: Commit（仅用户要求时）**

---

### Task 5: Java — `LitOnDemandService` + 导入

**Files:**
- Create: `.../service/paper/LitOnDemandService.java`
- Reuse: 现有 `LitPaperEntity` / Mapper 插入或调用小型 import 组件

入库策略（首期）：
- 子进程产出 JSONL 后，Java 逐行解析（字段对齐 `import_to_mysql.py`）写入 `lit_paper`，去重：先按 `cnki_id` / `doi` / `title_hash+year` 查是否存在则 skip。
- 或：子进程结束后再调 `python scripts/import_to_mysql.py ... --jsonl ...`（更快复用，需 DB 账号配置进 ondemand）。

**推荐首期：再调 Python import 脚本**（与运维脚本一致），配置 `paper.lit.ondemand.import-enabled` + DB 连接沿用应用数据源时则改 Java 插入。

若选 Java 插入，最小字段：`title, authors, source, year, cnki_id, detail_url, title_hash, crawl_keyword, lit_source=CNKI, status=active, crawled_at`。

- [ ] **Step 1: `startTask(sessionId, title)` → 分配 UUID，线程池/`@Async` 执行爬取+导入，立即返回 taskId**
- [ ] **Step 2: `getStatus(taskId)` → `LitOnDemandStatusVo`**
- [ ] **Step 3: 超时将 `running` → `partial|failed`**
- [ ] **Step 4: 单元测试 mock `CnkiCrawlerProcessClient`**
- [ ] **Step 5: Commit（仅用户要求时）**

内存 `ConcurrentHashMap<String, LitOnDemandTask>` 即可（单机）；重启任务丢失可接受。

---

### Task 6: API — 大纲触发 + 状态查询

**Files:**
- Modify: `.../controller/chat/PaperController.java`
- Modify: `PaperTocRequest` 可选 `startLitOnDemand`（默认 true）
- Create/Modify VO 包装：若不想改 TOC 返回类型，可：
  - `POST /api/paper/toc` 仍返回 `List<TocNode>`
  - 另增 `POST /api/paper/lit-ondemand/start` `{sessionId}` 与 `GET /api/paper/lit-ondemand/{taskId}`
  - 前端并行调用 TOC + start

**采用双 API（改动更小）：**

```text
POST /api/paper/lit-ondemand/start  Body: { sessionId }
  → R<{ taskId }>

GET  /api/paper/lit-ondemand/{taskId}
  → R<LitOnDemandStatusVo>
```

前端 `generateTocFlow` 内：`Promise.all([generatePaperToc(...), startLitOnDemand(sessionId)])`，再轮询 status。

- [ ] **Step 1: Controller + Service 接线**
- [ ] **Step 2: 用 curl/httpie 本地验证 start 返回 taskId**
- [ ] **Step 3: Commit（仅用户要求时）**

---

### Task 7: 前端 API + 双状态 UI + 流程 B1

**Files:**
- Modify: `AiSchoolWeb/src/api/paper/index.ts`
- Modify: `AiSchoolWeb/src/pages/paper-generator/PaperGenerator.vue`
- 可选小组件: `AiSchoolWeb/src/components/paper/OutlineLitProgressCards.vue`

- [ ] **Step 1: API**

```ts
export function startLitOnDemand(sessionId: string) {
  return http.post<{ taskId: string }>('/api/paper/lit-ondemand/start', { sessionId });
}
export function getLitOnDemandStatus(taskId: string) {
  return http.get<LitOnDemandStatusVo>(`/api/paper/lit-ondemand/${taskId}`);
}
```

- [ ] **Step 2: 改 `generateOutline` / `handlePrimaryAction` 路径**

当前：`continueAfterReferences` 要求先有文献再 TOC。  
改为：
1. 校验题目 + 截图前置（保留 `ensureScreenshotPrerequisites`）；
2. **不再要求** `refList.length > 0` 才能生成大纲；
3. 调用 `generateTocFlow`：并行 TOC + `startLitOnDemand`；
4. 展示双卡片：`outlineStatus` / `litStatus`；
5. lit `done|partial` 后允许点「参考文献」打开 `AiReferenceDialog`；
6. 进入正文撰写前仍要求已 `confirmPaperReferences`（可在写正文按钮处校验）。

伪代码：

```ts
async function generateOutline() {
  if (!(await ensureScreenshotPrerequisites())) return;
  outlineCardStatus.value = 'running';
  litCardStatus.value = 'running';
  const [, startRes] = await Promise.all([
    generateTocFlow(tocMode.value === 'template'),
    startLitOnDemand(sessionId.value),
  ]);
  outlineCardStatus.value = 'done';
  await pollLitTask(startRes.taskId); // 2s 间隔直到终态
}
```

- [ ] **Step 3: UI 双卡片**（文案对齐截图：「第2步-千字大纲」「第3步-参考文献」）
- [ ] **Step 4: 文案修正**：去掉「请先检索并确认文献再生成大纲」类提示；改为「大纲生成后可获取/选择文献」
- [ ] **Step 5: 本地 `pnpm dev` 手测主路径**
- [ ] **Step 6: Commit（仅用户要求时）**

---

### Task 8: 联调与回归

- [ ] **Step 1:** 无 Cookie `crawl-task` + 导入 → DB 有新行  
- [ ] **Step 2:** 前端点生成大纲 → 双状态 → 文献弹窗能搜到新题相关条目  
- [ ] **Step 3:** 故意弄错 `crawlerWorkDir` → lit failed，大纲仍成功  
- [ ] **Step 4:** 同一题目点两次生成 → 去重无爆炸增长  
- [ ] **Step 5:** 更新规格开放问题结论（list-only 默认是否保留）

---

## Spec coverage check

| 规格项 | Task |
|--------|------|
| B1 流程 / 双状态 | 7 |
| 3～5 词 ×20 | 2, 3, 5 |
| 无 Cookie 默认 | 1, 3 |
| 失败不挡大纲 | 5, 6, 7 |
| 复用 Python 爬虫 | 1, 4 |
| 入库去重 | 5 |
| 非广爬主路径 | 全计划不改 se.txt 广爬默认 |

## 执行方式

Plan complete and saved to `docs/superpowers/plans/2026-07-27-on-demand-lit-crawl.md`.

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每 Task 新开子代理，Task 间复查  
2. **Inline Execution** — 本会话按 Task 连续实现并设检查点  

Which approach?
