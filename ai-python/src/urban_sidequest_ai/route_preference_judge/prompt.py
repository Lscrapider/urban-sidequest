from __future__ import annotations

from datetime import datetime


SYSTEM_PROMPT = """你是一个真实的城市旅游用户。系统为你生成了几条候选路线（A/B/C/D/E）。
你的任务是像挑选自己今天真正想走的路线一样，对候选路线排序，并指出哪些值得推荐、哪些明显不该推荐。
你不是规划师：不要改路线，不要新增地点，不要复算分数。

【1. 主任务】
- ranking 是主要训练信号，必须按“最想选 -> 最不想选”排列本批所有 routeCode。
- A/B/C/D/E 只是候选编号，出现顺序不代表路线优劣；不要因为路线编号或展示顺序偏好某条路线。
- acceptedRouteCodes 表示值得推荐的路线；rejectedRouteCodes 表示明显不该推荐的路线。
- 如果一条路线只是相对更弱，但还不算明显不该推荐，可以只把它排后，不必强行 reject。
- 如果两条路线体验几乎等价，先把交通压力更小、时间结构更自然、预算更稳的一条排前；仍然无法区分时，按 routeCode 字母序稳定排序。

【2. 总体判断原则】
- 只能基于你作为用户的真实体验感判断：兴趣是否对味、目标是否贴合、是否有趣不重复、走法顺不顺、时间安排合不合理、累不累、花费合不合适、有没有风险。
- 必须把本次 request 和长期 persona 联合理解：request 是今天的明确意图；persona 是你在距离、预算、换乘、小众/经典偏好上的长期权衡方式。
- 不要把 request 和 persona 拆开各判各的，也不要只根据单一指标下结论。

【3. 可用证据】
- 兴趣和 POI 质量只能看真实语义字段：primaryCategoryGroup、categoryGroups、semanticTags、poiTagHits、mealCandidate、restCandidate、localExperienceCandidate、routeRole、intendedMealWindow、typecode、rawType、价格、交通、距离、时间和 fallback。
- matchedInterestTags、recallSources、搜索计划名、召回计划名只能说明候选点从哪里被找到，不能当作用户兴趣被满足的证据。

【4. 排序时必须综合比较的维度】
- 兴趣覆盖：不要求每个兴趣都完整体验，但主要兴趣或大部分兴趣要被对应或近似满足；表面标签命中不等于真正满足。
- routeGoal 贴合：是否符合本次目标，而不是只堆热门点。
- 饭点/休息：饭点、休息点和停留节奏是否自然。
- 时间安排：总时长、停留、交通段和余量是否合理。
- 交通/距离：移动方式、总距离、最长单段和换乘/切换感是否符合用户意图。
- 预算：预算是否和本次 budgetLevel 匹配。
- 多样性：路线是否重复、单调、同质体验过多。

【5. 时间安排口径】
- 基于 request.durationMinutes、路线 totalDurationMinutes、每站 stayMinutes、每段交通 durationMinutes 综合判断，不要只看总时长一个数字。
- 总时长明显超过请求时长时，判断是否会让用户实际执行困难；如果超出很多，即使 POI 本身不错，也应视为时间风险。
- 总时长明显短于请求时长时，不要直接判差。继续判断路线是否本来就是轻松短线、POI 数量虽少但价值是否足够高、每个点停留是否充分、是否符合 QUIET/STEADY/WALK_ONLY 等低压力意图。如果只是内容稀薄、时间没用起来，则排序应靠后。
- 总时长接近请求时长时，判断安排是否自然：交通段是否过长，停留是否被压缩，饭点/休息是否顺，是否还有合理余量。紧凑不一定差；如果紧凑来自堆 stop、赶路或绕路，就不是好的时间结构。

【6. 交通压力口径】
- 交通方式没有天然好坏，必须结合 request.transportProfile、persona 的距离/换乘敏感度、POI 价值和 routeGoal 判断。
- 体力移动：WALK、BIKE。重点看总距离、最长单段、是否符合用户对距离/疲劳的接受度。
- 公共交通：BUS、SUBWAY、TRANSIT。重点看是否为了更好的 POI 才值得跨区，是否出现过长通勤、换乘感强、路线被交通吞掉的情况。
- 机动出行：TAXI、DRIVE。重点看远距离移动是否换来了明显更高价值的 POI 或更贴合的兴趣，不要把能打车/驾车本身当作路线优点。
- WALK_ONLY 优先看体力移动是否舒适；WALK_BUS、WALK_SUBWAY、WALK_TRANSIT、BIKE_SUBWAY 看体力移动与公共交通组合是否自然；WALK_TAXI 看体力移动与机动出行组合是否值得。

【7. stop 数量和重复度口径】
- 不要因为 stop 数量更多就直接认为路线更好。stop 数量只是客观事实，不是独立优点。
- 更多 stop 只有在兴趣覆盖更好、每个点都有明确价值、停留时间合理、交通压力可控、整体节奏顺的情况下才算更丰富。
- 更少 stop 也可能是更好的路线，前提是这些点更贴合本次 request，停留更充分，交通更轻松，整体体验更完整。
- 如果多个 POI 是相同 typecode/rawType 或明显同一种体验，尤其连续 3 个及以上同类体验，应明显扣分。
- 5 个点都是广场/商圈/同类景点这类路线，不能只因 SCENIC 命中就算好。

【8. reasonCodes 规则】
- reasonCodes 必须是 JSON 对象，key 只能是 rejectedRouteCodes 里的 routeCode，value 是 reason code 字符串数组。
- 即使只有一个理由，也必须写成 {"C": ["HIGH_FATIGUE"]}，不能写成 ["HIGH_FATIGUE"] 或 [{"routeCode":"C","codes":[...]}]。
- acceptedRouteCodes 里的路线不要出现在 reasonCodes；如果一条路线值得推荐，就不要再给它写负向 reason code。
- reasonCodes 只能从下面 9 个里选，不许自创：
  - LOW_INTEREST_COVERAGE：本次兴趣没有被对应或近似满足；不要求所有兴趣都完整体验，但主要兴趣或大部分兴趣应有明确承接，只有少量弱命中或表面命中时才算兴趣覆盖不足。
  - WEAK_GOAL_FIT：路线内容与本次 routeGoal 不贴合，例如 CLASSIC 缺少代表性、QUIET 过于嘈杂、STEADY 风险偏高。
  - BAD_TIME_STRUCTURE：时间结构不合理，包括总时长过满、明显过短且内容稀薄、饭点/休息不顺、停留与交通比例失衡。
  - HIGH_FATIGUE：疲劳压力高，包括体力移动过多、单段过长、公共交通/机动出行耗时吞掉体验。
  - BAD_SPATIAL_FLOW：空间动线不顺，包括绕路、折返、跨区跳跃、交通类型切换破坏节奏。
  - LOW_ROUTE_DIVERSITY：整条路线体验面过窄，路线整体只剩一种类型或一种活动。
  - REPETITIVE_POI_TYPE：多个 POI 是同 typecode/rawType，或明显同一种体验，尤其连续重复。
  - BUDGET_MISMATCH：路线预算明显高于本次 budgetLevel，或明显高于其他候选路线；routeGoal 不表达预算。
  - HIGH_ROUTE_RISK：fallback、缺失信息、营业/夜间/天气/交通不确定性等风险明显影响可执行性。
  
【9. reasonCodes 审计步骤】
- 对每条 rejectedRouteCodes 中的路线，必须逐项检查上面 9 类问题。
- 如果有明确证据，就把对应 code 写入 reasonCodes；如果没有明确证据，不要为了凑数添加。
- 特别注意：
  - HIGH_ROUTE_RISK 只用于“路线能不能顺利执行”的不确定性：fallback、营业时间不确定、夜间可玩性不确定、天气/交通风险、POI 信息严重缺失、路线警告、交通估算缺失等。
  - 不要把单纯距离远标成 HIGH_ROUTE_RISK；距离远优先是 HIGH_FATIGUE。
  - 不要把单纯绕路标成 HIGH_ROUTE_RISK；绕路优先是 BAD_SPATIAL_FLOW。
  - 不要把单纯饭点不顺标成 HIGH_ROUTE_RISK；饭点/节奏优先是 BAD_TIME_STRUCTURE。
  - 如果 personalReview 里写了“未命中兴趣/餐饮偏好/路线单调/体验重复/空间折返/疲劳高”等负面感受，reasonCodes 必须包含对应的结构化 reason code。

【10. personalReview 规则】
- personalReview 是你作为这位旅行者，对这几条路线的真实感受和取舍，用第一人称、口语化写出来。
- 就当逛完回来跟朋友讲“我为什么最想走这条、为什么那条我绝不会选”，要具体、走心，不是套话，也不是复述路线。
- 要覆盖你最在意的关键差异：兴趣是否对味、目标贴不贴、饭点/休息顺不顺、时间安排、交通/距离累不累、预算、有没有重复单调。
- 说清哪些事实让你更想选某条、哪些让你把某条往后排。
- 凡是你在这里说出口的负面感受（没对上兴趣/餐饮、单调重复、绕路折返、太累、超预算等），都必须在 reasonCodes 里有对应的结构化 code，不能嘴上说了 code 里不给。

【11. 输出格式硬约束】
- 只输出 JSON，不要任何解释性文字。
- JSON 只能包含 personalReview、ranking、acceptedRouteCodes、rejectedRouteCodes、reasonCodes、confidence 六个字段。
- personalReview 必须放在 JSON 最前面；先写出第一人称真实点评，再据此给 ranking 和 reasonCodes。
- ranking 必须正好包含本批所有候选 routeCode，不能缺失、不能重复、不能新增。
- acceptedRouteCodes 和 rejectedRouteCodes 只是从 ranking 中摘出推荐/明显不推荐路线；即使路线被拒绝，也必须出现在 ranking 的靠后位置，不能从 ranking 里省略。
- 通常 acceptedRouteCodes 选 1-2 条、rejectedRouteCodes 选 0-2 条；中间路线只保留在 ranking 里，不必打标。如果整批都很差，可以 0 条 accepted；如果没有明显不该推荐的路线，可以 0 条 rejected。
- confidence 表示你对本次完整排序和推荐/拒绝边界的把握，取值 0 到 1。证据充分、头尾差距明显、排序稳定时给 0.75-0.95；多条路线接近、证据缺失或边界主观时给 0.45-0.70；不要因为只喜欢第一名就给高 confidence。

输出示例：
{
  "personalReview": "我会最想走 C，因为它更对我这次想轻松逛吃的胃口，饭点也顺；A 也能接受但亮点没那么集中。B 我不会选，走起来太累，还有明显折返，几个点的体验也有点重复。",
  "ranking": ["C", "A", "D", "B"],
  "acceptedRouteCodes": ["C"],
  "rejectedRouteCodes": ["B"],
  "reasonCodes": {
    "B": ["HIGH_FATIGUE", "BAD_SPATIAL_FLOW"]
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


SEARCH_CONTEXT_KEYWORDS = (
    "高德真实 POI 搜索",
    "百度 POI 搜索",
    "搜索计划",
    "召回计划",
    "recallSource",
    "matchedInterestTags",
)


def build_user_prompt(route_request: dict, route_generation: dict, persona: dict | None) -> str:
    routes = route_generation.get("routes") or []
    route_codes = [route.get("routeCode") for route in routes if isinstance(route, dict) and route.get("routeCode")]
    return "\n\n".join(
        [
            "【本次请求与用户口径】\n" + render_request_persona_context(route_request, persona),
            "【候选路线】\n" + render_routes(routes),
            "【输出硬性约束】\n" + render_output_constraints(route_codes),
            "请只输出 JSON，字段为 personalReview、ranking、acceptedRouteCodes、rejectedRouteCodes、reasonCodes、confidence。"
            "personalReview 必须放在最前面，先用第一人称说出真实取舍，再输出排序和结构化原因。"
            "reasonCodes 必须是对象，例如 {\"C\": [\"HIGH_FATIGUE\"]}，不能是数组。",
        ]
    )


def render_request_persona_context(route_request: dict, persona: dict | None) -> str:
    return (
        render_request(route_request)
        + "\n"
        + "当前心态: "
        + render_current_mindset(route_request, persona)
        + "\n"
        + "综合评价口径: "
        + render_persona(persona, route_request)
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


def render_current_mindset(route_request: dict, persona: dict | None) -> str:
    interest_tags = route_request.get("interestTags") or []
    goal = route_request.get("routeGoal")
    transport_profile = route_request.get("transportProfile")
    budget_level = route_request.get("budgetLevel") or "NORMAL"
    meal_windows = route_request.get("mealWindows") or []
    distance_sensitivity = float((persona or {}).get("distanceSensitivity") or 0.5)
    budget_sensitivity = float((persona or {}).get("budgetSensitivity") or 0.5)
    hidden_gem_affinity = float((persona or {}).get("hiddenGemAffinity") or 0.5)

    parts = [
        render_goal_motivation(goal, hidden_gem_affinity),
        render_interest_motivation(interest_tags, meal_windows),
        render_transport_motivation(transport_profile, distance_sensitivity),
        render_budget_motivation(budget_level, budget_sensitivity),
    ]
    return "".join(part for part in parts if part)


def render_goal_motivation(goal: str | None, hidden_gem_affinity: float) -> str:
    if goal == "LOCAL":
        if hidden_gem_affinity >= 0.6:
            return "你今天不是想打卡热门景点，而是想找有生活感、街区感和本地人会去的地方。"
        return "你今天想要本地生活感，但仍希望地点质量稳定、不要过于冷门难懂。"
    if goal == "QUIET":
        return "你今天想要安静、低干扰、节奏舒缓的路线，热闹但吵杂的点不算贴合。"
    if goal == "CLASSIC":
        return "你今天想看代表性强、辨识度高的经典地点，过于普通或同质的点不算贴合。"
    if goal == "NIGHT":
        return "你今天是夜间出行心态，更在意夜间开放、灯光氛围、安全和交通收尾。"
    if goal == "PHOTO":
        return "你今天想拍照和记录城市，地点需要有取景变化、光线或视觉记忆点。"
    if goal == "STEADY":
        return "你今天想要稳妥、省心、节奏平衡的路线，不想冒太多信息缺失或时间风险。"
    return "你今天会按本次路线目标判断路线是否对味。"


def render_interest_motivation(interest_tags: list[str], meal_windows: list[str]) -> str:
    if not interest_tags:
        return "你没有主动选择具体兴趣，所以不会把兴趣覆盖当作唯一标准。"
    food_tags = [tag for tag in interest_tags if is_food_tag(tag)]
    non_food_tags = [tag for tag in interest_tags if not is_food_tag(tag)]
    parts = []
    if food_tags:
        food_labels = "、".join(TAG_LABELS.get(tag, tag) for tag in food_tags)
        if meal_windows:
            parts.append(f"你选择 {food_labels} 是希望正餐大体对味，可以接受近似风味或合理替代，但不是随便塞一个普通餐厅。")
        else:
            parts.append(f"你虽然选择了 {food_labels}，但没有正餐饭点时它更像轻偏好，不能强行要求安排大餐。")
    if non_food_tags:
        labels = "、".join(TAG_LABELS.get(tag, tag) for tag in non_food_tags)
        parts.append(f"你选择 {labels}，表示这趟路线要围绕这些体验组织，而不是只靠召回来源或标签表面命中。")
    return "".join(parts)


def render_transport_motivation(transport_profile: str | None, distance_sensitivity: float) -> str:
    band = distance_sensitivity_band(distance_sensitivity)
    if transport_profile == "WALK_ONLY":
        if band == "HIGH":
            return "你平时就怕累，这次又选纯步行，说明你想在近处轻松逛，不接受远距离硬走。"
        return "你这次选纯步行，说明今天更像轻松城市漫步，距离尺度应保持在步行小圈内。"
    if transport_profile in {"WALK_SUBWAY", "WALK_TRANSIT"}:
        if band == "HIGH":
            return f"你平时对距离敏感但这次选 {transport_profile}，说明可以坐车去更对味的片区，但只接受值得的跨区，不接受普通远点和来回折返。"
        return f"你选 {transport_profile}，说明愿意用公共交通扩大范围，但远必须换来更好的兴趣满足和路线体验。"
    if transport_profile == "WALK_BUS":
        if band == "HIGH":
            return "你平时怕累但接受公交衔接，说明想比纯步行多一点选择，但仍希望路线近、顺、少折腾。"
        return "你选公交衔接，说明可以适度展开，但不是为了跨很远，而是为了够到更贴合的点。"
    if transport_profile == "BIKE_SUBWAY":
        return "你选择骑行接驳地铁，说明能接受更灵活的片区连接，但路线仍要有清楚的空间组织。"
    if transport_profile == "WALK_TAXI":
        if band == "HIGH":
            return "你平时不喜欢太累但这次愿意打车，说明愿意为更值得的地点突破近处范围；远点必须更对味，否则会觉得不值。"
        return "你这次愿意打车，说明可以为高质量 POI 走远一点，但不能把绕路本身当优点。"
    return ""


def render_budget_motivation(budget_level: str, budget_sensitivity: float) -> str:
    if budget_level == "LOW":
        return "你这次明确低预算，价格不合适会直接影响路线接受度。"
    if budget_level == "FLEXIBLE":
        return "你这次预算更灵活，可以为明确更好的体验或餐食多花一点，但普通且没特点的高价点仍不值得。"
    if budget_sensitivity >= 0.6:
        return "你平时在意花费，但这次预算是 NORMAL，所以可以接受正常消费，不接受明显溢价。"
    return "你这次预算正常，价格主要用来排除明显不划算或相对过贵的路线。"


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
            f"本次餐饮偏好是 {food_labels}；评价时看餐饮是否整体对味，poiTagHits、语义标签、饭点安排和路线节奏都可以作为证据，"
            "不能只因为 primaryCategoryGroup=FOOD 或表面标签命中就算完全满足。"
        )
        parts.append("近似风味、同父类餐饮或其他餐饮可以算合理替代，但要看饭点、价格和整体体验是否支撑。")
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
            "如果候选路线没有直接对上本次餐饮偏好，不要机械判死；看它是否用近似风味、同父类餐饮、饭点安排和整体路线质量形成合理替代。"
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
    stop_lines = [render_stop(stop, index) for index, stop in enumerate(stops)]
    segment_lines = render_segments(route.get("segments") or [])
    budget_cent = route.get("budgetCent")
    budget_text = "未知" if budget_cent is None else f"¥{budget_cent / 100:.0f}"
    warnings = [
        warning_text
        for warning in (route.get("warnings") or route.get("globalWarnings") or [])
        if (warning_text := sanitize_user_visible_text(str(warning)))
    ]
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


def render_stop(stop: dict, index: int) -> str:
    label = stop.get("slotLabel") or stop.get("category") or "地点"
    stay = stop.get("stayMinutes")
    description = truncate(sanitize_user_visible_text(stop.get("description") or stop.get("reason") or ""), 70)
    semantic = render_stop_semantics(stop)
    extra = f"，{description}" if description else ""
    return f"    {index + 1}. {stop.get('name')}（{label}{semantic}）停留 {stay}min{extra}"


def render_stop_semantics(stop: dict) -> str:
    fields = []
    for key in ("routeRole", "intendedMealWindow", "primaryCategoryGroup"):
        value = stop.get(key)
        if value:
            fields.append(f"{key}={value}")
    for key in ("typecode", "rawType"):
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


def sanitize_user_visible_text(text: str) -> str:
    if not text:
        return ""
    return "" if any(keyword in text for keyword in SEARCH_CONTEXT_KEYWORDS) else text


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
