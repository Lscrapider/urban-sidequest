from __future__ import annotations

import json
from pathlib import Path
import sys

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

if __package__:
    from .config import load_config
    from .job_factory import build_jobs
    from .runner import load_route_jobs, run
else:
    from urban_sidequest_ai.route_preference_judge.config import load_config
    from urban_sidequest_ai.route_preference_judge.job_factory import build_jobs
    from urban_sidequest_ai.route_preference_judge.runner import load_route_jobs, run

CONFIG_FILE = BASE_DIR / "config.json"
REQUESTS_FILE = BASE_DIR / "requests.json"
ROUTE_CONCURRENCY = 5
JUDGE_CONCURRENCY = 6
# 仅当 config.json 里 judgesPerCandidateSet > 1 时生效：
# 1.0 表示全部 route 走配置的 LLM judge 数；0.6 表示约 60% 走完整多 judge，其余走单 judge。
MULTI_JUDGE_RATIO = 0.8


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
    ensure_requests_file()
    config = load_config(CONFIG_FILE)
    jobs = load_route_jobs(REQUESTS_FILE)
    stats = run(
        config,
        jobs,
        dry_run=False,
        concurrency=ROUTE_CONCURRENCY,
        judge_concurrency=JUDGE_CONCURRENCY,
        multi_judge_ratio=MULTI_JUDGE_RATIO,
    )
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
