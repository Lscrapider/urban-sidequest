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

Python 运行配置统一来自仓库根目录环境文件：

```text
.env      # 可提交，只放变量名和空占位
.env.dev  # 不提交，本地真实 dev 值
```

加载优先级：

```text
Docker/CI 已注入环境变量 > .env.dev > .env
```

`config.json` 只保留 judge 策略参数。连接地址、模型、API Key、后端鉴权和数据库配置全部放在 `.env.dev` 或运行环境变量里；缺配置时直接报错，不做本地 JSON fallback。
`requests.json` 仍是默认 job 输入路径，本地文件已加入 `.gitignore`。

主路径使用 New API 单入口，常用变量：

```env
NEW_API_KEY=
ROUTE_LLM_PROVIDER=
ROUTE_LLM_BASE_URL=
ROUTE_LLM_COMPLETIONS_PATH=
ROUTE_LLM_MODEL=urban-mock-user
BACKEND_BASE_URL=
BACKEND_LOGIN_PHONE=
BACKEND_LOGIN_CODE=
BACKEND_TIMEOUT_SECONDS=
```

实际请求地址是 `ROUTE_LLM_BASE_URL + ROUTE_LLM_COMPLETIONS_PATH`。`ROUTE_LLM_MODEL=urban-mock-user` 是 New API 里用于模拟用户 judge 的路由模型名，不要填 Java 路线生成模型。脚本保存 judgment 时会优先使用响应 JSON 顶层的 `model` / `modelId` / `model_id`，例如 `kimi-k2.6`、`qwen3.6-flash`；响应缺少模型字段时才 fallback 到配置标识。

补 k 时可以配置模型池，脚本会在新增 judgment 间轮换 primary LLM，失败时仍按现有 fallback/retry 逻辑尝试其他模型：

```env
ROUTE_LLM_POOL_JSON=[
  {"provider":"new-api","baseUrl":"https://example.com","completionsPath":"/v1/chat/completions","model":"urban-mock-user-a","apiKeyEnv":"NEW_API_KEY"},
  {"provider":"new-api","baseUrl":"https://example.com","completionsPath":"/v1/chat/completions","model":"urban-mock-user-b","apiKeyEnv":"NEW_API_KEY"}
]
```

不配置 `ROUTE_LLM_POOL_JSON` 时继续使用单模型环境变量，保持原行为。

后端鉴权二选一：

- `BACKEND_AUTH_TOKEN`：直接填 `Bearer ...`
- `BACKEND_LOGIN_PHONE` / `BACKEND_LOGIN_CODE`：脚本先调用 `/api/auth/login` 获取 token

补跑缺失 judgment 时还会读取数据库，数据库连接同样来自 `.env.dev`。可以二选一：

```env
ROUTE_PREF_DB_DSN=
```

或拆分字段：

```env
ROUTE_PREF_DB_HOST=
ROUTE_PREF_DB_PORT=
ROUTE_PREF_DB_NAME=
ROUTE_PREF_DB_USER=
ROUTE_PREF_DB_PASSWORD=
ROUTE_PREF_DB_CONNECT_TIMEOUT=
```

`config.json` 示例：

```json
{
  "judge": {
    "promptVersion": "llm-sim-user-v7-reason-audit",
    "judgesPerCandidateSet": 3,
    "candidateSetJudgeConcurrency": 3,
    "maxRetries": 1,
    "timeoutSeconds": 300,
    "temperature": 0.2,
    "multiJudgeTemperatures": [0.2, 0.5, 1.0, 0.2, 0.5, 1.0, 0.2, 0.5, 1.0],
    "seed": 20260625
  }
}
```

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
- `judge.judgesPerCandidateSet` 是每个 candidate set 的 LLM judge 次数，必须配置为正整数；例如 `3` 表示每组路线调 3 次，`9` 表示每组路线调 9 次。旧布尔值仍可兼容读取：`true` 等价于 `3`，`false` 等价于 `1`。
- `judge.temperature` 是单 judge（`judgesPerCandidateSet=1`）时的温度，保留当前 k=1 行为。
- `judge.multiJudgeTemperatures` 是多 judge 调用的温度档位，必须是非空数组且包含 `judge.temperature`；当前建议手动配置 9 位循环温度，例如 `[0.2, 0.5, 1.0, 0.2, 0.5, 1.0, 0.2, 0.5, 1.0]`。温度不会运行时随机，judge 数超过数组长度时才会复用最后一档。
- 多 judge 时，第 1 次 prompt 保留后端返回的路线顺序；后续 judge 会随机打乱候选路线展示顺序，但 routeCode 不重贴，LLM 返回后仍直接按原始 routeCode 保存。
- `judge.candidateSetJudgeConcurrency` 只控制同一个 candidate set 内这些重复 judge 的并发数；实际并发仍受全局 `--judge-concurrency` 或 `run_once.py` 里的 `JUDGE_CONCURRENCY` 限制。
- `run_once.py` 里的 `MULTI_JUDGE_RATIO` 只在 `judgesPerCandidateSet > 1` 时生效：`1.0` 表示全部 route 走配置的完整 judge 数，`0.6` 表示约 60% route 走完整多 judge、其余走单 judge；`judgesPerCandidateSet=1` 时始终是单 judge。
- 保存 judgment 时会在 `judgePromptVersion` 后追加温度审计尾缀，例如 `llm-sim-user-v7-reason-audit@t0.5`。这个尾缀只用于诊断和 SQL 分组，不改变实际 prompt 文本。
- LLM timeout 使用 `judge.timeoutSeconds`，默认 300 秒。请求已经产生 token 成本，不建议随意调低。
- `judge.maxRetries` 表示每个 LLM 配置在一次 judgment 内除首次调用外的额外重试次数；primary 仍失败后，最多尝试 3 个其他 LLM，每个 fallback 也按同样次数重试。

