from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import asdict, dataclass, replace
import hashlib
import json
from pathlib import Path
import statistics
import sys
from typing import Any

import torch
from torch.optim import AdamW

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[5]))
    __package__ = "urban_sidequest_ai.models.route_preference.training.k_judgment_ablation"

from urban_sidequest_ai.models.route_preference.training.dataset import (
    DatasetBundle,
    LabeledCandidateSet,
    SplitBundle,
    iter_batches,
    split_by_candidate_set,
    tensorize_candidate_sets,
)
from urban_sidequest_ai.models.route_preference.training.eval import evaluate_model
from urban_sidequest_ai.models.route_preference.training.losses import LossConfig, compute_losses
from urban_sidequest_ai.models.route_preference.training.model import (
    RoutePreferenceModel,
    RoutePreferenceModelConfig,
)
from urban_sidequest_ai.models.route_preference.training.schema import FeatureSpec, InvalidJudgmentPolicy
from urban_sidequest_ai.models.route_preference.training.train import (
    TRAIN_CONFIG,
    TrainingRuntimeConfig,
    _build_scheduler,
    _compute_goodness_pos_weight,
    _compute_reason_pos_weight,
    _metric_higher_is_better,
)

from .run import build_projections, _load_rows


DEFAULT_OUTPUT_PATH = (
    Path(__file__).resolve().parent
    / "reports"
    / "k_judgment_model_ablation_metrics.json"
)


@dataclass(frozen=True)
class AblationRunConfig:
    feature_schema_version: str | None
    train_seeds: tuple[int, ...]
    projection_seed: int
    epochs: int
    limit_candidate_sets: int | None
    output: Path
    device: str
    skip_invalid_judgments: bool


@dataclass(frozen=True)
class RunSummary:
    variant: str
    train_seed: int
    projection_seed: int
    groups: int
    train_groups: int
    valid_groups: int
    test_groups: int
    best_epoch: int
    best_own_valid_metric: float
    metrics: dict[str, float]


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    config = AblationRunConfig(
        feature_schema_version=args.feature_schema_version,
        train_seeds=tuple(args.train_seed),
        projection_seed=args.projection_seed,
        epochs=args.epochs,
        limit_candidate_sets=args.limit_candidate_sets,
        output=args.output,
        device=args.device,
        skip_invalid_judgments=args.skip_invalid_judgments,
    )
    payload = run_model_ablation(config)
    config.output.parent.mkdir(parents=True, exist_ok=True)
    config.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"写入 k judgment model ablation 指标: {config.output}")
    return 0


