from __future__ import annotations

import unittest

from scripts.route_preference_simulator.prompt import build_user_prompt


class PromptTest(unittest.TestCase):

    def test_taxi_low_distance_sensitivity_accepts_far_only_when_quality_gain_is_clear(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_TAXI",
                "budgetLevel": "NORMAL",
                "interestTags": ["LOCAL", "FOOD"],
                "durationMinutes": 360,
            },
            {"routes": []},
            {"distanceSensitivity": 0.2},
        )

        self.assertIn("选择 WALK_TAXI 且对距离不敏感", prompt)
        self.assertIn("远一点换来明显更好的 POI", prompt)
        self.assertIn("不要因为能打车就接受无意义绕路", prompt)

    def test_taxi_high_distance_sensitivity_still_rejects_unnecessary_far_jumps(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_TAXI",
                "budgetLevel": "NORMAL",
                "interestTags": ["LOCAL", "FOOD"],
                "durationMinutes": 360,
            },
            {"routes": []},
            {"distanceSensitivity": 0.9},
        )

        self.assertIn("即使选择 WALK_TAXI", prompt)
        self.assertIn("远距离必须换来显著更好的兴趣命中或地点质量", prompt)
        self.assertIn("否则应把远距离、跨片区折返视为负面", prompt)

    def test_walk_only_low_distance_sensitivity_keeps_walk_comfort_boundary(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_ONLY",
                "budgetLevel": "NORMAL",
                "interestTags": ["LOCAL", "FOOD"],
                "durationMinutes": 180,
            },
            {"routes": []},
            {"distanceSensitivity": 0.2},
        )

        self.assertIn("WALK_ONLY 仍按步行舒适判断", prompt)
        self.assertIn("不能把明显长距离步行当成可接受", prompt)


if __name__ == "__main__":
    unittest.main()
