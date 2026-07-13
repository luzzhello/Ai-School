from __future__ import annotations

import random
import time
from datetime import date
from typing import Any

import httpx

from src.checkpoint import Checkpoint
from src.parse import CaptchaOrLoginError, detect_captcha_or_login


class RateLimitError(Exception):
    """Raised when CNKI rate-limits or daily detail cap is hit."""


class CnkiHttpClient:
    def __init__(
        self,
        cookie: str,
        user_agent: str,
        list_delay_sec: float = 2.0,
        detail_delay_sec: float = 4.0,
        delay_jitter_sec: float = 1.5,
        daily_detail_limit: int = 800,
        checkpoint: Checkpoint | None = None,
    ) -> None:
        self.list_delay_sec = list_delay_sec
        self.detail_delay_sec = detail_delay_sec
        self.delay_jitter_sec = delay_jitter_sec
        self.daily_detail_limit = daily_detail_limit
        self.checkpoint = checkpoint
        self._client = httpx.Client(
            headers={
                "User-Agent": user_agent,
                "Cookie": cookie,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9",
            },
            follow_redirects=True,
            timeout=60.0,
        )

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> CnkiHttpClient:
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()

    def _sleep(self, base: float) -> None:
        jitter = random.uniform(0, self.delay_jitter_sec) if self.delay_jitter_sec > 0 else 0
        time.sleep(max(0.0, base + jitter))

    def _ensure_daily_quota(self) -> None:
        if self.checkpoint is None:
            return
        today = date.today().isoformat()
        if self.checkpoint.details_date != today:
            self.checkpoint.details_date = today
            self.checkpoint.details_today = 0
            self.checkpoint.save()
        if self.checkpoint.details_today >= self.daily_detail_limit:
            raise RateLimitError(
                f"daily detail limit reached ({self.daily_detail_limit}); resume tomorrow"
            )

    def _bump_detail_count(self) -> None:
        if self.checkpoint is None:
            return
        today = date.today().isoformat()
        if self.checkpoint.details_date != today:
            self.checkpoint.details_date = today
            self.checkpoint.details_today = 0
        self.checkpoint.details_today += 1
        self.checkpoint.save()

    def get(self, url: str, *, is_detail: bool = False) -> str:
        if is_detail:
            self._ensure_daily_quota()
            self._sleep(self.detail_delay_sec)
        else:
            self._sleep(self.list_delay_sec)

        delays = [5.0, 15.0, 45.0]
        last_exc: Exception | None = None
        for attempt, backoff in enumerate([0.0, *delays]):
            if backoff:
                time.sleep(backoff)
            try:
                resp = self._client.get(url)
                if resp.status_code == 429:
                    last_exc = RateLimitError(f"HTTP 429 for {url}")
                    continue
                resp.raise_for_status()
                text = resp.text
                detect_captcha_or_login(text)
                if is_detail:
                    self._bump_detail_count()
                return text
            except CaptchaOrLoginError:
                raise
            except RateLimitError as e:
                last_exc = e
            except Exception as e:
                last_exc = e
        raise RateLimitError(str(last_exc) if last_exc else f"failed to GET {url}")
