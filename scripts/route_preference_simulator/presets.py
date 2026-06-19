from __future__ import annotations


CITY_PRESETS = {
    "shanghai": {
        "routeCityName": "上海",
        "routeCityAdcode": "310000",
        "areas": [
            {"areaLabel": "上海人民广场附近", "center": {"longitudeGcj02": 121.4737, "latitudeGcj02": 31.2304}},
            {"areaLabel": "上海衡山路-武康路附近", "center": {"longitudeGcj02": 121.4427, "latitudeGcj02": 31.2092}},
            {"areaLabel": "上海外滩-豫园附近", "center": {"longitudeGcj02": 121.4929, "latitudeGcj02": 31.2343}},
            {"areaLabel": "上海静安寺附近", "center": {"longitudeGcj02": 121.4444, "latitudeGcj02": 31.2246}},
        ],
    },
    "beijing": {
        "routeCityName": "北京",
        "routeCityAdcode": "110000",
        "areas": [
            {"areaLabel": "北京什刹海-鼓楼附近", "center": {"longitudeGcj02": 116.3974, "latitudeGcj02": 39.9442}},
            {"areaLabel": "北京前门-大栅栏附近", "center": {"longitudeGcj02": 116.3978, "latitudeGcj02": 39.8993}},
            {"areaLabel": "北京朝阳公园附近", "center": {"longitudeGcj02": 116.4825, "latitudeGcj02": 39.9440}},
        ],
    },
    "hangzhou": {
        "routeCityName": "杭州",
        "routeCityAdcode": "330100",
        "areas": [
            {"areaLabel": "杭州西湖湖滨附近", "center": {"longitudeGcj02": 120.1551, "latitudeGcj02": 30.2548}},
            {"areaLabel": "杭州武林广场附近", "center": {"longitudeGcj02": 120.1655, "latitudeGcj02": 30.2795}},
            {"areaLabel": "杭州小河直街附近", "center": {"longitudeGcj02": 120.1455, "latitudeGcj02": 30.3203}},
        ],
    },
    "chengdu": {
        "routeCityName": "成都",
        "routeCityAdcode": "510100",
        "areas": [
            {"areaLabel": "成都宽窄巷子附近", "center": {"longitudeGcj02": 104.0556, "latitudeGcj02": 30.6720}},
            {"areaLabel": "成都太古里-春熙路附近", "center": {"longitudeGcj02": 104.0807, "latitudeGcj02": 30.6530}},
            {"areaLabel": "成都玉林附近", "center": {"longitudeGcj02": 104.0603, "latitudeGcj02": 30.6264}},
        ],
    },
    "guangzhou": {
        "routeCityName": "广州",
        "routeCityAdcode": "440100",
        "areas": [
            {"areaLabel": "广州东山口附近", "center": {"longitudeGcj02": 113.2953, "latitudeGcj02": 23.1254}},
            {"areaLabel": "广州北京路附近", "center": {"longitudeGcj02": 113.2714, "latitudeGcj02": 23.1250}},
            {"areaLabel": "广州沙面附近", "center": {"longitudeGcj02": 113.2452, "latitudeGcj02": 23.1103}},
        ],
    },
}


