from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import asdict, dataclass
from itertools import combinations
import json
import math
from pathlib import Path
import statistics
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[5]))
    __package__ = "urban_sidequest_ai.models.route_preference.training.k_judgment_ablation"

from urban_sidequest_ai.models.route_preference.training.dataset import (
    LabeledCandidateSet,
    ParsedJudgment,
    RouteInput,
    _build_labeled_candidate_set,
    _parse_judgment,
    _samples_by_candidate_set,
    infer_feature_spec,
)
from urban_sidequest_ai.models.route_preference.training.db import connect, load_database_config
from urban_sidequest_ai.models.route_preference.training.repository import (
    JudgmentRow,
    RoutePreferenceTrainingRepository,
    TrainingSampleRow,
)
from urban_sidequest_ai.models.route_preference.training.schema import InvalidJudgmentPolicy
from urban_sidequest_ai.models.route_preference.training.train import TRAIN_CONFIG


DEFAULT_METRICS_PATH = (
    Path(__file__).resolve().parent
    / "reports"
    / "k_judgment_ablation_metrics.json"
)


@dataclass(frozen=True)
class Projection:
    variant: str
    candidate_set_id: str
    judgment_ids: tuple[str, ...]
    group: LabeledCandidateSet


@dataclass(frozen=True)
class VariantSummary:
    variant: str
    candidate_sets: int
    projections: int
    route_count_mean: float
    pair_count_mean: float
    pair_weight_sum_mean: float
    top1_agreement: float
    top2_hit_against_full: float
    ndcg_at_3_against_full: float


@dataclass(frozen=True)
class PairAgreementSummary:
    variant: str
    gap_label: str
    full_pairs: int
    compared_pairs: int
    same: int
    flipped: int
    missing: int
    same_rate: float
    flipped_rate: float
    missing_rate: float
    weighted_same_rate: float
    weighted_flipped_rate: float
    weighted_missing_rate: float


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    sample_rows, judgment_rows = _load_rows(args.feature_schema_version)
    projections_by_variant = build_projections(
        sample_rows,
        judgment_rows,
        invalid_judgment_policy=InvalidJudgmentPolicy.SKIP if args.skip_invalid_judgments else InvalidJudgmentPolicy.FAIL,
        limit_candidate_sets=args.limit_candidate_sets,
    )
    payload = build_result_payload(
        projections_by_variant,
        feature_schema_version=args.feature_schema_version,
        limit_candidate_sets=args.limit_candidate_sets,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"写入 k judgment ablation 指标: {args.output}")
    return 0


def build_projections(
    sample_rows: list[TrainingSampleRow],
    judgment_rows: list[JudgmentRow],
    invalid_judgment_policy: InvalidJudgmentPolicy,
    limit_candidate_sets: int | None,
) -> dict[str, list[Projection]]:
    feature_spec = infer_feature_spec(sample_rows)
    samples_by_set = _samples_by_candidate_set(sample_rows, feature_spec)
    judgments_by_set: dict[str, list[JudgmentRow]] = defaultdict(list)
    for judgment in judgment_rows:
        judgments_by_set[judgment.candidate_set_id].append(judgment)

    candidate_set_ids = sorted(
        candidate_set_id
        for candidate_set_id, judgments in judgments_by_set.items()
        if len(judgments) == 3 and candidate_set_id in samples_by_set
    )
    if limit_candidate_sets is not None:
        candidate_set_ids = candidate_set_ids[:limit_candidate_sets]
    if not candidate_set_ids:
        raise ValueError("没有找到同时具备训练样本和 3 个 completed judgment 的 candidate set")

    projections_by_variant: dict[str, list[Projection]] = {
        "pseudo-k1": [],
        "pseudo-k2": [],
        "full-k3": [],
    }
    skipped: list[str] = []
    for candidate_set_id in candidate_set_ids:
        route_inputs = samples_by_set[candidate_set_id]
        route_codes = sorted(route_inputs)
        parsed_judgments: list[ParsedJudgment] = []
        for judgment in judgments_by_set[candidate_set_id]:
            try:
                parsed_judgments.append(_parse_judgment(judgment, route_codes))
            except ValueError as exception:
                message = f"judgmentId={judgment.judgment_id} candidateSetId={candidate_set_id}: {exception}"
                if invalid_judgment_policy == InvalidJudgmentPolicy.FAIL:
                    raise ValueError(message) from exception
                skipped.append(message)
        if len(parsed_judgments) != 3:
            continue

        for subset in combinations(parsed_judgments, 1):
            projections_by_variant["pseudo-k1"].append(_build_projection("pseudo-k1", candidate_set_id, subset, route_inputs))
        for subset in combinations(parsed_judgments, 2):
            projections_by_variant["pseudo-k2"].append(_build_projection("pseudo-k2", candidate_set_id, subset, route_inputs))
        projections_by_variant["full-k3"].append(
            _build_projection("full-k3", candidate_set_id, tuple(parsed_judgments), route_inputs)
        )

    if skipped and invalid_judgment_policy == InvalidJudgmentPolicy.SKIP:
        print(f"跳过无效 judgment 数量: {len(skipped)}")
    return projections_by_variant


