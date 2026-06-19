from __future__ import annotations

import json

from .config import JudgeConfig, LlmConfig
from .http_json import post_json
from .prompt import SYSTEM_PROMPT


class LlmClient:
    def __init__(self, llm: LlmConfig, judge: JudgeConfig):
        self._llm = llm
        self._judge = judge

    def judge(self, user_prompt: str) -> dict:
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
        return parse_json_content(content)


def parse_json_content(content: str) -> dict:
    cleaned = content.strip()
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