def run_model_ablation(config: AblationRunConfig) -> dict[str, Any]:
    sample_rows, judgment_rows = _load_rows(config.feature_schema_version)
    projections_by_variant = build_projections(
        sample_rows,
        judgment_rows,
        invalid_judgment_policy=InvalidJudgmentPolicy.SKIP
        if config.skip_invalid_judgments
        else InvalidJudgmentPolicy.FAIL,
        limit_candidate_sets=config.limit_candidate_sets,
    )
    full_groups = tuple(projection.group for projection in projections_by_variant["full-k3"])
    selected_groups_by_variant = {
        "pseudo-k1": _select_one_projection_per_set(
            projections_by_variant["pseudo-k1"],
            projection_seed=config.projection_seed,
        ),
        "pseudo-k2": _select_one_projection_per_set(
            projections_by_variant["pseudo-k2"],
            projection_seed=config.projection_seed,
        ),
        "full-k3": full_groups,
    }
    full_bundle = DatasetBundle(
        feature_spec=_infer_feature_spec_from_groups(full_groups),
        groups=full_groups,
        skipped_judgments=tuple(),
    )
    loss_config = LossConfig(
        beta=TRAIN_CONFIG.beta,
        lambda_goodness=TRAIN_CONFIG.lambda_goodness,
        lambda_reason=TRAIN_CONFIG.lambda_reason,
    )
    model_config = RoutePreferenceModelConfig.from_feature_spec(full_bundle.feature_spec)
    model_config = RoutePreferenceModelConfig(
        **{
            **model_config.__dict__,
            "hidden_dim": TRAIN_CONFIG.hidden_dim,
            "dropout": TRAIN_CONFIG.dropout,
            "reason_hidden_dim": TRAIN_CONFIG.reason_hidden_dim,
        }
    )

    runs: list[RunSummary] = []
    device = torch.device(config.device)
    for variant, groups in selected_groups_by_variant.items():
        bundle = DatasetBundle(
            feature_spec=full_bundle.feature_spec,
            groups=groups,
            skipped_judgments=tuple(),
        )
        for train_seed in config.train_seeds:
            run_config = replace(
                TRAIN_CONFIG,
                seed=train_seed,
                epochs=config.epochs,
                output_dir=TRAIN_CONFIG.output_dir / "k_judgment_model_ablation" / variant / f"seed-{train_seed}",
                skip_onnx=True,
                device=config.device,
            )
            runs.append(
                _train_one_variant(
                    variant=variant,
                    bundle=bundle,
                    full_bundle=full_bundle,
                    model_config=model_config,
                    loss_config=loss_config,
                    run_config=run_config,
                    projection_seed=config.projection_seed,
                    device=device,
                )
            )
    return {
        "settings": {
            "feature_schema_version": config.feature_schema_version,
            "limit_candidate_sets": config.limit_candidate_sets,
            "projection_seed": config.projection_seed,
            "train_seeds": list(config.train_seeds),
            "epochs": config.epochs,
            "device": config.device,
            "candidate_set_filter": "completed judgment 数量恰好为 3，且存在 TRAIN_READY 训练样本",
            "early_stopping_metric": TRAIN_CONFIG.best_metric,
            "early_stopping_target": "各 variant 自己的 valid 标签",
            "full_k3_eval": "同一 split 上的 full-k3 valid/test 标签",
        },
        "runs": [asdict(run) for run in runs],
        "summary": _summarize_runs(runs),
    }


def _train_one_variant(
    variant: str,
    bundle: DatasetBundle,
    full_bundle: DatasetBundle,
    model_config: RoutePreferenceModelConfig,
    loss_config: LossConfig,
    run_config: TrainingRuntimeConfig,
    projection_seed: int,
    device: torch.device,
) -> RunSummary:
    torch.manual_seed(run_config.seed)
    splits = split_by_candidate_set(bundle.groups, seed=run_config.seed)
    full_splits = split_by_candidate_set(full_bundle.groups, seed=run_config.seed)
    if not splits.train:
        raise ValueError(f"{variant} seed={run_config.seed} 训练集为空")

    effective_loss_config = loss_config
    if run_config.reason_pos_weight_cap > 0:
        effective_loss_config = replace(
            effective_loss_config,
            reason_pos_weight=_compute_reason_pos_weight(
                splits.train,
                run_config.reason_pos_weight_cap,
                run_config.reason_pos_weight_min_support,
            ),
        )
    if run_config.goodness_pos_weight_cap > 0:
        effective_loss_config = replace(
            effective_loss_config,
            goodness_pos_weight=_compute_goodness_pos_weight(
                splits.train,
                run_config.goodness_pos_weight_cap,
            ),
        )

    model = RoutePreferenceModel(model_config).to(device)
    optimizer = AdamW(model.parameters(), lr=run_config.lr, weight_decay=run_config.weight_decay)
    scheduler = _build_scheduler(optimizer, run_config)
    best_metric_key = run_config.best_metric
    higher_is_better = _metric_higher_is_better(best_metric_key) if best_metric_key else True
    best_score: float | None = None
    best_state: dict[str, Any] | None = None
    best_epoch = 0
    epochs_without_improvement = 0

    for epoch in range(1, run_config.epochs + 1):
        model.train()
        for batch in iter_batches(
            splits.train,
            run_config.batch_candidate_sets,
            shuffle=True,
            seed=run_config.seed + epoch,
            device=device,
        ):
            optimizer.zero_grad()
            output = model(
                batch.stop_matrix,
                batch.segment_matrix,
                batch.route_derived_vector,
                batch.context_cross_vector,
                batch.intra_set_vector,
            )
            losses = compute_losses(output, batch, effective_loss_config)
            losses.total_loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), run_config.grad_clip_norm)
            optimizer.step()

        own_valid = evaluate_model(
            model,
            splits.valid,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "own_valid",
        )
        current = own_valid.get(f"own_{best_metric_key}") if best_metric_key else None
        if current is None and best_metric_key:
            current = own_valid.get(best_metric_key.replace("valid/", "own_valid/"))
        if current is None:
            current = own_valid.get("own_valid/ndcg@3", 0.0)

        improved = False
        if best_score is None:
            improved = True
        elif higher_is_better:
            improved = current > best_score + run_config.min_delta
        else:
            improved = current < best_score - run_config.min_delta
        if improved:
            best_score = current
            best_state = {key: value.detach().cpu().clone() for key, value in model.state_dict().items()}
            best_epoch = epoch
            epochs_without_improvement = 0
        else:
            epochs_without_improvement += 1
            if run_config.patience > 0 and epochs_without_improvement >= run_config.patience:
                break

        if scheduler is not None:
            scheduler.step()

    if best_state is not None:
        model.load_state_dict(best_state)

    metrics: dict[str, float] = {}
    metrics.update(
        evaluate_model(
            model,
            splits.valid,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "own_valid",
        )
    )
    metrics.update(
        evaluate_model(
            model,
            splits.test,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "own_test",
        )
    )
    metrics.update(
        evaluate_model(
            model,
            full_splits.valid,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "full_valid",
        )
    )
    metrics.update(
        evaluate_model(
            model,
            full_splits.test,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "full_test",
        )
    )
    metrics.update(
        _evaluate_pair_gap_metrics(
            model,
            full_splits.valid,
            run_config.batch_candidate_sets,
            device,
            "full_valid",
        )
    )
    metrics.update(
        _evaluate_pair_gap_metrics(
            model,
            full_splits.test,
            run_config.batch_candidate_sets,
            device,
            "full_test",
        )
    )

    return RunSummary(
        variant=variant,
        train_seed=run_config.seed,
        projection_seed=projection_seed,
        groups=len(bundle.groups),
        train_groups=len(splits.train),
        valid_groups=len(splits.valid),
        test_groups=len(splits.test),
        best_epoch=best_epoch,
        best_own_valid_metric=best_score if best_score is not None else 0.0,
        metrics=metrics,
    )


