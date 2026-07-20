from src.biblio_utils import normalize_page_range, parse_dazhong_payload, parse_source_year
from src.parse import merge_biblio_fill, parse_detail_html
from pathlib import Path

FIX = Path(__file__).parent / "fixtures"


def test_parse_source_year_variants():
    assert parse_source_year("2024(02)") == {"year": 2024, "issue": "2"}
    assert parse_source_year("2024年02期") == {"year": 2024, "issue": "2"}
    assert parse_source_year("2023,34(2)") == {"year": 2023, "volume": "34", "issue": "2"}
    assert parse_source_year("") == {}


def test_parse_source_year_fee_page_format():
    """bar.cnki `.article-source` text: 《刊名》, 2026年, 10期"""
    got = parse_source_year("《西部素质教育》, 2026年, 10期")
    assert got == {"source": "西部素质教育", "year": 2026, "issue": "10"}


def test_normalize_page_range():
    assert normalize_page_range("37-42") == "37-42"
    assert normalize_page_range("37_42") == "37-42"
    assert normalize_page_range("第156-160页") == "156-160"
    assert normalize_page_range("（第156-160页）") == "156-160"
    assert normalize_page_range("0") is None


def test_parse_dazhong_payload():
    parsed = parse_dazhong_payload(
        {
            "SourceYear": "《西部素质教育》, 2026年, 10期",
            "PageRange": "156-160",
            "ClassifiedPublicationName": "<期刊> · 社会科学II辑-高等教育",
            "Title": "OBE理念下AI赋能Python程序设计课程教学改革研究 马玉花; 156_160",
        }
    )
    assert parsed["year"] == 2026
    assert parsed["issue"] == "10"
    assert parsed["pages"] == "156-160"
    assert parsed["source"] == "西部素质教育"


def test_detail_fixture_reads_page_pange():
    html = (FIX / "detail_sample.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail["pages"] == "45-52"
    assert detail.get("cnki_id") == "SAMPLE001"


def test_parse_fee_article_source_html():
    from src.crawl_dazhong import parse_fee_article_source_html

    html = """
    <div class="article-info">
      <div class="article-source">《西部素质教育》, 2026年, 10期
        <div class="article-pages">【页 数】 5 页<span>（ 第156-160页 ）</span></div>
      </div>
    </div>
    """
    got = parse_fee_article_source_html(html)
    assert got["source"] == "西部素质教育"
    assert got["year"] == 2026
    assert got["issue"] == "10"
    assert got["pages"] == "156-160"


def test_parse_fee_article_source_sibling_pages():
    """fee_DZhy2_GB: .article-pages is sibling of .article-source under .article-info."""
    from src.crawl_dazhong import parse_fee_article_source_html

    html = """
    <div class="article-info">
      <div class="article-source">· 《参花》, 2024年, 17期</div>
      <div class="article-pages">【页 数】 3 页<span>（ 第154-156页 ）</span></div>
    </div>
    """
    got = parse_fee_article_source_html(html)
    assert got["source"] == "参花"
    assert got["year"] == 2024
    assert got["issue"] == "17"
    assert got["pages"] == "154-156"


def test_merge_biblio_fill_only_blanks():
    base = {"pages": "1-2", "year": 2020}
    extra = {"pages": "9-10", "issue": "3", "year": 2024}
    out = merge_biblio_fill(base, extra)
    assert out["pages"] == "1-2"
    assert out["year"] == 2020
    assert out["issue"] == "3"
