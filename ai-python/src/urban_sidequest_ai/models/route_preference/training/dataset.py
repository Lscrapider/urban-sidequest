from __future__ import annotations

from dataclasses import dataclass
import json
import logging
import random
from typing import Any, Iterable

import torch

from .pairs import PairwiseSample, build_pairwise_samples
from .repository import JudgmentRow, TrainingSampleRow
from .schema import (
    DatasetSplit,
    DEFAULT_TEST_RATIO,
    DEFAULT_TRAIN_RATIO,
    DEFAULT_VALID_RATIO,
    InvalidJudgmentPolicy,
    MAX_SEGMENTS,
    MAX_STOPS,
    MIN_SPLIT_GROUPS_WITH_TEST,
    REASON_CODE_TO_INDEX,
    REASON_CODES,
    FeatureSpec,
    validate_reason_codes,
)


LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class RouteInput:
    stop_matrix: tuple[tuple[float, ...], ...]
    segment_matrix: tuple[tuple[float, ...], ...]
    route_derived_vector: tuple[float, ...]
    context_cross_vector: tuple[float, ...]


@dataclass(frozen=True)
class RouteTrainingItem:
    candidate_set_id: str
    route_code: str
    route_input: RouteInput
    rank: int
    is_accepted: bool
    is_rejected: bool
    goodness_label: float
    goodness_mask: float
    reason_labels: tuple[float, ...]
    reason_mask: float


@dataclass(frozen=True)
class LabeledCandidateSet:
    candidate_set_id: str
    judgment_id: str
    judge_type: str
    judge_model: str
    judge_prompt_version: str
    confidence: float
    items: tuple[RouteTrainingItem, ...]
    pairs: tuple[PairwiseSample, ...]


@dataclass(frozen=True)
class DatasetBundle:
    feature_spec: FeatureSpec
    groups: tuple[LabeledCandidateSet, ...]
    skipped_judgments: tuple[str, ...]


@dataclass(frozen=True)
class SplitBundle:
    train: tuple[LabeledCandidateSet, ...]
    valid: tuple[LabeledCandidateSet, ...]
    test: tuple[LabeledCandidateSet, ...]


@dataclass(frozen=True)
class TensorBatch:
    stop_matrix: torch.Tensor
    segment_matrix: torch.Tensor
    route_derived_vector: torch.Tensor
    context_cross_vector: torch.Tensor
    goodness_label: torch.Tensor
    goodness_mask: torch.Tensor
    reason_labels: torch.Tensor
    reason_mask: torch.Tensor
    pair_chosen_index: torch.Tensor
    pair_rejected_index: torch.Tensor
    pair_weight_raw: torch.Tensor
    group_offsets: tuple[int, ...]
    route_codes: tuple[str, ...]

    @property
    def has_pairs(self) -> bool:
        return self.pair_chosen_index.numel() > 0


def build_dataset_bundle(
    sample_rows: list[TrainingSampleRow],
    judgment_rows: list[JudgmentRow],
    invalid_judgment_policy: InvalidJudgmentPolicy = InvalidJudgmentPolicy.FAIL,
) -> DatasetBundle:
    if not sample_rows:
        raise ValueError("没有读取到 route_preference_training_samples 训练样本")
    feature_spec = infer_feature_spec(sample_rows)
    samples_by_candidate_set = _samples_by_candidate_set(sample_rows, feature_spec)
    groups: list[LabeledCandidateSet] = []
    skipped: list[str] = []

    for judgment in judgment_rows:
        route_inputs = samples_by_candidate_set.get(judgment.candidate_set_id)
        if not route_inputs:
            continue
        try:
            groups.append(_build_labeled_candidate_set(judgment, route_inputs))
        except ValueError as exception:
            message = f"judgmentId={judgment.judgment_id} candidateSetId={judgment.candidate_set_id}: {exception}"
            if invalid_judgment_policy == InvalidJudgmentPolicy.FAIL:
                raise ValueError(message) from exception
            LOGGER.warning("跳过无效 judgment: %s", message)
            skipped.append(message)

    if not groups:
        raise ValueError("没有可用于训练的 completed judgment 与训练样本匹配")
    return DatasetBundle(feature_spec=feature_spec, groups=tuple(groups), skipped_judgments=tuple(skipped))