def _evaluate_pair_gap_metrics(
    model: RoutePreferenceModel,
    groups: tuple[LabeledCandidateSet, ...],
    batch_candidate_sets: int,
    device: torch.device,
    prefix: str,
) -> dict[str, float]:
    if not groups:
        return {}
    model.eval()
    buckets: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
    with torch.no_grad():
        for start in range(0, len(groups), batch_candidate_sets):
            batch_groups = list(groups[start : start + batch_candidate_sets])
            batch = tensorize_candidate_sets(batch_groups, device)
            output = model(
                batch.stop_matrix,
                batch.segment_matrix,
                batch.route_derived_vector,
                batch.context_cross_vector,
                batch.intra_set_vector,
            )
            scores = output.route_preference_score.detach().cpu().tolist()
            route_offset = 0
            for group in batch_groups:
                rank_by_index = {index: item.rank for index, item in enumerate(group.items)}
                for pair in group.pairs:
                    gap = abs(rank_by_index[pair.chosen_index] - rank_by_index[pair.rejected_index])
                    gap_key = f"gap{gap}" if gap <= 3 else "gap>=4"
                    correct = scores[route_offset + pair.chosen_index] > scores[route_offset + pair.rejected_index]
                    for bucket_key in ("all", gap_key):
                        bucket = buckets[bucket_key]
                        bucket["total"] += 1
                        bucket["weighted_total"] += pair.weight_raw
                        if correct:
                            bucket["correct"] += 1
                            bucket["weighted_correct"] += pair.weight_raw
                route_offset += len(group.items)

    metrics: dict[str, float] = {}
    for bucket_key, bucket in buckets.items():
        metrics[f"{prefix}/pairAccuracy/{bucket_key}"] = _safe_div(bucket["correct"], bucket["total"])
        metrics[f"{prefix}/weightedPairAccuracy/{bucket_key}"] = _safe_div(
            bucket["weighted_correct"],
            bucket["weighted_total"],
        )
    return metrics


