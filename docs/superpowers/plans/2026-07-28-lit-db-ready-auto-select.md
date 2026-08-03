# 库内文献充足则跳过爬取并自动选用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 第一次生成大纲触发 `lit-ondemand/start` 时，先用题目按参考文献弹窗同款规则检索；中英各 ≥50 则跳过爬取，自动确认写入会话参考文献（中文 18 + 英文 2）；不足则维持现有双语爬取（只入库不勾选）。

**Architecture:** 在 `LitOnDemandService.runAsync` 开头调用 `LitPaperSearchService.search(title, zh|en, 50)`。充足且 `session.references` 为空时，组装 18+2 经 `PaperSessionStore.update` 写入（与 `confirmReferences` 对齐），任务 `done` + `source=db`。否则走现有 bilingual crawl，`source=crawl`。前端根据 `source` 分支文案，并在 `db` 路径刷新 `aiRefList`。

**Tech Stack:** Java Spring Boot（ruoyi-chat）、Vue 3（AiSchoolWeb）、现有 `LitPaperSearchService` / `lit_paper` / `lit_paper_en`

**Spec:** `docs/superpowers/specs/2026-07-28-lit-db-ready-auto-select-design.md`

**Note:** 本仓库约定不自动 `git commit`、不使用 git worktree；各 Task 末「Commit」步骤仅在用户明确要求提交时执行。

---

## File map

| 文件 | 职责 |
|------|------|
| `ruoyi-chat/.../config/LitPaperProperties.java` | `OnDemand` 增加 `dbReadyMinCount` / `autoSelectZh` / `autoSelectEn` |
| `ruoyi-admin/.../application.yml` + `application-dev.yml` | 配置默认值 |
| `ruoyi-chat/.../paper/LitOnDemandTask.java` | `source`、`selectedCountZh/En` |
| `ruoyi-chat/.../vo/paper/LitOnDemandStatusVo.java` | 同上字段映射 |
| `ruoyi-chat/.../paper/LitOnDemandService.java` | 库存判定、自动选写、再决定是否爬 |
| `ruoyi-chat/.../test/.../LitOnDemandServiceDbReadyTest.java` | 充足/不足/不覆盖单测 |
| `AiSchoolWeb/src/api/paper/index.ts` | `LitOnDemandStatus` 类型 |
| `AiSchoolWeb/.../PaperGenerator.vue` | `applyLitStatus` 文案 + 同步 `aiRefList` |

---

### Task 1: 配置项

**Files:**
- Modify: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\config\LitPaperProperties.java`
- Modify: `D:\project3\Ai-School\ruoyi-admin\src\main\resources\application.yml`（`paper.lit.ondemand` 段）
- Modify: `D:\project3\Ai-School\ruoyi-admin\src\main\resources\application-dev.yml`（同段）

- [ ] **Step 1: 在 `OnDemand` 增加三个字段（带默认值）**

```java
/** 中/英库内检索各至少该条数才视为充足并跳过爬取 */
private int dbReadyMinCount = 50;

/** 库内充足时自动选用中文篇数 */
private int autoSelectZh = 18;

/** 库内充足时自动选用英文篇数 */
private int autoSelectEn = 2;
```

- [ ] **Step 2: YAML 写入默认值**

```yaml
db-ready-min-count: 50
auto-select-zh: 18
auto-select-en: 2
```

- [ ] **Step 3: Commit（仅用户要求时）**

```bash
git add ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/config/LitPaperProperties.java \
  ruoyi-admin/src/main/resources/application.yml \
  ruoyi-admin/src/main/resources/application-dev.yml
# git commit -m "feat(lit): add db-ready auto-select config"
```

---

### Task 2: Task / VO 状态字段

**Files:**
- Modify: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\service\paper\LitOnDemandTask.java`
- Modify: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\domain\vo\paper\LitOnDemandStatusVo.java`

- [ ] **Step 1: `LitOnDemandTask` 增加字段**

```java
/** db = 库内直选；crawl = 爬取路径；未判定前可为 null */
private String source;

private int selectedCountZh;

private int selectedCountEn;
```

- [ ] **Step 2: `LitOnDemandStatusVo` 增加同名字段，并在 `LitOnDemandService.toVo` 中映射**

```java
vo.setSource(task.getSource());
vo.setSelectedCountZh(task.getSelectedCountZh());
vo.setSelectedCountEn(task.getSelectedCountEn());
```

- [ ] **Step 3: Commit（仅用户要求时）**

---

### Task 3: 单测（先写失败用例）

**Files:**
- Create: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\test\java\org\ruoyi\service\paper\LitOnDemandServiceDbReadyTest.java`

