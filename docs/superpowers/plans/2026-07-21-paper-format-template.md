# Paper Format Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support multiple school Word layout templates (docx + format JSON) and per-session overrides, so paper export uses merged effective formatting instead of hard-coded Dalian Ocean constants.

**Architecture:** New `paper_format_template` table stores docx path + `format_json`. `paper_session` gains `format_template_id` + `format_override_json`. `PaperFormatConfig` / merge / validate live in Java; `WordExportService` reads `EffectiveFormat`. Admin CRUD + user session format APIs; AiSchoolAdminWeb / AiSchoolWeb UI.

**Tech Stack:** Spring Boot / MyBatis-Plus (ruoyi-chat), Apache POI (`WordExportService`), MySQL, Vue 3 + Element Plus / Ant Design Vue (existing admin & web apps).

**Spec:** `docs/superpowers/specs/2026-07-21-paper-format-template-design.md`

---

## File structure

| Path | Responsibility |
|------|----------------|
| `docs/script/sql/update/updat-paper-format-template.sql` | DDL + default row seed |
| `.../domain/paper/format/PaperFormatConfig.java` | Nested config POJO |
| `.../domain/paper/format/PaperFormatDefaults.java` | Dalian Ocean defaults |
| `.../service/paper/PaperFormatMerger.java` | deepMerge + validate |
| `.../domain/entity/paper/PaperFormatTemplateEntity.java` | Template table entity |
| `.../mapper/paper/PaperFormatTemplateMapper.java` | Mapper |
| `.../service/paper/PaperFormatTemplateService.java` | CRUD, docx storage, resolve effective |
| `.../controller/chat/PaperFormatTemplateController.java` | Admin + options APIs |
| `.../domain/entity/paper/PaperSessionEntity.java` | + format fields |
| `.../controller/chat/PaperController.java` | session format GET/PUT/reset |
| `.../service/paper/WordExportService.java` | Consume EffectiveFormat |
| `.../config/PaperFormatTemplateProperties.java` | upload dir under paper |
| `AiSchoolAdminWeb/.../views/chat/paper-format-template/` | Admin UI |
| `AiSchoolWeb/.../pages/paper-generator/` | Format picker + fine-tune panel |
| Unit tests under `ruoyi-chat/src/test/java/.../paper/format/` | Merger / defaults |

---

### Task 1: `PaperFormatConfig` + defaults + merge (TDD)

**Files:**
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/paper/format/PaperFormatConfig.java`
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/paper/format/PaperFormatDefaults.java`
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperFormatMerger.java`
- Create: `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/paper/PaperFormatMergerTest.java`

- [ ] **Step 1: Write failing tests for merge and validate**

```java
package org.ruoyi.service.paper;

import org.junit.jupiter.api.Test;
import org.ruoyi.domain.paper.format.PaperFormatConfig;
import org.ruoyi.domain.paper.format.PaperFormatDefaults;

import static org.junit.jupiter.api.Assertions.*;

class PaperFormatMergerTest {

    @Test
    void merge_overridesOnlyProvidedFields() {
        PaperFormatConfig base = PaperFormatDefaults.dalianOcean();
        PaperFormatConfig override = new PaperFormatConfig();
        override.getFontSize().setBody(12.0);
        PaperFormatConfig effective = PaperFormatMerger.merge(base, override);
        assertEquals(12.0, effective.getFontSize().getBody());
        assertEquals(PaperFormatDefaults.dalianOcean().getFont().getBodyEastAsia(),
            effective.getFont().getBodyEastAsia());
        assertEquals(30.0, effective.getPage().getMarginTopMm());
    }

    @Test
    void merge_threeLevels_templateThenSession() {
        PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
        PaperFormatConfig template = new PaperFormatConfig();
        template.getFont().setBodyEastAsia("仿宋");
        template.getFontSize().setBody(12.0);
        PaperFormatConfig session = new PaperFormatConfig();
        session.getFontSize().setBody(14.0);
        PaperFormatConfig effective = PaperFormatMerger.merge(def, template, session);
        assertEquals("仿宋", effective.getFont().getBodyEastAsia());
        assertEquals(14.0, effective.getFontSize().getBody());
    }

