from __future__ import annotations


def null_if_blank(value: str | None) -> str | None:
    if value is None:
        return None
    s = str(value).strip()
    return s or None


def resolve_match(
    existing_by_cnki: dict[str, int],
    existing_by_doi: dict[str, int],
    existing_by_hash_year: dict[tuple[str, int | None], int],
    row: dict,
) -> int | None:
    """Return existing lit_paper id or None for insert. Order: cnki_id → doi → (title_hash, year)."""
    cnki_id = null_if_blank(row.get("cnki_id"))
    if cnki_id and cnki_id in existing_by_cnki:
        return existing_by_cnki[cnki_id]
    doi = null_if_blank(row.get("doi"))
    if doi and doi in existing_by_doi:
        return existing_by_doi[doi]
    title_hash = null_if_blank(row.get("title_hash"))
    year = row.get("year")
    if title_hash is not None:
        key = (title_hash, year)
        if key in existing_by_hash_year:
            return existing_by_hash_year[key]
    return None
