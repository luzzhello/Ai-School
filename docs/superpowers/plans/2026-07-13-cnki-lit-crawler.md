# CNKI Literature Crawler & Lit DB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone CNKI metadata crawler for software-engineering papers, import into MySQL `lit_paper`, and replace LLM-fabricated paper references with DB retrieval.

**Architecture:** Python tool under `tools/cnki-crawler/` crawls CNKI with personal Cookie (rate-limited, checkpointed) into JSONL; import script upserts into MySQL; Java `PaperReferenceService` queries `lit_paper` via FULLTEXT and streams existing SSE events—no ChatModel fallback.

**Tech Stack:** Python 3.11+ (httpx, beautifulsoup4, pyyaml, pymysql), pytest; MySQL 8 ngram FULLTEXT; Java Spring Boot / MyBatis-Plus (existing ruoyi-chat paper module).

---

## File structure

| Path | Responsibility |
|------|----------------|
| `tools/cnki-crawler/README.md` | Cookie setup, rate limits, compliance, CLI usage |
| `tools/cnki-crawler/requirements.txt` | Python deps |
| `tools/cnki-crawler/config.example.yaml` | Cookie, delays, year range, daily cap |
| `tools/cnki-crawler/keywords/se.txt` | SE seed keywords |
| `tools/cnki-crawler/.gitignore` | `data/`, `config.yaml`, cookies |
| `tools/cnki-crawler/src/models.py` | PaperRecord dataclass / dict schema |
| `tools/cnki-crawler/src/gbt7714.py` | Local GB/T 7714 formatter |
| `tools/cnki-crawler/src/parse.py` | List/detail HTML→fields parsers |
| `tools/cnki-crawler/src/checkpoint.py` | Resume state |
| `tools/cnki-crawler/src/http_client.py` | Cookie session, delay, captcha detect |
| `tools/cnki-crawler/src/crawl_search.py` | Keyword search pagination |
| `tools/cnki-crawler/src/crawl_detail.py` | Detail enrichment |
| `tools/cnki-crawler/src/export_jsonl.py` | Append JSONL |
| `tools/cnki-crawler/src/__main__.py` | CLI entry |
| `tools/cnki-crawler/scripts/import_to_mysql.py` | Idempotent upsert |
| `tools/cnki-crawler/tests/fixtures/*.html` | Saved CNKI HTML snippets |
| `tools/cnki-crawler/tests/test_*.py` | Unit tests |
| `docs/script/sql/update/updat-lit-paper.sql` | `lit_paper` / `lit_paper_ref` DDL |
| `.../domain/entity/lit/LitPaperEntity.java` | MyBatis entity |
| `.../mapper/lit/LitPaperMapper.java` | Mapper + FULLTEXT search |
| `.../service/paper/LitPaperSearchService.java` | Search + map to `Reference` |
| `.../service/paper/PaperReferenceService.java` | Swap LLM → lit search |
| `.../config/LitPaperProperties.java` | `recent-years`, default limits |
| `AiSchoolWeb/.../AiReferenceDialog.vue` | Copy: 「从文献库检索」(optional small UI) |

---

### Task 1: Scaffold Python crawler project

**Files:**
- Create: `tools/cnki-crawler/requirements.txt`
- Create: `tools/cnki-crawler/config.example.yaml`
- Create: `tools/cnki-crawler/keywords/se.txt`
- Create: `tools/cnki-crawler/.gitignore`
- Create: `tools/cnki-crawler/src/__init__.py`
- Create: `tools/cnki-crawler/src/models.py`
- Create: `tools/cnki-crawler/README.md` (stub; expand in Task 6)

- [ ] **Step 1: Create directories and dependency file**

```text
tools/cnki-crawler/
  requirements.txt
  config.example.yaml
  keywords/se.txt
  .gitignore
  src/__init__.py
  src/models.py
  data/.gitkeep
  tests/__init__.py
  README.md
```

`requirements.txt`:

```text
httpx>=0.27.0
beautifulsoup4>=4.12.0
lxml>=5.0.0
pyyaml>=6.0
pymysql>=1.1.0
pytest>=8.0.0
```

