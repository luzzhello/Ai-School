from __future__ import annotations

import re
from typing import Any


_BOOK_TITLE_RE = re.compile(r"《([^》]+)》")
# 2026年, 10期 / 2026年，10期 / 2026年第10期 / 2026年10期
_YEAR_COMMA_ISSUE_RE = re.compile(
    r"(?P<year>19\d{2}|20\d{2})\s*年\s*[,，]?\s*第?\s*(?P<issue>\d+)\s*期"
)
# 2024(02) / 2024（02）
_YEAR_PAREN_ISSUE_RE = re.compile(
    r"(?P<year>19\d{2}|20\d{2})\s*[（(]\s*(?P<issue>[^）)]+?)\s*[）)]"
)
# 2023,34(2)
_YEAR_VOL_ISSUE_RE = re.compile(
    r"(?P<year>19\d{2}|20\d{2})\s*[,，]\s*(?P<volume>\d+)\s*[（(]\s*(?P<issue>[^）)]+?)\s*[）)]"
)
_YEAR_ONLY_RE = re.compile(r"(?P<year>19\d{2}|20\d{2})")
_PAGE_IN_TEXT_RE = re.compile(r"第\s*([A-Za-z0-9]+)\s*[-~～—－_]\s*([A-Za-z0-9]+)\s*页")


def parse_source_year(text: str | None) -> dict[str, str | int]:
    """Parse CNKI SourceYear / `.article-source` text into year / volume / issue [/source].

    Supports e.g. ``《西部素质教育》, 2026年, 10期``.
    """
    raw = (text or "").strip()
    if not raw:
        return {}
    out: dict[str, str | int] = {}

    bt = _BOOK_TITLE_RE.search(raw)
    if bt:
        out["source"] = bt.group(1).strip()

    m = _YEAR_VOL_ISSUE_RE.search(raw)
    if m:
        out["year"] = int(m.group("year"))
        out["volume"] = m.group("volume").strip()
        iss = m.group("issue").strip()
        out["issue"] = iss.lstrip("0") or iss
        return out

    m = _YEAR_COMMA_ISSUE_RE.search(raw)
    if m:
        out["year"] = int(m.group("year"))
        iss = m.group("issue").strip()
        out["issue"] = iss.lstrip("0") or iss
        return out

    m = _YEAR_PAREN_ISSUE_RE.search(raw)
    if m:
        out["year"] = int(m.group("year"))
        iss = m.group("issue").strip()
        out["issue"] = iss.lstrip("0") or iss
        return out

    m = _YEAR_ONLY_RE.search(raw)
    if m:
        out["year"] = int(m.group("year"))
    return out


def normalize_page_range(text: str | None) -> str | None:
    raw = (text or "").strip()
    if not raw or raw in {"0", "-", "--"}:
        return None
    m = _PAGE_IN_TEXT_RE.search(raw)
    if m:
        return f"{m.group(1)}-{m.group(2)}"
    raw = re.sub(r"\s+", "", raw)
    raw = raw.replace("~", "-").replace("～", "-").replace("—", "-").replace("－", "-").replace("_", "-")
    # strip wrappers like （156-160） / 第156-160页 leftovers
    raw = raw.strip("（）()[]【】")
    raw = re.sub(r"^第", "", raw)
    raw = re.sub(r"页$", "", raw)
    if not re.search(r"\d", raw):
        return None
    return raw or None


def _looks_like_album_label(text: str) -> bool:
    t = (text or "").strip()
    return t.startswith("<") or "辑-" in t or "·" in t[:8]


def parse_dazhong_payload(data: dict[str, Any]) -> dict[str, Any]:
    """Map showDazhongPage `data` object to lit biblio fields."""
    out: dict[str, Any] = {}
    source_year = data.get("SourceYear")
    parsed = parse_source_year(str(source_year) if source_year is not None else None)
    out.update(parsed)

    pages = normalize_page_range(str(data.get("PageRange") or "") or None)
    if not pages:
        # sometimes page hint only appears with 页 in Title / other fields
        for key in ("Title", "titleTitle"):
            pages = normalize_page_range(str(data.get(key) or "") or None)
            if pages:
                break
    if pages:
        out["pages"] = pages

    pub = str(data.get("ClassifiedPublicationName") or "").strip()
    if pub and not _looks_like_album_label(pub) and not out.get("source"):
        out["source"] = pub

    title = data.get("Title")
    if title and not out.get("title"):
        t = str(title).strip()
        # Title on fee page often "题名 作者; 156_160" — keep only leading title chunk if noisy
        if t and len(t) < 200:
            out.setdefault("title", t.split(";")[0].strip())
    return {k: v for k, v in out.items() if v is not None and v != ""}
