# 系统实现截图驱动大纲 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 大纲前分区上传系统截图 → AI 识别功能名（可编辑确认）→ 第五章目录完全由截图生成 → 正文自动插入对应截图。

**Architecture:** 会话持久化 `uiScreenshots` JSON；`PaperTocCustomizer.rebuildChapter5Modules` 改为只读截图清单；新增 analyze/save API（视觉模型复用 `chat.diagram.vision-model`）；前端双区上传 + 识别预览；章节生成后用 `screenshotAssetUrl` 替换 UI 截图占位。

**Tech Stack:** Java 17 / Spring Boot / MyBatis-Plus / LangChain4j（多模态）；Vue 3 + Element Plus（AiSchoolWeb）；本地 paper asset 上传。

**Spec:** `docs/superpowers/specs/2026-07-27-paper-ui-screenshot-toc-design.md`

**Note:** 按仓库约定，**不要**自动 `git commit`；任务末尾「Commit」步骤改为「自检 diff / 等待用户要求再提交」。不要创建 git worktree。

---

## File map

| 文件 | 职责 |
|------|------|
| `docs/script/sql/update/update-paper-ui-screenshots.sql` | `paper_session.ui_screenshots_json` |
| `.../domain/paper/PaperUiScreenshot.java` | 截图条目领域模型 |
| `.../domain/paper/PaperSession.java` | 增加 `uiScreenshots` |
| `.../domain/paper/TocNode.java` | 增加 `screenshotAssetUrl` |
| `.../domain/entity/paper/PaperSessionEntity.java` | 持久化列 |
| `.../service/paper/PaperSessionPersistence.java` | 读写 JSON |
| `.../service/paper/PaperTocCustomizer.java` | 截图驱动 rebuild ch5 |
| `.../service/paper/PaperScreenshotService.java` | 保存 / 视觉识别 |
| `.../service/paper/PaperUiScreenshotInjector.java` | 正文占位 → Markdown 图 |
| `.../controller/chat/PaperController.java` | analyze / save 端点；TOC 前置校验 |
| `AiSchoolWeb/src/api/paper/index.ts` | API 封装 |
| `AiSchoolWeb/.../paperUiScreenshotPlaceholder.ts` | 增加 `injectUiScreenshot` |
| `AiSchoolWeb/.../components/PaperUiScreenshotPanel.vue` | 双区上传 + 预览编辑 |
| `AiSchoolWeb/.../PaperGenerator.vue` | 接入面板、前置校验、生成后插图 |

---

### Task 1: SQL + 领域模型

**Files:**
- Create: `D:\project3\Ai-School\docs\script\sql\update\update-paper-ui-screenshots.sql`
- Create: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\domain\paper\PaperUiScreenshot.java`
- Modify: `...\domain\paper\PaperSession.java`
- Modify: `...\domain\paper\TocNode.java`
- Modify: `...\domain\entity\paper\PaperSessionEntity.java`

- [ ] **Step 1: 写 SQL**

```sql
ALTER TABLE paper_session
  ADD COLUMN ui_screenshots_json TEXT NULL COMMENT '系统实现截图清单 JSON' AFTER toc_json;
```

- [ ] **Step 2: 新增 `PaperUiScreenshot`**

```java
@Data
public class PaperUiScreenshot implements Serializable {
    private String id;          // uss_xxx
    private String module;      // admin | user
    private String assetUrl;
    private String title;
    private Integer sort;
    private Boolean confirmed;
}
```

- [ ] **Step 3: `PaperSession` 增加字段**

```java
private List<PaperUiScreenshot> uiScreenshots = new ArrayList<>();
```

- [ ] **Step 4: `TocNode` 增加字段**

```java
/** 第五章叶子节绑定的功能界面截图 URL（可空） */
private String screenshotAssetUrl;
```

- [ ] **Step 5: `PaperSessionEntity` 增加 `uiScreenshotsJson` 并映射列 `ui_screenshots_json`**

- [ ] **Step 6: 自检** — 编译前先完成 Task 2 持久化；本任务改完可先 `mvn -pl ruoyi-modules/ruoyi-chat -am compile -DskipTests`（若缺持久化读写会在 Task 2 补齐）。

---

### Task 2: 会话持久化读写截图清单

**Files:**
- Modify: `...\service\paper\PaperSessionPersistence.java`

- [ ] **Step 1: `toDomain` / `toEntity` 对称处理**

在已有 `tocJson` / `sqlParsedJson` 的读写旁增加：

```java
// load
session.setUiScreenshots(readList(entity.getUiScreenshotsJson(), PaperUiScreenshot.class));

