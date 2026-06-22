from __future__ import annotations

from datetime import datetime


SYSTEM_PROMPT = """你是一个真实的城市漫步用户。系统为你生成了几条候选路线（A/B/C/D/E），
你要像挑选自己今天真正想走的那条一样，对它们排序，并指出哪些值得推荐、
哪些明显不该推荐。你不是规划师，不要改路线、不要新增地点、不要复算分数。

判断只能基于你作为用户的真实体验感：兴趣是否对味、目标是否贴合、是否有趣不重复、
走法顺不顺、时间安排合不合理、累不累、花费合不合适、有没有风险。

评价兴趣覆盖时，只能把 POI 的真实语义当证据：primaryCategoryGroup、categoryGroups、
semanticTags、poiTagHits、mealCandidate、restCandidate、localExperienceCandidate、
routeRole、intendedMealWindow、价格、交通、距离、时间和 fallback。matchedInterestTags、
recallSources、搜索计划名、召回计划名只能说明候选点从哪里被找到，不能当作用户兴趣被满足的证据。

你需要按这几类依据综合排序：是否满足兴趣、是否符合 routeGoal、饭点/休息是否合理、
预算/交通/距离是否合理、为什么更偏好某条路线。最终仍只输出结构化 JSON 字段，
不要输出自然语言解释。

ranking 是主要训练信号。reasonCodes 只是给明显不该推荐的 rejectedRouteCodes 写弱解释；
如果一条路线只是比其他路线弱，但还不算明显不该推荐，可以只把它排后，不必强行 reject。

reasonCodes 必须是 JSON 对象，key 只能是 rejectedRouteCodes 里的 routeCode，value 是 reason code 字符串数组。
即使只有一个理由，也必须写成 {"C": ["HIGH_FATIGUE"]}，不能写成 ["HIGH_FATIGUE"] 或 [{"routeCode":"C","codes":[...]}]。
acceptedRouteCodes 里的路线不要出现在 reasonCodes；如果一条路线值得推荐，就不要再给它写负向 reason code。

reasonCodes 只能从下面 8 个里选，不许自创：
LOW_INTEREST_COVERAGE / WEAK_GOAL_FIT / LOW_DIVERSITY / BAD_SPATIAL_FLOW /
BAD_TIME_STRUCTURE / HIGH_FATIGUE / BUDGET_MISMATCH / HIGH_ROUTE_RISK

BUDGET_MISMATCH 只在路线预算明显高于本次 budgetLevel，或明显高于其他候选路线时使用；
routeGoal 不表达预算，预算只看 budgetLevel。
如果主要问题是步行距离过长、单段太远、总时长吃紧，应优先使用 HIGH_FATIGUE 或 BAD_SPATIAL_FLOW。

ranking 必须包含本批所有候选 routeCode，按从最想选到最不想选排序；
acceptedRouteCodes 和 rejectedRouteCodes 只是从 ranking 中分别摘出值得推荐和明显不该推荐的路线。
即使路线被拒绝，也必须出现在 ranking 的靠后位置，不能从 ranking 里省略。

只输出 JSON，不要任何解释性文字。JSON 只能包含 ranking、acceptedRouteCodes、rejectedRouteCodes、reasonCodes、confidence 五个字段。
输出示例：
{
  "ranking": ["A", "B", "C"],
  "acceptedRouteCodes": ["A"],
  "rejectedRouteCodes": ["C"],
  "reasonCodes": {
    "C": ["HIGH_FATIGUE", "BAD_SPATIAL_FLOW"]
  },
  "confidence": 0.72
}"""


TRANSPORT_LABELS = {
    "WALK": "步行",
    "BIKE": "骑行",
    "BUS": "公交",
    "SUBWAY": "地铁",
    "TRANSIT": "公共交通",
    "TAXI": "打车",
    "DRIVE": "驾车",
}


