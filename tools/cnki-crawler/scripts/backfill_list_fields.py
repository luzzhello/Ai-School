"""按 crawl_keyword 重拉知网列表，用列表「题名/作者/来源」回填已有 JSONL。

Usage:
  python scripts/backfill_list_fields.py --config config.yaml --jsonl data/papers.jsonl
  python scripts/backfill_list_fields.py --config config.yaml --jsonl data/papers_en.jsonl --search-lang foreign
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from src.config_loader import load_config  # noqa: E402
from src.crawl_search import (  # noqa: E402
    assert_grid_response_ok,
    build_search_payload,
    client_post_search,
    normalize_rlang,
    search_index_url,
    warm_search_session,
    _extract_turnpage,
)
from src.gbt7714 import format_gbt7714  # noqa: E402
from src.http_client import CnkiHttpClient, RateLimitError  # noqa: E402
from src.models import PaperRecord  # noqa: E402
from src.normalize import title_hash  # noqa: E402
from src.parse import CaptchaOrLoginError, parse_list_html  # noqa: E402

logger = logging.getLogger(__name__)


def _load_jsonl(path: Path) -> list[dict]:
    return [json.loads(ln) for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", delete=False, dir=str(path.parent), suffix=".tmp"
    ) as tmp:
        for row in rows:
            tmp.write(json.dumps(row, ensure_ascii=False) + "\n")
        tmp_path = Path(tmp.name)
    tmp_path.replace(path)


def collect_list_by_ids(
    client: CnkiHttpClient,
    keyword: str,
    *,
    needed_ids: set[str],
    rlang: str,
    from_year: int | None,
    to_year: int | None,
    max_pages: int = 40,
) -> dict[str, dict]:
    """翻列表页，收集 needed_ids 对应的题名/作者/来源。"""
    found: dict[str, dict] = {}
    if not needed_ids:
        return found

    warm_search_session(client, keyword, rlang=rlang)
    page = 1
    turnpage = ""
    empty_streak = 0
    while page <= max_pages and not needed_ids.issubset(found.keys()):
        is_first = page == 1
        payload = build_search_payload(
            keyword,
            page,
            from_year,
            to_year,
            is_first=is_first,
            turnpage="" if is_first else turnpage,
            rlang=rlang,
        )
        html = client_post_search(client, payload, referer=search_index_url(keyword))
        assert_grid_response_ok(html)
        rows = parse_list_html(html)
        if not rows:
            empty_streak += 1
            if empty_streak >= 2 or page == 1:
                break
            page = 1
            turnpage = ""
            continue
        empty_streak = 0
        for row in rows:
            cid = row.get("cnki_id")
            if cid and cid in needed_ids and cid not in found:
                found[cid] = {
                    "title": (row.get("title") or "").strip(),
                    "authors": row.get("authors"),
                    "source": row.get("source"),
                }
        turnpage = _extract_turnpage(html) or turnpage
        if not turnpage and page > 1:
            break
        page += 1

    logger.info(
        "keyword=%s list matched %s/%s ids (pages<=%s)",
        keyword,
        len(found),
        len(needed_ids),
        page - 1,
    )
    return found


def apply_list_fields(row: dict, list_hit: dict) -> bool:
    """用列表字段覆盖题名/作者/来源，并重算 citation / title_hash。返回是否有变更。"""
    new_title = (list_hit.get("title") or "").strip()
    new_authors = list_hit.get("authors")
    new_source = list_hit.get("source")
    changed = False
    if new_title and new_title != (row.get("title") or "").strip():
        row["title"] = new_title
        changed = True
    if new_authors and new_authors != row.get("authors"):
        row["authors"] = new_authors
        changed = True
    if new_source and new_source != row.get("source"):
        row["source"] = new_source
        changed = True
    if changed:
        paper = PaperRecord(
            cnki_id=row.get("cnki_id"),
            doi=row.get("doi"),
            title=row.get("title") or "",
            authors=row.get("authors"),
            source=row.get("source"),
            year=row.get("year"),
            volume=row.get("volume"),
            issue=row.get("issue"),
            pages=row.get("pages"),
            publisher=row.get("publisher"),
            publish_place=row.get("publish_place"),
            translator=row.get("translator"),
            degree=row.get("degree"),
            degree_place=row.get("degree_place"),
            patent_country=row.get("patent_country"),
            patent_kind=row.get("patent_kind"),
            patent_no=row.get("patent_no"),
            standard_code=row.get("standard_code"),
            publish_date=row.get("publish_date"),
            doc_type=row.get("doc_type"),
        )
        row["citation_gbt"] = format_gbt7714(paper)
        row["title_hash"] = title_hash(paper.title) if paper.title else row.get("title_hash")
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill title/authors/source from CNKI list")
    parser.add_argument("--config", default=str(ROOT / "config.yaml"))
    parser.add_argument("--jsonl", default=str(ROOT / "data" / "papers.jsonl"))
    parser.add_argument("--search-lang", default=None, choices=["chinese", "foreign"])
    parser.add_argument("--max-pages", type=int, default=40, help="每个关键词最多翻页数")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    cfg = load_config(args.config)
    path = Path(args.jsonl)
    rows = _load_jsonl(path)
    if not rows:
        logger.error("empty jsonl: %s", path)
        return 2

    search_lang = args.search_lang or cfg.get("search_lang") or "chinese"
    if "papers_en" in path.name and args.search_lang is None:
        search_lang = "foreign"
    rlang = normalize_rlang(str(search_lang))
    from_year = cfg.get("from_year")
    to_year = cfg.get("to_year")

    by_kw: dict[str, list[int]] = defaultdict(list)
    for i, row in enumerate(rows):
        kw = (row.get("crawl_keyword") or "").strip()
        cid = (row.get("cnki_id") or "").strip()
        if kw and cid:
            by_kw[kw].append(i)

    logger.info(
        "jsonl=%s rows=%s keywords=%s rlang=%s dry_run=%s",
        path,
        len(rows),
        len(by_kw),
        rlang,
        args.dry_run,
    )

    client = CnkiHttpClient(
        cookie=cfg["cookie"],
        user_agent=cfg.get("user_agent") or "Mozilla/5.0",
        list_delay_sec=float(cfg.get("list_delay_sec", 2.0)),
        detail_delay_sec=float(cfg.get("detail_delay_sec", 4.0)),
        delay_jitter_sec=float(cfg.get("delay_jitter_sec", 1.5)),
        daily_detail_limit=int(cfg.get("daily_detail_limit", 20000)),
        checkpoint=None,
    )

    updated = 0
    matched = 0
    try:
        for kw, idxs in by_kw.items():
            needed = {(rows[i].get("cnki_id") or "").strip() for i in idxs}
            needed.discard("")
            try:
                hits = collect_list_by_ids(
                    client,
                    kw,
                    needed_ids=needed,
                    rlang=rlang,
                    from_year=from_year,
                    to_year=to_year,
                    max_pages=args.max_pages,
                )
            except (CaptchaOrLoginError, RateLimitError) as e:
                logger.error("stop on keyword=%s: %s", kw, e)
                break

            for i in idxs:
                cid = (rows[i].get("cnki_id") or "").strip()
                hit = hits.get(cid)
                if not hit:
                    continue
                matched += 1
                if apply_list_fields(rows[i], hit):
                    updated += 1
    finally:
        client.close()

    logger.info("matched=%s updated=%s / %s", matched, updated, len(rows))
    if args.dry_run:
        logger.info("dry-run: not writing %s", path)
        return 0
    if updated:
        _write_jsonl(path, rows)
        logger.info("wrote %s", path)
    else:
        logger.info("no field changes, skip write")
    return 0


if __name__ == "__main__":
    sys.exit(main())
