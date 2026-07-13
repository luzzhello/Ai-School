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
