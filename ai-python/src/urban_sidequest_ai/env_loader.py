from __future__ import annotations

from pathlib import Path
import os

from dotenv import dotenv_values


PROJECT_ROOT = Path(__file__).resolve().parents[3]
ENV_FILES = (".env", ".env.dev")
SKIP_ENV_FILE_VALUE = "1"

_LOADED = False


def load_runtime_env() -> None:
    """加载本地 dotenv 配置；容器/CI 已有环境变量保持最高优先级。"""
    global _LOADED
    if _LOADED or os.environ.get("URBAN_SIDEQUEST_SKIP_ENV_FILE") == SKIP_ENV_FILE_VALUE:
        return

    protected_keys = set(os.environ)
    for filename in ENV_FILES:
        env_path = PROJECT_ROOT / filename
        if not env_path.exists():
            continue
        for key, value in dotenv_values(env_path).items():
            if not key or value in (None, "") or key in protected_keys:
                continue
            os.environ[key] = value

    _LOADED = True
