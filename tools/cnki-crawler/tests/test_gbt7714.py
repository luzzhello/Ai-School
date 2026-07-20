from src.gbt7714 import format_gbt7714, normalize_citation_gbt
from src.models import PaperRecord


def test_journal_citation_halfwidth_compact():
    p = PaperRecord(
        authors="张三;李四",
        title="《面向微服务的软件架构研究》",
        doc_type="J",
        source="软件学报",
        year=2023,
        volume="34",
        issue="2",
        pages="45 - 52",
        doi="10.1234/abc",
    )
    cite = format_gbt7714(p)
    assert cite == "张三,李四.面向微服务的软件架构研究[J].软件学报,2023,34(2):45-52.DOI:10.1234/abc."
    assert cite.endswith(".")
    assert "《" not in cite
    assert "（" not in cite
    assert "：" not in cite
    assert "，" not in cite


def test_thesis_uses_d_tag():
    p = PaperRecord(
        authors="王五",
        title="某某研究",
        doc_type="D",
        source="某某大学",
        degree="硕士",
        degree_place="北京",
        year=2022,
    )
    cite = format_gbt7714(p)
    assert cite == "王五.某某研究[D]:[硕士学位论文].北京:某某大学,2022."
    assert cite.endswith(".")


def test_english_journal_halfwidth_compact():
    p = PaperRecord(
        authors="Liu Y;Wang Z",
        title="Campus Second-Hand Textbook Trading Platform based on Vue 3 and Spring Boot",
        doc_type="J",
        source="International Core Journal of Engineering",
        year=2024,
        volume="10",
        issue="6",
        pages="1419-1425",
        doi="10.1007/s11227-025-07904-5",
    )
    cite = format_gbt7714(p)
    assert "Liu Y,Wang Z." in cite
    assert "[J]." in cite
    assert "2024,10(6):1419-1425.DOI:10.1007/s11227-025-07904-5." in cite
    assert cite.endswith(".")
    assert "10（6）" not in cite


def test_english_authors_from_compact_storage():
    p = PaperRecord(
        authors="Zeynab Chitsazian,Saeed Sedighian Kashi,Amin Nikanjam",
        title="Detecting concept drift in just-in-time software defect prediction using model interpretation",
        doc_type="J",
        source="International Journal of System Assurance Engineering and Management",
        year=2026,
        pages="1-22",
        doi="10.1007/S13198-025-03122-7",
    )
    cite = format_gbt7714(p)
    assert cite.startswith(
        "Zeynab Chitsazian,Saeed Sedighian Kashi,Amin Nikanjam.Detecting concept drift"
    )
    assert ", " not in cite.split("[J]")[0]
    assert cite.endswith(".")


def test_normalize_strips_spaces_and_fullwidth():
    raw = "张三。论文标题 [J] . 期刊，2025 , 81 ( 15 ) : 123 - 145 . DOI : 10.xxx"
    out = normalize_citation_gbt(raw)
    assert out == "张三.论文标题[J].期刊,2025,81(15):123-145.DOI:10.xxx."
    assert out.endswith(".")


def test_normalize_english_keeps_word_spaces():
    raw = (
        "Mohammad Ali Keshavarz, Saeed Sharifian. A containerized Edge AI [J] . "
        "The Journal of Supercomputing，2025 ,81 (15) :1419-1419. DOI : 10.1007/s11227-025-07904-5"
    )
    out = normalize_citation_gbt(raw)
    assert "Mohammad Ali Keshavarz,Saeed Sharifian." in out
    assert "A containerized Edge AI[J]." in out
    assert "2025,81(15):1419-1419.DOI:10.1007/s11227-025-07904-5." in out
    assert out.endswith(".")


def test_patent_and_standard():
    patent = PaperRecord(
        authors="张三",
        title="一种检测方法",
        doc_type="P",
        patent_country="中国",
        patent_kind="发明专利",
        patent_no="CN123456A",
        publish_date="2020-01-01",
    )
    assert format_gbt7714(patent) == "张三.一种检测方法[P].中国,发明专利,CN123456A,2020-01-01."

    std = PaperRecord(
        authors="国家标准委",
        title="信息安全技术",
        doc_type="S",
        standard_code="GB/T 1234-2020",
        publish_place="北京",
        publisher="中国标准出版社",
        publish_date="2020",
    )
    cite = format_gbt7714(std)
    assert cite == "国家标准委.GB/T 1234-2020.信息安全技术[S].北京:中国标准出版社,2020."
