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

    llm_pool = [_load_llm_config(require_api_key)]

    judge_raw = raw.get("judge")
    if not isinstance(judge_raw, dict):
        raise ValueError("config.json 缺少 judge 配置对象")
    judge = JudgeConfig(
        prompt_version=str(_required_json(judge_raw, "promptVersion")),
        judges_per_candidate_set=int(_required_json(judge_raw, "judgesPerCandidateSet")),
        full_judge_ratio=float(_required_json(judge_raw, "fullJudgeRatio")),
        max_retries=int(_required_json(judge_raw, "maxRetries")),
        timeout_seconds=int(_required_json(judge_raw, "timeoutSeconds")),
        temperature=float(_required_json(judge_raw, "temperature")),
        seed=_optional_int(_required_json(judge_raw, "seed", allow_none=True)),
    )
    if judge.judges_per_candidate_set < 1:
        raise ValueError("judgesPerCandidateSet 必须 >= 1")
    if not 0 <= judge.full_judge_ratio <= 1:
        raise ValueError("fullJudgeRatio 必须在 [0, 1]")
    if judge.max_retries < 0:
        raise ValueError("maxRetries 必须 >= 0")
    return AppConfig(backend=backend, llm_pool=llm_pool, judge=judge)


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
