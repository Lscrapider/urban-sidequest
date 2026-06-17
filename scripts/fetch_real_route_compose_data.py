#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


AMAP_BASE_URL = "https://restapi.amap.com"
DEFAULT_OUTPUT_FILE = "route_fallback_test_data.json"
DEFAULT_CENTER = {
    "longitudeGcj02": 121.4737,
    "latitudeGcj02": 31.2304
}
DEFAULT_BOUNDS = {
    "southwest": {
        "longitudeGcj02": 121.4215,
        "latitudeGcj02": 31.1912
    },
    "northeast": {
        "longitudeGcj02": 121.5462,
        "latitudeGcj02": 31.2715
    }
}


POI_PLANS = [
    {
        "keywords": ["上海博物馆"],
        "category": "CULTURE",
        "role": "ANCHOR",
        "tags": ["MUSEUM", "CULTURE", "INDOOR"],
        "features": ["文化展览", "室内游览"],
        "limit": 1
    },
    {
        "keywords": ["博物馆", "美术馆", "展览馆"],
        "category": "CULTURE",
        "role": "ANCHOR",
        "tags": ["MUSEUM", "CULTURE", "INDOOR"],
        "features": ["文化展览", "室内游览"],
        "limit": 8
    },
    {
        "keywords": ["外滩", "豫园", "公园", "景点"],
        "category": "SCENIC",
        "role": "ANCHOR",
        "tags": ["SCENIC", "PHOTO", "CITY_LANDMARK"],
        "features": ["城市景观", "适合拍照", "经典游览"],
        "limit": 8
    },
    {
        "keywords": ["田子坊", "新天地", "武康路", "思南公馆", "上生新所", "外滩源"],
        "category": "LOCAL",
        "role": "LOCAL",
        "tags": ["LOCAL", "CITY_WALK"],
        "features": ["本地街区", "城市漫步", "烟火气"],
        "limit": 8
    },
    {
        "keywords": ["本帮菜", "小吃", "面馆", "餐厅", "美食街"],
        "category": "FOOD",
        "role": "MEAL",
        "tags": ["FOOD", "LOCAL"],
        "features": ["餐饮", "适合饭点停留"],
        "limit": 8
    },
    {
        "keywords": ["咖啡", "咖啡馆", "甜品"],
        "category": "REST",
        "role": "REST",
        "tags": ["COFFEE", "REST"],
        "features": ["咖啡休息", "短暂停留", "节奏缓冲"],
        "limit": 6
    }
]


def read_default_amap_key():
    app_yml = Path(__file__).resolve().parents[1] / "backend/src/main/resources/application.yml"
    text = app_yml.read_text(encoding="utf-8")
    match = re.search(r"key:\s*\$\{AMAP_WEB_KEY:([^}]+)}", text)
    if not match:
        return None
    return match.group(1).strip()


def request_json(path, params, timeout):
    query = urllib.parse.urlencode(params, doseq=False)
    url = AMAP_BASE_URL + path + "?" + query
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def amap_polygon_param(bounds):
    sw = bounds["southwest"]
    ne = bounds["northeast"]
    nw = {
        "longitudeGcj02": sw["longitudeGcj02"],
        "latitudeGcj02": ne["latitudeGcj02"]
    }
    se = {
        "longitudeGcj02": ne["longitudeGcj02"],
        "latitudeGcj02": sw["latitudeGcj02"]
    }
    points = [sw, nw, ne, se]
    return "|".join(
        f"{point['longitudeGcj02']},{point['latitudeGcj02']}"
        for point in points
    )


def search_polygon(key, bounds, keywords, page_num, page_size, timeout):
    return request_json(
        "/v5/place/polygon",
        {
            "key": key,
            "polygon": amap_polygon_param(bounds),
            "keywords": "|".join(keywords),
            "page_num": page_num,
            "page_size": page_size,
            "show_fields": "business,photos"
        },
        timeout
    )


def normalize_location(location):
    if not location or "," not in location:
        return None
    lng, lat = location.split(",", 1)
    return {
        "longitudeGcj02": round(float(lng), 7),
        "latitudeGcj02": round(float(lat), 7)
    }


def poi_identity(raw_poi):
    amap_id = raw_poi.get("id") or ""
    if amap_id:
        return amap_id
    return raw_poi.get("name", "") + "|" + raw_poi.get("location", "")


def stable_poi_id(raw_poi):
    identity = poi_identity(raw_poi)
    digest = hashlib.sha1(identity.encode("utf-8")).hexdigest()[:10]
    return "amap-" + digest


