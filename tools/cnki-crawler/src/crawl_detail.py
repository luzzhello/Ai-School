from __future__ import annotations

from src.gbt7714 import format_gbt7714
from src.models import PaperRecord
from src.normalize import title_hash


def merge_list_and_detail(list_row: dict, detail: dict | None = None) -> PaperRecord:
    """Merge list-row fields with optional detail fields into a PaperRecord."""
    d = detail or {}
    title = (d.get("title") or list_row.get("title") or "").strip()
    authors = d.get("authors") or list_row.get("authors")
    source = d.get("source") or list_row.get("source")
    year = d.get("year") if d.get("year") is not None else list_row.get("year")
    doc_type = d.get("doc_type") or list_row.get("doc_type")
    cite_count = d.get("cite_count") if d.get("cite_count") is not None else list_row.get("cite_count")
    abstract_text = d.get("abstract_text")
    keywords = d.get("keywords")
    paper = PaperRecord(
        cnki_id=list_row.get("cnki_id") or d.get("cnki_id"),
        doi=d.get("doi"),
        title=title,
        authors=authors,
        organs=d.get("organs"),
        abstract_text=abstract_text,
        keywords=keywords,
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
    )
    if not paper.citation_gbt:
        paper.citation_gbt = format_gbt7714(paper)
    if not abstract_text or not keywords:
        paper.status = "incomplete"
    else:
        paper.status = "active"
    return paper
