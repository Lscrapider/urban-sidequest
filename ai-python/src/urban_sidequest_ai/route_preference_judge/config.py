from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import json
import os

DEFAULT_TIMEOUT_SECONDS = 300
DEFAULT_NEW_API_BASE_URL = "http://localhost:3000/v1"
DEFAULT_NEW_API_MODEL = "urban-mock-user"
DEFAULT_NEW_API_PROVIDER = "new-api"
DEFAULT_NEW_API_KEY_ENV = "NEW_API_KEY"


@dataclass(frozen=True)
class BackendConfig:
    base_url: str
    auth_token: str | None
    login_phone: str | None
    login_code: str | None
    timeout_seconds: int


@dataclass(frozen=True)
class LlmConfig:
    provider: str
    base_url: str
    api_key: str
    model: str
    completions_path: str

    @property
    def judge_model(self) -> str:
        return f"{self.provider}:{self.model}"


@dataclass(frozen=True)
class JudgeConfig:
    prompt_version: str
    judges_per_candidate_set: int
    full_judge_ratio: float
    max_retries: int
    timeout_seconds: int
    temperature: float
    seed: int | None


@dataclass(frozen=True)
class AppConfig:
    backend: BackendConfig
    llm_pool: list[LlmConfig]
    judge: JudgeConfig


def load_config(path: Path, require_api_key: bool = True) -> AppConfig:
    with path.open("r", encoding="utf-8") as file:
        raw = json.load(file)

    backend_raw = raw.get("backend") or {}
    login_raw = backend_raw.get("login") or {}
    backend = BackendConfig(
        base_url=str(backend_raw.get("baseUrl") or "http://localhost:8080").rstrip("/"),
        auth_token=backend_raw.get("authToken"),
        login_phone=login_raw.get("phone"),
        login_code=login_raw.get("code"),
        timeout_seconds=int(backend_raw.get("timeoutSeconds") or DEFAULT_TIMEOUT_SECONDS),
    )

    llm_pool = _parse_llm_pool(raw, require_api_key)

    judge_raw = raw.get("judge") or {}
    judge = JudgeConfig(
        prompt_version=str(judge_raw.get("promptVersion") or "llm-sim-user-v5-personal-review"),
        judges_per_candidate_set=int(judge_raw.get("judgesPerCandidateSet") or 2),
        full_judge_ratio=float(judge_raw.get("fullJudgeRatio") or 0.0),
        max_retries=int(judge_raw.get("maxRetries") or 1),
        timeout_seconds=int(judge_raw.get("timeoutSeconds") or DEFAULT_TIMEOUT_SECONDS),
        temperature=float(judge_raw.get("temperature") or 0.2),
        seed=judge_raw.get("seed"),
    )
    if judge.judges_per_candidate_set < 1:
        raise ValueError("judgesPerCandidateSet 必须 >= 1")
    if not 0 <= judge.full_judge_ratio <= 1:
        raise ValueError("fullJudgeRatio 必须在 [0, 1]")
    return AppConfig(backend=backend, llm_pool=llm_pool, judge=judge)


def _parse_llm_pool(raw: dict, require_api_key: bool) -> list[LlmConfig]:
    llm_pool_raw = raw.get("llmPool")
    if llm_pool_raw:
        if isinstance(llm_pool_raw, dict):
            llm_pool_raw = [llm_pool_raw]
        return [_parse_llm(item, require_api_key) for item in llm_pool_raw]

    new_api_raw = _first_present(raw, "newApi", "new_api", "newAPI")
    if new_api_raw is not None:
        if new_api_raw is True:
            new_api_raw = {}
        if not isinstance(new_api_raw, dict):
            raise ValueError("newApi/new_api 必须是对象")
        return [_parse_llm(_with_new_api_defaults(new_api_raw), require_api_key)]

    llm_raw = raw.get("llm")
    if llm_raw is not None:
        if not isinstance(llm_raw, dict):
            raise ValueError("llm 必须是对象")
        return [_parse_llm(llm_raw, require_api_key)]

    if _looks_like_llm(raw):
        return [_parse_llm(raw, require_api_key)]

    return [_parse_llm(_with_new_api_defaults({}), require_api_key)]


def _parse_llm(raw: dict, require_api_key: bool) -> LlmConfig:
    api_key = _first_present(raw, "apiKey", "apikey", "api_key", "api-key")
    api_key_env = _first_present(raw, "apiKeyEnv", "apikeyEnv", "api_key_env", "api-key-env")
    if not api_key and api_key_env:
        api_key = os.environ.get(str(api_key_env))
    if not api_key and require_api_key:
        provider = _first_present(raw, "provider", "vendor", "name", "llm", "model") or "unknown"
        raise ValueError(f"LLM {provider} 缺少 apiKey/apikey/api_key 或 apiKeyEnv")

    model = _first_present(raw, "model", "modelId", "model_id", "modelName", "model_name")
    if not model:
        provider = _first_present(raw, "provider", "vendor", "name", "llm") or "unknown"
        raise ValueError(f"LLM {provider} 缺少 model")

    base_url = _first_present(raw, "baseUrl", "base_url")
    endpoint_url = _first_present(raw, "url", "apiUrl", "api_url", "endpoint")
    if base_url:
        completions_path = _first_present(raw, "completionsPath", "completions_path", "path") or "/chat/completions"
    elif endpoint_url:
        base_url = endpoint_url
        completions_path = _first_present(raw, "completionsPath", "completions_path", "path") or ""
    else:
        provider = _first_present(raw, "provider", "vendor", "name", "llm", "model") or "unknown"
        raise ValueError(f"LLM {provider} 缺少 baseUrl/base_url 或 url")

    return LlmConfig(
        provider=str(_first_present(raw, "provider", "vendor", "name", "llm") or model),
        base_url=str(base_url).rstrip("/"),
        api_key=str(api_key or ""),
        model=str(model),
        completions_path=str(completions_path),
    )


def _first_present(raw: dict, *keys: str):
    for key in keys:
        value = raw.get(key)
        if value is not None and value != "":
            return value
    return None


def _with_new_api_defaults(raw: dict) -> dict:
    merged = dict(raw)
    if not _first_present(merged, "provider", "vendor", "name", "llm"):
        merged["provider"] = DEFAULT_NEW_API_PROVIDER
    if not _first_present(merged, "model", "modelId", "model_id", "modelName", "model_name"):
        merged["model"] = DEFAULT_NEW_API_MODEL
    if not _first_present(merged, "baseUrl", "base_url", "url", "apiUrl", "api_url", "endpoint"):
        merged["baseUrl"] = DEFAULT_NEW_API_BASE_URL
    if not _first_present(merged, "apiKey", "apikey", "api_key", "api-key", "apiKeyEnv", "apikeyEnv", "api_key_env", "api-key-env"):
        merged["apiKeyEnv"] = DEFAULT_NEW_API_KEY_ENV
    return merged


def _looks_like_llm(raw: dict) -> bool:
    return bool(
        _first_present(raw, "model", "modelId", "model_id", "modelName", "model_name")
        or _first_present(raw, "baseUrl", "base_url", "url", "apiUrl", "api_url", "endpoint")
    )
