from __future__ import annotations

import unittest

from scripts.route_preference_simulator.prompt import SYSTEM_PROMPT, build_user_prompt


class PromptTest(unittest.TestCase):

    def test_taxi_low_distance_sensitivity_accepts_far_only_when_quality_gain_is_clear(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_TAXI",
                "budgetLevel": "NORMAL",
                "interestTags": ["LOCAL", "FOOD_LOCAL_FLAVOR"],
                "mealWindows": ["LUNCH"],
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
                "interestTags": ["LOCAL", "FOOD_LOCAL_FLAVOR"],
                "mealWindows": ["LUNCH"],
                "durationMinutes": 360,
            },
            {"routes": []},
            {"distanceSensitivity": 0.9},
        )

        self.assertIn("平时对距离敏感，但这次选择 WALK_TAXI", prompt)
        self.assertIn("愿意为了更值得的地点出远门", prompt)
        self.assertIn("无意义绕路、折返仍应降分", prompt)

    def test_walk_only_low_distance_sensitivity_keeps_walk_comfort_boundary(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_ONLY",
                "budgetLevel": "NORMAL",
                "interestTags": ["LOCAL", "FOOD_LOCAL_FLAVOR"],
                "mealWindows": ["LUNCH"],
                "durationMinutes": 180,
            },
            {"routes": []},
            {"distanceSensitivity": 0.2},
        )

        self.assertIn("WALK_ONLY 仍按步行舒适判断", prompt)
        self.assertIn("不能把明显长距离步行当成可接受", prompt)

    def test_prompt_uses_local_departure_and_semantic_route_fields(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_SUBWAY",
                "budgetLevel": "LOW",
                "interestTags": ["FOOD_SICHUAN", "COFFEE", "MUSEUM"],
                "mealWindows": ["DINNER"],
                "departureTime": "2026-06-22T14:30:00",
                "durationMinutes": 360,
            },
            {
                "routes": [
                    {
                        "routeCode": "A",
                        "title": "路线 A",
                        "summary": "川菜和咖啡休息结合。",
                        "totalDurationMinutes": 220,
                        "totalDistanceMeters": 2600,
                        "budgetCent": 9000,
                        "riskLevel": "LOW",
                        "explanation": "饭点和休息点完整。",
                        "stops": [
                            {
                                "name": "川味小馆",
                                "slotLabel": "餐饮",
                                "stayMinutes": 60,
                                "routeRole": "MEAL",
                                "intendedMealWindow": "DINNER",
                                "primaryCategoryGroup": "FOOD",
                                "categoryGroups": ["FOOD"],
                                "semanticTags": ["LOCAL"],
                                "poiTagHits": ["FOOD_SICHUAN"],
                                "mealCandidate": True,
                                "restCandidate": False,
                                "localExperienceCandidate": True,
                                "avgPriceCent": 6500,
                                "transportToNext": "WALK",
                                "distanceToNextMeters": 600,
                                "durationToNextMinutes": 8,
                                "description": "适合作为正餐",
                                "matchedInterestTags": ["FOOD_SICHUAN"],
                                "recallSources": ["召回计划:FOOD_CHINESE"],
                            }
                        ],
                        "segments": [
                            {"order": 1, "mode": "WALK", "distanceMeters": 600, "durationMinutes": 8, "source": "AMAP"}
                        ],
                    }
                ]
            },
            None,
        )

        self.assertIn("2026-06-22 14:30（北京时间本地）", prompt)
        self.assertIn("饭点: DINNER", prompt)
        self.assertIn("primaryCategoryGroup=FOOD", prompt)
        self.assertIn("poiTagHits=FOOD_SICHUAN", prompt)
        self.assertIn("routeRole=MEAL", prompt)
        self.assertIn("intendedMealWindow=DINNER", prompt)
        self.assertIn("avgPrice=¥65", prompt)
        self.assertIn("matchedInterestTags、", SYSTEM_PROMPT)
        self.assertNotIn("召回计划:FOOD_CHINESE", prompt)

    def test_request_interest_is_stronger_than_persona_background(self) -> None:
        prompt = build_user_prompt(
            {
                "routeGoal": "LOCAL",
                "transportProfile": "WALK_TAXI",
                "budgetLevel": "NORMAL",
                "interestTags": ["FOOD_SICHUAN", "COFFEE", "EVENT"],
                "mealWindows": ["DINNER"],
                "durationMinutes": 300,
            },
            {"routes": []},
            {
                "distanceSensitivity": 0.85,
                "budgetSensitivity": 0.4,
                "transferSensitivity": 0.3,
                "hiddenGemAffinity": 0.7,
                "tagAffinities": {
                    "LOCAL": 0.9,
                    "FOOD_LOCAL_FLAVOR": 0.8,
                    "PHOTO": 0.7,
                },
            },
        )

        self.assertIn("本次 request 是你此刻的明确意图，优先级高于长期画像", prompt)
        self.assertIn("本次餐饮偏好是 川菜", prompt)
        self.assertIn("不能只因为 primaryCategoryGroup=FOOD 就算完全满足", prompt)
        self.assertIn("本次还临时选择了：川菜、咖啡、活动", prompt)
        self.assertIn("长期喜欢但本次没显式选择的兴趣只能作为加分项", prompt)
        self.assertIn("只有当它用同父类餐饮、饭点安排和整体路线质量形成合理替代", prompt)


if __name__ == "__main__":
    unittest.main()
