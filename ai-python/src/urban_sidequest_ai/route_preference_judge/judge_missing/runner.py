from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
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
    _run_judgment_task,
    llm_attempt_candidates,
)

from .repository import RawSnapshotJudgeJob, fetch_missing_raw_snapshot_jobs


@dataclass(frozen=True)
class MissingJudgmentStats:
    raw_snapshots: int = 0
    candidate_sets: int = 0
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
) -> MissingJudgmentStats:
    if judge_concurrency < 1:
        raise ValueError("judge_concurrency 必须 >= 1")
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
        )

    _print_stderr(f"待补评价 raw snapshot 数量：{len(jobs)}")
    if not jobs:
        return MissingJudgmentStats()

    rng = random.Random(app_config.judge.seed)
    llm_order = list(app_config.llm_pool)
    rng.shuffle(llm_order)
    llm_cursor = 0
    tasks: list[JudgmentTask] = []
    for index, job in enumerate(jobs, start=1):
        selected_llms, llm_cursor = _select_single_llm(llm_order, llm_cursor)
        llm_attempt_groups = [llm_attempt_candidates(app_config, llm, rng) for llm in selected_llms]
        task = build_judgment_task_from_snapshot(
            job=job,
            index=index,
            total_jobs=len(jobs),
            llm_attempt_groups=llm_attempt_groups,
            temperature=app_config.judge.temperature,
        )
        if task is None:
            continue
        tasks.append(task)

    judgment_results: list[JobResult] = []
    with ThreadPoolExecutor(max_workers=judge_concurrency) as judge_executor:
        futures = [
            judge_executor.submit(_run_judgment_task, app_config, backend, dry_run, task)
            for task in tasks
        ]
        for future in as_completed(futures):
            result = future.result()
            _print_job_result(result)
            judgment_results.append(result)

    return MissingJudgmentStats(
        raw_snapshots=len(jobs),
        candidate_sets=len(tasks),
        judgments_saved=sum(result.stats.judgments_saved for result in judgment_results),
        judgments_failed=sum(result.stats.judgments_failed for result in judgment_results),
        skipped=len(jobs) - len(tasks),
    )


def build_judgment_task_from_snapshot(
    job: RawSnapshotJudgeJob,
    index: int,
    total_jobs: int,
    llm_attempt_groups: list,
    temperature: float,
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
    user_prompt = build_user_prompt(job.route_request, route_generation, job.persona)
    _print_stderr(
        f"[{index}/{total_jobs}] 复用 raw snapshot 构建 LLM prompt，"
        f"candidateSetId={job.candidate_set_id} routes={route_codes} judges={len(llm_attempt_groups)}"
    )
    return JudgmentTask(
        index=index,
        total_jobs=total_jobs,
        candidate_set_id=job.candidate_set_id,
        route_codes=route_codes,
        llm_attempt_groups=llm_attempt_groups,
        user_prompts=[user_prompt],
        temperatures=[temperature],
    )


def _select_single_llm(llm_order: list, llm_cursor: int):
    return [llm_order[llm_cursor % len(llm_order)]], llm_cursor + 1
