from __future__ import annotations

from datetime import datetime, timezone
from io import BytesIO
import json
import os
from typing import Any

import pyarrow as pa
import pyarrow.parquet as pq

from .dataset_manifest import DatasetFiles, DatasetManifest
from .ingest_repository import IngestCandidateSet, IngestJudgment, RoutePreferenceIngestRepository
from .object_storage import (
    ObjectStorageClient,
    ObjectStorageConfig,
    json_bytes,
    load_object_storage_config,
)


def build_dataset_from_ingest(
    config: ObjectStorageConfig,
    dataset_version: str,
    base_dataset_version: str | None = None,
    delete_processed_ingest: bool = True,
) -> DatasetManifest:
    client = ObjectStorageClient(config)
    repository = RoutePreferenceIngestRepository(client, config)
    base_sample_rows, base_judgment_rows, base_raw_sources = _read_base_dataset(
        client,
        config,
        base_dataset_version,
    )
    base_candidate_set_ids = {str(row["candidate_set_id"]) for row in base_sample_rows}

    ready_items = repository.fetch_ready_candidate_sets()
    processed = [item for item in ready_items if item.judgments]
    processed_candidate_set_ids = {item.candidate_set_id for item in processed}
    if base_candidate_set_ids.intersection(processed_candidate_set_ids):
        raise ValueError(
            "candidate-set ingest 与 base dataset 重复: "
            f"{sorted(base_candidate_set_ids.intersection(processed_candidate_set_ids))}"
        )

    judgments_by_candidate_set = repository.fetch_judgments_by_candidate_set()
    judgment_only_items = _judgment_only_items(
        judgments_by_candidate_set,
        base_candidate_set_ids,
        processed_candidate_set_ids,
    )
    missing_candidate_sets = [
        candidate_set_id
        for candidate_set_id in judgments_by_candidate_set
        if candidate_set_id not in base_candidate_set_ids
        and candidate_set_id not in processed_candidate_set_ids
    ]
    if missing_candidate_sets:
        raise ValueError(
            "存在没有 candidate-set ingest 且不在 base dataset 中的 judgment: "
            f"{sorted(missing_candidate_sets)}"
        )
    if not processed and not judgment_only_items:
        raise ValueError("没有可处理的 candidate set 或 judgment ingest 数据")

    dataset_prefix = f"datasets/{dataset_version}"
    sample_rows = [*base_sample_rows, *_sample_rows(processed)]
    judgment_rows = [
        *base_judgment_rows,
        *_judgment_rows(processed),
        *_judgment_rows_from_payloads([item.payload for item in judgment_only_items]),
    ]
    raw_index_rows = [
        *_copy_base_raw_snapshots(client, config, dataset_prefix, base_raw_sources),
        *_copy_ingest_raw_snapshots(client, config, dataset_prefix, processed),
    ]

    feature_versions = {str(row["feature_schema_version"]) for row in sample_rows}
    if len(feature_versions) != 1:
        raise ValueError(f"feature_schema_version 不唯一: {sorted(feature_versions)}")
    raw_versions = {str(row["raw_schema_version"]) for row in raw_index_rows}
    if len(raw_versions) != 1:
        raise ValueError(f"raw_schema_version 不唯一: {sorted(raw_versions)}")

    _write_parquet(client, config.key(f"{dataset_prefix}/training_samples.parquet"), sample_rows)
    _write_parquet(client, config.key(f"{dataset_prefix}/judgments.parquet"), judgment_rows)
    _write_parquet(client, config.key(f"{dataset_prefix}/raw_snapshot_index.parquet"), raw_index_rows)

    manifest = DatasetManifest(
        dataset_version=dataset_version,
        candidate_set_count=len({str(row["candidate_set_id"]) for row in sample_rows}),
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
    if delete_processed_ingest:
        repository.delete_processed(processed)
        repository.delete_judgments(judgment_only_items)
    return manifest


def _read_base_dataset(
    client: ObjectStorageClient,
    config: ObjectStorageConfig,
    base_dataset_version: str | None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    if not base_dataset_version:
        return [], [], []
    manifest_key = config.key(f"datasets/{base_dataset_version}/manifest.json")
    manifest = DatasetManifest.from_json(
        json.loads(client.read_bytes(manifest_key).decode("utf-8"))
    )
    dataset_prefix = f"datasets/{base_dataset_version}"
    return (
        _read_parquet_rows(client, config.key(f"{dataset_prefix}/{manifest.files.training_samples}")),
        _read_parquet_rows(client, config.key(f"{dataset_prefix}/{manifest.files.judgments}")),
        _read_parquet_rows(client, config.key(f"{dataset_prefix}/{manifest.files.raw_snapshot_index}")),
    )


def _judgment_only_items(
    judgments_by_candidate_set: dict[str, tuple[IngestJudgment, ...]],
    base_candidate_set_ids: set[str],
    processed_candidate_set_ids: set[str],
) -> list[IngestJudgment]:
    items: list[IngestJudgment] = []
    for candidate_set_id, judgments in judgments_by_candidate_set.items():
        if candidate_set_id in processed_candidate_set_ids:
            continue
        if candidate_set_id in base_candidate_set_ids:
            items.extend(judgments)
    return items


def _sample_rows(items: list[IngestCandidateSet]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for item in items:
        for sample in item.payload.get("trainingSamples", []):
            rows.append(
                {
                    "candidate_set_id": str(sample["candidateSetId"]),
                    "route_code": str(sample["routeCode"]),
                    "feature_schema_version": str(sample["featureSchemaVersion"]),
                    "stop_matrix_json": str(sample["stopMatrixJson"]),
                    "segment_matrix_json": str(sample["segmentMatrixJson"]),
                    "route_derived_vector_json": str(sample["routeDerivedVectorJson"]),
                    "context_cross_vector_json": str(sample.get("contextCrossVectorJson") or "{}"),
                    "intra_set_vector_json": str(sample.get("intraSetVectorJson") or "{}"),
                }
            )
    return rows


def _judgment_rows(items: list[IngestCandidateSet]) -> list[dict[str, Any]]:
    return _judgment_rows_from_payloads([judgment for item in items for judgment in item.judgments])


def _judgment_rows_from_payloads(judgments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for judgment in judgments:
        rows.append(
            {
                "judgment_id": str(judgment["judgmentId"]),
                "candidate_set_id": str(judgment["candidateSetId"]),
                "ranking_json": json.dumps(judgment.get("rankingJson") or [], ensure_ascii=False),
                "accepted_route_codes_json": json.dumps(
                    judgment.get("acceptedRouteCodesJson") or [], ensure_ascii=False
                ),
                "rejected_route_codes_json": json.dumps(
                    judgment.get("rejectedRouteCodesJson") or [], ensure_ascii=False
                ),
                "reason_codes_json": json.dumps(judgment.get("reasonCodesJson") or {}, ensure_ascii=False),
                "confidence": judgment.get("confidence"),
                "judge_type": str(judgment["judgeType"]),
                "judge_model": str(judgment.get("judgeModel") or ""),
                "judge_prompt_version": str(judgment.get("judgePromptVersion") or ""),
                "status": str(judgment.get("status") or "COMPLETED"),
                "completed_at": str(judgment.get("completedAt") or ""),
            }
        )
    return rows


def _copy_base_raw_snapshots(
    client: ObjectStorageClient,
    config: ObjectStorageConfig,
    dataset_prefix: str,
    rows: list[dict[str, Any]],
) -> list[dict[str, str]]:
    copied: list[dict[str, str]] = []
    for row in rows:
        candidate_set_id = str(row["candidate_set_id"])
        shard = candidate_set_id.replace("-", "")[:2].lower()
        key = config.key(f"{dataset_prefix}/raw_snapshots/shard={shard}/{candidate_set_id}.json.gz")
        client.write_bytes(key, client.read_bytes(str(row["object_key"])), "application/gzip")
        copied.append(
            {
                "candidate_set_id": candidate_set_id,
                "raw_schema_version": str(row["raw_schema_version"]),
                "object_key": key,
            }
        )
    return copied


def _copy_ingest_raw_snapshots(
    client: ObjectStorageClient,
    config: ObjectStorageConfig,
    dataset_prefix: str,
    items: list[IngestCandidateSet],
) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for item in items:
        candidate_set_id = item.candidate_set_id
        shard = candidate_set_id.replace("-", "")[:2].lower()
        key = config.key(f"{dataset_prefix}/raw_snapshots/shard={shard}/{candidate_set_id}.json.gz")
        client.write_bytes(key, json_bytes(item.payload["rawSnapshot"], gzipped=True), "application/gzip")
        rows.append(
            {
                "candidate_set_id": candidate_set_id,
                "raw_schema_version": str(item.payload["rawSnapshot"]["rawSchemaVersion"]),
                "object_key": key,
            }
        )
    return rows


def _write_parquet(client: ObjectStorageClient, key: str, rows: list[dict[str, Any]]) -> None:
    if not rows:
        raise ValueError(f"{key} 没有可写入数据")
    output = BytesIO()
    pq.write_table(pa.Table.from_pylist(rows), output)
    client.write_bytes(key, output.getvalue(), "application/vnd.apache.parquet")


def _read_parquet_rows(client: ObjectStorageClient, key: str) -> list[dict[str, Any]]:
    data = client.read_bytes(key)
    return pq.read_table(BytesIO(data)).to_pylist()


def main() -> int:
    config = load_object_storage_config()
    if not config.dataset_version:
        raise ValueError("ROUTE_PREF_DATASET_VERSION 未配置")
    base_dataset_version = os.environ.get("ROUTE_PREF_BASE_DATASET_VERSION")
    manifest = build_dataset_from_ingest(config, config.dataset_version, base_dataset_version)
    print(
        "datasetVersion={dataset} baseDatasetVersion={base_dataset} candidateSetCount={candidate_sets} "
        "sampleCount={samples} judgmentCount={judgments}".format(
            dataset=manifest.dataset_version,
            base_dataset=base_dataset_version or "",
            candidate_sets=manifest.candidate_set_count,
            samples=manifest.sample_count,
            judgments=manifest.judgment_count,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