// save
entity.setUiScreenshotsJson(writeJson(session.getUiScreenshots()));
```

复用该类内已有的 `objectMapper` / `readList` / `writeJson` 辅助方法（若无 list 读取，仿 `toc` 解析写一个 `readList`）。

- [ ] **Step 2: 确认 `paperSessionStore.update` 保存路径会落库**（现有 save 全量会话则无需改 Store）。

- [ ] **Step 3: Compile**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am compile -DskipTests -q
```

Expected: exit 0

---

### Task 3: `rebuildChapter5Modules` 改为截图驱动

**Files:**
- Modify: `...\service\paper\PaperTocCustomizer.java`（约 `rebuildChapter5Modules` 139–179 行）
- Modify: `...\service\paper\PaperTocService.java`（generate / refresh 前置校验）
- Test: 可临时用 package-visible 方法 + 简单 JUnit（若模块无现成 test 目录，则用手动断言脚本或 `PaperTocCustomizer` 包内测试类 `src/test/java/.../PaperTocCustomizerScreenshotTest.java`）

- [ ] **Step 1: 重写 `rebuildChapter5Modules`**

逻辑要点：

```java
List<PaperUiScreenshot> shots = session.getUiScreenshots() == null
    ? List.of() : session.getUiScreenshots();
List<PaperUiScreenshot> admin = filterModule(shots, "admin");
List<PaperUiScreenshot> user = filterModule(shots, "user");
if (admin.isEmpty() && user.isEmpty()) {
    throw new ServiceException("请先上传系统功能截图（管理员或用户至少一侧）");
}
// 只添加非空模块分支；叶子 title = "5.1.n " + displayTitle(shot.getTitle())
// leaf.setScreenshotAssetUrl(shot.getAssetUrl());
// 末节：leafNode("5." + nextIndex + " 本章小结", 2) 且 screenshotAssetUrl=null
```

`displayTitle`：若 title 已以「功能」结尾则不加「功能」；管理端可保持用户确认的原文。

- [ ] **Step 2: `PaperTocService.generate` / `refreshChapter5` 在 customize 前校验截图非空**（与 customizer 双重保险）。

- [ ] **Step 3: `parse-sql` 触发的 refresh-ch5**：若无截图则**跳过** rebuild（不要抛错打断 SQL 解析）；若有截图则按截图重建编号。在 `PaperController` parse-sql 或 `refreshChapter5Modules` 调用处改：

```java
if (session.getUiScreenshots() != null && !session.getUiScreenshots().isEmpty()) {
    paperTocCustomizer.refreshChapter5Modules(toc, session);
}
```

- [ ] **Step 4: Compile + 手工构造 session 测两种模块组合（可写单元测试）**

```java
@Test
void rebuild_onlyAdmin_noUserBranch() {
  // admin 1 张 → children 含 5.1 + 本章小结，不含 5.2
}
```

---

### Task 4: 保存 / 识别 API

**Files:**
- Create: `...\domain\dto\request\PaperScreenshotsSaveRequest.java`
- Create: `...\domain\dto\request\PaperScreenshotsAnalyzeRequest.java`
- Create: `...\service\paper\PaperScreenshotService.java`
- Modify: `...\controller\chat\PaperController.java`
- Modify: session 详情 VO / `getSession` 映射（确保前端能读到 `uiScreenshots`）

- [ ] **Step 1: Save API**

```java
@PutMapping("/screenshots")
public R<Void> saveScreenshots(@RequestBody @Valid PaperScreenshotsSaveRequest req) {
    paperScreenshotService.save(req.getSessionId(), req.getScreenshots());
    return R.ok();
}
```

`save`：校验 module∈{admin,user}、assetUrl 非空；补 id（`uss_`+uuid）、sort；写入 session 并 persist。

- [ ] **Step 2: Analyze API**

