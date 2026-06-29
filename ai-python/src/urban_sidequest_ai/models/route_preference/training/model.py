from __future__ import annotations

from dataclasses import dataclass

import torch
from torch import nn

from .schema import (
    CONTEXT_CROSS_BRANCH_DIM,
    DEFAULT_DROPOUT,
    DEFAULT_HIDDEN_DIM,
    DEFAULT_REASON_HIDDEN_DIM,
    FUSION_HIDDEN_DIM,
    INTRA_SET_BRANCH_DIM,
    REASON_CODES,
    ROUTE_DERIVED_BRANCH_DIM,
    SEGMENT_BRANCH_DIM,
    STOP_BRANCH_DIM,
    FeatureSpec,
)


@dataclass(frozen=True)
class RoutePreferenceModelConfig:
    route_derived_dim: int
    context_cross_dim: int
    intra_set_dim: int
    stop_dim: int
    segment_dim: int
    max_stops: int
    max_segments: int
    hidden_dim: int = DEFAULT_HIDDEN_DIM
    dropout: float = DEFAULT_DROPOUT
    reason_hidden_dim: int = DEFAULT_REASON_HIDDEN_DIM
    reason_count: int = len(REASON_CODES)

    @classmethod
    def from_feature_spec(cls, feature_spec: FeatureSpec) -> "RoutePreferenceModelConfig":
        return cls(
            route_derived_dim=feature_spec.route_derived_dim,
            context_cross_dim=feature_spec.context_cross_dim,
            intra_set_dim=feature_spec.intra_set_dim,
            stop_dim=feature_spec.stop_dim,
            segment_dim=feature_spec.segment_dim,
            max_stops=feature_spec.max_stops,
            max_segments=feature_spec.max_segments,
        )

    def to_json_dict(self) -> dict:
        return {
            "routeDerivedDim": self.route_derived_dim,
            "contextCrossDim": self.context_cross_dim,
            "intraSetDim": self.intra_set_dim,
            "stopDim": self.stop_dim,
            "segmentDim": self.segment_dim,
            "maxStops": self.max_stops,
            "maxSegments": self.max_segments,
            "hiddenDim": self.hidden_dim,
            "dropout": self.dropout,
            "reasonHiddenDim": self.reason_hidden_dim,
            "reasonCount": self.reason_count,
        }


@dataclass(frozen=True)
class RoutePreferenceOutput:
    route_preference_score: torch.Tensor
    route_goodness_logit: torch.Tensor
    reason_code_logits: torch.Tensor


class RoutePreferenceModel(nn.Module):
    def __init__(self, config: RoutePreferenceModelConfig):
        super().__init__()
        self.config = config
        self.route_derived_encoder = _branch(config.route_derived_dim, ROUTE_DERIVED_BRANCH_DIM, config.dropout)
        self.context_cross_encoder = _branch(config.context_cross_dim, CONTEXT_CROSS_BRANCH_DIM, config.dropout)
        self.intra_set_encoder = _branch(config.intra_set_dim, INTRA_SET_BRANCH_DIM, config.dropout)
        self.stop_encoder = _branch(config.max_stops * config.stop_dim, STOP_BRANCH_DIM, config.dropout)
        self.segment_encoder = _branch(config.max_segments * config.segment_dim, SEGMENT_BRANCH_DIM, config.dropout)
        self.fusion = nn.Sequential(
            nn.Linear(
                ROUTE_DERIVED_BRANCH_DIM
                + CONTEXT_CROSS_BRANCH_DIM
                + STOP_BRANCH_DIM
                + SEGMENT_BRANCH_DIM
                + INTRA_SET_BRANCH_DIM,
                FUSION_HIDDEN_DIM,
            ),
            nn.LayerNorm(FUSION_HIDDEN_DIM),
            nn.GELU(),
            nn.Dropout(config.dropout),
            nn.Linear(FUSION_HIDDEN_DIM, config.hidden_dim),
            nn.LayerNorm(config.hidden_dim),
            nn.GELU(),
        )
        self.score_head = nn.Linear(config.hidden_dim, 1)
        self.goodness_head = nn.Linear(config.hidden_dim, 1)
        self.reason_head = nn.Sequential(
            nn.Linear(config.hidden_dim, config.reason_hidden_dim),
            nn.GELU(),
            nn.Linear(config.reason_hidden_dim, config.reason_count),
        )

    def forward(
        self,
        stop_matrix: torch.Tensor,
        segment_matrix: torch.Tensor,
        route_derived_vector: torch.Tensor,
        context_cross_vector: torch.Tensor,
        intra_set_vector: torch.Tensor | None = None,
    ) -> RoutePreferenceOutput:
        stop_flat = stop_matrix.reshape(stop_matrix.shape[0], self.config.max_stops * self.config.stop_dim)
        segment_flat = segment_matrix.reshape(segment_matrix.shape[0], self.config.max_segments * self.config.segment_dim)
        if intra_set_vector is None:
            intra_set_vector = getattr(context_cross_vector, "intra_set_vector", None)
        if intra_set_vector is None:
            intra_set_vector = torch.zeros(
                stop_matrix.shape[0],
                self.config.intra_set_dim,
                dtype=stop_matrix.dtype,
                device=stop_matrix.device,
            )
        hidden = self.encode(stop_flat, segment_flat, route_derived_vector, context_cross_vector, intra_set_vector)
        return RoutePreferenceOutput(
            route_preference_score=self.score_head(hidden).squeeze(-1),
            route_goodness_logit=self.goodness_head(hidden).squeeze(-1),
            reason_code_logits=self.reason_head(hidden),
        )

    def encode(
        self,
        stop_flat: torch.Tensor,
        segment_flat: torch.Tensor,
        route_derived_vector: torch.Tensor,
        context_cross_vector: torch.Tensor,
        intra_set_vector: torch.Tensor,
    ) -> torch.Tensor:
        parts = (
            self.route_derived_encoder(route_derived_vector),
            self.context_cross_encoder(context_cross_vector),
            self.stop_encoder(stop_flat),
            self.segment_encoder(segment_flat),
            self.intra_set_encoder(intra_set_vector),
        )
        return self.fusion(torch.cat(parts, dim=-1))


def _branch(input_dim: int, output_dim: int, dropout: float) -> nn.Sequential:
    return nn.Sequential(
        nn.Linear(input_dim, output_dim),
        nn.LayerNorm(output_dim),
        nn.GELU(),
        nn.Dropout(dropout),
    )
