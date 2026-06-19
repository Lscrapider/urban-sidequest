from __future__ import annotations

import json
from pathlib import Path
import sys

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from scripts.route_preference_simulator.config import load_config
from scripts.route_preference_simulator.job_factory import build_jobs
from scripts.route_preference_simulator.runner import load_route_jobs, run

CONFIG_FILE = BASE_DIR / "config.json"
REQUESTS_FILE = BASE_DIR / "requests.json"


def ensure_requests_file() -> None:
    if REQUESTS_FILE.exists():
        return
    jobs = build_jobs(
        persona_count=1,
        requests_per_persona=1,
        city_keys=["shanghai"],
    )
    REQUESTS_FILE.write_text(json.dumps(jobs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已生成默认单条请求：{REQUESTS_FILE}")


def main() -> int:
    if not CONFIG_FILE.exists():
        raise FileNotFoundError(f"缺少配置文件：{CONFIG_FILE}")
    ensure_requests_file()
    config = load_config(CONFIG_FILE)
    jobs = load_route_jobs(REQUESTS_FILE)
    stats = run(config, jobs, dry_run=False)
    print(
        "完成："
        f"routeRequests={stats.route_requests}, "
        f"candidateSets={stats.candidate_sets}, "
        f"judgmentsSaved={stats.judgments_saved}, "
        f"judgmentsFailed={stats.judgments_failed}"
    )
    return 0 if stats.judgments_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