- [ ] **Step 2: Write `config.example.yaml`**

```yaml
cookie: "REPLACE_WITH_BROWSER_COOKIE"
user_agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
list_delay_sec: 2.0
detail_delay_sec: 4.0
delay_jitter_sec: 1.5
daily_detail_limit: 800
max_per_keyword: 200
from_year: 2018
to_year: 2026
doc_types:
  - journal
  - conference
  - master
  - phd
output_jsonl: data/papers.jsonl
checkpoint_path: data/checkpoint.json
```

- [ ] **Step 3: Write seed keywords `keywords/se.txt`** (one per line)

```text
软件工程
需求分析
软件测试
软件架构
微服务
DevOps
敏捷开发
软件质量
代码缺陷
软件过程
UML
面向对象
软件维护
持续集成
领域驱动设计
```

- [ ] **Step 4: Write `src/models.py`**

```python
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class PaperRecord:
    cnki_id: str | None = None
    doi: str | None = None
    title: str = ""
    authors: str | None = None
    organs: str | None = None
    abstract_text: str | None = None
    keywords: str | None = None
    source: str | None = None
    year: int | None = None
    doc_type: str | None = None  # J/D/C/M
    cite_count: int | None = None
    lit_source: str = "CNKI"
    citation_gbt: str | None = None
    detail_url: str | None = None
    title_hash: str | None = None
    crawl_keyword: str | None = None
    status: str = "active"  # active / incomplete
    references: list[str] = field(default_factory=list)

    def to_json_dict(self) -> dict[str, Any]:
        return asdict(self)
```

- [ ] **Step 5: Write `.gitignore`**

```text
data/*.jsonl
data/checkpoint.json
data/logs/
config.yaml
__pycache__/
.pytest_cache/
.venv/
```

- [ ] **Step 6: Commit**

```bash
git add tools/cnki-crawler
git commit -m "chore: scaffold CNKI crawler project layout"
```

---

### Task 2: GB/T 7714 formatter (TDD)

**Files:**
- Create: `tools/cnki-crawler/src/gbt7714.py`
- Create: `tools/cnki-crawler/tests/test_gbt7714.py`

- [ ] **Step 1: Write failing tests**

```python
from src.gbt7714 import format_gbt7714
from src.models import PaperRecord


def test_journal_citation():
    p = PaperRecord(
        authors="张三;李四",
        title="面向微服务的软件架构研究",
        doc_type="J",
        source="软件学报",
        year=2023,
        doi="10.1234/abc",
    )
    cite = format_gbt7714(p)
    assert "张三" in cite
    assert "[J]" in cite
    assert "软件学报" in cite
    assert "2023" in cite
    assert "DOI:10.1234/abc" in cite


def test_thesis_uses_d_tag():
    p = PaperRecord(authors="王五", title="某某研究", doc_type="D", source="某某大学", year=2022)
    assert "[D]" in format_gbt7714(p)
```

- [ ] **Step 2: Run tests — expect fail**

```bash
cd tools/cnki-crawler
python -m pytest tests/test_gbt7714.py -v
```

Expected: `ModuleNotFoundError` or import error for `gbt7714`.

- [ ] **Step 3: Implement `src/gbt7714.py`**

```python
from __future__ import annotations

from src.models import PaperRecord

_TAG = {"J": "[J]", "D": "[D]", "C": "[C]", "M": "[M]"}


def format_gbt7714(paper: PaperRecord) -> str:
    tag = _TAG.get((paper.doc_type or "J").upper(), "[J]")
    authors = (paper.authors or "").replace(";", ",").strip()
    title = (paper.title or "").strip()
    source = (paper.source or "").strip()
    parts: list[str] = []
    if authors:
        parts.append(f"{authors}.")
    if title:
        parts.append(f"{title}{tag}")
    else:
        parts.append(tag)
    body = "".join(parts)
    if source:
        body += source
    if paper.year:
        body += f",{paper.year}"
    body += "."
    if paper.doi:
        body += f"DOI:{paper.doi.strip()}."
    return body
```

