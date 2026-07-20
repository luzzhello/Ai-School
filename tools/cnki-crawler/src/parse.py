from __future__ import annotations

import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

BASE_URL = "https://kns.cnki.net"

DOC_TYPE_MAP = {
    "期刊": "J",
    "外文期刊": "J",
    "硕士": "D",
    "博士": "D",
    "会议": "C",
    "中国会议": "C",
    "国际会议": "C",
    "外文会议": "C",
    "图书": "M",
    "专利": "P",
    "标准": "S",
    "技术标准": "S",
}

# label text（去空白/冒号后）→ 字段名
_LABEL_FIELDS = {
    "DOI": "doi",
    "页码": "pages",
    "页": "pages",
    "卷": "volume",
    "卷号": "volume",
    "期": "issue",
    "期号": "issue",
    "出版地": "publish_place",
    "出版者": "publisher",
    "出版社": "publisher",
    "译者": "translator",
    "学位授予单位": "source",
    "授予单位": "source",
    "学位": "degree",
    "国别": "patent_country",
    "国名": "patent_country",
    "专利号": "patent_no",
    "申请号": "patent_no",
    "公开号": "patent_no",
    "专利类型": "patent_kind",
    "文献种类": "patent_kind",
    "标准号": "standard_code",
    "标准代号": "standard_code",
    "发布日期": "publish_date",
    "公开日期": "publish_date",
    "出版日期": "publish_date",
}


class CaptchaOrLoginError(Exception):
    """Raised when CNKI returns a captcha or login page."""


def _soup(html: str) -> BeautifulSoup:
    return BeautifulSoup(html, "lxml")


def _text(el) -> str:
    if el is None:
        return ""
    return re.sub(r"\s+", " ", el.get_text(" ", strip=True)).strip()


def _inline_text(el) -> str:
    """子节点直接拼接，不加空格（避免检索高亮 span/font 把中文题名拆开）。"""
    if el is None:
        return ""
    return el.get_text("", strip=True)


def _compact_title_text(text: str | None) -> str | None:
    t = _clean_ui_noise(text)
    if not t:
        return None
    if _is_mostly_cjk(t):
        return re.sub(r"\s+", "", t) or None
    return re.sub(r"\s+", " ", t).strip() or None


def _looks_like_cn_author_name(token: str) -> bool:
    t = (token or "").strip()
    if not t or len(t) > 8:
        return False
    cjk = _cjk_count(t)
    return cjk >= 2 and cjk >= len(re.sub(r"\s+", "", t)) * 0.8


def _is_english_author_tokens(tokens: list[str]) -> bool:
    for t in tokens:
        if re.search(r"[A-Za-z]", t):
            return True
    return False


def _split_author_tokens(s: str) -> list[str]:
    """空格分隔作者：中文单块；连续英文块合并为一名（如 David LO）。"""
    tokens = s.split()
    if not tokens:
        return []
    parts: list[str] = []
    latin_buf: list[str] = []
    for t in tokens:
        if _looks_like_cn_author_name(t):
            if latin_buf:
                parts.append(" ".join(latin_buf))
                latin_buf = []
            parts.append(t)
        elif re.search(r"[A-Za-z]", t):
            latin_buf.append(t)
        else:
            if latin_buf:
                parts.append(" ".join(latin_buf))
                latin_buf = []
            parts.append(t)
    if latin_buf:
        parts.append(" ".join(latin_buf))
    return parts


def _merge_split_latin_names(parts: list[str]) -> list[str]:
    """修复 `David,LO` 这类被误拆的英文作者。"""
    out: list[str] = []
    i = 0
    while i < len(parts):
        p = parts[i].strip()
        if (
            i + 1 < len(parts)
            and re.match(r"^[A-Za-z][a-zA-Z'.-]+$", p)
            and re.match(r"^[A-Z]{2,6}$", parts[i + 1].strip())
        ):
            out.append(f"{p} {parts[i + 1].strip()}")
            i += 2
            continue
        out.append(p)
        i += 1
    return out


