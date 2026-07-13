from src.crawl_detail import merge_list_and_detail


def test_merge_sets_incomplete_without_abstract():
    row = {
        "cnki_id": "X1",
        "title": "面向微服务的软件架构研究",
        "authors": "张三",
        "source": "软件学报",
        "year": 2023,
        "doc_type": "J",
        "cite_count": 12,
        "detail_url": "https://example.com/a",
        "crawl_keyword": "软件工程",
    }
    paper = merge_list_and_detail(row, {"doi": "10.1/x", "references": ["[1] a"]})
    assert paper.title.startswith("面向")
    assert paper.status == "incomplete"
    assert paper.citation_gbt
    assert paper.title_hash
    assert paper.references == ["[1] a"]


def test_merge_active_when_abstract_and_keywords():
    row = {"title": "t", "crawl_keyword": "k"}
    detail = {"abstract_text": "摘要内容", "keywords": "微服务;架构", "citation_gbt": "custom"}
    paper = merge_list_and_detail(row, detail)
    assert paper.status == "active"
    assert paper.citation_gbt == "custom"
