#!/usr/bin/env python3
"""初始化城市副本在通用 PostgreSQL 栈中的项目数据库。

脚本在初始化容器内调用 psql 连接通用栈 `common-postgres`：先用 common
PostgreSQL root 账号创建项目库和项目账号，再用项目账号执行
`database/migrations/` 下的 SQL。
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_MIGRATIONS_DIR = BASE_DIR / "migrations"
DEFAULT_SCHEMA_MARKER_TABLE = "users"


def main() -> int:
    args = parse_args()
    wait_for_postgres(args)
    ensure_project_role(args)
    ensure_project_database(args)
    ensure_project_extensions(args)

    if schema_exists(args):
        print(f"数据库已初始化，检测到表 {DEFAULT_SCHEMA_MARKER_TABLE}，跳过迁移。")
        return 0

    migration_files = sorted(args.migrations_dir.glob("V*.sql"))
    if not migration_files:
        print(f"未找到迁移脚本：{args.migrations_dir}", file=sys.stderr)
        return 1

    for migration_file in migration_files:
        print(f"执行迁移：{migration_file.name}")
        run_psql_file(args, migration_file)

    print("PostgreSQL 初始化完成。")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="初始化通用 PostgreSQL 中的城市副本数据库")
    parser.add_argument("--database", default=os.getenv("POSTGRES_DB", "urban_sidequest"))
    parser.add_argument("--user", default=os.getenv("POSTGRES_USER", "urban_sidequest"))
    parser.add_argument("--password", default=os.getenv("POSTGRES_PASSWORD", "urban_sidequest_dev"))
    parser.add_argument("--common-host", default=os.getenv("COMMON_POSTGRES_HOST", "common-postgres"))
    parser.add_argument("--common-port", default=int(os.getenv("COMMON_POSTGRES_PORT", "5432")), type=int)
    parser.add_argument("--common-database", default=os.getenv("COMMON_POSTGRES_DB", "postgres"))
    parser.add_argument("--common-user", default=os.getenv("COMMON_POSTGRES_USER", "postgres-root"))
    parser.add_argument(
        "--common-password",
        default=os.getenv("COMMON_POSTGRES_PASSWORD", "postgres-root-password"),
    )
    parser.add_argument("--migrations-dir", default=DEFAULT_MIGRATIONS_DIR, type=Path)
    return parser.parse_args()


def wait_for_postgres(args: argparse.Namespace) -> None:
    print(f"等待通用 PostgreSQL 可用：{args.common_host}:{args.common_port}")
    for _ in range(60):
        result = subprocess.run(
            with_password(
                args.common_password,
                "pg_isready",
                "-h",
                args.common_host,
                "-p",
                str(args.common_port),
                "-U",
                args.common_user,
                "-d",
                args.common_database,
            ),
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0:
            return
        time.sleep(2)

    raise RuntimeError(f"通用 PostgreSQL 不可用：{args.common_host}:{args.common_port}")


def ensure_project_role(args: argparse.Namespace) -> None:
    print(f"创建或更新项目 PostgreSQL 账号：{args.user}")
    run_admin_sql(
        args,
        args.common_database,
        """
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'project_user', :'project_password')
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = :'project_user'
)\\gexec

ALTER ROLE :"project_user" WITH LOGIN PASSWORD :'project_password';
""",
        variables={
            "project_user": args.user,
            "project_password": args.password,
        },
    )


def ensure_project_database(args: argparse.Namespace) -> None:
    print(f"创建项目数据库：{args.database}")
    run_admin_sql(
        args,
        args.common_database,
        """
SELECT format('CREATE DATABASE %I OWNER %I', :'project_database', :'project_user')
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = :'project_database'
)\\gexec

ALTER DATABASE :"project_database" OWNER TO :"project_user";
GRANT ALL PRIVILEGES ON DATABASE :"project_database" TO :"project_user";
""",
        variables={
            "project_database": args.database,
            "project_user": args.user,
        },
    )


def ensure_project_extensions(args: argparse.Namespace) -> None:
    print("启用项目数据库扩展：postgis、pgcrypto")
    run_admin_sql(
        args,
        args.database,
        """
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
GRANT USAGE, CREATE ON SCHEMA public TO :"project_user";
""",
        variables={"project_user": args.user},
    )


def schema_exists(args: argparse.Namespace) -> bool:
    result = run_project_psql(
        args,
        [
            "-tAc",
            (
                "SELECT EXISTS ("
                "SELECT 1 FROM information_schema.tables "
                f"WHERE table_schema = 'public' AND table_name = '{DEFAULT_SCHEMA_MARKER_TABLE}'"
                ");"
            ),
        ],
        capture_output=True,
    )
    return result.stdout.strip().lower() == "t"


def run_psql_file(args: argparse.Namespace, migration_file: Path) -> None:
    with migration_file.open("rb") as stdin:
        run_project_psql(args, ["-v", "ON_ERROR_STOP=1"], stdin=stdin)


def run_admin_sql(
    args: argparse.Namespace,
    database: str,
    sql: str,
    variables: dict[str, str],
) -> subprocess.CompletedProcess[str]:
    command = base_psql_command(args, args.common_password, args.common_user, database)
    for key, value in variables.items():
        command.extend(["-v", f"{key}={value}"])
    return subprocess.run(
        command,
        input=sql,
        check=True,
        text=True,
    )


def run_project_psql(
    args: argparse.Namespace,
    extra_args: list[str],
    *,
    capture_output: bool = False,
    stdin: object | None = None,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        base_psql_command(args, args.password, args.user, args.database) + extra_args,
        check=True,
        capture_output=capture_output,
        stdin=stdin,
        text=stdin is None,
    )


def base_psql_command(
    args: argparse.Namespace,
    password: str,
    user: str,
    database: str,
) -> list[str]:
    return with_password(
        password,
        "psql",
        "-v",
        "ON_ERROR_STOP=1",
        "-h",
        args.common_host,
        "-p",
        str(args.common_port),
        "-U",
        user,
        "-d",
        database,
    )


def with_password(password: str, *command: str) -> list[str]:
    return [
        "env",
        f"PGPASSWORD={password}",
        *command,
    ]


if __name__ == "__main__":
    raise SystemExit(main())