def _normalize_authors_text(raw: str | None) -> str | None:
    """作者字段统一紧凑逗号分隔（无空格）：`张三,李四` / `Liu Y,Wang Z`；引文拼装在 gbt7714 层处理。"""
    if not raw:
        return None
    s = (_clean_ui_noise(raw) or "").strip()
    if not s:
        return None
    if re.search(r"[;；]", s):
        parts = re.split(r"[;；]+", s)
    elif "," in s:
        parts = [p.strip() for p in s.split(",")]
    elif re.search(r"[、/|]", s):
        parts = re.split(r"[、/|]+", s)
    else:
        parts = _split_author_tokens(s)
        if len(parts) <= 1 and " " in s:
            parts = [s]
    cleaned = [p.strip() for p in parts if p and p.strip()]
    cleaned = _merge_split_latin_names(cleaned)
    if not cleaned:
        return None
    return ",".join(cleaned)


def _authors_from_cell(el) -> str | None:
    if el is None:
        return None
    links = [(_inline_text(a) or "").strip() for a in el.select("a")]
    links = [x for x in links if x]
    if links:
        return ",".join(links)
    return _normalize_authors_text(_text(el))


def detect_captcha_or_login(html: str) -> None:
    lower = html.lower()
    if (
        "验证码" in html
        or "安全验证" in html
        or "滑块验证" in html
        or "拖动下方拼图" in html
        or "/verify/" in lower
        or "checkcode" in lower
        or "login.aspx" in lower
    ):
        raise CaptchaOrLoginError(
            "CNKI captcha/security verify required; open site in browser, pass verify, refresh Cookie"
        )
    soup = _soup(html)
    title = _text(soup.title).lower()
    if "登录" in title or "login" in title or "安全验证" in title:
        raise CaptchaOrLoginError("CNKI login/verify page detected; update Cookie and retry")


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


def _label_key(label: str) -> str | None:
    raw = re.sub(r"[\s：:]+$", "", (label or "").strip())
    if not raw:
        return None
    if "DOI" in raw.upper():
        return "doi"
    for k, field in _LABEL_FIELDS.items():
        if k.upper() == raw.upper() or raw == k:
            return field
    return None


# 知网现行详情多用 .rowtit；旧页/测试页用 .label
_ROW_LABEL_SEL = ".label, [class^='rowtit'], .rowtit, .rowtit2"
_DOI_RE = re.compile(
    r"(?:https?://(?:dx\.)?doi\.org/|https?://link\.cnki\.net/doi/)?(10\.\d{4,9}/[^\s\"'<>]+)",
    re.I,
)
_DOI_IN_CITE_RE = re.compile(r"DOI\s*[:：]\s*(10\.\d{4,9}/\S+)", re.I)


def normalize_doi(raw: str | None) -> str | None:
    """Extract canonical DOI (10.xxxx/...) or None."""
    if not raw:
        return None
    text = re.sub(r"\s+", " ", str(raw)).strip()
    if not text or "申请" in text:
        return None
    m = _DOI_RE.search(text)
    if not m:
        return None
    doi = m.group(1).strip()
    doi = re.sub(r"[\]）。，,;；.]+$", "", doi)
    return doi or None


def _row_value_text(row, label_el, label: str) -> str:
    """Row text without the label node (Zotero-style)."""
    if label_el is not None:
        try:
            label_el.extract()
            value = _text(row)
            # BeautifulSoup mutates tree; restore is unnecessary for throwaway soup
            return value.strip(" ：:;；")
        except Exception:
            pass
    value = _text(row)
    if label:
        value = value.replace(label, "", 1)
    return value.strip(" ：:;；")


def _extract_labeled_fields(soup: BeautifulSoup) -> dict[str, str]:
    out: dict[str, str] = {}
    # 期刊常见：li.top-space > .rowtit；专利/标准等也可能在 .row
    candidates = soup.select(
        "li.top-space, ul.proinfo li, .rowtip li, "
        ".row > ul > li, .doc-detail-scholar .row, .row"
    )
    seen: set[int] = set()
    for row in candidates:
        rid = id(row)
        if rid in seen:
            continue
        seen.add(rid)
        label_el = row.select_one(_ROW_LABEL_SEL)
        label = _text(label_el)
        field = _label_key(label)
        if not field or field in out:
            continue
        # clone so extract() does not break later selectors on same soup
        clone = _soup(str(row))
        clone_root = clone.body.contents[0] if clone.body and clone.body.contents else clone
        clone_label = clone_root.select_one(_ROW_LABEL_SEL) if hasattr(clone_root, "select_one") else None
        value = _row_value_text(clone_root, clone_label, label)
        if field == "doi":
            value = normalize_doi(value) or ""
        if value:
            out[field] = value
    return out


