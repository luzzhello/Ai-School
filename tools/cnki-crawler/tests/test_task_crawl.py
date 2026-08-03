from src.config_loader import load_config
from src.task_crawl import parse_keywords, run_bilingual_task, run_task


def test_load_config_allows_empty_cookie_when_flag(tmp_path):
    config_path = tmp_path / "config.yaml"
    config_path.write_text("cookie: ''\n", encoding="utf-8")

    config = load_config(config_path, allow_empty_cookie=True)

    assert config["cookie"] == ""


def test_load_config_normalizes_placeholder_cookie_when_flag(tmp_path):
    config_path = tmp_path / "config.yaml"
    config_path.write_text("cookie: REPLACE_WITH_BROWSER_COOKIE\n", encoding="utf-8")

    config = load_config(config_path, allow_empty_cookie=True)

    assert config["cookie"] == ""


def test_parse_keywords_trims_and_ignores_empty_values():
    assert parse_keywords("SpringBoot, 学生选课 ,") == ["SpringBoot", "学生选课"]


def test_run_task_crawls_each_keyword_separately(monkeypatch, tmp_path):
    calls = []

    def fake_run_crawl(client, checkpoint, keywords, **kwargs):
        calls.append((client, checkpoint, keywords, kwargs))
        return len(keywords)

    class FakeClient:
        def __init__(self):
            self.checkpoint = None

        def clone(self, *, checkpoint=None):
            c = FakeClient()
            c.checkpoint = checkpoint
            return c

        def close(self):
            pass

    monkeypatch.setattr("src.task_crawl.run_crawl", fake_run_crawl)
    client = FakeClient()
    checkpoint = object()
    output = tmp_path / "task.jsonl"

    total = run_task(
        client,
        checkpoint,
        ["SpringBoot", "学生选课"],
        max_per_keyword=20,
        output_jsonl=output,
        from_year=None,
        to_year=2026,
        list_only=True,
        search_lang="chinese",
    )

    assert total == 2
    assert sorted(call[2][0] for call in calls) == ["SpringBoot", "学生选课"]
    assert all(call[1] is checkpoint for call in calls)
    assert all(
        call[3]
        == {
            "max_per_keyword": 20,
            "from_year": None,
            "to_year": 2026,
            "output_jsonl": output,
            "max_total": 20,
            "list_only": True,
            "search_lang": "chinese",
        }
        for call in calls
    )


def test_run_bilingual_task_same_quota_for_zh_and_en(monkeypatch, tmp_path):
    calls = []

    def fake_run_crawl(client, checkpoint, keywords, **kwargs):
        calls.append((checkpoint, keywords, kwargs["search_lang"], kwargs["output_jsonl"], kwargs["max_per_keyword"]))
        return kwargs["max_per_keyword"]

    class FakeClient:
        def __init__(self):
            self.checkpoint = None

        def clone(self, *, checkpoint=None):
            c = FakeClient()
            c.checkpoint = checkpoint
            return c

        def close(self):
            pass

    monkeypatch.setattr("src.task_crawl.run_crawl", fake_run_crawl)
    client = FakeClient()
    checkpoint_zh = object()
    checkpoint_en = object()
    output_zh = tmp_path / "zh.jsonl"
    output_en = tmp_path / "en.jsonl"

    zh_n, en_n = run_bilingual_task(
        client,
        checkpoint_zh,
        checkpoint_en,
        ["人工智能"],
        max_per_keyword=15,
        output_jsonl_zh=output_zh,
        output_jsonl_en=output_en,
        from_year=2020,
        to_year=2026,
        list_only=True,
    )

    assert zh_n == 15
    assert en_n == 15
    assert calls == [
        (checkpoint_zh, ["人工智能"], "chinese", output_zh, 15),
        (checkpoint_en, ["人工智能"], "foreign", output_en, 15),
    ]
    assert client.checkpoint is checkpoint_en


def test_crawl_task_cli_allows_empty_cookie(monkeypatch, tmp_path):
    from src import __main__ as cli

    load_calls = []
    run_calls = []

    def fake_load_config(path, *, allow_empty_cookie=False):
        load_calls.append((path, allow_empty_cookie))
        return {"cookie": ""}

    class FakeCheckpoint:
        def __init__(self, path):
            self.path = path

        def load(self):
            pass

    class FakeClient:
        def __init__(self, **kwargs):
            self.kwargs = kwargs
            self.checkpoint = None

        def close(self):
            pass

    def fake_run_task(client, checkpoint, keywords, **kwargs):
        run_calls.append((client, checkpoint, keywords, kwargs))
        return 2

    monkeypatch.setattr(cli, "load_config", fake_load_config)
    monkeypatch.setattr(cli, "Checkpoint", FakeCheckpoint)
    monkeypatch.setattr(cli, "CnkiHttpClient", FakeClient)
    monkeypatch.setattr("src.task_crawl.run_task", fake_run_task)

    result = cli.main(
        [
            "--config",
            str(tmp_path / "config.yaml"),
            "crawl-task",
            "--keywords",
            "SpringBoot, 学生选课 ,",
            "--output",
            str(tmp_path / "task.jsonl"),
            "--checkpoint",
            str(tmp_path / "task_cp.json"),
            "--list-only",
        ]
    )

    assert result == 0
    assert load_calls == [(str(tmp_path / "config.yaml"), True)]
    assert run_calls[0][2] == ["SpringBoot", "学生选课"]
    assert run_calls[0][3]["max_per_keyword"] == 20
    assert run_calls[0][3]["search_lang"] == "chinese"


