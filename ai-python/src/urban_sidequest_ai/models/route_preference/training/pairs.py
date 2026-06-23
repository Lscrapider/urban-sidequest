from __future__ import annotations

from dataclasses import dataclass

from .schema import (
    ACCEPT_REJECT_BOOST,
    COARSE_PAIR_MIN_RANK_GAP,
    CONFIDENCE_FLOOR,
    DEFAULT_ACCEPT_REJECT_WEIGHT,
    DEFAULT_CONFIDENCE,
    DEFAULT_PAIR_TYPE_WEIGHT,
    DEFAULT_SOURCE_WEIGHT,
    HEAD_PAIR_DEPTH,
    MIN_PAIR_WEIGHT_DENOMINATOR,
    SOURCE_WEIGHTS,
)


@dataclass(frozen=True)
class PairwiseSample:
    chosen_route_code: str
    rejected_route_code: str
    chosen_index: int
    rejected_index: int
    weight_raw: float
    pair_type: str


def build_pairwise_samples(
    route_codes: list[str],
    ranking: list[str],
    accepted_route_codes: list[str],
    rejected_route_codes: list[str],
    judge_type: str,
    confidence: float | None,
) -> list[PairwiseSample]:
    route_index = {route_code: index for index, route_code in enumerate(route_codes)}
    rank_index = {route_code: index for index, route_code in enumerate(ranking)}
    route_count = len(ranking)
    if route_count < 2:
        return []

    source_weight = SOURCE_WEIGHTS.get(judge_type, DEFAULT_SOURCE_WEIGHT)
    confidence_clipped = min(max(confidence if confidence is not None else DEFAULT_CONFIDENCE, CONFIDENCE_FLOOR), 1.0)
    denominator = max(route_count - 1, MIN_PAIR_WEIGHT_DENOMINATOR)
    pairs: dict[tuple[str, str], PairwiseSample] = {}

    for chosen_pos, chosen_code in enumerate(ranking):
        for rejected_pos in range(chosen_pos + COARSE_PAIR_MIN_RANK_GAP, route_count):
            rejected_code = ranking[rejected_pos]
            rank_gap = rank_index[rejected_code] - rank_index[chosen_code]
            weight = _pair_weight(rank_gap, denominator, source_weight, confidence_clipped)
            _put_pair(pairs, route_index, chosen_code, rejected_code, weight, "coarse")

    head_pair_limit = min(HEAD_PAIR_DEPTH, route_count)
    for chosen_pos in range(max(head_pair_limit - 1, 0)):
        chosen_code = ranking[chosen_pos]
        rejected_code = ranking[chosen_pos + 1]
        weight = _pair_weight(1, denominator, source_weight, confidence_clipped)
        _put_pair(pairs, route_index, chosen_code, rejected_code, weight, "head")

    for chosen_code in accepted_route_codes:
        for rejected_code in rejected_route_codes:
            rank_gap = max(rank_index[rejected_code] - rank_index[chosen_code], 1)
            weight = _pair_weight(
                rank_gap,
                denominator,
                source_weight,
                confidence_clipped,
                accept_reject_boost=ACCEPT_REJECT_BOOST,
            )
            _put_pair(pairs, route_index, chosen_code, rejected_code, weight, "accept_reject")

    return list(pairs.values())


def _pair_weight(
    rank_gap: int,
    denominator: float,
    source_weight: float,
    confidence_clipped: float,
    accept_reject_boost: float = DEFAULT_ACCEPT_REJECT_WEIGHT,
    pair_type_weight: float = DEFAULT_PAIR_TYPE_WEIGHT,
) -> float:
    route_rank_weight = rank_gap / denominator
    return route_rank_weight * source_weight * confidence_clipped * accept_reject_boost * pair_type_weight


def _put_pair(
    pairs: dict[tuple[str, str], PairwiseSample],
    route_index: dict[str, int],
    chosen_code: str,
    rejected_code: str,
    weight: float,
    pair_type: str,
) -> None:
    key = (chosen_code, rejected_code)
    sample = PairwiseSample(
        chosen_route_code=chosen_code,
        rejected_route_code=rejected_code,
        chosen_index=route_index[chosen_code],
        rejected_index=route_index[rejected_code],
        weight_raw=weight,
        pair_type=pair_type,
    )
    current = pairs.get(key)
    if current is None or _should_replace(current, sample):
        pairs[key] = sample


def _should_replace(current: PairwiseSample, candidate: PairwiseSample) -> bool:
    if candidate.pair_type == "accept_reject" and current.pair_type != "accept_reject":
        return True
    if current.pair_type == "accept_reject" and candidate.pair_type != "accept_reject":
        return False
    return candidate.weight_raw > current.weight_raw

