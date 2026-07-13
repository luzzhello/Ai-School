from src.gbt7714 import format_gbt7714
from src.models import PaperRecord


def test_journal_citation():
    p = PaperRecord(
        authors="张三;李四",
        title="面向微服务的软件架构研究",
        doc_type="J",
        source="软件学报",
        year=2023,
        doi="10.1234/abc",
    )
    cite = format_gbt7714(p)
    assert "张三" in cite
    assert "[J]" in cite
    assert "软件学报" in cite
    assert "2023" in cite
    assert "DOI:10.1234/abc" in cite


def test_thesis_uses_d_tag():
    p = PaperRecord(authors="王五", title="某某研究", doc_type="D", source="某某大学", year=2022)
    assert "[D]" in format_gbt7714(p)
