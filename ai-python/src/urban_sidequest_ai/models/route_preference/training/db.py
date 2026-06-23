from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import json
import os
from typing import Any


DEFAULT_DB_CONNECT_TIMEOUT_SECONDS = 10


@dataclass(frozen=True)
class DatabaseConfig:
    dsn: str | None = None
    host: str | None = None
    port: int | None = None
    dbname: str | None = None
    user: str | None = None
    password: str | None = None
    sslmode: str | None = None
    connect_timeout: int = DEFAULT_DB_CONNECT_TIMEOUT_SECONDS

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


def load_database_config(path: Path | None = None) -> DatabaseConfig:
    raw = _load_raw_config(path)
    db_raw = raw.get("database") or raw.get("db") or raw
    dsn = _first_present(db_raw, "dsn", "url", "databaseUrl", "database_url") or _env_first(
        "ROUTE_PREF_DB_DSN",
        "URBAN_SIDEQUEST_AI_DB_DSN",
        "DATABASE_URL",
    )
    connect_timeout = int(
        _first_present(db_raw, "connectTimeout", "connect_timeout")
        or os.environ.get("ROUTE_PREF_DB_CONNECT_TIMEOUT")
        or DEFAULT_DB_CONNECT_TIMEOUT_SECONDS
    )
    return DatabaseConfig(
        dsn=dsn,
        host=_first_present(db_raw, "host") or os.environ.get("ROUTE_PREF_DB_HOST"),
        port=_optional_int(_first_present(db_raw, "port") or os.environ.get("ROUTE_PREF_DB_PORT")),
        dbname=_first_present(db_raw, "dbname", "database", "databaseName") or os.environ.get("ROUTE_PREF_DB_NAME"),
        user=_first_present(db_raw, "user", "username") or os.environ.get("ROUTE_PREF_DB_USER"),
        password=_first_present(db_raw, "password") or os.environ.get("ROUTE_PREF_DB_PASSWORD"),
        sslmode=_first_present(db_raw, "sslmode", "sslMode") or os.environ.get("ROUTE_PREF_DB_SSLMODE"),
        connect_timeout=connect_timeout,
    )


def connect(config: DatabaseConfig):
    import psycopg

    return psycopg.connect(**config.connection_kwargs())


def _load_raw_config(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {}
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def _first_present(raw: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = raw.get(key)
        if value is not None and value != "":
            return value
    return None


def _env_first(*keys: str) -> str | None:
    for key in keys:
        value = os.environ.get(key)
        if value:
            return value
    return None


def _optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    return int(value)
