#!/usr/bin/env python3
"""初始化城市副本本地数据库。

默认通过当前目录的 Docker Compose postgres 服务执行 psql，不依赖宿主机安装 psql
或 Python 数据库驱动。脚本只在 users 表不存在时执行 migrations 下的 SQL。
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_MIGRATIONS_DIR = BASE_DIR / "migrations"
DEFAULT_SCHEMA_MARKER_TABLE = "users"


def main() -> int:
    args = parse_args()
    env = {
        **os.environ,
        "POSTGRES_DB": args.database,
        "POSTGRES_USER": args.user,
        "POSTGRES_PASSWORD": args.password,
        "POSTGRES_PORT": str(args.port),
    }

    if schema_exists(args, env):
        print(f"数据库已初始化，检测到表 {DEFAULT_SCHEMA_MARKER_TABLE}，跳过。")
        return 0

    migration_files = sorted(args.migrations_dir.glob("V*.sql"))
    if not migration_files:
        print(f"未找到迁移脚本：{args.migrations_dir}", file=sys.stderr)
        return 1

    for migration_file in migration_files:
        print(f"执行迁移：{migration_file.name}")
        run_psql_file(args, env, migration_file)

    print("数据库初始化完成。")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="初始化城市副本 PostgreSQL/PostGIS 数据库")
    parser.add_argument("--database", default=os.getenv("POSTGRES_DB", "urban_sidequest"))
    parser.add_argument("--user", default=os.getenv("POSTGRES_USER", "urban_sidequest"))
    parser.add_argument("--password", default=os.getenv("POSTGRES_PASSWORD", "urban_sidequest_dev"))
    parser.add_argument("--port", default=int(os.getenv("POSTGRES_PORT", "5432")), type=int)
    parser.add_argument("--service", default=os.getenv("POSTGRES_SERVICE", "postgres"))
    parser.add_argument("--migrations-dir", default=DEFAULT_MIGRATIONS_DIR, type=Path)
    return parser.parse_args()


def schema_exists(args: argparse.Namespace, env: dict[str, str]) -> bool:
    command = docker_compose_psql_command(args) + [
        "-tAc",
        (
            "SELECT EXISTS ("
            "SELECT 1 FROM information_schema.tables "
            f"WHERE table_schema = 'public' AND table_name = '{DEFAULT_SCHEMA_MARKER_TABLE}'"
            ");"
        ),
    ]
    result = subprocess.run(
        command,
        cwd=BASE_DIR,
        env=env,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip().lower() == "t"


def run_psql_file(args: argparse.Namespace, env: dict[str, str], migration_file: Path) -> None:
    command = docker_compose_psql_command(args) + [
        "-v",
        "ON_ERROR_STOP=1",
        "-f",
        f"/migrations/{migration_file.name}",
    ]
    subprocess.run(command, cwd=BASE_DIR, env=env, check=True)


def docker_compose_psql_command(args: argparse.Namespace) -> list[str]:
    return [
        "docker",
        "compose",
        "exec",
        "-T",
        args.service,
        "psql",
        "-U",
        args.user,
        "-d",
        args.database,
    ]


if __name__ == "__main__":
    raise SystemExit(main())
