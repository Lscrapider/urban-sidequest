from __future__ import annotations

from copy import deepcopy
from datetime import datetime, time, timedelta
import random

from .presets import CITY_PRESETS, INTEREST_TAG_CODES, PERSONA_ARCHETYPES, REQUEST_TEMPLATES


REQUEST_GLOBAL_INTEREST_BUCKET_MIN = 2
REQUEST_GLOBAL_INTEREST_BUCKET_MAX = 5
REQUEST_FOOD_INTEREST_TAG_MAX = 3
PERSONA_TAG_AFFINITY_MIN = 3
PERSONA_TAG_AFFINITY_MAX = 6
PERSONA_EXTRA_TAG_AFFINITY_MIN = 0.20
PERSONA_EXTRA_TAG_AFFINITY_MAX = 0.55
REQUEST_PRIMARY_TAG_COUNT = 1
PERSONA_CORE_TAG_COUNT = 2

MEAL_WINDOW_TIMES = {
    "LUNCH": (time(11, 30), time(13, 30)),
    "DINNER": (time(17, 30), time(20, 0)),
}

FOOD_PARENT_BY_TAG = {
    "FOOD_CHINESE": "FOOD",
    "FOOD_FOREIGN": "FOOD",
    "FOOD_FAST_FOOD": "FOOD",
    "FOOD_SICHUAN": "FOOD_CHINESE",
    "FOOD_CANTONESE": "FOOD_CHINESE",
    "FOOD_SHANDONG": "FOOD_CHINESE",
    "FOOD_JIANGSU": "FOOD_CHINESE",
    "FOOD_ZHEJIANG": "FOOD_CHINESE",
    "FOOD_HUNAN": "FOOD_CHINESE",
    "FOOD_DONG_BEI": "FOOD_CHINESE",
    "FOOD_OLD_BRAND": "FOOD_CHINESE",
    "FOOD_HOT_POT": "FOOD_CHINESE",
    "FOOD_LOCAL_FLAVOR": "FOOD_CHINESE",
    "FOOD_HALAL": "FOOD_CHINESE",
    "FOOD_WESTERN": "FOOD_FOREIGN",
    "FOOD_AMERICAN": "FOOD_FOREIGN",
    "FOOD_INDIAN": "FOOD_FOREIGN",
    "FOOD_MEXICAN": "FOOD_FOREIGN",
}


def build_jobs(
    persona_count: int = 100,
    requests_per_persona: int = 20,
    seed: int | None = None,
    city_keys: list[str] | None = None,
    request_count: int | None = None,
    probe_ratio: float = 0.1,
    probe_persona_count: int = 2,
) -> list[dict]:
    rng = random.Random(seed)
    selected_city_keys = city_keys or list(CITY_PRESETS.keys())
    base_date = datetime(2026, 6, 20)
    if request_count is not None:
        return build_request_probe_jobs(
            request_count=request_count,
            probe_ratio=probe_ratio,
            probe_persona_count=probe_persona_count,
            rng=rng,
            city_keys=selected_city_keys,
            base_date=base_date,
        )

    personas = [build_persona(index, rng) for index in range(persona_count)]
    jobs = []
    for persona_index, persona in enumerate(personas):
        for request_index in range(requests_per_persona):
            absolute_index = persona_index * requests_per_persona + request_index
            jobs.append(
                {
                    "request": build_request(absolute_index, rng, selected_city_keys, base_date),
                    "persona": persona,
                    "meta": {
                        "personaIndex": persona_index,
                        "requestIndex": request_index,
                        "personaArchetype": persona["questionnaireVersion"].split(":")[-1],
                    },
                }
            )
    return jobs


