from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any


@dataclass(frozen=True)
class RawSnapshotJudgeJob:
    candidate_set_id: str
    judgment_count: int
    route_request: dict
    persona: dict
    selected_routes: list[dict]
    warnings: list[str]


def fetch_missing_raw_snapshot_jobs(
    connection,
    limit: int | None = None,
    candidate_set_ids: list[str] | None = None,
    target_k: int = 3,
    original_k: int | None = None,
) -> list[RawSnapshotJudgeJob]:
    if limit is not None and limit < 1:
        raise ValueError("limit 必须 >= 1")
    if target_k < 1:
        raise ValueError("target_k 必须 >= 1")
    if original_k is not None and original_k < 0:
        raise ValueError("original_k 必须 >= 0")
    if original_k is not None and original_k >= target_k:
        raise ValueError("original_k 必须小于 target_k")

    ids = [item for item in (candidate_set_ids or []) if item]
    params: list[Any] = [len(ids), ids, original_k, original_k, target_k]
    limit_clause = ""
    if limit is not None:
        limit_clause = "LIMIT %s"
        params.append(limit)

    sql = f"""
        SELECT
            rprs.candidate_set_id::text,
            rprs.generate_param_json,
            rprs.user_preference_profile_json,
            rprs.selected_routes_json,
            rprs.warnings_json,
            COALESCE(judgment_counts.completed_count, 0)::int AS judgment_count
        FROM route_preference_raw_snapshots rprs
        LEFT JOIN (
            SELECT candidate_set_id, COUNT(*) AS completed_count
            FROM route_preference_judgments
            WHERE status = 'COMPLETED'
            GROUP BY candidate_set_id
        ) judgment_counts
          ON judgment_counts.candidate_set_id = rprs.candidate_set_id
        WHERE jsonb_typeof(rprs.selected_routes_json) = 'array'
          AND jsonb_array_length(rprs.selected_routes_json) >= 2
          AND (%s::int = 0 OR rprs.candidate_set_id::text = ANY(%s::text[]))
          AND (%s::int IS NULL OR COALESCE(judgment_counts.completed_count, 0)::int = %s::int)
          AND EXISTS (
              SELECT 1
              FROM route_preference_training_samples rpts
              WHERE rpts.candidate_set_id = rprs.candidate_set_id
          )
          AND COALESCE(judgment_counts.completed_count, 0)::int < %s::int
        ORDER BY rprs.created_at, rprs.candidate_set_id
        {limit_clause}
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        rows = cursor.fetchall()

    return [
        RawSnapshotJudgeJob(
            candidate_set_id=str(row[0]),
            judgment_count=int(row[5]),
            route_request=_dict_json(row[1], "generate_param_json"),
            persona=_dict_json(row[2], "user_preference_profile_json"),
            selected_routes=_list_json(row[3], "selected_routes_json"),
            warnings=_string_list_json(row[4], "warnings_json"),
        )
        for row in rows
    ]


def _json_value(value: Any, field_name: str) -> Any:
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError as exception:
            raise ValueError(f"{field_name} 不是合法 JSON") from exception
    return value


def _dict_json(value: Any, field_name: str) -> dict:
    decoded = _json_value(value, field_name)
    if not isinstance(decoded, dict):
        raise ValueError(f"{field_name} 必须是对象")
    return decoded


def _list_json(value: Any, field_name: str) -> list[dict]:
    decoded = _json_value(value, field_name)
    if not isinstance(decoded, list):
        raise ValueError(f"{field_name} 必须是数组")
    invalid = [item for item in decoded if not isinstance(item, dict)]
    if invalid:
        raise ValueError(f"{field_name} 必须是对象数组")
    return decoded


def _string_list_json(value: Any, field_name: str) -> list[str]:
    decoded = _json_value(value, field_name)
    if decoded is None:
        return []
    if not isinstance(decoded, list):
        raise ValueError(f"{field_name} 必须是数组")
    return [str(item) for item in decoded]
