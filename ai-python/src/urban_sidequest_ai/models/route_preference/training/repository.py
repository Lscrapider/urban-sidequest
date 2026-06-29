from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
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


class RoutePreferenceTrainingRepository:
    def __init__(self, connection):
        self._connection = connection

    def fetch_training_samples(self, feature_schema_version: str | None = None) -> list[TrainingSampleRow]:
        sql = """
            SELECT
                candidate_set_id::text,
                route_code,
                feature_schema_version,
                stop_matrix_json,
                segment_matrix_json,
                route_derived_vector_json,
                context_cross_vector_json,
                intra_set_vector_json
            FROM route_preference_training_samples
            WHERE (%s::text IS NULL OR feature_schema_version = %s::text)
              AND sample_status = 'TRAIN_READY'
            ORDER BY candidate_set_id, route_code
        """
        with self._connection.cursor() as cursor:
            cursor.execute(sql, (feature_schema_version, feature_schema_version))
            rows = cursor.fetchall()
        return [
            TrainingSampleRow(
                candidate_set_id=str(row[0]),
                route_code=str(row[1]),
                feature_schema_version=str(row[2]),
                stop_matrix_json=row[3],
                segment_matrix_json=row[4],
                route_derived_vector_json=row[5],
                context_cross_vector_json=row[6],
                intra_set_vector_json=row[7],
            )
            for row in rows
        ]

    def fetch_completed_judgments(self, candidate_set_ids: set[str] | None = None) -> list[JudgmentRow]:
        ids = sorted(candidate_set_ids or [])
        sql = """
            SELECT
                id::text,
                candidate_set_id::text,
                ranking_json,
                accepted_route_codes_json,
                rejected_route_codes_json,
                reason_codes_json,
                confidence,
                judge_type,
                judge_model,
                judge_prompt_version
            FROM route_preference_judgments
            WHERE status = 'COMPLETED'
              AND (%s = 0 OR candidate_set_id::text = ANY(%s))
            ORDER BY candidate_set_id, completed_at, id
        """
        with self._connection.cursor() as cursor:
            cursor.execute(sql, (len(ids), ids))
            rows = cursor.fetchall()
        return [
            JudgmentRow(
                judgment_id=str(row[0]),
                candidate_set_id=str(row[1]),
                ranking_json=row[2],
                accepted_route_codes_json=row[3],
                rejected_route_codes_json=row[4],
                reason_codes_json=row[5],
                confidence=_float_decimal(row[6]),
                judge_type=str(row[7]),
                judge_model=str(row[8]),
                judge_prompt_version=str(row[9]),
            )
            for row in rows
        ]

    def fetch_samples_for_candidate_set(
        self,
        candidate_set_id: str,
        feature_schema_version: str | None = None,
    ) -> list[TrainingSampleRow]:
        sql = """
            SELECT
                candidate_set_id::text,
                route_code,
                feature_schema_version,
                stop_matrix_json,
                segment_matrix_json,
                route_derived_vector_json,
                context_cross_vector_json,
                intra_set_vector_json
            FROM route_preference_training_samples
            WHERE candidate_set_id::text = %s::text
              AND (%s::text IS NULL OR feature_schema_version = %s::text)
            ORDER BY route_code
        """
        with self._connection.cursor() as cursor:
            cursor.execute(sql, (candidate_set_id, feature_schema_version, feature_schema_version))
            rows = cursor.fetchall()
        return [
            TrainingSampleRow(
                candidate_set_id=str(row[0]),
                route_code=str(row[1]),
                feature_schema_version=str(row[2]),
                stop_matrix_json=row[3],
                segment_matrix_json=row[4],
                route_derived_vector_json=row[5],
                context_cross_vector_json=row[6],
                intra_set_vector_json=row[7],
            )
            for row in rows
        ]


def _float_decimal(value: Decimal | float | int | None) -> float:
    if value is None:
        return 0.0
    return float(value)
