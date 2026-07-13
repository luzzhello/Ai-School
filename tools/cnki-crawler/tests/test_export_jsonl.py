from src.export_jsonl import append_record
from src.models import PaperRecord


def test_append_jsonl(tmp_path):
    path = tmp_path / "out.jsonl"
    append_record(path, PaperRecord(title="t1", cnki_id="x"))
    append_record(path, PaperRecord(title="t2", cnki_id="y"))
    lines = path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 2