    @Test
    void validate_rejectsOutOfRangeMargin() {
        PaperFormatConfig c = PaperFormatDefaults.dalianOcean();
        c.getPage().setMarginTopMm(100.0);
        assertThrows(IllegalArgumentException.class, () -> PaperFormatMerger.validate(c));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `ruoyi-modules/ruoyi-chat` or repo root Maven module):

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am test -Dtest=PaperFormatMergerTest
```

Expected: FAIL (classes missing)

- [ ] **Step 3: Implement config POJOs**

`PaperFormatConfig` nested static classes: `Page`, `Font`, `FontSize`, `Paragraph`, `Heading`, `Export` — fields per spec §3 (Double for numbers, String for fonts/align/rule). Use Lombok `@Data`. All fields nullable so override JSON can be sparse.

`PaperFormatDefaults.dalianOcean()` fills:

| Field | Value |
|-------|-------|
| page margins mm | top 30, bottom 25, left 30, right 25 |
| font body/heading/table/code/footer | 宋体 / 黑体 / 宋体+TNR / Consolas / 宋体 |
| fontSize | title 18, h1 16, h2 12, h3 10.5, body 10.5, caption 9, footer 9, toc/reference/abstractLabel 10.5 |
| paragraph | lineSpacingPt 18, rule `exact`, firstLineIndentChars 2, bodyAlign `both` |
| heading | h1Align `center`, h2/h3 `left`, bold flags false except titleBold true; spacingBefore/AfterPt **12** each (from `HEADING_SPACING_LINE=240` twips → 12 pt) |
| export | patchTemplateStyles true, applyPageSetup true |

- [ ] **Step 4: Implement `PaperFormatMerger`**

```java
public final class PaperFormatMerger {
    private PaperFormatMerger() {}

    /** Later layers win; null fields in overlay are skipped. */
    public static PaperFormatConfig merge(PaperFormatConfig... layers) { /* deep copy + field-wise */ }

    public static void validate(PaperFormatConfig c) {
        // margins 5–50; fontSize 6–72; lineSpacingPt 10–40; multiple 1–3; fonts non-blank when present
    }

    public static PaperFormatConfig parseJson(String json) { /* Jackson ObjectMapper; blank → empty config */ }