def build_result_payload(
    projections_by_variant: dict[str, list[Projection]],
    feature_schema_version: str | None,
    limit_candidate_sets: int | None,
) -> dict[str, object]:
    full_by_set = {
        projection.candidate_set_id: projection
        for projection in projections_by_variant["full-k3"]
    }
    variant_summaries = [
        _summarize_variant(variant, projections, full_by_set)
        for variant, projections in projections_by_variant.items()
    ]
    pair_summaries = [
        summary
        for variant, projections in projections_by_variant.items()
        for summary in _summarize_pair_agreement(variant, projections, full_by_set)
    ]
    return {
        "settings": {
            "feature_schema_version": feature_schema_version,
            "limit_candidate_sets": limit_candidate_sets,
            "candidate_set_filter": "completed judgment 数量恰好为 3，且存在 TRAIN_READY 训练样本",
            "variants": {
                "pseudo-k1": "每个 full-k3 candidate set 的 3 个单 judgment 投影",
                "pseudo-k2": "每个 full-k3 candidate set 的 3 个二 judgment 组合",
                "full-k3": "完整 3 judgment 聚合",
            },
            "baseline": "同一 candidate set 的 full-k3 共识标签",
        },
        "variant_summaries": [asdict(summary) for summary in variant_summaries],
        "pair_agreement_summaries": [asdict(summary) for summary in pair_summaries],
    }


def _load_rows(feature_schema_version: str | None) -> tuple[list[TrainingSampleRow], list[JudgmentRow]]:
    db_config = load_database_config()
    with connect(db_config) as connection:
        repository = RoutePreferenceTrainingRepository(connection)
        sample_rows = repository.fetch_training_samples(feature_schema_version)
        judgment_rows = repository.fetch_completed_judgments({row.candidate_set_id for row in sample_rows})
    return sample_rows, judgment_rows


def _build_projection(
    variant: str,
    candidate_set_id: str,
    judgments: tuple[ParsedJudgment, ...],
    route_inputs: dict[str, RouteInput],
) -> Projection:
    group = _build_labeled_candidate_set(candidate_set_id, list(judgments), route_inputs)
    judgment_ids = tuple(judgment.row.judgment_id for judgment in judgments)
    return Projection(
        variant=variant,
        candidate_set_id=candidate_set_id,
        judgment_ids=judgment_ids,
        group=group,
    )


def _summarize_variant(
    variant: str,
    projections: list[Projection],
    full_by_set: dict[str, Projection],
) -> VariantSummary:
    route_counts = [len(projection.group.items) for projection in projections]
    pair_counts = [len(projection.group.pairs) for projection in projections]
    pair_weight_sums = [
        sum(pair.weight_raw for pair in projection.group.pairs)
        for projection in projections
    ]
    return VariantSummary(
        variant=variant,
        candidate_sets=len({projection.candidate_set_id for projection in projections}),
        projections=len(projections),
        route_count_mean=_mean(route_counts),
        pair_count_mean=_mean(pair_counts),
        pair_weight_sum_mean=_mean(pair_weight_sums),
        top1_agreement=_mean([
            float(_top_codes(projection.group, 1) == _top_codes(full_by_set[projection.candidate_set_id].group, 1))
            for projection in projections
        ]),
        top2_hit_against_full=_mean([
            float(_top_codes(full_by_set[projection.candidate_set_id].group, 1)[0] in _top_codes(projection.group, 2))
            for projection in projections
        ]),
        ndcg_at_3_against_full=_mean([
            _ndcg_at_3_against_full(projection.group, full_by_set[projection.candidate_set_id].group)
            for projection in projections
        ]),
    )