def parse_rating(raw_poi):
    rating = raw_poi.get("business", {}).get("rating")
    try:
        return round(float(rating), 1)
    except (TypeError, ValueError):
        return None


def parse_avg_price_cent(raw_poi):
    cost = raw_poi.get("business", {}).get("cost")
    try:
        value = float(cost)
    except (TypeError, ValueError):
        return None
    if value <= 0:
        return None
    return int(round(value * 100))


def to_candidate(raw_poi, plan):
    location = normalize_location(raw_poi.get("location"))
    if location is None:
        return None
    name = raw_poi.get("name") or ""
    if not name:
        return None
    if "暂停开放" in name:
        return None
    return {
        "poiId": stable_poi_id(raw_poi),
        "amapPoiId": raw_poi.get("id") or None,
        "name": name,
        "category": plan["category"],
        "role": plan["role"],
        "location": location,
        "address": raw_poi.get("address") or None,
        "type": raw_poi.get("type") or None,
        "typecode": raw_poi.get("typecode") or None,
        "rating": parse_rating(raw_poi),
        "avgPriceCent": parse_avg_price_cent(raw_poi),
        "tags": plan["tags"],
        "features": plan["features"],
        "nearestTransit": [],
        "transitAccessibility": "UNKNOWN",
        "reasonSeed": "高德真实 POI 搜索：" + "、".join(plan["keywords"])
    }


def collect_pois(key, bounds, timeout):
    candidates = {}
    for plan in POI_PLANS:
        for page_num in range(1, 4):
            response = search_polygon(key, bounds, plan["keywords"], page_num, 25, timeout)
            pois = response.get("pois") or []
            for raw_poi in pois:
                candidate = to_candidate(raw_poi, plan)
                if candidate is None:
                    continue
                identity = poi_identity(raw_poi)
                candidates.setdefault(identity, candidate)
            if len(pois) < 25:
                break
        time.sleep(0.1)
    selected = list(candidates.values())
    return select_balanced_candidates(selected)


def select_balanced_candidates(candidates):
    candidates.sort(key=lambda item: (-(item["rating"] or 0), item["name"]))
    must_visit = next(
        (
            candidate for candidate in candidates
            if "上海博物馆" in candidate["name"] and "暂停开放" not in candidate["name"]
        ),
        None
    )
    if must_visit is not None:
        must_visit["role"] = "MUST_VISIT"
        must_visit["tags"] = sorted(set(must_visit["tags"] + ["MUST_VISIT"]))
        must_visit["features"] = sorted(set(must_visit["features"] + ["用户指定必去点"]))
        must_visit["reasonSeed"] = "用户指定必去点，高德真实 POI"

    quotas = {
        "CULTURE": 7,
        "SCENIC": 8,
        "LOCAL": 8,
        "FOOD": 8,
        "REST": 6
    }
    result = []
    seen_ids = set()
    if must_visit is not None:
        result.append(must_visit)
        seen_ids.add(must_visit["poiId"])
        quotas["CULTURE"] = max(0, quotas["CULTURE"] - 1)

    for category, quota in quotas.items():
        for candidate in candidates:
            if len([item for item in result if item["category"] == category]) >= quota:
                break
            if candidate["poiId"] in seen_ids or candidate["category"] != category:
                continue
            result.append(candidate)
            seen_ids.add(candidate["poiId"])

    if len(result) < 32:
        for candidate in candidates:
            if candidate["poiId"] in seen_ids:
                continue
            result.append(candidate)
            seen_ids.add(candidate["poiId"])
            if len(result) >= 32:
                break
    return result[:40]


def collect_transit(key, bounds, timeout):
    transit = []
    for transit_type, keywords in [
        ("SUBWAY", ["地铁站"]),
        ("BUS", ["公交站"])
    ]:
        for page_num in range(1, 6):
            response = search_polygon(key, bounds, keywords, page_num, 25, timeout)
            pois = response.get("pois") or []
            for raw_poi in pois:
                location = normalize_location(raw_poi.get("location"))
                name = raw_poi.get("name") or ""
                if location is None or not name:
                    continue
                transit.append({
                    "type": transit_type,
                    "name": name,
                    "location": location,
                    "amapPoiId": raw_poi.get("id") or None
                })
            if len(pois) < 25:
                break
        time.sleep(0.1)
    return transit


def distance_meters(left, right):
    radius = 6371008.8
    lat1 = math.radians(left["latitudeGcj02"])
    lat2 = math.radians(right["latitudeGcj02"])
    dlat = lat2 - lat1
    dlng = math.radians(right["longitudeGcj02"] - left["longitudeGcj02"])
    value = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return int(round(2 * radius * math.asin(math.sqrt(value))))


