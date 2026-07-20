import json

from src.crawl_search import build_query_json, normalize_rlang


def test_normalize_rlang():
    assert normalize_rlang("chinese") == "CHINESE"
    assert normalize_rlang("foreign") == "FOREIGN"
    assert normalize_rlang("外文") == "FOREIGN"
    assert normalize_rlang(None) == "CHINESE"


def test_build_query_json_foreign_matches_capture():
    raw = build_query_json(
        "The International Congress on Hazardous",
        from_year=None,
        to_year=None,
        search_from=1,
        rlang="foreign",
    )
    q = json.loads(raw)
    assert q["Rlang"] == "FOREIGN"
    assert q["View"] == "changeDBCh"
    assert q["Resource"] == "CROSSDB"
    assert q["SearchType"] == 2
    assert "YSTT4HG0" in q["KuaKuCode"]
    assert q["QNode"]["QGroup"][0]["Items"][0]["Value"] == "The International Congress on Hazardous"


def test_build_query_json_chinese_has_no_change_view():
    raw = build_query_json("软件工程", from_year=2020, to_year=2026, search_from=1, rlang="chinese")
    q = json.loads(raw)
    assert q["Rlang"] == "CHINESE"
    assert "View" not in q
