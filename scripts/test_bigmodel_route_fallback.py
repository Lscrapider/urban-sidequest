#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


# API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
API_URL = "https://api.deepseek.com/chat/completions"
# MODEL = "glm-4.7"
MODEL = "deepseek-v4-pro"
DEFAULT_DATA_FILE = "route_fallback_test_data.json"

SYSTEM_PROMPT = """你是 Urban Sidequest 的路线编排助手。你只根据输入 JSON 工作，必须返回合法 JSON，不要输出 Markdown、解释文字或代码块。你的任务是从后端筛选过的真实 poiPool 中生成 5 条路线草案。你不能编造 POI、坐标、距离、交通耗时、评分、营业信息或图片。所有路线 stop 必须引用 poiPool 中存在的 poiId。你不能删除 request.mustVisitPoiIds 中的必去点，不能改变用户选择的城市、出发时间、路线时长、交通方式和路线目标。距离、交通耗时和真实路径由后端/地图服务计算，你只能决定选点、排序、停留时间、路线主题、路线说明、节点说明和 warning。每条路线的停留时间总和不得超过 request.durationMinutes 的 85%，因为后端还需要预留交通时间；不要为了贴近请求时长而吃满上限，跨区域路线应预留更充足交通余量。每个 stop 的 routeRole 只能取 MUST_VISIT、ANCHOR、MEAL、REST、LOCAL、PHOTO、BACKUP 之一，不能输出其他枚举值。每个 stop 的 stayMinutes 必须符合用户 prompt 中的停留时间参考；必去点也要按其 POI category 选择合理停留时间，除非 route.warnings 明确说明原因。午餐或晚餐 stop 的 routeRole 必须是 MEAL，并且必须优先选择 category=FOOD 或 role=MEAL 的 POI；category=FOOD 或 role=MEAL 就视为可用于午餐/晚餐，不要求 tags 中额外包含午餐或晚餐标签，也不要因为缺少这类标签产生 warning。只有候选池没有合适 FOOD/MEAL 时才允许使用其他 POI，并必须在 route.warnings 中说明。每个 MEAL stop 必须填写 intendedMealWindow：LUNCH、DINNER 或 OTHER。WALK_TAXI 可以跨区域，但应按空间相近性组织 stop 顺序，避免远距离片区之间来回跳转；如果路线存在明显折返风险，必须在 backendReviewHints 中说明。若无法满足某个需求，返回 warnings 说明原因，不要编造地点。"""

USER_PROMPT_TEMPLATE = """请基于下面的真实 POI 候选池生成路线草案。

目标：
1. 从 poiPool 中生成 request.routeCountRange 指定数量范围内的路线，生成 5 条高质量路线。
2. 每条路线都必须包含 request.mustVisitPoiIds 中的所有必去点。
3. 每条路线只能引用 poiPool 中存在的 poiId。
4. 根据 request.durationMinutes、departureTime、mealWindows 安排午饭、晚饭和咖啡/休息点。
5. 8 小时路线应有跨区域感，避免所有 stop 过度聚集在同一小片区。
6. 根据 category、role、tags、features、rating、avgPriceCent、nearestTransit 和 transitAccessibility 选择 POI。
7. 每条路线需要有明确主题，A/B/C/D/E 路线应有差异，不要只是换顺序。
8. 输出路线草案即可，距离、交通耗时和真实路径由后端之后调用高德路线 API 计算。

硬约束：
- 只能引用 poiPool 中存在的 poiId。
- 不能新增虚构地点。
- 每条路线都必须包含全部 request.mustVisitPoiIds。
- 不能返回自然语言说明，只能返回 JSON。
- 每条路线 stop 只能为6个。
- 每条路线停留时间总和不得超过 request.durationMinutes 的 85%。
- estimatedStayMinutes 必须等于该路线所有 stops.stayMinutes 的总和。
- routeRole 只能取 MUST_VISIT、ANCHOR、MEAL、REST、LOCAL、PHOTO、BACKUP，不能输出 SCENIC、CULTURE、FOOD、COFFEE 等 schema 外枚举。
- 每个 stop 的 stayMinutes 必须符合下面“停留时间参考”；如果确实需要超出参考范围，必须在 route.warnings 中说明原因。
- 如果覆盖午饭窗口，优先安排 FOOD/MEAL stop；如果覆盖晚饭窗口，也优先安排 FOOD/MEAL stop。
- 如果 stop 用作午餐或晚餐，routeRole 必须是 MEAL，且应优先选择 category=FOOD 或 role=MEAL 的 POI。
- category=FOOD 或 role=MEAL 的 POI 可以直接作为午餐/晚餐候选，不要求 tags 额外包含 LUNCH 或 DINNER，不要因此产生 warning。
- 每个 routeRole=MEAL 的 stop 必须填写 intendedMealWindow，取值为 LUNCH、DINNER 或 OTHER。
- 如果没有安排某个饭点，必须在 route.warnings 中说明原因。
- WALK_TAXI 模式下可以跨区域，但路线顺序应符合城市移动常识。
- 避免远距离片区之间来回折返。路线可以跨多个片区，但同一片区内的 stop 应尽量连续安排；如果必须折返，backendReviewHints 必须说明原因。
- nearestTransit 只能作为可达性参考，不要把它当作已经计算好的路线耗时。

饭点判断：
- 午饭窗口：11:30-13:30。
- 晚饭窗口：17:30-20:00。
- 路线时间段与饭点窗口有交集，则认为覆盖饭点。

停留时间参考：
- 文化展馆/博物馆：60-90 分钟。
- 公园/景点：45-75 分钟。
- 正餐餐饮：45-75 分钟。
- 咖啡/休息：20-40 分钟。
- 拍照点/轻量打卡：15-30 分钟。
- 普通街区体验：30-60 分钟。

请按以下 JSON Schema 返回：
{
  "overallVerdict": "COMPOSED | PARTIAL | FAILED",
  "globalWarnings": ["string"],
  "routes": [
    {
      "routeCode": "A",
      "title": "string",
      "theme": "string",
      "summary": "string",
      "explanation": "string",
      "estimatedStayMinutes": 0,
      "routeTags": ["string"],
      "stops": [
        {
          "order": 1,
          "poiId": "string",
          "routeRole": "MUST_VISIT | ANCHOR | MEAL | REST | LOCAL | PHOTO | BACKUP",
          "intendedMealWindow": "LUNCH | DINNER | OTHER | null",
          "stayMinutes": 0,
          "description": "string",
          "reason": "string"
        }
      ],
      "warnings": ["string"],
      "backendReviewHints": [
        {
          "type": "TIME_WINDOW | ROUTE_DISTANCE | TRANSIT | BUDGET | OTHER",
          "message": "string"
        }
      ],
      "needsBackendReview": true
    }
  ]
}

输入数据：
{input_json}
"""


