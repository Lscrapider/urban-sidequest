from __future__ import annotations

import copy
from dataclasses import dataclass, replace
import logging
import math
from pathlib import Path
import sys
from typing import Any

import torch
from torch.optim import AdamW
from torch.optim.lr_scheduler import LambdaLR, ReduceLROnPlateau

if __package__ in (None, ""):
    # 允许直接执行 train.py，同时保留 python -m 的包入口。
    sys.path.insert(0, str(Path(__file__).resolve().parents[4]))
    __package__ = "urban_sidequest_ai.models.route_preference.training"

from .dataset import (
    DatasetBundle,
    LabeledCandidateSet,
    RouteInput,
    RouteTrainingItem,
    build_dataset_bundle,
    iter_batches,
    split_by_candidate_set,
)
from .db import connect, load_database_config
from .eval import evaluate_model
from .export import ExportConfig, export_training_artifacts
from .losses import LossConfig, compute_losses
from .model import RoutePreferenceModel, RoutePreferenceModelConfig
from .pairs import build_pairwise_samples
from .plots import append_history, render_history_plots
from .repository import RoutePreferenceTrainingRepository
from .schema import (
    DEFAULT_BATCH_CANDIDATE_SETS,
    DEFAULT_DROPOUT,
    DEFAULT_GRAD_CLIP_NORM,
    DEFAULT_HIDDEN_DIM,
    DEFAULT_LAMBDA_GOODNESS,
    DEFAULT_LAMBDA_REASON,
    DEFAULT_LR,
    DEFAULT_RANDOM_SEED,
    DEFAULT_RANKING_BETA,
    DEFAULT_REASON_HIDDEN_DIM,
    DEFAULT_WEIGHT_DECAY,
    InvalidJudgmentPolicy,
    REASON_CODES,
    FeatureSpec,
)


LOGGER = logging.getLogger(__name__)
HISTORY_FILENAME = "history.jsonl"
PROJECT_ROOT = Path(__file__).resolve().parents[6]


@dataclass(frozen=True)
class TrainingRuntimeConfig:
    feature_schema_version: str | None
    output_dir: Path
    epochs: int
    batch_candidate_sets: int
    lr: float
    weight_decay: float
    grad_clip_norm: float
    hidden_dim: int
    reason_hidden_dim: int
    dropout: float
    beta: float
    lambda_goodness: float
    lambda_reason: float
    seed: int
    skip_invalid_judgments: bool
    skip_onnx: bool
    device: str
    best_metric: str
    patience: int
    min_delta: float
    lr_scheduler: str
    warmup_epochs: int
    lr_min: float
    lr_plateau_factor: float
    lr_plateau_patience: int
    reason_pos_weight_cap: float
    reason_pos_weight_min_support: int
    goodness_pos_weight_cap: float


# PyCharm 运行入口：直接运行本文件即可。
# 可选值：
# - "train"：连接 PostgreSQL 读取真实样本训练。
# - "self-check"：不连接数据库，用合成样本验证训练、导出和画图流程。
RUN_MODE = "train"