def _extract_doi(soup: BeautifulSoup, labeled_doi: str | None, citation: str | None) -> str | None:
    doi = normalize_doi(labeled_doi)
    if doi:
        return doi
    for sel in ("#catalog_DOI", "li#catalog_DOI"):
        el = soup.select_one(sel)
        if el is None:
            continue
        doi = normalize_doi(_text(el))
        if doi:
            return doi
    for el in soup.find_all(id=True):
        eid = str(el.get("id") or "")
        if "doi" not in eid.lower():
            continue
        doi = normalize_doi(_text(el))
        if doi:
            return doi
    for a in soup.select("a[href*='doi.org'], a[href*='link.cnki.net/doi']"):
        doi = normalize_doi(a.get("href") or "") or normalize_doi(_text(a))
        if doi:
            return doi
    if citation:
        m = _DOI_IN_CITE_RE.search(citation)
        if m:
            return normalize_doi(m.group(1))
    return None


_VOL_ISSUE_EN_TIP_RE = re.compile(
    r"[Vv]olume\s*(\d+)\s*,?\s*[Ii]ssue\s*(\d+)",
    re.I,
)
_VOL_ONLY_EN_TIP_RE = re.compile(r"[Vv]olume\s*(\d+)", re.I)
_YEAR_IN_TIP_RE = re.compile(r"(?:^|[,\s])(19\d{2}|20\d{2})(?:\D|$)")
_PP_PAGES_RE = re.compile(r"\bPP\.?\s*([0-9]+(?:\s*[-–—]\s*[0-9]+)*)", re.I)
# 中文详情 `.top-tip`：电子设计工程 . 2026 ,34 (14) : 19-24
_CN_TOP_TIP_YVI_PAGES_RE = re.compile(
    r"(19\d{2}|20\d{2})\s*[,，]\s*"
    r"(\d+)\s*[（(]\s*([^）)]+?)\s*[）)]\s*"
    r"[:：]\s*([A-Za-z0-9]+(?:\s*[-~～—－–]\s*[A-Za-z0-9]+)?)"
)
# 无卷：刊名 . 2026 (14) : 19-24
_CN_TOP_TIP_YI_PAGES_RE = re.compile(
    r"(19\d{2}|20\d{2})\s*[（(]\s*([^）)]+?)\s*[）)]\s*"
    r"[:：]\s*([A-Za-z0-9]+(?:\s*[-~～—－–]\s*[A-Za-z0-9]+)?)"
)
_CN_TOP_TIP_PAGES_ONLY_RE = re.compile(
    r"[:：]\s*([A-Za-z0-9]+(?:\s*[-~～—－–]\s*[A-Za-z0-9]+)?)"
)


def _normalize_tip_pages(raw: str) -> str:
    return re.sub(
        r"\s+",
        "",
        raw.replace("~", "-")
        .replace("～", "-")
        .replace("—", "-")
        .replace("－", "-")
        .replace("–", "-"),
    )


def _normalize_tip_issue(raw: str) -> str:
    s = raw.strip()
    stripped = s.lstrip("0")
    return stripped or s


