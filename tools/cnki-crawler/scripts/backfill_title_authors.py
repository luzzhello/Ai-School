#!/usr/bin/env python3
"""修复 JSONL 中题名多余空格、作者空格分隔等问题。"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.gbt7714 import format_gbt7714  # noqa: E402
from src.models import PaperRecord  # noqa: E402
from src.normalize import title_hash  # noqa: E402
from src.parse import _compact_title_text, _normalize_authors_text  # noqa: E402

logger = logging.getLogger(__name__)


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
    )


def backfill(path: Path, *, dry_run: bool = False) -> tuple[int, int]:
    rows = _load_jsonl(path)
    changed = 0
    for row in rows:
        old_title = (row.get("title") or "").strip()
        old_authors = (row.get("authors") or "").strip()
        new_title = _compact_title_text(old_title) or old_title
        new_authors = _normalize_authors_text(old_authors) or old_authors
        row_changed = False
        if new_title and new_title != old_title:
            row["title"] = new_title
            row_changed = True
        if new_authors and new_authors != old_authors:
            row["authors"] = new_authors
            row_changed = True
        if row_changed:
            if new_title:
                row["title_hash"] = title_hash(new_title)
            row["citation_gbt"] = format_gbt7714(_paper_from_row(row))
            changed += 1
    logger.info("%s rows=%s changed=%s dry_run=%s", path.name, len(rows), changed, dry_run)
    if not dry_run and changed:
        _write_jsonl(path, rows)
    return len(rows), changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill title/authors spacing in JSONL")
    parser.add_argument("--jsonl", action="append", default=[])
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    paths = [Path(p) for p in args.jsonl] if args.jsonl else [
        ROOT / "data" / "papers.jsonl",
        ROOT / "data" / "papers_en.jsonl",
    ]
    total = 0
    for path in paths:
        if not path.is_file():
            logger.warning("skip missing: %s", path)
            continue
        _, changed = backfill(path, dry_run=args.dry_run)
        total += changed
    logger.info("done total_changed=%s", total)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
