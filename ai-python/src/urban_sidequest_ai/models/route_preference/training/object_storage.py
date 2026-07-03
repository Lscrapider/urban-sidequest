from __future__ import annotations

from dataclasses import dataclass
import gzip
import json
import os
from typing import Any

from urban_sidequest_ai.env_loader import load_runtime_env


@dataclass(frozen=True)
class ObjectStorageConfig:
    endpoint: str
    access_key: str
    secret_key: str
    bucket: str
    prefix: str
    dataset_version: str | None = None

    def key(self, path: str) -> str:
        return "/".join(part.strip("/") for part in (self.prefix, path) if part.strip("/"))


class ObjectStorageClient:
    def __init__(self, config: ObjectStorageConfig):
        import boto3

        self.config = config
        self._client = boto3.client(
            "s3",
            endpoint_url=config.endpoint,
            aws_access_key_id=config.access_key,
            aws_secret_access_key=config.secret_key,
        )

    def list_keys(self, prefix: str) -> list[str]:
        keys: list[str] = []
        paginator = self._client.get_paginator("list_objects_v2")
        for page in paginator.paginate(Bucket=self.config.bucket, Prefix=prefix):
            keys.extend(item["Key"] for item in page.get("Contents", []))
        return sorted(keys)

    def read_bytes(self, key: str) -> bytes:
        response = self._client.get_object(Bucket=self.config.bucket, Key=key)
        return response["Body"].read()

    def write_bytes(self, key: str, data: bytes, content_type: str) -> None:
        self._client.put_object(
            Bucket=self.config.bucket,
            Key=key,
            Body=data,
            ContentType=content_type,
        )

    def delete_keys(self, keys: list[str]) -> None:
        if not keys:
            return
        for start in range(0, len(keys), 1000):
            batch = keys[start:start + 1000]
            self._client.delete_objects(
                Bucket=self.config.bucket,
                Delete={"Objects": [{"Key": key} for key in batch], "Quiet": True},
            )


def load_object_storage_config(dataset_version: str | None = None) -> ObjectStorageConfig:
    load_runtime_env()
    return ObjectStorageConfig(
        endpoint=_required_env("ROUTE_PREF_MINIO_ENDPOINT"),
        access_key=_required_env("ROUTE_PREF_MINIO_ACCESS_KEY"),
        secret_key=_required_env("ROUTE_PREF_MINIO_SECRET_KEY"),
        bucket=_required_env("ROUTE_PREF_MINIO_BUCKET"),
        prefix=os.environ.get("ROUTE_PREF_MINIO_PREFIX", "route-preference"),
        dataset_version=dataset_version or os.environ.get("ROUTE_PREF_DATASET_VERSION"),
    )


def read_json_bytes(data: bytes, gzipped: bool) -> Any:
    raw = gzip.decompress(data) if gzipped else data
    return json.loads(raw.decode("utf-8"))


def json_bytes(value: Any, gzipped: bool) -> bytes:
    raw = json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str).encode("utf-8")
    return gzip.compress(raw) if gzipped else raw


def _required_env(key: str) -> str:
    value = os.environ.get(key)
    if not value:
        raise ValueError(f"环境变量 {key} 未配置")
    return value
