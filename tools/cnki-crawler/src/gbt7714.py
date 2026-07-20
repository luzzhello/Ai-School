"""GB/T 7714 式引文：一律英文半角标点，紧凑无多余空格（知网导出风格）。"""
from __future__ import annotations

import re

from src.models import PaperRecord

_FULLWIDTH = str.maketrans(
    {
        "，": ",",
        "。": ".",
        "：": ":",
        "；": ";",
        "（": "(",
        "）": ")",
        "【": "[",
        "】": "]",
        "［": "[",
        "］": "]",
        "－": "-",
        "—": "-",
        "–": "-",
        "～": "-",
        "　": " ",
    }
)

_DOCTYPE_TAG = re.compile(r"\[\s*([A-Za-z])\s*\]")
_DOI_PREFIX = re.compile(r"(?i)\bDOI\s*:\s*")
_MULTI_SPACE = re.compile(r"[ \t]{2,}")
_BOOK_MARKS = re.compile(r"[《》〈〉]")
# 标点两侧多余空格（保留英文单词间空格）
_SPACE_AROUND_PUNCT = re.compile(r"\s*([.,:;[\]()])\s*")
_SPACE_AROUND_HYPHEN = re.compile(r"(\d)\s*-\s*(\d)")


def _s(v: str | None) -> str:
    return (v or "").strip()


def _clean_title(title: str | None) -> str:
    t = _BOOK_MARKS.sub("", _s(title))
    return _MULTI_SPACE.sub(" ", t).strip()


def _is_english_paper(paper: PaperRecord) -> bool:
    title = _s(paper.title)
    if not title:
        return False
    cjk = sum(1 for ch in title if "\u4e00" <= ch <= "\u9fff")
    return cjk < 2


def _authors(paper: PaperRecord) -> str:
    raw = _s(paper.authors)
    if not raw:
        return ""
    # 统一分隔符
    parts = re.split(r"[;；、,/|]+", raw)
    names = [_MULTI_SPACE.sub(" ", p).strip() for p in parts if p and p.strip()]
    if not names:
        return ""
    return ",".join(names)


def _vol_issue(volume: str | None, issue: str | None) -> str:
    v, i = _s(volume), _s(issue)
    if v and i:
        return f"{v}({i})"
    if v:
        return v
    if i:
        return f"({i})"
    return ""


def _pages(pages: str | None) -> str:
    p = _s(pages).translate(_FULLWIDTH)
    p = _SPACE_AROUND_HYPHEN.sub(r"\1-\2", p)
    return p.replace(" ", "")


def _doi(doi: str | None) -> str:
    d = _s(doi)
    if not d:
        return ""
    d = re.sub(r"(?i)^DOI\s*:\s*", "", d)
    return d.strip()


def normalize_citation_gbt(text: str | None) -> str:
    """清洗已有引文：全角→半角、去书名号、标点紧贴、DOI 规范化。"""
    if not text:
        return ""
    s = str(text).strip().translate(_FULLWIDTH)
    s = _BOOK_MARKS.sub("", s)
    s = s.replace("\r", " ").replace("\n", " ")
    s = _DOI_PREFIX.sub("DOI:", s)
    s = _DOCTYPE_TAG.sub(lambda m: f"[{m.group(1).upper()}]", s)
    # 先处理页码连字符，再压标点两侧空格
    s = _SPACE_AROUND_HYPHEN.sub(r"\1-\2", s)
    # 标点紧贴：. , : ; [ ] ( ) 两侧去空格，再按规则补回
    s = _SPACE_AROUND_PUNCT.sub(r"\1", s)
    s = re.sub(r"(?i)DOI:\s+", "DOI:", s)
    s = _MULTI_SPACE.sub(" ", s).strip()
    while s.endswith(".."):
        s = s[:-1]
    if s and not s.endswith("."):
        s += "."
    return s


def format_gbt7714(paper: PaperRecord) -> str:
    """按主要类型拼 GB/T 7714 式著录（缺字段则跳过该段）。一律英文半角。"""
    dtype = (paper.doc_type or "J").upper()
    if dtype == "P":
        body = _format_patent(paper)
    elif dtype == "S":
        body = _format_standard(paper)
    elif dtype == "M":
        body = _format_monograph(paper)
    elif dtype == "C":
        body = _format_proceedings(paper)
    elif dtype == "D":
        body = _format_thesis(paper)
    else:
        body = _format_journal(paper)
    return normalize_citation_gbt(body)


