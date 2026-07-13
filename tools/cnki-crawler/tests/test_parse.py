from pathlib import Path

from src.parse import parse_detail_html, parse_list_html

FIX = Path(__file__).parent / "fixtures"


def test_parse_list_extracts_rows():
    html = (FIX / "list_sample.html").read_text(encoding="utf-8")
    rows = parse_list_html(html)
    assert len(rows) >= 1
    assert rows[0]["title"]
    assert rows[0]["detail_url"]


def test_parse_detail_extracts_abstract():
    html = (FIX / "detail_sample.html").read_text(encoding="utf-8")
    detail = parse_detail_html(html)
    assert detail.get("abstract_text")
    assert isinstance(detail.get("references"), list)
