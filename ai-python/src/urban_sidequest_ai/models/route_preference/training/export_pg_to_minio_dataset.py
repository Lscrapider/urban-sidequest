from __future__ import annotations

from datetime import datetime, timezone
from io import BytesIO
import json
from typing import Any

import pyarrow as pa
import pyarrow.parquet as pq

from .dataset_manifest import DatasetFiles, DatasetManifest
from .db import connect, load_database_config
from .object_storage import ObjectStorageClient, json_bytes, load_object_storage_config


def export_pg_to_minio_dataset(dataset_version: str | None = None) -> DatasetManifest:
    config = load_object_storage_config(dataset_version)
    if not config.dataset_version:
        raise ValueError("ROUTE_PREF_DATASET_VERSION 未配置")
    client = ObjectStorageClient(config)
    with connect(load_database_config()) as connection:
        raw_rows = _fetch_raw_snapshots(connection)
        sample_rows = _fetch_training_samples(connection)
        judgment_rows = _fetch_judgments(connection)

    if not raw_rows:
        raise ValueError("route_preference_raw_snapshots 为空")
    if not sample_rows:
        raise ValueError("route_preference_training_samples 为空")
    if not judgment_rows:
        raise ValueError("route_preference_judgments 为空")

    dataset_prefix = f"datasets/{config.dataset_version}"
    raw_index_rows = _copy_raw_snapshots(client, config, dataset_prefix, raw_rows)
    _write_parquet(client, config.key(f"{dataset_prefix}/training_samples.parquet"), sample_rows)
    _write_parquet(client, config.key(f"{dataset_prefix}/judgments.parquet"), judgment_rows)
    _write_parquet(client, config.key(f"{dataset_prefix}/raw_snapshot_index.parquet"), raw_index_rows)

    feature_versions = {str(row["feature_schema_version"]) for row in sample_rows}
    raw_versions = {str(row["raw_schema_version"]) for row in raw_rows}
    if len(feature_versions) != 1:
        raise ValueError(f"feature_schema_version 不唯一: {sorted(feature_versions)}")
    if len(raw_versions) != 1:
        raise ValueError(f"raw_schema_version 不唯一: {sorted(raw_versions)}")

    manifest = DatasetManifest(
        dataset_version=config.dataset_version,
        candidate_set_count=len({row["candidate_set_id"] for row in sample_rows}),
        feature_schema_version=next(iter(feature_versions)),
        raw_schema_version=next(iter(raw_versions)),
        sample_count=len(sample_rows),
        judgment_count=len(judgment_rows),
        created_at=datetime.now(timezone.utc).isoformat(),
        files=DatasetFiles(
            training_samples="training_samples.parquet",
            judgments="judgments.parquet",
            raw_snapshot_index="raw_snapshot_index.parquet",
            raw_snapshots_prefix="raw_snapshots/",
        ),
    )
    client.write_bytes(
        config.key(f"{dataset_prefix}/manifest.json"),
        json_bytes(manifest.to_json(), gzipped=False),
        "application/json",
    )
    return manifest


def _fetch_training_samples(connection) -> list[dict[str, Any]]:
    sql = """
        SELECT
            candidate_set_id::text,
            route_code,
            feature_schema_version,
            stop_matrix_json::text,
            segment_matrix_json::text,
            route_derived_vector_json::text,
            context_cross_vector_json::text,
            intra_set_vector_json::text
        FROM route_preference_training_samples
        WHERE sample_status = 'TRAIN_READY'
        ORDER BY candidate_set_id, route_code
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        rows = cursor.fetchall()
    return [
        {
            "candidate_set_id": str(row[0]),
            "route_code": str(row[1]),
            "feature_schema_version": str(row[2]),
            "stop_matrix_json": row[3],
            "segment_matrix_json": row[4],
            "route_derived_vector_json": row[5],
            "context_cross_vector_json": row[6] or "{}",
            "intra_set_vector_json": row[7] or "{}",
        }
        for row in rows
    ]


def _fetch_judgments(connection) -> list[dict[str, Any]]:
    sql = """
        SELECT
            id::text,
            candidate_set_id::text,
            ranking_json::text,
            accepted_route_codes_json::text,
            rejected_route_codes_json::text,
            reason_codes_json::text,
            confidence,
            judge_type,
            judge_model,
            judge_prompt_version,
            status,
            completed_at::text
        FROM route_preference_judgments
        WHERE status = 'COMPLETED'
        ORDER BY candidate_set_id, completed_at, id
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        rows = cursor.fetchall()
    return [
        {
            "judgment_id": str(row[0]),
            "candidate_set_id": str(row[1]),
            "ranking_json": row[2],
            "accepted_route_codes_json": row[3],
            "rejected_route_codes_json": row[4],
            "reason_codes_json": row[5],
            "confidence": float(row[6] or 0.0),
            "judge_type": str(row[7]),
            "judge_model": str(row[8] or ""),
            "judge_prompt_version": str(row[9] or ""),
            "status": str(row[10]),
            "completed_at": str(row[11] or ""),
        }
        for row in rows
    ]


