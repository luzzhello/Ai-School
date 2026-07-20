import json

from src.checkpoint import (
    Checkpoint,
    migrate_legacy_shared_checkpoint,
    resolve_checkpoint_path,
)
from src.crawl_search import checkpoint_keyword_key


def test_checkpoint_roundtrip(tmp_path):
    path = tmp_path / "cp.json"
    cp = Checkpoint(path)
    cp.mark_url_done("https://example.com/a")
    cp.mark_done(cnki_id="FILE001")
    cp.set_keyword_page("软件工程", 3)
    cp.set_keyword_turnpage("软件工程", "token!!")
    cp.incr_keyword_fetched("软件工程", 5)
    cp.save()
    cp2 = Checkpoint(path)
    cp2.load()
    assert cp2.is_url_done("https://example.com/a")
    assert cp2.is_done(cnki_id="FILE001")
    assert cp2.get_keyword_page("软件工程") == 3
    assert cp2.get_keyword_turnpage("软件工程") == "token!!"
    assert cp2.get_keyword_fetched("软件工程") == 5


def test_resolve_checkpoint_path_by_lang(tmp_path):
    zh = tmp_path / "checkpoint.json"
    assert resolve_checkpoint_path(zh, rlang="CHINESE") == zh
    assert resolve_checkpoint_path(zh, rlang="FOREIGN") == tmp_path / "checkpoint_en.json"
    en = tmp_path / "custom_en.json"
    assert resolve_checkpoint_path(zh, rlang="FOREIGN", checkpoint_path_en=en) == en


def test_checkpoint_keyword_key_plain():
    assert checkpoint_keyword_key("微服务", "CHINESE") == "微服务"
    assert checkpoint_keyword_key("microservices", "FOREIGN") == "microservices"


def test_migrate_legacy_shared_checkpoint(tmp_path):
    zh = tmp_path / "checkpoint.json"
    en = tmp_path / "checkpoint_en.json"
    zh.write_text(
        json.dumps(
            {
                "done_urls": ["https://a"],
                "done_ids": ["ID1"],
                "keyword_pages": {"软件工程": 2, "software::foreign": 3},
                "keyword_turnpage": {"software::foreign": "t"},
                "keyword_fetched": {"软件工程": 10, "software::foreign": 7},
                "details_today": 5,
                "details_date": "2026-07-15",
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    assert migrate_legacy_shared_checkpoint(zh, en) is True
    assert en.exists()
    en_data = json.loads(en.read_text(encoding="utf-8"))
    zh_data = json.loads(zh.read_text(encoding="utf-8"))
    assert en_data["keyword_pages"] == {"software": 3}
    assert en_data["keyword_fetched"] == {"software": 7}
    assert en_data["done_ids"] == ["ID1"]
    assert zh_data["keyword_pages"] == {"软件工程": 2}
    assert "software::foreign" not in zh_data["keyword_fetched"]
    # 已拆过则不再迁移
    assert migrate_legacy_shared_checkpoint(zh, en) is False