    public static String toJson(PaperFormatConfig c) { /* ... */ }
}
```

Use existing project Jackson (`ObjectMapper` bean or `JsonUtils` if present). Deep merge: for each nested object, copy non-null properties from overlay onto a clone of base.

- [ ] **Step 5: Run tests — expect PASS**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am test -Dtest=PaperFormatMergerTest
```

- [ ] **Step 6: Commit** (only if user asked to commit)

```bash
git add ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/paper/format \
  ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperFormatMerger.java \
  ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/paper/PaperFormatMergerTest.java
git commit -m "feat(paper): add PaperFormatConfig merge and defaults"
```

---

### Task 2: SQL migration

**Files:**
- Create: `docs/script/sql/update/updat-paper-format-template.sql`

- [ ] **Step 1: Write DDL**

```sql
-- paper format templates + session columns
CREATE TABLE IF NOT EXISTS paper_format_template (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name            VARCHAR(100) NOT NULL COMMENT '模板名称',
  school_name     VARCHAR(100) DEFAULT NULL COMMENT '学校备注',
  is_default      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否全站默认 0/1',
  status          CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0停用 1启用',
  docx_path       VARCHAR(500) DEFAULT NULL COMMENT '相对上传目录路径',
  docx_original_name VARCHAR(255) DEFAULT NULL,
  docx_size       BIGINT       DEFAULT NULL,
  format_json     MEDIUMTEXT   NOT NULL COMMENT 'PaperFormatConfig JSON',
  style_mapping_json VARCHAR(2000) DEFAULT NULL COMMENT '样式ID缓存可选',
  remark          VARCHAR(500) DEFAULT NULL,
  create_by       VARCHAR(64)  DEFAULT NULL,
  create_time     DATETIME     DEFAULT NULL,
  update_by       VARCHAR(64)  DEFAULT NULL,
  update_time     DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文Word排版模板';

ALTER TABLE paper_session
  ADD COLUMN format_template_id BIGINT NULL COMMENT '排版模板ID' AFTER education_level,
  ADD COLUMN format_override_json MEDIUMTEXT NULL COMMENT '会话版式覆盖JSON' AFTER format_template_id;
```

- [ ] **Step 2: Seed default template**

Insert one row `name='大连海洋大学'`, `is_default=1`, `status='1'`, `format_json` = JSON produced by `PaperFormatDefaults.dalianOcean()` (paste canonical JSON in SQL or leave `format_json='{}'` and let app fill on first boot — **prefer paste full JSON in SQL** so DB is self-contained).

`docx_path` can be NULL initially; Task 4 copies classpath docx into upload dir and updates path, OR seed path `paper/format-templates/1/thesis-template.docx` and document that boot copies file.

- [ ] **Step 3: Apply on target DB** (ops step)

```bash
# example
mysql -h ... -P 3307 -u root -p ai_sc < docs/script/sql/update/updat-paper-format-template.sql
```

- [ ] **Step 4: Commit SQL file** (if user asked)

---

### Task 3: Entity + Mapper + session fields

**Files:**
- Create: `.../domain/entity/paper/PaperFormatTemplateEntity.java`
- Create: `.../mapper/paper/PaperFormatTemplateMapper.java`
- Modify: `.../domain/entity/paper/PaperSessionEntity.java`
- Modify: `.../domain/paper/PaperSession.java` (domain DTO if persisted fields round-trip)
- Modify: `PaperSessionPersistence` / `PaperSessionStore` to load/save new columns

- [ ] **Step 1: Entity**

```java
@Data
@TableName("paper_format_template")
public class PaperFormatTemplateEntity {
    @TableId(value = "id")
    private Long id;
    private String name;
    private String schoolName;
    private Integer isDefault;
    private String status;
    private String docxPath;
    private String docxOriginalName;
    private Long docxSize;
    private String formatJson;
    private String styleMappingJson;
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
```

- [ ] **Step 2: Mapper**

```java
@Mapper
public interface PaperFormatTemplateMapper extends BaseMapper<PaperFormatTemplateEntity> {}
```

- [ ] **Step 3: Extend `PaperSessionEntity`**

```java
private Long formatTemplateId;
private String formatOverrideJson;
```

Update persistence layer wherever session rows are inserted/updated/selected so columns are not dropped.

- [ ] **Step 4: Smoke compile**

```bash
mvn -pl ruoyi-modules/ruoyi-chat -am compile -DskipTests
```

---

### Task 4: `PaperFormatTemplateService`

**Files:**
- Create: `.../config/PaperFormatTemplateProperties.java` (`paper.format-template.local-dir`, default `${sys.upload.path}/paper/format-templates`)
- Create: `.../service/paper/PaperFormatTemplateService.java`
- Modify: boot or service `@PostConstruct` to ensure default docx exists

- [ ] **Step 1: Service API (minimal)**

```java
public interface /* or class */ PaperFormatTemplateService {
    List<PaperFormatTemplateEntity> listEnabled();
    List<PaperFormatTemplateEntity> listAll();
    PaperFormatTemplateEntity getById(Long id);
    Long create(PaperFormatTemplateEntity meta, PaperFormatConfig format);
    void updateMetaAndFormat(Long id, PaperFormatTemplateEntity meta, PaperFormatConfig format);
    void uploadDocx(Long id, MultipartFile file);
    void setDefault(Long id);
    void setStatus(Long id, String status); // cannot delete if referenced; disable ok
    InputStream openDocx(Long id); // null id → default template; missing file → classpath fallback
    PaperFormatConfig resolveEffective(Long templateId, String overrideJson);
    boolean isReferencedBySession(Long id);
}
```

`resolveEffective`:

```java
PaperFormatConfig def = PaperFormatDefaults.dalianOcean();
PaperFormatConfig tpl = PaperFormatDefaults.dalianOcean();
if (templateId != null) {
    PaperFormatTemplateEntity e = getById(templateId);
    if (e != null) {
        tpl = PaperFormatMerger.merge(def, PaperFormatMerger.parseJson(e.getFormatJson()));
    }
} else {
    // optional: load is_default=1 row's format_json
    PaperFormatTemplateEntity d = findDefault();
    if (d != null) {
        tpl = PaperFormatMerger.merge(def, PaperFormatMerger.parseJson(d.getFormatJson()));
    }
}
PaperFormatConfig session = PaperFormatMerger.parseJson(overrideJson);
PaperFormatConfig effective = PaperFormatMerger.merge(tpl.getClass()/* wait */, /* use merge(def, templateLayer, session) */);
PaperFormatMerger.validate(effective);
return effective;
```

Correct call: `merge(def, templateOnlyOverlay, sessionOverlay)` where templateOnlyOverlay = parse(template.format_json) without re-applying defaults twice — i.e. `merge(def, parse(tplJson), parse(overrideJson))`.

- [ ] **Step 2: Docx storage**

Save to `{localDir}/{id}/thesis-template.docx`. On create default id=1, copy from `classpath:paper/thesis-template.docx` if file missing.

- [ ] **Step 3: Unit/integration smoke** — resolveEffective with nulls returns Dalian defaults sizes.

---

### Task 5: Admin + options HTTP APIs

**Files:**
- Create: `.../controller/chat/PaperFormatTemplateController.java`
- Create request DTOs under `.../domain/dto/request/` as needed
- Wire permissions consistent with existing `PaperTemplateController`

- [ ] **Step 1: Endpoints** (match spec §5.1 + options)

| Method | Path |
|--------|------|
| GET | `/api/paper/format-template/list` |
| GET | `/api/paper/format-template/{id}` |
| POST | `/api/paper/format-template` |
| PUT | `/api/paper/format-template/{id}` |
| POST | `/api/paper/format-template/{id}/docx` |
| POST | `/api/paper/format-template/{id}/set-default` |
| PUT | `/api/paper/format-template/{id}/status` |
| GET | `/api/paper/format-template/{id}/download` |
| GET | `/api/paper/format-template/options` | (no admin perm; login user) |

Validate format_json via `PaperFormatMerger.validate(merge(defaults, parsed))` before save.

- [ ] **Step 2: Manual API smoke with curl/Swagger**

---

### Task 6: Session format APIs

**Files:**
- Modify: `PaperController.java`
- Modify: session update persistence

- [ ] **Step 1: Endpoints**

```text
GET  /api/paper/session/{sessionId}/format
PUT  /api/paper/session/{sessionId}/format
POST /api/paper/session/{sessionId}/format/reset
```

PUT body:

```json
{
  "templateId": 1,
  "override": { "fontSize": { "body": 12 } },
  "clearOverride": false
}
```

Rules:
- If `templateId` changes and `clearOverride` is true (default **true** when templateId changes): set override null
- Persist `format_template_id` / `format_override_json`
- GET returns `{ templateId, override, effective, defaults }`

- [ ] **Step 2: Ownership check** — same as other paper session APIs (userId)

---

### Task 7: Refactor `WordExportService` to use EffectiveFormat

**Files:**
- Modify: `WordExportService.java` (large)
- Inject: `PaperFormatTemplateService`, `PaperSessionStore`/`Persistence`

- [ ] **Step 1: ThreadLocal or method-scoped `PaperFormatConfig effective`**

At start of `export(sessionId)`:

```java
PaperSessionEntity session = ...;
PaperFormatConfig effective = paperFormatTemplateService.resolveEffective(
    session.getFormatTemplateId(), session.getFormatOverrideJson());
// hold in a field ThreadLocal or pass through private methods
```

Open docx via `paperFormatTemplateService.openDocx(session.getFormatTemplateId())` instead of only global `PaperTemplateService` (fallback to old service if openDocx fails).

- [ ] **Step 2: Replace page setup**

Rename `applyDalianOceanPageSetup` → `applyPageSetup(doc, effective)` reading `effective.getPage()` margins (mm → twips as today).

- [ ] **Step 3: Replace font helpers**

Change `applyFont(run, family, size)` call sites to use:
- body → `font.bodyEastAsia` + `fontSize.body` (ascii via existing eastAsia/ascii split logic using `bodyAscii`)
- heading → `headingEastAsia` + heading sizes
- table/code/footer/caption from config

Remove reliance on `private static final String FONT_*` for runtime (keep defaults only inside `PaperFormatDefaults`).

- [ ] **Step 4: `patchTemplateStyles(doc, mapping, effective)`**

Use effective font sizes/families when `effective.getExport().isPatchTemplateStyles()`.

- [ ] **Step 5: Body paragraph spacing**

`lineSpacingRule` `exact` → `LineSpacingRule.EXACT` with `lineSpacingPt`; `auto` → AUTO with multiple.

`firstLineIndentChars` → firstLineChars = chars * 100 (current code uses 200 for 2 chars).

- [ ] **Step 6: Heading align/bold/spacing from `effective.getHeading()`**

- [ ] **Step 7: Regression export**

Export one known session twice (null template vs default template id) — visually/binary spot-check margins and 宋体/黑体 unchanged for default.

---

### Task 8: Admin Web UI

**Files:**
- Create: `AiSchoolAdminWeb/apps/web-antd/src/api/chat/paperFormatTemplate/`
- Create: `AiSchoolAdminWeb/apps/web-antd/src/views/chat/paper-format-template/index.vue` (+ edit drawer/page)
- Modify: router/menu SQL or local route config for「论文排版模板」
- Deprecate or redirect old `paper-template/index.vue` to new page (or keep TOC-only note)

- [ ] **Step 1: API module** mirroring Task 5 paths

- [ ] **Step 2: List page** — name, school, default badge, status, actions (edit/upload/set default/disable)

- [ ] **Step 3: Edit form** — tabs: 页面 / 字体 / 字号 / 段落标题 / 高级 JSON；save PUT; docx upload separate

- [ ] **Step 4: Manual QA** create second template with body font 仿宋, download docx optional

---

### Task 9: User Web UI (AiSchoolWeb)

**Files:**
- Modify: `AiSchoolWeb/src/api/paper/index.ts` — format APIs + types
- Modify: `AiSchoolWeb/src/pages/paper-generator/PaperGenerator.vue` (or extract `PaperFormatPanel.vue`)

- [ ] **Step 1: API**

```ts
export function getPaperFormatOptions() { ... }
export function getSessionFormat(sessionId: string) { ... }
export function updateSessionFormat(sessionId: string, body: {...}) { ... }
export function resetSessionFormat(sessionId: string) { ... }
```

- [ ] **Step 2: UI**

- Select template (el-select from options)
- Collapse「排版微调」with key fields (at least fonts + font sizes + margins + line spacing); save on blur/button via PUT
- Button「恢复模板默认」→ reset
- On template change: confirm dialog then PUT with `clearOverride: true`

- [ ] **Step 3: Export still uses existing download button** — ensure format saved before export (auto-save on change or warn)

---

### Task 10: End-to-end verification

- [ ] **Step 1: Run SQL** on ai_sc

- [ ] **Step 2: Boot backend**, open admin, confirm default template present

- [ ] **Step 3: Create template B** with `fontSize.body=14`, `marginLeftMm=20`

- [ ] **Step 4: User session** bind B, export Word — body ~14pt, left margin ~20mm

- [ ] **Step 5: Override** body to 12, export — 12pt; reset — back to 14

- [ ] **Step 6: Old session** with null template_id — still exports with Dalian defaults

- [ ] **Step 7: Update design status** to「已定稿」in spec header if all pass

---

## Spec coverage check

| Spec item | Task |
|-----------|------|
| PaperFormatConfig fields | 1 |
| Merge Default←Template←Session | 1, 4 |
| Validate ranges | 1, 5 |
| `paper_format_template` table | 2 |
| Session columns | 2, 3 |
| Docx + format_json | 4, 5 |
| Admin APIs | 5 |
| User format APIs | 6 |
| WordExport reads config | 7 |
| Admin UI | 8 |
| User UI | 9 |
| Default Dalian seed | 2, 4 |
| Disable vs delete policy | 4, 5 |
| No cover designer / freeze | out of scope |

## Notes for agents

- Do **not** git commit unless the user explicitly asks.
- Keep TOC「默认模板大纲」(`useDefaultTemplate`) separate from format templates.
- Prefer small PRs: Tasks 1–7 backend first, then 8–9 frontends.