def _fetch_raw_snapshots(connection) -> list[dict[str, Any]]:
    sql = """
        SELECT
            candidate_set_id::text,
            request_id::text,
            user_id::text,
            raw_schema_version,
            generate_param_json::text,
            area_json::text,
            weather_json::text,
            user_preference_profile_json::text,
            interest_tag_catalog_json::text,
            interest_tags_json::text,
            poi_semantic_mappings_json::text,
            poi_candidates_json::text,
            poi_linear_traces_json::text,
            selected_routes_json::text,
            segment_costs_json::text,
            warnings_json::text
        FROM route_preference_raw_snapshots
        ORDER BY candidate_set_id
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        rows = cursor.fetchall()
    fields = (
        "candidate_set_id",
        "request_id",
        "user_id",
        "raw_schema_version",
        "generate_param_json",
        "area_json",
        "weather_json",
        "user_preference_profile_json",
        "interest_tag_catalog_json",
        "interest_tags_json",
        "poi_semantic_mappings_json",
        "poi_candidates_json",
        "poi_linear_traces_json",
        "selected_routes_json",
        "segment_costs_json",
        "warnings_json",
    )
    return [dict(zip(fields, row, strict=True)) for row in rows]


def _copy_raw_snapshots(
    client: ObjectStorageClient,
    config,
    dataset_prefix: str,
    rows: list[dict[str, Any]],
) -> list[dict[str, str]]:
    index_rows: list[dict[str, str]] = []
    for row in rows:
        candidate_set_id = str(row["candidate_set_id"])
        shard = candidate_set_id.replace("-", "")[:2].lower()
        key = config.key(f"{dataset_prefix}/raw_snapshots/shard={shard}/{candidate_set_id}.json.gz")
        payload = {
            "candidateSetId": candidate_set_id,
            "requestId": row["request_id"],
            "userId": row["user_id"],
            "rawSchemaVersion": row["raw_schema_version"],
            "generateParam": _json(row["generate_param_json"]),
            "area": _json(row["area_json"]),
            "weather": _json(row["weather_json"]),
            "userPreferenceProfile": _json(row["user_preference_profile_json"]),
            "interestTagCatalog": _json(row["interest_tag_catalog_json"]),
            "interestTags": _json(row["interest_tags_json"]),
            "poiSemanticMappings": _json(row["poi_semantic_mappings_json"]),
            "poiCandidates": _json(row["poi_candidates_json"]),
            "poiLinearTraces": _json(row["poi_linear_traces_json"]),
            "selectedRoutes": _json(row["selected_routes_json"]),
            "segmentCosts": _json(row["segment_costs_json"]),
            "warnings": _json(row["warnings_json"]),
        }
        client.write_bytes(key, json_bytes(payload, gzipped=True), "application/gzip")
        index_rows.append(
            {
                "candidate_set_id": candidate_set_id,
                "raw_schema_version": str(row["raw_schema_version"]),
                "object_key": key,
            }
        )
    return index_rows


def _write_parquet(client: ObjectStorageClient, key: str, rows: list[dict[str, Any]]) -> None:
    output = BytesIO()
    pq.write_table(pa.Table.from_pylist(rows), output)
    client.write_bytes(key, output.getvalue(), "application/vnd.apache.parquet")


def _json(value: str | None) -> Any:
    if value is None:
        return None
    return json.loads(value)


def main() -> int:
    manifest = export_pg_to_minio_dataset()
    print(
        "candidateSetCount={candidate_sets} sampleCount={samples} judgmentCount={judgments} "
        "featureSchemaVersion={feature_schema} rawSchemaVersion={raw_schema}".format(
            candidate_sets=manifest.candidate_set_count,
            samples=manifest.sample_count,
            judgments=manifest.judgment_count,
            feature_schema=manifest.feature_schema_version,
            raw_schema=manifest.raw_schema_version,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