PERSONA_ARCHETYPES = [
    {
        "name": "low_budget_local",
        "distanceSensitivity": 0.55,
        "budgetSensitivity": 0.90,
        "transferSensitivity": 0.50,
        "hiddenGemAffinity": 0.75,
        "tagAffinities": {"LOCAL": 0.90, "FOOD": 0.80, "COFFEE": 0.70, "CLASSIC": 0.25},
    },
    {
        "name": "classic_first_timer",
        "distanceSensitivity": 0.45,
        "budgetSensitivity": 0.45,
        "transferSensitivity": 0.45,
        "hiddenGemAffinity": 0.20,
        "tagAffinities": {"CLASSIC": 0.92, "CULTURE": 0.72, "PHOTO": 0.65, "LOCAL": 0.35},
    },
    {
        "name": "photo_citywalker",
        "distanceSensitivity": 0.40,
        "budgetSensitivity": 0.45,
        "transferSensitivity": 0.35,
        "hiddenGemAffinity": 0.55,
        "tagAffinities": {"PHOTO": 0.92, "LOCAL": 0.68, "COFFEE": 0.58, "CLASSIC": 0.52},
    },
    {
        "name": "slow_pace_rest",
        "distanceSensitivity": 0.88,
        "budgetSensitivity": 0.55,
        "transferSensitivity": 0.70,
        "hiddenGemAffinity": 0.45,
        "tagAffinities": {"COFFEE": 0.82, "FOOD": 0.66, "LOCAL": 0.55, "CLASSIC": 0.40},
    },
    {
        "name": "night_food",
        "distanceSensitivity": 0.45,
        "budgetSensitivity": 0.50,
        "transferSensitivity": 0.48,
        "hiddenGemAffinity": 0.62,
        "tagAffinities": {"NIGHT_MARKET_VIEW": 0.90, "FOOD": 0.88, "LOCAL": 0.70, "PHOTO": 0.55},
    },
    {
        "name": "museum_culture",
        "distanceSensitivity": 0.50,
        "budgetSensitivity": 0.40,
        "transferSensitivity": 0.45,
        "hiddenGemAffinity": 0.42,
        "tagAffinities": {"CULTURE": 0.92, "CLASSIC": 0.70, "COFFEE": 0.45, "PHOTO": 0.40},
    },
    {
        "name": "food_explorer",
        "distanceSensitivity": 0.50,
        "budgetSensitivity": 0.58,
        "transferSensitivity": 0.42,
        "hiddenGemAffinity": 0.72,
        "tagAffinities": {"FOOD": 0.94, "LOCAL": 0.82, "COFFEE": 0.62, "CLASSIC": 0.28},
    },
    {
        "name": "transit_averse",
        "distanceSensitivity": 0.65,
        "budgetSensitivity": 0.50,
        "transferSensitivity": 0.88,
        "hiddenGemAffinity": 0.48,
        "tagAffinities": {"LOCAL": 0.70, "COFFEE": 0.55, "CLASSIC": 0.52, "FOOD": 0.50},
    },
    {
        "name": "high_energy_mixed",
        "distanceSensitivity": 0.20,
        "budgetSensitivity": 0.35,
        "transferSensitivity": 0.25,
        "hiddenGemAffinity": 0.58,
        "tagAffinities": {"LOCAL": 0.72, "CLASSIC": 0.68, "PHOTO": 0.66, "FOOD": 0.62},
    },
    {
        "name": "budget_classic",
        "distanceSensitivity": 0.55,
        "budgetSensitivity": 0.86,
        "transferSensitivity": 0.55,
        "hiddenGemAffinity": 0.28,
        "tagAffinities": {"CLASSIC": 0.85, "CULTURE": 0.62, "FOOD": 0.50, "PHOTO": 0.45},
    },
    {
        "name": "hidden_gem_photo",
        "distanceSensitivity": 0.42,
        "budgetSensitivity": 0.52,
        "transferSensitivity": 0.38,
        "hiddenGemAffinity": 0.90,
        "tagAffinities": {"LOCAL": 0.88, "PHOTO": 0.82, "COFFEE": 0.65, "CLASSIC": 0.20},
    },
    {
        "name": "balanced_family",
        "distanceSensitivity": 0.72,
        "budgetSensitivity": 0.62,
        "transferSensitivity": 0.65,
        "hiddenGemAffinity": 0.35,
        "tagAffinities": {"CLASSIC": 0.72, "FOOD": 0.62, "CULTURE": 0.58, "LOCAL": 0.48},
    },
]


REQUEST_TEMPLATES = [
    {"routeGoal": "LOCAL", "transportProfile": "WALK_SUBWAY", "budgetLevel": "NORMAL", "interestTags": ["LOCAL", "FOOD", "COFFEE"], "durationMinutes": 240, "hour": 14},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_SUBWAY", "budgetLevel": "NORMAL", "interestTags": ["CLASSIC", "CULTURE", "PHOTO"], "durationMinutes": 300, "hour": 10},
    {"routeGoal": "LOW_BUDGET", "transportProfile": "WALK_ONLY", "budgetLevel": "LOW", "interestTags": ["LOCAL", "FOOD", "COFFEE"], "durationMinutes": 180, "hour": 13},
    {"routeGoal": "NIGHT", "transportProfile": "WALK_TAXI", "budgetLevel": "NORMAL", "interestTags": ["NIGHT_MARKET_VIEW", "FOOD", "PHOTO"], "durationMinutes": 240, "hour": 17},
    {"routeGoal": "PHOTO", "transportProfile": "WALK_TRANSIT", "budgetLevel": "NORMAL", "interestTags": ["PHOTO", "LOCAL", "CLASSIC"], "durationMinutes": 240, "hour": 15},
    {"routeGoal": "STEADY", "transportProfile": "WALK_BUS", "budgetLevel": "NORMAL", "interestTags": ["CULTURE", "COFFEE", "FOOD"], "durationMinutes": 210, "hour": 11},
    {"routeGoal": "LOCAL", "transportProfile": "BIKE_SUBWAY", "budgetLevel": "FLEXIBLE", "interestTags": ["LOCAL", "PHOTO", "COFFEE"], "durationMinutes": 360, "hour": 10},
    {"routeGoal": "CLASSIC", "transportProfile": "WALK_TAXI", "budgetLevel": "FLEXIBLE", "interestTags": ["CLASSIC", "PHOTO", "FOOD"], "durationMinutes": 420, "hour": 9},
]
