from __future__ import annotations

import unittest

from urban_sidequest_ai.models.route_preference.training.dataset import build_dataset_bundle
from urban_sidequest_ai.models.route_preference.training.pairs import (
    PairwiseSample,
    aggregate_pairwise_samples,
    build_pairwise_samples,
)
from urban_sidequest_ai.models.route_preference.training.repository import JudgmentRow, TrainingSampleRow


class RoutePreferenceDatasetTest(unittest.TestCase):
    def test_single_judge_aggregation_preserves_labels_and_pairs(self) -> None:
        sample_rows = [
            TrainingSampleRow(
                candidate_set_id="set-1",
                route_code=route_code,
                feature_schema_version="route_pref_test",
                stop_matrix_json=[{"stopFeature": float(index)}],
                segment_matrix_json=[{"segmentFeature": float(index)}],
                route_derived_vector_json={"routeFeature": float(index)},
                context_cross_vector_json={"contextFeature": float(index)},
            )
            for index, route_code in enumerate(["A", "B", "C"], start=1)
        ]
        judgment = JudgmentRow(
            judgment_id="judgment-1",
            candidate_set_id="set-1",
            ranking_json=["A", "B", "C"],
            accepted_route_codes_json=["A"],
            rejected_route_codes_json=["C"],
            reason_codes_json={"C": ["HIGH_FATIGUE"]},
            confidence=0.8,
            judge_type="LLM_SIM_USER",
            judge_model="test-model",
            judge_prompt_version="test-prompt",
        )

        bundle = build_dataset_bundle(sample_rows, [judgment])

        self.assertEqual(1, len(bundle.groups))
        group = bundle.groups[0]
        self.assertEqual("set-1", group.candidate_set_id)
        self.assertEqual("judgment-1", group.judgment_id)

        items_by_code = {item.route_code: item for item in group.items}
        self.assertEqual((1.0, 1.0, 1.0), (
            items_by_code["A"].goodness_label,
            items_by_code["A"].goodness_mask,
            items_by_code["A"].goodness_weight_raw,
        ))
        self.assertEqual(0.0, items_by_code["B"].goodness_mask)
        self.assertEqual((0.0, 1.0, 1.0), (
            items_by_code["C"].goodness_label,
            items_by_code["C"].goodness_mask,
            items_by_code["C"].goodness_weight_raw,
        ))
        self.assertEqual(1.0, items_by_code["C"].reason_mask)
        self.assertEqual(1.0, items_by_code["C"].reason_weight_raw)

        expected_pairs = build_pairwise_samples(
            route_codes=["A", "B", "C"],
            ranking=["A", "B", "C"],
            accepted_route_codes=["A"],
            rejected_route_codes=["C"],
            judge_type="LLM_SIM_USER",
            confidence=0.8,
        )
        actual = {
            (pair.chosen_route_code, pair.rejected_route_code): pair.weight_raw
            for pair in group.pairs
        }
        expected = {
            (pair.chosen_route_code, pair.rejected_route_code): pair.weight_raw
            for pair in expected_pairs
        }
        self.assertEqual(expected, actual)

    def test_pair_aggregation_uses_direction_margin_as_weight(self) -> None:
        pairs = [
            PairwiseSample("A", "B", 0, 1, 1.0, "coarse"),
            PairwiseSample("A", "B", 0, 1, 1.0, "coarse"),
            PairwiseSample("B", "A", 1, 0, 1.0, "coarse"),
            PairwiseSample("A", "C", 0, 2, 1.0, "coarse"),
            PairwiseSample("C", "A", 2, 0, 1.0, "coarse"),
        ]

        aggregated = aggregate_pairwise_samples(["A", "B", "C"], pairs)

        actual = {
            (pair.chosen_route_code, pair.rejected_route_code): pair.weight_raw
            for pair in aggregated
        }
        self.assertEqual({("A", "B"): 1.0}, actual)

    def test_route_label_aggregation_uses_accept_reject_margin_as_weight(self) -> None:
        sample_rows = [
            TrainingSampleRow(
                candidate_set_id="set-1",
                route_code=route_code,
                feature_schema_version="route_pref_test",
                stop_matrix_json=[{"stopFeature": float(index)}],
                segment_matrix_json=[{"segmentFeature": float(index)}],
                route_derived_vector_json={"routeFeature": float(index)},
                context_cross_vector_json={"contextFeature": float(index)},
            )
            for index, route_code in enumerate(["A", "B", "C"], start=1)
        ]
        judgments = [
            JudgmentRow(
                judgment_id="judgment-1",
                candidate_set_id="set-1",
                ranking_json=["A", "B", "C"],
                accepted_route_codes_json=["A"],
                rejected_route_codes_json=["C"],
                reason_codes_json={"C": ["HIGH_FATIGUE"]},
                confidence=0.8,
                judge_type="LLM_SIM_USER",
                judge_model="test-model",
                judge_prompt_version="test-prompt",
            ),
            JudgmentRow(
                judgment_id="judgment-2",
                candidate_set_id="set-1",
                ranking_json=["A", "B", "C"],
                accepted_route_codes_json=["A"],
                rejected_route_codes_json=["C"],
                reason_codes_json={"C": ["HIGH_FATIGUE"]},
                confidence=0.8,
                judge_type="LLM_SIM_USER",
                judge_model="test-model",
                judge_prompt_version="test-prompt",
            ),
            JudgmentRow(
                judgment_id="judgment-3",
                candidate_set_id="set-1",
                ranking_json=["C", "B", "A"],
                accepted_route_codes_json=["C"],
                rejected_route_codes_json=["A"],
                reason_codes_json={"A": ["LOW_INTEREST_COVERAGE"]},
                confidence=0.8,
                judge_type="LLM_SIM_USER",
                judge_model="test-model",
                judge_prompt_version="test-prompt",
            ),
        ]

        bundle = build_dataset_bundle(sample_rows, judgments)

        items_by_code = {item.route_code: item for item in bundle.groups[0].items}
        self.assertEqual(1.0, items_by_code["A"].goodness_label)
        self.assertEqual(1.0, items_by_code["A"].goodness_mask)
        self.assertAlmostEqual(1.0, items_by_code["A"].goodness_weight_raw)
        self.assertEqual(0.0, items_by_code["A"].reason_mask)
        self.assertEqual(0.0, items_by_code["C"].goodness_label)
        self.assertEqual(1.0, items_by_code["C"].goodness_mask)
        self.assertAlmostEqual(1.0, items_by_code["C"].goodness_weight_raw)
        self.assertEqual(1.0, items_by_code["C"].reason_mask)
        self.assertAlmostEqual(1.0, items_by_code["C"].reason_weight_raw)


if __name__ == "__main__":
    unittest.main()