def enrich_from_top_tip_scholar(
    soup: BeautifulSoup,
    biblio: dict,
    *,
    source: str | None,
    year: int | None,
) -> tuple[dict, str | None, int | None]:
    """详情页 `.top-tip` / `.top-tip-scholar`：刊名 + 年/卷/期/页（中英皆可）。

    中文示例：``电子设计工程 . 2026 ,34 (14) : 19-24``
    外文示例：``Journal ... Volume 10, Issue 6 ... PP. 12-20``
    有顶栏数据时以顶栏为准（可覆盖错误的 hidden ``page-pange``），避免再去 bar.cnki 页。
    """
    tip = (
        soup.select_one(".top-tip-scholar")
        or soup.select_one(".top-tip")
        or soup.select_one(".top-first")
    )
    if tip is None:
        return biblio, source, year

    tip_text = _text(tip)
    from_tip = False

    m_cn = _CN_TOP_TIP_YVI_PAGES_RE.search(tip_text)
    if m_cn:
        from_tip = True
        if year is None:
            year = int(m_cn.group(1))
        biblio["volume"] = m_cn.group(2)
        biblio["issue"] = _normalize_tip_issue(m_cn.group(3))
        biblio["pages"] = _normalize_tip_pages(m_cn.group(4))
    else:
        m_cn2 = _CN_TOP_TIP_YI_PAGES_RE.search(tip_text)
        if m_cn2:
            from_tip = True
            if year is None:
                year = int(m_cn2.group(1))
            biblio["issue"] = _normalize_tip_issue(m_cn2.group(2))
            biblio["pages"] = _normalize_tip_pages(m_cn2.group(3))

    if not from_tip:
        if not biblio.get("volume") or not biblio.get("issue"):
            m = _VOL_ISSUE_EN_TIP_RE.search(tip_text)
            if m:
                if not biblio.get("volume"):
                    biblio["volume"] = m.group(1)
                if not biblio.get("issue"):
                    biblio["issue"] = _normalize_tip_issue(m.group(2))
            elif not biblio.get("volume"):
                m2 = _VOL_ONLY_EN_TIP_RE.search(tip_text)
                if m2:
                    biblio["volume"] = m2.group(1)

        m_pp = _PP_PAGES_RE.search(tip_text)
        if m_pp:
            biblio["pages"] = _normalize_tip_pages(m_pp.group(1))
        elif not biblio.get("pages"):
            m_pg = _CN_TOP_TIP_PAGES_ONLY_RE.search(tip_text)
            if m_pg:
                biblio["pages"] = _normalize_tip_pages(m_pg.group(1))

    if year is None:
        m = _YEAR_IN_TIP_RE.search(tip_text)
        if m:
            year = int(m.group(1))

    if not source:
        journal_a = tip.select_one("a[href*='journal'], a[href*='navi'], a")
        if journal_a is not None:
            src = _text(journal_a).strip().rstrip(".").strip()
            if src and src.lower() not in {"journal", "j", "conference", "p"}:
                source = src

    mark = _text(tip.select_one(".marktip"))
    if mark and "国际" in mark and "期刊" in mark:
        biblio["doc_type_hint"] = "J"
    tip_low = tip_text.lower()
    if "conference" in tip_low or "[c]" in tip_low:
        biblio.setdefault("doc_type_hint", "C")

    return biblio, source, year


_PAGES_RE = re.compile(
    r"(?::|：)\s*([A-Za-z0-9]+(?:\s*[-~～—－]\s*[A-Za-z0-9]+)?)\s*(?:\.|$)"
)
_VOL_ISSUE_RE = re.compile(
    r"[,，]\s*(\d+)\s*[（(]\s*([^）)]+)\s*[）)]\s*(?=[:：]|$)"
)
_VOL_ONLY_RE = re.compile(r"[,，]\s*(?:[Vv]ol\.?\s*)?(\d+)\s*(?=[:：])")


def enrich_from_citation_gbt(fields: dict, citation: str | None) -> dict:
    """从整段 GB/T 引文回填卷期页 / DOI（解析不到标签时兜底）。"""
    if not citation:
        return fields
    if not fields.get("pages"):
        m = _PAGES_RE.search(citation)
        if m:
            fields["pages"] = re.sub(r"\s+", "", m.group(1).replace("~", "-").replace("～", "-").replace("—", "-").replace("－", "-"))
    if not fields.get("volume") or not fields.get("issue"):
        m = _VOL_ISSUE_RE.search(citation)
        if m:
            fields.setdefault("volume", m.group(1))
            fields.setdefault("issue", m.group(2).strip())
        elif not fields.get("volume"):
            m2 = _VOL_ONLY_RE.search(citation)
            if m2:
                fields["volume"] = m2.group(1)
    if not fields.get("doi"):
        m = _DOI_IN_CITE_RE.search(citation)
        if m:
            doi = normalize_doi(m.group(1))
            if doi:
                fields["doi"] = doi
    return fields


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
        authors = _authors_from_cell(tr.select_one("td.author"))
        source = _text(tr.select_one("td.source"))
        year = _parse_int(_text(tr.select_one("td.date")))
        cite = _parse_int(_text(tr.select_one("td.quote .quoteNum") or tr.select_one("td.quote")))
        doc_raw = _text(tr.select_one("td.data"))
        rows.append(
            {
                "cnki_id": cnki_id,
                "title": _title_text(a) or _compact_title_text(_inline_text(a)),
                "authors": authors or None,
                "source": source or None,
                "year": year,
                "cite_count": cite,
                "doc_type": _map_doc_type(doc_raw),
                "detail_url": detail_url,
            }
        )
    return rows