def _select_one_projection_per_set(projections, projection_seed: int) -> tuple[LabeledCandidateSet, ...]:
    by_set = defaultdict(list)
    for projection in projections:
        by_set[projection.candidate_set_id].append(projection)
    selected = []
    for candidate_set_id, choices in sorted(by_set.items()):
        ordered = sorted(choices, key=lambda projection: projection.judgment_ids)
        index = _stable_index(candidate_set_id, projection_seed, len(ordered))
        selected.append(ordered[index].group)
    return tuple(selected)


def _stable_index(candidate_set_id: str, seed: int, count: int) -> int:
    digest = hashlib.sha256(f"{seed}:{candidate_set_id}".encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") % count


def _infer_feature_spec_from_groups(groups: tuple[LabeledCandidateSet, ...]):
    if not groups:
        raise ValueError("groups 不能为空")
    first = groups[0].items[0].route_input
    return FeatureSpec(
        feature_schema_version=TRAIN_CONFIG.feature_schema_version or "route_pref",
        stop_feature_keys=tuple(str(index) for index in range(len(first.stop_matrix[0]) if first.stop_matrix else 0)),
        segment_feature_keys=tuple(
            str(index) for index in range(len(first.segment_matrix[0]) if first.segment_matrix else 0)
        ),
        route_derived_keys=tuple(str(index) for index in range(len(first.route_derived_vector))),
        context_cross_keys=tuple(str(index) for index in range(len(first.context_cross_vector))),
        intra_set_keys=tuple(str(index) for index in range(len(first.intra_set_vector))),
    )


def _summarize_runs(runs: list[RunSummary]) -> dict[str, dict[str, dict[str, float]]]:
    metric_keys = [
        "full_test/ndcg@3",
        "full_test/top1Accuracy",
        "full_test/top2HitRate",
        "full_test/pairwiseAccuracy",
        "full_test/weightedPairwiseAccuracy",
        "full_test/pairAccuracy/gap1",
        "full_test/weightedPairAccuracy/gap1",
        "full_valid/ndcg@3",
        "own_test/ndcg@3",
    ]
    grouped: dict[str, list[RunSummary]] = defaultdict(list)
    for run in runs:
        grouped[run.variant].append(run)
    summary: dict[str, dict[str, dict[str, float]]] = {}
    for variant, variant_runs in grouped.items():
        summary[variant] = {}
        for key in metric_keys:
            values = [run.metrics[key] for run in variant_runs if key in run.metrics]
            if values:
                summary[variant][key] = {
                    "mean": statistics.fmean(values),
                    "std": statistics.stdev(values) if len(values) > 1 else 0.0,
                    "n": float(len(values)),
                }
    return summary


def _safe_div(numerator: float, denominator: float) -> float:
    return 0.0 if denominator == 0 else numerator / denominator


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="训练 pseudo-k1/pseudo-k2/full-k3 三种标签视图，并统一评估到 full-k3 标签。",
    )
    parser.add_argument(
        "--feature-schema-version",
        default=TRAIN_CONFIG.feature_schema_version,
        help="训练样本 feature_schema_version；默认复用训练配置。",
    )
    parser.add_argument(
        "--train-seed",
        type=int,
        nargs="+",
        default=[23, 29, 31],
        help="训练随机种子列表。",
    )
    parser.add_argument(
        "--projection-seed",
        type=int,
        default=0,
        help="pseudo-k1/pseudo-k2 每个 candidate set 选哪一个投影的稳定 hash seed。",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=TRAIN_CONFIG.epochs,
        help="每次训练最大 epoch。",
    )
    parser.add_argument(
        "--limit-candidate-sets",
        type=int,
        default=None,
        help="只取排序后的前 N 个 k=3 candidate set，默认使用当前快照全部。",
    )
    parser.add_argument(
        "--skip-invalid-judgments",
        action="store_true",
        help="遇到无效 judgment 时跳过，否则失败。",
    )
    parser.add_argument(
        "--device",
        default=TRAIN_CONFIG.device,
        help="训练设备。",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help="JSON 指标输出路径。",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    raise SystemExit(main())
