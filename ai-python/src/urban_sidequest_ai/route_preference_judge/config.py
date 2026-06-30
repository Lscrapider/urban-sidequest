from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import json
import os

from urban_sidequest_ai.env_loader import load_runtime_env

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
    multi_judge_enabled: bool
    judge_count: int
    candidate_set_judge_concurrency: int
    full_judge_ratio: float
    max_retries: int
    timeout_seconds: int
    temperature: float
    multi_judge_temperatures: tuple[float, ...]
    seed: int | None


@dataclass(frozen=True)
class AppConfig:
    backend: BackendConfig
    llm_pool: list[LlmConfig]
    judge: JudgeConfig


def load_config(path: Path | None = None, require_api_key: bool = True) -> AppConfig:
    load_runtime_env()
    raw = _load_raw_config(path)

    backend = BackendConfig(
        base_url=_required_env("BACKEND_BASE_URL").rstrip("/"),
        auth_token=_env_first("BACKEND_AUTH_TOKEN"),
        login_phone=_env_first("BACKEND_LOGIN_PHONE"),
        login_code=_env_first("BACKEND_LOGIN_CODE"),
        timeout_seconds=_required_int_env("BACKEND_TIMEOUT_SECONDS"),
    )

    llm_pool = _load_llm_pool(require_api_key)

    judge_raw = raw.get("judge")
    if not isinstance(judge_raw, dict):
        raise ValueError("config.json 缺少 judge 配置对象")
    multi_judge_enabled, judge_count = _parse_judge_count(_required_json(judge_raw, "judgesPerCandidateSet"))
    temperature = float(_required_json(judge_raw, "temperature"))
    multi_judge_temperatures = _parse_multi_judge_temperatures(
        judge_raw.get("multiJudgeTemperatures"),
        temperature,
    )
    candidate_set_judge_concurrency = (
        int(judge_raw["candidateSetJudgeConcurrency"])
        if "candidateSetJudgeConcurrency" in judge_raw
        else 1
    )
    judge = JudgeConfig(
        prompt_version=str(_required_json(judge_raw, "promptVersion")),
        multi_judge_enabled=multi_judge_enabled,
        judge_count=judge_count,
        candidate_set_judge_concurrency=candidate_set_judge_concurrency,
        full_judge_ratio=float(judge_raw.get("fullJudgeRatio", 1.0)),
        max_retries=int(_required_json(judge_raw, "maxRetries")),
        timeout_seconds=int(_required_json(judge_raw, "timeoutSeconds")),
        temperature=temperature,
        multi_judge_temperatures=multi_judge_temperatures,
        seed=_optional_int(_required_json(judge_raw, "seed", allow_none=True)),
    )
    if judge.candidate_set_judge_concurrency < 1:
        raise ValueError("candidateSetJudgeConcurrency 必须 >= 1")
    if not 0 <= judge.full_judge_ratio <= 1:
        raise ValueError("fullJudgeRatio 必须在 [0, 1]")
    if judge.max_retries < 0:
        raise ValueError("maxRetries 必须 >= 0")
    return AppConfig(backend=backend, llm_pool=llm_pool, judge=judge)


def _load_llm_pool(require_api_key: bool) -> list[LlmConfig]:
    raw_pool = _env_first("ROUTE_LLM_POOL_JSON")
    if not raw_pool:
        return [_load_llm_config(require_api_key)]
    try:
        decoded = json.loads(raw_pool)
    except json.JSONDecodeError as exception:
        raise ValueError("ROUTE_LLM_POOL_JSON 不是合法 JSON") from exception
    if not isinstance(decoded, list) or not decoded:
        raise ValueError("ROUTE_LLM_POOL_JSON 必须是非空数组")
    return [_load_llm_config_from_pool_item(item, require_api_key) for item in decoded]


