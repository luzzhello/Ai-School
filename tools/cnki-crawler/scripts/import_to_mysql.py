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
from src.gbt7714 import format_gbt7714, normalize_citation_gbt  # noqa: E402
from src.models import PaperRecord  # noqa: E402
from src.parse import _compact_title_text, _normalize_authors_text  # noqa: E402

_PLACEHOLDER_CITES = frozenset({"[J].", "[D].", "[M].", "[C].", "[P].", "[S]."})


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

# 仅 lit_paper_en
UPSERT_FIELDS_EN_EXTRA = ("title_zh", "abstract_zh", "keywords_zh")


def upsert_fields_for(paper_table: str) -> tuple[str, ...]:
    if paper_table == "lit_paper_en":
        # 插在 keywords 之后，与表结构一致
        base = list(UPSERT_FIELDS)
        idx = base.index("keywords") + 1
        return tuple(base[:idx] + list(UPSERT_FIELDS_EN_EXTRA) + base[idx:])
    return UPSERT_FIELDS

# 与表字段长度对齐；超长截断避免 1406
_VARCHAR_LIMITS = {
    "cnki_id": 64,
    "doi": 200,
    "title": 500,
    "authors": 1000,
    "organs": 1000,
    "keywords": 500,
    "title_zh": 500,
    "keywords_zh": 500,
    "source": 1000,
    "volume": 64,
    "issue": 64,
    "pages": 100,
    "publisher": 200,
    "publish_place": 200,
    "translator": 200,
    "degree": 100,
    "degree_place": 200,
    "patent_country": 100,
    "patent_kind": 100,
    "patent_no": 100,
    "standard_code": 100,
    "publish_date": 64,
    "doc_type": 10,
    "lit_source": 32,
    "detail_url": 1000,
    "title_hash": 64,
    "crawl_keyword": 200,
    "status": 20,
}


def clip(s: str | None, max_len: int) -> str | None:
    if s is None:
        return None
    if len(s) <= max_len:
        return s
    return s[: max_len - 1] + "…"


def clean_source(raw: str | None) -> str | None:
    """少数脏数据把摘要拼进 source，取刊名/短来源。"""
    s = null_if_blank(raw)
    if not s:
        return None
    for sep in ("摘要：", "摘要:", "摘要 "):
        if sep in s:
            s = s.split(sep, 1)[0].strip(" ;,，")
            break
    if len(s) > 200 and (" " in s or "；" in s or ";" in s):
        # 过长时更像整段元数据粘贴，尽量保留前段刊名
        for sep in ("。", ".", "\n"):
            if sep in s[:120]:
                s = s.split(sep, 1)[0].strip()
                break
    return clip(s, _VARCHAR_LIMITS["source"])


def citation_from_rec(rec: dict) -> str | None:
    """按结构化字段重拼引文；缺字段时规范化已有 citation_gbt。"""
    raw_title = (rec.get("title") or "").strip()
    title = _compact_title_text(raw_title) or raw_title
    authors = _normalize_authors_text(rec.get("authors")) or rec.get("authors")
    paper = PaperRecord(
        cnki_id=rec.get("cnki_id"),
        doi=rec.get("doi"),
        title=title or "(无 title)",
        authors=authors,
        source=rec.get("source"),
        year=rec.get("year"),
        volume=rec.get("volume"),
        issue=rec.get("issue"),
        pages=rec.get("pages"),
        publisher=rec.get("publisher"),
        publish_place=rec.get("publish_place"),
        translator=rec.get("translator"),
        degree=rec.get("degree"),
        degree_place=rec.get("degree_place"),
        patent_country=rec.get("patent_country"),
        patent_kind=rec.get("patent_kind"),
        patent_no=rec.get("patent_no"),
        standard_code=rec.get("standard_code"),
        publish_date=rec.get("publish_date"),
        doc_type=rec.get("doc_type"),
    )
    rebuilt = format_gbt7714(paper)
    if rebuilt and rebuilt not in _PLACEHOLDER_CITES:
        return rebuilt
    raw = null_if_blank(rec.get("citation_gbt"))
    if raw:
        return normalize_citation_gbt(raw)
    return rebuilt or None


