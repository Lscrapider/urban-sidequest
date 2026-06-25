from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import json
import sys
from typing import Any

import torch

DEFAULT_INPUT_PATH = Path("/Users/qinzeyu/Desktop/route_preference_training_samples_202606232333.json")

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[4]))
    __package__ = "urban_sidequest_ai.models.route_preference.predict"

from ..training.db import connect, load_database_config
from ..training.export import MODEL_CARD_FILENAME, MODEL_STATE_FILENAME, REASON_CODES_FILENAME
from ..training.model import RoutePreferenceModel, RoutePreferenceModelConfig
from ..training.repository import RoutePreferenceTrainingRepository, TrainingSampleRow
from ..training.schema import (
    DEFAULT_GOOD_ROUTE_THRESHOLD,
    DEFAULT_HIGH_ISSUE_THRESHOLD,
    DEFAULT_ISSUE_THRESHOLD,
    HIGH_ISSUE_THRESHOLD_NAME,
    ISSUE_THRESHOLD_NAME,
)


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    model, model_config, feature_schema, reason_codes, thresholds = load_predictor(args.model_dir, args.device)
    route_inputs = load_route_inputs(args, feature_schema, model_config)
    predictions = predict_routes(model, model_config, feature_schema, route_inputs, reason_codes, thresholds)
    json.dump(predictions, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


def build_arg_parser() -> ArgumentParser:
    parser = ArgumentParser(description="使用 RoutePreferenceModel 对路线打分。")
    parser.add_argument(
        "--model-dir",
        type=Path,
        default=default_model_dir(),
        help="训练输出目录，包含 route-preference.pt；默认读取项目 tmp/route-pref-training-output。",
    )
    parser.add_argument(
        "--input",
        type=Path,
        help=f"routeInput JSON 文件；可为单条对象或数组。不传时默认读取 {DEFAULT_INPUT_PATH}。",
    )
    parser.add_argument("--candidate-set-id", help="从数据库读取该 candidate_set_id 下的路线样本预测。")
    parser.add_argument("--feature-schema-version", help="读取数据库样本时限定 feature_schema_version。")
    parser.add_argument("--device", default="cpu", help="cpu/cuda/mps。")
    parser.add_argument("--limit", type=int, help="只预测前 N 条路线，方便本地预览。")
    return parser


def default_model_dir() -> Path:
    return Path(__file__).resolve().parents[6] / "tmp" / "route-pref-training-output"


def load_predictor(
    model_dir: Path,
    device_name: str,
) -> tuple[RoutePreferenceModel, RoutePreferenceModelConfig, dict[str, Any], list[str], dict[str, float]]:
    device = torch.device(device_name)
    checkpoint = torch.load(model_dir / MODEL_STATE_FILENAME, map_location=device, weights_only=True)
    model_config = _model_config_from_json(checkpoint["modelConfig"])
    model = RoutePreferenceModel(model_config).to(device)
    model.load_state_dict(checkpoint["stateDict"])
    model.eval()

    feature_schema = checkpoint.get("featureSchema") or _read_json(model_dir / "feature_schema.json")
    reason_payload = _read_json(model_dir / REASON_CODES_FILENAME)
    model_card = _read_json(model_dir / MODEL_CARD_FILENAME)
    thresholds = {
        "goodRouteThreshold": float(
            model_card.get("thresholds", {}).get("GOOD_ROUTE_THRESHOLD", DEFAULT_GOOD_ROUTE_THRESHOLD)
        ),
        "highIssueThreshold": float(
            model_card.get("thresholds", {}).get(HIGH_ISSUE_THRESHOLD_NAME, DEFAULT_HIGH_ISSUE_THRESHOLD)
        ),
        "issueThreshold": float(model_card.get("thresholds", {}).get(ISSUE_THRESHOLD_NAME, DEFAULT_ISSUE_THRESHOLD)),
    }
    return model, model_config, feature_schema, list(reason_payload["reasonCodes"]), thresholds


def load_route_inputs(args, feature_schema: dict[str, Any], model_config: RoutePreferenceModelConfig) -> list[dict]:
    if args.input and args.candidate_set_id:
        raise ValueError("--input 和 --candidate-set-id 只能二选一")
    if args.candidate_set_id:
        db_config = load_database_config()
        with connect(db_config) as connection:
            repository = RoutePreferenceTrainingRepository(connection)
            rows = repository.fetch_samples_for_candidate_set(args.candidate_set_id, args.feature_schema_version)
        if not rows:
            raise ValueError(f"没有找到 candidate_set_id={args.candidate_set_id} 的路线样本")
        return _limit_route_inputs([_route_input_from_row(row) for row in rows], args.limit)
    if args.input:
        payload = _read_json(args.input)
        return _limit_route_inputs(_normalize_route_payload(payload), args.limit)
    payload = _read_json(DEFAULT_INPUT_PATH)
    return _limit_route_inputs(_normalize_route_payload(payload), args.limit)


def _normalize_route_payload(payload: Any) -> list[dict]:
    if isinstance(payload, list):
        return _ensure_route_dicts(payload)
    if isinstance(payload, dict):
        for key in ("route_preference_training_samples", "routeInputs", "rows", "data", "items", "records"):
            value = payload.get(key)
            if isinstance(value, list):
                return _ensure_route_dicts(value)
        if _looks_like_route_input(payload):
            return [payload]
    raise ValueError("输入 JSON 必须是路线对象、路线数组，或包含 route_preference_training_samples/rows/data 的对象")


def _ensure_route_dicts(items: list[Any]) -> list[dict]:
    route_inputs = []
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            raise ValueError(f"第 {index} 条路线必须是对象")
        route_inputs.append(item)
    return route_inputs


def _looks_like_route_input(item: dict[str, Any]) -> bool:
    return any(key in item for key in ("stopMatrix", "stop_matrix_json")) and any(
        key in item for key in ("routeDerivedVector", "route_derived_vector_json")
    )


def _limit_route_inputs(route_inputs: list[dict], limit: int | None) -> list[dict]:
    if limit is None:
        return route_inputs
    if limit <= 0:
        raise ValueError("--limit 必须大于 0")
    return route_inputs[:limit]


def predict_routes(
    model: RoutePreferenceModel,
    model_config: RoutePreferenceModelConfig,
    feature_schema: dict[str, Any],
    route_inputs: list[dict],
    reason_codes: list[str],
    thresholds: dict[str, float],
) -> list[dict]:
    device = next(model.parameters()).device
    stop_matrix = torch.tensor(
        [
            _pad_matrix(
                _matrix(item, "stopMatrix", "stop_matrix_json"),
                model_config.max_stops,
                feature_schema["stopFeatureKeys"],
            )
            for item in route_inputs
        ],
        dtype=torch.float32,
        device=device,
    )
    segment_matrix = torch.tensor(
        [
            _pad_matrix(
                _matrix(item, "segmentMatrix", "segment_matrix_json"),
                model_config.max_segments,
                feature_schema["segmentFeatureKeys"],
            )
            for item in route_inputs
        ],
        dtype=torch.float32,
        device=device,
    )
    route_derived_vector = torch.tensor(
        [
            _vector(item, "routeDerivedVector", "route_derived_vector_json", feature_schema["routeDerivedKeys"])
            for item in route_inputs
        ],
        dtype=torch.float32,
        device=device,
    )
    context_cross_vector = torch.tensor(
        [
            _vector(item, "contextCrossVector", "context_cross_vector_json", feature_schema["contextCrossKeys"])
            for item in route_inputs
        ],
        dtype=torch.float32,
        device=device,
    )
    with torch.no_grad():
        output = model(stop_matrix, segment_matrix, route_derived_vector, context_cross_vector)
        scores = output.route_preference_score.detach().cpu().tolist()
        goodness_probs = torch.sigmoid(output.route_goodness_logit).detach().cpu().tolist()
        reason_probs = torch.sigmoid(output.reason_code_logits).detach().cpu().tolist()
    results = []
    for index, item in enumerate(route_inputs):
        reason_score_map = {
            reason_code: float(reason_probs[index][reason_index]) for reason_index, reason_code in enumerate(reason_codes)
        }
        results.append(
            {
                "routeCode": item.get("routeCode") or item.get("route_code") or f"route-{index}",
                "routePreferenceScore": float(scores[index]),
                "routeGoodnessProb": float(goodness_probs[index]),
                "reasonCodeScores": reason_score_map,
                "issues": _issues(goodness_probs[index], reason_score_map, thresholds),
            }
        )
    return sorted(results, key=lambda item: item["routePreferenceScore"], reverse=True)


def _model_config_from_json(raw: dict[str, Any]) -> RoutePreferenceModelConfig:
    return RoutePreferenceModelConfig(
        route_derived_dim=int(raw["routeDerivedDim"]),
        context_cross_dim=int(raw["contextCrossDim"]),
        stop_dim=int(raw["stopDim"]),
        segment_dim=int(raw["segmentDim"]),
        max_stops=int(raw["maxStops"]),
        max_segments=int(raw["maxSegments"]),
        hidden_dim=int(raw["hiddenDim"]),
        dropout=float(raw["dropout"]),
        reason_hidden_dim=int(raw["reasonHiddenDim"]),
        reason_count=int(raw["reasonCount"]),
    )


def _route_input_from_row(row: TrainingSampleRow) -> dict:
    return {
        "routeCode": row.route_code,
        "stopMatrix": row.stop_matrix_json,
        "segmentMatrix": row.segment_matrix_json,
        "routeDerivedVector": row.route_derived_vector_json,
        "contextCrossVector": row.context_cross_vector_json,
    }


def _zero_route_input(feature_schema: dict[str, Any], model_config: RoutePreferenceModelConfig) -> dict:
    return {
        "routeCode": "sample-zero-route",
        "stopMatrix": [_zero_object(feature_schema["stopFeatureKeys"])],
        "segmentMatrix": [_zero_object(feature_schema["segmentFeatureKeys"])],
        "routeDerivedVector": _zero_object(feature_schema["routeDerivedKeys"]),
        "contextCrossVector": _zero_object(feature_schema["contextCrossKeys"]),
    }


def _zero_object(keys: list[str]) -> dict[str, float]:
    return {key: 0.0 for key in keys}


def _matrix(item: dict, camel_key: str, snake_key: str) -> list:
    value = item.get(camel_key, item.get(snake_key))
    if isinstance(value, str):
        value = json.loads(value)
    if not isinstance(value, list):
        raise ValueError(f"{camel_key} 必须是数组")
    return value


def _vector(item: dict, camel_key: str, snake_key: str, keys: list[str]) -> list[float]:
    value = item.get(camel_key, item.get(snake_key))
    if isinstance(value, str):
        value = json.loads(value)
    if isinstance(value, dict):
        return [float(value.get(key, 0.0)) for key in keys]
    if isinstance(value, list):
        return [float(value_item) for value_item in value]
    raise ValueError(f"{camel_key} 必须是对象或数组")


def _pad_matrix(matrix: list, max_rows: int, keys: list[str]) -> list[list[float]]:
    if len(matrix) > max_rows:
        raise ValueError(f"矩阵行数超过上限: actual={len(matrix)}, maxRows={max_rows}")
    row_dim = len(keys)
    rows = [_matrix_row(row, keys) for row in matrix]
    rows.extend([[0.0] * row_dim for _ in range(max_rows - len(rows))])
    return rows


def _matrix_row(row: Any, keys: list[str]) -> list[float]:
    if isinstance(row, dict):
        values = [float(row.get(key, 0.0)) for key in keys]
    elif isinstance(row, list):
        values = [float(value) for value in row]
    else:
        raise ValueError("矩阵行必须是对象或数组")
    row_dim = len(keys)
    if len(values) != row_dim:
        raise ValueError(f"矩阵行宽不一致: actual={len(values)}, expected={row_dim}")
    return values


def _issues(goodness_prob: float, reason_score_map: dict[str, float], thresholds: dict[str, float]) -> list[dict]:
    threshold = thresholds["highIssueThreshold"] if goodness_prob >= thresholds["goodRouteThreshold"] else thresholds["issueThreshold"]
    issues = [
        {"code": code, "score": score}
        for code, score in sorted(reason_score_map.items(), key=lambda item: item[1], reverse=True)
        if score >= threshold
    ]
    return issues[:3]


def _read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


if __name__ == "__main__":
    raise SystemExit(main())