from src.biblio_utils import normalize_page_range


def _hidden_value(soup: BeautifulSoup, element_id: str) -> str | None:
    el = soup.select_one(f"#{element_id}")
    if el is None:
        return None
    val = (el.get("value") or "").strip()
    return val or None


def _enrich_from_hidden_inputs(soup: BeautifulSoup, biblio: dict) -> dict:
    """详情页隐藏域页码。

    - ``prite-page-num``：正文起止页（与顶栏一致，优先）
    - ``page-pange``：偶发为错误区间，仅作兜底
    """
    if not biblio.get("pages"):
        pages = normalize_page_range(_hidden_value(soup, "prite-page-num")) or normalize_page_range(
            _hidden_value(soup, "page-pange")
        )
        if pages:
            biblio["pages"] = pages
    return biblio


_CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def _cjk_count(text: str | None) -> int:
    if not text:
        return 0
    return len(_CJK_RE.findall(text))


def _is_mostly_cjk(text: str | None, *, min_chars: int = 2) -> bool:
    """粗判是否为中文为主（知网外文页译文）。"""
    if not text:
        return False
    compact = re.sub(r"\s+", "", text)
    if len(compact) < min_chars:
        return False
    cjk = _cjk_count(compact)
    return cjk >= min_chars and (cjk / len(compact)) >= 0.12


def _clean_ui_noise(text: str | None) -> str | None:
    if not text:
        return None
    s = text.strip()
    for noise in (
        "收起翻译",
        "展开翻译",
        "显示原文",
        "显示译文",
        "MT翻译",
        "机翻",
        "附视频",
        "网络首发",
        "更多",
        "还原",
    ):
        s = s.replace(noise, "")
    s = re.sub(r"\s+", " ", s).strip(" ;；|")
    return s or None


def _normalize_keyword_list(parts: list[str]) -> str | None:
    cleaned: list[str] = []
    for x in parts:
        t = (x or "").strip().strip(";；").strip()
        if not t or t in ("显示原文", "显示译文", "MT翻译", "关键词"):
            continue
        cleaned.append(t)
    if not cleaned:
        return None
    return ";".join(cleaned)


def _title_text(el) -> str | None:
    """题名节点去掉 MT翻译 / 附视频 等后再取文。"""
    if el is None:
        return None
    clone = BeautifulSoup(str(el), "lxml")
    root = clone.body.contents[0] if clone.body and clone.body.contents else clone
    for btn in root.select(
        ".mt-trans, .btn-translate, .btn-original, a.btn-translate, "
        "#corr-video, span.type, .type, "
        "[onclick*='Trans'], [onclick*='trans']"
    ):
        btn.decompose()
    return _compact_title_text(_inline_text(root))


def _keywords_from_el(el) -> str | None:
    if el is None:
        return None
    links = [_text(a) for a in el.select("a") if _text(a)]
    if links:
        return _normalize_keyword_list(links)
    raw = _text(el)
    for prefix in ("关键词：", "关键词:", "关键词"):
        if raw.startswith(prefix):
            raw = raw[len(prefix) :].strip()
    raw = _clean_ui_noise(raw)
    if not raw:
        return None
    return _normalize_keyword_list(re.split(r"[;；]+", raw))