```java
@PostMapping("/screenshots/analyze")
public R<List<PaperUiScreenshot>> analyze(@RequestBody @Valid PaperScreenshotsAnalyzeRequest req) {
    return R.ok(paperScreenshotService.analyze(req.getSessionId(), req.getItems()));
}
```

`analyze` 每张图：

1. 用 `PaperAssetService` / 本地路径解析 URL → bytes（可参考 `DrawReferenceImageLoader`）。
2. LangChain4j：`UserMessage.from(ImageContent.fromBase64(b64, mime), TextContent.from(prompt))`。
3. System/User 提示：只输出 JSON `{"title":"功能名"}`，中文短名（如「角色管理」），不要编号。
4. 模型：`DrawDiagramProperties.getVisionModel()`，空则默认模型。
5. 写回 `title`；识别失败 title 置 `""`，不中断整批（单条记日志）。

- [ ] **Step 3: GET session 响应带上 `uiScreenshots`**

- [ ] **Step 4: Compile**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am compile -DskipTests -q
```

---

### Task 5: 后端 finalize 兜底插图（可选但建议同迭代）

**Files:**
- Create: `...\service\paper\PaperUiScreenshotInjector.java`
- Modify: `...\service\paper\PaperGenerateService.java` `finalizeChapter`

- [ ] **Step 1: Injector**

```java
public static String inject(String content, String assetUrl, String caption) {
    if (StringUtils.isBlank(content) || StringUtils.isBlank(assetUrl)) return content;
    String md = "![" + (caption == null ? "功能界面" : caption) + "](" + assetUrl + ")";
    // 匹配与前端一致的占位正则，替换首处或全部 UI 截图占位
    return content.replaceFirst("【此处插入[^】]*界面截图[^】]*】", Matcher.quoteReplacement(md));
}
```

- [ ] **Step 2: `finalizeChapter` 中若 `chapterNode.getScreenshotAssetUrl()` 非空则调用 inject**（在 citation sanitize 之后或之前均可，建议 sanitize 之后）。

---

### Task 6: 前端 API + 占位注入工具

**Files:**
- Modify: `D:\project3\AiSchoolWeb\src\api\paper\index.ts`
- Modify: `D:\project3\AiSchoolWeb\src\pages\paper-generator\paperUiScreenshotPlaceholder.ts`
- Modify: paper 相关 types（同文件或 `types`）

- [ ] **Step 1: 类型与 API**

```ts
export interface PaperUiScreenshot {
  id?: string
  module: 'admin' | 'user'
  assetUrl: string
  title?: string
  sort?: number
  confirmed?: boolean
}

export async function savePaperScreenshots(sessionId: string, screenshots: PaperUiScreenshot[]) {
  const res = await put<void>('/api/paper/screenshots', { sessionId, screenshots }).json()
  return unwrapData<void>(res)
}

export async function analyzePaperScreenshots(
  sessionId: string,
  items?: PaperUiScreenshot[],
) {
  const res = await post<PaperUiScreenshot[]>('/api/paper/screenshots/analyze', {
    sessionId,
    items,
  }).json()
  return unwrapData(res)
}
```

（若项目 `put` 未导出，用 `post` 并改后端为 POST `/screenshots/save`——**前后端保持一致**；优先检查现有 request 封装是否有 put。）

- [ ] **Step 2: 扩展 placeholder 工具**

```ts
export function injectUiScreenshot(
  content: string,
  assetUrl: string,
  caption: string,
): string {
  if (!content || !assetUrl) return content || ''
  const md = `![${caption || '功能界面'}](${assetUrl})`
  for (const re of UI_SCREENSHOT_PLACEHOLDER_PATTERNS) {
    re.lastIndex = 0
    if (re.test(content))
      return content.replace(re, md)
  }
  // 无占位则在文末追加
  return `${content.trim()}\n\n${md}\n`
}
```

---

### Task 7: `PaperUiScreenshotPanel.vue`

**Files:**
- Create: `D:\project3\AiSchoolWeb\src\pages\paper-generator\components\PaperUiScreenshotPanel.vue`

- [ ] **Step 1: UI 结构**

- 两列 / 两块：`管理员功能截图`、`用户功能截图`
- 每块：`el-upload` multiple、list-type=picture-card；上传调用 `uploadPaperAsset`，本地 list push `{ module, assetUrl, title: '', sort }`
- 底部操作：`识别功能`（调用 analyze → 回填 title）、`保存清单`（savePaperScreenshots）
- 表格/列表：缩略图、模块标签、`el-input` 编辑 title、删除
- Props：`sessionId`；`v-model` 或 emit `update:screenshots`
- 样式：沿用 paper-generator / saas-card 现有风格，勿引入新色板

- [ ] **Step 2: 暴露方法**

```ts
defineExpose({
  getScreenshots: () => list.value,
  ensureReady: () => { /* 至少一侧有图且每条有非空 title，否则 throw/返回 false */ }
})
```

---

### Task 8: 接入 `PaperGenerator.vue`

**Files:**
- Modify: `D:\project3\AiSchoolWeb\src\pages\paper-generator\PaperGenerator.vue`

- [ ] **Step 1: 配置区挂载面板**（SQL/代码附近）

```vue
<PaperUiScreenshotPanel
  v-if="sessionId"
  ref="screenshotPanelRef"
  :session-id="sessionId"
  v-model:screenshots="uiScreenshots"
