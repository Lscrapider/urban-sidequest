from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class TrainingSampleRow:
    candidate_set_id: str
    route_code: str
    feature_schema_version: str
    stop_matrix_json: Any
    segment_matrix_json: Any
    route_derived_vector_json: Any
    context_cross_vector_json: Any
    intra_set_vector_json: Any


@dataclass(frozen=True)
class JudgmentRow:
    judgment_id: str
    candidate_set_id: str
    ranking_json: Any
    accepted_route_codes_json: Any
    rejected_route_codes_json: Any
    reason_codes_json: Any
    confidence: float
    judge_type: str
    judge_model: str
    judge_prompt_version: str
