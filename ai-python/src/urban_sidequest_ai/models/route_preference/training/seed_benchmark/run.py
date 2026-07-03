from __future__ import annotations

import argparse
import csv
from dataclasses import asdict, dataclass, replace
import json
from pathlib import Path
import statistics
import sys
from typing import Any

import torch
from torch.optim import AdamW
from torch.optim.lr_scheduler import ReduceLROnPlateau

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[5]))
    __package__ = "urban_sidequest_ai.models.route_preference.training.seed_benchmark"

from urban_sidequest_ai.models.route_preference.training.dataset import (
    DatasetBundle,
    build_dataset_bundle,
    iter_batches,
    split_by_candidate_set,
)
from urban_sidequest_ai.models.route_preference.training.db import connect, load_database_config
from urban_sidequest_ai.models.route_preference.training.eval import evaluate_model, fit_goodness_calibration
from urban_sidequest_ai.models.route_preference.training.losses import LossConfig, compute_losses
from urban_sidequest_ai.models.route_preference.training.model import (
    RoutePreferenceModel,
    RoutePreferenceModelConfig,
)
from urban_sidequest_ai.models.route_preference.training.repository import RoutePreferenceTrainingRepository
from urban_sidequest_ai.models.route_preference.training.schema import InvalidJudgmentPolicy
from urban_sidequest_ai.models.route_preference.training.train import (
    PROJECT_ROOT,
    TRAIN_CONFIG,
    TrainingRuntimeConfig,
    _build_scheduler,
    _compute_goodness_pos_weight,
    _compute_reason_pos_weight,
    _metric_higher_is_better,
    _model_config_from_feature_spec,
)


DEFAULT_SPLIT_SEEDS = (13, 17, 19)
DEFAULT_TRAIN_SEEDS = (23, 29, 31)
DEFAULT_OUTPUT_PATH = (
    Path(__file__).resolve().parent
    / "reports"
    / "seed_benchmark_metrics.json"
)
DEFAULT_RUN_OUTPUT_DIR = PROJECT_ROOT / "tmp" / "route-pref-training-seed-benchmark"
SUMMARY_METRIC_KEYS = (
    "test/ndcg@3",
    "test/weightedPairwiseAccuracy",
    "test/pairwiseAccuracy",
    "test/top1Accuracy",
    "test/top2HitRate",
    "test/goodnessAuc",
    "test/goodnessPrAuc",
    "test/goodnessAccuracy@0.5",
    "test/goodnessAccuracy@bestThreshold",
    "test/goodnessCalibratedAccuracy@0.5",
    "test/goodnessCalibratedAccuracy@bestThreshold",
    "test/goodnessEce@calibrated",
    "valid/goodnessBestThreshold",
    "valid/goodnessCalibratedBestThreshold",
    "valid/goodnessCalibrationTemperature",
    "test/reasonConditionalPerCodeAucMacro",
    "test/pairwiseAccuracy@gap1",
    "test/pairwiseAccuracy@gap2",
    "test/pairwiseAccuracy@gap3",
    "test/pairwiseAccuracy@gap4",
)


@dataclass(frozen=True)
class BenchmarkConfig:
    feature_schema_version: str | None
    split_seeds: tuple[int, ...]
    train_seeds: tuple[int, ...]
    epochs: int
    output: Path
    csv_output: Path
    run_output_dir: Path
    device: str
    skip_invalid_judgments: bool
    lambda_goodness: float | None
    dropout: float | None
    min_delta: float | None


@dataclass(frozen=True)
class RunSummary:
    split_seed: int
    train_seed: int
    output_dir: str
    groups: int
    train_groups: int
    valid_groups: int
    test_groups: int
    best_epoch: int
    best_valid_metric: float
    metrics: dict[str, float]


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    output = args.output
    csv_output = args.csv_output or output.with_suffix(".csv")
    config = BenchmarkConfig(
        feature_schema_version=args.feature_schema_version,
        split_seeds=tuple(args.split_seed),
        train_seeds=tuple(args.train_seed),
        epochs=args.epochs,
        output=output,
        csv_output=csv_output,
        run_output_dir=args.run_output_dir,
        device=args.device,
        skip_invalid_judgments=args.skip_invalid_judgments,
        lambda_goodness=args.lambda_goodness,
        dropout=args.dropout,
        min_delta=args.min_delta,
    )
    payload = run_benchmark(config)
    config.output.parent.mkdir(parents=True, exist_ok=True)
    config.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    _write_summary_csv(config.csv_output, payload["summary"])
    print(_format_summary_table(payload["summary"]))
    print(f"写入 seed benchmark JSON: {config.output}")
    print(f"写入 seed benchmark CSV: {config.csv_output}")
    return 0


