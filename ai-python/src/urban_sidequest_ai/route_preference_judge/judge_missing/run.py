from __future__ import annotations

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
LIMIT = 20
JUDGE_CONCURRENCY = 3
DRY_RUN = False
CANDIDATE_SET_IDS: list[str] = []


def main() -> int:
    app_config = load_config(JUDGE_CONFIG_FILE, require_api_key=not DRY_RUN)
    database_config = load_database_config()
    stats = run_missing_judgments(
        app_config=app_config,
        database_config=database_config,
        limit=LIMIT,
        judge_concurrency=JUDGE_CONCURRENCY,
        dry_run=DRY_RUN,
        candidate_set_ids=CANDIDATE_SET_IDS,
    )
    print(
        "补跑完成："
        f"rawSnapshots={stats.raw_snapshots}, "
        f"candidateSets={stats.candidate_sets}, "
        f"judgmentsSaved={stats.judgments_saved}, "
        f"judgmentsFailed={stats.judgments_failed}, "
        f"skipped={stats.skipped}"
    )
    return 0 if stats.judgments_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