TAG_LABELS = {
    "LOCAL": "本地生活",
    "FOOD_CHINESE": "中餐",
    "FOOD_FOREIGN": "异国餐",
    "FOOD_FAST_FOOD": "快餐",
    "FOOD_SICHUAN": "川菜",
    "FOOD_CANTONESE": "粤菜",
    "FOOD_SHANDONG": "鲁菜",
    "FOOD_JIANGSU": "苏菜",
    "FOOD_ZHEJIANG": "浙菜",
    "FOOD_HUNAN": "湘菜",
    "FOOD_DONG_BEI": "东北菜",
    "FOOD_OLD_BRAND": "老字号",
    "FOOD_HOT_POT": "火锅",
    "FOOD_LOCAL_FLAVOR": "本地风味",
    "FOOD_HALAL": "清真",
    "FOOD_WESTERN": "西餐",
    "FOOD_AMERICAN": "美式",
    "FOOD_INDIAN": "印度菜",
    "FOOD_MEXICAN": "墨西哥菜",
    "COFFEE": "咖啡",
    "CULTURE": "文化",
    "MUSEUM": "展馆文化",
    "SCENIC": "景点地标",
    "PHOTO": "拍照",
    "SHOPPING": "购物",
    "NIGHT": "夜游",
    "ENTERTAINMENT": "娱乐",
    "EVENT": "活动",
}


def build_user_prompt(route_request: dict, route_generation: dict, persona: dict | None) -> str:
    routes = route_generation.get("routes") or []
    route_codes = [route.get("routeCode") for route in routes if isinstance(route, dict) and route.get("routeCode")]
    return "\n\n".join(
        [
            "【本次请求】\n" + render_request(route_request),
            "【你的偏好】\n" + render_persona(persona, route_request),
            "【候选路线】\n" + render_routes(routes),
            "【输出硬性约束】\n" + render_output_constraints(route_codes),
            "请只输出 JSON，字段为 ranking、acceptedRouteCodes、rejectedRouteCodes、reasonCodes、confidence。"
            "reasonCodes 必须是对象，例如 {\"C\": [\"HIGH_FATIGUE\"]}，不能是数组。",
        ]
    )


def render_request(route_request: dict) -> str:
    interest_tags = route_request.get("interestTags") or []
    meal_windows = route_request.get("mealWindows") or []
    departure = route_request.get("departureTime")
    return (
        f"目标: {route_request.get('routeGoal')}        交通方式: {route_request.get('transportProfile')}\n"
        f"预算档: {route_request.get('budgetLevel', 'NORMAL')}                 兴趣: {', '.join(interest_tags) or '未指定'}\n"
        f"饭点: {', '.join(meal_windows) or '未选择正餐饭点'}\n"
        f"出发: {format_departure(departure)}              时长: {route_request.get('durationMinutes')} 分钟\n"
        f"城市/区域: {route_request.get('routeCityName') or '未知'} / {route_request.get('areaLabel') or '未知'}\n"
        f"兴趣解读: {render_interest_intent(interest_tags)}"
    )


def render_persona(persona: dict | None, route_request: dict | None = None) -> str:
    route_request = route_request or {}
    if not persona:
        distance_attitude = render_distance_attitude(route_request.get("transportProfile"), 0.5)
        return "你按本次请求中的目标、预算、交通方式和兴趣标签做选择。" + distance_attitude
    lines = []
    budget = float(persona.get("budgetSensitivity") or 0)
    distance = float(persona.get("distanceSensitivity") or 0)
    transfer = float(persona.get("transferSensitivity") or 0)
    hidden = float(persona.get("hiddenGemAffinity") or 0)
    if budget >= 0.6:
        lines.append("你很在意花费，偏好低预算。")
    elif budget >= 0.4:
        lines.append("你对花费有点敏感。")
    if distance >= 0.6:
        lines.append("走太多路或长距离步行会让你很累。")
    if transfer >= 0.6:
        lines.append("你不喜欢频繁换乘、绕路。")
    if hidden >= 0.6:
        lines.append("你偏好小众、本地人才知道的地方，不追热门打卡。")
    elif hidden < 0.4:
        lines.append("你更喜欢经典、热门、有名的地方。")

    tag_affinities = persona.get("tagAffinities") or {}
    liked = [
        TAG_LABELS.get(code, code)
        for code, score in tag_affinities.items()
        if float(score or 0) >= 0.6
    ]
    if liked:
        lines.append("尤其喜欢：" + "、".join(liked) + "。")
    request_context = render_persona_request_context(persona, route_request)
    if request_context:
        lines.append(request_context)
    distance_attitude = render_distance_attitude(route_request.get("transportProfile"), distance)
    if distance_attitude:
        lines.append(distance_attitude)
    return "".join(lines) if lines else "你按本次请求中的目标、预算、交通方式和兴趣标签做选择。"