# 真实训练配置。这里只放训练执行参数，不改变模型输入 X、监督 Y、输出口径或 reason code 契约。
TRAIN_CONFIG = TrainingRuntimeConfig(
    feature_schema_version="route_pref_v4",
    output_dir=PROJECT_ROOT / "tmp" / "route-pref-training-output",
    # 缩短训练上限并配合 early stopping，避免后段 train loss 继续下降但验证排序回落。
    epochs=12,
    # batch 单位是 candidate_set_id，不是单条 route。
    batch_candidate_sets=12,
    lr=8e-4,
    weight_decay=1e-3,
    grad_clip_norm=DEFAULT_GRAD_CLIP_NORM,
    hidden_dim=DEFAULT_HIDDEN_DIM,
    reason_hidden_dim=DEFAULT_REASON_HIDDEN_DIM,
    dropout=0.25,
    beta=DEFAULT_RANKING_BETA,
    # 排序是主任务；goodness 辅助头略降权，避免后期牵引共享 encoder 过拟合。
    lambda_goodness=0.60,
    # reason 只作为轻量辅助头；0.35 会明显恢复 reason，但对 ranking 主任务有拖累。
    lambda_reason=0.025,
    seed=DEFAULT_RANDOM_SEED,
    skip_invalid_judgments=False,
    skip_onnx=True,
    device="cpu",
    # 直接按主排序指标选轮，验证是否比 ranking loss 选轮更贴近线上排序目标。
    best_metric="valid/weightedPairwiseAccuracy",
    # 连续 3 轮验证主排序指标不改善则提前停止，减少后段过拟合训练。
    patience=3,
    min_delta=0.0,
    # 学习率调度默认关闭；可选 "none"、"cosine"、"plateau"。
    lr_scheduler="cosine",
    warmup_epochs=0,
    lr_min=1e-4,
    lr_plateau_factor=0.5,
    lr_plateau_patience=2,
    # 只用训练集统计 reason 正样本权重；cap 限制极端不平衡 code 的放大倍数。
    reason_pos_weight_cap=6.0,
    # 正样本太少的 reason code 不放大，避免 HIGH_ROUTE_RISK 这类低支持度噪声被高权重牵引。
    reason_pos_weight_min_support=40,
    # goodness 先不加正样本权重；若 accepted/rejected 明显失衡，再改为 3.0 试。
    goodness_pos_weight_cap=0.0,
)

# 自检配置：用于 PyCharm 里快速确认训练流程能跑通，不依赖数据库。
SELF_CHECK_CONFIG = TrainingRuntimeConfig(
    feature_schema_version="self_check",
    output_dir=PROJECT_ROOT / "tmp" / "route-pref-training-self-check",
    epochs=2,
    batch_candidate_sets=2,
    lr=DEFAULT_LR,
    weight_decay=DEFAULT_WEIGHT_DECAY,
    grad_clip_norm=DEFAULT_GRAD_CLIP_NORM,
    hidden_dim=DEFAULT_HIDDEN_DIM,
    reason_hidden_dim=DEFAULT_REASON_HIDDEN_DIM,
    dropout=DEFAULT_DROPOUT,
    beta=DEFAULT_RANKING_BETA,
    lambda_goodness=DEFAULT_LAMBDA_GOODNESS,
    lambda_reason=DEFAULT_LAMBDA_REASON,
    seed=DEFAULT_RANDOM_SEED,
    skip_invalid_judgments=False,
    skip_onnx=True,
    device="cpu",
    best_metric="",
    patience=0,
    min_delta=0.0,
    lr_scheduler="none",
    warmup_epochs=0,
    lr_min=0.0,
    lr_plateau_factor=0.5,
    lr_plateau_patience=2,
    reason_pos_weight_cap=0.0,
    reason_pos_weight_min_support=30,
    goodness_pos_weight_cap=0.0,
)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if RUN_MODE == "train":
        return run_train(TRAIN_CONFIG)
    if RUN_MODE == "self-check":
        return run_self_check(SELF_CHECK_CONFIG)
    raise ValueError(f"未知 RUN_MODE: {RUN_MODE}")


def _model_config_from_feature_spec(
    feature_spec: FeatureSpec,
    config: TrainingRuntimeConfig,
) -> RoutePreferenceModelConfig:
    base_config = RoutePreferenceModelConfig.from_feature_spec(feature_spec)
    return RoutePreferenceModelConfig(
        **{
            **base_config.__dict__,
            "hidden_dim": config.hidden_dim,
            "dropout": config.dropout,
            "reason_hidden_dim": config.reason_hidden_dim,
        }
    )


def run_train(config: TrainingRuntimeConfig) -> int:
    torch.manual_seed(config.seed)
    device = torch.device(config.device)
    db_config = load_database_config()
    with connect(db_config) as connection:
        repository = RoutePreferenceTrainingRepository(connection)
        sample_rows = repository.fetch_training_samples(config.feature_schema_version)
        judgment_rows = repository.fetch_completed_judgments({row.candidate_set_id for row in sample_rows})
    policy = InvalidJudgmentPolicy.SKIP if config.skip_invalid_judgments else InvalidJudgmentPolicy.FAIL
    bundle = build_dataset_bundle(sample_rows, judgment_rows, policy)
    loss_config = LossConfig(
        beta=config.beta,
        lambda_goodness=config.lambda_goodness,
        lambda_reason=config.lambda_reason,
    )
    model_config = _model_config_from_feature_spec(bundle.feature_spec, config)
    return train_and_export(bundle, model_config, loss_config, config, device)


