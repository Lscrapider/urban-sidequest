# LLM 模拟用户路线偏好生成模块

这个模块负责冷启动数据生成编排：

```text
路线请求 JSON
  -> 调 Java /api/routes/requests 生成路线
  -> 从 LLM 池随机抽 2 个 judge（可按比例全评）
  -> 构造模拟用户路线选择 prompt
  -> 校验 LLM 输出 JSON
  -> 调 Java /api/route-preferences/judgments 保存 judgment
```

## 配置

复制配置样例：

```bash
cp scripts/route_preference_simulator/config.example.json /tmp/route-sim-config.json
cp scripts/route_preference_simulator/requests.example.json /tmp/route-sim-requests.json
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
python3 -m scripts.route_preference_simulator generate-jobs \
  --output /tmp/route-sim-requests.json
```

可调整规模：

```bash
python3 -m scripts.route_preference_simulator generate-jobs \
  --persona-count 100 \
  --requests-per-persona 20 \
  --cities shanghai,beijing,hangzhou,chengdu,guangzhou \
  --output /tmp/route-sim-requests.json
```

生成结果就是 `run --requests` 的输入。

## 运行评价

先启动 Java 后端，再运行：

```bash
python3 -m scripts.route_preference_simulator run \
  --config /tmp/route-sim-config.json \
  --requests /tmp/route-sim-requests.json
```

结构校验，不调用 Java/LLM：

```bash
python3 -m scripts.route_preference_simulator run \
  --config scripts/route_preference_simulator/config.example.json \
  --requests scripts/route_preference_simulator/requests.example.json \
  --dry-run
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
LOCAL / CLASSIC / LOW_BUDGET / NIGHT / PHOTO / STEADY
WALK_ONLY / WALK_SUBWAY / WALK_BUS / WALK_TRANSIT / BIKE_SUBWAY / WALK_TAXI
LOW / NORMAL / FLEXIBLE
```

生成时 `request.interestTags` 会从模板主题里随机抽取 2-4 个，`persona.tagAffinities` 会从画像偏好里随机抽取 3-6 个；合法兴趣标签为 `FOOD / COFFEE / MUSEUM / SCENIC / PHOTO / SHOPPING / NIGHT / LOCAL`。

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
  "judgePromptVersion": "llm-sim-user-v2",
  "ranking": ["A", "B", "C"],
  "acceptedRouteCodes": ["A"],
  "rejectedRouteCodes": ["C"],
  "reasonCodes": {
    "C": ["HIGH_FATIGUE"]
  },
  "confidence": 0.6
}
```
