from pathlib import Path

from src.parse import normalize_doi, parse_detail_html, parse_list_html

FIX = Path(__file__).parent / "fixtures"


def test_parse_list_extracts_rows():
    html = (FIX / "list_sample.html").read_text(encoding="utf-8")
    rows = parse_list_html(html)
    assert len(rows) >= 1
    assert rows[0]["title"]
    assert rows[0]["detail_url"]
    assert rows[0]["authors"] == "张三,李四"


def test_parse_list_strips_highlight_spaces_and_joins_authors_with_comma():
    html = (FIX / "list_highlight.html").read_text(encoding="utf-8")
    rows = parse_list_html(html)
    assert len(rows) == 1
    assert rows[0]["title"] == "山东省公路工程建设标准化需求分析及标准体系构建研究"
    assert " " not in rows[0]["title"]
    assert rows[0]["authors"] == "王晓燕,王继凯,王俊,刘厚铄"


def test_normalize_authors_from_space_separated_cn():
    from src.parse import _normalize_authors_text

    assert _normalize_authors_text("王晓燕 王继凯 王俊") == "王晓燕,王继凯,王俊"
    assert _normalize_authors_text("张三; 李四") == "张三,李四"
    assert _normalize_authors_text("Liu Y; Wang Z") == "Liu Y,Wang Z"
    assert _normalize_authors_text("杨晓帆 凌赐免 林志远 David LO 陈翔") == "杨晓帆,凌赐免,林志远,David LO,陈翔"
    assert _normalize_authors_text("王璐瑶,沈科迪,万志远,David,LO,刘宁") == "王璐瑶,沈科迪,万志远,David LO,刘宁"




def test_parse_detail_extracts_abstract():
    html = (FIX / "detail_sample.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail.get("abstract_text")
    assert isinstance(detail.get("references"), list)
    assert detail.get("pages") == "45-52"
    assert detail.get("volume") == "34"
    assert detail.get("issue") == "2"
    assert detail.get("doi") == "10.1234/abc.2023.001"


def test_parse_cn_top_tip_source_year_volume_issue_pages():
    """中文详情 `.top-tip` 已含来源/年/卷/期/页，勿依赖 bar.cnki；并覆盖错误 page-pange。"""
    html = (FIX / "detail_cn_toptip.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail.get("source") and "电子设计工程" in detail["source"]
    assert detail.get("year") == 2026
    assert detail.get("volume") == "34"
    assert detail.get("issue") == "14"
    assert detail.get("pages") == "19-24"
    assert detail.get("pages") != "25-30"



def test_parse_foreign_detail_zh_fields():
    """旧 fixture：title-translate + 显式中英摘要（非 scholar 布局）。"""
    html = (FIX / "detail_foreign_zh.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert "Campus Second-Hand" in (detail.get("title") or "")
    assert detail.get("title_zh") and "二手教材" in detail["title_zh"]
    assert detail.get("abstract_zh") and "二手教材" in detail["abstract_zh"]
    assert detail.get("abstract_text") and "textbook" in detail["abstract_text"].lower()
    assert detail.get("keywords_zh") and "二手教材" in detail["keywords_zh"]
    assert detail.get("keywords") and "textbook" in detail["keywords"].lower()
    assert detail.get("doc_type") == "J"
    assert detail.get("volume") == "10"
    assert detail.get("issue") == "6"
    assert detail.get("year") == 2024
    assert "International Core Journal of Engineering" in (detail.get("source") or "")


def test_parse_foreign_scholar_hidden_zh_fields():
    """真实外文知网节：中译摘要/关键词在 display:none 的 ChDivSummary2 / keyword_trans。"""
    html = (FIX / "detail_foreign_scholar.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert "Student Management System" in (detail.get("title") or "")
    assert detail.get("authors") and "Yichun Tang" in detail["authors"]
    assert detail.get("abstract_text") and "schools is constantly expanding" in detail["abstract_text"]
    assert detail.get("abstract_zh") and "学生" in detail["abstract_zh"]
    assert detail.get("keywords") and "springboot" in detail["keywords"].lower()
    assert detail.get("keywords_zh") and "个人健康信息" in detail["keywords_zh"]
    assert "MySQL数据库" in detail["keywords_zh"]
    assert detail.get("volume") == "12721"
    assert detail.get("year") == 2023
    assert detail.get("pages") and "1272107" in detail["pages"]
    assert detail.get("title_zh") and "学生管理" in detail["title_zh"]


def test_parse_detail_rowtit_doi():
    """Current CNKI detail pages use .rowtit, not .label."""
    html = (FIX / "detail_rowtit.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail.get("doi") == "10.13328/j.cnki.jos.007001"
    assert detail.get("volume") == "37"
    assert detail.get("issue") == "6"
    assert detail.get("pages") == "1001-1020"


def test_title_strips_mt_translate_button():
    detail = parse_detail_html(
        "<html><body>"
        "<div class='wx-tit-scholar'>"
        "<div class='h1-scholar'>Design and Implementation of Online Ordering System Based on SpringBoot"
        "<span class='mt-trans' onclick='x'>MT翻译</span></div>"
        "</div>"
        "<span id='ChDivSummary'>abstract english text here for length ok</span>"
        "<input id='param-filename' value='T1'/>"
        "</body></html>"
    )
    assert detail.get("title")
    assert "MT翻译" not in detail["title"]
    assert detail["title"].endswith("SpringBoot")


def test_title_strips_corr_video_and_keywords_no_double_semicolon():
    detail = parse_detail_html(
        "<html><body>"
        "<div class='wx-tit'><h1>“十五五”视域下公共图书馆地方文献适老化阅读推广路径研究"
        "<span id='corr-video' class='type' style='display: none'>附视频</span></h1></div>"
        "<p class='keywords'>"
        "<a>“十五五”;</a><a>;</a><a>公共图书馆;</a><a>地方文献;</a>"
        "</p>"
        "<span id='ChDivSummary'>摘要正文内容足够长即可</span>"
        "<input id='param-filename' value='CN1'/>"
        "</body></html>"
    )
    assert detail.get("title")
    assert "附视频" not in detail["title"]
    assert "适老化阅读推广路径研究" in detail["title"]
    assert detail.get("keywords")
    assert ";;" not in detail["keywords"]
    assert detail["keywords"] == "“十五五”;公共图书馆;地方文献"


def test_normalize_doi():
    assert normalize_doi("10.1234/abc.2023.001") == "10.1234/abc.2023.001"
    assert normalize_doi("https://doi.org/10.1234/abc.2023.001") == "10.1234/abc.2023.001"
    assert (
        normalize_doi("https://link.cnki.net/doi/10.13328/j.cnki.jos.007001")
        == "10.13328/j.cnki.jos.007001"
    )
    assert normalize_doi("pending") is None
    assert normalize_doi("DOI:10.1234/abc.") == "10.1234/abc"
    assert normalize_doi("not-a-doi") is None
