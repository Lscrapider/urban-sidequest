from __future__ import annotations

from io import BytesIO
import json
from typing import Any

import pyarrow.parquet as pq

from .dataset_manifest import DatasetManifest
from .object_storage import ObjectStorageClient, ObjectStorageConfig, read_json_bytes
from .repository import JudgmentRow, TrainingSampleRow


class RoutePreferenceDatasetRepository:
    def __init__(self, client: ObjectStorageClient, config: ObjectStorageConfig):
        if not config.dataset_version:
            raise ValueError("ROUTE_PREF_DATASET_VERSION 未配置")
        self._client = client
        self._config = config
        self._manifest = self._load_manifest()

    @property
    def manifest(self) -> DatasetManifest:
        return self._manifest

    def fetch_training_samples(self, feature_schema_version: str | None = None) -> list[TrainingSampleRow]:
        if feature_schema_version and feature_schema_version != self._manifest.feature_schema_version:
            raise ValueError(
                f"训练配置 feature_schema_version={feature_schema_version} 与数据集 "
                f"featureSchemaVersion={self._manifest.feature_schema_version} 不一致"
            )
        rows = self._read_parquet_rows(self._dataset_key(self._manifest.files.training_samples))
        return [
            TrainingSampleRow(
                candidate_set_id=str(row["candidate_set_id"]),
                route_code=str(row["route_code"]),
                feature_schema_version=str(row["feature_schema_version"]),
                stop_matrix_json=_json_field(row["stop_matrix_json"]),
                segment_matrix_json=_json_field(row["segment_matrix_json"]),
                route_derived_vector_json=_json_field(row["route_derived_vector_json"]),
                context_cross_vector_json=_json_field(row.get("context_cross_vector_json"), default={}),
                intra_set_vector_json=_json_field(row.get("intra_set_vector_json"), default={}),
            )
            for row in rows
        ]

    def fetch_samples_for_candidate_set(
        self,
        candidate_set_id: str,
        feature_schema_version: str | None = None,
    ) -> list[TrainingSampleRow]:
        return [
            row
            for row in self.fetch_training_samples(feature_schema_version)
            if row.candidate_set_id == candidate_set_id
        ]

    def fetch_completed_judgments(self, candidate_set_ids: set[str] | None = None) -> list[JudgmentRow]:
        allowed_ids = set(candidate_set_ids or [])
        rows = self._read_parquet_rows(self._dataset_key(self._manifest.files.judgments))
        results: list[JudgmentRow] = []
        for row in rows:
            candidate_set_id = str(row["candidate_set_id"])
            if allowed_ids and candidate_set_id not in allowed_ids:
                continue
            results.append(
                JudgmentRow(
                    judgment_id=str(row["judgment_id"]),
                    candidate_set_id=candidate_set_id,
                    ranking_json=_json_field(row["ranking_json"]),
                    accepted_route_codes_json=_json_field(row["accepted_route_codes_json"]),
                    rejected_route_codes_json=_json_field(row["rejected_route_codes_json"]),
                    reason_codes_json=_json_field(row["reason_codes_json"]),
                    confidence=float(row["confidence"] or 0.0),
                    judge_type=str(row["judge_type"]),
                    judge_model=str(row["judge_model"] or ""),
                    judge_prompt_version=str(row["judge_prompt_version"] or ""),
                )
            )
        return results

    def _load_manifest(self) -> DatasetManifest:
        key = self._dataset_key("manifest.json")
        return DatasetManifest.from_json(read_json_bytes(self._client.read_bytes(key), gzipped=False))

    def _dataset_key(self, filename: str) -> str:
        return self._config.key(f"datasets/{self._config.dataset_version}/{filename}")

    def _read_parquet_rows(self, key: str) -> list[dict[str, Any]]:
        data = self._client.read_bytes(key)
        return pq.read_table(BytesIO(data)).to_pylist()


def _json_field(value: Any, default: Any = None) -> Any:
    if value in (None, ""):
        return default
    if isinstance(value, str):
        decoded = json.loads(value)
        return default if decoded is None else decoded
    return value
