# Session Custom Format Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users upload a school Word layout (docx + format JSON) bound only to the current paper session, mutually exclusive with school templates, and use it on export.

**Architecture:** Extend `paper_session` with `custom_format_*` columns and disk files under `{upload}/paper/session-format/{userId}/{sessionId}/`. New `PaperSessionCustomFormatService` owns upload/delete/open. `PaperFormatTemplateService.resolveEffective(PaperSession)` and `WordExportService` branch on custom mode. AiSchoolWeb `PaperFormatPanel` adds upload/clear UX.

**Tech Stack:** Spring Boot / MyBatis-Plus (`ruoyi-chat`), MySQL, Vue 3 + Element Plus (`AiSchoolWeb`)

**Spec:** `docs/superpowers/specs/2026-07-22-session-custom-format-template-design.md`

**Conventions for this repo:** Work in main tree `D:\project3\Ai-School` / `D:\project3\AiSchoolWeb` (no git worktree). Do **not** `git commit` unless the user explicitly asks.

---

## File structure

| Path | Responsibility |
|------|----------------|
| `docs/script/sql/update/updat-paper-session-custom-format.sql` | ALTER `paper_session` |
| `.../domain/entity/paper/PaperSessionEntity.java` | + custom columns |
| `.../domain/paper/PaperSession.java` | + custom domain fields |
| `.../service/paper/PaperSessionPersistence.java` | map custom fields ↔ entity |
| `.../domain/dto/response/PaperSessionFormatVo.java` | + mode / custom fields |
| `.../service/paper/PaperSessionCustomFormatService.java` | upload / delete / open / clear |
| `.../service/paper/PaperFormatTemplateService.java` | `resolveEffective(PaperSession)` |
| `.../controller/chat/PaperController.java` | custom-docx APIs + format VO/PUT |
| `.../service/paper/WordExportService.java` | open custom docx; honor patch flag |
| `.../test/.../PaperSessionCustomFormatMergeTest.java` | merge + patch semantics |
| `AiSchoolWeb/src/api/paper/index.ts` | types + upload/delete APIs |
| `AiSchoolWeb/.../PaperFormatPanel.vue` | upload UI |

---

### Task 1: SQL migration

**Files:**
- Create: `docs/script/sql/update/updat-paper-session-custom-format.sql`

- [ ] **Step 1: Write migration SQL**

```sql
-- 会话级自定义排版模板（仅绑定当前会话）
ALTER TABLE `paper_session`
  ADD COLUMN `custom_format_docx_path` varchar(500) DEFAULT NULL COMMENT '自定义排版docx相对路径' AFTER `format_override_json`,
  ADD COLUMN `custom_format_docx_name` varchar(255) DEFAULT NULL COMMENT '自定义docx原名' AFTER `custom_format_docx_path`,
  ADD COLUMN `custom_format_docx_size` bigint DEFAULT NULL COMMENT '自定义docx字节数' AFTER `custom_format_docx_name`,
  ADD COLUMN `custom_format_json` mediumtext DEFAULT NULL COMMENT '自定义版式主配置JSON' AFTER `custom_format_docx_size`,
  ADD COLUMN `custom_patch_styles` tinyint DEFAULT NULL COMMENT '1强制patch样式 0不patch' AFTER `custom_format_json`;
```

- [ ] **Step 2: Apply on target DB** (dev `ai_sc` or local)

```bash
# example — use project DB credentials
mysql -h ... -P 3307 -u root -p ai_sc < docs/script/sql/update/updat-paper-session-custom-format.sql
```

Expected: columns exist on `paper_session`.

- [ ] **Step 3: Commit only if user asks** — skip by default.

---

### Task 2: Entity + domain + persistence mapping

**Files:**
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/entity/paper/PaperSessionEntity.java`
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/paper/PaperSession.java`
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperSessionPersistence.java` (around lines mapping format fields ~172–227)

- [ ] **Step 1: Add fields to `PaperSessionEntity`**

```java
private Long formatTemplateId;
private String formatOverrideJson;

