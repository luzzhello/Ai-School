from __future__ import annotations

from src.gbt7714 import format_gbt7714, normalize_citation_gbt
from src.models import PaperRecord
from src.normalize import title_hash

_BIBLIO_KEYS = (
    "volume",
    "issue",
    "pages",
    "publisher",
    "publish_place",
    "translator",
    "degree",
    "degree_place",
    "patent_country",
    "patent_kind",
    "patent_no",
    "standard_code",
    "publish_date",
)


def merge_list_and_detail(list_row: dict, detail: dict | None = None) -> PaperRecord:
    """Merge list-row fields with optional detail fields into a PaperRecord.

    题名 / 作者 / 来源以检索列表为准（与知网结果表「题名、作者、来源」列一致），
    详情页只补摘要、关键词、DOI、卷期页等。
    """
    d = detail or {}
    title = (list_row.get("title") or d.get("title") or "").strip()
    authors = list_row.get("authors") or d.get("authors")
    source = list_row.get("source") or d.get("source")
    year = d.get("year") if d.get("year") is not None else list_row.get("year")
    doc_type = d.get("doc_type") or list_row.get("doc_type")
    cite_count = d.get("cite_count") if d.get("cite_count") is not None else list_row.get("cite_count")
    abstract_text = d.get("abstract_text")
    keywords = d.get("keywords")
    title_zh = d.get("title_zh")
    abstract_zh = d.get("abstract_zh")
    keywords_zh = d.get("keywords_zh")
    biblio = {k: d.get(k) for k in _BIBLIO_KEYS if d.get(k)}
    paper = PaperRecord(
        cnki_id=list_row.get("cnki_id") or d.get("cnki_id"),
        doi=d.get("doi"),
        title=title,
        authors=authors,
        organs=d.get("organs"),
        abstract_text=abstract_text,
        keywords=keywords,
        title_zh=title_zh,
        abstract_zh=abstract_zh,
        keywords_zh=keywords_zh,
        source=source,
        year=year,
        doc_type=doc_type,
        cite_count=cite_count,
        lit_source="CNKI",
        citation_gbt=d.get("citation_gbt"),
        detail_url=list_row.get("detail_url"),
        title_hash=title_hash(title) if title else None,
        crawl_keyword=list_row.get("crawl_keyword"),
        references=list(d.get("references") or []),
        **biblio,
    )
    # 优先按结构化字段重拼（英文半角紧凑）；官方引文仅作兜底并规范化
    rebuilt = format_gbt7714(paper)
    if rebuilt and rebuilt not in ("[J].", "[D].", "[M].", "[C].", "[P].", "[S]."):
        paper.citation_gbt = rebuilt
    elif paper.citation_gbt:
        paper.citation_gbt = normalize_citation_gbt(paper.citation_gbt)
    else:
        paper.citation_gbt = rebuilt
    has_abs = bool(abstract_text or abstract_zh)
    has_kw = bool(keywords or keywords_zh)
    if not has_abs or not has_kw:
        paper.status = "incomplete"
    else:
        paper.status = "active"
    return paper
