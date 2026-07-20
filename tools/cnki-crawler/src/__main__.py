from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from src.checkpoint import Checkpoint
from src.config_loader import ConfigError, load_config, load_keywords
from src.http_client import CnkiHttpClient
from src.runner import run_crawl

ROOT = Path(__file__).resolve().parent.parent


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="cnki-crawler", description="CNKI SE literature metadata crawler")
    parser.add_argument("--config", default=str(ROOT / "config.yaml"))
    sub = parser.add_subparsers(dest="command", required=True)

    crawl_p = sub.add_parser("crawl", help="Crawl CNKI metadata into JSONL")
    crawl_p.add_argument("--keyword", default=None, help="Single keyword override")
    crawl_p.add_argument("--max", type=int, default=None, help="Max total detail records this run")
    crawl_p.add_argument(
        "--keywords-file",
        default=None,
        help="Keywords file (default: keywords/se.txt；外文默认 keywords/se-en.txt)",
    )
    crawl_p.add_argument(
        "--list-only",
        action="store_true",
        help="Only save list fields (title/authors/source/year), skip detail pages (fewer captchas)",
    )
    crawl_p.add_argument(
        "--search-lang",
        default=None,
        choices=["chinese", "foreign"],
        help="CNKI 总库语言：chinese=中文，foreign=外文（对应 Rlang=FOREIGN）",
    )

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    if args.command != "crawl":
        parser.error("unknown command")

    try:
        cfg = load_config(args.config)
    except ConfigError as e:
        logging.error("%s", e)
        return 2

    search_lang = args.search_lang or cfg.get("search_lang") or "chinese"
    from src.checkpoint import migrate_legacy_shared_checkpoint, resolve_checkpoint_path
    from src.crawl_search import normalize_rlang

    rlang = normalize_rlang(str(search_lang))
    zh_cp = resolve_checkpoint_path(cfg.get("checkpoint_path"), rlang="CHINESE")
    en_cp = resolve_checkpoint_path(
        cfg.get("checkpoint_path"),
        rlang="FOREIGN",
        checkpoint_path_en=cfg.get("checkpoint_path_en"),
    )
    migrate_legacy_shared_checkpoint(zh_cp, en_cp)
    cp_path = en_cp if rlang == "FOREIGN" else zh_cp
    checkpoint = Checkpoint(cp_path)
    checkpoint.load()
    logging.info("checkpoint=%s (rlang=%s)", cp_path, rlang)

    if args.keywords_file:
        keywords_file = args.keywords_file
    elif rlang == "FOREIGN":
        keywords_file = str(cfg.get("keywords_file_en") or (ROOT / "keywords" / "se-en.txt"))
    else:
        keywords_file = str(cfg.get("keywords_file") or (ROOT / "keywords" / "se.txt"))

    keywords = [args.keyword] if args.keyword else load_keywords(keywords_file)
    if not keywords:
        logging.error("no keywords")
        return 2

    if rlang == "FOREIGN":
        output_jsonl = str(cfg.get("output_jsonl_en") or "data/papers_en.jsonl")
    else:
        output_jsonl = str(cfg.get("output_jsonl") or "data/papers.jsonl")

    client = CnkiHttpClient(
        cookie=cfg["cookie"],
        user_agent=cfg.get("user_agent") or "Mozilla/5.0",
        list_delay_sec=float(cfg.get("list_delay_sec", 2.0)),
        detail_delay_sec=float(cfg.get("detail_delay_sec", 4.0)),
        delay_jitter_sec=float(cfg.get("delay_jitter_sec", 1.5)),
        daily_detail_limit=int(cfg.get("daily_detail_limit", 800)),
        checkpoint=checkpoint,
    )
    try:
        total = run_crawl(
            client,
            checkpoint,
            keywords,
            max_per_keyword=int(cfg.get("max_per_keyword", 200)),
            from_year=cfg.get("from_year"),
            to_year=cfg.get("to_year"),
            output_jsonl=output_jsonl,
            max_total=args.max,
            list_only=bool(args.list_only or cfg.get("list_only")),
            captcha_pause_sec=float(cfg.get("captcha_pause_sec", 90)),
            captcha_stop_after=int(cfg.get("captcha_stop_after", 3)),
            fetch_dazhong=bool(cfg.get("fetch_dazhong", False)),
            search_lang=str(search_lang),
        )
        logging.info("done, exported %s records -> %s (rlang=%s)", total, output_jsonl, rlang)
        return 0
    except Exception:
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