def test_crawl_task_cli_keywords_file_utf8(monkeypatch, tmp_path):
    from src import __main__ as cli

    kw_file = tmp_path / "keywords.txt"
    kw_file.write_text("人工智能\n知识图谱\n", encoding="utf-8")
    run_calls = []

    class FakeCheckpoint:
        def __init__(self, path):
            self.path = path

        def load(self):
            return None

    class FakeClient:
        def __init__(self, **kwargs):
            self.checkpoint = None

        def close(self):
            pass

    def fake_run_task(client, checkpoint, keywords, **kwargs):
        run_calls.append(keywords)
        return 1

    monkeypatch.setattr(cli, "load_config", lambda *a, **k: {"cookie": ""})
    monkeypatch.setattr(cli, "Checkpoint", FakeCheckpoint)
    monkeypatch.setattr(cli, "CnkiHttpClient", FakeClient)
    monkeypatch.setattr("src.task_crawl.run_task", fake_run_task)

    result = cli.main(
        [
            "--config",
            str(tmp_path / "config.yaml"),
            "crawl-task",
            "--keywords-file",
            str(kw_file),
            "--output",
            str(tmp_path / "task.jsonl"),
            "--checkpoint",
            str(tmp_path / "task_cp.json"),
            "--list-only",
        ]
    )

    assert result == 0
    assert run_calls[0] == ["人工智能", "知识图谱"]


def test_crawl_task_cli_both_requires_en_paths(monkeypatch, tmp_path):
    from src import __main__ as cli

    monkeypatch.setattr(cli, "load_config", lambda *a, **k: {"cookie": ""})
    monkeypatch.setattr(cli, "CnkiHttpClient", lambda **kwargs: type("C", (), {"close": lambda self: None})())

    result = cli.main(
        [
            "--config",
            str(tmp_path / "config.yaml"),
            "crawl-task",
            "--keywords",
            "人工智能",
            "--output",
            str(tmp_path / "zh.jsonl"),
            "--checkpoint",
            str(tmp_path / "zh_cp.json"),
            "--search-lang",
            "both",
            "--list-only",
        ]
    )

    assert result == 2


def test_crawl_task_cli_both_runs_bilingual(monkeypatch, tmp_path):
    from src import __main__ as cli

    bilingual_calls = []

    class FakeCheckpoint:
        def __init__(self, path):
            self.path = path

        def load(self):
            pass

    class FakeClient:
        def __init__(self, **kwargs):
            self.checkpoint = None

        def close(self):
            pass

    def fake_bilingual(*args, **kwargs):
        bilingual_calls.append(kwargs)
        return 3, 3

    monkeypatch.setattr(cli, "load_config", lambda *a, **k: {"cookie": ""})
    monkeypatch.setattr(cli, "Checkpoint", FakeCheckpoint)
    monkeypatch.setattr(cli, "CnkiHttpClient", FakeClient)
    monkeypatch.setattr("src.task_crawl.run_bilingual_task", fake_bilingual)

    out_zh = tmp_path / "zh.jsonl"
    out_en = tmp_path / "en.jsonl"
    result = cli.main(
        [
            "--config",
            str(tmp_path / "config.yaml"),
            "crawl-task",
            "--keywords",
            "人工智能",
            "--max-per-keyword",
            "10",
            "--output",
            str(out_zh),
            "--checkpoint",
            str(tmp_path / "zh_cp.json"),
            "--output-en",
            str(out_en),
            "--checkpoint-en",
            str(tmp_path / "en_cp.json"),
            "--search-lang",
            "both",
            "--list-only",
        ]
    )

    assert result == 0
    assert bilingual_calls[0]["max_per_keyword"] == 10
    assert bilingual_calls[0]["output_jsonl_zh"] == str(out_zh)
    assert bilingual_calls[0]["output_jsonl_en"] == str(out_en)
    assert bilingual_calls[0]["list_only"] is True
