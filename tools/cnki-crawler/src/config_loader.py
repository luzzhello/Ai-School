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
    try:
        data = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError as e:
        raise ConfigError(
            f"invalid YAML in {cfg_path}: {e}\n"
            "提示: Cookie 若含双引号/JSON，请用块写法:\n"
            "cookie: |\n"
            "  你的Cookie整行粘贴在这里"
        ) from e
    cookie = str(data.get("cookie") or "").strip()
    data["cookie"] = cookie
    if not cookie or cookie.startswith("REPLACE_WITH"):
        raise ConfigError("config.cookie must be set to a real browser Cookie string")
    return data


def load_keywords(path: str | Path) -> list[str]:
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    return [ln.strip() for ln in lines if ln.strip() and not ln.strip().startswith("#")]
