from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from dataclasses import dataclass
import random

from urban_sidequest_ai.models.route_preference.training.db import DatabaseConfig, connect
from urban_sidequest_ai.route_preference_judge.config import AppConfig
from urban_sidequest_ai.route_preference_judge.java_client import BackendClient
from urban_sidequest_ai.route_preference_judge.prompt import build_user_prompt
from urban_sidequest_ai.route_preference_judge.runner import (
    JobResult,
    JudgmentTask,
    _print_job_result,
    _print_stderr,
    _submit_judgment_task,
    judge_temperatures,
    llm_attempt_candidates,
    select_llms,
    shuffled_routes,
)

from .repository import RawSnapshotJudgeJob, fetch_missing_raw_snapshot_jobs


@dataclass(frozen=True)
class MissingJudgmentStats:
    raw_snapshots: int = 0
    candidate_sets: int = 0
    judgments_planned: int = 0
    judgments_saved: int = 0
    judgments_failed: int = 0
    skipped: int = 0


def run_missing_judgments(
    app_config: AppConfig,
    database_config: DatabaseConfig,
    limit: int | None = None,
    judge_concurrency: int = 1,
    dry_run: bool = False,
    candidate_set_ids: list[str] | None = None,
    target_k: int = 3,
    original_k: int | None = None,
) -> MissingJudgmentStats:
    if judge_concurrency < 1:
        raise ValueError("judge_concurrency 必须 >= 1")
    if target_k < 1:
        raise ValueError("target_k 必须 >= 1")
    if original_k is not None and original_k < 0:
        raise ValueError("original_k 必须 >= 0")
    if original_k is not None and original_k >= target_k:
        raise ValueError("original_k 必须小于 target_k")
    if not app_config.llm_pool:
        raise ValueError("缺少可用 LLM 配置")

    backend = BackendClient(app_config.backend)
    if not dry_run:
        backend.ensure_token()

    with connect(database_config) as connection:
        jobs = fetch_missing_raw_snapshot_jobs(
            connection,
            limit=limit,
            candidate_set_ids=candidate_set_ids,
            target_k=target_k,
            original_k=original_k,
        )

    _print_stderr(
        f"待补评价 raw snapshot 数量：{len(jobs)} targetK={target_k} "
        f"originalK={original_k if original_k is not None else 'ANY'}"
    )
    if not jobs:
        return MissingJudgmentStats()

    rng = random.Random(app_config.judge.seed)
    llm_order = list(app_config.llm_pool)
    rng.shuffle(llm_order)
    llm_cursor = 0
    tasks: list[JudgmentTask] = []
    for index, job in enumerate(jobs, start=1):
        missing_judge_count = target_k - job.judgment_count
        if missing_judge_count <= 0:
            _print_stderr(
                f"[{index}/{len(jobs)}] 跳过：candidateSetId={job.candidate_set_id} "
                f"currentK={job.judgment_count} targetK={target_k}"
            )
            continue
        selected_llms, llm_cursor = select_llms(app_config, llm_order, llm_cursor, missing_judge_count)
        llm_attempt_groups = [llm_attempt_candidates(app_config, llm, rng) for llm in selected_llms]
        prompt_seed = rng.randrange(1 << 63)
        task = build_judgment_task_from_snapshot(
            job=job,
            index=index,
            total_jobs=len(jobs),
            llm_attempt_groups=llm_attempt_groups,
            temperatures=topup_judge_temperatures(app_config, job.judgment_count, target_k),
            prompt_seed=prompt_seed,
        )
        if task is None:
            continue
        tasks.append(task)

    judgment_results: list[JobResult] = []
    with ThreadPoolExecutor(max_workers=judge_concurrency) as judge_executor:
        futures = []
        for task in tasks:
            futures.extend(_submit_judgment_task(judge_executor, app_config, backend, dry_run, task))
        for future in as_completed(futures):
            result = future.result()
            _print_job_result(result)
            judgment_results.append(result)

    return MissingJudgmentStats(
        raw_snapshots=len(jobs),
        candidate_sets=len(tasks),
        judgments_planned=sum(len(task.llm_attempt_groups) for task in tasks),
        judgments_saved=sum(result.stats.judgments_saved for result in judgment_results),
        judgments_failed=sum(result.stats.judgments_failed for result in judgment_results),
        skipped=len(jobs) - len(tasks),
    )


def build_judgment_task_from_snapshot(
    job: RawSnapshotJudgeJob,
    index: int,
    total_jobs: int,
    llm_attempt_groups: list,
    temperatures: list[float],
    prompt_seed: int,
) -> JudgmentTask | None:
    route_codes = [
        route.get("routeCode")
        for route in job.selected_routes
        if isinstance(route, dict) and route.get("routeCode")
    ]
    if len(route_codes) < 2:
        _print_stderr(
            f"[{index}/{total_jobs}] 跳过 LLM：raw snapshot 候选路线少于 2 条，"
            f"candidateSetId={job.candidate_set_id} routeCodes={route_codes}"
        )
        return None

    route_generation = {
        "candidateSetId": job.candidate_set_id,
        "routes": job.selected_routes,
        "warnings": job.warnings,
    }
    prompt_rng = random.Random(prompt_seed)
    user_prompts = build_topup_judge_prompts(
        job.route_request,
        route_generation,
        job.persona,
        len(llm_attempt_groups),
        prompt_rng,
    )
    _print_stderr(
        f"[{index}/{total_jobs}] 复用 raw snapshot 构建 LLM prompt，candidateSetId={job.candidate_set_id} "
        f"routes={route_codes} currentK={job.judgment_count} "
        f"plannedTopup={len(llm_attempt_groups)} temperatures={temperatures}"
    )
    return JudgmentTask(
        index=index,
        total_jobs=total_jobs,
        candidate_set_id=job.candidate_set_id,
        route_codes=route_codes,
        llm_attempt_groups=llm_attempt_groups,
        user_prompts=user_prompts,
        temperatures=temperatures,
    )


def topup_judge_temperatures(app_config: AppConfig, current_k: int, target_k: int) -> list[float]:
    return judge_temperatures(app_config, target_k)[current_k:target_k]


def build_topup_judge_prompts(
    route_request: dict,
    route_generation: dict,
    persona: dict | None,
    judge_count: int,
    rng: random.Random,
) -> list[str]:
    prompts = []
    routes = list(route_generation.get("routes") or [])
    for _ in range(judge_count):
        prompt_route_generation = deepcopy(route_generation)
        prompt_route_generation["routes"] = shuffled_routes(routes, rng)
        prompts.append(build_user_prompt(route_request, prompt_route_generation, persona))
    return prompts