def load_data(data_path):
    with data_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def build_user_prompt(data):
    input_json = json.dumps(data, ensure_ascii=False, indent=2)
    return USER_PROMPT_TEMPLATE.replace("{input_json}", input_json)


def build_payload(data, model):
    return {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": SYSTEM_PROMPT
            },
            {
                "role": "user",
                "content": build_user_prompt(data)
            }
        ],
        "stream": False,
        "response_format": {
            "type": "json_object"
        },
        "thinking": {
            "type": "disabled"
        }
    }


def call_bigmodel(payload, api_key, endpoint, timeout_seconds):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json"
        },
        method="POST"
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def print_assistant_message_json(response):
    content = response["choices"][0]["message"]["content"]
    try:
        parsed_content = json.loads(content)
    except json.JSONDecodeError:
        print(content)
        return
    print(json.dumps(parsed_content, ensure_ascii=False, indent=2))


def parse_args():
    parser = argparse.ArgumentParser(description="测试大模型 POI 池路线编排 prompt。")
    parser.add_argument(
        "--data",
        default=str(Path(__file__).with_name(DEFAULT_DATA_FILE)),
        help="测试数据 JSON 路径，默认读取脚本同级 route_fallback_test_data.json。"
    )
    parser.add_argument("--endpoint", default=API_URL, help="BigModel chat completions URL。")
    parser.add_argument("--model", default=MODEL, help="模型名称。")
    parser.add_argument("--timeout", type=int, default=180, help="接口超时时间，默认 180 秒。")
    parser.add_argument(
        "--api-key-env",
        default="BIGMODEL_API_KEY",
        help="读取 API Key 的环境变量名，默认 BIGMODEL_API_KEY。"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="只打印请求 payload，不调用接口。"
    )
    return parser.parse_args()


def main():
    args = parse_args()
    data_path = Path(args.data)
    data = load_data(data_path)
    payload = build_payload(data, args.model)

    if args.dry_run:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    # api_key = "0094424cbb0d4e17b3efa2781d24cec7.CXbNc3v4gKoxD961"
    api_key = "sk-ec5d8d0ebe2c4a439a9a78a24430f451"
    if not api_key:
        print("缺少 API Key，请先设置环境变量：" + args.api_key_env, file=sys.stderr)
        return 2

    try:
        response = call_bigmodel(payload, api_key, args.endpoint, args.timeout)
    except urllib.error.HTTPError as error:
        print(error.read().decode("utf-8", errors="replace"), file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(str(error), file=sys.stderr)
        return 1

    print_assistant_message_json(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
