from __future__ import annotations

import logging
import re
from typing import Any
from urllib.parse import urlencode, urljoin

from bs4 import BeautifulSoup

from src.biblio_utils import normalize_page_range, parse_dazhong_payload, parse_source_year
from src.http_client import CnkiHttpClient

logger = logging.getLogger(__name__)

SHOW_DAZHONG_URL = "https://bar.cnki.net/bar/downloadDaZhong/showDazhongPage"
FEE_PAGE_PATH = "/bar/fee_DZhy2_GB.html"
ORDER_HREF_RE = re.compile(
    r'href="(https://bar\.cnki\.net/bar/download/order\?id=[^"]+)"',
    re.I,
)


def extract_order_links(detail_html: str) -> list[str]:
    return list(dict.fromkeys(ORDER_HREF_RE.findall(detail_html or "")))


def parse_fee_article_source_html(html: str) -> dict[str, Any]:
    """Parse rendered fee page `.article-source` / `.article-pages`.

    Real page shape (bar.cnki fee_DZhy2_GB.html)::

        <div class="article-info">
          <div class="article-source">· 《参花》, 2024年, 17期</div>
          <div class="article-pages">【页 数】 3 页（ 第154-156页 ）</div>
        </div>

    ``.article-pages`` may be sibling of ``.article-source`` (not nested).
    """
    soup = BeautifulSoup(html or "", "lxml")
    src = soup.select_one(".article-source")
    if src is None:
        return {}
    # own text excluding nested .article-pages (older nesting variant)
    parts: list[str] = []
    for child in src.children:
        name = getattr(child, "name", None)
        if name == "div" and "article-pages" in (child.get("class") or []):
            continue
        text = child if isinstance(child, str) else child.get_text(" ", strip=True)
        text = re.sub(r"\s+", " ", str(text or "")).strip()
        if text:
            parts.append(text)
    source_text = " ".join(parts).strip() or src.get_text(" ", strip=True)
    # leading middot / bullet from fee UI
    source_text = re.sub(r"^[·•.\s]+", "", source_text)
    out = parse_source_year(source_text)

    pages_el = src.select_one(".article-pages")
    if pages_el is None:
        info = src.find_parent(class_="article-info") or soup.select_one(".article-info")
        if info is not None:
            pages_el = info.select_one(".article-pages")
    if pages_el is None:
        pages_el = soup.select_one(".article-pages")
    if pages_el is not None:
        pages = normalize_page_range(pages_el.get_text(" ", strip=True))
        if pages:
            out["pages"] = pages
    return {k: v for k, v in out.items() if v is not None and v != ""}


def fetch_dazhong_biblio(
    client: CnkiHttpClient,
    detail_html: str,
    *,
    referer: str | None = None,
) -> dict[str, Any]:
    """Follow detail-page download/order link → showDazhongPage for year/issue/pages.

    Body must be raw cacheID bytes (not JSON-quoted). Failures fall back to fee HTML.
    """
    hrefs = extract_order_links(detail_html)
    if not hrefs:
        return {}

    hx = client._client  # noqa: SLF001
    last_err: str | None = None
    for href in hrefs[:4]:
        try:
            resp = hx.get(
                href,
                headers={"Referer": referer or "https://kns.cnki.net/"},
                follow_redirects=False,
            )
            loc = resp.headers.get("location") or resp.headers.get("Location") or ""
            if "cacheID=" not in loc:
                last_err = f"no cacheID in location status={resp.status_code}"
                continue
            m = re.search(r"cacheID=(.+)$", loc)
            if not m:
                last_err = "cacheID regex miss"
                continue
            cache = m.group(1).split("#")[0]
            fee_url = "https://bar.cnki.net" + FEE_PAGE_PATH + "?" + urlencode(
                {"lang": "CHS", "cacheID": cache}
            )
            referer_fee = urljoin("https://bar.cnki.net", loc)

            parsed: dict[str, Any] = {}
            try:
                api_resp = hx.post(
                    SHOW_DAZHONG_URL,
                    content=cache.encode("utf-8"),
                    headers={
                        "Content-Type": "application/json;charset=UTF-8",
                        "Origin": "https://bar.cnki.net",
                        "Referer": referer_fee,
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    follow_redirects=True,
                )
                api_resp.raise_for_status()
                payload = api_resp.json()
                if payload.get("code") == 200000 and isinstance(payload.get("data"), dict):
                    parsed = parse_dazhong_payload(payload["data"])
                else:
                    last_err = f"api code={payload.get('code')} msg={payload.get('message')}"
            except Exception as e:
                last_err = f"api error: {e}"

            # 期号常在 SourceYear「2026年, 10期」；API 失败或未拆出 issue 时读 fee HTML
            if not parsed.get("issue") or not parsed.get("pages"):
                fee_resp = hx.get(
                    fee_url,
                    headers={"Referer": href},
                    follow_redirects=True,
                )
                fee_resp.raise_for_status()
                from_html = parse_fee_article_source_html(fee_resp.text)
                for k, v in from_html.items():
                    if v is not None and v != "" and not parsed.get(k):
                        parsed[k] = v

            if parsed.get("issue") or parsed.get("pages") or parsed.get("year"):
                logger.info(
                    "dazhong biblio ok: year=%s issue=%s pages=%s source=%s",
                    parsed.get("year"),
                    parsed.get("issue"),
                    parsed.get("pages"),
                    parsed.get("source"),
                )
                return parsed
            last_err = last_err or "empty parsed fields"
        except Exception as e:
            last_err = str(e)
            logger.debug("dazhong attempt failed: %s", e)
    if last_err:
        logger.warning("dazhong biblio skipped: %s", last_err)
    return {}