def run_self_check(config: TrainingRuntimeConfig) -> int:
    torch.manual_seed(config.seed)
    device = torch.device(config.device)
    bundle = synthetic_bundle()
    loss_config = LossConfig(
        beta=config.beta,
        lambda_goodness=config.lambda_goodness,
        lambda_reason=config.lambda_reason,
    )
    model_config = _model_config_from_feature_spec(bundle.feature_spec, config)
    return train_and_export(bundle, model_config, loss_config, config, device)


def _metric_higher_is_better(metric_key: str) -> bool:
    # loss 越低越好，其余排序 / AUC / F1 指标越高越好。
    return "loss" not in metric_key


def _build_scheduler(optimizer: AdamW, config: TrainingRuntimeConfig):
    kind = config.lr_scheduler
    if kind == "none":
        return None
    if kind == "cosine":
        total_epochs = max(config.epochs, 1)
        warmup_epochs = max(config.warmup_epochs, 0)
        lr_floor_ratio = (config.lr_min / config.lr) if config.lr > 0 else 0.0

        def lr_lambda(epoch_index: int) -> float:
            # epoch_index 为 0 基的已完成 step 数。
            if warmup_epochs > 0 and epoch_index < warmup_epochs:
                return (epoch_index + 1) / warmup_epochs
            progress = (epoch_index - warmup_epochs) / max(total_epochs - warmup_epochs, 1)
            progress = min(max(progress, 0.0), 1.0)
            cosine = 0.5 * (1.0 + math.cos(math.pi * progress))
            return lr_floor_ratio + (1.0 - lr_floor_ratio) * cosine

        return LambdaLR(optimizer, lr_lambda)
    if kind == "plateau":
        monitor = config.best_metric or "valid/loss/total"
        mode = "max" if _metric_higher_is_better(monitor) else "min"
        return ReduceLROnPlateau(
            optimizer,
            mode=mode,
            factor=config.lr_plateau_factor,
            patience=config.lr_plateau_patience,
        )
    raise ValueError(f"未知 lr-scheduler: {kind}")


def _compute_reason_pos_weight(
    groups: tuple[LabeledCandidateSet, ...],
    cap: float,
    min_support: int,
) -> tuple[float, ...] | None:
    if cap <= 0:
        return None
    required_support = max(min_support, 1)
    pos_count = [0] * len(REASON_CODES)
    pos_weight = [0.0] * len(REASON_CODES)
    neg_weight = [0.0] * len(REASON_CODES)
    for group in groups:
        for item in group.items:
            if item.reason_mask:
                for index, label in enumerate(item.reason_labels):
                    if label >= 0.5:
                        pos_count[index] += 1
                        pos_weight[index] += item.reason_weight_raw
                    else:
                        neg_weight[index] += item.reason_weight_raw
    weights = []
    for index in range(len(REASON_CODES)):
        # min_support 按正样本条数判断；pos/neg 比值按聚合后的 route-level weight 计算。
        if pos_count[index] < required_support or pos_weight[index] <= 0:
            weights.append(1.0)
        else:
            weights.append(min(neg_weight[index] / pos_weight[index], cap))
    return tuple(weights)


def _compute_goodness_pos_weight(
    groups: tuple[LabeledCandidateSet, ...],
    cap: float,
) -> float | None:
    if cap <= 0:
        return None
    pos = 0.0
    neg = 0.0
    for group in groups:
        for item in group.items:
            if item.goodness_mask:
                if item.goodness_label >= 0.5:
                    pos += item.goodness_weight_raw
                else:
                    neg += item.goodness_weight_raw
    if not pos:
        return None
    return min(neg / pos, cap)