def attach_nearest_transit(candidates, transit):
    for candidate in candidates:
        ranked = sorted(
            (
                {
                    "type": item["type"],
                    "name": item["name"],
                    "distanceMeters": distance_meters(candidate["location"], item["location"])
                }
                for item in transit
            ),
            key=lambda item: (item["distanceMeters"], item["type"])
        )
        subway = [item for item in ranked if item["type"] == "SUBWAY"][:1]
        buses = [item for item in ranked if item["type"] == "BUS"][:2]
        merged = sorted(subway + buses, key=lambda item: item["distanceMeters"])[:3]
        candidate["nearestTransit"] = merged
        nearest = merged[0]["distanceMeters"] if merged else None
        if nearest is None:
            candidate["transitAccessibility"] = "UNKNOWN"
        elif nearest <= 300:
            candidate["transitAccessibility"] = "HIGH"
        elif nearest <= 800:
            candidate["transitAccessibility"] = "MEDIUM"
        else:
            candidate["transitAccessibility"] = "LOW"


def build_output(candidates, transit, bounds):
    must_visit_ids = [
        candidate["poiId"] for candidate in candidates
        if candidate["role"] == "MUST_VISIT"
    ][:1]
    return {
        "request": {
            "areaMode": "AUTO_RADIUS",
            "areaLabel": "上海市中心",
            "center": DEFAULT_CENTER,
            "radiusMeters": 12000,
            "routeCityName": "上海",
            "routeCityAdcode": "310000",
            "departureTime": "2026-06-17T02:00:00Z",
            "durationMinutes": 480,
            "transportProfile": "WALK_TAXI",
            "routeGoal": "LOCAL",
            "interestTags": ["SCENIC", "MUSEUM", "LOCAL", "COFFEE", "FOOD"],
            "mustVisitPoiIds": must_visit_ids,
            "routeCountRange": {
                "min": 3,
                "max": 5
            }
        },
        "mealWindows": [
            {
                "type": "LUNCH",
                "start": "11:30",
                "end": "13:30"
            },
            {
                "type": "DINNER",
                "start": "17:30",
                "end": "20:00"
            }
        ],
        "stayTimeGuidance": {
            "CULTURE": {
                "minMinutes": 60,
                "maxMinutes": 90
            },
            "SCENIC": {
                "minMinutes": 45,
                "maxMinutes": 75
            },
            "FOOD": {
                "minMinutes": 45,
                "maxMinutes": 75
            },
            "REST": {
                "minMinutes": 20,
                "maxMinutes": 40
            },
            "LOCAL": {
                "minMinutes": 30,
                "maxMinutes": 60
            }
        },
        "poolMetadata": {
            "source": "AMAP_WEB_SERVICE",
            "selectedCandidateCount": len(candidates),
            "transitFacilityCount": len(transit),
            "selectionStrategy": "按上海市中心矩形区域高德搜索真实 POI，再绑定区域内最近交通设施。",
            "transportSearchBounds": bounds
        },
        "transportPolicy": {
            "profile": "WALK_TAXI",
            "nearestTransitLimit": 3,
            "subwayPreferred": False,
            "busPreferred": False,
            "notes": "步行加打车模式下，交通设施用于判断集合点和步行友好程度，不作为硬约束。"
        },
        "poiPool": candidates
    }


def parse_args():
    parser = argparse.ArgumentParser(description="从高德接口生成真实 POI 池测试数据。")
    parser.add_argument(
        "--output",
        default=str(Path(__file__).with_name(DEFAULT_OUTPUT_FILE)),
        help="输出 JSON 路径。"
    )
    parser.add_argument(
        "--api-key-env",
        default="AMAP_WEB_KEY",
        help="读取高德 Web Key 的环境变量名。"
    )
    parser.add_argument("--timeout", type=int, default=12, help="接口超时时间。")
    return parser.parse_args()


def main():
    args = parse_args()
    key = os.environ.get(args.api_key_env) or read_default_amap_key()
    if not key:
        print("缺少高德 Web Key，请设置环境变量：" + args.api_key_env, file=sys.stderr)
        return 2
    candidates = collect_pois(key, DEFAULT_BOUNDS, args.timeout)
    transit = collect_transit(key, DEFAULT_BOUNDS, args.timeout)
    attach_nearest_transit(candidates, transit)
    output = build_output(candidates, transit, DEFAULT_BOUNDS)
    output_path = Path(args.output)
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"写入 {output_path}，POI {len(candidates)} 个，交通设施 {len(transit)} 个")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
