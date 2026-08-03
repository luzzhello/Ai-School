from __future__ import annotations

import json
import threading
from pathlib import Path

from src.models import PaperRecord

_file_locks: dict[str, threading.Lock] = {}
_file_locks_guard = threading.Lock()


def _lock_for(path: Path) -> threading.Lock:
    key = str(path.resolve()) if path.exists() or path.parent.exists() else str(path)
    # resolve 在父目录尚不存在时可能失败；统一用绝对化字符串
    try:
        key = str(path.resolve())
    except OSError:
        key = str(path.absolute())
    with _file_locks_guard:
        lock = _file_locks.get(key)
        if lock is None:
            lock = threading.Lock()
            _file_locks[key] = lock
        return lock


def append_record(path: str | Path, record: PaperRecord) -> None:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    line = json.dumps(record.to_json_dict(), ensure_ascii=False) + "\n"
    with _lock_for(out):
        with out.open("a", encoding="utf-8") as f:
            f.write(line)
