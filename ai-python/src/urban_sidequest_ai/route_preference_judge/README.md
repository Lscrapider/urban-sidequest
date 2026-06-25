# LLM 模拟用户路线偏好生成模块

这个模块负责冷启动数据生成编排：

```text
路线请求 JSON
  -> 调 Java /api/routes/requests 生成路线
  -> 通过 New API /v1/chat/completions 调用模拟用户 judge
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

`config.json` 中的 LLM key 可以直接写在本地配置里，也可以用环境变量。本地 `config.json` 已加入 `.gitignore`，不要把真实 key 写进 example 文件。

主路径使用 New API 单入口：

```json
{
  "newApi": {
    "provider": "new-api",
    "baseUrl": "http://localhost:3000/v1",
    "completionsPath": "/chat/completions",
    "apiKey": "填你的 New API key",
    "model": "urban-mock-user"
  }
}
```

实际请求地址是 `http://localhost:3000/v1/chat/completions`。`model=urban-mock-user` 是 New API 路由模型名，不建议直接作为数据库里的真实 `judge_model` 解释。脚本保存 judgment 时会优先使用响应 JSON 顶层的 `model` / `modelId` / `model_id`，例如 `kimi-k2.6`、`qwen3.6-flash`；响应缺少模型字段时才 fallback 到配置标识。

`llmPool` 仍可作为 legacy / advanced / optional fallback，用于多供应商轮询、全量评价或 fallback 实验。当前代码解析优先级仍是 `llmPool` > `newApi` > `llm` > 裸 LLM 配置 > 默认 New API，因此同时配置时会优先使用 `llmPool`：

```json
{
  "llmPool": [
    {
      "provider": "qwen",
      "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "apiKeyEnv": "QWEN_API_KEY",
      "model": "qwen3.6-flash",
      "completionsPath": "/chat/completions"
    }
  ]
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

并发参数：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge run \
  --concurrency 2 \
  --judge-concurrency 4
```

执行口径：

- 路线生成和 LLM judge 使用分离线程池；不传 `--judge-concurrency` 时与 `--concurrency` 相同。
- 主流程会等待所有 judgment 保存成功或失败后再退出。
- 候选路线少于 2 条时跳过 LLM judge 并记录原因。
- `judge.fullJudgeRatio` 表示进入多评判的 candidate set 比例；未命中时只评价 1 次，命中后按 `judge.judgesPerCandidateSet` 评价多次。
- 当前主路径是 New API 单入口，所以 `judgesPerCandidateSet=2`、`fullJudgeRatio=0.1` 表示约 10% 的 candidate set 会调用 2 次 New API；`judgesPerCandidateSet=2`、`fullJudgeRatio=1` 表示每个 candidate set 都调用 2 次 New API。
- 如果配置了多个 `llmPool`，命中多评判时会按轮询顺序选择 `judgesPerCandidateSet` 个评价任务；New API 单入口时则重复选择同一个入口。
- LLM timeout 使用 `judge.timeoutSeconds`，默认 300 秒。请求已经产生 token 成本，不建议随意调低。
- `judge.maxRetries` 表示每个 LLM 配置在一次 judgment 内除首次调用外的额外重试次数；primary 仍失败后，最多尝试 3 个其他 LLM，每个 fallback 也按同样次数重试。

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

模拟用户 prompt 会把本次 request 当作当前强意图，把 persona 当作长期偏好背景。例如距离敏感用户这次选择 `WALK_TAXI` 时，prompt 会表达“愿意为了更值得的地点出远门，但无意义绕路和折返仍应降分”。餐饮兴趣按整体是否对味判断：如果本次选择 `FOOD_SICHUAN`，可以接受近似风味、同父类餐饮、饭点安排和整体路线质量形成的合理替代，但不能只因为是普通 FOOD 或表面标签命中就算完全满足。

当前 `llm-sim-user-v5-personal-review` 会要求 LLM 先输出 `personalReview`，用第一人称写出作为漫步者的真实取舍，再输出 ranking 和 reasonCodes。该字段只用于人工查看和 dry-run 输出，不写入 Java judgment 接口，也不进入训练标签。

reason code 固定为 9 个：

```text
LOW_INTEREST_COVERAGE
WEAK_GOAL_FIT
BAD_TIME_STRUCTURE
HIGH_FATIGUE
BAD_SPATIAL_FLOW
LOW_ROUTE_DIVERSITY
REPETITIVE_POI_TYPE
BUDGET_MISMATCH
HIGH_ROUTE_RISK
```

未知 reason code 会导致本次 LLM 输出校验失败，并触发 fallback；所有 fallback 都失败时，该 judgment 不保存。

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
  "judgeModel": "kimi-k2.6",
  "judgePromptVersion": "llm-sim-user-v5-personal-review",
  "ranking": ["A", "B", "C"],
  "acceptedRouteCodes": ["A"],
  "rejectedRouteCodes": ["C"],
  "reasonCodes": {
    "C": ["HIGH_FATIGUE"]
  },
  "confidence": 0.6
}
```
