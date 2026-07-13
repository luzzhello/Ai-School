from __future__ import annotations

import json
from pathlib import Path


class Checkpoint:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.done_urls: set[str] = set()
        self.keyword_pages: dict[str, int] = {}
        self.details_today: int = 0
        self.details_date: str | None = None

    def load(self) -> None:
        if not self.path.exists():
            return
        data = json.loads(self.path.read_text(encoding="utf-8"))
        self.done_urls = set(data.get("done_urls") or [])
        self.keyword_pages = dict(data.get("keyword_pages") or {})
        self.details_today = int(data.get("details_today") or 0)
        self.details_date = data.get("details_date")

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "done_urls": sorted(self.done_urls),
            "keyword_pages": self.keyword_pages,
            "details_today": self.details_today,
            "details_date": self.details_date,
        }
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def mark_url_done(self, url: str) -> None:
        self.done_urls.add(url)

    def is_url_done(self, url: str) -> bool:
        return url in self.done_urls

    def set_keyword_page(self, keyword: str, page: int) -> None:
        self.keyword_pages[keyword] = page

    def get_keyword_page(self, keyword: str) -> int:
        return int(self.keyword_pages.get(keyword, 1))