def render_distance_attitude(transport_profile: str | None, distance_sensitivity: float) -> str:
    band = distance_sensitivity_band(distance_sensitivity)
    if transport_profile == "WALK_ONLY":
        if band == "LOW":
            return "WALK_ONLY 仍按步行舒适判断：可以接受适度多走，但不能把明显长距离步行当成可接受。"
        if band == "HIGH":
            return "你选择 WALK_ONLY 且对距离敏感：优先选择近、顺、少走路的路线，长距离步行应明显降分。"
        return "你选择 WALK_ONLY：路线应保持近、顺、步行负担可控。"

    if transport_profile == "WALK_BUS":
        if band == "HIGH":
            return "你选择 WALK_BUS 但对距离敏感：公交只能支持轻度展开，过长步行、绕路和折返仍是负面。"
        if band == "LOW":
            return "你选择 WALK_BUS 且对距离不敏感：可以接受轻度展开，但远一点必须换来更好的 POI 或更贴合兴趣。"
        return "你选择 WALK_BUS：可以接受比纯步行稍远，但不要为了远而远。"

    if transport_profile in {"WALK_SUBWAY", "WALK_TRANSIT"}:
        if band == "HIGH":
            return f"你选择 {transport_profile} 但对距离敏感：跨片区可以接受，但只有明显更好的 POI 才值得多走或多坐车。"
        if band == "LOW":
            return f"你选择 {transport_profile} 且对距离不敏感：可以接受合理跨片区，前提是远一点换来明显更好的 POI。"
        return f"你选择 {transport_profile}：中等跨片区是合理的，但路线仍要顺。"

    if transport_profile == "BIKE_SUBWAY":
        if band == "HIGH":
            return "你选择 BIKE_SUBWAY 但对距离敏感：骑行接驳能缓解距离，但长跳和折返仍应降分。"
        if band == "LOW":
            return "你选择 BIKE_SUBWAY 且对距离不敏感：可以接受更分散的片区，前提是 POI 质量和兴趣命中明显更好。"
        return "你选择 BIKE_SUBWAY：可以接受中高程度展开，但片区衔接要自然。"

    if transport_profile == "WALK_TAXI":
        if band == "HIGH":
            return "你平时对距离敏感，但这次选择 WALK_TAXI，表示愿意为了更值得的地点出远门；远距离需要换来明确的兴趣命中或地点质量，无意义绕路、折返仍应降分。"
        if band == "LOW":
            return "你选择 WALK_TAXI 且对距离不敏感：可以接受远一点换来明显更好的 POI，但不要因为能打车就接受无意义绕路或来回跳片区。"
        return "你这次选择 WALK_TAXI，说明可以为了明显更好的 POI 接受更远；但路线仍要顺，不接受没有质量收益的绕路和折返。"

    return ""


def distance_sensitivity_band(distance_sensitivity: float) -> str:
    if distance_sensitivity < 0.35:
        return "LOW"
    if distance_sensitivity > 0.65:
        return "HIGH"
    return "MEDIUM"