def _load_llm_config(require_api_key: bool) -> LlmConfig:
    api_key = _env_first(DEFAULT_NEW_API_KEY_ENV)
    if not api_key and require_api_key:
        raise ValueError(f"环境变量 {DEFAULT_NEW_API_KEY_ENV} 未配置")
    return LlmConfig(
        provider=_required_env("ROUTE_LLM_PROVIDER"),
        base_url=_required_env("ROUTE_LLM_BASE_URL").rstrip("/"),
        api_key=str(api_key or ""),
        model=_required_env("ROUTE_LLM_MODEL"),
        completions_path=_required_env("ROUTE_LLM_COMPLETIONS_PATH"),
    )


def _load_llm_config_from_pool_item(raw: dict, require_api_key: bool) -> LlmConfig:
    if not isinstance(raw, dict):
        raise ValueError("ROUTE_LLM_POOL_JSON 每一项必须是对象")
    api_key = raw.get("apiKey") or raw.get("api_key")
    api_key_env = raw.get("apiKeyEnv") or raw.get("api_key_env") or DEFAULT_NEW_API_KEY_ENV
    if not api_key:
        api_key = _env_first(str(api_key_env))
    if not api_key and require_api_key:
        raise ValueError(f"ROUTE_LLM_POOL_JSON 模型缺少 apiKey，且环境变量 {api_key_env} 未配置")
    return LlmConfig(
        provider=str(_required_pool_value(raw, "provider")),
        base_url=str(_required_pool_value(raw, "baseUrl", "base_url")).rstrip("/"),
        api_key=str(api_key or ""),
        model=str(_required_pool_value(raw, "model")),
        completions_path=str(_required_pool_value(raw, "completionsPath", "completions_path")),
    )


def _required_pool_value(raw: dict, *keys: str):
    for key in keys:
        value = raw.get(key)
        if value:
            return value
    raise ValueError(f"ROUTE_LLM_POOL_JSON 模型缺少字段：{'/'.join(keys)}")


def _env_first(*keys: str) -> str | None:
    for key in keys:
        value = os.environ.get(key)
        if value:
            return value
    return None


def _required_env(key: str) -> str:
    value = _env_first(key)
    if not value:
        raise ValueError(f"环境变量 {key} 未配置")
    return value


def _required_int_env(key: str) -> int:
    return int(_required_env(key))


def _load_raw_config(path: Path | None) -> dict:
    if path is None:
        raise ValueError("缺少 judge 策略配置文件路径")
    if not path.exists():
        raise FileNotFoundError(f"judge 策略配置文件不存在：{path}")
    with path.open("r", encoding="utf-8") as file:
        raw = json.load(file)
    if not isinstance(raw, dict):
        raise ValueError("judge 策略配置文件必须是 JSON 对象")
    return raw


def _required_json(raw: dict, key: str, allow_none: bool = False):
    if key not in raw:
        raise ValueError(f"config.json 缺少 judge.{key}")
    value = raw[key]
    if value == "" or (value is None and not allow_none):
        raise ValueError(f"config.json 缺少 judge.{key}")
    return value


def _optional_int(value) -> int | None:
    if value is None or value == "":
        return None
    return int(value)


def _parse_judge_count(value) -> tuple[bool, int]:
    if isinstance(value, bool):
        return value, 3 if value else 1
    count = int(value)
    if count < 1:
        raise ValueError("judgesPerCandidateSet 必须是正整数")
    return count > 1, count


def _parse_multi_judge_temperatures(value, single_temperature: float) -> tuple[float, ...]:
    if value is None:
        return (single_temperature, 0.5, 1.0)
    if not isinstance(value, list) or not value:
        raise ValueError("multiJudgeTemperatures 必须是非空数字数组")
    temperatures = tuple(float(item) for item in value)
    if any(item < 0 for item in temperatures):
        raise ValueError("multiJudgeTemperatures 不能包含负数")
    if single_temperature not in temperatures:
        raise ValueError("multiJudgeTemperatures 必须包含 judge.temperature，保留 k=1 温度")
    return temperatures
