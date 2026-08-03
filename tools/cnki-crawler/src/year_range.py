from __future__ import annotations

from datetime import date
from typing import Any


def resolve_year_range(cfg: dict[str, Any]) -> tuple[int | None, int | None]:
    """解析配置中的年份范围。

    优先 ``recent_years``（过去 N 年，含当年）；否则用 ``from_year`` / ``to_year``。
    """
    recent = cfg.get("recent_years")
    if recent is not None and str(recent).strip() != "":
        n = int(recent)
        if n > 0:
            to_y = int(date.today().year)
            from_y = to_y - n + 1
            return from_y, to_y
    from_y = cfg.get("from_year")
    to_y = cfg.get("to_year")
    return (
        int(from_y) if from_y is not None and str(from_y).strip() != "" else None,
        int(to_y) if to_y is not None and str(to_y).strip() != "" else None,
    )


def year_span_list(from_year: int | None, to_year: int | None) -> list[int] | None:
    """返回闭区间年份列表；任一端缺失则返回 None（表示不按年拆分）。"""
    if from_year is None or to_year is None:
        return None
    y0, y1 = int(from_year), int(to_year)
    if y0 > y1:
        y0, y1 = y1, y0
    return list(range(y0, y1 + 1))


def split_quota_evenly(total: int, parts: int) -> list[int]:
    """将 total 均分到 parts 份；余数补给更靠后的年份（通常更新的年份）。"""
    if parts <= 0:
        return []
    if total <= 0:
        return [0] * parts
    base = total // parts
    rem = total % parts
    # 例如 50 / 3 → [16, 17, 17]（较新年份多 1）
    return [base + (1 if i >= parts - rem else 0) for i in range(parts)]
