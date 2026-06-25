from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any


@dataclass(frozen=True)
class RawSnapshotJudgeJob:
    candidate_set_id: str
    route_request: dict
    persona: dict
    selected_routes: list[dict]
    warnings: list[str]


def fetch_missing_raw_snapshot_jobs(
    connection,
    limit: int | None = None,
    candidate_set_ids: list[str] | None = None,
) -> list[RawSnapshotJudgeJob]:
    if limit is not None and limit < 1:
        raise ValueError("limit 必须 >= 1")

    ids = [item for item in (candidate_set_ids or []) if item]
    params: list[Any] = [len(ids), ids]
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
            rprs.warnings_json
        FROM route_preference_raw_snapshots rprs
        WHERE jsonb_typeof(rprs.selected_routes_json) = 'array'
          AND jsonb_array_length(rprs.selected_routes_json) >= 2
          AND (%s::int = 0 OR rprs.candidate_set_id::text = ANY(%s::text[]))
          AND EXISTS (
              SELECT 1
              FROM route_preference_training_samples rpts
              WHERE rpts.candidate_set_id = rprs.candidate_set_id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM route_preference_judgments rpj
              WHERE rpj.candidate_set_id = rprs.candidate_set_id
                AND rpj.status = 'COMPLETED'
          )
        ORDER BY rprs.created_at, rprs.candidate_set_id
        {limit_clause}
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        rows = cursor.fetchall()

    return [
        RawSnapshotJudgeJob(
            candidate_set_id=str(row[0]),
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
