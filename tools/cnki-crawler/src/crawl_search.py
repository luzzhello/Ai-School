from __future__ import annotations

import json
import time
from collections.abc import Iterator

from src.checkpoint import Checkpoint
from src.http_client import CnkiHttpClient, RateLimitError
from src.parse import CaptchaOrLoginError, detect_captcha_or_login, parse_list_html

# kns8s brief result grid HTML endpoint (verify against live CNKI; update README if DOM/API changes)
SEARCH_URL = "https://kns.cnki.net/kns8s/brief/grid"


def build_search_payload(keyword: str, page: int, from_year: int | None, to_year: int | None) -> dict:
    # Form fields mirror kns8s brief search; keep minimal stable set for topic search.
    _ = (from_year, to_year)  # year filter applied client-side on parsed rows
    query = {
        "Platform": "",
        "Resource": "CROSSDB",
        "Classid": "WD0FTY92",
        "Products": "",
        "QNode": {
            "QGroup": [
                {
                    "Key": "Subject",
                    "Title": "",
                    "Logic": 0,
                    "Items": [
                        {
                            "Key": "input",
                            "Title": "主题",
                            "Logic": 0,
                            "Field": "SU",
                            "Operator": 0,
                            "Value": keyword,
                            "Value2": "",
                        }
                    ],
                    "ChildItems": [],
                }
            ]
        },
        "ExScope": "1",
        "SearchType": 2,
        "Rlang": "CHINESE",
        "KuaKuCode": "",
        "SearchFrom": 1,
    }
    return {
        "boolSearch": "true",
        "pageNum": str(page),
        "pageSize": "20",
        "queryJson": json.dumps(query, ensure_ascii=False),
        "sortType": "(发表时间,'time') desc",
        "oneDbs": "",
    }


def iter_search_rows(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    keyword: str,
    *,
    max_per_keyword: int,
    from_year: int | None,
    to_year: int | None,
) -> Iterator[dict]:
    """Yield list-row dicts for a keyword, respecting checkpoint and max_per_keyword."""
    page = checkpoint.get_keyword_page(keyword)
    yielded = 0
    while yielded < max_per_keyword:
        # Prefer GET with query for simpler fixture/testing; live CNKI often uses POST.
        # Client posts form body to SEARCH_URL.
        payload = build_search_payload(keyword, page, from_year, to_year)
        html = client_post_search(client, payload)
        rows = parse_list_html(html)
        if not rows:
            break
        for row in rows:
            url = row.get("detail_url") or ""
            if url and checkpoint.is_url_done(url):
                continue
            if from_year and row.get("year") and int(row["year"]) < from_year:
                continue
            if to_year and row.get("year") and int(row["year"]) > to_year:
                continue
            row["crawl_keyword"] = keyword
            yield row
            yielded += 1
            if yielded >= max_per_keyword:
                break
        checkpoint.set_keyword_page(keyword, page + 1)
        checkpoint.save()
        page += 1
        if len(rows) < 20:
            break


def client_post_search(client: CnkiHttpClient, payload: dict) -> str:
    """POST search form with same delay/rate-limit behavior as list GET."""
    client._sleep(client.list_delay_sec)  # noqa: SLF001 — intentional reuse of delay
    delays = [5.0, 15.0, 45.0]
    last_exc: Exception | None = None
    for backoff in [0.0, *delays]:
        if backoff:
            time.sleep(backoff)
        try:
            resp = client._client.post(SEARCH_URL, data=payload)  # noqa: SLF001
            if resp.status_code == 429:
                last_exc = RateLimitError(f"HTTP 429 for {SEARCH_URL}")
                continue
            resp.raise_for_status()
            text = resp.text
            detect_captcha_or_login(text)
            return text
        except CaptchaOrLoginError:
            raise
        except Exception as e:
            last_exc = e
    raise RateLimitError(str(last_exc) if last_exc else f"search failed: {SEARCH_URL}")
