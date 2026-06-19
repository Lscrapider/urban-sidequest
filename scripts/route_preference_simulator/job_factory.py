from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timedelta, timezone
import random

from .presets import CITY_PRESETS, PERSONA_ARCHETYPES, REQUEST_TEMPLATES


def build_jobs(
    persona_count: int = 100,
    requests_per_persona: int = 20,
    seed: int = 20260619,
    city_keys: list[str] | None = None,
) -> list[dict]:
    rng = random.Random(seed)
    selected_city_keys = city_keys or list(CITY_PRESETS.keys())
    personas = [build_persona(index, rng) for index in range(persona_count)]
    jobs = []
    base_date = datetime(2026, 6, 20, tzinfo=timezone.utc)
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


def build_persona(index: int, rng: random.Random) -> dict:
    archetype = PERSONA_ARCHETYPES[index % len(PERSONA_ARCHETYPES)]
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
    city_key = city_keys[index % len(city_keys)]
    city = CITY_PRESETS[city_key]
    area = deepcopy(city["areas"][(index // len(city_keys)) % len(city["areas"])])
    template = deepcopy(REQUEST_TEMPLATES[index % len(REQUEST_TEMPLATES)])
    departure = base_date + timedelta(days=index % 28, hours=template.pop("hour"))
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
