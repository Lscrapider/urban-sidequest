from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
import json
import random
import sys
from threading import Lock
from time import monotonic

from .config import AppConfig
from .java_client import BackendClient
from .llm_client import LlmClient
from .prompt import build_user_prompt
from .validation import validate_judgment

MAX_LLM_FALLBACK_ATTEMPTS = 3
PRINT_LOCK = Lock()
PERSONAL_REVIEW_FIELD = "personalReview"


@dataclass(frozen=True)
class RunStats:
    route_requests: int = 0
    candidate_sets: int = 0
    judgments_saved: int = 0
    judgments_failed: int = 0


@dataclass(frozen=True)
class JobResult:
    stats: RunStats
    stdout_payloads: list[str]


@dataclass(frozen=True)
class JudgmentTask:
    index: int
    total_jobs: int
    candidate_set_id: str
    route_codes: list[str]
    user_prompt: str
    llm_attempt_groups: list


@dataclass(frozen=True)
class RouteJobResult:
    stats: RunStats
    judgment_tasks: list[JudgmentTask]


def load_route_jobs(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as file:
        raw = json.load(file)
    if not isinstance(raw, list):
        raise ValueError("requests 文件必须是数组")
    return raw


def run(
    config: AppConfig,
    jobs: list[dict],
    dry_run: bool = False,
    concurrency: int = 1,
    judge_concurrency: int | None = None,
) -> RunStats:
    if concurrency < 1:
        raise ValueError("concurrency 必须 >= 1")
    judge_concurrency = judge_concurrency or concurrency
    if judge_concurrency < 1:
        raise ValueError("judge_concurrency 必须 >= 1")
    rng = random.Random(config.judge.seed)
    backend = BackendClient(config.backend)
    if not dry_run:
        backend.ensure_token()

    llm_order = list(config.llm_pool)
    rng.shuffle(llm_order)
    llm_cursor = 0
    job_inputs = []
    for index, job in enumerate(jobs, start=1):
        persona = job.get("persona")
        route_request = build_route_request(job, persona)
        selected_llms, llm_cursor = select_llms(config, rng, llm_order, llm_cursor)
        llm_attempt_groups = [llm_attempt_candidates(config, llm, rng) for llm in selected_llms]
        job_inputs.append((index, persona, route_request, llm_attempt_groups))

    route_results: list[RouteJobResult] = []
    judgment_results: list[JobResult] = []
    with ThreadPoolExecutor(max_workers=concurrency) as route_executor:
        with ThreadPoolExecutor(max_workers=judge_concurrency) as judge_executor:
            route_futures = [
                route_executor.submit(_run_route_job, config, backend, len(jobs), dry_run, *job_input)
                for job_input in job_inputs
            ]
            judgment_futures = []
            for future in as_completed(route_futures):
                result = future.result()
                route_results.append(result)
                for task in result.judgment_tasks:
                    judgment_futures.append(
                        judge_executor.submit(_run_judgment_task, config, backend, dry_run, task)
                    )
            for future in as_completed(judgment_futures):
                result = future.result()
                _print_job_result(result)
                judgment_results.append(result)

    return RunStats(
        route_requests=len(jobs),
        candidate_sets=sum(result.stats.candidate_sets for result in route_results),
        judgments_saved=sum(result.stats.judgments_saved for result in judgment_results),
        judgments_failed=(
            sum(result.stats.judgments_failed for result in route_results)
            + sum(result.stats.judgments_failed for result in judgment_results)
        ),
    )


def _run_route_job(
    config: AppConfig,
    backend: BackendClient,
    total_jobs: int,
    dry_run: bool,
    index: int,
    persona: dict | None,
    route_request: dict,
    llm_attempt_groups,
) -> RouteJobResult:
    _print_stderr(f"[{index}/{total_jobs}] 生成路线...")
    failed = 0
    candidate_sets = 0

    route_started_at = monotonic()
    try:
        if dry_run:
            route_generation = _mock_route_generation(index)
        else:
            route_generation = backend.generate_route(route_request)
    except Exception as exception:
        failed += len(llm_attempt_groups)
        _print_stderr(
            f"[{index}/{total_jobs}] 路线生成失败，用时={monotonic() - route_started_at:.1f}s，"
            f"跳过本 job: {exception}"
        )
        return RouteJobResult(
            RunStats(
                route_requests=1,
                candidate_sets=0,
                judgments_saved=0,
                judgments_failed=failed,
            ),
            [],
        )
    _print_stderr(f"[{index}/{total_jobs}] 路线生成完毕，用时={monotonic() - route_started_at:.1f}s，开始解析响应...")

    route_generation = _unwrap_route_generation(route_generation)
    candidate_set_id = route_generation.get("candidateSetId")
    routes = route_generation.get("routes") or []
    route_codes = [route.get("routeCode") for route in routes if isinstance(route, dict) and route.get("routeCode")]
    if not candidate_set_id or not route_codes:
        _print_stderr(f"[{index}/{total_jobs}] 跳过：路线生成响应不可评价")
        _print_stderr(
            f"    candidateSetIdPresent={bool(candidate_set_id)} "
            f"routesCount={len(routes) if isinstance(routes, list) else 'not-list'} "
            f"routeCodes={route_codes}"
        )
        for line in _route_warning_lines(route_generation):
            _print_stderr(line)
        return RouteJobResult(RunStats(route_requests=1), [])

    candidate_sets += 1
    if len(route_codes) < 2:
        _print_stderr(
            f"[{index}/{total_jobs}] 跳过 LLM：候选路线少于 2 条，"
            f"candidateSetId={candidate_set_id} routeCodes={route_codes}"
        )
        return RouteJobResult(RunStats(route_requests=1, candidate_sets=candidate_sets), [])

    user_prompt = build_user_prompt(route_request, route_generation, persona)
    _print_stderr(
        f"[{index}/{total_jobs}] candidateSetId={candidate_set_id} "
        f"routes={route_codes} judges={len(llm_attempt_groups)}"
    )
    _print_stderr(f"[{index}/{total_jobs}] LLM prompt 构建完毕，提交异步模拟用户评价任务...")
    return RouteJobResult(
        RunStats(route_requests=1, candidate_sets=candidate_sets),
        [
            JudgmentTask(
                index=index,
                total_jobs=total_jobs,
                candidate_set_id=candidate_set_id,
                route_codes=route_codes,
                user_prompt=user_prompt,
                llm_attempt_groups=llm_attempt_groups,
            )
        ],
    )


def _run_judgment_task(
    config: AppConfig,
    backend: BackendClient,
    dry_run: bool,
    task: JudgmentTask,
) -> JobResult:
    stdout_payloads = []
    saved = 0
    failed = 0
    index = task.index
    total_jobs = task.total_jobs
    _print_stderr(f"[{index}/{total_jobs}] 异步 LLM 评价开始，judges={len(task.llm_attempt_groups)}")

    for llm_candidates in task.llm_attempt_groups:
        primary_llm = llm_candidates[0]
        judgment = None
        judgment_model_id = primary_llm.judge_model
        if dry_run:
            judgment = fake_judgment(task.route_codes)
            _print_stderr(f"[{index}/{total_jobs}] dry-run judgment 已生成")
        else:
            for attempt_index, llm in enumerate(llm_candidates, start=1):
                try:
                    llm_started_at = monotonic()
                    _print_stderr(
                        f"[{index}/{total_jobs}] 调用 LLM 获取评价: {llm.judge_model} "
                        f"apiAttempt={attempt_index}/{len(llm_candidates)}"
                    )
                    judgment, response_model = call_once(llm, config, task.user_prompt, task.route_codes)
                    judgment_model_id = response_model or llm.judge_model
                    _print_stderr(
                        f"[{index}/{total_jobs}] LLM 评价返回且校验通过: {llm.judge_model} "
                        f"responseModel={judgment_model_id} "
                        f"用时={monotonic() - llm_started_at:.1f}s"
                    )
                    if attempt_index > 1:
                        _print_stderr(
                            f"[{index}/{total_jobs}] fallback judgment succeeded: {llm.judge_model} "
                            f"apiAttempt={attempt_index}/{len(llm_candidates)}"
                        )
                    break
                except Exception as exception:
                    _print_stderr(
                        f"[{index}/{total_jobs}] judgment attempt failed: {llm.judge_model} "
                        f"apiAttempt={attempt_index}/{len(llm_candidates)} error={exception}"
                    )

        if judgment is None:
            failed += 1
            _print_stderr(
                f"[{index}/{total_jobs}] failed judgment: {primary_llm.judge_model}: "
                f"fallback exhausted after {len(llm_candidates)} api attempts"
            )
            continue

        try:
            personal_review = judgment.get(PERSONAL_REVIEW_FIELD)
            if personal_review:
                _print_stderr(f"[{index}/{total_jobs}] personalReview: {personal_review}")
            judgment_payload = judgment_payload_for_save(judgment)
            payload = {
                "candidateSetId": task.candidate_set_id,
                "judgeType": "LLM_SIM_USER",
                "judgeModel": judgment_model_id,
                "judgePromptVersion": config.judge.prompt_version,
                **judgment_payload,
            }
            if dry_run:
                dry_run_payload = dict(payload)
                if personal_review:
                    dry_run_payload[PERSONAL_REVIEW_FIELD] = personal_review
                stdout_payloads.append(json.dumps(dry_run_payload, ensure_ascii=False, indent=2))
            else:
                _print_stderr(f"[{index}/{total_jobs}] 保存 judgment: {judgment_model_id}")
                save_started_at = monotonic()
                backend.save_judgment(payload)
                _print_stderr(
                    f"[{index}/{total_jobs}] judgment 保存接口返回: {judgment_model_id} "
                    f"用时={monotonic() - save_started_at:.1f}s"
                )
            saved += 1
            _print_stderr(f"[{index}/{total_jobs}] saved judgment: {judgment_model_id}")
        except Exception as exception:
            failed += 1
            _print_stderr(f"[{index}/{total_jobs}] failed judgment: {judgment_model_id}: {exception}")

    return JobResult(
        RunStats(
            route_requests=0,
            candidate_sets=0,
            judgments_saved=saved,
            judgments_failed=failed,
        ),
        stdout_payloads,
    )


def _print_job_result(result: JobResult) -> None:
    for payload in result.stdout_payloads:
        print(payload)


def _print_stderr(line: str) -> None:
    with PRINT_LOCK:
        print(line, file=sys.stderr, flush=True)


def _route_warning_lines(route_generation: dict) -> list[str]:
    lines = []
    status = route_generation.get("status")
    warnings = route_generation.get("warnings") or []
    if status:
        lines.append(f"    status={status}")
    for warning in warnings[:5]:
        lines.append(f"    warning={warning}")
    if len(warnings) > 5:
        lines.append(f"    warning=... 还有 {len(warnings) - 5} 条")
    return lines


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


def select_llms(config: AppConfig, rng: random.Random, llm_order: list, llm_cursor: int):
    if not llm_order:
        return [], llm_cursor

    # fullJudgeRatio 控制进入多评判的 candidate set 比例；命中后按 judgesPerCandidateSet
    # 发起多次评价。New API 单入口时会重复选择同一个 LLM 配置，从而真实调用多次 New API。
    count = config.judge.judges_per_candidate_set if rng.random() < config.judge.full_judge_ratio else 1
    selected = [llm_order[(llm_cursor + offset) % len(llm_order)] for offset in range(count)]
    return selected, llm_cursor + count


def llm_attempt_candidates(config: AppConfig, primary_llm, rng: random.Random):
    candidates = [primary_llm]
    remaining = [llm for llm in config.llm_pool if llm != primary_llm]
    fallback_count = min(MAX_LLM_FALLBACK_ATTEMPTS, len(remaining))
    if fallback_count > 0:
        candidates.extend(rng.sample(remaining, fallback_count))
    return candidates


def call_once(llm, config: AppConfig, user_prompt: str, route_codes: list[str]) -> tuple[dict, str | None]:
    client = LlmClient(llm, config.judge)
    result = client.judge(user_prompt)
    try:
        return validate_judgment(result.judgment, route_codes), result.response_model
    except Exception as validation_exception:
        raise ValueError(
            f"raw judgment invalid: {validation_exception}\n{_debug_json(result.judgment)}"
        ) from validation_exception


def judgment_payload_for_save(judgment: dict) -> dict:
    # personalReview 是模拟用户的自然语言点评，不写入 Java judgment 接口和训练标签。
    return {
        key: value
        for key, value in judgment.items()
        if key != PERSONAL_REVIEW_FIELD
    }


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
            "personalReview": "我会先选前面的路线；最后一条只是 dry-run 示例，假设走起来更累，所以我会往后排。",
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