/>
```

加载 session 时把后端 `uiScreenshots` 赋给 `uiScreenshots`。

- [ ] **Step 2: 生成大纲前置**

在 `generateTocFlow` / `ensureReferencePrerequisites` 旁增加：

```ts
async function ensureScreenshotPrerequisites() {
  const list = uiScreenshots.value || []
  if (!list.length) {
    ElMessage.warning('请至少上传一侧系统功能截图（管理员或用户）')
    throw new Error('no screenshots')
  }
  if (list.some(s => !s.title?.trim())) {
    ElMessage.warning('请先识别或填写每张截图的功能名称并保存')
    throw new Error('screenshot title missing')
  }
  await savePaperScreenshots(sessionId.value!, list)
}
```

调用顺序：截图校验 → 文献确认 → `generatePaperToc`。

- [ ] **Step 3: `generateSingleChapter` 完成后插图**

```ts
const node = findTocNode(toc.value, chapterId)
if (node?.screenshotAssetUrl) {
  content = injectUiScreenshot(content, node.screenshotAssetUrl, bareTitle(node.title))
  await savePaperChapterContent(sessionId, chapterId, content)
}
```

（若后端 Task 5 已注入，前端仍可再跑一次幂等替换。）

- [ ] **Step 4: 停止 SQL 解析后无截图时对 ch5 的破坏性覆盖** — 前端若收到 `tocRefreshed` 仅在有截图时刷新本地 toc。

- [ ] **Step 5: 手动冒烟清单**

1. 只传管理员 2 张 → 识别改标题 → 生成大纲 → 仅 5.1.x + 小结  
2. 生成 5.1.1 → 正文出现对应 Markdown 图  
3. 两侧皆空点生成大纲 → 被拦截  

---

### Task 9: 端到端验收与文档

- [ ] **Step 1: 执行 spec 第 11 节验收标准勾选**
- [ ] **Step 2: 本地执行 `update-paper-ui-screenshots.sql`**
- [ ] **Step 3: 重启后端，验证 vision 模型配置（`chat.diagram.vision-model`）**
- [ ] **Step 4: 更新设计文档状态已落地（可选一行 changelog）**

---

## Spec coverage self-check

| Spec 项 | Task |
|---------|------|
| 双区上传 | T7 |
| 识别预览可编辑 | T4 + T7 |
| 至少一侧有图 | T3 + T8 |
| 无图侧不建分支 | T3 |
| 完全截图驱动 ch5 | T3 |
| SQL 不再覆盖 ch5 | T3 Step 3 + T8 Step 4 |
| 正文自动插图 | T5 + T6 + T8 |
| 持久化 | T1 + T2 |
| 非目标未纳入 | — |

## Placeholder scan

无 TBD；计费按 spec「实现时对齐，无则打日志不阻断」——在 `PaperScreenshotService.analyze` 开头加注释即可，不强制扣费除非已有明确 feature code。

---

## Execution

Plan saved to `docs/superpowers/plans/2026-07-27-paper-ui-screenshot-toc.md`.

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 每任务独立子代理，任务间复查  
2. **Inline Execution** — 本会话按任务连续实现并设检查点  

选哪一种？