def row_values(rec: dict) -> dict:
    raw_title = (rec.get("title") or "").strip()
    title = _compact_title_text(raw_title) or raw_title or "(无 title)"
    authors = _normalize_authors_text(rec.get("authors")) or null_if_blank(rec.get("authors"))
    vals = {
        "cnki_id": null_if_blank(rec.get("cnki_id")),
        "doi": null_if_blank(rec.get("doi")),
        "title": title,
        "authors": authors,
        "organs": null_if_blank(rec.get("organs")),
        "abstract_text": null_if_blank(rec.get("abstract_text")),
        "keywords": null_if_blank(rec.get("keywords")),
        "title_zh": null_if_blank(rec.get("title_zh")),
        "abstract_zh": null_if_blank(rec.get("abstract_zh")),
        "keywords_zh": null_if_blank(rec.get("keywords_zh")),
        "source": clean_source(rec.get("source")),
        "year": rec.get("year"),
        "volume": null_if_blank(rec.get("volume")),
        "issue": null_if_blank(rec.get("issue")),
        "pages": null_if_blank(rec.get("pages")),
        "publisher": null_if_blank(rec.get("publisher")),
        "publish_place": null_if_blank(rec.get("publish_place")),
        "translator": null_if_blank(rec.get("translator")),
        "degree": null_if_blank(rec.get("degree")),
        "degree_place": null_if_blank(rec.get("degree_place")),
        "patent_country": null_if_blank(rec.get("patent_country")),
        "patent_kind": null_if_blank(rec.get("patent_kind")),
        "patent_no": null_if_blank(rec.get("patent_no")),
        "standard_code": null_if_blank(rec.get("standard_code")),
        "publish_date": null_if_blank(rec.get("publish_date")),
        "doc_type": null_if_blank(rec.get("doc_type")),
        "cite_count": rec.get("cite_count") or 0,
        "lit_source": null_if_blank(rec.get("lit_source")) or "CNKI",
        "citation_gbt": citation_from_rec(rec),
        "detail_url": null_if_blank(rec.get("detail_url")),
        "title_hash": null_if_blank(rec.get("title_hash")),
        "crawl_keyword": null_if_blank(rec.get("crawl_keyword")),
        "status": null_if_blank(rec.get("status")) or "active",
        "crawled_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    for key, limit in _VARCHAR_LIMITS.items():
        if key in vals and isinstance(vals[key], str):
            vals[key] = clip(vals[key], limit)
    return vals


def load_indexes(conn, table: str) -> tuple[dict[str, int], dict[str, int], dict[tuple[str, int | None], int]]:
    by_cnki: dict[str, int] = {}
    by_doi: dict[str, int] = {}
    by_hash: dict[tuple[str, int | None], int] = {}
    with conn.cursor() as cur:
        cur.execute(f"SELECT id, cnki_id, doi, title_hash, year FROM {table}")
        for pid, cnki_id, doi, title_hash, year in cur.fetchall():
            if cnki_id:
                by_cnki[cnki_id] = pid
            if doi:
                by_doi[doi] = pid
            if title_hash:
                by_hash[(title_hash, year)] = pid
    return by_cnki, by_doi, by_hash


def replace_refs(conn, ref_table: str, paper_id: int, references: list) -> None:
    with conn.cursor() as cur:
        cur.execute(f"DELETE FROM {ref_table} WHERE paper_id=%s", (paper_id,))
        for i, text in enumerate(references or [], start=1):
            raw = str(text).strip()
            if not raw:
                continue
            cur.execute(
                f"INSERT INTO {ref_table} (paper_id, ref_index, raw_text) VALUES (%s,%s,%s)",
                (paper_id, i, raw),
            )


def import_jsonl(
    conn,
    jsonl_path: Path,
    batch_size: int = 100,
    *,
    paper_table: str = "lit_paper",
    ref_table: str = "lit_paper_ref",
) -> tuple[int, int]:
    by_cnki, by_doi, by_hash = load_indexes(conn, paper_table)
    fields = upsert_fields_for(paper_table)
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
                    cols = ", ".join(fields)
                    ph = ", ".join(["%s"] * len(fields))
                    cur.execute(
                        f"INSERT INTO {paper_table} ({cols}) VALUES ({ph})",
                        tuple(vals[c] for c in fields),
                    )
                    paper_id = cur.lastrowid
                    inserted += 1
                else:
                    sets = ", ".join(f"{c}=%s" for c in fields if c != "cnki_id")
                    cur.execute(
                        f"UPDATE {paper_table} SET {sets} WHERE id=%s",
                        tuple(vals[c] for c in fields if c != "cnki_id") + (existing_id,),
                    )
                    paper_id = existing_id
                    updated += 1
                replace_refs(conn, ref_table, paper_id, rec.get("references") or [])
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


def resolve_tables(lang: str) -> tuple[str, str]:
    if lang == "en":
        return "lit_paper_en", "lit_paper_en_ref"
    return "lit_paper", "lit_paper_ref"


def main() -> int:
    p = argparse.ArgumentParser(description="Import CNKI JSONL into lit_paper / lit_paper_en")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", required=True)
    p.add_argument("--password", required=True)
    p.add_argument("--database", required=True)
    p.add_argument("--jsonl", required=True)
    p.add_argument("--batch-size", type=int, default=100)
    p.add_argument(
        "--lang",
        choices=["zh", "en"],
        default="zh",
        help="zh → lit_paper；en → lit_paper_en",
    )
    args = p.parse_args()
    path = Path(args.jsonl)
    if not path.exists():
        print(f"jsonl not found: {path}", file=sys.stderr)
        return 2
    paper_table, ref_table = resolve_tables(args.lang)
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
        inserted, updated = import_jsonl(
            conn, path, args.batch_size, paper_table=paper_table, ref_table=ref_table
        )
        print(f"done: table={paper_table} inserted={inserted} updated={updated}")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