- [ ] **Step 4: Run tests — expect pass**

```bash
python -m pytest tests/test_gbt7714.py -v
```

- [ ] **Step 5: Commit**

```bash
git add tools/cnki-crawler/src/gbt7714.py tools/cnki-crawler/tests/test_gbt7714.py
git commit -m "feat(cnki-crawler): add GB/T 7714 citation formatter"
```

---

### Task 3: HTML parsers with fixtures (TDD)

**Files:**
- Create: `tools/cnki-crawler/src/parse.py`
- Create: `tools/cnki-crawler/src/normalize.py` (title_hash)
- Create: `tools/cnki-crawler/tests/fixtures/list_sample.html`
- Create: `tools/cnki-crawler/tests/fixtures/detail_sample.html`
- Create: `tools/cnki-crawler/tests/test_parse.py`
- Create: `tools/cnki-crawler/tests/test_normalize.py`

CNKI DOM changes often. Use **stable fixture HTML** shaped like current kns result/detail pages; when live crawl breaks, update fixtures + selectors together.

- [ ] **Step 1: Write `normalize.py` + test**

```python
# src/normalize.py
import hashlib
import re


def normalize_title(title: str) -> str:
    t = (title or "").strip().lower()
    t = re.sub(r"\s+", "", t)
    t = re.sub(r"[^\w\u4e00-\u9fff]+", "", t, flags=re.UNICODE)
    return t


def title_hash(title: str) -> str:
    return hashlib.sha256(normalize_title(title).encode("utf-8")).hexdigest()
```

```python
# tests/test_normalize.py
from src.normalize import title_hash


def test_title_hash_stable():
    a = title_hash("面向微服务的 软件架构研究！")
    b = title_hash("面向微服务的软件架构研究")
    assert a == b
```

- [ ] **Step 2: Add minimal fixtures**

`list_sample.html` — at least one result row with title link, author, source, year, cite count, and a `data-filename` / href containing a fake cnki id.

`detail_sample.html` — blocks labeled 摘要 / 关键词 / DOI / 作者单位 / 参考文献, plus optional citation text.

(Engineer: paste anonymized real HTML snippets when available; until then craft minimal HTML matching the selectors written in Step 3.)

- [ ] **Step 3: Write failing parse tests**

```python
from pathlib import Path
from src.parse import parse_list_html, parse_detail_html

FIX = Path(__file__).parent / "fixtures"


def test_parse_list_extracts_rows():
    html = (FIX / "list_sample.html").read_text(encoding="utf-8")
    rows = parse_list_html(html)
    assert len(rows) >= 1
    assert rows[0]["title"]
    assert rows[0]["detail_url"]


def test_parse_detail_extracts_abstract():
    html = (FIX / "detail_sample.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail.get("abstract_text")
    assert isinstance(detail.get("references"), list)
```

- [ ] **Step 4: Implement `parse.py`**

Implement `parse_list_html(html) -> list[dict]` and `parse_detail_html(html) -> dict` with BeautifulSoup. Centralize CSS/XPath selectors as module-level constants so CNKI layout changes are one-file edits.

Map resource type strings to `doc_type`: 期刊→J, 硕士/博士→D, 会议→C, 图书→M.

Detect captcha/login pages: if title/body contains `验证码` or login form markers, raise `CaptchaOrLoginError`.

- [ ] **Step 5: Run pytest**

```bash
cd tools/cnki-crawler
python -m pytest tests/test_normalize.py tests/test_parse.py -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add tools/cnki-crawler/src/parse.py tools/cnki-crawler/src/normalize.py tools/cnki-crawler/tests
git commit -m "feat(cnki-crawler): parse CNKI list/detail HTML fixtures"
```

---

### Task 4: HTTP client, checkpoint, JSONL export

**Files:**
- Create: `tools/cnki-crawler/src/http_client.py`
- Create: `tools/cnki-crawler/src/checkpoint.py`
- Create: `tools/cnki-crawler/src/export_jsonl.py`
- Create: `tools/cnki-crawler/tests/test_checkpoint.py`
- Create: `tools/cnki-crawler/tests/test_export_jsonl.py`

