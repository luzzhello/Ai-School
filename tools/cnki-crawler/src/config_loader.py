from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml


class ConfigError(Exception):
    pass


def load_config(path: str | Path) -> dict[str, Any]:
    cfg_path = Path(path)
    if not cfg_path.exists():
        raise ConfigError(f"config not found: {cfg_path} (copy config.example.yaml to config.yaml)")
    data = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    cookie = str(data.get("cookie") or "").strip()
    if not cookie or cookie.startswith("REPLACE_WITH"):
        raise ConfigError("config.cookie must be set to a real browser Cookie string")
    return data


def load_keywords(path: str | Path) -> list[str]:
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    return [ln.strip() for ln in lines if ln.strip() and not ln.strip().startswith("#")]
