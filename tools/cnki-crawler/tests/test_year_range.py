from datetime import date

from src.year_range import resolve_year_range, split_quota_evenly, year_span_list


def test_split_quota_evenly_fifty_over_three():
    assert split_quota_evenly(50, 3) == [16, 17, 17]


def test_split_quota_evenly_exact():
    assert split_quota_evenly(30, 3) == [10, 10, 10]


def test_split_quota_evenly_zeros():
    assert split_quota_evenly(0, 3) == [0, 0, 0]
    assert split_quota_evenly(10, 0) == []


def test_year_span_list():
    assert year_span_list(2024, 2026) == [2024, 2025, 2026]
    assert year_span_list(2026, 2024) == [2024, 2025, 2026]
    assert year_span_list(None, 2026) is None
    assert year_span_list(2024, None) is None


def test_resolve_year_range_recent_years(monkeypatch):
    class FakeDate:
        @classmethod
        def today(cls):
            return date(2026, 7, 29)

    monkeypatch.setattr("src.year_range.date", FakeDate)
    assert resolve_year_range({"recent_years": 3}) == (2024, 2026)


def test_resolve_year_range_absolute_fallback():
    assert resolve_year_range({"from_year": 2020, "to_year": 2022}) == (2020, 2022)


def test_run_crawl_splits_quota_by_year(monkeypatch, tmp_path):
    from src import runner

    calls = []

    def fake_iter(client, checkpoint, keyword, **kwargs):
        calls.append(
            {
                "keyword": keyword,
                "max_per_keyword": kwargs["max_per_keyword"],
                "from_year": kwargs["from_year"],
                "to_year": kwargs["to_year"],
            }
        )
        return iter([])

    class FakeCp:
        def get_keyword_fetched(self, key):
            return 0

    monkeypatch.setattr(runner, "iter_search_rows", fake_iter)

    total = runner.run_crawl(
        object(),
        FakeCp(),
        ["人工智能"],
        max_per_keyword=50,
        from_year=2024,
        to_year=2026,
        output_jsonl=tmp_path / "out.jsonl",
        list_only=True,
        search_lang="chinese",
    )

    assert total == 0
    assert calls == [
        {"keyword": "人工智能", "max_per_keyword": 16, "from_year": 2024, "to_year": 2024},
        {"keyword": "人工智能", "max_per_keyword": 33, "from_year": 2025, "to_year": 2025},
        {"keyword": "人工智能", "max_per_keyword": 50, "from_year": 2026, "to_year": 2026},
    ]
