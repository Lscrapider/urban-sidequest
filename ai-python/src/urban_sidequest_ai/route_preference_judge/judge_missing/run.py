from __future__ import annotations

import argparse
from pathlib import Path
import sys


BASE_DIR = Path(__file__).resolve().parent
AI_SRC_ROOT = BASE_DIR.parents[2]
if str(AI_SRC_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SRC_ROOT))

from urban_sidequest_ai.models.route_preference.training.db import load_database_config
from urban_sidequest_ai.route_preference_judge.config import load_config
from urban_sidequest_ai.route_preference_judge.judge_missing.runner import run_missing_judgments


JUDGE_CONFIG_FILE = BASE_DIR.parent / "config.json"

# PyCharm 直接运行时改这里即可。
LIMIT = 100
JUDGE_CONCURRENCY = 4
DRY_RUN = False
CANDIDATE_SET_IDS: list[str] = []
TARGET_K = 3
ORIGINAL_K = 1


def parse_args(argv: list[str] | None = None):
    parser = argparse.ArgumentParser(
        description="从 raw snapshot 复用冻结路线，为已有 candidate set 补齐 LLM_SIM_USER judgment 到目标 k。",
    )
    parser.add_argument("--config", default=str(JUDGE_CONFIG_FILE), help="judge 策略配置 JSON。")
    parser.add_argument("--limit", type=int, default=LIMIT, help="最多处理多少个 candidate set；传 0 表示不限制。")
    parser.add_argument("--judge-concurrency", type=int, default=JUDGE_CONCURRENCY, help="LLM judge 并发数。")
    parser.add_argument("--dry-run", action="store_true", default=DRY_RUN, help="只打印计划和 judgment payload，不保存。")
    parser.add_argument("--target-k", type=int, default=TARGET_K, help="目标 completed judgment 数，默认 3。")
    parser.add_argument(
        "--original-k",
        "--o-k",
        dest="original_k",
        type=int,
        default=ORIGINAL_K,
        help="只补当前 completed judgment 数等于该值的 candidate set；不传则补所有 c < target-k。",
    )
    parser.add_argument(
        "--candidate-set-ids",
        default=",".join(CANDIDATE_SET_IDS),
        help="逗号分隔的 candidate_set_id 子集；不传则不限制。",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    candidate_set_ids = [item.strip() for item in args.candidate_set_ids.split(",") if item.strip()]
    app_config = load_config(Path(args.config), require_api_key=not args.dry_run)
    database_config = load_database_config()
    stats = run_missing_judgments(
        app_config=app_config,
        database_config=database_config,
        limit=None if args.limit == 0 else args.limit,
        judge_concurrency=args.judge_concurrency,
        dry_run=args.dry_run,
        candidate_set_ids=candidate_set_ids,
        target_k=args.target_k,
        original_k=args.original_k,
    )
    print(
        "补跑完成："
        f"rawSnapshots={stats.raw_snapshots}, "
        f"candidateSets={stats.candidate_sets}, "
        f"judgmentsPlanned={stats.judgments_planned}, "
        f"judgmentsSaved={stats.judgments_saved}, "
        f"judgmentsFailed={stats.judgments_failed}, "
        f"skipped={stats.skipped}"
    )
    return 0 if stats.judgments_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
