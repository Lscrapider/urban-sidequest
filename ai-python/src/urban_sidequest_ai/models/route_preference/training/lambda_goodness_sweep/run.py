from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import asdict, dataclass, replace
import json
from pathlib import Path
import statistics
import sys
from typing import Any

import torch
from torch.optim import AdamW

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[5]))
    __package__ = "urban_sidequest_ai.models.route_preference.training.lambda_goodness_sweep"

from urban_sidequest_ai.models.route_preference.training.dataset import (
    DatasetBundle,
    LabeledCandidateSet,
    SplitBundle,
    build_dataset_bundle,
    iter_batches,
    split_by_candidate_set,
    tensorize_candidate_sets,
)
from urban_sidequest_ai.models.route_preference.training.dataset_repository import RoutePreferenceDatasetRepository
from urban_sidequest_ai.models.route_preference.training.eval import evaluate_model
from urban_sidequest_ai.models.route_preference.training.losses import LossConfig, compute_losses
from urban_sidequest_ai.models.route_preference.training.model import (
    RoutePreferenceModel,
    RoutePreferenceModelConfig,
)
from urban_sidequest_ai.models.route_preference.training.object_storage import ObjectStorageClient, load_object_storage_config
from urban_sidequest_ai.models.route_preference.training.schema import (
    FeatureSpec,
    InvalidJudgmentPolicy,
)
from urban_sidequest_ai.models.route_preference.training.train import (
    TRAIN_CONFIG,
    TrainingRuntimeConfig,
    _build_scheduler,
    _compute_goodness_pos_weight,
    _compute_reason_pos_weight,
    _metric_higher_is_better,
    _model_config_from_feature_spec,
)


DEFAULT_OUTPUT_PATH = (
    Path(__file__).resolve().parent
    / "reports"
    / "lambda_goodness_sweep_metrics.json"
)


# 扫描的 lambda_goodness 候选：0.6 是当前基线，0.3 是怀疑的甜点，0.0 验证 goodness 是否纯负作用。
DEFAULT_LAMBDA_GOODNESS_GRID = (0.0, 0.3, 0.6)
# 训练 seed 复用 k_judgment_model_ablation 的 seed 集，结论可直接对照。
DEFAULT_TRAIN_SEEDS = (23, 29, 31)
# split seed 固定，保证三个 lambda 在相同 split 上对比，消除 split 抖动。
DEFAULT_SPLIT_SEED = 23


@dataclass(frozen=True)
class SweepRunConfig:
    feature_schema_version: str | None
    lambda_goodness_grid: tuple[float, ...]
    train_seeds: tuple[int, ...]
    split_seed: int
    epochs: int
    output: Path
    device: str
    skip_invalid_judgments: bool


@dataclass(frozen=True)
class RunSummary:
    lambda_goodness: float
    train_seed: int
    split_seed: int
    groups: int
    train_groups: int
    valid_groups: int
    test_groups: int
    best_epoch: int
    best_own_valid_metric: float
    metrics: dict[str, float]


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    config = SweepRunConfig(
        feature_schema_version=args.feature_schema_version,
        lambda_goodness_grid=tuple(args.lambda_goodness),
        train_seeds=tuple(args.train_seed),
        split_seed=args.split_seed,
        epochs=args.epochs,
        output=args.output,
        device=args.device,
        skip_invalid_judgments=args.skip_invalid_judgments,
    )
    payload = run_sweep(config)
    config.output.parent.mkdir(parents=True, exist_ok=True)
    config.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"写入 lambda_goodness 扫描指标: {config.output}")
    return 0


def run_sweep(config: SweepRunConfig) -> dict[str, Any]:
    bundle = _load_bundle(config)
    model_config = _model_config_from_feature_spec(bundle.feature_spec, TRAIN_CONFIG)
    device = torch.device(config.device)

    runs: list[RunSummary] = []
    for lambda_goodness in config.lambda_goodness_grid:
        for train_seed in config.train_seeds:
            run_config = replace(
                TRAIN_CONFIG,
                seed=train_seed,
                split_seed=config.split_seed,
                lambda_goodness=lambda_goodness,
                epochs=config.epochs,
                output_dir=TRAIN_CONFIG.output_dir
                / "lambda_goodness_sweep"
                / f"lambda-{lambda_goodness}"
                / f"seed-{train_seed}",
                skip_onnx=True,
                device=config.device,
            )
            loss_config = LossConfig(
                beta=run_config.beta,
                lambda_goodness=run_config.lambda_goodness,
                lambda_reason=run_config.lambda_reason,
            )
            runs.append(
                _train_one(
                    bundle=bundle,
                    model_config=model_config,
                    loss_config=loss_config,
                    run_config=run_config,
                    device=device,
                )
            )
    return {
        "settings": {
            "feature_schema_version": config.feature_schema_version,
            "lambda_goodness_grid": list(config.lambda_goodness_grid),
            "train_seeds": list(config.train_seeds),
            "split_seed": config.split_seed,
            "epochs": config.epochs,
            "device": config.device,
            "candidate_set_filter": "全部 TRAIN_READY 样本聚合多 judge（与主训练同口径）",
            "early_stopping_metric": TRAIN_CONFIG.best_metric,
            "early_stopping_target": "固定 split 上各 lambda 自己的 valid 标签",
            "purpose": "验证 lambda_goodness=0.6 是否在用排序换 goodness（negative transfer）",
            "controlled_variables": "除 lambda_goodness 外，beta/lambda_reason/dropout/lr/epochs/split 完全对齐主训练",
        },
        "runs": [asdict(run) for run in runs],
        "summary": _summarize_runs(runs),
    }


