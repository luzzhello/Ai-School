from src.runner import run_crawl


def test_run_crawl_splits_quota_by_year(monkeypatch, tmp_path):
    """多年份时逐年检索，累计上限保证每年均分配额。"""
    search_calls = []

    def fake_iter_search_rows(client, checkpoint, keyword, **kwargs):
        search_calls.append(dict(kwargs))
        # 不实际 yield，只验证调用参数
        return iter(())

    class FakeCp:
        def get_keyword_fetched(self, key):
            return 0

    monkeypatch.setattr("src.runner.iter_search_rows", fake_iter_search_rows)

    total = run_crawl(
        object(),
        FakeCp(),
        ["骑行"],
        max_per_keyword=50,
        from_year=2024,
        to_year=2026,
        output_jsonl=tmp_path / "out.jsonl",
        list_only=True,
        search_lang="chinese",
    )

    assert total == 0
    assert len(search_calls) == 3
    assert [c["from_year"] for c in search_calls] == [2024, 2025, 2026]
    assert [c["to_year"] for c in search_calls] == [2024, 2025, 2026]
    # 累计上限：16 → 33 → 50（50/3 = 16+17+17）
    assert [c["max_per_keyword"] for c in search_calls] == [16, 33, 50]