def run_benchmark(config: BenchmarkConfig) -> dict[str, Any]:
    bundle = _load_bundle(config)
    device = torch.device(config.device)
    runs: list[RunSummary] = []
    total_runs = len(config.split_seeds) * len(config.train_seeds)
    run_index = 0
    for split_seed in config.split_seeds:
        for train_seed in config.train_seeds:
            run_index += 1
            run_output_dir = config.run_output_dir / f"split-{split_seed}" / f"train-{train_seed}"
            print(f"[{run_index}/{total_runs}] split_seed={split_seed} train_seed={train_seed} output={run_output_dir}")
            run_config = replace(
                TRAIN_CONFIG,
                seed=train_seed,
                split_seed=split_seed,
                epochs=config.epochs,
                output_dir=run_output_dir,
                skip_onnx=True,
                device=config.device,
            )
            if config.lambda_goodness is not None:
                run_config = replace(run_config, lambda_goodness=config.lambda_goodness)
            if config.dropout is not None:
                run_config = replace(run_config, dropout=config.dropout)
            if config.min_delta is not None:
                run_config = replace(run_config, min_delta=config.min_delta)
            # dropout 在 model_config 里，必须用覆盖后的 run_config 重建，不能循环外用 TRAIN_CONFIG 固化。
            model_config = _model_config_from_feature_spec(bundle.feature_spec, run_config)
            loss_config = LossConfig(
                beta=run_config.beta,
                lambda_goodness=run_config.lambda_goodness,
                lambda_reason=run_config.lambda_reason,
            )
            run = _train_one(
                bundle=bundle,
                model_config=model_config,
                loss_config=loss_config,
                run_config=run_config,
                device=device,
            )
            runs.append(run)
            run_output_dir.mkdir(parents=True, exist_ok=True)
            (run_output_dir / "run_metrics.json").write_text(
                json.dumps(asdict(run), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    return {
        "settings": {
            "feature_schema_version": config.feature_schema_version,
            "split_seeds": list(config.split_seeds),
            "train_seeds": list(config.train_seeds),
            "epochs": config.epochs,
            "lambda_goodness": TRAIN_CONFIG.lambda_goodness if config.lambda_goodness is None else config.lambda_goodness,
            "lambda_goodness_source": "TRAIN_CONFIG" if config.lambda_goodness is None else "CLI override",
            "dropout": TRAIN_CONFIG.dropout if config.dropout is None else config.dropout,
            "dropout_source": "TRAIN_CONFIG" if config.dropout is None else "CLI override",
            "min_delta": TRAIN_CONFIG.min_delta if config.min_delta is None else config.min_delta,
            "min_delta_source": "TRAIN_CONFIG" if config.min_delta is None else "CLI override",
            "device": config.device,
            "run_output_dir": str(config.run_output_dir),
            "candidate_set_filter": "全部 TRAIN_READY 样本聚合多 judge（与主训练同口径）",
            "early_stopping_metric": TRAIN_CONFIG.best_metric,
            "early_stopping_target": "每个 split_seed 自己的 valid/ndcg@3",
            "comparison_rule": "任何改动前后必须固定同一组 split_seed 和 train_seed，对比 test mean±std。",
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
    if run_config.split_seed is None:
        raise ValueError("seed benchmark 必须显式设置 split_seed")
    torch.manual_seed(run_config.seed)
    splits = split_by_candidate_set(bundle.groups, seed=run_config.split_seed)
    if not splits.train:
        raise ValueError(f"split_seed={run_config.split_seed} train_seed={run_config.seed} 训练集为空")

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
    plateau_monitor = (best_metric_key or "valid/loss/total") if isinstance(scheduler, ReduceLROnPlateau) else None
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

        if isinstance(scheduler, ReduceLROnPlateau):
            monitored = valid_metrics.get(plateau_monitor) if plateau_monitor else None
            if monitored is not None:
                scheduler.step(monitored)
        elif scheduler is not None:
            scheduler.step()

    if best_state is not None:
        model.load_state_dict(best_state)

    goodness_calibration = fit_goodness_calibration(
        model,
        splits.valid,
        run_config.batch_candidate_sets,
        device,
        run_config.seed,
    )
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
            goodness_calibration,
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
            goodness_calibration,
        )
    )
    return RunSummary(
        split_seed=run_config.split_seed,
        train_seed=run_config.seed,
        output_dir=str(run_config.output_dir),
        groups=len(bundle.groups),
        train_groups=len(splits.train),
        valid_groups=len(splits.valid),
        test_groups=len(splits.test),
        best_epoch=best_epoch,
        best_valid_metric=best_score if best_score is not None else 0.0,
        metrics=metrics,
    )


def _load_bundle(config: BenchmarkConfig) -> DatasetBundle:
    db_config = load_database_config()
    with connect(db_config) as connection:
        repository = RoutePreferenceTrainingRepository(connection)
        sample_rows = repository.fetch_training_samples(config.feature_schema_version)
        judgment_rows = repository.fetch_completed_judgments({row.candidate_set_id for row in sample_rows})
    policy = InvalidJudgmentPolicy.SKIP if config.skip_invalid_judgments else InvalidJudgmentPolicy.FAIL
    bundle = build_dataset_bundle(sample_rows, judgment_rows, policy)
    print(f"加载完成: groups={len(bundle.groups)} skipped={len(bundle.skipped_judgments)}")
    return bundle


def _summarize_runs(runs: list[RunSummary]) -> dict[str, dict[str, float]]:
    summary: dict[str, dict[str, float]] = {}
    for key in SUMMARY_METRIC_KEYS:
        values = [run.metrics[key] for run in runs if key in run.metrics]
        if not values:
            continue
        summary[key] = {
            "mean": statistics.fmean(values),
            "std": statistics.stdev(values) if len(values) > 1 else 0.0,
            "n": float(len(values)),
        }
    return summary


def _write_summary_csv(path: Path, summary: dict[str, dict[str, float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["metric", "mean", "std", "n", "mean±std"])
        writer.writeheader()
        for metric, stats in summary.items():
            mean = stats["mean"]
            std = stats["std"]
            writer.writerow(
                {
                    "metric": metric,
                    "mean": f"{mean:.8f}",
                    "std": f"{std:.8f}",
                    "n": int(stats["n"]),
                    "mean±std": f"{mean:.6f}±{std:.6f}",
                }
            )


def _format_summary_table(summary: dict[str, dict[str, float]]) -> str:
    rows = [("metric", "mean", "std", "mean±std", "n")]
    for metric, stats in summary.items():
        mean = stats["mean"]
        std = stats["std"]
        rows.append((metric, f"{mean:.6f}", f"{std:.6f}", f"{mean:.6f}±{std:.6f}", str(int(stats["n"]))))
    widths = [max(len(row[index]) for row in rows) for index in range(len(rows[0]))]
    lines = []
    for row_index, row in enumerate(rows):
        lines.append(" | ".join(value.ljust(widths[index]) for index, value in enumerate(row)))
        if row_index == 0:
            lines.append("-+-".join("-" * width for width in widths))
    return "\n".join(lines)


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="按固定 split_seed×train_seed 重复训练路线偏好模型，并汇总 test 指标 mean±std。",
    )
    parser.add_argument(
        "--feature-schema-version",
        default=TRAIN_CONFIG.feature_schema_version or "route_pref_v5",
        help="训练样本 feature_schema_version；默认 route_pref_v5。",
    )
    parser.add_argument(
        "--split-seed",
        type=int,
        nargs="+",
        default=list(DEFAULT_SPLIT_SEEDS),
        help="数据切分随机种子列表；默认 13 17 19。",
    )
    parser.add_argument(
        "--train-seed",
        type=int,
        nargs="+",
        default=list(DEFAULT_TRAIN_SEEDS),
        help="训练随机种子列表；默认 23 29 31。",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=TRAIN_CONFIG.epochs,
        help="每次训练最大 epoch。",
    )
    parser.add_argument(
        "--lambda-goodness",
        type=float,
        default=None,
        help="临时覆盖 lambda_goodness；默认使用 TRAIN_CONFIG.lambda_goodness。",
    )
    parser.add_argument(
        "--dropout",
        type=float,
        default=None,
        help="临时覆盖 dropout（含 model_config）；默认使用 TRAIN_CONFIG.dropout。",
    )
    parser.add_argument(
        "--min-delta",
        type=float,
        default=None,
        help="临时覆盖 early stopping min_delta；默认使用 TRAIN_CONFIG.min_delta。",
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
        "--run-output-dir",
        type=Path,
        default=DEFAULT_RUN_OUTPUT_DIR,
        help="每次运行的临时产物根目录；默认 tmp/route-pref-training-seed-benchmark。",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help="JSON 汇总输出路径。",
    )
    parser.add_argument(
        "--csv-output",
        type=Path,
        default=None,
        help="CSV 汇总输出路径；默认与 --output 同名 .csv。",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    raise SystemExit(main())