def _pick_first_text(soup: BeautifulSoup, selectors: list[str]) -> str | None:
    for sel in selectors:
        el = soup.select_one(sel)
        if el is None:
            continue
        clone = BeautifulSoup(str(el), "lxml")
        root = clone.body.contents[0] if clone.body and clone.body.contents else clone
        for btn in root.select(
            "a.btn-translate, a.btn-original, .btn-translate, .btn-original"
        ):
            btn.decompose()
        text = _clean_ui_noise(_text(root))
        if text:
            return text
    return None


def _extract_title_zh(soup: BeautifulSoup, title_en: str | None) -> str | None:
    """外文详情题名中译。

    scholar 页常见：``.wx-tit-scholar > .title-trans.h2-scholar``（可为 display:none，HTML 仍有文）。
    """
    if _is_mostly_cjk(title_en):
        return None

    candidates: list[str] = []
    for sel in (
        ".title-trans.h2-scholar",
        ".wx-tit-scholar .title-trans",
        ".title-trans",
        "#title_trans",
        ".title-translate .translate-text",
        ".title-translate",
        ".doc-title-translate .translate-text",
        ".doc-title-translate",
        "#ChTitleCn",
        "[class*='title-translate']",
        "[class*='title-trans']",
    ):
        t = _pick_first_text(soup, [sel])
        if t and _is_mostly_cjk(t):
            candidates.append(t)

    for root_sel in ("h1.title", ".h1-scholar", ".wx-tit > h1", "h1", ".wx-tit-scholar"):
        h1 = soup.select_one(root_sel)
        if h1 is None:
            continue
        for sib in h1.find_next_siblings():
            name = getattr(sib, "name", None)
            if name is None:
                continue
            classes = " ".join(sib.get("class") or [])
            if "author" in classes or "orgn" in classes:
                continue
            if "trans" not in classes:
                continue
            t = _clean_ui_noise(_text(sib))
            if t and _is_mostly_cjk(t) and t != title_en:
                candidates.append(t)
                break
        break

    for t in candidates:
        if title_en and t == title_en:
            continue
        if len(t) >= 4:
            return t[:500]
    return None


def _split_bilingual_abstract(soup: BeautifulSoup, primary: str | None) -> tuple[str | None, str | None]:
    """返回 (abstract_en, abstract_zh)。

    外文 scholar 页：英文在 `#ChDivSummary`/`#abstract_cn`，中译在隐藏的
    `#ChDivSummary2`/`#abstract_trans`（HTML 已带文，仅 CSS 隐藏）。
    """
    zh_candidates = [
        _pick_first_text(
            soup,
            [
                "#ChDivSummary2",
                "#abstract_trans .abstract-text",
                "#abstract_trans",
                "#ChDivSummaryCn",
                "#abstract_zh",
                ".abstract-text-cn",
            ],
        ),
    ]
    en_candidates = [
        _pick_first_text(
            soup,
            [
                "#abstract_cn .abstract-text",
                "#abstract_cn",
                "#ChDivSummaryEn",
                "#EnDivSummary",
                "#abstract_en",
                ".abstract-text-en",
            ],
        ),
    ]
    if primary:
        if _is_mostly_cjk(primary):
            zh_candidates.insert(0, primary)
        else:
            en_candidates.insert(0, primary)

    abstract_zh = next((t for t in zh_candidates if t and _is_mostly_cjk(t)), None)
    abstract_en = next((t for t in en_candidates if t and not _is_mostly_cjk(t)), None)
    if primary and not abstract_zh and _is_mostly_cjk(primary):
        abstract_zh = primary
    if primary and not abstract_en and not _is_mostly_cjk(primary):
        abstract_en = primary
    return abstract_en, abstract_zh


_MTKY_JS_RE = re.compile(
    r"var\s+mtky\s*=\s*(['\"])(?P<val>.*?)\1",
    re.DOTALL,
)


def _keywords_from_mtky_script(el) -> str | None:
    """外文页中译关键词常嵌在 script：var mtky = '中文；词；...'，DOM 文本为空。"""
    if el is None:
        return None
    for script in el.select("script"):
        code = script.string or script.get_text() or ""
        m = _MTKY_JS_RE.search(code)
        if not m:
            continue
        raw = m.group("val").strip()
        if not raw:
            continue
        # 与页面 JS 一致：中文分隔符 → ;
        parts = re.split(r"[;；，、]+", raw)
        return _normalize_keyword_list(parts)
    return None


