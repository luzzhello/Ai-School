from __future__ import annotations

import json
from pathlib import Path


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
        data = json.loads(self.path.read_text(encoding="utf-8"))
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