- [ ] **Step 1: Tests for checkpoint + export**

```python
# tests/test_checkpoint.py
from src.checkpoint import Checkpoint


def test_checkpoint_roundtrip(tmp_path):
    path = tmp_path / "cp.json"
    cp = Checkpoint(path)
    cp.mark_url_done("https://example.com/a")
    cp.set_keyword_page("软件工程", 3)
    cp.save()
    cp2 = Checkpoint(path)
    cp2.load()
    assert cp2.is_url_done("https://example.com/a")
    assert cp2.get_keyword_page("软件工程") == 3
```

```python
# tests/test_export_jsonl.py
from src.export_jsonl import append_record
from src.models import PaperRecord


def test_append_jsonl(tmp_path):
    path = tmp_path / "out.jsonl"
    append_record(path, PaperRecord(title="t1", cnki_id="x"))
    append_record(path, PaperRecord(title="t2", cnki_id="y"))
    lines = path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 2
```

- [ ] **Step 2: Implement checkpoint / export / http_client**

`http_client.py` responsibilities:
- Load cookie string into `httpx.Client` headers
- `get(url)` with configured delay + jitter
- On HTTP 429 / frequent failures: exponential backoff (e.g. 5s, 15s, 45s), then raise `RateLimitError`
- After response: if `parse` detects captcha/login → raise `CaptchaOrLoginError`
- Track `details_today` counter; refuse when `>= daily_detail_limit`

- [ ] **Step 3: pytest pass + commit**

```bash
python -m pytest tests/test_checkpoint.py tests/test_export_jsonl.py -v
git add tools/cnki-crawler/src tools/cnki-crawler/tests
git commit -m "feat(cnki-crawler): add HTTP client, checkpoint, JSONL export"
```

---

### Task 5: Search + detail crawl orchestration + CLI

**Files:**
- Create: `tools/cnki-crawler/src/crawl_search.py`
- Create: `tools/cnki-crawler/src/crawl_detail.py`
- Create: `tools/cnki-crawler/src/__main__.py`
- Create: `tools/cnki-crawler/src/config_loader.py`

- [ ] **Step 1: Implement config loader**

```python
# loads config.yaml (copy from example); raises if cookie still REPLACE_WITH...
```

- [ ] **Step 2: Implement `crawl_search.py`**

For each keyword in `keywords/se.txt`:
1. Resume from checkpoint page
2. Request CNKI search list URL/API (kns8s-style or grid HTML—document the chosen endpoint in README once verified against live site)
3. Parse rows; skip URLs already in checkpoint
4. Yield list item dicts; update checkpoint page

Stop a keyword when `max_per_keyword` reached or no more pages.

- [ ] **Step 3: Implement `crawl_detail.py`**

For each list item:
1. Delay via http_client
2. Fetch detail HTML
3. Merge detail fields into `PaperRecord`
4. If `citation_gbt` missing → `format_gbt7714`
5. Set `title_hash`; if abstract/keywords missing → `status=incomplete`
6. `append_record` JSONL; mark URL done

On `CaptchaOrLoginError` / `RateLimitError`: save checkpoint, log clear message, exit non-zero.

- [ ] **Step 4: CLI `__main__.py`**

```bash
python -m src --config config.yaml crawl
python -m src --config config.yaml crawl --keyword 软件测试 --max 20
```

- [ ] **Step 5: Dry-run without network (unit)**

Add `tests/test_crawl_merge.py`: given a list row dict + detail dict, assert merged `PaperRecord` fields and status.

- [ ] **Step 6: Commit**

```bash
git add tools/cnki-crawler
git commit -m "feat(cnki-crawler): wire search/detail crawl CLI with rate limits"
```

**Manual gate (not automated):** with real `config.yaml` Cookie, run `--max 5` for one keyword; confirm JSONL rows look correct. Do **not** commit Cookie or JSONL data.

---

### Task 6: Crawler README (ops + compliance)

**Files:**
- Modify: `tools/cnki-crawler/README.md`

