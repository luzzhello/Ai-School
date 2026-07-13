#!/usr/bin/env python3
"""Idempotent JSONL → lit_paper / lit_paper_ref importer."""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.import_dedupe import null_if_blank, resolve_match  # noqa: E402


UPSERT_FIELDS = (
    "cnki_id",
    "doi",
    "title",
    "authors",
    "organs",
    "abstract_text",
    "keywords",
    "source",
    "year",
    "doc_type",
    "cite_count",
    "lit_source",
    "citation_gbt",
    "detail_url",
    "title_hash",
    "crawl_keyword",
    "status",
    "crawled_at",
)


def load_indexes(conn) -> tuple[dict[str, int], dict[str, int], dict[tuple[str, int | None], int]]:
    by_cnki: dict[str, int] = {}
    by_doi: dict[str, int] = {}
    by_hash: dict[tuple[str, int | None], int] = {}
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, cnki_id, doi, title_hash, year FROM lit_paper"
        )
        for pid, cnki_id, doi, title_hash, year in cur.fetchall():
            if cnki_id:
                by_cnki[cnki_id] = pid
            if doi:
                by_doi[doi] = pid
            if title_hash:
                by_hash[(title_hash, year)] = pid
    return by_cnki, by_doi, by_hash


def row_values(rec: dict) -> dict:
    return {
        "cnki_id": null_if_blank(rec.get("cnki_id")),
        "doi": null_if_blank(rec.get("doi")),
        "title": (rec.get("title") or "").strip() or "(无 title)",
        "authors": null_if_blank(rec.get("authors")),
        "organs": null_if_blank(rec.get("organs")),
        "abstract_text": null_if_blank(rec.get("abstract_text")),
        "keywords": null_if_blank(rec.get("keywords")),
        "source": null_if_blank(rec.get("source")),
        "year": rec.get("year"),
        "doc_type": null_if_blank(rec.get("doc_type")),
        "cite_count": rec.get("cite_count") or 0,
        "lit_source": null_if_blank(rec.get("lit_source")) or "CNKI",
        "citation_gbt": null_if_blank(rec.get("citation_gbt")),
        "detail_url": null_if_blank(rec.get("detail_url")),
        "title_hash": null_if_blank(rec.get("title_hash")),
        "crawl_keyword": null_if_blank(rec.get("crawl_keyword")),
        "status": null_if_blank(rec.get("status")) or "active",
        "crawled_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }


def replace_refs(conn, paper_id: int, references: list) -> None:
    with conn.cursor() as cur:
        cur.execute("DELETE FROM lit_paper_ref WHERE paper_id=%s", (paper_id,))
        for i, text in enumerate(references or [], start=1):
            raw = str(text).strip()
            if not raw:
                continue
            cur.execute(
                "INSERT INTO lit_paper_ref (paper_id, ref_index, raw_text) VALUES (%s,%s,%s)",
                (paper_id, i, raw),
            )


def import_jsonl(conn, jsonl_path: Path, batch_size: int = 100) -> tuple[int, int]:
    by_cnki, by_doi, by_hash = load_indexes(conn)
    inserted = updated = 0
    pending = 0
    with jsonl_path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            vals = row_values(rec)
            existing_id = resolve_match(by_cnki, by_doi, by_hash, vals)
            with conn.cursor() as cur:
                if existing_id is None:
                    cols = ", ".join(UPSERT_FIELDS)
                    ph = ", ".join(["%s"] * len(UPSERT_FIELDS))
                    cur.execute(
                        f"INSERT INTO lit_paper ({cols}) VALUES ({ph})",
                        tuple(vals[c] for c in UPSERT_FIELDS),
                    )
                    paper_id = cur.lastrowid
                    inserted += 1
                else:
                    sets = ", ".join(f"{c}=%s" for c in UPSERT_FIELDS if c != "cnki_id")
                    cur.execute(
                        f"UPDATE lit_paper SET {sets} WHERE id=%s",
                        tuple(vals[c] for c in UPSERT_FIELDS if c != "cnki_id") + (existing_id,),
                    )
                    paper_id = existing_id
                    updated += 1
                replace_refs(conn, paper_id, rec.get("references") or [])
                # refresh indexes
                if vals["cnki_id"]:
                    by_cnki[vals["cnki_id"]] = paper_id
                if vals["doi"]:
                    by_doi[vals["doi"]] = paper_id
                if vals["title_hash"]:
                    by_hash[(vals["title_hash"], vals["year"])] = paper_id
            pending += 1
            if pending >= batch_size:
                conn.commit()
                pending = 0
    if pending:
        conn.commit()
    return inserted, updated


def main() -> int:
    p = argparse.ArgumentParser(description="Import CNKI JSONL into lit_paper")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", required=True)
    p.add_argument("--password", required=True)
    p.add_argument("--database", required=True)
    p.add_argument("--jsonl", required=True)
    p.add_argument("--batch-size", type=int, default=100)
    args = p.parse_args()
    path = Path(args.jsonl)
    if not path.exists():
        print(f"jsonl not found: {path}", file=sys.stderr)
        return 2
    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        inserted, updated = import_jsonl(conn, path, args.batch_size)
        print(f"done: inserted={inserted} updated={updated}")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