def _train_one(
    bundle: DatasetBundle,
    model_config: RoutePreferenceModelConfig,
    loss_config: LossConfig,
    run_config: TrainingRuntimeConfig,
    device: torch.device,
) -> RunSummary:
    torch.manual_seed(run_config.seed)
    splits = split_by_candidate_set(bundle.groups, seed=run_config.split_seed)
    if not splits.train:
        raise ValueError(f"lambda={run_config.lambda_goodness} seed={run_config.seed} 训练集为空")

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

        valid_metrics = evaluate_model(
            model,
            splits.valid,
            effective_loss_config,
            run_config.batch_candidate_sets,
            device,
            run_config.seed,
            "valid",
        )
        current = valid_metrics.get(best_metric_key)
        if current is None:
            current = valid_metrics.get("valid/ndcg@3", 0.0)

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
            "valid",
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
            "test",
        )
    )
    metrics.update(
        _evaluate_pair_gap_metrics(
            model,
            splits.valid,
            run_config.batch_candidate_sets,
            device,
            "valid",
        )
    )
    metrics.update(
        _evaluate_pair_gap_metrics(
            model,
            splits.test,
            run_config.batch_candidate_sets,
            device,
            "test",
        )
    )

    return RunSummary(
        lambda_goodness=run_config.lambda_goodness,
        train_seed=run_config.seed,
        split_seed=run_config.split_seed,
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


def _load_bundle(config: SweepRunConfig) -> DatasetBundle:
    object_storage_config = load_object_storage_config()
    repository = RoutePreferenceDatasetRepository(ObjectStorageClient(object_storage_config), object_storage_config)
    sample_rows = repository.fetch_training_samples(config.feature_schema_version)
    judgment_rows = repository.fetch_completed_judgments({row.candidate_set_id for row in sample_rows})
    policy = InvalidJudgmentPolicy.SKIP if config.skip_invalid_judgments else InvalidJudgmentPolicy.FAIL
    bundle = build_dataset_bundle(sample_rows, judgment_rows, policy)
    print(f"加载完成: groups={len(bundle.groups)} skipped={len(bundle.skipped_judgments)}")
    return bundle


def _summarize_runs(runs: list[RunSummary]) -> dict[str, dict[str, dict[str, float]]]:
    # 重点关注：排序主指标（ndcg@3 / weightedPairwise）、gap=1 瓶颈、goodness 是否随 lambda 同步变化。
    metric_keys = [
        "test/ndcg@3",
        "test/top1Accuracy",
        "test/top2HitRate",
        "test/pairwiseAccuracy",
        "test/weightedPairwiseAccuracy",
        "test/pairAccuracy/gap1",
        "test/pairAccuracy/gap2",
        "test/pairAccuracy/gap3",
        "test/pairAccuracy/gap>=4",
        "test/weightedPairAccuracy/gap1",
        "test/weightedPairAccuracy/gap2",
        "valid/ndcg@3",
        "valid/weightedPairwiseAccuracy",
        "valid/goodnessAuc",
        "valid/goodnessAccuracy@0.5",
        "test/goodnessAuc",
        "test/goodnessAccuracy@0.5",
        "test/goodnessPrAuc",
    ]
    grouped: dict[str, list[RunSummary]] = defaultdict(list)
    for run in runs:
        grouped[f"lambda_goodness={run.lambda_goodness}"].append(run)
    summary: dict[str, dict[str, dict[str, float]]] = {}
    for key_prefix, group_runs in grouped.items():
        summary[key_prefix] = {}
        for key in metric_keys:
            values = [run.metrics[key] for run in group_runs if key in run.metrics]
            if values:
                summary[key_prefix][key] = {
                    "mean": statistics.fmean(values),
                    "std": statistics.stdev(values) if len(values) > 1 else 0.0,
                    "n": float(len(values)),
                }
    return summary


def _safe_div(numerator: float, denominator: float) -> float:
    return 0.0 if denominator == 0 else numerator / denominator


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="扫描 lambda_goodness，验证 goodness 头是否在抢占共享 encoder（negative transfer）。",
    )
    parser.add_argument(
        "--feature-schema-version",
        default=TRAIN_CONFIG.feature_schema_version,
        help="训练样本 feature_schema_version；默认复用训练配置。",
    )
    parser.add_argument(
        "--lambda-goodness",
        type=float,
        nargs="+",
        default=list(DEFAULT_LAMBDA_GOODNESS_GRID),
        help="扫描的 lambda_goodness 候选；默认 0.0 0.3 0.6。",
    )
    parser.add_argument(
        "--train-seed",
        type=int,
        nargs="+",
        default=list(DEFAULT_TRAIN_SEEDS),
        help="训练随机种子列表。",
    )
    parser.add_argument(
        "--split-seed",
        type=int,
        default=DEFAULT_SPLIT_SEED,
        help="固定 split seed，保证不同 lambda 在相同 split 上对比。",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=TRAIN_CONFIG.epochs,
        help="每次训练最大 epoch。",
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