def infer_feature_spec(sample_rows: list[TrainingSampleRow]) -> FeatureSpec:
    versions = {row.feature_schema_version for row in sample_rows}
    if len(versions) != 1:
        raise ValueError(f"训练集只能包含一个 feature_schema_version，实际为 {sorted(versions)}")
    stop_keys: set[str] = set()
    segment_keys: set[str] = set()
    route_keys: set[str] = set()
    context_keys: set[str] = set()
    for row in sample_rows:
        stop_keys.update(_matrix_keys(_parse_json(row.stop_matrix_json)))
        segment_keys.update(_matrix_keys(_parse_json(row.segment_matrix_json)))
        route_keys.update(_vector_keys(_parse_json(row.route_derived_vector_json)))
        context_keys.update(_vector_keys(_parse_json(row.context_cross_vector_json)))
    return FeatureSpec(
        feature_schema_version=next(iter(versions)),
        stop_feature_keys=tuple(sorted(stop_keys)),
        segment_feature_keys=tuple(sorted(segment_keys)),
        route_derived_keys=tuple(sorted(route_keys)),
        context_cross_keys=tuple(sorted(context_keys)),
    )


def split_by_candidate_set(
    groups: tuple[LabeledCandidateSet, ...],
    seed: int,
    train_ratio: float = DEFAULT_TRAIN_RATIO,
    valid_ratio: float = DEFAULT_VALID_RATIO,
    test_ratio: float = DEFAULT_TEST_RATIO,
) -> SplitBundle:
    ratio_sum = train_ratio + valid_ratio + test_ratio
    if ratio_sum <= 0:
        raise ValueError("数据切分比例之和必须大于 0")
    candidate_set_ids = sorted({group.candidate_set_id for group in groups})
    rng = random.Random(seed)
    rng.shuffle(candidate_set_ids)
    total = len(candidate_set_ids)
    if total < MIN_SPLIT_GROUPS_WITH_TEST:
        valid_count = 1 if total > 1 else 0
        test_count = 0
    else:
        valid_count = max(1, round(total * valid_ratio / ratio_sum))
        test_count = max(1, round(total * test_ratio / ratio_sum))
        if valid_count + test_count >= total:
            test_count = max(0, total - valid_count - 1)
    train_count = max(total - valid_count - test_count, 0)
    train_ids = set(candidate_set_ids[:train_count])
    valid_ids = set(candidate_set_ids[train_count : train_count + valid_count])
    test_ids = set(candidate_set_ids[train_count + valid_count :])
    return SplitBundle(
        train=tuple(group for group in groups if group.candidate_set_id in train_ids),
        valid=tuple(group for group in groups if group.candidate_set_id in valid_ids),
        test=tuple(group for group in groups if group.candidate_set_id in test_ids),
    )


def iter_batches(
    groups: tuple[LabeledCandidateSet, ...],
    batch_candidate_sets: int,
    shuffle: bool,
    seed: int,
    device: torch.device,
) -> Iterable[TensorBatch]:
    if batch_candidate_sets < 1:
        raise ValueError("batch_candidate_sets 必须 >= 1")
    ordered = list(groups)
    if shuffle:
        random.Random(seed).shuffle(ordered)
    for start in range(0, len(ordered), batch_candidate_sets):
        yield tensorize_candidate_sets(ordered[start : start + batch_candidate_sets], device)


