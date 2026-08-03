from __future__ import annotations

import logging
import time
from pathlib import Path

from src.checkpoint import Checkpoint
from src.crawl_dazhong import fetch_dazhong_biblio
from src.crawl_detail import merge_list_and_detail
from src.crawl_search import iter_search_rows
from src.export_jsonl import append_record
from src.http_client import CnkiHttpClient, RateLimitError
from src.parse import CaptchaOrLoginError, merge_biblio_fill, parse_detail_html

logger = logging.getLogger(__name__)


def enrich_and_export(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    list_row: dict,
    output_jsonl: str | Path,
    *,
    list_only: bool = False,
    captcha_pause_sec: float = 90.0,
    captcha_state: dict | None = None,
    fetch_dazhong: bool = False,
) -> bool:
    """Fetch detail and append JSONL. Returns True if a new record was exported."""
    url = list_row.get("detail_url")
    cnki_id = list_row.get("cnki_id")
    keyword = list_row.get("crawl_keyword") or ""
    cp_key = list_row.get("checkpoint_key") or keyword
    if checkpoint.is_done(url=url, cnki_id=cnki_id):
        return False

    def _save_list_only(*, reason: str) -> bool:
        paper = merge_list_and_detail(list_row, None)
        paper.status = "incomplete"
        append_record(output_jsonl, paper)
        checkpoint.mark_done(url=url, cnki_id=cnki_id)
        if cp_key:
            checkpoint.incr_keyword_fetched(cp_key)
        checkpoint.save()
        logger.warning("saved list-only (%s): %s", reason, (list_row.get("title") or "")[:40])
        return True

    if not url or list_only:
        return _save_list_only(reason="list-only" if list_only else "no-detail-url")

    try:
        html = client.get(url, is_detail=True)
        detail = parse_detail_html(html)
        # 详情 `.top-tip` 已能解析年/卷/期/页时跳过 bar.cnki dazhong
        if fetch_dazhong and (not detail.get("pages") or not detail.get("issue")):
            try:
                extra = fetch_dazhong_biblio(client, html, referer=url)
                detail = merge_biblio_fill(detail, extra)
            except Exception as e:
                logger.warning("dazhong enrich failed: %s", e)
    except CaptchaOrLoginError:
        state = captcha_state if captcha_state is not None else {}
        state["count"] = int(state.get("count") or 0) + 1
        logger.error(
            "detail captcha hit (%s). pause %.0fs then continue with list-only. "
            "Please open CNKI in browser, pass verify, update config.yaml Cookie.",
            state["count"],
            captcha_pause_sec,
        )
        if captcha_pause_sec > 0:
            time.sleep(captcha_pause_sec)
        stop_after = int(state.get("stop_after") or 3)
        if state["count"] >= stop_after:
            raise CaptchaOrLoginError(
                f"captcha triggered {state['count']} times; stop for Cookie refresh"
            )
        return _save_list_only(reason="captcha")

    paper = merge_list_and_detail(list_row, detail)
    append_record(output_jsonl, paper)
    checkpoint.mark_done(url=url, cnki_id=cnki_id or paper.cnki_id)
    if cp_key:
        checkpoint.incr_keyword_fetched(cp_key)
    checkpoint.save()
    if captcha_state is not None:
        captcha_state["count"] = 0
    return True


def run_crawl(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    keywords: list[str],
    *,
    max_per_keyword: int,
    from_year: int | None,
    to_year: int | None,
    output_jsonl: str | Path,
    max_total: int | None = None,
    list_only: bool = False,
    captcha_pause_sec: float = 90.0,
    captcha_stop_after: int = 3,
    fetch_dazhong: bool = False,
    search_lang: str = "chinese",
) -> int:
    from src.crawl_search import checkpoint_keyword_key, normalize_rlang
    from src.year_range import split_quota_evenly, year_span_list

    rlang = normalize_rlang(search_lang)
    years = year_span_list(from_year, to_year)
    # 多年份：逐年检索并均分每个词的配额，避免宽范围时结果几乎全是最新年
    year_plans: list[tuple[int | None, int | None, int]]
    if years and len(years) > 1:
        quotas = split_quota_evenly(max_per_keyword, len(years))
        year_plans = [(y, y, q) for y, q in zip(years, quotas)]
    else:
        year_plans = [(from_year, to_year, max_per_keyword)]

    total = 0
    captcha_state = {"count": 0, "stop_after": captcha_stop_after}
    try:
        for keyword in keywords:
            cumulative_cap = 0
            for y_from, y_to, year_quota in year_plans:
                if year_quota <= 0:
                    continue
                cumulative_cap += year_quota
                logger.info(
                    "crawl keyword=%s rlang=%s year=%s..%s "
                    "(year_quota=%s, cumulative_cap=%s, already_fetched=%s, list_only=%s)",
                    keyword,
                    rlang,
                    y_from,
                    y_to,
                    year_quota,
                    cumulative_cap,
                    checkpoint.get_keyword_fetched(checkpoint_keyword_key(keyword, rlang)),
                    list_only,
                )
                for row in iter_search_rows(
                    client,
                    checkpoint,
                    keyword,
                    # 用累计上限：断点已抓数量跨年累加，本轮只补足本年配额
                    max_per_keyword=cumulative_cap,
                    from_year=y_from,
                    to_year=y_to,
                    rlang=rlang,
                ):
                    if enrich_and_export(
                        client,
                        checkpoint,
                        row,
                        output_jsonl,
                        list_only=list_only,
                        captcha_pause_sec=captcha_pause_sec,
                        captcha_state=captcha_state,
                        fetch_dazhong=fetch_dazhong,
                    ):
                        total += 1
                    if max_total is not None and total >= max_total:
                        logger.info("reached max_total=%s", total)
                        return total
    except (CaptchaOrLoginError, RateLimitError) as e:
        checkpoint.save()
        logger.error("crawl stopped: %s", e)
        raise
    return total