def train_and_export(
    bundle: DatasetBundle,
    model_config: RoutePreferenceModelConfig,
    loss_config: LossConfig,
    config: TrainingRuntimeConfig,
    device: torch.device,
) -> int:
    splits = split_by_candidate_set(bundle.groups, seed=config.seed)
    if not splits.train:
        raise ValueError("训练集为空，无法训练")
    if config.reason_pos_weight_cap > 0:
        reason_pos_weight = _compute_reason_pos_weight(
            splits.train,
            config.reason_pos_weight_cap,
            config.reason_pos_weight_min_support,
        )
        loss_config = replace(loss_config, reason_pos_weight=reason_pos_weight)
        LOGGER.info("reason pos_weight=%s", reason_pos_weight)
    if config.goodness_pos_weight_cap > 0:
        goodness_pos_weight = _compute_goodness_pos_weight(splits.train, config.goodness_pos_weight_cap)
        loss_config = replace(loss_config, goodness_pos_weight=goodness_pos_weight)
        LOGGER.info("goodness pos_weight=%s", goodness_pos_weight)
    model = RoutePreferenceModel(model_config).to(device)
    optimizer = AdamW(model.parameters(), lr=config.lr, weight_decay=config.weight_decay)
    scheduler = _build_scheduler(optimizer, config)

    best_metric_key = config.best_metric
    patience = max(config.patience, 0)
    min_delta = max(config.min_delta, 0.0)
    higher_is_better = _metric_higher_is_better(best_metric_key) if best_metric_key else True
    plateau_monitor = (best_metric_key or "valid/loss/total") if isinstance(scheduler, ReduceLROnPlateau) else None
    best_score: float | None = None
    best_state: dict | None = None
    best_epoch = 0
    epochs_without_improvement = 0

    output_dir = config.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    history_path = output_dir / HISTORY_FILENAME
    if history_path.exists():
        history_path.unlink()

    for epoch in range(1, config.epochs + 1):
        model.train()
        train_loss_totals: dict[str, float] = {}
        batch_count = 0
        for batch in iter_batches(
            splits.train,
            config.batch_candidate_sets,
            shuffle=True,
            seed=config.seed + epoch,
            device=device,
        ):
            optimizer.zero_grad()
            output = model(
                batch.stop_matrix,
                batch.segment_matrix,
                batch.route_derived_vector,
                batch.context_cross_vector,
            )
            losses = compute_losses(output, batch, loss_config)
            losses.total_loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), config.grad_clip_norm)
            optimizer.step()
            for key, value in losses.detached_metrics().items():
                train_loss_totals[key] = train_loss_totals.get(key, 0.0) + value
            batch_count += 1
        record: dict[str, Any] = {"epoch": epoch, "lr": optimizer.param_groups[0]["lr"]}
        for key, value in train_loss_totals.items():
            record[f"train/{key}"] = value / max(batch_count, 1)
        record.update(
            evaluate_model(
                model,
                splits.valid,
                loss_config,
                config.batch_candidate_sets,
                device,
                config.seed,
                "valid",
            )
        )
        append_history(history_path, record)
        LOGGER.info(
            "epoch=%s lr=%.2e trainLoss=%.6f validPairwise=%.4f validNdcg@3=%.4f",
            epoch,
            record.get("lr", 0.0),
            record.get("train/loss/total", 0.0),
            record.get("valid/pairwiseAccuracy", 0.0),
            record.get("valid/ndcg@3", 0.0),
        )

        # 学习率调度：plateau 监控验证指标，cosine/warmup 按轮推进。
        if isinstance(scheduler, ReduceLROnPlateau):
            monitored = record.get(plateau_monitor)
            if monitored is not None:
                scheduler.step(monitored)
        elif scheduler is not None:
            scheduler.step()

        # 选最优轮 + early stopping：仅在 best_metric 非空时生效。
        if best_metric_key:
            current = record.get(best_metric_key)
            if current is None:
                LOGGER.warning("验证记录缺少 best-metric=%s，本轮跳过最优轮判定", best_metric_key)
            else:
                if best_score is None:
                    improved = True
                elif higher_is_better:
                    improved = current > best_score + min_delta
                else:
                    improved = current < best_score - min_delta
                if improved:
                    best_score = current
                    best_state = copy.deepcopy(model.state_dict())
                    best_epoch = epoch
                    epochs_without_improvement = 0
                else:
                    epochs_without_improvement += 1
                    if patience > 0 and epochs_without_improvement >= patience:
                        LOGGER.info(
                            "触发 early stopping: epoch=%s 最优 %s=%.4f @epoch %s",
                            epoch,
                            best_metric_key,
                            best_score,
                            best_epoch,
                        )
                        break

    if best_state is not None:
        LOGGER.info("导出最优轮权重: %s=%.4f @epoch %s", best_metric_key, best_score, best_epoch)
        model.load_state_dict(best_state)

    final_metrics = {}
    final_metrics.update(
        evaluate_model(
            model,
            splits.valid,
            loss_config,
            config.batch_candidate_sets,
            device,
            config.seed,
            "valid",
        )
    )
    final_metrics.update(
        evaluate_model(
            model,
            splits.test,
            loss_config,
            config.batch_candidate_sets,
            device,
            config.seed,
            "test",
        )
    )
    export_training_artifacts(
        model,
        bundle.feature_spec,
        model_config,
        final_metrics,
        ExportConfig(output_dir=output_dir, export_onnx=not config.skip_onnx),
    )
    render_history_plots(history_path, output_dir)
    LOGGER.info(
        "训练完成: groups=%s train=%s valid=%s test=%s skippedJudgments=%s output=%s",
        len(bundle.groups),
        len(splits.train),
        len(splits.valid),
        len(splits.test),
        len(bundle.skipped_judgments),
        output_dir,
    )
    return 0