def _keywords_from_trans_block(el) -> str | None:
    """`#keyword_trans`：可能是已渲染的 h3，或仅有含 mtky 的 script。"""
    if el is None:
        return None
    from_js = _keywords_from_mtky_script(el)
    if from_js:
        return from_js
    from_links = _keywords_from_el(el)
    if from_links and _is_mostly_cjk(from_links):
        return from_links
    raw = _clean_ui_noise(_text(el))
    if not raw or not _is_mostly_cjk(raw):
        return from_links
    return _normalize_keyword_list(re.split(r"[;；]+", raw))


def _split_bilingual_keywords(soup: BeautifulSoup, primary: str | None) -> tuple[str | None, str | None]:
    """返回 (keywords_en, keywords_zh)。

    scholar 页命名易混：`#keyword_cn` 实际是英文原词，`#keyword_trans` 是中译
    （可能 display:none，且内容写在 script 的 mtky 变量里）。
    """
    zh_el = soup.select_one("#keyword_trans, #ChDivKeyWord, #keywords_zh, .keywords-cn")
    zh = _keywords_from_trans_block(zh_el)
    if not zh:
        # 兜底：整页搜 mtky（有的模板 script 不在 keyword_trans 内）
        zh = _keywords_from_mtky_script(soup)

    en_el = soup.select_one("#keyword_cn, #ChDivKeyWordEn, #keywords_en, .keywords-en")
    en = _keywords_from_el(en_el)

    if primary:
        if _is_mostly_cjk(primary):
            zh = zh or primary
        else:
            en = en or primary

    if not zh and not en:
        kw_el = soup.select_one("p.keywords, #doc-keyword")
        raw = _keywords_from_el(kw_el) if kw_el and kw_el.name == "p" else _keywords_from_trans_block(kw_el)
        if raw:
            if _is_mostly_cjk(raw):
                zh = raw
            else:
                en = raw
    elif not zh and primary and _is_mostly_cjk(primary):
        zh = primary
    elif not en and primary and not _is_mostly_cjk(primary):
        en = primary

    return en, zh


