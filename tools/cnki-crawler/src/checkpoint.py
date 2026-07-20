from __future__ import annotations

import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

# 旧版单文件中外文关键词进度后缀；拆文件后可迁移剥离
_LEGACY_FOREIGN_SUFFIX = "::foreign"


def resolve_checkpoint_path(
    checkpoint_path: str | Path | None = None,
    *,
    rlang: str = "CHINESE",
    checkpoint_path_en: str | Path | None = None,
) -> Path:
    """按语言选择断点文件：中文 checkpoint.json，外文 checkpoint_en.json。"""
    zh = Path(checkpoint_path or "data/checkpoint.json")
    if str(rlang).upper() == "FOREIGN":
        if checkpoint_path_en:
            return Path(checkpoint_path_en)
        return zh.with_name(f"{zh.stem}_en{zh.suffix}")
    return zh


def _filter_keyword_map(
    data: dict[str, object],
    *,
    foreign: bool,
) -> dict[str, object]:
    out: dict[str, object] = {}
    for key, val in data.items():
        is_foreign = str(key).endswith(_LEGACY_FOREIGN_SUFFIX)
        if foreign and is_foreign:
            out[str(key)[: -len(_LEGACY_FOREIGN_SUFFIX)]] = val
        elif foreign and not is_foreign:
            continue
        elif (not foreign) and is_foreign:
            continue
        else:
            out[str(key)] = val
    return out


def migrate_legacy_shared_checkpoint(
    zh_path: Path,
    en_path: Path,
) -> bool:
    """若外文文件尚不存在、且旧中文文件含 ``词::foreign``，拆出外文进度。

    - 外文新文件：外文关键词进度 + 全量 done_*（保守跳过，避免重复抓）
    - 中文文件：去掉 ``::foreign`` 键，保留中文进度与 done_*
    返回是否执行了迁移。
    """
    if en_path.exists() or not zh_path.exists():
        return False
    raw = zh_path.read_text(encoding="utf-8").strip()
    if not raw:
        return False
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return False
    if not isinstance(data, dict):
        return False

    has_foreign = any(
        str(k).endswith(_LEGACY_FOREIGN_SUFFIX)
        for mapping in (
            data.get("keyword_pages") or {},
            data.get("keyword_turnpage") or {},
            data.get("keyword_fetched") or {},
        )
        for k in mapping
    )
    if not has_foreign:
        return False

    pages = dict(data.get("keyword_pages") or {})
    turns = dict(data.get("keyword_turnpage") or {})
    fetched = dict(data.get("keyword_fetched") or {})

    en_payload = {
        "done_urls": list(data.get("done_urls") or []),
        "done_ids": list(data.get("done_ids") or []),
        "keyword_pages": _filter_keyword_map(pages, foreign=True),
        "keyword_turnpage": _filter_keyword_map(turns, foreign=True),
        "keyword_fetched": {
            k: int(v) for k, v in _filter_keyword_map(fetched, foreign=True).items()
        },
        "details_today": 0,
        "details_date": None,
    }
    zh_payload = {
        "done_urls": list(data.get("done_urls") or []),
        "done_ids": list(data.get("done_ids") or []),
        "keyword_pages": _filter_keyword_map(pages, foreign=False),
        "keyword_turnpage": _filter_keyword_map(turns, foreign=False),
        "keyword_fetched": {
            k: int(v) for k, v in _filter_keyword_map(fetched, foreign=False).items()
        },
        "details_today": int(data.get("details_today") or 0),
        "details_date": data.get("details_date"),
    }

    en_path.parent.mkdir(parents=True, exist_ok=True)
    en_path.write_text(json.dumps(en_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    zh_path.write_text(json.dumps(zh_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info(
        "migrated legacy checkpoint: foreign keys -> %s ; cleaned %s",
        en_path,
        zh_path,
    )
    return True


class Checkpoint:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.done_urls: set[str] = set()
        self.done_ids: set[str] = set()
        self.keyword_pages: dict[str, int] = {}
        self.keyword_turnpage: dict[str, str] = {}
        self.keyword_fetched: dict[str, int] = {}
        self.details_today: int = 0
        self.details_date: str | None = None

    def load(self) -> None:
        if not self.path.exists():
            return
        raw = self.path.read_text(encoding="utf-8").strip()
        if not raw:
            return
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            # 空文件/手动删坏后的残文件：视为无断点，避免直接崩
            return
        if not isinstance(data, dict):
            return
        self.done_urls = set(data.get("done_urls") or [])
        self.done_ids = set(data.get("done_ids") or [])
        self.keyword_pages = dict(data.get("keyword_pages") or {})
        self.keyword_turnpage = dict(data.get("keyword_turnpage") or {})
        self.keyword_fetched = {
            k: int(v) for k, v in (data.get("keyword_fetched") or {}).items()
        }
        self.details_today = int(data.get("details_today") or 0)
        self.details_date = data.get("details_date")

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "done_urls": sorted(self.done_urls),
            "done_ids": sorted(self.done_ids),
            "keyword_pages": self.keyword_pages,
            "keyword_turnpage": self.keyword_turnpage,
            "keyword_fetched": self.keyword_fetched,
            "details_today": self.details_today,
            "details_date": self.details_date,
        }
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def mark_done(self, *, url: str | None = None, cnki_id: str | None = None) -> None:
        if url:
            self.done_urls.add(url)
        if cnki_id:
            self.done_ids.add(cnki_id)

    def mark_url_done(self, url: str) -> None:
        self.done_urls.add(url)

    def is_url_done(self, url: str) -> bool:
        return url in self.done_urls

    def is_done(self, *, url: str | None = None, cnki_id: str | None = None) -> bool:
        if cnki_id and cnki_id in self.done_ids:
            return True
        if url and url in self.done_urls:
            return True
        return False

    def set_keyword_page(self, keyword: str, page: int) -> None:
        self.keyword_pages[keyword] = page

    def get_keyword_page(self, keyword: str) -> int:
        return int(self.keyword_pages.get(keyword, 1))

    def set_keyword_turnpage(self, keyword: str, turnpage: str) -> None:
        if turnpage:
            self.keyword_turnpage[keyword] = turnpage
        elif keyword in self.keyword_turnpage:
            del self.keyword_turnpage[keyword]

    def get_keyword_turnpage(self, keyword: str) -> str:
        return self.keyword_turnpage.get(keyword) or ""

    def incr_keyword_fetched(self, keyword: str, n: int = 1) -> int:
        self.keyword_fetched[keyword] = int(self.keyword_fetched.get(keyword, 0)) + n
        return self.keyword_fetched[keyword]

    def get_keyword_fetched(self, keyword: str) -> int:
        return int(self.keyword_fetched.get(keyword, 0))
