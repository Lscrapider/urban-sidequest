from __future__ import annotations

from collections import defaultdict
from math import log2
from typing import Iterable

import torch

from .dataset import LabeledCandidateSet, iter_batches
from .losses import LossConfig, compute_losses
from .model import RoutePreferenceModel
from .schema import DEFAULT_ISSUE_THRESHOLD, REASON_CODES


def evaluate_model(
    model: RoutePreferenceModel,
    groups: tuple[LabeledCandidateSet, ...],
    loss_config: LossConfig,
    batch_candidate_sets: int,
    device: torch.device,
    seed: int,
    prefix: str,
) -> dict[str, float]:
    if not groups:
        return {}
    model.eval()
    loss_totals: dict[str, float] = defaultdict(float)
    batch_count = 0
    pair_correct = 0
    pair_total = 0
    weighted_pair_correct = 0.0
    weighted_pair_total = 0.0
    top1_correct = 0
    top2_hit = 0
    ndcg_at_3_values: list[float] = []
    goodness_scores: list[float] = []
    goodness_labels: list[int] = []
    reason_scores: list[list[float]] = []
    reason_labels: list[list[int]] = []
    accepted_reason_false_positive = 0
    accepted_total = 0

    with torch.no_grad():
        for batch in iter_batches(groups, batch_candidate_sets, shuffle=False, seed=seed, device=device):
            output = model(
                batch.stop_matrix,
                batch.segment_matrix,
                batch.route_derived_vector,
                batch.context_cross_vector,
                batch.intra_set_vector,
            )
            losses = compute_losses(output, batch, loss_config)
            for key, value in losses.detached_metrics().items():
                loss_totals[key] += value
            batch_count += 1

            if batch.has_pairs:
                chosen = output.route_preference_score[batch.pair_chosen_index]
                rejected = output.route_preference_score[batch.pair_rejected_index]
                correct = (chosen > rejected).detach().cpu()
                weights = batch.pair_weight_raw.detach().cpu()
                pair_correct += int(correct.sum())
                pair_total += int(correct.numel())
                weighted_pair_correct += float((correct.float() * weights).sum())
                weighted_pair_total += float(weights.sum())

            goodness_prob = torch.sigmoid(output.route_goodness_logit).detach().cpu().tolist()
            reason_prob = torch.sigmoid(output.reason_code_logits).detach().cpu().tolist()
            route_offset = 0
            for group in groups_from_offsets(groups, batch_count, batch_candidate_sets):
                route_count = len(group.items)
                group_scores = output.route_preference_score[route_offset : route_offset + route_count].detach().cpu().tolist()
                true_order = [item.rank for item in group.items]
                top1_correct += int(_predicted_rank(group_scores, true_order, 1))
                top2_hit += int(_top_rank_hit(group_scores, true_order, 2))
                ndcg_at_3_values.append(_ndcg_at_k(group_scores, true_order, 3))
                for local_index, item in enumerate(group.items):
                    global_index = route_offset + local_index
                    if item.goodness_mask:
                        goodness_scores.append(goodness_prob[global_index])
                        goodness_labels.append(int(item.goodness_label))
                    if item.reason_mask:
                        reason_scores.append(reason_prob[global_index])
                        reason_labels.append([int(value) for value in item.reason_labels])
                    if item.is_accepted:
                        accepted_total += 1
                        if max(reason_prob[global_index], default=0.0) >= DEFAULT_ISSUE_THRESHOLD:
                            accepted_reason_false_positive += 1
                route_offset += route_count

    metrics: dict[str, float] = {}
    if batch_count:
        for key, value in loss_totals.items():
            metrics[f"{prefix}/{key}"] = value / batch_count
    metrics[f"{prefix}/pairwiseAccuracy"] = _safe_div(pair_correct, pair_total)
    metrics[f"{prefix}/weightedPairwiseAccuracy"] = _safe_div(weighted_pair_correct, weighted_pair_total)
    metrics[f"{prefix}/top1Accuracy"] = _safe_div(top1_correct, len(groups))
    metrics[f"{prefix}/top2HitRate"] = _safe_div(top2_hit, len(groups))
    metrics[f"{prefix}/ndcg@3"] = _mean(ndcg_at_3_values)
    metrics[f"{prefix}/goodnessAuc"] = _binary_auc(goodness_labels, goodness_scores)
    metrics[f"{prefix}/goodnessPrAuc"] = _binary_pr_auc(goodness_labels, goodness_scores)
    metrics[f"{prefix}/goodnessAccuracy@0.5"] = _threshold_accuracy(goodness_labels, goodness_scores, 0.5)
    metrics[f"{prefix}/acceptedReasonFalsePositiveRate@issueThreshold"] = _safe_div(
        accepted_reason_false_positive,
        accepted_total,
    )
    metrics.update(_reason_metrics(prefix, reason_labels, reason_scores))
    return metrics


def groups_from_offsets(
    groups: tuple[LabeledCandidateSet, ...],
    batch_number: int,
    batch_candidate_sets: int,
) -> Iterable[LabeledCandidateSet]:
    start = (batch_number - 1) * batch_candidate_sets
    return groups[start : start + batch_candidate_sets]


def _predicted_rank(scores: list[float], true_ranks: list[int], target_rank: int) -> bool:
    best_index = max(range(len(scores)), key=lambda index: scores[index])
    return true_ranks[best_index] == target_rank - 1


def _top_rank_hit(scores: list[float], true_ranks: list[int], depth: int) -> bool:
    predicted = sorted(range(len(scores)), key=lambda index: scores[index], reverse=True)[:depth]
    return any(true_ranks[index] == 0 for index in predicted)


