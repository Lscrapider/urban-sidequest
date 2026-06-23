from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import logging
import sys
from typing import Any

import torch
from torch.optim import AdamW

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
from .repository import JudgmentRow, RoutePreferenceTrainingRepository, TrainingSampleRow
from .schema import (
    DEFAULT_BATCH_CANDIDATE_SETS,
    DEFAULT_DROPOUT,
    DEFAULT_EPOCHS,
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


def main(argv: list[str] | None = None) -> int:
    parser = build_arg_parser()
    if argv is None and len(sys.argv) == 1:
        project_tmp = Path(__file__).resolve().parents[6] / "tmp"
        # 本地直接运行时改这里：保留一个 local_argv 赋值，另一个注释掉。
        # 训练配置：从 PostgreSQL 读取真实样本。
        local_argv = [
            "train",
            "--feature-schema-version",
            "route_pref_v4",
            "--output-dir",
            str(project_tmp / "route-pref-training-output"),
            "--epochs",
            str(DEFAULT_EPOCHS),
            "--batch-candidate-sets",
            str(DEFAULT_BATCH_CANDIDATE_SETS),
            "--lr",
            str(DEFAULT_LR),
            "--weight-decay",
            str(DEFAULT_WEIGHT_DECAY),
            "--grad-clip-norm",
            str(DEFAULT_GRAD_CLIP_NORM),
            "--device",
            "cpu",
            "--config",
            str(Path(__file__).with_name("config.json")),
            # "--skip-invalid-judgments",
            "--skip-onnx",
        ]

        # 自检配置：不连数据库，使用合成数据验证训练、导出和画图流程。
        # local_argv = [
        #     "self-check",
        #     "--output-dir",
        #     str(project_tmp / "route-pref-training-self-check"),
        #     "--skip-onnx",
        #     "--device",
        #     "cpu",
        # ]
        argv = local_argv
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.command == "train":
        return run_train(args)
    if args.command == "self-check":
        return run_self_check(args)
    parser.print_help()
    return 2


def build_arg_parser() -> ArgumentParser:
    parser = ArgumentParser(description="训练路线偏好排序模型。")
    subparsers = parser.add_subparsers(dest="command")
    train_parser = subparsers.add_parser("train", help="从 PostgreSQL 读取样本并训练模型。")
    train_parser.add_argument("--config", type=Path, help="JSON 配置文件，database/db 节点或直接 DB 字段。")
    train_parser.add_argument("--feature-schema-version", help="只训练指定 feature_schema_version。")
    train_parser.add_argument("--output-dir", type=Path, required=True, help="训练产物输出目录。")
    train_parser.add_argument("--epochs", type=int, default=DEFAULT_EPOCHS)
    train_parser.add_argument("--batch-candidate-sets", type=int, default=DEFAULT_BATCH_CANDIDATE_SETS)
    train_parser.add_argument("--lr", type=float, default=DEFAULT_LR)
    train_parser.add_argument("--weight-decay", type=float, default=DEFAULT_WEIGHT_DECAY)
    train_parser.add_argument("--grad-clip-norm", type=float, default=DEFAULT_GRAD_CLIP_NORM)
    train_parser.add_argument("--hidden-dim", type=int, default=DEFAULT_HIDDEN_DIM)
    train_parser.add_argument("--reason-hidden-dim", type=int, default=DEFAULT_REASON_HIDDEN_DIM)
    train_parser.add_argument("--dropout", type=float, default=DEFAULT_DROPOUT)
    train_parser.add_argument("--beta", type=float, default=DEFAULT_RANKING_BETA)
    train_parser.add_argument("--lambda-goodness", type=float, default=DEFAULT_LAMBDA_GOODNESS)
    train_parser.add_argument("--lambda-reason", type=float, default=DEFAULT_LAMBDA_REASON)
    train_parser.add_argument("--seed", type=int, default=DEFAULT_RANDOM_SEED)
    train_parser.add_argument("--skip-invalid-judgments", action="store_true", help="遇到非法 reason code 等 judgment 时跳过并记录。")
    train_parser.add_argument("--skip-onnx", action="store_true", help="只导出 PyTorch state，不导出 ONNX。")
    train_parser.add_argument("--device", default="cpu", help="cpu/cuda/mps。")

    self_check_parser = subparsers.add_parser("self-check", help="不连数据库，使用合成数据跑一轮训练和绘图。")
    self_check_parser.add_argument("--output-dir", type=Path, required=True)
    self_check_parser.add_argument("--skip-onnx", action="store_true")
    self_check_parser.add_argument("--device", default="cpu")
    return parser


def run_train(args) -> int:
    torch.manual_seed(args.seed)
    device = torch.device(args.device)
    db_config = load_database_config(args.config)
    with connect(db_config) as connection:
        repository = RoutePreferenceTrainingRepository(connection)
        sample_rows = repository.fetch_training_samples(args.feature_schema_version)
        judgment_rows = repository.fetch_completed_judgments({row.candidate_set_id for row in sample_rows})
    policy = InvalidJudgmentPolicy.SKIP if args.skip_invalid_judgments else InvalidJudgmentPolicy.FAIL
    bundle = build_dataset_bundle(sample_rows, judgment_rows, policy)
    loss_config = LossConfig(beta=args.beta, lambda_goodness=args.lambda_goodness, lambda_reason=args.lambda_reason)
    model_config = RoutePreferenceModelConfig.from_feature_spec(bundle.feature_spec)
    model_config = RoutePreferenceModelConfig(
        **{
            **model_config.__dict__,
            "hidden_dim": args.hidden_dim,
            "dropout": args.dropout,
            "reason_hidden_dim": args.reason_hidden_dim,
        }
    )
    return train_and_export(bundle, model_config, loss_config, args, device)


def run_self_check(args) -> int:
    torch.manual_seed(DEFAULT_RANDOM_SEED)
    device = torch.device(args.device)
    bundle = synthetic_bundle()
    loss_config = LossConfig()
    model_config = RoutePreferenceModelConfig.from_feature_spec(bundle.feature_spec)
    args.epochs = 2
    args.batch_candidate_sets = 2
    args.lr = DEFAULT_LR
    args.weight_decay = DEFAULT_WEIGHT_DECAY
    args.grad_clip_norm = DEFAULT_GRAD_CLIP_NORM
    return train_and_export(bundle, model_config, loss_config, args, device)


def train_and_export(
    bundle: DatasetBundle,
    model_config: RoutePreferenceModelConfig,
    loss_config: LossConfig,
    args,
    device: torch.device,
) -> int:
    splits = split_by_candidate_set(bundle.groups, seed=getattr(args, "seed", DEFAULT_RANDOM_SEED))
    if not splits.train:
        raise ValueError("训练集为空，无法训练")
    model = RoutePreferenceModel(model_config).to(device)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    output_dir: Path = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    history_path = output_dir / HISTORY_FILENAME
    if history_path.exists():
        history_path.unlink()

    for epoch in range(1, args.epochs + 1):
        model.train()
        train_loss_totals: dict[str, float] = {}
        batch_count = 0
        for batch in iter_batches(
            splits.train,
            args.batch_candidate_sets,
            shuffle=True,
            seed=getattr(args, "seed", DEFAULT_RANDOM_SEED) + epoch,
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
            torch.nn.utils.clip_grad_norm_(model.parameters(), args.grad_clip_norm)
            optimizer.step()
            for key, value in losses.detached_metrics().items():
                train_loss_totals[key] = train_loss_totals.get(key, 0.0) + value
            batch_count += 1
        record: dict[str, Any] = {"epoch": epoch}
        for key, value in train_loss_totals.items():
            record[f"train/{key}"] = value / max(batch_count, 1)
        record.update(
            evaluate_model(
                model,
                splits.valid,
                loss_config,
                args.batch_candidate_sets,
                device,
                getattr(args, "seed", DEFAULT_RANDOM_SEED),
                "valid",
            )
        )
        append_history(history_path, record)
        LOGGER.info(
            "epoch=%s trainLoss=%.6f validPairwise=%.4f validNdcg@3=%.4f",
            epoch,
            record.get("train/loss/total", 0.0),
            record.get("valid/pairwiseAccuracy", 0.0),
            record.get("valid/ndcg@3", 0.0),
        )

    final_metrics = {}
    final_metrics.update(
        evaluate_model(
            model,
            splits.valid,
            loss_config,
            args.batch_candidate_sets,
            device,
            getattr(args, "seed", DEFAULT_RANDOM_SEED),
            "valid",
        )
    )
    final_metrics.update(
        evaluate_model(
            model,
            splits.test,
            loss_config,
            args.batch_candidate_sets,
            device,
            getattr(args, "seed", DEFAULT_RANDOM_SEED),
            "test",
        )
    )
    export_training_artifacts(
        model,
        bundle.feature_spec,
        model_config,
        final_metrics,
        ExportConfig(output_dir=output_dir, export_onnx=not args.skip_onnx),
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
                    reason_labels=tuple(labels),
                    reason_mask=1.0 if route_code in reason_codes else 0.0,
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
