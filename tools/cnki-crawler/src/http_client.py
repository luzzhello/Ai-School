from __future__ import annotations

import random
import time
from typing import Any

import httpx

from src.checkpoint import Checkpoint
from src.parse import CaptchaOrLoginError, detect_captcha_or_login


class RateLimitError(Exception):
    """Raised when CNKI rate-limits or daily detail cap is hit."""


def parse_cookie_header(cookie: str) -> httpx.Cookies:
    """把浏览器 Cookie 字符串写入 jar，避免固定 Cookie 头覆盖暖场 Set-Cookie。"""
    jar = httpx.Cookies()
    for part in (cookie or "").split(";"):
        part = part.strip()
        if not part or "=" not in part:
            continue
        name, value = part.split("=", 1)
        name, value = name.strip(), value.strip()
        if not name:
            continue
        # 知网多子域共享；交给 httpx 按 domain 匹配
        jar.set(name, value, domain=".cnki.net")
    return jar


class CnkiHttpClient:
    def __init__(
        self,
        cookie: str,
        user_agent: str,
        list_delay_sec: float = 1.0,
        detail_delay_sec: float = 2.0,
        delay_jitter_sec: float = 0.5,
        daily_detail_limit: int = 800,
        checkpoint: Checkpoint | None = None,
    ) -> None:
        self.cookie = cookie or ""
        self.user_agent = user_agent
        self.list_delay_sec = list_delay_sec
        self.detail_delay_sec = detail_delay_sec
        self.delay_jitter_sec = delay_jitter_sec
        self.daily_detail_limit = daily_detail_limit
        self.checkpoint = checkpoint
        self._client = httpx.Client(
            headers={
                "User-Agent": user_agent,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "sec-ch-ua": '"Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"',
                "sec-ch-ua-mobile": "?0",
                "sec-ch-ua-platform": '"Windows"',
            },
            cookies=parse_cookie_header(self.cookie),
            follow_redirects=True,
            timeout=60.0,
        )

    def clone(self, *, checkpoint: Checkpoint | None = None) -> CnkiHttpClient:
        """每关键词独立 HTTP 会话（httpx.Client 非线程安全）。"""
        return CnkiHttpClient(
            cookie=self.cookie,
            user_agent=self.user_agent,
            list_delay_sec=self.list_delay_sec,
            detail_delay_sec=self.detail_delay_sec,
            delay_jitter_sec=self.delay_jitter_sec,
            daily_detail_limit=self.daily_detail_limit,
            checkpoint=self.checkpoint if checkpoint is None else checkpoint,
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
        if not self.checkpoint.prepare_daily_detail_quota(self.daily_detail_limit):
            raise RateLimitError(
                f"daily detail limit reached ({self.daily_detail_limit}); resume tomorrow"
            )

    def _bump_detail_count(self) -> None:
        if self.checkpoint is None:
            return
        self.checkpoint.bump_daily_detail()

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
