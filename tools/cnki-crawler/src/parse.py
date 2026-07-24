from __future__ import annotations

import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

BASE_URL = "https://kns.cnki.net"

DOC_TYPE_MAP = {
    "期刊": "J",
    "硕士": "D",
    "博士": "D",
    "会议": "C",
    "中国会议": "C",
    "国际会议": "C",
    "图书": "M",
}


class CaptchaOrLoginError(Exception):
    """Raised when CNKI returns a captcha or login page."""


def _soup(html: str) -> BeautifulSoup:
    return BeautifulSoup(html, "lxml")


def _text(el) -> str:
    if el is None:
        return ""
    return re.sub(r"\s+", " ", el.get_text(" ", strip=True)).strip()


def detect_captcha_or_login(html: str) -> None:
    lower = html.lower()
    if "验证码" in html or "checkcode" in lower or "login.aspx" in lower:
        raise CaptchaOrLoginError("CNKI captcha or login required; update Cookie and retry")
    soup = _soup(html)
    title = _text(soup.title).lower()
    if "登录" in title or "login" in title:
        raise CaptchaOrLoginError("CNKI login page detected; update Cookie and retry")


def _map_doc_type(raw: str | None) -> str | None:
    if not raw:
        return None
    for key, val in DOC_TYPE_MAP.items():
        if key in raw:
            return val
    return None


def _parse_int(raw: str | None) -> int | None:
    if not raw:
        return None
    m = re.search(r"\d+", raw.replace(",", ""))
    return int(m.group()) if m else None


def parse_list_html(html: str) -> list[dict]:
    detect_captcha_or_login(html)
    soup = _soup(html)
    rows: list[dict] = []
    # 现行 kns8s 列表：table.result-table-list > tr（无 result-item class）
    candidates = soup.select("table.result-table-list tr")
    if not candidates:
        candidates = soup.select("tr.result-item")
    for tr in candidates:
        a = tr.select_one("td.name a.fz14") or tr.select_one("td.name a")
        if a is None:
            continue
        href = a.get("href") or ""
        collect = tr.select_one("a.icon-collect[data-filename], [data-filename]")
        cnki_id = None
        if collect and collect.get("data-filename"):
            cnki_id = collect.get("data-filename")
        elif a.get("data-filename"):
            cnki_id = a.get("data-filename")
        detail_url = urljoin(BASE_URL, href) if href else None
        authors = _text(tr.select_one("td.author"))
        source = _text(tr.select_one("td.source"))
        year = _parse_int(_text(tr.select_one("td.date")))
        cite = _parse_int(_text(tr.select_one("td.quote .quoteNum") or tr.select_one("td.quote")))
        doc_raw = _text(tr.select_one("td.data"))
        rows.append(
            {
                "cnki_id": cnki_id,
                "title": _text(a),
                "authors": authors or None,
                "source": source or None,
                "year": year,
                "cite_count": cite,
                "doc_type": _map_doc_type(doc_raw),
                "detail_url": detail_url,
            }
        )
    return rows


def parse_detail_html(html: str) -> dict:
    detect_captcha_or_login(html)
    soup = _soup(html)

    abstract = _text(soup.select_one("#ChDivSummary"))
    if not abstract:
        abs_block = soup.select_one(".abstract-text")
        if abs_block:
            abstract = _text(abs_block).removeprefix("摘要：").removeprefix("摘要:").strip()

    kw_el = soup.select_one("p.keywords")
    keywords = None
    if kw_el:
        links = [_text(a) for a in kw_el.select("a") if _text(a)]
        keywords = ";".join(links) if links else _text(kw_el).removeprefix("关键词：").strip() or None

    doi = None
    for li in soup.select("li.top-space"):
        label = _text(li.select_one(".label"))
        if "DOI" in label.upper():
            doi = _text(li).replace(label, "").strip() or None
            break

    organs = _text(soup.select_one(".orgn"))
    citation = _text(soup.select_one("#gb7714")) or None
    title = _text(soup.select_one("h1.title")) or None
    authors = _text(soup.select_one("h3.author")) or None
    source = _text(soup.select_one("a.journal")) or None
    year = _parse_int(_text(soup.select_one("span.year")))
    cite_count = _parse_int(_text(soup.select_one(".quote em")))
    doc_type = _map_doc_type(_text(soup.select_one("span.type")))

    references: list[str] = []
    for li in soup.select("#ReferenceList li, ul.bibliography li"):
        text = _text(li)
        if text:
            references.append(text)

    return {
        "title": title,
        "authors": authors,
        "organs": organs or None,
        "abstract_text": abstract or None,
        "keywords": keywords,
        "doi": doi,
        "source": source,
        "year": year,
        "cite_count": cite_count,
        "doc_type": doc_type,
        "citation_gbt": citation,
        "references": references,
    }
