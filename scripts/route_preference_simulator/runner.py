from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
import json
import random
import sys

from .config import AppConfig
from .java_client import BackendClient
from .llm_client import LlmClient
from .prompt import build_user_prompt
from .validation import validate_judgment


@dataclass(frozen=True)
class RunStats:
    route_requests: int = 0
    candidate_sets: int = 0
    judgments_saved: int = 0
    judgments_failed: int = 0


def load_route_jobs(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as file:
        raw = json.load(file)
    if not isinstance(raw, list):
        raise ValueError("requests 文件必须是数组")
    return raw


def run(config: AppConfig, jobs: list[dict], dry_run: bool = False) -> RunStats:
    rng = random.Random(config.judge.seed)
    backend = BackendClient(config.backend)
    stats = RunStats()
    saved = 0
    failed = 0
    candidate_sets = 0

    for index, job in enumerate(jobs, start=1):
        persona = job.get("persona")
        route_request = build_route_request(job, persona)
        print(f"[{index}/{len(jobs)}] 生成路线...", file=sys.stderr)
        if dry_run:
            route_generation = _mock_route_generation(index)
        else:
            route_generation = backend.generate_route(route_request)

        route_generation = _unwrap_route_generation(route_generation)
        candidate_set_id = route_generation.get("candidateSetId")
        routes = route_generation.get("routes") or []
        route_codes = [route.get("routeCode") for route in routes if isinstance(route, dict) and route.get("routeCode")]
        if not candidate_set_id or not route_codes:
            print("  跳过：路线生成响应不可评价", file=sys.stderr)
            print(f"    candidateSetIdPresent={bool(candidate_set_id)} routesCount={len(routes) if isinstance(routes, list) else 'not-list'} routeCodes={route_codes}", file=sys.stderr)
            _print_route_warnings(route_generation)
            continue

        candidate_sets += 1
        selected_llms = select_llms(config, rng)
        user_prompt = build_user_prompt(route_request, route_generation, persona)
        print(f"  candidateSetId={candidate_set_id} routes={route_codes} judges={len(selected_llms)}", file=sys.stderr)

        for llm in selected_llms:
            try:
                judgment = fake_judgment(route_codes) if dry_run else call_with_retry(llm, config, user_prompt, route_codes)
                payload = {
                    "candidateSetId": candidate_set_id,
                    "judgeType": "LLM_SIM_USER",
                    "judgeModel": llm.judge_model,
                    "judgePromptVersion": config.judge.prompt_version,
                    **judgment,
                }
                if dry_run:
                    print(json.dumps(payload, ensure_ascii=False, indent=2))
                else:
                    backend.save_judgment(payload)
                saved += 1
                print(f"  saved judgment: {llm.judge_model}", file=sys.stderr)
            except Exception as exception:
                failed += 1
                print(f"  failed judgment: {llm.judge_model}: {exception}", file=sys.stderr)

    return RunStats(
        route_requests=len(jobs),
        candidate_sets=candidate_sets,
        judgments_saved=saved,
        judgments_failed=failed,
    )


def build_route_request(job: dict, persona: dict | None) -> dict:
    route_request = deepcopy(job.get("request") or job)
    if persona and "userPreferenceProfileOverride" not in route_request:
        route_request["userPreferenceProfileOverride"] = deepcopy(persona)
    return route_request


def _unwrap_route_generation(response: dict) -> dict:
    if "candidateSetId" in response or "routes" in response:
        return response
    for key in ("data", "result"):
        value = response.get(key)
        if isinstance(value, dict):
            return value
    return response


def _print_route_warnings(route_generation: dict) -> None:
    status = route_generation.get("status")
    warnings = route_generation.get("warnings") or []
    if status:
        print(f"    status={status}", file=sys.stderr)
    for warning in warnings[:5]:
        print(f"    warning={warning}", file=sys.stderr)
    if len(warnings) > 5:
        print(f"    warning=... 还有 {len(warnings) - 5} 条", file=sys.stderr)


def select_llms(config: AppConfig, rng: random.Random):
    if rng.random() < config.judge.full_judge_ratio:
        return list(config.llm_pool)
    count = min(config.judge.judges_per_candidate_set, len(config.llm_pool))
    return rng.sample(config.llm_pool, count)


def call_with_retry(llm, config: AppConfig, user_prompt: str, route_codes: list[str]) -> dict:
    client = LlmClient(llm, config.judge)
    last_error = None
    for attempt in range(config.judge.max_retries + 1):
        try:
            raw = client.judge(user_prompt)
            try:
                return validate_judgment(raw, route_codes)
            except Exception as validation_exception:
                print(
                    f"  raw judgment invalid: {llm.judge_model} attempt={attempt + 1} error={validation_exception}",
                    file=sys.stderr,
                )
                print(_debug_json(raw), file=sys.stderr)
                raise
        except Exception as exception:
            last_error = exception
    raise RuntimeError(last_error)


def _debug_json(value, max_length: int = 3000) -> str:
    try:
        text = json.dumps(value, ensure_ascii=False, indent=2, default=str)
    except TypeError:
        text = repr(value)
    if len(text) <= max_length:
        return text
    return text[:max_length] + f"\n... truncated {len(text) - max_length} chars"


def fake_judgment(route_codes: list[str]) -> dict:
    rejected = route_codes[-1:] if route_codes else []
    accepted_count = min(2, max(0, len(route_codes) - len(rejected)))
    accepted = route_codes[:accepted_count]
    reason_codes = {rejected[0]: ["HIGH_FATIGUE"]} if rejected else {}
    return validate_judgment(
        {
            "ranking": route_codes,
            "acceptedRouteCodes": accepted,
            "rejectedRouteCodes": rejected,
            "reasonCodes": reason_codes,
            "confidence": 0.5,
        },
        route_codes,
    )


def _mock_route_generation(index: int) -> dict:
    candidate_set_id = f"dry-run-candidate-set-{index}"
    return {
        "candidateSetId": candidate_set_id,
        "routes": [
            {
                "routeCode": "A",
                "title": "路线 A",
                "summary": "本地生活与咖啡休息结合。",
                "totalDurationMinutes": 220,
                "totalDistanceMeters": 2600,
                "budgetCent": 9000,
                "riskLevel": "LOW",
                "explanation": "节奏平衡，饭点和休息点完整。",
                "stops": [
                    {"name": "老街入口", "slotLabel": "本地体验", "stayMinutes": 45, "transportToNext": "WALK", "distanceToNextMeters": 600, "description": "适合城市漫步"},
                    {"name": "咖啡馆", "slotLabel": "休息", "stayMinutes": 30, "transportToNext": None, "distanceToNextMeters": None, "description": "中途休息"},
                ],
            },
            {
                "routeCode": "B",
                "title": "路线 B",
                "summary": "经典点位更多。",
                "totalDurationMinutes": 235,
                "totalDistanceMeters": 4200,
                "budgetCent": 15000,
                "riskLevel": "MEDIUM",
                "explanation": "内容丰富但移动压力略高。",
                "stops": [
                    {"name": "热门景点", "slotLabel": "经典景点", "stayMinutes": 75, "transportToNext": "WALK", "distanceToNextMeters": 1600, "description": "经典但人多"},
                    {"name": "商圈", "slotLabel": "餐饮", "stayMinutes": 60, "transportToNext": None, "distanceToNextMeters": None, "description": "消费偏高"},
                ],
            },
        ],
    }