private String customFormatDocxPath;
private String customFormatDocxName;
private Long customFormatDocxSize;
private String customFormatJson;
private Integer customPatchStyles;
```

- [ ] **Step 2: Add same fields to `PaperSession` domain** (after `formatOverrideJson`)

```java
/** 会话自定义排版 docx 相对路径；非空 = 自定义模式 */
private String customFormatDocxPath;
private String customFormatDocxName;
private Long customFormatDocxSize;
/** 自定义版式主配置 JSON */
private String customFormatJson;
/** 1=强制 patch 样式；0=不 patch；自定义模式默认 1 */
private Integer customPatchStyles;
```

- [ ] **Step 3: Wire `PaperSessionPersistence` both directions**

In `toSession` / `toEntity` (names may differ — mirror existing `formatTemplateId` mapping):

```java
session.setCustomFormatDocxPath(entity.getCustomFormatDocxPath());
session.setCustomFormatDocxName(entity.getCustomFormatDocxName());
session.setCustomFormatDocxSize(entity.getCustomFormatDocxSize());
session.setCustomFormatJson(entity.getCustomFormatJson());
session.setCustomPatchStyles(entity.getCustomPatchStyles());

entity.setCustomFormatDocxPath(session.getCustomFormatDocxPath());
entity.setCustomFormatDocxName(session.getCustomFormatDocxName());
entity.setCustomFormatDocxSize(session.getCustomFormatDocxSize());
entity.setCustomFormatJson(session.getCustomFormatJson());
entity.setCustomPatchStyles(session.getCustomPatchStyles());
```

- [ ] **Step 4: Compile check**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am compile -q
```

Expected: SUCCESS

---

### Task 3: Custom merge semantics (TDD)

**Files:**
- Create: `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/paper/PaperSessionCustomFormatMergeTest.java`
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperFormatTemplateService.java`

- [ ] **Step 1: Write failing tests for custom merge + patch flag**

```java
package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;

import static org.junit.jupiter.api.Assertions.*;

class PaperSessionCustomFormatMergeTest {

    @Test
    void customMode_mergesDefaultThenCustomThenOverride() {
        PaperFormatConfig custom = new PaperFormatConfig();
        custom.getFont().setBodyEastAsia("仿宋");
        custom.getFontSize().setBody(12.0);

        PaperFormatConfig override = new PaperFormatConfig();
        override.getFontSize().setBody(14.0);

        PaperFormatConfig effective = PaperFormatMerger.merge(
            PaperFormatDefaults.dalianOcean(), custom, override);
        assertEquals("仿宋", effective.getFont().getBodyEastAsia());
        assertEquals(14.0, effective.getFontSize().getBody());
    }

