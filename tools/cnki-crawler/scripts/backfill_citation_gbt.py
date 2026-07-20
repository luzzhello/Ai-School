#!/usr/bin/env python3
"""按 GB/T 7714 英文半角规则重算 JSONL 中 citation_gbt。"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.gbt7714 import format_gbt7714, normalize_citation_gbt  # noqa: E402
from src.models import PaperRecord  # noqa: E402

logger = logging.getLogger(__name__)
_PLACEHOLDER = frozenset({"[J].", "[D].", "[M].", "[C].", "[P].", "[S]."})


def _load_jsonl(path: Path) -> list[dict]:
    rows: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def _paper_from_row(row: dict) -> PaperRecord:
    return PaperRecord(
        cnki_id=row.get("cnki_id"),
        doi=row.get("doi"),
        title=(row.get("title") or "").strip(),
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
        citation_gbt=row.get("citation_gbt"),
    )


def _resolve_citation(row: dict) -> str:
    paper = _paper_from_row(row)
    rebuilt = format_gbt7714(paper)
    if rebuilt and rebuilt not in _PLACEHOLDER:
        return rebuilt
    raw = (row.get("citation_gbt") or "").strip()
    if raw:
        return normalize_citation_gbt(raw)
    return rebuilt


def backfill(path: Path, *, dry_run: bool = False) -> tuple[int, int]:
    rows = _load_jsonl(path)
    if not rows:
        logger.error("empty jsonl: %s", path)
        return 0, 0
    changed = 0
    for row in rows:
        new_cite = _resolve_citation(row)
        old_cite = (row.get("citation_gbt") or "").strip()
        if new_cite != old_cite:
            row["citation_gbt"] = new_cite
            changed += 1
    logger.info("rows=%s changed=%s dry_run=%s", len(rows), changed, dry_run)
    if not dry_run and changed:
        _write_jsonl(path, rows)
        logger.info("wrote %s", path)
    return len(rows), changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill citation_gbt with GB/T 7714 halfwidth rules")
    parser.add_argument("--jsonl", action="append", default=[], help="可多次指定；默认中英文各一份")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    paths = [Path(p) for p in args.jsonl] if args.jsonl else [
        ROOT / "data" / "papers.jsonl",
        ROOT / "data" / "papers_en.jsonl",
    ]
    total_changed = 0
    for path in paths:
        if not path.is_file():
            logger.warning("skip missing: %s", path)
            continue
        _, changed = backfill(path, dry_run=args.dry_run)
        total_changed += changed
    logger.info("done total_changed=%s", total_changed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