def _ndcg_at_k(scores: list[float], true_ranks: list[int], k: int) -> float:
    route_count = len(scores)
    if route_count == 0:
        return 0.0
    predicted = sorted(range(route_count), key=lambda index: scores[index], reverse=True)[:k]
    ideal = sorted(range(route_count), key=lambda index: true_ranks[index])[:k]
    relevances = [route_count - rank for rank in true_ranks]
    dcg = sum(relevances[index] / log2(position + 2) for position, index in enumerate(predicted))
    idcg = sum(relevances[index] / log2(position + 2) for position, index in enumerate(ideal))
    return _safe_div(dcg, idcg)


def _reason_metrics(prefix: str, labels: list[list[int]], scores: list[list[float]]) -> dict[str, float]:
    metrics: dict[str, float] = {}
    if not labels:
        metrics[f"{prefix}/reasonConditionalMicroF1@0.5"] = 0.0
        metrics[f"{prefix}/reasonConditionalMacroF1@0.5"] = 0.0
        metrics[f"{prefix}/reasonTopIssueHitRate"] = 0.0
        return metrics
    predicted = [[1 if score >= 0.5 else 0 for score in row] for row in scores]
    metrics[f"{prefix}/reasonConditionalMicroF1@0.5"] = _micro_f1(labels, predicted)
    metrics[f"{prefix}/reasonConditionalMacroF1@0.5"] = _macro_f1(labels, predicted)
    metrics[f"{prefix}/reasonTopIssueHitRate"] = _top_issue_hit(labels, scores)
    auc_values = []
    for index, reason_code in enumerate(REASON_CODES):
        code_labels = [row[index] for row in labels]
        code_scores = [row[index] for row in scores]
        auc = _binary_auc(code_labels, code_scores)
        metrics[f"{prefix}/reasonAuc/{reason_code}"] = auc
        if auc > 0:
            auc_values.append(auc)
    metrics[f"{prefix}/reasonConditionalPerCodeAucMacro"] = _mean(auc_values)
    return metrics


def _binary_auc(labels: list[int], scores: list[float]) -> float:
    positives = sum(labels)
    negatives = len(labels) - positives
    if positives == 0 or negatives == 0:
        return 0.0
    order = sorted(range(len(scores)), key=lambda index: scores[index])
    rank_sum = 0.0
    for rank, index in enumerate(order, start=1):
        if labels[index] == 1:
            rank_sum += rank
    return (rank_sum - positives * (positives + 1) / 2) / (positives * negatives)


def _binary_pr_auc(labels: list[int], scores: list[float]) -> float:
    positives = sum(labels)
    if positives == 0:
        return 0.0
    order = sorted(range(len(scores)), key=lambda index: scores[index], reverse=True)
    points: list[tuple[float, float]] = [(0.0, 1.0)]
    true_positive = 0
    false_positive = 0
    for index in order:
        if labels[index] == 1:
            true_positive += 1
        else:
            false_positive += 1
        recall = true_positive / positives
        precision = true_positive / max(true_positive + false_positive, 1)
        points.append((recall, precision))
    area = 0.0
    for (last_recall, last_precision), (recall, precision) in zip(points, points[1:]):
        area += (recall - last_recall) * ((precision + last_precision) / 2)
    return area


def _threshold_accuracy(labels: list[int], scores: list[float], threshold: float) -> float:
    if not labels:
        return 0.0
    correct = sum(int((score >= threshold) == bool(label)) for label, score in zip(labels, scores))
    return correct / len(labels)


def _micro_f1(labels: list[list[int]], predicted: list[list[int]]) -> float:
    true_positive = false_positive = false_negative = 0
    for label_row, predicted_row in zip(labels, predicted):
        for label, pred in zip(label_row, predicted_row):
            true_positive += int(label == 1 and pred == 1)
            false_positive += int(label == 0 and pred == 1)
            false_negative += int(label == 1 and pred == 0)
    return _f1(true_positive, false_positive, false_negative)


def _macro_f1(labels: list[list[int]], predicted: list[list[int]]) -> float:
    values = []
    for index in range(len(REASON_CODES)):
        true_positive = false_positive = false_negative = 0
        for label_row, predicted_row in zip(labels, predicted):
            true_positive += int(label_row[index] == 1 and predicted_row[index] == 1)
            false_positive += int(label_row[index] == 0 and predicted_row[index] == 1)
            false_negative += int(label_row[index] == 1 and predicted_row[index] == 0)
        values.append(_f1(true_positive, false_positive, false_negative))
    return _mean(values)


def _top_issue_hit(labels: list[list[int]], scores: list[list[float]]) -> float:
    hits = 0
    total = 0
    for label_row, score_row in zip(labels, scores):
        if not any(label_row):
            continue
        total += 1
        top_index = max(range(len(score_row)), key=lambda index: score_row[index])
        hits += int(label_row[top_index] == 1)
    return _safe_div(hits, total)


def _f1(true_positive: int, false_positive: int, false_negative: int) -> float:
    precision = _safe_div(true_positive, true_positive + false_positive)
    recall = _safe_div(true_positive, true_positive + false_negative)
    return _safe_div(2 * precision * recall, precision + recall)


def _safe_div(numerator: float, denominator: float) -> float:
    return 0.0 if denominator == 0 else numerator / denominator


def _mean(values: list[float]) -> float:
    return 0.0 if not values else sum(values) / len(values)