## 给已有 candidate set 补 k

补 k 不重新生成 route，也不写 `route_preference_training_samples` 或 raw snapshot；它只从 `route_preference_raw_snapshots.selected_routes_json` 读取已冻结的候选路线，并向 `route_preference_judgments` 新增 `LLM_SIM_USER` judgment。

先 dry-run 看计划：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge topup-judgments \
  --dry-run \
  --target-k 3 \
  --original-k 1 \
  --limit 20
```

也可以直接运行内部 harness：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge.judge_missing \
  --dry-run \
  --target-k 3 \
  --o-k 1 \
  --limit 20
```

参数口径：

- `--target-k`：目标 completed judgment 数，默认 3。
- `--original-k` / `--o-k`：只补当前 completed judgment 数等于该值的集合；不传则补所有 `c < target-k` 的集合。
- `--limit`：最多处理多少个 candidate set；传 `0` 表示全量。
- `--candidate-set-ids`：逗号分隔指定子集，用于抽查或重跑少量集合。
- `--dry-run`：不保存，只打印每个新增 judgment payload 和计划日志。

示例：只把当前 k=5 的集合补到 k=7：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge topup-judgments \
  --target-k 7 \
  --o-k 5 \
  --limit 50
```

示例：小批量实跑当前 k=1 到 k=3：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.route_preference_judge topup-judgments \
  --target-k 3 \
  --o-k 1 \
  --limit 50 \
  --judge-concurrency 3
```

验收 SQL 示例：

```sql
-- count 分布
SELECT completed_count, COUNT(*) AS candidate_sets
FROM (
  SELECT candidate_set_id, COUNT(*) AS completed_count
  FROM route_preference_judgments
  WHERE status = 'COMPLETED'
  GROUP BY candidate_set_id
) counted
GROUP BY completed_count
ORDER BY completed_count;

-- 新 judgment 的 routeCode 必须仍来自 raw snapshot 冻结路线
SELECT j.candidate_set_id, j.id
FROM route_preference_judgments j
JOIN route_preference_raw_snapshots r
  ON r.candidate_set_id = j.candidate_set_id
WHERE j.status = 'COMPLETED'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(j.ranking_json) ranked(route_code)
    WHERE NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(r.selected_routes_json) route
      WHERE route->>'routeCode' = ranked.route_code
    )
  );
```

补 k 入口每次都会按数据库实时 completed count 计算缺口，重跑时只会继续补仍小于 `target-k` 的集合；如果某次 LLM 失败导致只保存了部分新增 judgment，下一次会按新的 count 继续补剩余缺口。

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

当前 `llm-sim-user-v7-reason-audit` 会要求 LLM 先输出 `personalReview`，用第一人称写出作为漫步者的真实取舍，再输出 ranking 和 reasonCodes；同时不再把 reasonCodes 描述为弱解释，并要求对每条 rejectedRouteCodes 逐项审计 9 类 reason code，避免削弱结构化拒绝原因。该字段只用于人工查看和 dry-run 输出，不写入 Java judgment 接口，也不进入训练标签。

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

reasonCodes 审计要求：

- 对每条 rejectedRouteCodes 中的路线逐项检查 9 类问题。
- 有明确证据才写入对应 code；没有明确证据不要为了凑数添加。
- HIGH_ROUTE_RISK 只用于路线能不能顺利执行的不确定性，例如 fallback、营业时间不确定、夜间可玩性不确定、天气/交通风险、POI 信息严重缺失、路线警告、交通估算缺失。
- 单纯距离远优先标 HIGH_FATIGUE，不标 HIGH_ROUTE_RISK。
- 单纯绕路优先标 BAD_SPATIAL_FLOW，不标 HIGH_ROUTE_RISK。
- 单纯饭点不顺优先标 BAD_TIME_STRUCTURE，不标 HIGH_ROUTE_RISK。

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
  "judgePromptVersion": "llm-sim-user-v7-reason-audit@t0.2",
  "ranking": ["A", "B", "C"],
  "acceptedRouteCodes": ["A"],
  "rejectedRouteCodes": ["C"],
  "reasonCodes": {
    "C": ["HIGH_FATIGUE"]
  },
  "confidence": 0.6
}
```