def tensorize_candidate_sets(groups: list[LabeledCandidateSet], device: torch.device) -> TensorBatch:
    route_inputs: list[RouteInput] = []
    goodness_label: list[float] = []
    goodness_mask: list[float] = []
    reason_labels: list[tuple[float, ...]] = []
    reason_mask: list[float] = []
    pair_chosen_index: list[int] = []
    pair_rejected_index: list[int] = []
    pair_weight_raw: list[float] = []
    group_offsets: list[int] = []
    route_codes: list[str] = []

    route_offset = 0
    for group in groups:
        group_offsets.append(route_offset)
        for item in group.items:
            route_inputs.append(item.route_input)
            goodness_label.append(item.goodness_label)
            goodness_mask.append(item.goodness_mask)
            reason_labels.append(item.reason_labels)
            reason_mask.append(item.reason_mask)
            route_codes.append(item.route_code)
        for pair in group.pairs:
            pair_chosen_index.append(route_offset + pair.chosen_index)
            pair_rejected_index.append(route_offset + pair.rejected_index)
            pair_weight_raw.append(pair.weight_raw)
        route_offset += len(group.items)
    stop_dim = max((len(row) for item in route_inputs for row in item.stop_matrix), default=0)
    segment_dim = max((len(row) for item in route_inputs for row in item.segment_matrix), default=0)

    return TensorBatch(
        stop_matrix=torch.tensor(
            [_pad_matrix(item.stop_matrix, MAX_STOPS, stop_dim) for item in route_inputs],
            dtype=torch.float32,
            device=device,
        ),
        segment_matrix=torch.tensor(
            [_pad_matrix(item.segment_matrix, MAX_SEGMENTS, segment_dim) for item in route_inputs],
            dtype=torch.float32,
            device=device,
        ),
        route_derived_vector=torch.tensor(
            [item.route_derived_vector for item in route_inputs], dtype=torch.float32, device=device
        ),
        context_cross_vector=torch.tensor(
            [item.context_cross_vector for item in route_inputs], dtype=torch.float32, device=device
        ),
        goodness_label=torch.tensor(goodness_label, dtype=torch.float32, device=device),
        goodness_mask=torch.tensor(goodness_mask, dtype=torch.float32, device=device),
        reason_labels=torch.tensor(reason_labels, dtype=torch.float32, device=device),
        reason_mask=torch.tensor(reason_mask, dtype=torch.float32, device=device),
        pair_chosen_index=torch.tensor(pair_chosen_index, dtype=torch.long, device=device),
        pair_rejected_index=torch.tensor(pair_rejected_index, dtype=torch.long, device=device),
        pair_weight_raw=torch.tensor(pair_weight_raw, dtype=torch.float32, device=device),
        group_offsets=tuple(group_offsets),
        route_codes=tuple(route_codes),
    )


def _samples_by_candidate_set(
    sample_rows: list[TrainingSampleRow],
    feature_spec: FeatureSpec,
) -> dict[str, dict[str, RouteInput]]:
    result: dict[str, dict[str, RouteInput]] = {}
    for row in sample_rows:
        result.setdefault(row.candidate_set_id, {})[row.route_code] = RouteInput(
            stop_matrix=_matrix_to_tuple(_parse_json(row.stop_matrix_json), feature_spec.stop_feature_keys),
            segment_matrix=_matrix_to_tuple(_parse_json(row.segment_matrix_json), feature_spec.segment_feature_keys),
            route_derived_vector=_vector_to_tuple(
                _parse_json(row.route_derived_vector_json), feature_spec.route_derived_keys
            ),
            context_cross_vector=_vector_to_tuple(
                _parse_json(row.context_cross_vector_json), feature_spec.context_cross_keys
            ),
        )
    return result


def _build_labeled_candidate_set(
    judgment: JudgmentRow,
    route_inputs: dict[str, RouteInput],
) -> LabeledCandidateSet:
    route_codes = sorted(route_inputs)
    ranking = _string_list(_parse_json(judgment.ranking_json), "ranking_json")
    accepted = _string_list(_parse_json(judgment.accepted_route_codes_json), "accepted_route_codes_json")
    rejected = _string_list(_parse_json(judgment.rejected_route_codes_json), "rejected_route_codes_json")
    reason_codes = _reason_code_map(_parse_json(judgment.reason_codes_json))
    _validate_judgment_payload(route_codes, ranking, accepted, rejected, reason_codes)

    rank_by_code = {route_code: rank for rank, route_code in enumerate(ranking)}
    accepted_set = set(accepted)
    rejected_set = set(rejected)
    items: list[RouteTrainingItem] = []
    for route_code in ranking:
        route_reasons = reason_codes.get(route_code, [])
        reason_labels = [0.0] * len(REASON_CODES)
        for reason_code in route_reasons:
            reason_labels[REASON_CODE_TO_INDEX[reason_code]] = 1.0
        is_accepted = route_code in accepted_set
        is_rejected = route_code in rejected_set
        items.append(
            RouteTrainingItem(
                candidate_set_id=judgment.candidate_set_id,
                route_code=route_code,
                route_input=route_inputs[route_code],
                rank=rank_by_code[route_code],
                is_accepted=is_accepted,
                is_rejected=is_rejected,
                goodness_label=1.0 if is_accepted else 0.0,
                goodness_mask=1.0 if is_accepted or is_rejected else 0.0,
                reason_labels=tuple(reason_labels),
                reason_mask=1.0 if is_rejected and bool(route_reasons) else 0.0,
            )
        )
    pairs = build_pairwise_samples(
        [item.route_code for item in items],
        ranking,
        accepted,
        rejected,
        judgment.judge_type,
        judgment.confidence,
    )
    return LabeledCandidateSet(
        candidate_set_id=judgment.candidate_set_id,
        judgment_id=judgment.judgment_id,
        judge_type=judgment.judge_type,
        judge_model=judgment.judge_model,
        judge_prompt_version=judgment.judge_prompt_version,
        confidence=judgment.confidence,
        items=tuple(items),
        pairs=tuple(pairs),
    )


