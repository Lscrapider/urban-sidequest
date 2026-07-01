from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import json

import torch

from .model import RoutePreferenceModel, RoutePreferenceModelConfig
from .schema import (
    DEFAULT_GOOD_ROUTE_THRESHOLD,
    DEFAULT_GOODNESS_TEMPERATURE,
    DEFAULT_HIGH_ISSUE_THRESHOLD,
    DEFAULT_ISSUE_THRESHOLD,
    GOOD_ROUTE_THRESHOLD_NAME,
    HIGH_ISSUE_THRESHOLD_NAME,
    ISSUE_THRESHOLD_NAME,
    REASON_CODES,
    REASON_CODES_VERSION,
    FeatureSpec,
)


MODEL_STATE_FILENAME = "route-preference.pt"
ONNX_FILENAME = "route-preference.onnx"
FEATURE_SCHEMA_FILENAME = "feature_schema.json"
MODEL_CARD_FILENAME = "model_card.json"
REASON_CODES_FILENAME = "reason_codes.json"


@dataclass(frozen=True)
class ExportConfig:
    output_dir: Path
    export_onnx: bool = True


def export_training_artifacts(
    model: RoutePreferenceModel,
    feature_spec: FeatureSpec,
    model_config: RoutePreferenceModelConfig,
    metrics: dict[str, float],
    export_config: ExportConfig,
) -> None:
    export_config.output_dir.mkdir(parents=True, exist_ok=True)
    good_route_threshold = DEFAULT_GOOD_ROUTE_THRESHOLD
    goodness_temperature = DEFAULT_GOODNESS_TEMPERATURE
    torch.save(
        {
            "stateDict": model.state_dict(),
            "modelConfig": model_config.to_json_dict(),
            "featureSchema": feature_spec.to_json_dict(),
            "reasonCodes": list(REASON_CODES),
        },
        export_config.output_dir / MODEL_STATE_FILENAME,
    )
    _write_json(export_config.output_dir / FEATURE_SCHEMA_FILENAME, feature_spec.to_json_dict())
    _write_json(
        export_config.output_dir / REASON_CODES_FILENAME,
        {
            "version": REASON_CODES_VERSION,
            "reasonCodes": list(REASON_CODES),
        },
    )
    _write_json(
        export_config.output_dir / MODEL_CARD_FILENAME,
        {
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "model": "RoutePreferenceModel",
            "modelConfig": model_config.to_json_dict(),
            "featureSchemaVersion": feature_spec.feature_schema_version,
            "reasonCodesVersion": REASON_CODES_VERSION,
            "thresholds": {
                GOOD_ROUTE_THRESHOLD_NAME: good_route_threshold,
                HIGH_ISSUE_THRESHOLD_NAME: DEFAULT_HIGH_ISSUE_THRESHOLD,
                ISSUE_THRESHOLD_NAME: DEFAULT_ISSUE_THRESHOLD,
            },
            "calibration": {
                "goodnessTemperature": goodness_temperature,
                "goodnessThresholdSource": "fixed calibrated probability default; tune by business false-reject tolerance",
                "validFittedGoodnessThreshold": metrics.get("valid/goodnessBestThreshold"),
                "validFittedGoodnessTemperature": metrics.get("valid/goodnessCalibrationTemperature"),
                "reasonThresholdSource": "default; reason threshold calibration is a future follow-up",
            },
            "metrics": metrics,
            "notes": [
                "线上 goodness 概率使用 sigmoid(routeGoodnessLogit / goodnessTemperature)，GOOD_ROUTE_THRESHOLD=0.5 是校准后概率上的默认业务阈值。",
                "valid 拟合出的 goodness 阈值只作为诊断记录，单次训练抖动较大，不作为线上自动阈值。",
                "reason 阈值当前使用默认值；后续可复用同样 valid-only 流程标定 ISSUE_THRESHOLD。",
                "reason 指标只在带 reasonCodes 的 rejected route 子集上计算。",
            ],
        },
    )
    if export_config.export_onnx:
        export_onnx_model(model, model_config, export_config.output_dir / ONNX_FILENAME)


def export_onnx_model(model: RoutePreferenceModel, model_config: RoutePreferenceModelConfig, output_path: Path) -> None:
    model.eval()
    device = next(model.parameters()).device
    dummy_inputs = (
        torch.zeros(1, model_config.max_stops, model_config.stop_dim, dtype=torch.float32, device=device),
        torch.zeros(1, model_config.max_segments, model_config.segment_dim, dtype=torch.float32, device=device),
        torch.zeros(1, model_config.route_derived_dim, dtype=torch.float32, device=device),
        torch.zeros(1, model_config.context_cross_dim, dtype=torch.float32, device=device),
        torch.zeros(1, model_config.intra_set_dim, dtype=torch.float32, device=device),
    )
    torch.onnx.export(
        _OnnxExportWrapper(model),
        dummy_inputs,
        output_path,
        input_names=["stopMatrix", "segmentMatrix", "routeDerivedVector", "contextCrossVector", "intraSetVector"],
        output_names=["routePreferenceScore", "routeGoodnessLogit", "reasonCodeLogits"],
        dynamic_axes={
            "stopMatrix": {0: "batch"},
            "segmentMatrix": {0: "batch"},
            "routeDerivedVector": {0: "batch"},
            "contextCrossVector": {0: "batch"},
            "intraSetVector": {0: "batch"},
            "routePreferenceScore": {0: "batch"},
            "routeGoodnessLogit": {0: "batch"},
            "reasonCodeLogits": {0: "batch"},
        },
        opset_version=17,
        dynamo=False,
    )


class _OnnxExportWrapper(torch.nn.Module):
    def __init__(self, model: RoutePreferenceModel):
        super().__init__()
        self.model = model

    def forward(
        self,
        stop_matrix: torch.Tensor,
        segment_matrix: torch.Tensor,
        route_derived_vector: torch.Tensor,
        context_cross_vector: torch.Tensor,
        intra_set_vector: torch.Tensor,
    ) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        output = self.model(stop_matrix, segment_matrix, route_derived_vector, context_cross_vector, intra_set_vector)
        return output.route_preference_score, output.route_goodness_logit, output.reason_code_logits


def _write_json(path: Path, payload: dict) -> None:
    with path.open("w", encoding="utf-8") as file:
        json.dump(payload, file, ensure_ascii=False, indent=2, sort_keys=True)
        file.write("\n")
