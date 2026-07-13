from __future__ import annotations

import json
from pathlib import Path

from src.models import PaperRecord


def append_record(path: str | Path, record: PaperRecord) -> None:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record.to_json_dict(), ensure_ascii=False) + "\n")