def _validate_judgment_payload(
    route_codes: list[str],
    ranking: list[str],
    accepted: list[str],
    rejected: list[str],
    reason_codes: dict[str, list[str]],
) -> None:
    route_code_set = set(route_codes)
    if len(ranking) != len(route_codes) or set(ranking) != route_code_set:
        raise ValueError(f"ranking 必须是 routeCode 全排列: expected={route_codes}, actual={ranking}")
    if len(set(ranking)) != len(ranking):
        raise ValueError("ranking 不能包含重复 routeCode")
    if not set(accepted).issubset(route_code_set):
        raise ValueError(f"acceptedRouteCodes 包含外来 routeCode: {accepted}")
    if not set(rejected).issubset(route_code_set):
        raise ValueError(f"rejectedRouteCodes 包含外来 routeCode: {rejected}")
    overlap = set(accepted) & set(rejected)
    if overlap:
        raise ValueError(f"accepted/rejected 不能重叠: {sorted(overlap)}")
    rejected_set = set(rejected)
    for route_code, codes in reason_codes.items():
        if route_code not in route_code_set:
            raise ValueError(f"reasonCodes 包含外来 routeCode: {route_code}")
        if route_code not in rejected_set:
            raise ValueError(f"reasonCodes 只能包含 rejectedRouteCodes 中的路线: {route_code}")
        validate_reason_codes(codes, route_code)


def _parse_json(value: Any) -> Any:
    if isinstance(value, str):
        return json.loads(value)
    return value


def _matrix_keys(value: Any) -> set[str]:
    if not isinstance(value, list):
        raise ValueError(f"矩阵特征必须是数组: {type(value).__name__}")
    keys: set[str] = set()
    for row in value:
        keys.update(_vector_keys(row))
    return keys


def _vector_keys(value: Any) -> set[str]:
    if isinstance(value, dict):
        return {str(key) for key in value}
    if isinstance(value, list):
        return {str(index) for index in range(len(value))}
    raise ValueError(f"向量特征必须是对象或数组: {type(value).__name__}")


def _matrix_to_tuple(value: Any, keys: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
    if not isinstance(value, list):
        raise ValueError(f"矩阵特征必须是数组: {type(value).__name__}")
    return tuple(_vector_to_tuple(row, keys) for row in value)


def _vector_to_tuple(value: Any, keys: tuple[str, ...]) -> tuple[float, ...]:
    if isinstance(value, dict):
        return tuple(_float_value(value.get(key, 0.0)) for key in keys)
    if isinstance(value, list):
        by_index = {str(index): item for index, item in enumerate(value)}
        return tuple(_float_value(by_index.get(key, 0.0)) for key in keys)
    raise ValueError(f"向量特征必须是对象或数组: {type(value).__name__}")


def _string_list(value: Any, field_name: str) -> list[str]:
    if not isinstance(value, list):
        raise ValueError(f"{field_name} 必须是数组")
    return [str(item) for item in value]


def _reason_code_map(value: Any) -> dict[str, list[str]]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise ValueError("reason_codes_json 必须是对象")
    return {str(route_code): _string_list(codes, f"reasonCodes.{route_code}") for route_code, codes in value.items()}


def _float_value(value: Any) -> float:
    if value is None:
        return 0.0
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    return float(value)


def _pad_matrix(
    matrix: tuple[tuple[float, ...], ...],
    max_rows: int,
    row_dim: int,
) -> tuple[tuple[float, ...], ...]:
    if len(matrix) > max_rows:
        raise ValueError(f"矩阵行数超过上限: actual={len(matrix)}, maxRows={max_rows}")
    for row in matrix:
        if len(row) != row_dim:
            raise ValueError(f"矩阵行宽不一致: actual={len(row)}, expected={row_dim}")
    zero_row = tuple(0.0 for _ in range(row_dim))
    return matrix + tuple(zero_row for _ in range(max_rows - len(matrix)))
