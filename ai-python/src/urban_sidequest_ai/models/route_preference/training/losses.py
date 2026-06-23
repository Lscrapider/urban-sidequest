from __future__ import annotations

from dataclasses import dataclass

import torch
import torch.nn.functional as F

from .dataset import TensorBatch
from .model import RoutePreferenceOutput
from .schema import DEFAULT_LAMBDA_GOODNESS, DEFAULT_LAMBDA_REASON, DEFAULT_RANKING_BETA, REASON_CODES


@dataclass(frozen=True)
class LossConfig:
    beta: float = DEFAULT_RANKING_BETA
    lambda_goodness: float = DEFAULT_LAMBDA_GOODNESS
    lambda_reason: float = DEFAULT_LAMBDA_REASON


@dataclass(frozen=True)
class LossResult:
    total_loss: torch.Tensor
    ranking_loss: torch.Tensor
    goodness_loss: torch.Tensor
    reason_loss: torch.Tensor

    def detached_metrics(self) -> dict[str, float]:
        return {
            "loss/total": float(self.total_loss.detach().cpu()),
            "loss/ranking": float(self.ranking_loss.detach().cpu()),
            "loss/goodness": float(self.goodness_loss.detach().cpu()),
            "loss/reason": float(self.reason_loss.detach().cpu()),
        }


def compute_losses(output: RoutePreferenceOutput, batch: TensorBatch, config: LossConfig) -> LossResult:
    ranking_loss = _ranking_loss(output, batch, config.beta)
    goodness_loss = _goodness_loss(output, batch)
    reason_loss = _reason_loss(output, batch)
    total_loss = ranking_loss + config.lambda_goodness * goodness_loss + config.lambda_reason * reason_loss
    return LossResult(
        total_loss=total_loss,
        ranking_loss=ranking_loss,
        goodness_loss=goodness_loss,
        reason_loss=reason_loss,
    )


def _ranking_loss(output: RoutePreferenceOutput, batch: TensorBatch, beta: float) -> torch.Tensor:
    if not batch.has_pairs:
        return torch.zeros((), dtype=output.route_preference_score.dtype, device=output.route_preference_score.device)
    chosen_scores = output.route_preference_score[batch.pair_chosen_index]
    rejected_scores = output.route_preference_score[batch.pair_rejected_index]
    raw_weights = batch.pair_weight_raw
    normalized_weights = raw_weights / raw_weights.mean().clamp_min(1e-8)
    return (normalized_weights * F.softplus(-beta * (chosen_scores - rejected_scores))).mean()


def _goodness_loss(output: RoutePreferenceOutput, batch: TensorBatch) -> torch.Tensor:
    raw_loss = F.binary_cross_entropy_with_logits(
        output.route_goodness_logit,
        batch.goodness_label,
        reduction="none",
    )
    masked_loss = raw_loss * batch.goodness_mask
    return masked_loss.sum() / batch.goodness_mask.sum().clamp_min(1.0)


def _reason_loss(output: RoutePreferenceOutput, batch: TensorBatch) -> torch.Tensor:
    raw_loss = F.binary_cross_entropy_with_logits(
        output.reason_code_logits,
        batch.reason_labels,
        reduction="none",
    )
    masked_loss = raw_loss * batch.reason_mask.unsqueeze(-1)
    denominator = (batch.reason_mask.sum() * len(REASON_CODES)).clamp_min(1.0)
    return masked_loss.sum() / denominator

