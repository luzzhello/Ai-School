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


def test_merge_prefers_list_title_authors_source():
    """题名/作者/来源以列表为准，不被详情页覆盖。"""
    row = {
        "title": "列表题名",
        "authors": "列表作者",
        "source": "列表来源",
        "year": 2024,
        "crawl_keyword": "springboot",
    }
    detail = {
        "title": "详情题名（不应采用）",
        "authors": "详情作者",
        "source": "详情来源",
        "abstract_text": "摘要",
        "keywords": "关键词",
    }
    paper = merge_list_and_detail(row, detail)
    assert paper.title == "列表题名"
    assert paper.authors == "列表作者"
    assert paper.source == "列表来源"
    assert paper.abstract_text == "摘要"
