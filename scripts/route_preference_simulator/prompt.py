from __future__ import annotations

from datetime import datetime, timezone


SYSTEM_PROMPT = """你是一个真实的城市漫步用户。系统为你生成了几条候选路线（A/B/C/D/E），
你要像挑选自己今天真正想走的那条一样，对它们排序，并指出哪些值得推荐、
哪些明显不该推荐。你不是规划师，不要改路线、不要新增地点、不要复算分数。

判断只能基于你作为用户的真实体验感：兴趣是否对味、目标是否贴合、是否有趣不重复、
走法顺不顺、时间安排合不合理、累不累、花费合不合适、有没有风险。

reasonCodes 必须是 JSON 对象，key 只能是 rejectedRouteCodes 里的 routeCode，value 是 reason code 字符串数组。
即使只有一个理由，也必须写成 {"C": ["HIGH_FATIGUE"]}，不能写成 ["HIGH_FATIGUE"] 或 [{"routeCode":"C","codes":[...]}]。
acceptedRouteCodes 里的路线不要出现在 reasonCodes；如果一条路线值得推荐，就不要再给它写负向 reason code。

reasonCodes 只能从下面 8 个里选，不许自创：
LOW_INTEREST_COVERAGE / WEAK_GOAL_FIT / LOW_DIVERSITY / BAD_SPATIAL_FLOW /
BAD_TIME_STRUCTURE / HIGH_FATIGUE / BUDGET_MISMATCH / HIGH_ROUTE_RISK

BUDGET_MISMATCH 只在路线预算明显高于本次预算档，或明显高于其他候选路线时使用；
不要因为本次目标是 LOW_BUDGET 就默认给落选路线标 BUDGET_MISMATCH。
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
    "FOOD": "本地小吃",
    "COFFEE": "咖啡",
    "MUSEUM": "展馆文化",
    "SCENIC": "景点地标",
    "PHOTO": "拍照",
    "SHOPPING": "购物",
    "NIGHT": "夜游",
}


def build_user_prompt(route_request: dict, route_generation: dict, persona: dict | None) -> str:
    routes = route_generation.get("routes") or []
    route_codes = [route.get("routeCode") for route in routes if isinstance(route, dict) and route.get("routeCode")]
    return "\n\n".join(
        [
            "【本次请求】\n" + render_request(route_request),
            "【你的偏好】\n" + render_persona(persona),
            "【候选路线】\n" + render_routes(routes),
            "【输出硬性约束】\n" + render_output_constraints(route_codes),
            "请只输出 JSON，字段为 ranking、acceptedRouteCodes、rejectedRouteCodes、reasonCodes、confidence。"
            "reasonCodes 必须是对象，例如 {\"C\": [\"HIGH_FATIGUE\"]}，不能是数组。",
        ]
    )


def render_request(route_request: dict) -> str:
    interest_tags = route_request.get("interestTags") or []
    departure = route_request.get("departureTime")
    return (
        f"目标: {route_request.get('routeGoal')}        交通方式: {route_request.get('transportProfile')}\n"
        f"预算档: {route_request.get('budgetLevel', 'NORMAL')}                 兴趣: {', '.join(interest_tags) or '未指定'}\n"
        f"出发: {format_departure(departure)}              时长: {route_request.get('durationMinutes')} 分钟\n"
        f"城市/区域: {route_request.get('routeCityName') or '未知'} / {route_request.get('areaLabel') or '未知'}"
    )


def render_persona(persona: dict | None) -> str:
    if not persona:
        return "你按本次请求中的目标、预算、交通方式和兴趣标签做选择。"
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
    return "".join(lines) if lines else "你按本次请求中的目标、预算、交通方式和兴趣标签做选择。"


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
    budget_cent = route.get("budgetCent")
    budget_text = "未知" if budget_cent is None else f"¥{budget_cent / 100:.0f}"
    return (
        f"路线 {route.get('routeCode')}: {route.get('title')}\n"
        f"  概述: {route.get('summary')}\n"
        f"  说明: {route.get('explanation')}\n"
        f"  行程:\n" + "\n".join(stop_lines) + "\n"
        f"  合计: 总时长 {route.get('totalDurationMinutes')}min / 总距离 {route.get('totalDistanceMeters')}m / "
        f"预算 {budget_text} / 风险 {route.get('riskLevel')}"
    )


def render_stop(stop: dict, index: int, stop_count: int) -> str:
    label = stop.get("slotLabel") or stop.get("category") or "地点"
    stay = stop.get("stayMinutes")
    description = truncate(stop.get("description") or stop.get("reason") or "", 70)
    if index == stop_count - 1:
        next_text = "终点，无下一段"
    else:
        mode = TRANSPORT_LABELS.get(stop.get("transportToNext"), stop.get("transportToNext") or "未知交通")
        distance = stop.get("distanceToNextMeters")
        next_text = f"{mode} {distance}m" if distance is not None else mode
    extra = f"，{description}" if description else ""
    return f"    {index + 1}. {stop.get('name')}（{label}）停留 {stay}min{extra} → {next_text}"


def truncate(text: str, max_length: int) -> str:
    if len(text) <= max_length:
        return text
    return text[: max_length - 1] + "…"


def format_departure(value: str | None) -> str:
    if not value:
        return "未知"
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return value
    local = parsed.astimezone(timezone.utc)
    return local.strftime("%Y-%m-%d %H:%M UTC")
