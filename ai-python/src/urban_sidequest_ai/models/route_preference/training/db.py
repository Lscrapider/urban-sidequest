from __future__ import annotations

from dataclasses import dataclass
import os
from typing import Any

from urban_sidequest_ai.env_loader import load_runtime_env


@dataclass(frozen=True)
class DatabaseConfig:
    dsn: str | None = None
    host: str | None = None
    port: int | None = None
    dbname: str | None = None
    user: str | None = None
    password: str | None = None
    sslmode: str | None = None
    connect_timeout: int = 0

    def connection_kwargs(self) -> dict[str, Any]:
        if self.dsn:
            return {"conninfo": self.dsn, "connect_timeout": self.connect_timeout}
        kwargs: dict[str, Any] = {"connect_timeout": self.connect_timeout}
        for key in ("host", "port", "dbname", "user", "password", "sslmode"):
            value = getattr(self, key)
            if value is not None and value != "":
                kwargs[key] = value
        if "dbname" not in kwargs:
            raise ValueError("数据库配置缺少 dsn 或 dbname")
        return kwargs


def load_database_config() -> DatabaseConfig:
    load_runtime_env()
    dsn = _env_first(
        "ROUTE_PREF_DB_DSN",
        "URBAN_SIDEQUEST_AI_DB_DSN",
        "DATABASE_URL",
    )
    connect_timeout = _required_int_env("ROUTE_PREF_DB_CONNECT_TIMEOUT")
    if dsn:
        return DatabaseConfig(dsn=dsn, connect_timeout=connect_timeout)
    return DatabaseConfig(
        host=_required_env("ROUTE_PREF_DB_HOST"),
        port=_required_int_env("ROUTE_PREF_DB_PORT"),
        dbname=_required_env("ROUTE_PREF_DB_NAME"),
        user=_required_env("ROUTE_PREF_DB_USER"),
        password=_required_env("ROUTE_PREF_DB_PASSWORD"),
        sslmode=_env_first("ROUTE_PREF_DB_SSLMODE"),
        connect_timeout=connect_timeout,
    )


def connect(config: DatabaseConfig):
    import psycopg

    return psycopg.connect(**config.connection_kwargs())


def _env_first(*keys: str) -> str | None:
    for key in keys:
        value = os.environ.get(key)
        if value:
            return value
    return None


def _required_env(key: str) -> str:
    value = _env_first(key)
    if not value:
        raise ValueError(f"环境变量 {key} 未配置")
    return value


def _required_int_env(key: str) -> int:
    return int(_required_env(key))
