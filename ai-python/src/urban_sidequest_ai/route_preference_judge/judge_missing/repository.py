from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
import json
from typing import Any

import pyarrow.parquet as pq

from urban_sidequest_ai.models.route_preference.training.dataset_manifest import DatasetManifest
from urban_sidequest_ai.models.route_preference.training.object_storage import (
    ObjectStorageClient,
    ObjectStorageConfig,
    read_json_bytes,
)


@dataclass(frozen=True)
class RawSnapshotJudgeJob:
    candidate_set_id: str
    judgment_count: int
    route_request: dict
    persona: dict
    selected_routes: list[dict]
    warnings: list[str]


def fetch_missing_raw_snapshot_jobs(
    client: ObjectStorageClient,
    config: ObjectStorageConfig,
    limit: int | None = None,
    candidate_set_ids: list[str] | None = None,
    target_k: int = 3,
    original_k: int | None = None,
) -> list[RawSnapshotJudgeJob]:
    if not config.dataset_version:
        raise ValueError("ROUTE_PREF_DATASET_VERSION 未配置")
    if limit is not None and limit < 1:
        raise ValueError("limit 必须 >= 1")
    if target_k < 1:
        raise ValueError("target_k 必须 >= 1")
    if original_k is not None and original_k < 0:
        raise ValueError("original_k 必须 >= 0")
    if original_k is not None and original_k >= target_k:
        raise ValueError("original_k 必须小于 target_k")

    requested_ids = {item for item in (candidate_set_ids or []) if item}
    manifest = _load_manifest(client, config)
    judgment_counts = _judgment_counts(client, config, manifest)
    raw_index_rows = _read_parquet_rows(client, config.key(
        f"datasets/{config.dataset_version}/{manifest.files.raw_snapshot_index}"
    ))
    jobs: list[RawSnapshotJudgeJob] = []
    for row in raw_index_rows:
        candidate_set_id = str(row["candidate_set_id"])
        if requested_ids and candidate_set_id not in requested_ids:
            continue
        judgment_count = judgment_counts.get(candidate_set_id, 0)
        if original_k is not None and judgment_count != original_k:
            continue
        if judgment_count >= target_k:
            continue
        raw_snapshot = read_json_bytes(client.read_bytes(str(row["object_key"])), gzipped=True)
        selected_routes = _list_json(raw_snapshot.get("selectedRoutes"), "selectedRoutes")
        if len(selected_routes) < 2:
            continue
        jobs.append(
            RawSnapshotJudgeJob(
                candidate_set_id=candidate_set_id,
                judgment_count=judgment_count,
                route_request=_dict_json(raw_snapshot.get("generateParam"), "generateParam"),
                persona=_dict_json(raw_snapshot.get("userPreferenceProfile"), "userPreferenceProfile"),
                selected_routes=selected_routes,
                warnings=_string_list_json(raw_snapshot.get("warnings"), "warnings"),
            )
        )
        if limit is not None and len(jobs) >= limit:
            break
    return jobs


def _load_manifest(client: ObjectStorageClient, config: ObjectStorageConfig) -> DatasetManifest:
    key = config.key(f"datasets/{config.dataset_version}/manifest.json")
    return DatasetManifest.from_json(read_json_bytes(client.read_bytes(key), gzipped=False))


def _judgment_counts(
    client: ObjectStorageClient,
    config: ObjectStorageConfig,
    manifest: DatasetManifest,
) -> dict[str, int]:
    rows = _read_parquet_rows(client, config.key(f"datasets/{config.dataset_version}/{manifest.files.judgments}"))
    counts: dict[str, int] = {}
    for row in rows:
        if str(row.get("status") or "COMPLETED") != "COMPLETED":
            continue
        candidate_set_id = str(row["candidate_set_id"])
        counts[candidate_set_id] = counts.get(candidate_set_id, 0) + 1
    return counts


def _read_parquet_rows(client: ObjectStorageClient, key: str) -> list[dict[str, Any]]:
    return pq.read_table(BytesIO(client.read_bytes(key))).to_pylist()


def _dict_json(value: Any, field_name: str) -> dict:
    decoded = _json_value(value)
    if not isinstance(decoded, dict):
        raise ValueError(f"{field_name} 必须是对象")
    return decoded


def _list_json(value: Any, field_name: str) -> list[dict]:
    decoded = _json_value(value)
    if not isinstance(decoded, list):
        raise ValueError(f"{field_name} 必须是数组")
    invalid = [item for item in decoded if not isinstance(item, dict)]
    if invalid:
        raise ValueError(f"{field_name} 必须是对象数组")
    return decoded


def _string_list_json(value: Any, field_name: str) -> list[str]:
    decoded = _json_value(value)
    if decoded is None:
        return []
    if not isinstance(decoded, list):
        raise ValueError(f"{field_name} 必须是数组")
    return [str(item) for item in decoded]


def _json_value(value: Any) -> Any:
    if isinstance(value, str):
        return json.loads(value)
    return value