def build_request_probe_jobs(
    request_count: int,
    probe_ratio: float,
    probe_persona_count: int,
    rng: random.Random,
    city_keys: list[str],
    base_date: datetime,
) -> list[dict]:
    if request_count < 1:
        raise ValueError("request_count 必须 >= 1")
    if probe_persona_count < 2:
        raise ValueError("probe_persona_count 必须 >= 2")
    if not 0 <= probe_ratio <= 1:
        raise ValueError("probe_ratio 必须在 [0, 1]")

    probe_request_count = round(request_count * probe_ratio)
    probe_indexes = set(rng.sample(range(request_count), probe_request_count))
    jobs = []
    persona_index = 0
    for request_index in range(request_count):
        request = build_request(request_index, rng, city_keys, base_date)
        persona_repeats = probe_persona_count if request_index in probe_indexes else 1
        for persona_repeat_index in range(persona_repeats):
            persona = build_persona(persona_index, rng)
            jobs.append(
                {
                    "request": deepcopy(request),
                    "persona": persona,
                    "meta": {
                        "baseRequestIndex": request_index,
                        "personaIndex": persona_index,
                        "personaRepeatIndex": persona_repeat_index,
                        "personaRepeatCount": persona_repeats,
                        "probeRequest": persona_repeats > 1,
                        "personaArchetype": persona["questionnaireVersion"].split(":")[-1],
                    },
                }
            )
            persona_index += 1
    return jobs


def build_persona(index: int, rng: random.Random) -> dict:
    archetype = PERSONA_ARCHETYPES[index % len(PERSONA_ARCHETYPES)]
    tag_affinities = select_persona_tag_affinities(archetype["tagAffinities"], rng)
    persona = {
        "distanceSensitivity": jitter(archetype["distanceSensitivity"], rng),
        "budgetSensitivity": jitter(archetype["budgetSensitivity"], rng),
        "transferSensitivity": jitter(archetype["transferSensitivity"], rng),
        "hiddenGemAffinity": jitter(archetype["hiddenGemAffinity"], rng),
        "profileConfidence": round(rng.uniform(0.68, 0.90), 2),
        "tagAffinities": tag_affinities,
        "newUser": False,
        "questionnaireVersion": f"sim-persona-v1:{archetype['name']}",
    }
    return persona


def build_request(index: int, rng: random.Random, city_keys: list[str], base_date: datetime) -> dict:
    city_key = rng.choice(city_keys)
    city = CITY_PRESETS[city_key]
    area = deepcopy(rng.choice(city["areas"]))
    template = deepcopy(REQUEST_TEMPLATES[index % len(REQUEST_TEMPLATES)])
    departure = base_date + timedelta(days=rng.randrange(28), hours=template.pop("hour"))
    duration_minutes = template["durationMinutes"]
    interest_tags = select_request_interest_tags(template["interestTags"], rng)
    meal_windows = feasible_meal_windows(departure, duration_minutes)
    if not meal_windows:
        interest_tags = [tag_code for tag_code in interest_tags if not is_food_tag(tag_code)]
    return {
        "areaMode": "AUTO_RADIUS",
        "areaLabel": area["areaLabel"],
        "center": area["center"],
        "areaPolygonGcj02": [],
        "routeCityName": city["routeCityName"],
        "routeCityAdcode": city["routeCityAdcode"],
        "departureTime": departure.strftime("%Y-%m-%dT%H:%M:%S"),
        "durationMinutes": duration_minutes,
        "transportProfile": template["transportProfile"],
        "routeGoal": template["routeGoal"],
        "budgetLevel": template["budgetLevel"],
        "interestTags": interest_tags,
        "mealWindows": meal_windows,
        "mustVisitPoints": [],
    }


def select_request_interest_tags(base_tags: list[str], rng: random.Random) -> list[str]:
    target_bucket_count = rng.randint(REQUEST_GLOBAL_INTEREST_BUCKET_MIN, REQUEST_GLOBAL_INTEREST_BUCKET_MAX)
    primary_tags = unique_tags(base_tags[:REQUEST_PRIMARY_TAG_COUNT])
    selected = []
    for tag_code in primary_tags:
        if len(global_interest_buckets(selected)) >= target_bucket_count:
            break
        if can_add_request_tag(selected, tag_code):
            selected.append(tag_code)
    candidates = unique_tags(base_tags[REQUEST_PRIMARY_TAG_COUNT:] + [
        tag_code for tag_code in INTEREST_TAG_CODES if tag_code not in selected
    ])
    rng.shuffle(candidates)
    for tag_code in candidates:
        if len(global_interest_buckets(selected)) >= target_bucket_count:
            break
        if can_add_request_tag(selected, tag_code):
            selected.append(tag_code)
    selected = expand_food_tags(selected, candidates, rng)
    return selected