- [ ] **Step 1: Document**

Must include:
1. Copy `config.example.yaml` → `config.yaml`, paste Cookie from browser DevTools
2. `python -m venv .venv` + `pip install -r requirements.txt`
3. Rate-limit defaults and personal-account warning
4. Checkpoint resume behavior
5. “Metadata only / no PDF” compliance note
6. How to update selectors when CNKI DOM changes

- [ ] **Step 2: Commit**

```bash
git add tools/cnki-crawler/README.md
git commit -m "docs(cnki-crawler): add setup, rate-limit, and compliance guide"
```

---

### Task 7: MySQL DDL for lit_paper

**Files:**
- Create: `docs/script/sql/update/updat-lit-paper.sql`

- [ ] **Step 1: Write DDL**

```sql
-- 文献库：知网等来源元数据（供论文生成检索）
-- MySQL 8+；中文 FULLTEXT 需 ngram（my.cnf: ngram_token_size=2）

CREATE TABLE IF NOT EXISTS `lit_paper` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `cnki_id`        VARCHAR(64)   DEFAULT NULL COMMENT '知网文献ID',
  `doi`            VARCHAR(200)  DEFAULT NULL COMMENT 'DOI',
  `title`          VARCHAR(500)  NOT NULL COMMENT '标题',
  `authors`        VARCHAR(500)  DEFAULT NULL COMMENT '作者',
  `organs`         VARCHAR(1000) DEFAULT NULL COMMENT '机构',
  `abstract_text`  MEDIUMTEXT    DEFAULT NULL COMMENT '摘要',
  `keywords`       VARCHAR(500)  DEFAULT NULL COMMENT '关键词',
  `source`         VARCHAR(300)  DEFAULT NULL COMMENT '期刊/会议',
  `year`           INT           DEFAULT NULL COMMENT '年份',
  `doc_type`       VARCHAR(10)   DEFAULT NULL COMMENT 'J/D/C/M',
  `cite_count`     INT           DEFAULT 0 COMMENT '被引次数',
  `lit_source`     VARCHAR(32)   NOT NULL DEFAULT 'CNKI' COMMENT '文献来源',
  `citation_gbt`   TEXT          DEFAULT NULL COMMENT 'GB/T 7714',
  `detail_url`     VARCHAR(1000) DEFAULT NULL COMMENT '详情URL',
  `title_hash`     CHAR(64)      DEFAULT NULL COMMENT '规范化标题哈希',
  `crawl_keyword`  VARCHAR(200)  DEFAULT NULL COMMENT '首次检索词',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT 'active/incomplete',
  `crawled_at`     DATETIME      DEFAULT NULL,
  `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lit_paper_cnki_id` (`cnki_id`),
  UNIQUE KEY `uk_lit_paper_doi` (`doi`),
  KEY `idx_lit_paper_year` (`year`),
  KEY `idx_lit_paper_cite` (`cite_count`),
  KEY `idx_lit_paper_type` (`doc_type`),
  KEY `idx_lit_paper_title_hash_year` (`title_hash`, `year`),
  KEY `idx_lit_paper_status` (`status`),
  FULLTEXT KEY `ft_lit_paper` (`title`, `keywords`, `abstract_text`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献库主表';

CREATE TABLE IF NOT EXISTS `lit_paper_ref` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`   BIGINT       NOT NULL,
  `ref_index`  INT          NOT NULL,
  `raw_text`   TEXT         NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lit_paper_ref_paper` (`paper_id`, `ref_index`),
  CONSTRAINT `fk_lit_paper_ref_paper` FOREIGN KEY (`paper_id`) REFERENCES `lit_paper` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献参考文献列表';
```

Note: MySQL UNIQUE allows multiple NULLs for `cnki_id`/`doi`—acceptable. Empty string DOI should be normalized to NULL in import.

- [ ] **Step 2: Commit**

```bash
git add docs/script/sql/update/updat-lit-paper.sql
git commit -m "feat(sql): add lit_paper and lit_paper_ref schema"
```

---

### Task 8: JSONL → MySQL import script (idempotent)