def _append_doi(body: str, paper: PaperRecord) -> str:
    d = _doi(paper.doi)
    if d:
        # 页码/正文末已有句点时直接接 DOI，不再多加句点
        if not body.endswith("."):
            body += "."
        body += f"DOI:{d}."
    return body


def _format_journal(paper: PaperRecord) -> str:
    # 作者.题名[J].刊名,年,卷(期):页.DOI:xxx
    a, t, src = _authors(paper), _clean_title(paper.title), _s(paper.source)
    body = ""
    if a:
        body += f"{a}."
    body += f"{t}[J]." if t else "[J]."
    chunks: list[str] = []
    if src:
        chunks.append(src.rstrip("."))
    if paper.year:
        chunks.append(str(paper.year))
    vi = _vol_issue(paper.volume, paper.issue)
    if vi:
        chunks.append(vi)
    body += ",".join(chunks)
    if paper.pages:
        body += f":{_pages(paper.pages)}"
    body += "."
    return _append_doi(body, paper)


def _place_publisher(place: str | None, publisher: str | None) -> str:
    p, pub = _s(place), _s(publisher)
    if p and pub:
        return f"{p}:{pub}"
    return p or pub


def _format_monograph(paper: PaperRecord) -> str:
    a = _authors(paper)
    title = _clean_title(paper.title) or _s(paper.source)
    body = ""
    if a:
        body += f"{a}."
    body += f"{title}[M]" if title else "[M]"
    if paper.translator:
        body += f",({_s(paper.translator)})"
    body += "."
    place_pub = _place_publisher(paper.publish_place, paper.publisher)
    bits: list[str] = []
    if place_pub:
        bits.append(place_pub)
    if paper.year:
        bits.append(str(paper.year))
    elif paper.publish_date:
        bits.append(_s(paper.publish_date))
    if paper.pages:
        bits.append(_pages(paper.pages))
    body += ",".join(bits)
    body += "."
    return _append_doi(body, paper)


def _format_proceedings(paper: PaperRecord) -> str:
    a, t, src = _authors(paper), _clean_title(paper.title), _s(paper.source)
    body = ""
    if a:
        body += f"{a}."
    body += f"{t}[C]." if t else "[C]."
    if src:
        body += f"{src}."
    place_pub = _place_publisher(paper.publish_place, paper.publisher)
    bits: list[str] = []
    if place_pub:
        bits.append(place_pub)
    if paper.year:
        bits.append(str(paper.year))
    if paper.pages:
        bits.append(_pages(paper.pages))
    body += ",".join(bits)
    body += "."
    return _append_doi(body, paper)


def _format_thesis(paper: PaperRecord) -> str:
    a, t = _authors(paper), _clean_title(paper.title)
    body = ""
    if a:
        body += f"{a}."
    body += f"{t}[D]" if t else "[D]"
    degree = _s(paper.degree)
    if degree:
        if degree in ("硕士", "博士", "学士") and not degree.endswith("学位论文"):
            degree = f"{degree}学位论文"
        body += f":[{degree}]"
    body += "."
    place = _s(paper.degree_place) or _s(paper.publish_place)
    unit = _s(paper.source)
    if place and unit:
        body += f"{place}:{unit}"
    else:
        body += unit or place
    if paper.year:
        body += f",{paper.year}"
    body += "."
    return body


def _format_patent(paper: PaperRecord) -> str:
    a, t = _authors(paper), _clean_title(paper.title)
    body = ""
    if a:
        body += f"{a}."
    body += f"{t}[P]." if t else "[P]."
    mid = [
        x
        for x in (
            _s(paper.patent_country),
            _s(paper.patent_kind),
            _s(paper.patent_no),
            _s(paper.publish_date) or (str(paper.year) if paper.year else ""),
        )
        if x
    ]
    body += ",".join(mid) + "."
    return body


def _format_standard(paper: PaperRecord) -> str:
    issuer = _s(paper.authors) or _s(paper.source)
    code, title = _s(paper.standard_code), _clean_title(paper.title)
    body = ""
    if issuer:
        body += f"{issuer}."
    if code:
        body += f"{code}."
    body += f"{title}[S]." if title else "[S]."
    place_pub = _place_publisher(paper.publish_place, paper.publisher)
    date = _s(paper.publish_date) or (str(paper.year) if paper.year else "")
    if place_pub and date:
        body += f"{place_pub},{date}"
    elif place_pub:
        body += place_pub
    elif date:
        body += date
    body += "."
    return body
