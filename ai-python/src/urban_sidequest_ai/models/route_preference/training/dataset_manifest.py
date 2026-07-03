from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class DatasetFiles:
    training_samples: str
    judgments: str
    raw_snapshot_index: str
    raw_snapshots_prefix: str

    @classmethod
    def from_json(cls, value: dict[str, Any]) -> DatasetFiles:
        return cls(
            training_samples=str(value["trainingSamples"]),
            judgments=str(value["judgments"]),
            raw_snapshot_index=str(value["rawSnapshotIndex"]),
            raw_snapshots_prefix=str(value["rawSnapshotsPrefix"]),
        )

    def to_json(self) -> dict[str, str]:
        return {
            "trainingSamples": self.training_samples,
            "judgments": self.judgments,
            "rawSnapshotIndex": self.raw_snapshot_index,
            "rawSnapshotsPrefix": self.raw_snapshots_prefix,
        }


@dataclass(frozen=True)
class DatasetManifest:
    dataset_version: str
    candidate_set_count: int
    feature_schema_version: str
    raw_schema_version: str
    sample_count: int
    judgment_count: int
    created_at: str
    files: DatasetFiles

    @classmethod
    def from_json(cls, value: dict[str, Any]) -> DatasetManifest:
        manifest = cls(
            dataset_version=str(value["datasetVersion"]),
            candidate_set_count=int(value["candidateSetCount"]),
            feature_schema_version=str(value["featureSchemaVersion"]),
            raw_schema_version=str(value["rawSchemaVersion"]),
            sample_count=int(value["sampleCount"]),
            judgment_count=int(value["judgmentCount"]),
            created_at=str(value["createdAt"]),
            files=DatasetFiles.from_json(value["files"]),
        )
        manifest.validate()
        return manifest

    def to_json(self) -> dict[str, Any]:
        self.validate()
        return {
            "datasetVersion": self.dataset_version,
            "candidateSetCount": self.candidate_set_count,
            "featureSchemaVersion": self.feature_schema_version,
            "rawSchemaVersion": self.raw_schema_version,
            "sampleCount": self.sample_count,
            "judgmentCount": self.judgment_count,
            "createdAt": self.created_at,
            "files": self.files.to_json(),
        }

    def validate(self) -> None:
        if not self.dataset_version:
            raise ValueError("datasetVersion 不能为空")
        if self.candidate_set_count < 1:
            raise ValueError("candidateSetCount 必须 >= 1")
        if self.sample_count < 1:
            raise ValueError("sampleCount 必须 >= 1")
        if self.judgment_count < 1:
            raise ValueError("judgmentCount 必须 >= 1")
        if not self.feature_schema_version:
            raise ValueError("featureSchemaVersion 不能为空")
        if not self.raw_schema_version:
            raise ValueError("rawSchemaVersion 不能为空")