**Files:**
- Create: `tools/cnki-crawler/scripts/import_to_mysql.py`
- Create: `tools/cnki-crawler/tests/test_import_dedupe.py`

- [ ] **Step 1: Write dedupe unit test (pure functions)**

Extract lookup key logic into `scripts/import_logic.py` (or `src/import_dedupe.py`) so pytest can run without DB:

```python
def resolve_match(existing_by_cnki, existing_by_doi, existing_by_hash_year, row) -> str | None:
    """Return existing id key or None for insert."""
```

Test order: cnki_id → doi → (title_hash, year).

- [ ] **Step 2: Implement importer CLI**

```bash
python scripts/import_to_mysql.py \
  --host 127.0.0.1 --port 3306 --user root --password xxx --database ry-vue \
  --jsonl data/papers.jsonl
```

For each JSONL line:
1. Normalize empty doi/cnki_id → NULL
2. SELECT by dedupe keys
3. INSERT or UPDATE `lit_paper`
4. DELETE + re-INSERT `lit_paper_ref` rows when references present
5. Commit in batches (e.g. 100)

- [ ] **Step 3: pytest + commit**

```bash
python -m pytest tests/test_import_dedupe.py -v
git add tools/cnki-crawler/scripts tools/cnki-crawler/src tools/cnki-crawler/tests
git commit -m "feat(cnki-crawler): idempotent JSONL import into lit_paper"
```

---

### Task 9: Java LitPaper entity, mapper, search service

**Files:**
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/entity/lit/LitPaperEntity.java`
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/mapper/lit/LitPaperMapper.java`
- Create: `ruoyi-modules/ruoyi-chat/src/main/resources/mapper/lit/LitPaperMapper.xml` (if XML preferred; else `@Select`)
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/config/LitPaperProperties.java`
- Create: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/LitPaperSearchService.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml` (bind `paper.lit.*`)

Follow existing paper entity style (`PaperReferenceEntity`).

- [ ] **Step 1: Add `LitPaperProperties`**

```java
@Data
@Component
@ConfigurationProperties(prefix = "paper.lit")
public class LitPaperProperties {
    /** 近 N 年，默认 8 */
    private int recentYears = 8;
}
```

```yaml
# application.yml
paper:
  lit:
    recent-years: 8
```

- [ ] **Step 2: Entity + Mapper search method**

SQL sketch:

```sql
SELECT *
FROM lit_paper
WHERE status = 'active'
  AND MATCH(title, keywords, abstract_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
  AND (#{fromYear} IS NULL OR year >= #{fromYear})
ORDER BY cite_count DESC, year DESC
LIMIT #{limit}
```

If FULLTEXT returns too few rows, optional fallback (same task):

```sql
AND (title LIKE CONCAT('%', #{keyword}, '%')
  OR keywords LIKE CONCAT('%', #{keyword}, '%'))
```

Document fallback in class Javadoc; keep single service method `search(String keyword, String language, int limit)`.

- [ ] **Step 3: `LitPaperSearchService` maps to `Reference`**

```java
public List<Reference> search(String keyword, String language, int limit) {
    int fromYear = LocalDate.now().getYear() - litPaperProperties.getRecentYears();
    List<LitPaperEntity> rows = litPaperMapper.search(keyword, fromYear, limit * 3); // over-fetch then filter language
    // filter language via CJK on title like PaperReferenceService.detectLanguage
    // map: author<-authors, citation<-citation_gbt (or format if blank), abstractText<-abstract_text
    // re-index 1..n, truncate to limit
}
```

- [ ] **Step 4: Commit**

```bash
git add ruoyi-modules/ruoyi-chat ruoyi-admin/src/main/resources/application.yml
git commit -m "feat(paper): add lit_paper search service for real references"
```

---

### Task 10: Replace LLM in `PaperReferenceService`

