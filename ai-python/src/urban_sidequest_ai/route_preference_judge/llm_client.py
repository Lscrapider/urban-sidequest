from __future__ import annotations

from dataclasses import dataclass
import json

from .config import JudgeConfig, LlmConfig
from .http_json import post_json
from .prompt import SYSTEM_PROMPT


@dataclass(frozen=True)
class LlmJudgeResult:
    judgment: dict
    response_model: str | None


class LlmClient:
    def __init__(self, llm: LlmConfig, judge: JudgeConfig):
        self._llm = llm
        self._judge = judge

    def judge(self, user_prompt: str) -> LlmJudgeResult:
        if not self._llm.api_key:
            raise ValueError(f"LLM {self._llm.judge_model} 缺少 apiKey")
        payload = {
            "model": self._llm.model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            "stream": False,
            "temperature": self._judge.temperature,
            "response_format": {"type": "json_object"},
        }
        response = post_json(
            self._llm.base_url + self._llm.completions_path,
            payload,
            headers={"Authorization": "Bearer " + self._llm.api_key},
            timeout=self._judge.timeout_seconds,
        )
        content = response["choices"][0]["message"]["content"]
        response_model = response.get("model") or response.get("modelId") or response.get("model_id")
        return LlmJudgeResult(
            judgment=parse_json_content(content),
            response_model=str(response_model) if response_model else None,
        )


def parse_json_content(content: str) -> dict:
    cleaned = content.strip()
    cleaned = strip_markdown_blockquote(cleaned)
    if cleaned.startswith("```"):
        first_line_break = cleaned.find("\n")
        last_fence = cleaned.rfind("```")
        if first_line_break >= 0 and last_fence > first_line_break:
            cleaned = cleaned[first_line_break + 1:last_fence].strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError as exception:
        preview = cleaned[:3000]
        if len(cleaned) > 3000:
            preview += f"\n... truncated {len(cleaned) - 3000} chars"
        raise ValueError(f"LLM 返回不是合法 JSON：{exception}; rawContent={preview}") from exception


def strip_markdown_blockquote(content: str) -> str:
    if not content.startswith(">"):
        return content
    unquoted_lines = []
    for line in content.splitlines():
        if line.startswith(">"):
            unquoted_lines.append(line[1:].lstrip())
        else:
            unquoted_lines.append(line)
    unquoted = "\n".join(unquoted_lines).strip()
    if unquoted.startswith(("```", "{", "[")):
        return unquoted
    return content