def _summarize_pair_agreement(
    variant: str,
    projections: list[Projection],
    full_by_set: dict[str, Projection],
) -> list[PairAgreementSummary]:
    buckets: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
    for projection in projections:
        full_group = full_by_set[projection.candidate_set_id].group
        full_rank = _rank_by_route(full_group)
        projection_pairs = _pair_direction_by_unordered_key(projection.group)
        for full_pair in full_group.pairs:
            gap = abs(full_rank[full_pair.chosen_route_code] - full_rank[full_pair.rejected_route_code])
            for gap_label in ("all", f"gap={gap}" if gap <= 3 else "gap>=4"):
                bucket = buckets[gap_label]
                bucket["full_pairs"] += 1
                bucket["full_weight"] += full_pair.weight_raw
                key = tuple(sorted((full_pair.chosen_route_code, full_pair.rejected_route_code)))
                projection_direction = projection_pairs.get(key)
                if projection_direction is None:
                    bucket["missing"] += 1
                    bucket["missing_weight"] += full_pair.weight_raw
                    continue
                bucket["compared_pairs"] += 1
                if projection_direction == (full_pair.chosen_route_code, full_pair.rejected_route_code):
                    bucket["same"] += 1
                    bucket["same_weight"] += full_pair.weight_raw
                else:
                    bucket["flipped"] += 1
                    bucket["flipped_weight"] += full_pair.weight_raw

    ordered_gap_labels = ["all", "gap=1", "gap=2", "gap=3", "gap>=4"]
    summaries: list[PairAgreementSummary] = []
    for gap_label in ordered_gap_labels:
        bucket = buckets.get(gap_label)
        if not bucket:
            continue
        full_pairs = int(bucket["full_pairs"])
        full_weight = bucket["full_weight"]
        summaries.append(
            PairAgreementSummary(
                variant=variant,
                gap_label=gap_label,
                full_pairs=full_pairs,
                compared_pairs=int(bucket["compared_pairs"]),
                same=int(bucket["same"]),
                flipped=int(bucket["flipped"]),
                missing=int(bucket["missing"]),
                same_rate=_safe_div(bucket["same"], full_pairs),
                flipped_rate=_safe_div(bucket["flipped"], full_pairs),
                missing_rate=_safe_div(bucket["missing"], full_pairs),
                weighted_same_rate=_safe_div(bucket["same_weight"], full_weight),
                weighted_flipped_rate=_safe_div(bucket["flipped_weight"], full_weight),
                weighted_missing_rate=_safe_div(bucket["missing_weight"], full_weight),
            )
        )
    return summaries


def _top_codes(group: LabeledCandidateSet, count: int) -> tuple[str, ...]:
    ordered = sorted(group.items, key=lambda item: item.rank)
    return tuple(item.route_code for item in ordered[:count])


def _rank_by_route(group: LabeledCandidateSet) -> dict[str, int]:
    return {item.route_code: item.rank for item in group.items}


def _pair_direction_by_unordered_key(group: LabeledCandidateSet) -> dict[tuple[str, str], tuple[str, str]]:
    return {
        tuple(sorted((pair.chosen_route_code, pair.rejected_route_code))): (
            pair.chosen_route_code,
            pair.rejected_route_code,
        )
        for pair in group.pairs
    }


def _ndcg_at_3_against_full(projection_group: LabeledCandidateSet, full_group: LabeledCandidateSet) -> float:
    full_rank = _rank_by_route(full_group)
    route_count = len(full_group.items)
    predicted = _top_codes(projection_group, 3)
    ideal = _top_codes(full_group, 3)
    dcg = sum(
        (route_count - full_rank[route_code]) / math.log2(position + 2)
        for position, route_code in enumerate(predicted)
    )
    idcg = sum(
        (route_count - full_rank[route_code]) / math.log2(position + 2)
        for position, route_code in enumerate(ideal)
    )
    return _safe_div(dcg, idcg)


def _mean(values) -> float:
    values = list(values)
    return 0.0 if not values else statistics.fmean(values)


def _safe_div(numerator: float, denominator: float) -> float:
    return 0.0 if denominator == 0 else numerator / denominator


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="对现有 k=3 route preference judgment 做 pseudo-k1/pseudo-k2/full-k3 标签层 ablation。",
    )
    parser.add_argument(
        "--feature-schema-version",
        default=TRAIN_CONFIG.feature_schema_version,
        help="训练样本 feature_schema_version；默认复用训练配置。",
    )
    parser.add_argument(
        "--limit-candidate-sets",
        type=int,
        default=None,
        help="只取排序后的前 N 个 k=3 candidate set，默认使用全部。",
    )
    parser.add_argument(
        "--skip-invalid-judgments",
        action="store_true",
        help="遇到无效 judgment 时跳过，否则失败。",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_METRICS_PATH,
        help="JSON 指标输出路径。",
    )
    return parser.parse_args(argv)