def select_persona_tag_affinities(base_affinities: dict[str, float], rng: random.Random) -> dict[str, float]:
    target_count = rng.randint(PERSONA_TAG_AFFINITY_MIN, PERSONA_TAG_AFFINITY_MAX)
    target_count = min(target_count, len(INTEREST_TAG_CODES))
    affinity_pool = dict(base_affinities)
    missing_tags = [tag_code for tag_code in INTEREST_TAG_CODES if tag_code not in affinity_pool]
    extra_count = max(0, target_count - len(affinity_pool))
    for tag_code in rng.sample(missing_tags, extra_count):
        affinity_pool[tag_code] = round(
            rng.uniform(PERSONA_EXTRA_TAG_AFFINITY_MIN, PERSONA_EXTRA_TAG_AFFINITY_MAX),
            2,
        )

    ranked_tags = sorted(affinity_pool, key=lambda tag_code: affinity_pool[tag_code], reverse=True)
    selected = ranked_tags[:min(PERSONA_CORE_TAG_COUNT, target_count)]
    candidates = [tag_code for tag_code in ranked_tags if tag_code not in selected]
    selected.extend(rng.sample(candidates, target_count - len(selected)))
    selected.sort(key=lambda tag_code: affinity_pool[tag_code], reverse=True)
    return {
        tag_code: jitter(affinity_pool[tag_code], rng)
        for tag_code in selected
    }


def unique_tags(tags: list[str]) -> list[str]:
    return list(dict.fromkeys(tags))


def can_add_request_tag(selected: list[str], tag_code: str) -> bool:
    if tag_code in selected:
        return False
    candidate_buckets = global_interest_buckets(selected + [tag_code])
    if len(candidate_buckets) > REQUEST_GLOBAL_INTEREST_BUCKET_MAX:
        return False
    if not is_food_tag(tag_code):
        return True
    if food_parent_child_conflicts(selected, tag_code):
        return False
    return sum(1 for selected_tag in selected if is_food_tag(selected_tag)) < REQUEST_FOOD_INTEREST_TAG_MAX


def is_food_tag(tag_code: str) -> bool:
    return tag_code.startswith("FOOD_")


def global_interest_buckets(tags: list[str]) -> set[str]:
    return {"FOOD" if is_food_tag(tag_code) else tag_code for tag_code in tags}


def expand_food_tags(selected: list[str], candidates: list[str], rng: random.Random) -> list[str]:
    if not any(is_food_tag(tag_code) for tag_code in selected):
        return selected
    food_target = rng.randint(1, REQUEST_FOOD_INTEREST_TAG_MAX)
    expanded = list(selected)
    food_candidates = [tag_code for tag_code in candidates if is_food_tag(tag_code)]
    rng.shuffle(food_candidates)
    for tag_code in food_candidates:
        if sum(1 for selected_tag in expanded if is_food_tag(selected_tag)) >= food_target:
            break
        if can_add_request_tag(expanded, tag_code):
            expanded.append(tag_code)
    return expanded


def feasible_meal_windows(departure: datetime, duration_minutes: int) -> list[str]:
    route_end = departure + timedelta(minutes=duration_minutes)
    result = []
    current_date = departure.date()
    while current_date <= route_end.date():
        for meal_window, (start, end) in MEAL_WINDOW_TIMES.items():
            window_start = datetime.combine(current_date, start)
            window_end = datetime.combine(current_date, end)
            if departure < window_end and route_end > window_start and meal_window not in result:
                result.append(meal_window)
        current_date += timedelta(days=1)
    return result


def food_parent_child_conflicts(selected: list[str], tag_code: str) -> bool:
    ancestors = food_ancestors(tag_code)
    if any(selected_tag in ancestors for selected_tag in selected):
        return True
    return any(tag_code in food_ancestors(selected_tag) for selected_tag in selected)


def food_ancestors(tag_code: str) -> set[str]:
    ancestors = set()
    parent = FOOD_PARENT_BY_TAG.get(tag_code)
    while parent:
        ancestors.add(parent)
        parent = FOOD_PARENT_BY_TAG.get(parent)
    return ancestors


def jitter(value: float, rng: random.Random, width: float = 0.08) -> float:
    return round(min(0.95, max(0.05, value + rng.uniform(-width, width))), 2)
