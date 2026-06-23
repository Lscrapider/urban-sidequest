# LLM 模拟用户路线偏好生成模块

这个模块负责冷启动数据生成编排：

```text
路线请求 JSON
  -> 调 Java /api/routes/requests 生成路线
  -> 从 LLM 池轮询选择 judge primary，失败后按 fallback 补位（可按比例全评）
  -> 构造模拟用户路线选择 prompt
  -> 校验 LLM 输出 JSON
  -> 调 Java /api/route-preferences/judgments 保存 judgment
```

## 配置

首次使用时，把样例复制为模块本地配置。`config.json` 和 `requests.json` 都是默认运行路径：

```bash
cp ai-python/src/urban_sidequest_ai/route_preference_judge/config.example.json \
  ai-python/src/urban_sidequest_ai/route_preference_judge/config.json
cp ai-python/src/urban_sidequest_ai/route_preference_judge/requests.example.json \
  ai-python/src/urban_sidequest_ai/route_preference_judge/requests.json
```

`config.json` 中的 LLM key 可以直接写在本地配置里，也可以用环境变量。本地
`config.json` 已加入 `.gitignore`，不要把真实 key 写进 example 文件。

直接写 key 时可以用完整接口 `url`：

```json
{
  "provider": "GLM",
  "url": "https://open.bigmodel.cn/api/paas/v4/chat/completions",
  "apikey": "填你的真实 key",
  "model": "glm-4-plus"
}
```

也可以用 `baseUrl + completionsPath`：

```json
{
  "provider": "deepseek",
  "baseUrl": "https://api.deepseek.com",
  "apiKeyEnv": "DEEPSEEK_API_KEY",
  "model": "deepseek-v4-pro",
  "completionsPath": "/chat/completions"
}
```

后端鉴权二选一：

- `backend.authToken`：直接填 `Bearer ...`
- `backend.login.phone/code`：脚本先调用 `/api/auth/login` 获取 token

## 生成画像和 request

默认参数会生成：

```text
100 个 persona × 每个 20 个 request = 2000 个 job
```

命令：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge generate-jobs \
  --request-count 10 \
  --probe-ratio 0
```

可调整规模：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge generate-jobs \
  --persona-count 100 \
  --requests-per-persona 20 \
  --cities shanghai,beijing,hangzhou,chengdu,guangzhou
```

生成结果默认写入模块目录下的 `requests.json`，也可以用 `--output` 覆盖。

## 运行评价

先启动 Java 后端，再运行：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge run
```

结构校验，不调用 Java/LLM：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge run --dry-run
```

## 输入请求

`requests.json` 是数组。每项可以是：

```json
{
  "request": { "...": "RouteGenerateParam 字段" },
  "persona": { "...": "UserPreferenceProfileDTO 同构字段，用于评价 prompt" }
}
```

也可以直接放 `RouteGenerateParam` 对象。当前脚本会把 `persona` 写入 `userPreferenceProfileOverride`，使路线生成特征和 LLM 模拟用户评价使用同一份画像。

内置生成器会覆盖这些用户画像方向：

```text
低预算本地生活、首次经典游、拍照 citywalk、慢节奏休息、夜游美食、
文化展馆、美食探索、购物发烧友、抗拒换乘、高体力混合、低预算经典、小众拍照、家庭稳妥
```

内置 request 会覆盖：

```text
STEADY / QUIET / CLASSIC / LOCAL / NIGHT / PHOTO
WALK_ONLY / WALK_SUBWAY / WALK_BUS / WALK_TRANSIT / BIKE_SUBWAY / WALK_TAXI
LOW / NORMAL / FLEXIBLE
```

生成时 `request.interestTags` 会先抽取 2-5 个全局兴趣大类；如果包含 FOOD，会在 FOOD 子树内额外抽取 1-3 个可选标签，因此总标签数可以超过 5。`persona.tagAffinities` 会从画像偏好里随机抽取 3-6 个。`routeGoal` 不再传 `LOW_BUDGET`，预算只用 `budgetLevel` 表达。`departureTime` 使用北京时间本地字符串，例如 `2026-06-22T14:30:00`，不带 `Z` 或时区偏移。

`request.mealWindows` 表示用户本次明确选择的正餐饭点，只能包含 `LUNCH / DINNER`。生成器会按 `departureTime + durationMinutes` 计算可行饭点并默认带上；如果某个请求没有可行饭点，则会从 `request.interestTags` 中移除 FOOD 子标签，避免生成后端必拒的请求。

`request.interestTags` 只传稳定 `tagCode`。非 FOOD 标签传顶层兴趣：`SCENIC / CULTURE / MUSEUM / COFFEE / SHOPPING / LOCAL / NIGHT / PHOTO / ENTERTAINMENT / EVENT`。FOOD 不允许传根标签，只能传子标签，例如 `FOOD_LOCAL_FLAVOR / FOOD_SICHUAN / FOOD_HOT_POT / FOOD_CANTONESE / FOOD_WESTERN / FOOD_AMERICAN` 等。

全局兴趣大类最多 5 个；FOOD 子树无论传几个都只算一个全局大类。FOOD 内部最多显式选择 3 个标签，且不能同时选择同一父子链，例如不能同时传 `FOOD_CHINESE` 和 `FOOD_SICHUAN`。

模拟用户 prompt 会把本次 request 当作当前强意图，把 persona 当作长期偏好背景。例如距离敏感用户这次选择 `WALK_TAXI` 时，prompt 会表达“愿意为了更值得的地点出远门，但无意义绕路和折返仍应降分”。FOOD 子标签也是强意图：如果本次选择 `FOOD_SICHUAN`，路线只命中普通 FOOD 只能算弱替代，不能当作完全满足。

当前 `llm-sim-user-v4-debug` 会要求 LLM 额外输出 `debugRationale`，用于本地排查判断原因。该字段不会写入 Java judgment 接口；正式用 LLM 批量造训练数据前，应删除 prompt/validation/runner 中的 `debugRationale` 调试支持，并切回非 debug prompt version。

## 输出保存

保存到 Java 接口：

```text
POST /api/route-preferences/judgments
```

脚本发送字段：

```json
{
  "candidateSetId": "...",
  "judgeType": "LLM_SIM_USER",
  "judgeModel": "provider:model",
  "judgePromptVersion": "llm-sim-user-v4-debug",
  "ranking": ["A", "B", "C"],
  "acceptedRouteCodes": ["A"],
  "rejectedRouteCodes": ["C"],
  "reasonCodes": {
    "C": ["HIGH_FATIGUE"]
  },
  "confidence": 0.6
}
```
