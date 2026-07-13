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
    crawl_p.add_argument("--keywords-file", default=str(ROOT / "keywords" / "se.txt"))

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    if args.command != "crawl":
        parser.error("unknown command")

    try:
        cfg = load_config(args.config)
    except ConfigError as e:
        logging.error("%s", e)
        return 2

    checkpoint = Checkpoint(cfg.get("checkpoint_path") or "data/checkpoint.json")
    checkpoint.load()

    keywords = [args.keyword] if args.keyword else load_keywords(args.keywords_file)
    if not keywords:
        logging.error("no keywords")
        return 2

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
            output_jsonl=cfg.get("output_jsonl") or "data/papers.jsonl",
            max_total=args.max,
        )
        logging.info("done, exported %s records", total)
        return 0
    except Exception:
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
