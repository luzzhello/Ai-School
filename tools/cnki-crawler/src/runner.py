from __future__ import annotations

import logging
from pathlib import Path

from src.checkpoint import Checkpoint
from src.crawl_detail import merge_list_and_detail
from src.crawl_search import iter_search_rows
from src.export_jsonl import append_record
from src.http_client import CnkiHttpClient, RateLimitError
from src.parse import CaptchaOrLoginError, parse_detail_html

logger = logging.getLogger(__name__)


def enrich_and_export(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    list_row: dict,
    output_jsonl: str | Path,
) -> bool:
    """Fetch detail and append JSONL. Returns True if a new record was exported."""
    url = list_row.get("detail_url")
    cnki_id = list_row.get("cnki_id")
    keyword = list_row.get("crawl_keyword") or ""
    if checkpoint.is_done(url=url, cnki_id=cnki_id):
        return False
    if not url:
        paper = merge_list_and_detail(list_row, None)
        append_record(output_jsonl, paper)
        checkpoint.mark_done(cnki_id=cnki_id)
        if keyword:
            checkpoint.incr_keyword_fetched(keyword)
        checkpoint.save()
        return True
    html = client.get(url, is_detail=True)
    detail = parse_detail_html(html)
    paper = merge_list_and_detail(list_row, detail)
    append_record(output_jsonl, paper)
    checkpoint.mark_done(url=url, cnki_id=cnki_id or paper.cnki_id)
    if keyword:
        checkpoint.incr_keyword_fetched(keyword)
    checkpoint.save()
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
) -> int:
    total = 0
    try:
        for keyword in keywords:
            logger.info(
                "crawl keyword=%s (already_fetched=%s, max_per_keyword=%s)",
                keyword,
                checkpoint.get_keyword_fetched(keyword),
                max_per_keyword,
            )
            for row in iter_search_rows(
                client,
                checkpoint,
                keyword,
                max_per_keyword=max_per_keyword,
                from_year=from_year,
                to_year=to_year,
            ):
                if enrich_and_export(client, checkpoint, row, output_jsonl):
                    total += 1
                if max_total is not None and total >= max_total:
                    logger.info("reached max_total=%s", max_total)
                    return total
    except (CaptchaOrLoginError, RateLimitError) as e:
        checkpoint.save()
        logger.error("crawl stopped: %s", e)
        raise
    return total