def render_interest_intent(interest_tags: list[str]) -> str:
    if not interest_tags:
        return "没有显式兴趣标签，按路线目标、交通、预算和 POI 质量综合判断。"
    food_tags = [tag for tag in interest_tags if is_food_tag(tag)]
    non_food_tags = [tag for tag in interest_tags if not is_food_tag(tag)]
    parts = []
    if food_tags:
        food_labels = "、".join(TAG_LABELS.get(tag, tag) for tag in food_tags)
        parts.append(
            f"本次餐饮偏好是 {food_labels}；评价时优先看 poiTagHits 是否命中这些 FOOD 子标签，"
            "不能只因为 primaryCategoryGroup=FOOD 就算完全满足。"
        )
        parts.append("同一 FOOD 父类或其他餐饮只能算弱替代，除非路线在饭点、价格和整体体验上明显更好。")
    if non_food_tags:
        parts.append("其他显式兴趣是 " + "、".join(TAG_LABELS.get(tag, tag) for tag in non_food_tags) + "。")
    return "".join(parts)


def render_persona_request_context(persona: dict, route_request: dict) -> str:
    request_tags = route_request.get("interestTags") or []
    request_tag_set = set(request_tags)
    tag_affinities = persona.get("tagAffinities") or {}
    persona_liked_tags = {
        tag for tag, score in tag_affinities.items()
        if float(score or 0) >= 0.6
    }
    request_liked = [tag for tag in request_tags if tag in persona_liked_tags]
    request_new = [tag for tag in request_tags if tag not in persona_liked_tags]
    parts = ["本次 request 是你此刻的明确意图，优先级高于长期画像；长期画像用于解释你会怎样权衡距离、预算、换乘和小众程度。"]
    if request_liked:
        parts.append(
            "本次兴趣里和你长期偏好一致的是："
            + "、".join(TAG_LABELS.get(tag, tag) for tag in request_liked)
            + "。"
        )
    if request_new:
        parts.append(
            "本次还临时选择了："
            + "、".join(TAG_LABELS.get(tag, tag) for tag in request_new)
            + "；这些也要认真评价，不要因为长期画像里分数不高就忽略。"
        )
    persona_only = [
        tag for tag in persona_liked_tags
        if tag not in request_tag_set
    ]
    if persona_only:
        parts.append(
            "长期喜欢但本次没显式选择的兴趣只能作为加分项，不能覆盖本次 request："
            + "、".join(TAG_LABELS.get(tag, tag) for tag in persona_only[:3])
            + "。"
        )
    food_request_tags = [tag for tag in request_tags if is_food_tag(tag)]
    if food_request_tags:
        parts.append(
            "如果候选路线没有命中本次 FOOD 子标签，应降低兴趣匹配判断；只有当它用同父类餐饮、饭点安排和整体路线质量形成合理替代时，才不要直接判死。"
        )
    return "".join(parts)


def is_food_tag(tag_code: str) -> bool:
    return tag_code.startswith("FOOD_")


def render_routes(routes: list[dict]) -> str:
    if not routes:
        return "无候选路线。"
    return "\n\n".join(render_route(route) for route in routes)


def render_output_constraints(route_codes: list[str]) -> str:
    if not route_codes:
        return "ranking 必须包含本批所有候选 routeCode，不能只输出推荐路线。"
    return (
        f"本批候选 routeCode 共 {len(route_codes)} 条：{', '.join(route_codes)}。\n"
        f"ranking 必须正好包含这 {len(route_codes)} 个 routeCode，不能缺失、不能重复、不能新增；"
        "顺序是从最想选到最不想选。\n"
        "acceptedRouteCodes/rejectedRouteCodes 可以只包含部分路线，但 ranking 不能只输出 acceptedRouteCodes。\n"
        "reasonCodes 只能给 rejectedRouteCodes 中的路线；acceptedRouteCodes 中的路线不要写 reasonCodes。"
    )


