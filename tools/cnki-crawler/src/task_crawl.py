from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from src.checkpoint import Checkpoint
from src.http_client import CnkiHttpClient
from src.runner import run_crawl

logger = logging.getLogger(__name__)


def parse_keywords(value: str) -> list[str]:
    return [keyword.strip() for keyword in value.split(",") if keyword.strip()]


def _resolve_workers(keywords: list[str], max_workers: int | None) -> int:
    n = len(keywords)
    if n <= 0:
        return 1
    if max_workers is None or max_workers <= 0:
        # 一词一线程；略设上限避免瞬时打爆知网
        return min(n, 8)
    return max(1, min(n, max_workers))


def run_task(
    client: CnkiHttpClient,
    checkpoint: Checkpoint,
    keywords: list[str],
    *,
    max_per_keyword: int,
    output_jsonl: str | Path,
    from_year: int | None,
    to_year: int | None,
    list_only: bool,
    search_lang: str,
    max_workers: int | None = None,
) -> int:
    """每个关键词独立线程爬取；共享 checkpoint / JSONL（已加锁）。"""
    if not keywords:
        return 0

    workers = _resolve_workers(keywords, max_workers)
    logger.info(
        "crawl-task start lang=%s keywords=%s max_per_keyword=%s list_only=%s "
        "year=%s..%s output=%s workers=%s",
        search_lang,
        keywords,
        max_per_keyword,
        list_only,
        from_year,
        to_year,
        output_jsonl,
        workers,
    )

    def _crawl_one(keyword: str) -> int:
        logger.info("crawl-task keyword begin lang=%s keyword=%s", search_lang, keyword)
        local = client.clone(checkpoint=checkpoint)
        try:
            n = run_crawl(
                local,
                checkpoint,
                [keyword],
                max_per_keyword=max_per_keyword,
                from_year=from_year,
                to_year=to_year,
                output_jsonl=output_jsonl,
                max_total=max_per_keyword,
                list_only=list_only,
                search_lang=search_lang,
            )
        finally:
            local.close()
        if n == 0:
            logger.warning(
                "crawl-task keyword empty lang=%s keyword=%s "
                "(no list rows or all skipped by checkpoint/year filter)",
                search_lang,
                keyword,
            )
        else:
            logger.info(
                "crawl-task keyword done lang=%s keyword=%s exported=%s",
                search_lang,
                keyword,
                n,
            )
        return n

    total = 0
    errors: list[tuple[str, BaseException]] = []
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="cnki-kw") as pool:
        futures = {pool.submit(_crawl_one, kw): kw for kw in keywords}
        for fut in as_completed(futures):
            kw = futures[fut]
            try:
                total += int(fut.result() or 0)
            except BaseException as exc:  # noqa: BLE001 — 汇总后决定是否抛出
                errors.append((kw, exc))
                logger.exception(
                    "crawl-task keyword failed lang=%s keyword=%s: %s",
                    search_lang,
                    kw,
                    exc,
                )

    logger.info(
        "crawl-task finished lang=%s total_exported=%s keywords=%s errors=%s",
        search_lang,
        total,
        keywords,
        len(errors),
    )
    if total == 0 and errors:
        raise errors[0][1]
    return total


def run_bilingual_task(
    client: CnkiHttpClient,
    checkpoint_zh: Checkpoint,
    checkpoint_en: Checkpoint,
    keywords: list[str],
    *,
    max_per_keyword: int,
    output_jsonl_zh: str | Path,
    output_jsonl_en: str | Path,
    from_year: int | None,
    to_year: int | None,
    list_only: bool,
    max_workers: int | None = None,
) -> tuple[int, int]:
    """中文 / 外文各爬一遍；每种语言内关键词并发。"""
    logger.info(
        "crawl-task bilingual start keywords=%s max_per_keyword=%s list_only=%s "
        "year=%s..%s zh_out=%s en_out=%s workers=%s",
        keywords,
        max_per_keyword,
        list_only,
        from_year,
        to_year,
        output_jsonl_zh,
        output_jsonl_en,
        _resolve_workers(keywords, max_workers),
    )
    client.checkpoint = checkpoint_zh
    zh_total = run_task(
        client,
        checkpoint_zh,
        keywords,
        max_per_keyword=max_per_keyword,
        output_jsonl=output_jsonl_zh,
        from_year=from_year,
        to_year=to_year,
        list_only=list_only,
        search_lang="chinese",
        max_workers=max_workers,
    )
    client.checkpoint = checkpoint_en
    en_total = run_task(
        client,
        checkpoint_en,
        keywords,
        max_per_keyword=max_per_keyword,
        output_jsonl=output_jsonl_en,
        from_year=from_year,
        to_year=to_year,
        list_only=list_only,
        search_lang="foreign",
        max_workers=max_workers,
    )
    logger.info(
        "crawl-task bilingual finished zh_exported=%s en_exported=%s keywords=%s",
        zh_total,
        en_total,
        keywords,
    )
    return zh_total, en_total
