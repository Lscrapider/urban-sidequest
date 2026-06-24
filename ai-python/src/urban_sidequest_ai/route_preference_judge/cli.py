from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .config import load_config
from .job_factory import build_jobs
from .presets import DEFAULT_CITY_KEYS
from .runner import load_route_jobs, run

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG_FILE = BASE_DIR / "config.json"
DEFAULT_REQUESTS_FILE = BASE_DIR / "requests.json"


def parse_args():
    parser = argparse.ArgumentParser(description="批量生成路线并调用 LLM 模拟用户保存路线偏好 judgment。")
    subparsers = parser.add_subparsers(dest="command")

    run_parser = subparsers.add_parser("run", help="执行路线生成 + LLM 模拟用户评价。")
    run_parser.add_argument("--config", default=str(DEFAULT_CONFIG_FILE), help="配置 JSON，默认读取模块目录下的 config.json。")
    run_parser.add_argument("--requests", default=str(DEFAULT_REQUESTS_FILE), help="路线请求 JSON 数组，默认读取模块目录下的 requests.json。")
    run_parser.add_argument("--dry-run", action="store_true", help="不调用 Java 后端，使用内置假路线并打印 judgment payload。")
    run_parser.add_argument("--concurrency", type=int, default=1, help="路线生成并发数，默认 1；本地小批量可设为 2。")
    run_parser.add_argument(
        "--judge-concurrency",
        type=int,
        help="LLM judge 并发数；不传则与 --concurrency 相同。",
    )

    generate_parser = subparsers.add_parser("generate-jobs", help="生成可直接用于 run 的画像 + request 输入文件。")
    generate_parser.add_argument("--output", default=str(DEFAULT_REQUESTS_FILE), help="输出 requests JSON 路径，默认写入模块目录下的 requests.json。")
    generate_parser.add_argument("--persona-count", type=int, default=100, help="画像数量，默认 100。")
    generate_parser.add_argument("--requests-per-persona", type=int, default=20, help="每个画像生成 request 数，默认 20。")
    generate_parser.add_argument("--request-count", type=int, help="按 request 主轴生成的基础 request 数；设置后启用 90/10 探针策略。")
    generate_parser.add_argument("--probe-ratio", type=float, default=0.1, help="启用 --request-count 时，多 persona 探针 request 占比，默认 0.1。")
    generate_parser.add_argument("--probe-persona-count", type=int, default=2, help="启用 --request-count 时，每个探针 request 生成的 persona 数，默认 2。")
    generate_parser.add_argument("--seed", type=int, help="随机种子；不传则每次生成不同，传入后可复现。")
    generate_parser.add_argument(
        "--cities",
        default=",".join(DEFAULT_CITY_KEYS),
        help="城市 preset，逗号分隔。默认使用 presets.py 中的全部城市。",
    )
    argv = sys.argv[1:]
    if argv and argv[0].startswith("--"):
        argv = ["run", *argv]
    return parser.parse_args(argv)


def main() -> int:
    args = parse_args()
    if args.command is None:
        raise SystemExit("请使用 run 或 generate-jobs 子命令")
    if args.command == "generate-jobs":
        city_keys = [item.strip() for item in args.cities.split(",") if item.strip()]
        jobs = build_jobs(
            persona_count=args.persona_count,
            requests_per_persona=args.requests_per_persona,
            seed=args.seed,
            city_keys=city_keys,
            request_count=args.request_count,
            probe_ratio=args.probe_ratio,
            probe_persona_count=args.probe_persona_count,
        )
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(jobs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"已生成 {len(jobs)} 个 job：{output}")
        return 0

    config = load_config(Path(args.config), require_api_key=not args.dry_run)
    jobs = load_route_jobs(Path(args.requests))
    stats = run(
        config,
        jobs,
        dry_run=args.dry_run,
        concurrency=args.concurrency,
        judge_concurrency=args.judge_concurrency,
    )
    print(
        "完成："
        f"routeRequests={stats.route_requests}, "
        f"candidateSets={stats.candidate_sets}, "
        f"judgmentsSaved={stats.judgments_saved}, "
        f"judgmentsFailed={stats.judgments_failed}"
    )
    return 0 if stats.judgments_failed == 0 else 1
