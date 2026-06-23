from __future__ import annotations

from decimal import Decimal


ALLOWED_REASON_CODES = {
    "LOW_INTEREST_COVERAGE",
    "WEAK_GOAL_FIT",
    "BAD_TIME_STRUCTURE",
    "HIGH_FATIGUE",
    "BAD_SPATIAL_FLOW",
    "LOW_ROUTE_DIVERSITY",
    "REPETITIVE_POI_TYPE",
    "BUDGET_MISMATCH",
    "HIGH_ROUTE_RISK",
}


def validate_judgment(raw: dict, route_codes: list[str]) -> dict:
    code_set = set(route_codes)
    ranking = list(raw.get("ranking") or [])
    if len(ranking) != len(route_codes) or set(ranking) != code_set:
        raise ValueError(f"ranking 必须是 routeCode 全排列：expected={route_codes}, actual={ranking}")
    if len(set(ranking)) != len(ranking):
        raise ValueError("ranking 不能包含重复 routeCode")

    accepted = list(raw.get("acceptedRouteCodes") or [])
    rejected = list(raw.get("rejectedRouteCodes") or [])
    if not set(accepted).issubset(code_set):
        raise ValueError(f"acceptedRouteCodes 包含外来 routeCode：{accepted}")
    if not set(rejected).issubset(code_set):
        raise ValueError(f"rejectedRouteCodes 包含外来 routeCode：{rejected}")
    overlap = set(accepted) & set(rejected)
    if overlap:
        raise ValueError(f"accepted/rejected 不能重叠：{sorted(overlap)}")

    reason_codes = raw.get("reasonCodes") or {}
    if not isinstance(reason_codes, dict):
        raise ValueError("reasonCodes 必须是对象")
    normalized_reasons = {}
    rejected_set = set(rejected)
    for route_code, codes in reason_codes.items():
        if route_code not in code_set:
            raise ValueError(f"reasonCodes 包含外来 routeCode：{route_code}")
        if route_code not in rejected_set:
            raise ValueError(f"reasonCodes 只能包含 rejectedRouteCodes 中的路线：{route_code}")
        if not isinstance(codes, list):
            raise ValueError(f"reasonCodes.{route_code} 必须是数组")
        invalid = [code for code in codes if code not in ALLOWED_REASON_CODES]
        if invalid:
            raise ValueError(f"reasonCodes.{route_code} 包含非法 reason code：{invalid}")
        normalized_reasons[route_code] = list(dict.fromkeys(codes))

    confidence = raw.get("confidence")
    if confidence is None:
        confidence = 0.5
    confidence_decimal = Decimal(str(confidence))
    if confidence_decimal < Decimal("0") or confidence_decimal > Decimal("1"):
        raise ValueError("confidence 必须在 [0, 1]")

    personal_review = str(raw.get("personalReview") or "").strip()

    return {
        "personalReview": personal_review,
        "ranking": ranking,
        "acceptedRouteCodes": accepted,
        "rejectedRouteCodes": rejected,
        "reasonCodes": normalized_reasons,
        "confidence": float(confidence_decimal),
    }