    @Test
    void applyCustomPatchFlag_setsExportPatchTemplateStyles() {
        PaperFormatConfig effective = PaperFormatDefaults.dalianOcean();
        PaperSessionCustomFormatService.applyPatchFlag(effective, 0);
        assertEquals(Boolean.FALSE, effective.getExport().getPatchTemplateStyles());

        PaperFormatConfig effective2 = PaperFormatDefaults.dalianOcean();
        PaperSessionCustomFormatService.applyPatchFlag(effective2, 1);
        assertEquals(Boolean.TRUE, effective2.getExport().getPatchTemplateStyles());
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL** (missing `applyPatchFlag`)

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am test -Dtest=PaperSessionCustomFormatMergeTest
```

- [ ] **Step 3: Add `PaperSessionCustomFormatService` stub helpers + `resolveEffective(PaperSession)`**

Create `PaperSessionCustomFormatService.java` with:

```java
public static boolean isCustomMode(PaperSession session) {
    return session != null && StringUtils.isNotBlank(session.getCustomFormatDocxPath());
}

public static void applyPatchFlag(PaperFormatConfig effective, Integer customPatchStyles) {
    if (effective.getExport() == null) {
        effective.setExport(new PaperFormatConfig.Export());
    }
    boolean patch = customPatchStyles == null || customPatchStyles != 0;
    effective.getExport().setPatchTemplateStyles(patch);
}

public static void clearCustomFields(PaperSession session) {
    session.setCustomFormatDocxPath(null);
    session.setCustomFormatDocxName(null);
    session.setCustomFormatDocxSize(null);
    session.setCustomFormatJson(null);
    session.setCustomPatchStyles(null);
}
```

In `PaperFormatTemplateService`:

```java
public PaperFormatConfig resolveEffective(PaperSession session) {
    if (PaperSessionCustomFormatService.isCustomMode(session)) {
        try {
            PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
            PaperFormatConfig custom = PaperFormatMerger.parseJson(session.getCustomFormatJson());
            PaperFormatConfig override = PaperFormatMerger.parseJson(session.getFormatOverrideJson());
            PaperFormatConfig effective = PaperFormatMerger.merge(def, custom, override);
            PaperSessionCustomFormatService.applyPatchFlag(effective, session.getCustomPatchStyles());
            PaperFormatMerger.validate(effective);
            return effective;
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }
    return resolveEffective(session.getFormatTemplateId(), session.getFormatOverrideJson());
}
```

Keep existing `resolveEffective(Long, String)` for callers that only have ids.

- [ ] **Step 4: Re-run tests — expect PASS**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am test -Dtest=PaperSessionCustomFormatMergeTest
```

---

### Task 4: Upload / delete / open custom docx service

**Files:**
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperSessionCustomFormatService.java`
- Reuse validation patterns from `PaperFormatTemplateService.uploadDocx` / `validateDocx`
- Use `PaperFormatTemplateProperties.getLocalDir()` parent or same upload root; path relative: `paper/session-format/{userId}/{sessionId}/thesis-template.docx`

- [ ] **Step 1: Implement storage paths**

```java
private static final long MAX_BYTES = 10L * 1024 * 1024;
private static final String DOCX_NAME = "thesis-template.docx";

public Path absoluteDocxPath(Long userId, String sessionId) {
    return Paths.get(baseUploadDir(), "paper", "session-format",
        String.valueOf(userId), sessionId, DOCX_NAME);
}

public String relativeDocxPath(Long userId, String sessionId) {
    return "paper/session-format/" + userId + "/" + sessionId + "/" + DOCX_NAME;
}
```

`baseUploadDir()`: same strategy as `PaperFormatTemplateService.baseDir()` (read from properties / RuoYi profile). Prefer injecting `PaperFormatTemplateProperties` or a shared upload root bean already used by format templates.

- [ ] **Step 2: Implement `saveCustomDocx(PaperSession session, Long userId, MultipartFile file, PaperFormatConfig formatOrNull, Boolean patchStyles)`**

Logic:

1. Validate file non-empty, `.docx`, size ≤ 10MB, `validateDocx(file)` (copy private helper or extract shared util from template service — if private, duplicate minimal ZIP magic check for YAGNI or make package-private static).
2. If `formatOrNull == null`: compute snapshot via `paperFormatTemplateService.resolveEffective(session)` **before** mutating custom fields (school-mode effective), use that as `custom_format_json`.
3. Else validate: `PaperFormatMerger.merge(defaults, format)` + `validate`.
4. Write bytes to absolute path (create dirs).
5. Set session fields: path/name/size/json/`customPatchStyles` = `Boolean.FALSE.equals(patchStyles) ? 0 : 1`
6. `session.setFormatTemplateId(null)`
7. Persist via caller `paperSessionStore.update` (service mutates session object; controller wraps update)

- [ ] **Step 3: Implement `clearCustomDocx(PaperSession session, Long userId)`**

Delete directory `paper/session-format/{userId}/{sessionId}` best-effort; always `clearCustomFields(session)`.

- [ ] **Step 4: Implement `openCustomDocx(PaperSession session)`**

```java
public InputStream openCustomDocx(PaperSession session) {
    Path path = resolveAbsolute(session.getCustomFormatDocxPath());
    if (!Files.isRegularFile(path)) {
        throw new ServiceException("自定义排版模板文件缺失，请重新上传");
    }
    try {
        return Files.newInputStream(path);
    } catch (IOException e) {
        throw new ServiceException("读取自定义排版模板失败: " + e.getMessage());
    }
}
```

---

### Task 5: Controller APIs + format VO / PUT

**Files:**
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/dto/response/PaperSessionFormatVo.java`
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/controller/chat/PaperController.java`

- [ ] **Step 1: Extend `PaperSessionFormatVo`**

```java
/** school | custom */
private String mode;
private Boolean hasCustomDocx;
private String customDocxName;
private Boolean customPatchStyles;
private PaperFormatConfig customFormat;
```

- [ ] **Step 2: Rewrite `toSessionFormatVo`**

```java
private PaperSessionFormatVo toSessionFormatVo(PaperSession session) {
    PaperSessionFormatVo vo = new PaperSessionFormatVo();
    boolean custom = PaperSessionCustomFormatService.isCustomMode(session);
    vo.setMode(custom ? "custom" : "school");
    vo.setHasCustomDocx(custom);
    vo.setCustomDocxName(session.getCustomFormatDocxName());
    vo.setCustomPatchStyles(custom && (session.getCustomPatchStyles() == null || session.getCustomPatchStyles() != 0));
    if (StringUtils.isNotBlank(session.getCustomFormatJson())) {
        vo.setCustomFormat(PaperFormatMerger.parseJson(session.getCustomFormatJson()));
    }
    vo.setTemplateId(session.getFormatTemplateId());
    // override + effective via resolveEffective(session)
    ...
    vo.setEffective(paperFormatTemplateService.resolveEffective(session));
    vo.setDefaults(PaperFormatDefaults.dalianOcean());
    return vo;
}
```

- [ ] **Step 3: Add endpoints on `PaperController`**

```java
@PostMapping(value = "/session/{sessionId}/format/custom-docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<PaperSessionFormatVo> uploadCustomFormatDocx(
    @PathVariable String sessionId,
    @RequestPart("file") MultipartFile file,
    @RequestPart(value = "format", required = false) String formatJson,
    @RequestParam(value = "patchStyles", required = false) Boolean patchStyles) {
    Long userId = LoginHelper.getUserId();
    paperSessionStore.require(sessionId, userId);
    PaperFormatConfig format = null;
    if (StringUtils.isNotBlank(formatJson)) {
        format = PaperFormatMerger.parseJson(formatJson);
    }
    paperSessionStore.update(sessionId, s ->
        paperSessionCustomFormatService.saveCustomDocx(s, userId, file, format, patchStyles));
    return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
}

@DeleteMapping("/session/{sessionId}/format/custom-docx")
public R<PaperSessionFormatVo> deleteCustomFormatDocx(@PathVariable String sessionId) {
    Long userId = LoginHelper.getUserId();
    paperSessionStore.require(sessionId, userId);
    paperSessionStore.update(sessionId, s ->
        paperSessionCustomFormatService.clearCustomDocx(s, userId));
    return R.ok(toSessionFormatVo(paperSessionStore.require(sessionId, userId)));
}
```

Note: if `format` is easier as `@RequestParam String format`, match frontend FormData; prefer one consistent approach and document it in the Vue API helper.

- [ ] **Step 4: Update `updateSessionFormat` for custom → school**

When `templateIdSpecified` and session is custom mode (or always when binding a school template):

```java
paperSessionStore.update(sessionId, s -> {
    if (templateIdSpecified) {
        if (PaperSessionCustomFormatService.isCustomMode(s)) {
            paperSessionCustomFormatService.clearCustomDocx(s, userId);
        }
        s.setFormatTemplateId(requestedTemplateId);
    }
    // existing clearOverride / override logic — for override validation use resolveEffective(s) after mutations
    ...
});
```

When validating override in custom mode, call `resolveEffective` on the updated session (or merge custom+override explicitly).

- [ ] **Step 5: Manual smoke with curl** (after backend up)

```bash
# upload
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@school.docx" -F "patchStyles=true" -F "format={}" \
  "$API/api/paper/session/$SID/format/custom-docx"
```

Expected: `mode=custom`, `hasCustomDocx=true`.

---

### Task 6: WordExportService branch

**Files:**
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/WordExportService.java` (~200–239)

- [ ] **Step 1: Use session-aware resolve + open**

```java
public byte[] export(String sessionId) {
    PaperSession session = paperSessionStore.get(sessionId);
    if (session == null) {
        throw new ServiceException("会话不存在或已过期");
    }
    PaperFormatConfig effective = paperFormatTemplateService.resolveEffective(session);
    exportFormat.set(effective);
    try (InputStream templateIn = openExportTemplate(session);
         XWPFDocument doc = openCleanTemplateDocument(templateIn)) {
        ...
        // existing applyPageSetup / patchTemplateStyles already read fmt().getExport()
```

```java
private InputStream openExportTemplate(PaperSession session) {
    if (PaperSessionCustomFormatService.isCustomMode(session)) {
        return paperSessionCustomFormatService.openCustomDocx(session);
    }
    try {
        return paperFormatTemplateService.openDocx(session.getFormatTemplateId());
    } catch (Exception e) {
        log.warn("打开排版模板 docx 失败，回退全局模板: {}", e.getMessage());
        return paperTemplateService.openTemplateInputStream();
    }
}
```

Do **not** silently fallback to classpath when custom file missing — `openCustomDocx` already throws.

- [ ] **Step 2: Export smoke** with custom patch true/false (manual).

---

### Task 7: AiSchoolWeb API + PaperFormatPanel UI

**Files:**
- Modify: `D:\project3\AiSchoolWeb\src\api\paper\index.ts`
- Modify: `D:\project3\AiSchoolWeb\src\pages\paper-generator\components\PaperFormatPanel.vue`

- [ ] **Step 1: Extend types and API helpers**

```ts
export interface PaperSessionFormatVo {
  templateId?: number | string | null
  override?: PaperFormatConfig | null
  effective?: PaperFormatConfig | null
  defaults?: PaperFormatConfig | null
  mode?: 'school' | 'custom'
  hasCustomDocx?: boolean
  customDocxName?: string | null
  customPatchStyles?: boolean
  customFormat?: PaperFormatConfig | null
}

export async function uploadSessionCustomFormatDocx(
  sessionId: string,
  file: File,
  options?: { format?: PaperFormatConfig; patchStyles?: boolean },
) {
  const fd = new FormData()
  fd.append('file', file)
  if (options?.format) {
    fd.append('format', JSON.stringify(options.format))
  }
  if (options?.patchStyles !== undefined) {
    fd.append('patchStyles', String(options.patchStyles))
  }
  const res = await post<PaperSessionFormatVo>(
    `/api/paper/session/${sessionId}/format/custom-docx`,
    fd,
  ).json()
  return unwrapData<PaperSessionFormatVo>(res)
}

export async function deleteSessionCustomFormatDocx(sessionId: string) {
  const res = await del<PaperSessionFormatVo>(
    `/api/paper/session/${sessionId}/format/custom-docx`,
  ).json()
  return unwrapData<PaperSessionFormatVo>(res)
}
```

Use the project’s existing `post`/`del` that attach auth; if `post` forces JSON Content-Type, use the same multipart pattern as other uploads in AiSchoolWeb (search `FormData` / `multipart`).

- [ ] **Step 2: UI in `PaperFormatPanel`**

1. State: `mode`, `customDocxName`, `customPatchStyles`, `uploading`
2. Template row: button「上传本校模板」→ hidden `input[type=file] accept=.docx` → dialog:
   - show selected filename
   - checkbox「强制用版式参数覆盖模板样式」default checked
   - reuse current fine-tune fields (or pass `buildOverride()` / current effective as `format` body — prefer send **full current effective** from last loaded `vo.effective` as `format` so `custom_format_json` is a full snapshot)
3. On confirm → `uploadSessionCustomFormatDocx` → `applyFromVo`
4. If `mode === 'custom'`: show tip with filename + patch status; buttons「更换文件」「清除自定义」
5. On school `el-select` change: if current mode custom, `ElMessageBox.confirm('将丢弃已上传的本校模板，是否继续？')` then `updateSessionFormat({ templateId, clearOverride: true })`
6. `resetSessionFormat` unchanged (only clears override)
7. Include `mode`/`customDocxName` in dirty snapshot only if needed — template switch already handled separately; fine-tune dirty stays as today

- [ ] **Step 3: Manual E2E**

1. Upload docx + patch on → GET format `mode=custom` → export
2. Fine-tune save / restore default → custom file remains
3. Switch school template → custom cleared
4. Clear custom → default/school path
5. Reject non-docx / oversized (server 400)

---

### Task 8: E2E verification checklist

- [ ] SQL applied on env used by running backend
- [ ] Backend restarted with new code
- [ ] Admin school templates still list/export as before
- [ ] User custom upload path works end-to-end
- [ ] No tenant filter issues on `paper_session` (already excluded)

---

## Spec coverage self-check

| Spec item | Task |
|-----------|------|
| Session columns + SQL | T1–T2 |
| File path layout | T4 |
| Merge Default←custom←override + patch flag | T3, T6 |
| Mutual exclusion upload / school switch / clear / reset | T4–T5, T7 |
| POST/DELETE custom-docx + GET VO fields | T5, T7 |
| Export custom docx + missing file error | T4, T6 |
| 10MB / .docx validation | T4 |
| format omitted → effective snapshot | T4 |
| Frontend drawer UX | T7 |
| Non-goals (my-templates, auto-detect fonts) | not in plan |

## Placeholder / consistency check

- Method names: `isCustomMode`, `applyPatchFlag`, `clearCustomFields`, `saveCustomDocx`, `clearCustomDocx`, `openCustomDocx`, `resolveEffective(PaperSession)` — used consistently.
- Max size **10MB** (spec), not admin’s 20MB.
- Commits optional per project convention.