**Files:**
- Modify: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperReferenceService.java`

- [ ] **Step 1: Rewrite `runGenerate`**

Behavior contract:
1. `sendEvent(start)`
2. Call `litPaperSearchService.search(keyword, language, count)`
3. If empty → `sendError("文献库未匹配到相关文献，请更换关键词或先导入文献")` — **no ChatModel**
4. Else `paperSessionStore.update(... setReferences ...)`
5. SSE each `type: reference`
6. `done` with total; if `total < count`, still success (optionally include `message` field: `文献库匹配到 N 篇`)

Remove unused: `SYSTEM_PROMPT`, `USER_PROMPT_TEMPLATE`, `chatModelService`, `erDiagramProperties`, `parseReferences`, `extractJsonArray`, `resolveModelName`, `buildLanguageRule` (language filtering moves to search service).

Keep: `formatCitation` as fallback when `citation_gbt` blank; `detectLanguage`; SSE helpers.

Constructor deps become: `LitPaperSearchService`, `PaperSessionStore`, `ObjectMapper`.

- [ ] **Step 2: Update class Javadoc**

State clearly: 从 `lit_paper` 检索真实文献，不再调用大模型生成。

- [ ] **Step 3: Manual/API smoke**

With ≥3 seeded `lit_paper` rows matching a known keyword:
1. Create paper session
2. `POST /api/paper/references` with that keyword
3. Assert SSE contains those titles; process logs show **no** “AI 检索参考文献” / ChatModel call

- [ ] **Step 4: Commit**

```bash
git add ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/paper/PaperReferenceService.java
git commit -m "feat(paper): replace LLM reference generation with lit_paper search"
```

---

### Task 11: Frontend copy (optional, small)

**Files:**
- Modify: `D:/project3/AiSchoolWeb/src/components/paper/AiReferenceDialog.vue` (and any parent label in `ReferenceManagerSection.vue`)

- [ ] **Step 1: Change user-facing strings**

- 「AI 提供」→「文献库检索」或「智能检索」
- Loading/error text: remove “AI 生成” wording; keep custom paste path unchanged

- [ ] **Step 2: Commit in AiSchoolWeb repo**

```bash
cd D:/project3/AiSchoolWeb
git add src/components/paper/AiReferenceDialog.vue src/components/paper/ReferenceManagerSection.vue
git commit -m "fix(paper): clarify reference source as literature DB search"
```

---

### Task 12: End-to-end verification checklist

- [ ] **Step 1: Apply SQL** on target DB (`updat-lit-paper.sql`); confirm `ngram` FULLTEXT works (`SHOW VARIABLES LIKE 'ngram_token_size';` → 2 recommended).

- [ ] **Step 2: Import sample JSONL** (hand-made 5 records if crawl not ready):

```bash
cd tools/cnki-crawler
python scripts/import_to_mysql.py ... --jsonl tests/fixtures/sample_papers.jsonl
```

- [ ] **Step 3: Restart backend; run paper reference SSE; confirm + outline flow still works.**

- [ ] **Step 4: Re-run import twice; assert row count unchanged (idempotent).**

- [ ] **Step 5: Final commit only if checklist docs or fixtures added; otherwise done.**

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| Python crawler scaffold + Cookie/rate limit/checkpoint/JSONL | 1, 4, 5 |
| SE keywords | 1 |
| Metadata fields + GB/T 7714 | 2, 3, 5 |
| References list optional | 3, 5, 8 |
| MySQL lit_paper / lit_paper_ref + indexes | 7 |
| Idempotent import dedupe | 8 |
| PaperReferenceService DB-only, no LLM fallback | 9, 10 |
| Keep custom paste path | 10 (no change to confirm/custom) |
| README compliance | 6 |
| Frontend copy optional | 11 |
| E2E verify | 12 |
| ES/vector / PDF / captcha / multi-account | Out of scope |

## Self-review notes

- No TBD placeholders in tasks; CNKI live endpoint URL must be verified in Task 5 against current kns and written into README—parser tests remain fixture-based so CI does not need CNKI.
- Types aligned: `PaperRecord` ↔ JSONL ↔ `lit_paper` ↔ `LitPaperEntity` ↔ `Reference`.
- `doi`/`cnki_id` empty → NULL before unique upsert (Task 8).
