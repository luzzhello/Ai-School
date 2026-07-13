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
) -> None:
    url = list_row.get("detail_url")
    if not url:
        paper = merge_list_and_detail(list_row, None)
        append_record(output_jsonl, paper)
        return
    if checkpoint.is_url_done(url):
        return
    html = client.get(url, is_detail=True)
    detail = parse_detail_html(html)
    paper = merge_list_and_detail(list_row, detail)
    append_record(output_jsonl, paper)
    checkpoint.mark_url_done(url)
    checkpoint.save()


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
            logger.info("crawl keyword=%s", keyword)
            for row in iter_search_rows(
                client,
                checkpoint,
                keyword,
                max_per_keyword=max_per_keyword,
                from_year=from_year,
                to_year=to_year,
            ):
                enrich_and_export(client, checkpoint, row, output_jsonl)
                total += 1
                if max_total is not None and total >= max_total:
                    logger.info("reached max_total=%s", max_total)
                    return total
    except (CaptchaOrLoginError, RateLimitError) as e:
        checkpoint.save()
        logger.error("crawl stopped: %s", e)
        raise
    return total
