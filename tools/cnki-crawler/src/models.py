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
    # 知网外文详情页中译（用于中文检索英文库）
    title_zh: str | None = None
    abstract_zh: str | None = None
    keywords_zh: str | None = None
    source: str | None = None
    year: int | None = None
    volume: str | None = None
    issue: str | None = None
    pages: str | None = None
    publisher: str | None = None
    publish_place: str | None = None
    translator: str | None = None
    degree: str | None = None
    degree_place: str | None = None
    patent_country: str | None = None
    patent_kind: str | None = None
    patent_no: str | None = None
    standard_code: str | None = None
    publish_date: str | None = None
    doc_type: str | None = None  # J/D/C/M/P/S
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
