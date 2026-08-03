from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from src.checkpoint import Checkpoint
from src.config_loader import ConfigError, load_config, load_keywords
from src.http_client import CnkiHttpClient
from src.runner import run_crawl
from src.task_crawl import parse_keywords, run_task
from src.year_range import resolve_year_range

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

    task_p = sub.add_parser("crawl-task", help="Crawl on-demand keywords into task JSONL")
    task_p.add_argument(
        "--keywords",
        default=None,
        help="Comma-separated keywords（与 --keywords-file 二选一）",
    )
    task_p.add_argument(
        "--keywords-file",
        default=None,
        help="UTF-8 关键词文件（每行一词；推荐由 Java 侧写入，避免 Windows CLI 乱码）",
    )
    task_p.add_argument("--max-per-keyword", type=int, default=20)
    task_p.add_argument("--output", default="data/task_xxx.jsonl")
    task_p.add_argument("--checkpoint", default="data/task_xxx_cp.json")
    task_p.add_argument(
        "--output-en",
        default=None,
        help="外文 JSONL（--search-lang both 时必填）",
    )
    task_p.add_argument(
        "--checkpoint-en",
        default=None,
        help="外文断点（--search-lang both 时必填）",
    )
    task_p.add_argument("--list-only", action="store_true")
    task_p.add_argument(
        "--search-lang",
        default="chinese",
        choices=["chinese", "foreign", "both"],
        help="chinese / foreign / both（中英各爬相同数量）",
    )

    args = parser.parse_args(argv)
    # Windows 管道场景下强制 UTF-8，避免 keyword 日志乱码
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    try:
        cfg = load_config(args.config, allow_empty_cookie=args.command == "crawl-task")
    except ConfigError as e:
        logging.error("%s", e)
        return 2

    from src.year_range import resolve_year_range

    year_from, year_to = resolve_year_range(cfg)
    logging.info("year range: %s .. %s (recent_years=%s)", year_from, year_to, cfg.get("recent_years"))

    if args.command == "crawl-task":
        from src.task_crawl import parse_keywords, run_bilingual_task, run_task

        if args.keywords_file:
            keywords = load_keywords(args.keywords_file)
            logging.info("loaded keywords from file=%s count=%s", args.keywords_file, len(keywords))
        elif args.keywords:
            keywords = parse_keywords(args.keywords)
        else:
            logging.error("crawl-task requires --keywords or --keywords-file")
            return 2
        if not keywords:
            logging.error("no keywords")
            return 2
        # 确认日志侧中文可读（便于对照 Windows 乱码问题）
        logging.info("crawl-task keywords=%s", keywords)
        client = CnkiHttpClient(
            cookie=cfg["cookie"],
            user_agent=cfg.get("user_agent") or "Mozilla/5.0",
            list_delay_sec=float(cfg.get("list_delay_sec", 1.0)),
            detail_delay_sec=float(cfg.get("detail_delay_sec", 2.0)),
            delay_jitter_sec=float(cfg.get("delay_jitter_sec", 0.5)),
            daily_detail_limit=int(cfg.get("daily_detail_limit", 800)),
            checkpoint=None,
        )
        keyword_workers = cfg.get("keyword_workers")
        max_workers = int(keyword_workers) if keyword_workers not in (None, "") else None
        try:
            if args.search_lang == "both":
                if not args.output_en or not args.checkpoint_en:
                    logging.error("--search-lang both requires --output-en and --checkpoint-en")
                    return 2
                checkpoint_zh = Checkpoint(args.checkpoint)
                checkpoint_zh.load()
                checkpoint_en = Checkpoint(args.checkpoint_en)
                checkpoint_en.load()
                client.checkpoint = checkpoint_zh
                zh_n, en_n = run_bilingual_task(
                    client,
                    checkpoint_zh,
                    checkpoint_en,
                    keywords,
                    max_per_keyword=args.max_per_keyword,
                    output_jsonl_zh=args.output,
                    output_jsonl_en=args.output_en,
                    from_year=year_from,
                    to_year=year_to,
                    list_only=args.list_only,
                    max_workers=max_workers,
                )
                if zh_n == 0 and en_n == 0:
                    logging.warning(
                        "done bilingual but empty: zh=0 en=0 keywords=%s "
                        "(check Cookie / captcha / search hits)",
                        keywords,
                    )
                else:
                    logging.info(
                        "done bilingual zh=%s -> %s, en=%s -> %s",
                        zh_n,
                        args.output,
                        en_n,
                        args.output_en,
                    )
                return 0

            checkpoint = Checkpoint(args.checkpoint)
            checkpoint.load()
            client.checkpoint = checkpoint
            total = run_task(
                client,
                checkpoint,
                keywords,
                max_per_keyword=args.max_per_keyword,
                output_jsonl=args.output,
                from_year=year_from,
                to_year=year_to,
                list_only=args.list_only,
                search_lang=args.search_lang,
                max_workers=max_workers,
            )
            logging.info("done, exported %s records -> %s", total, args.output)
            return 0
        except Exception:
            logging.exception("crawl-task failed")
            return 1
        finally:
            client.close()

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
        list_delay_sec=float(cfg.get("list_delay_sec", 1.0)),
        detail_delay_sec=float(cfg.get("detail_delay_sec", 2.0)),
        delay_jitter_sec=float(cfg.get("delay_jitter_sec", 0.5)),
        daily_detail_limit=int(cfg.get("daily_detail_limit", 800)),
        checkpoint=checkpoint,
    )
    try:
        total = run_crawl(
            client,
            checkpoint,
            keywords,
            max_per_keyword=int(cfg.get("max_per_keyword", 200)),
            from_year=year_from,
            to_year=year_to,
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
