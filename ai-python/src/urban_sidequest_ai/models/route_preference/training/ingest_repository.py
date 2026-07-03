from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Any

from .object_storage import ObjectStorageClient, ObjectStorageConfig, read_json_bytes


@dataclass(frozen=True)
class IngestCandidateSet:
    ready_key: str
    candidate_set_key: str
    judgment_keys: tuple[str, ...]
    payload: dict[str, Any]
    judgments: tuple[dict[str, Any], ...]

    @property
    def candidate_set_id(self) -> str:
        return str(self.payload["candidateSetId"])


@dataclass(frozen=True)
class IngestJudgment:
    key: str
    payload: dict[str, Any]

    @property
    def candidate_set_id(self) -> str:
        return str(self.payload["candidateSetId"])


class RoutePreferenceIngestRepository:
    def __init__(self, client: ObjectStorageClient, config: ObjectStorageConfig):
        self._client = client
        self._config = config

    def fetch_ready_candidate_sets(self) -> list[IngestCandidateSet]:
        judgments_by_candidate_set = self.fetch_judgments_by_candidate_set()
        ready_prefix = self._config.key("ingest/candidate_sets_ready/")
        ready_keys = self._client.list_keys(ready_prefix)
        items: list[IngestCandidateSet] = []
        for ready_key in ready_keys:
            marker = read_json_bytes(self._client.read_bytes(ready_key), gzipped=False)
            candidate_set_key = str(marker.get("objectKey") or self._candidate_set_key_from_ready_key(ready_key))
            payload = read_json_bytes(self._client.read_bytes(candidate_set_key), gzipped=True)
            candidate_set_id = str(payload["candidateSetId"])
            judgment_items = tuple(judgments_by_candidate_set.get(candidate_set_id, ()))
            judgment_keys = tuple(item.key for item in judgment_items)
            judgments = tuple(item.payload for item in judgment_items)
            items.append(IngestCandidateSet(ready_key, candidate_set_key, judgment_keys, payload, judgments))
        return items

    def fetch_judgments_by_candidate_set(self) -> dict[str, tuple[IngestJudgment, ...]]:
        judgment_prefix = self._config.key("ingest/judgments/")
        judgment_keys = self._client.list_keys(judgment_prefix)
        grouped: dict[str, list[IngestJudgment]] = {}
        for key in judgment_keys:
            payload = read_json_bytes(self._client.read_bytes(key), gzipped=True)
            candidate_set_id = str(payload["candidateSetId"])
            grouped.setdefault(candidate_set_id, []).append(IngestJudgment(key, payload))
        return {candidate_set_id: tuple(items) for candidate_set_id, items in grouped.items()}

    def delete_processed(self, items: list[IngestCandidateSet]) -> None:
        keys: list[str] = []
        for item in items:
            keys.append(item.ready_key)
            keys.append(item.candidate_set_key)
            keys.extend(item.judgment_keys)
        self._client.delete_keys(sorted(set(keys)))

    def delete_judgments(self, items: list[IngestJudgment]) -> None:
        self._client.delete_keys(sorted({item.key for item in items}))

    def _candidate_set_key_from_ready_key(self, ready_key: str) -> str:
        filename = PurePosixPath(ready_key).name
        candidate_set_id = filename.removesuffix(".json")
        return self._config.key(
            f"ingest/candidate_sets/shard={self._shard(candidate_set_id)}/{candidate_set_id}.json.gz"
        )

    @staticmethod
    def _shard(candidate_set_id: str) -> str:
        return candidate_set_id.replace("-", "")[:2].lower()
