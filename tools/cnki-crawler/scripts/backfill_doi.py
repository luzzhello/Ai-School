"""Backfill missing DOI (and rowtit biblio fields) by re-fetching detail pages.

Usage:
  python scripts/backfill_doi.py --config config.yaml --jsonl data/papers.jsonl --limit 50
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from src.checkpoint import Checkpoint  # noqa: E402
from src.config_loader import load_config  # noqa: E402
from src.crawl_dazhong import fetch_dazhong_biblio  # noqa: E402
from src.http_client import CnkiHttpClient  # noqa: E402
from src.parse import CaptchaOrLoginError, merge_biblio_fill, parse_detail_html  # noqa: E402

logger = logging.getLogger(__name__)

_FILL_KEYS = (
    "doi",
    "volume",
    "issue",
    "pages",
    "publisher",
    "publish_place",
    "translator",
    "degree",
    "degree_place",
    "patent_country",
    "patent_kind",
    "patent_no",
    "standard_code",
    "publish_date",
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill DOI from CNKI detail pages")
    parser.add_argument("--config", default=str(ROOT / "config.yaml"))
    parser.add_argument("--jsonl", default=str(ROOT / "data" / "papers.jsonl"))
    parser.add_argument("--limit", type=int, default=0, help="Max records to fetch (0=all missing doi)")
    parser.add_argument("--only-missing-doi", action="store_true", default=True)
    parser.add_argument(
        "--fetch-dazhong",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Also hit bar.cnki fee/dazhong to fill issue/pages (default: off)",
    )
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    cfg = load_config(args.config)
    path = Path(args.jsonl)
    rows = [json.loads(ln) for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]
    need = [i for i, r in enumerate(rows) if not r.get("doi") and r.get("detail_url")]
    if args.limit and args.limit > 0:
        need = need[: args.limit]
    logger.info("candidates=%s total=%s", len(need), len(rows))
    if not need:
        return 0

    cp = Checkpoint(cfg.get("checkpoint_path") or "data/checkpoint.json")
    cp.load()
    client = CnkiHttpClient(
        cookie=cfg["cookie"],
        user_agent=cfg.get("user_agent") or "Mozilla/5.0",
        list_delay_sec=float(cfg.get("list_delay_sec", 2.0)),
        detail_delay_sec=float(cfg.get("detail_delay_sec", 4.0)),
        delay_jitter_sec=float(cfg.get("delay_jitter_sec", 1.5)),
        daily_detail_limit=int(cfg.get("daily_detail_limit", 20000)),
        checkpoint=cp,
    )

    updated = 0
    try:
        for n, idx in enumerate(need, 1):
            row = rows[idx]
            url = row["detail_url"]
            try:
                html = client.get(url, is_detail=True)
                detail = parse_detail_html(html)
                if args.fetch_dazhong and (not detail.get("pages") or not detail.get("issue")):
                    try:
                        extra = fetch_dazhong_biblio(client, html, referer=url)
                        detail = merge_biblio_fill(detail, extra)
                    except Exception as e:
                        logger.warning("dazhong failed: %s", e)
            except CaptchaOrLoginError as e:
                logger.error("captcha/login: %s — update Cookie and retry", e)
                break
            except Exception as e:
                logger.warning("fetch failed %s: %s", row.get("cnki_id"), e)
                continue

            changed = False
            for k in _FILL_KEYS:
                if row.get(k) or not detail.get(k):
                    continue
                row[k] = detail[k]
                changed = True
            if changed:
                rows[idx] = row
                updated += 1
                logger.info(
                    "[%s/%s] filled %s doi=%s",
                    n,
                    len(need),
                    row.get("cnki_id"),
                    row.get("doi"),
                )
            else:
                logger.info("[%s/%s] no new fields %s", n, len(need), row.get("cnki_id"))
    finally:
        client.close()

    if updated:
        # atomic replace
        fd, tmp_name = tempfile.mkstemp(prefix="papers_", suffix=".jsonl", dir=str(path.parent))
        Path(tmp_name).write_text(
            "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n",
            encoding="utf-8",
        )
        Path(tmp_name).replace(path)
        logger.info("wrote %s updated=%s", path, updated)
    else:
        logger.info("nothing updated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
