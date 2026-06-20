from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timedelta, timezone
import random

from .presets import CITY_PRESETS, PERSONA_ARCHETYPES, REQUEST_TEMPLATES


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
    base_date = datetime(2026, 6, 20, tzinfo=timezone.utc)
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
    archetype = rng.choice(PERSONA_ARCHETYPES)
    persona = {
        "distanceSensitivity": jitter(archetype["distanceSensitivity"], rng),
        "budgetSensitivity": jitter(archetype["budgetSensitivity"], rng),
        "transferSensitivity": jitter(archetype["transferSensitivity"], rng),
        "hiddenGemAffinity": jitter(archetype["hiddenGemAffinity"], rng),
        "profileConfidence": round(rng.uniform(0.68, 0.90), 2),
        "tagAffinities": {
            tag_code: jitter(score, rng)
            for tag_code, score in archetype["tagAffinities"].items()
        },
        "newUser": False,
        "questionnaireVersion": f"sim-persona-v1:{archetype['name']}",
    }
    return persona


def build_request(index: int, rng: random.Random, city_keys: list[str], base_date: datetime) -> dict:
    city_key = rng.choice(city_keys)
    city = CITY_PRESETS[city_key]
    area = deepcopy(rng.choice(city["areas"]))
    template = deepcopy(rng.choice(REQUEST_TEMPLATES))
    departure = base_date + timedelta(days=rng.randrange(28), hours=template.pop("hour"))
    return {
        "areaMode": "AUTO_RADIUS",
        "areaLabel": area["areaLabel"],
        "center": area["center"],
        "areaPolygonGcj02": [],
        "routeCityName": city["routeCityName"],
        "routeCityAdcode": city["routeCityAdcode"],
        "departureTime": departure.isoformat().replace("+00:00", "Z"),
        "durationMinutes": template["durationMinutes"],
        "transportProfile": template["transportProfile"],
        "routeGoal": template["routeGoal"],
        "budgetLevel": template["budgetLevel"],
        "interestTags": template["interestTags"],
        "mustVisitPoints": [],
    }


def jitter(value: float, rng: random.Random, width: float = 0.08) -> float:
    return round(min(0.95, max(0.05, value + rng.uniform(-width, width))), 2)