def synthetic_bundle() -> DatasetBundle:
    feature_spec = FeatureSpec(
        feature_schema_version="self_check",
        stop_feature_keys=("interestScore", "goalScore"),
        segment_feature_keys=("straightDistanceNorm", "backtrackingFlag"),
        route_derived_keys=("avgInterestScore", "totalDistanceNorm"),
        context_cross_keys=("interestFit", "budgetPressure"),
    )
    groups = []
    for index in range(4):
        candidate_set_id = f"candidate-set-{index}"
        route_codes = ["A", "B", "C"]
        inputs = {
            "A": RouteInput(((0.9, 0.8), (0.7, 0.6)), ((0.1, 0.0),), (0.85, 0.1), (0.8, 0.1)),
            "B": RouteInput(((0.5, 0.4),), ((0.3, 0.0),), (0.45, 0.3), (0.5, 0.2)),
            "C": RouteInput(((0.2, 0.1),), ((0.8, 1.0),), (0.15, 0.9), (0.2, 0.8)),
        }
        ranking = ["A", "B", "C"]
        accepted = ["A"]
        rejected = ["C"]
        reason_codes = {"C": ["HIGH_FATIGUE"]}
        items = []
        for rank, route_code in enumerate(ranking):
            is_accepted = route_code in accepted
            is_rejected = route_code in rejected
            labels = [0.0] * len(REASON_CODES)
            for reason_code in reason_codes.get(route_code, []):
                labels[REASON_CODES.index(reason_code)] = 1.0
            items.append(
                RouteTrainingItem(
                    candidate_set_id=candidate_set_id,
                    route_code=route_code,
                    route_input=inputs[route_code],
                    rank=rank,
                    is_accepted=is_accepted,
                    is_rejected=is_rejected,
                    goodness_label=1.0 if is_accepted else 0.0,
                    goodness_mask=1.0 if is_accepted or is_rejected else 0.0,
                    goodness_weight_raw=1.0 if is_accepted or is_rejected else 0.0,
                    reason_labels=tuple(labels),
                    reason_mask=1.0 if route_code in reason_codes else 0.0,
                    reason_weight_raw=1.0 if route_code in reason_codes else 0.0,
                )
            )
        pairs = build_pairwise_samples(route_codes, ranking, accepted, rejected, "LLM_SIM_USER", 0.8)
        groups.append(
            LabeledCandidateSet(
                candidate_set_id=candidate_set_id,
                judgment_id=f"judgment-{index}",
                judge_type="LLM_SIM_USER",
                judge_model="self-check",
                judge_prompt_version="self-check",
                confidence=0.8,
                items=tuple(items),
                pairs=tuple(pairs),
            )
        )
    return DatasetBundle(feature_spec=feature_spec, groups=tuple(groups), skipped_judgments=tuple())


if __name__ == "__main__":
    sys.exit(main())