说明：用 Mockito 构造 `LitOnDemandService` 依赖（`LitPaperProperties`、`PaperSessionStore`、`CnkiCrawlerProcessClient`、`LitPaperMapper`、`LitPaperEnMapper`、`LitPaperSearchService`、`ObjectMapper`）。若现有工程对 `@Async` / `SpringUtils` 难测，可将「库存判定 + 自动写入」抽成 package-visible 方法 `tryAutoSelectFromDb(LitOnDemandTask task)`，单测直接调该方法；`runAsync` 开头调用它，返回 `true` 则不再爬。

- [ ] **Step 1: 写三个失败用例骨架**

```java
@Test
void autoSelectsWhenZhAndEnEachHaveMinCount() {
    // search zh/en 各返回 50 条；session.references 空
    // assert: crawler 未被调用；update 写入 20 条（18 zh + 2 en）；
    // task.source == "db"；fetchedCountZh==18；fetchedCountEn==2；litStatus==done
}

@Test
void crawlsWhenEitherLanguageBelowMinCount() {
    // zh=50, en=10
    // assert: crawler 被调用；references 未被写入；source == "crawl"
}

@Test
void doesNotOverwriteExistingReferences() {
    // session 已有 3 条 references；库内充足
    // assert: references 仍为原 3 条；source 可为 db 且跳过爬，或仍标记 db 但不改列表
}
```

辅助：`Reference` 最小构造只需 `setTitle` / `setLanguage` / `setIndex`。

- [ ] **Step 2: 跑测确认失败**

```powershell
cd D:\project3\Ai-School
mvn -pl ruoyi-modules/ruoyi-chat -am -Dtest=LitOnDemandServiceDbReadyTest test
```

Expected: 编译失败或断言失败（方法尚不存在）。

- [ ] **Step 3: Commit（仅用户要求时）**

---

### Task 4: `LitOnDemandService` 实现库存判定与自动选用

**Files:**
- Modify: `D:\project3\Ai-School\ruoyi-modules\ruoyi-chat\src\main\java\org\ruoyi\service\paper\LitOnDemandService.java`

- [ ] **Step 1: 注入 `LitPaperSearchService`**

构造器已由 `@RequiredArgsConstructor` 生成，新增：

```java
private final LitPaperSearchService litPaperSearchService;
```

- [ ] **Step 2: 实现 `tryAutoSelectFromDb`（返回 true 表示已处理完、勿爬）**

伪代码（实现时写成完整 Java）：

```java
boolean tryAutoSelectFromDb(LitOnDemandTask task) {
    LitPaperProperties.OnDemand cfg = litPaperProperties.getOndemand();
    int min = Math.max(1, cfg.getDbReadyMinCount());
    int takeZh = Math.max(0, cfg.getAutoSelectZh());
    int takeEn = Math.max(0, cfg.getAutoSelectEn());
    String title = task.getTitle();

    List<Reference> zh = litPaperSearchService.search(title, "zh", min);
    List<Reference> en = litPaperSearchService.search(title, "en", min);
    if (zh.size() < min || en.size() < min) {
        return false;
    }

    task.setSource("db");
    PaperSession session = paperSessionStore.require(task.getSessionId(), /* 需要 userId */);
    // 注意：runAsync 无 userId。改用 paperSessionStore.get(sessionId) 或不校验 user 的内部读，
    // 或在 start() 时把 userId 写入 LitOnDemandTask。
    // 推荐：LitOnDemandTask 增加 Long userId，start() 时写入。

    if (session.getReferences() != null && !session.getReferences().isEmpty()) {
        task.setLitStatus(LitOnDemandTask.Status.DONE);
        task.setFetchedCountZh(0);
        task.setFetchedCountEn(0);
        task.setFetchedCount(0);
        task.setSelectedCountZh(0);
        task.setSelectedCountEn(0);
        task.setError(null);
        touch(task);
        return true; // 不覆盖、不爬
    }

    List<Reference> picked = new ArrayList<>();
    for (int i = 0; i < takeZh && i < zh.size(); i++) {
        picked.add(zh.get(i));
    }
    for (int i = 0; i < takeEn && i < en.size(); i++) {
        picked.add(en.get(i));
    }
    for (int i = 0; i < picked.size(); i++) {
        picked.get(i).setIndex(i + 1);
        // language 已由 search 的 toReference 设置
    }

    paperSessionStore.update(task.getSessionId(), s -> {
        s.setReferences(picked);
        s.setStatus(PaperSession.Status.REF_CONFIRMED);
        PaperReferenceContentHelper.syncReferenceChapter(s);
    });

    task.setSelectedCountZh(Math.min(takeZh, zh.size()));
    task.setSelectedCountEn(Math.min(takeEn, en.size()));
    task.setFetchedCountZh(task.getSelectedCountZh());
    task.setFetchedCountEn(task.getSelectedCountEn());
    task.setFetchedCount(task.getSelectedCountZh() + task.getSelectedCountEn());
    task.setLitStatus(LitOnDemandTask.Status.DONE);
    task.setError(null);
    touch(task);
    return true;
}
```

