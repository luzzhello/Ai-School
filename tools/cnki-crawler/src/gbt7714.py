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