def parse_detail_html(html: str) -> dict:
    detect_captcha_or_login(html)
    soup = _soup(html)

    abstract_primary = _text(soup.select_one("#ChDivSummary"))
    if not abstract_primary:
        abs_block = soup.select_one(".abstract-text")
        if abs_block:
            # 去掉 label / 按钮再取文本
            clone = BeautifulSoup(str(abs_block), "lxml")
            for rem in clone.select(".label, .btn-original, .btn-translate, a.btn-original"):
                rem.decompose()
            abstract_primary = (
                _text(clone).removeprefix("摘要：").removeprefix("摘要:").removeprefix("摘要").strip()
            )

    kw_el = soup.select_one("p.keywords")
    keywords_primary = _keywords_from_el(kw_el)
    if not keywords_primary:
        keywords_primary = _keywords_from_el(soup.select_one("#keyword_cn")) or _keywords_from_trans_block(
            soup.select_one("#keyword_trans")
        )

    labeled = _extract_labeled_fields(soup)
    organs = _text(soup.select_one(".orgn"))
    citation = _text(soup.select_one("#gb7714")) or None
    title = _title_text(soup.select_one("h1.title"))
    if not title:
        title = _title_text(soup.select_one(".h1-scholar"))
    if not title:
        title = _title_text(
            soup.select_one(".wx-tit > h1, .wx-tit-scholar .h1-scholar, .wx-tit-scholar, h1")
        )
    authors = _authors_from_cell(soup.select_one("h3.author"))
    if not authors:
        author_el = soup.select_one("h3.author-scholar, #authorpart")
        if author_el is not None:
            authors = _authors_from_cell(author_el)
            if not authors:
                raw = _text(author_el)
                for prefix in ("作者：", "作者:", "作者"):
                    if raw.startswith(prefix):
                        raw = raw[len(prefix) :].strip()
                        break
                authors = _normalize_authors_text(raw)
    source = _text(soup.select_one("a.journal")) or None
    if not source:
        tip = _text(soup.select_one(".top-tip-scholar a, .top-tip a[href*='journal']"))
        if tip:
            source = tip.strip().rstrip(".") or None
    year = _parse_int(_text(soup.select_one("span.year")))
    cite_count = _parse_int(_text(soup.select_one(".quote em")))
    type_raw = _text(soup.select_one("span.type"))
    doc_type = _map_doc_type(type_raw)

    # 学位类型从 type 文案推断
    degree = labeled.get("degree")
    if not degree and type_raw:
        if "硕士" in type_raw:
            degree = "硕士"
        elif "博士" in type_raw:
            degree = "博士"

    biblio = {
        "volume": labeled.get("volume"),
        "issue": labeled.get("issue"),
        "pages": labeled.get("pages"),
        "publisher": labeled.get("publisher"),
        "publish_place": labeled.get("publish_place"),
        "translator": labeled.get("translator"),
        "degree": degree,
        "degree_place": labeled.get("degree_place") or labeled.get("publish_place"),
        "patent_country": labeled.get("patent_country"),
        "patent_kind": labeled.get("patent_kind"),
        "patent_no": labeled.get("patent_no"),
        "standard_code": labeled.get("standard_code"),
        "publish_date": labeled.get("publish_date"),
    }
    # 授予单位可能标在 labeled.source
    if labeled.get("source") and not source:
        source = labeled["source"]

    biblio = _enrich_from_hidden_inputs(soup, biblio)
    biblio, source, year = enrich_from_top_tip_scholar(
        soup, biblio, source=source, year=year
    )
    hint = biblio.pop("doc_type_hint", None)
    if hint and not doc_type:
        doc_type = hint
    biblio = enrich_from_citation_gbt(biblio, citation)
    doi = _extract_doi(soup, labeled.get("doi") or biblio.pop("doi", None), citation)

    # filename / dbcode（供调试与后续接口）
    cnki_id = _hidden_value(soup, "param-filename")
    dbcode = _hidden_value(soup, "param-dbcode")

    references: list[str] = []
    for li in soup.select("#ReferenceList li, ul.bibliography li"):
        text = _text(li)
        if text:
            references.append(text)

    title_zh = _extract_title_zh(soup, title)
    abstract_en, abstract_zh = _split_bilingual_abstract(soup, abstract_primary)
    keywords_en, keywords_zh = _split_bilingual_keywords(soup, keywords_primary)

    # 主字段：中文文献保留中文；外文文献保留英文，中译进 *_zh
    if _is_mostly_cjk(title):
        abstract_text = abstract_en or abstract_zh or abstract_primary
        keywords = keywords_en or keywords_zh or keywords_primary
        abstract_zh = None
        keywords_zh = None
        title_zh = None
    else:
        abstract_text = abstract_en
        keywords = keywords_en
        # 外文页若只有中译摘要/关键词，仍写入 *_zh（检索靠中译字段）

    title = _compact_title_text(title)

    result = {
        "title": title,
        "authors": authors,
        "organs": organs or None,
        "abstract_text": abstract_text or None,
        "keywords": keywords,
        "title_zh": title_zh,
        "abstract_zh": abstract_zh,
        "keywords_zh": keywords_zh,
        "doi": doi,
        "source": source,
        "year": year,
        "cite_count": cite_count,
        "doc_type": doc_type,
        "citation_gbt": citation,
        "references": references,
        "cnki_id": cnki_id,
        "dbcode": dbcode,
        **{k: v for k, v in biblio.items() if v},
    }
    return result


def merge_biblio_fill(base: dict, extra: dict | None) -> dict:
    """Fill missing biblio keys from extra (e.g. dazhong SourceYear/PageRange)."""
    if not extra:
        return base
    out = dict(base)
    for key, val in extra.items():
        if val is None or val == "":
            continue
        if key == "year":
            if out.get("year") is None:
                out["year"] = int(val) if not isinstance(val, int) else val
            continue
        if not out.get(key):
            out[key] = val
    # SourceYear 已拆成 year/issue/volume；若还有原文可忽略
    return out