**`userId` 处理（必须落地）：** 在 `LitOnDemandTask` 增加 `Long userId`；`start(sessionId, userId)` 写入；`runAsync` / `tryAutoSelectFromDb` 用 `paperSessionStore.require(sessionId, userId)`。

- [ ] **Step 3: 在 `runAsync` 开头调用**

```java
task.setLitStatus(LitOnDemandTask.Status.RUNNING);
touch(task);
if (tryAutoSelectFromDb(task)) {
    return;
}
task.setSource("crawl");
// ... 现有 bilingual crawl ...
```

爬取路径结束时若未设 `source`，确保为 `"crawl"`。

- [ ] **Step 4: 跑 Task 3 单测至通过**

```powershell
mvn -pl ruoyi-modules/ruoyi-chat -am -Dtest=LitOnDemandServiceDbReadyTest,CnkiCrawlerProcessClientTest test
```

Expected: PASS

- [ ] **Step 5: Commit（仅用户要求时）**

---

### Task 5: 前端状态与同步

**Files:**
- Modify: `D:\project3\AiSchoolWeb\src\api\paper\index.ts`
- Modify: `D:\project3\AiSchoolWeb\src\pages\paper-generator\PaperGenerator.vue`

- [ ] **Step 1: 扩展类型**

```ts
export interface LitOnDemandStatus {
  // ...existing...
  source?: 'db' | 'crawl' | string
  selectedCountZh?: number
  selectedCountEn?: number
}
```

- [ ] **Step 2: 改 `applyLitStatus`**

当 `s === 'done' && status.source === 'db'`：

1. Toast：`库内文献充足，已自动选用中文 ${zh} + 英文 ${en}，可直接生成正文或打开参考文献调整`
2. 若有 `sessionId`：`const session = await getPaperSession(sessionId.value)`，`aiRefList.value = dedupeReferences(session.references ?? [])`
3. `litFetchedCount` 用 `status.fetchedCount ?? selectedCountZh+selectedCountEn`

当 `source === 'crawl'`（或缺省）：保持现有中英入库文案，**不**改 `aiRefList`。

注意：`applyLitStatus` 若改为 async，调用处 `startLitPoll` 内需 `await applyLitStatus(status)`。

- [ ] **Step 3: 卡片文案（可选微调）**

`done && source===db` 时可显示「已自动选用（20）」；若不想改模板，仅 toast + 同步列表即可。

- [ ] **Step 4: 手工验证清单**

1. 库内题目相关中英各 ≥50：生成大纲 → 文献卡片很快 done → 参考文献已有 20 篇 → 可不打开弹窗直接生成正文  
2. 库存不足：仍出现「文献获取中」较久 → 完成后列表仍空，需手动勾选  
3. 已有勾选再生成大纲：不丢用户已选

- [ ] **Step 5: Commit（仅用户要求时）**

---

## Spec coverage checklist

| Spec 要求 | Task |
|-----------|------|
| 入口扩写 lit-ondemand | Task 4 |
| LitQueryNormalizer / LitPaperSearchService 检索 | Task 4 |
| 中英各 ≥50 | Task 1 配置 + Task 4 |
| 自动 18+2 confirm | Task 4 |
| 不足则双语爬取不勾选 | Task 4 |
| 不覆盖已有 references | Task 3/4 |
| source / selectedCount* | Task 2/5 |
| 前端文案 + 同步 aiRefList | Task 5 |
| 配置项 | Task 1 |
| 单测 | Task 3/4 |

## Self-review notes

- `userId` 写入 Task 已在 Task 4 Step 2 标明，避免 `require` 无 user。
- 不抽新 HTTP 接口，符合方案 A。
- Commit 步骤受仓库「不自动提交」约束，执行时跳过除非用户要求。