def render_route(route: dict) -> str:
    stops = route.get("stops") or []
    stop_lines = [render_stop(stop, index, len(stops)) for index, stop in enumerate(stops)]
    segment_lines = render_segments(route.get("segments") or [])
    budget_cent = route.get("budgetCent")
    budget_text = "未知" if budget_cent is None else f"¥{budget_cent / 100:.0f}"
    warnings = route.get("warnings") or route.get("globalWarnings") or []
    warnings_text = "；".join(str(warning) for warning in warnings[:3]) if warnings else "无"
    return (
        f"路线 {route.get('routeCode')}: {route.get('title')}\n"
        f"  概述: {route.get('summary')}\n"
        f"  说明: {route.get('explanation')}\n"
        f"  行程:\n" + "\n".join(stop_lines) + "\n"
        f"{segment_lines}"
        f"  合计: 总时长 {route.get('totalDurationMinutes')}min / 总距离 {route.get('totalDistanceMeters')}m / "
        f"预算 {budget_text} / 风险 {route.get('riskLevel')} / warnings: {warnings_text}"
    )


def render_stop(stop: dict, index: int, stop_count: int) -> str:
    label = stop.get("slotLabel") or stop.get("category") or "地点"
    stay = stop.get("stayMinutes")
    description = truncate(stop.get("description") or stop.get("reason") or "", 70)
    semantic = render_stop_semantics(stop)
    if index == stop_count - 1:
        next_text = "终点，无下一段"
    else:
        mode = TRANSPORT_LABELS.get(stop.get("transportToNext"), stop.get("transportToNext") or "未知交通")
        distance = stop.get("distanceToNextMeters")
        duration = stop.get("durationToNextMinutes")
        duration_text = f" / {duration}min" if duration is not None else ""
        next_text = f"{mode} {distance}m{duration_text}" if distance is not None else mode
    extra = f"，{description}" if description else ""
    return f"    {index + 1}. {stop.get('name')}（{label}{semantic}）停留 {stay}min{extra} → {next_text}"


def render_stop_semantics(stop: dict) -> str:
    fields = []
    for key in ("routeRole", "intendedMealWindow", "primaryCategoryGroup"):
        value = stop.get(key)
        if value:
            fields.append(f"{key}={value}")
    for key in ("categoryGroups", "semanticTags", "poiTagHits"):
        value = stop.get(key)
        if value:
            fields.append(f"{key}={', '.join(str(item) for item in value)}")
    for key in ("mealCandidate", "restCandidate", "localExperienceCandidate"):
        if key in stop:
            fields.append(f"{key}={bool(stop.get(key))}")
    if stop.get("avgPriceCent") is not None:
        fields.append(f"avgPrice=¥{stop.get('avgPriceCent') / 100:.0f}")
    if stop.get("fallback") is not None:
        fields.append(f"fallback={stop.get('fallback')}")
    return "" if not fields else "；" + "；".join(fields)


def render_segments(segments: list[dict]) -> str:
    if not segments:
        return ""
    lines = []
    for segment in segments:
        mode = TRANSPORT_LABELS.get(segment.get("mode"), segment.get("mode") or "未知交通")
        source = segment.get("source")
        source_text = f" / source={source}" if source else ""
        fallback = segment.get("fallback") or segment.get("fallbackReason")
        fallback_text = f" / fallback={fallback}" if fallback else ""
        lines.append(
            f"    段 {segment.get('order')}: {mode} {segment.get('distanceMeters')}m / "
            f"{segment.get('durationMinutes')}min{source_text}{fallback_text}"
        )
    return "  交通段:\n" + "\n".join(lines) + "\n"


def truncate(text: str, max_length: int) -> str:
    if len(text) <= max_length:
        return text
    return text[: max_length - 1] + "…"


def format_departure(value: str | None) -> str:
    if not value:
        return "未知"
    if value.endswith("Z") or "+" in value[10:] or "-" in value[10:]:
        return value
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError:
        return value
    return parsed.strftime("%Y-%m-%d %H:%M（北京时间本地）")
