# LLM 模拟用户路线选择设计

本文设计 LLM 模拟用户选择模块。它用于真实用户数据不足时，对同一批候选路线做偏好判断，生成冷启动训练信号。

一句话：

```text
LLM 模拟用户不伪装成真实点击行为。
它只在生成流程内，对刚生成的这批路线写偏好判断，用来生成低权重训练样本。
```

## 0. 边界约定（先读这一段）

本模块有三条不可逾越的边界，后续所有设计都建立在它们之上：

```text
1. 评价在生成流程内完成。
   路线刚生成时仍在内存（CandidateRouteDTO），直接喂给 LLM。
   LLM 不读库、不重新规划路线、不复算特征。

2. 一次生成 = 一个 candidate set。
   首轮评价在生成流程内完成；补 k 只能复用 MinIO raw snapshot 中冻结的候选路线。
   不允许为了补评价重新生成路线或改写 X。

3. MinIO 训练对象只服务 Python 训练，不是 LLM 的默认输入源。
   首轮 LLM 输入来自流程内存；补 k 输入来自 frozen raw snapshot。
```

第 2 条带来一个被接受的取舍：后续补 k 可以追评历史 candidate set，但只能评价当时冻结的候选路线，不能引入新的路线生成结果。这样保证同一组 X/Y 的边界稳定。

## 1. 这个模块解决什么问题

`RoutePreferenceModel` 需要路线级 pairwise 偏好监督，但早期真实用户反馈不足。

真实用户偏好数据包括：

- 用户看到了哪些路线。
- 用户选了哪条 / 是否排序。
- 用户是否收藏、开始、完成或跳过。

这些数据质量高，但积累慢。LLM 模拟用户用于冷启动阶段补充：

```text
一次请求 + 内存里的 K 条候选路线
  -> 模拟一个符合请求画像的用户
  -> 判断哪些路线更可能被喜欢
  -> 输出排序、可接受路线、明显拒绝路线和固定 reason codes
```

这个模块只补"偏好判断"，不补"真实行为事件"。

## 2. MinIO 训练对象与各自边界

路线偏好预热训练数据不再落产品 PostgreSQL。后端生成路线和保存 judgment 时只把训练对象写入 MinIO `ingest/` 区，Python 再合并为版本化 dataset：

```text
route-preference/
  ingest/
    candidate_sets/shard=xx/{candidateSetId}.json.gz       # raw snapshot + 训练输入 X
    candidate_sets_ready/shard=xx/{candidateSetId}.json    # 完整对象 ready marker
    judgments/shard=xx/{candidateSetId}/{judgmentId}.json.gz # 一次评价 Y

  datasets/{datasetVersion}/
    manifest.json
    training_samples.parquet
    judgments.parquet
    raw_snapshot_index.parquet
    raw_snapshots/
```

举例：一次生成 5 条路线，3 个 LLM 评价。

```text
candidate_sets ingest : 1 个 JSON.GZ
  set_001, rawSnapshot, A/B/C/D/E 五条 routeInput

judgments ingest : 3 个 JSON.GZ
  set_001 + GPT    + ranking/reasonCodes/confidence
  set_001 + Claude + ranking/reasonCodes/confidence
  set_001 + Gemini + ranking/reasonCodes/confidence

dataset : 1 个版本目录
  training_samples.parquet + judgments.parquet + manifest.json
```

边界一句话：

```text
candidate-set ingest 存"这一批候选路线长什么样"（raw snapshot + X）
judgment ingest      存"谁觉得哪条更好"（一次评价一个对象，append）
dataset manifest     管"这一版训练集包含哪些完整文件和统计量"
```

LLM 模拟用户**只通过 Java judgment 接口新增 judgment ingest**（`judge_type = LLM_SIM_USER`），不写任何真实行为事件表（`route_feedback` 等）。原因：

- LLM 没有真实点击、收藏、开始、完成这些行为；把判断伪装成真实事件会污染真实行为分布。
- 训练时需要按来源区分样本，并给 synthetic 样本更低权重。

还要区分 `userPreferenceProfile` 与 judgment：

```text
userPreferenceProfile : 用户偏好画像，是路线生成/评价的输入之一，进入 context。
judgments             : 模拟或真实的偏好判断，是后续训练的监督来源，不是推理输入。
```

当前代码落地状态：

- `SaveRoutePreferenceTrainingSamplesStep`：路线生成后写 MinIO candidate-set ingest，保留产品路线历史写入。
- `POST /api/route-preferences/judgments`：保存一次评价时写 MinIO judgment ingest。
- Python dataset builder：读取 ready marker 和 judgment ingest，写出下一版 `datasets/{datasetVersion}`，校验 manifest 后删除已处理 ingest 对象。
- Python training：只读取指定 `ROUTE_PREF_DATASET_VERSION`，不直接读产品 PostgreSQL。
- dataset version 不属于线上写入参数；它只属于离线冻结训练集。已有 dataset 追加 judgment 时，用 `ROUTE_PREF_BASE_DATASET_VERSION` 指向上一版，再生成新的 `ROUTE_PREF_DATASET_VERSION`。

## 2.1 LLM 调用主路径（New API）

当前 Python LLM judge 客户端使用 OpenAI-compatible chat completions 请求。主路径配置为 New API 单入口：

```text
endpoint = http://localhost:3000/v1/chat/completions
request.model = urban-mock-user   # New API 路由模型名，可按环境调整
```

`request.model` 是 New API 的路由标识，不等同于数据库里的 `judge_model`。保存 judgment 时应优先使用 New API 响应 JSON 顶层的 `model` / `modelId` / `model_id`，例如 `kimi-k2.6`、`qwen3.6-flash`；只有响应缺少模型字段时，才 fallback 到配置标识（例如 `new-api:urban-mock-user`）。

API key 可以直接写在本地 `config.json` 的 `apiKey` / `apikey` / `api_key` 中，也可以使用 `apiKeyEnv` 从环境变量读取。本地真实配置不提交，示例配置只放占位 key。

`llmPool` 仍被当前代码支持，并且当前解析优先级仍是 `llmPool` > `newApi` > `llm` > 裸 LLM 配置 > 默认 New API。文档主路径按 New API 写；`llmPool` 只作为 legacy / advanced / optional fallback，用于多供应商轮询、全量评价和 fallback 实验，不再作为推荐的默认配置形态。

## 3. 输入来源（流程内，全部来自内存）

LLM 模拟用户不读库、不重新生成路线，只读流程内已有的对象。

### 3.1 请求上下文

来源：`RouteGenerationContext.getGenerateParam()` + 环境快照（与 `training_samples.context_json` 同源）。

```text
routeGoal
transportProfile
budgetLevel
interestTags
departureTime
durationMinutes
routeTimeStructure
weather
userPreferenceProfile   （真实用户）
```

回答："这个用户当时想要什么？"

### 3.2 候选路线集合（人类可读）

来源：内存里的 `List<CandidateRouteDTO>`。

```text
routeCode（A / B / C / D / E）
title
summary
explanation
totalDurationMinutes
totalDistanceMeters
budgetCent
riskLevel
stops：每个 stop 的 name / slotLabel / stayMinutes / 段间交通 / description
```

回答："系统当时给了哪些路线？"

候选集合默认包含展示给用户的、被软拒绝但仍有分析价值的、排名靠后的路线；被硬拒绝的路线默认不参与模拟用户偏好排序。

### 3.3 派生指标（v1 默认不喂）

`stopMatrix / segmentMatrix / routeDerivedVector` 是给模型训练的数值特征，**默认不喂给 LLM**：喂了会诱导它复算底层特征、把训练 schema 泄露给标注端、并显著增加 token 与不稳定性。

v1 让 LLM 像"读行程的用户"一样判断。若后续发现 LLM 对折返 / 超时 / 预算不敏感，再作为 prompt v2 旋钮，**少量**补几个锚点指标（它们与固定 9 个 reason code 大致对应）：

```text
interestCoverageRatio     <-> LOW_INTEREST_COVERAGE
avgGoalScore              <-> WEAK_GOAL_FIT
categoryDiversityRatio    <-> LOW_ROUTE_DIVERSITY / REPETITIVE_POI_TYPE
backtrackingSegmentRatio  <-> BAD_SPATIAL_FLOW
timeBudgetUsageRatio / missingRequiredMealFlag <-> BAD_TIME_STRUCTURE / HIGH_FATIGUE
budgetPressure            <-> BUDGET_MISMATCH
highRiskStopRatio         <-> HIGH_ROUTE_RISK
```

不建议输入：完整矩阵、大量 POI 原始详情、真实用户行为标签、历史 judgment、训练 label。后两者会造成标签泄漏。

## 4. MinIO 对象结构

预热训练数据不进入产品 PostgreSQL。一次路线生成和多次 LLM 评价在 MinIO 中形成追加式对象：

```text
route-preference/
  ingest/
    candidate_sets/shard=xx/{candidateSetId}.json.gz
    candidate_sets_ready/shard=xx/{candidateSetId}.json
    judgments/shard=xx/{candidateSetId}/{judgmentId}.json.gz
```

`candidate_sets` 对象保存 raw snapshot 和同批全部路线的 X：

```json
{
  "candidateSetId": "uuid",
  "requestId": "uuid",
  "userId": "uuid-or-null",
  "createdAt": "2026-07-03T00:00:00+08:00",
  "rawSnapshot": {
    "rawSchemaVersion": "route_pref_raw_v1",
    "generateParam": {},
    "selectedRoutes": []
  },
  "trainingSamples": [
    {
      "candidateSetId": "uuid",
      "requestId": "uuid",
      "userId": "uuid-or-null",
      "routeCode": "A",
      "featureSchemaVersion": "route_pref_v5",
      "stopMatrixJson": [],
      "segmentMatrixJson": [],
      "routeDerivedVectorJson": [],
      "contextCrossVectorJson": [],
      "intraSetVectorJson": []
    }
  ]
}
```

`candidate_sets_ready` 是小 JSON marker，只在 candidate-set 对象写完后写入。Python dataset builder 只消费有 ready marker 的对象，避免读到半写入数据。

`judgments` 对象保存一次 LLM / 用户 / 标注员评价：

```json
{
  "judgmentId": "uuid",
  "candidateSetId": "uuid",
  "judgeType": "LLM_SIM_USER",
  "judgeModel": "kimi-k2.6",
  "judgePromptVersion": "llm-sim-user-v7-reason-audit@t0.5",
  "rankingJson": ["C", "A", "B", "E", "D"],
  "acceptedRouteCodesJson": ["C", "A"],
  "rejectedRouteCodesJson": ["E", "D"],
  "reasonCodesJson": {"D": ["BAD_SPATIAL_FLOW"], "E": ["LOW_INTEREST_COVERAGE"]},
  "confidence": 0.65,
  "status": "COMPLETED",
  "completedAt": "2026-07-03T00:00:00+08:00"
}
```

一个 candidate set 可以有多个 judgment 对象，互不覆盖。后端保存 judgment 时不回填 X，也不写真实行为事件表；Python 离线任务按 `candidateSetId + routeCode` 关联 X 和 Y。

`judge_type` 取值：`REAL_USER / LLM_SIM_USER / HUMAN_ANNOTATOR / HEURISTIC_JUDGE`。LLM 模拟用户写 `LLM_SIM_USER`。

## 5. LLM 输出 JSON schema

LLM 只输出偏好判断，**不输出** `candidateSetId / judgeType / judgeModel / judgePromptVersion`——这些服务端已知，由编排层注入，避免模型编错 set 或伪造来源。

LLM 实际返回：

```json
{
  "ranking": ["C", "A", "B", "E", "D"],
  "acceptedRouteCodes": ["C", "A"],
  "rejectedRouteCodes": ["E", "D"],
  "reasonCodes": { "D": ["BAD_SPATIAL_FLOW", "HIGH_FATIGUE"], "E": ["LOW_INTEREST_COVERAGE"] },
  "confidence": 0.65
}
```

约束：

- 落库和训练只使用这 5 个字段，不要附带 freeText / explanation 之类非结构化训练标签。
- 当前 `llm-sim-user-v5-personal-review` prompt 会额外要求 `personalReview` 供人工抽查和 dry-run 查看；编排层会在保存 Java judgment 前丢弃该字段，它不进入 MinIO judgment 对象，也不进入模型 X/Y。
- `ranking` 必须是本批 `routeCode`（A/B/C/D/E）的**全排列**。
- `acceptedRouteCodes` / `rejectedRouteCodes` 必须是本批 `routeCode` 子集。
- 同一条路线不能同时出现在 accepted 和 rejected。
- `reasonCodes` 的 key 必须是本批 `routeCode`，且只能是 `rejectedRouteCodes` 中的路线；value 只能用固定 9 个 reason code。
- `confidence` 取 `[0,1]`，训练时不直接等同真实置信度。

编排层拿到上面 5 个训练字段后，注入 `candidateSetId`、`judgeType=LLM_SIM_USER`、`judgeModel`、`judgePromptVersion`，组成完整 `RoutePreferenceJudgmentParam` 落库（字段名严格对齐接口：`acceptedRouteCodes / rejectedRouteCodes / reasonCodes`）。其中 `judgeModel` 使用 §2.1 的实际响应模型名口径。

固定 reason codes（仅此 9 个，顺序与训练 `reason_codes.json` 保持一致）：

```text
LOW_INTEREST_COVERAGE   兴趣覆盖不足
WEAK_GOAL_FIT           与本次目标不贴合
BAD_TIME_STRUCTURE      时段安排不合理（缺正餐 / 节奏乱）
HIGH_FATIGUE            太累 / 距离体力压力大
BAD_SPATIAL_FLOW        走法绕路 / 折返
LOW_ROUTE_DIVERSITY     整体体验面过窄
REPETITIVE_POI_TYPE     点位类型重复
BUDGET_MISMATCH         花费与预算不匹配
HIGH_ROUTE_RISK         风险偏高（闭店 / 夜间 / 远）
```

## 6. Prompt 设计

### 6.1 System（角色 + 任务 + 约束）

```text
你是一个真实的城市漫步用户。系统为你生成了几条候选路线（A/B/C/D/E），
你要像挑选自己今天真正想走的那条一样，对它们排序，并指出哪些值得推荐、
哪些明显不该推荐。你不是规划师，不要改路线、不要新增地点、不要复算分数。

判断只能基于你作为用户的真实体验感：兴趣是否对味、目标是否贴合、是否有趣不重复、
走法顺不顺、时间安排合不合理、累不累、花费合不合适、有没有风险。

reasonCodes 只能从下面 9 个里选，不许自创：
LOW_INTEREST_COVERAGE / WEAK_GOAL_FIT / BAD_TIME_STRUCTURE / HIGH_FATIGUE /
BAD_SPATIAL_FLOW / LOW_ROUTE_DIVERSITY / REPETITIVE_POI_TYPE /
BUDGET_MISMATCH / HIGH_ROUTE_RISK

只输出 JSON，不要任何解释性文字。
```

System 只放角色与规则，不放具体画像。画像/persona 渲染进 User 的【你的偏好】块（见 6.3），同一批路线可由不同 persona / 模型各评一次，每次一行 judgment。

### 6.2 User（数据，全自然语言）

```text
【本次请求】
目标: CITYWALK 本地生活        交通方式: 步行+地铁
预算档: NORMAL                 兴趣: 咖啡, 本地小吃, 老街
出发: 周六 14:00              时长: 240 分钟
时段结构: 下午→傍晚            天气: 多云 24℃

【你的偏好】
（由 persona 渲染，见 6.3）
你很在意花费，偏好低预算；走太多路会累；尤其喜欢：本地生活、咖啡、老街；
偏好小众、本地人才知道的地方，不追热门打卡。

【候选路线】
（由 CandidateRouteDTO 渲染，见 6.4）
路线 A: ...
路线 B: ...
...（A~E）
```

### 6.3 画像 → 自然语言渲染规则（关键：不把裸 0~1 数值给 LLM）

persona / userPreferenceProfile 的标量是给后端用的，喂给 LLM 会诱导它"算分"。渲染时按阈值翻成用户口吻短句，拼成【你的偏好】：

```text
阈值：>=0.6 强 / 0.4~0.6 中 / <0.4 不提

budgetSensitivity   >=0.6 "你很在意花费，偏好低预算"
                    0.4~0.6 "你对花费有点敏感"
distanceSensitivity >=0.6 "走太多路 / 长距离步行会让你很累"
transferSensitivity >=0.6 "你不喜欢频繁换乘、绕路"
hiddenGemAffinity   >=0.6 "你偏好小众、本地人才知道的地方，不追热门打卡"
                    <0.4   "你更喜欢经典、热门、有名的地方"
tagAffinities       affinity>=0.6 的 tag 列为 "尤其喜欢：<tag 中文名…>"
                    （tag 中文名取 interest_tag_catalog 的展示名）
```

约定：
- `profileConfidence` / `newUser` **不渲染进 prompt**——它们是后端 POI 个性化的开关，不是用户会说的话。
- 不编造"不喜欢 X"：只把高 affinity 的 tag 渲成喜欢，低 / 缺省的 tag 不渲成厌恶（已删 negativePreferences）。
- 真实用户和 synthetic persona 走**同一套渲染**（因为同构），prompt 无需区分来源。

### 6.4 候选路线渲染模板

由内存 `CandidateRouteDTO` + `RouteStopDTO` 渲染，固定格式：

```text
路线 A: <title>
  概述: <summary>
  行程:
    1. <stop.name>（<slotLabel 或 category>）停留 <stayMinutes>min → <段交通> <distanceToNextMeters>m
    2. <stop.name>（…）停留 <stayMinutes>min → <段交通> <…>m
    N. <最后一个 stop>（终点，无下一段）
  合计: 总时长 <totalDurationMinutes>min / 总距离 <totalDistanceMeters>m /
        预算 ¥<budgetCent/100> / 风险 <riskLevel>
```

- 段交通 `transportToNext`（SegmentTransportMode）翻中文：WALK 步行 / BIKE 骑行 / BUS 公交 / SUBWAY 地铁 / TAXI 打车。
- `budgetCent` 为空写"未知"；`description` 可选作每个 stop 的一句话补充，过长则截断。
- 默认只渲染参与评价的路线（硬拒绝路线不进 prompt）。

### 6.5 组装顺序与版本

```text
messages = [ system(6.1) , user(6.2: 请求 + 你的偏好(6.3) + 候选路线(6.4)) ]
要求模型至少返回 §5 的 5 个训练字段；当前 `llm-sim-user-v5-personal-review` 会额外返回 `personalReview` 供人工抽查，保存前丢弃。
```

- `judgePromptVersion`：标识**模板**版本，如 `llm-sim-user-v1`；prompt 文案（6.1/6.3/6.4 任一）改动即升版，落进 judgments，便于 Python 按版本切片。
- persona 内容版本走 persona 的 `questionnaireVersion`（如 `sim-persona-v1`），与 prompt 模板版本相互独立。

## 7. 为什么不能只输出排序

排序只能表达相对偏好（C > A > B > E > D），不能表达"C 和 A 真的可以给用户、B 只是比 E/D 好、D 绝对不行"。所以必须同时输出 `ranking + acceptedRouteCodes + rejectedRouteCodes`，才能同时支撑：

- v1 的 pairwise 训练样本；
- 后续 pointwise accept / reject 与概率校准；
- 后续 listwise 排序样本。

## 8. 训练样本派生与读取

MinIO judgment ingest 是原始偏好判断，不是最终训练样本。离线 Python 任务从它派生。

### 8.1 读取路径

```text
1. Spring Boot 写 candidate-set ingest 和 judgment ingest。
2. Python dataset builder 读取 ready marker、candidate-set 对象和 judgment 对象。
3. 写出 datasets/{datasetVersion}/training_samples.parquet。
4. 写出 datasets/{datasetVersion}/judgments.parquet。
5. 写出 manifest.json 并校验计数。
6. 训练入口读取指定 ROUTE_PREF_DATASET_VERSION。
7. 用 route_code 对齐 X 与评价，展开 pairwise 训练样本。
```

一组路线 × N 个评价 = N 套监督信号。`training_samples.parquet` 只提供 X，监督 Y 全部来自 `judgments.parquet`，互不覆盖。

### 8.2 Pairwise 样本

由每条 judgment 的 `ranking` 展开：

```text
ranking = [C, A, B, E, D]
=> C>A, C>B, C>E, C>D, A>B, A>E, A>D, B>E, B>D, E>D
```

为了和《路线偏好排序模型训练设计》对齐，当前训练使用同一 `candidate_set_id` 内的粗对、头部精对和 accepted/rejected 强对构造 pair，并对重复 pair 保留更高权重版本。pair 不能跨 candidate set 构造。

### 8.3 后续 Pointwise / Listwise

`acceptedRouteCodes` -> `routeGoodnessLogit` 的 label=1，`rejectedRouteCodes` -> label=0，中间路线不参与 goodness loss。完整 `ranking` 用于 pairwise 排序监督；listwise 仍属于后续扩展。

## 9. Synthetic persona（冷启动用户画像规范）

冷启动无真实画像时生成 synthetic persona。**persona 必须与真实用户画像 `UserPreferenceProfileDTO` 同构**——不另造字段。

原因（不是为了好看，是不这样根本不生效）：

```text
1. POI Linear Ranker 的全部个性化只读这套字段（PoiLinearFeatureExtractor）：
   profileConfidence 乘进所有个性化列；
   distanceSensitivity -> personalizedDistancePressure
   budgetSensitivity   -> personalizedBudgetPressure
   transferSensitivity -> personalizedTransitPressure
   hiddenGemAffinity   -> personalizedExplorationMatch
   tagAffinities       -> POI 侧 userInterestAffinity；
                          route 侧 profileTagAffinityCoverage / profileTagAffinityPrecision /
                          profileTagAffinityJaccard / profileTopTagHitRatio
   旧 persona 的 classicAffinity/photoAffinity/pacePreference/riskTolerance 等一个都不读，是死字段。
2. 生成 route X 时的原始用户画像会冻结在 `context_json.userPreferenceProfile`，并派生进 `context_cross_vector_json`。
   模型实际读取的是五块 routeInput，`context_json` 只作审计/重建；打标签的 persona 必须与生成路线时的画像一致，X 与 Y 才同一个人。
```

persona 字段（= `UserPreferenceProfileDTO`）：

```text
distanceSensitivity   0~1
budgetSensitivity     0~1
transferSensitivity   0~1
hiddenGemAffinity     0~1
profileConfidence     0~1   -- 见下方旋钮
tagAffinities         Map<tagCode, 0~1>，key 必须是 interest_tag_catalog 的合法 tag_code
                            （如 FOOD / COFFEE / MUSEUM / SCENIC / PHOTO / SHOPPING / NIGHT / LOCAL）
newUser               造数据时设 false（见下）
questionnaireVersion  标记 persona 来源版本，如 "sim-persona-v1"
```

示例（低预算本地党）：

```json
{
  "distanceSensitivity": 0.6,
  "budgetSensitivity": 0.9,
  "transferSensitivity": 0.5,
  "hiddenGemAffinity": 0.7,
  "profileConfidence": 0.8,
  "tagAffinities": { "LOCAL": 0.9, "FOOD": 0.8, "COFFEE": 0.7, "SCENIC": 0.3 },
  "newUser": false,
  "questionnaireVersion": "sim-persona-v1"
}
```

关键旋钮 `profileConfidence`：它是 POI 个性化的总闸。设 0（或 `newUser=true` 的空画像）会把个性化全关——选出的路线对 persona 偏好不敏感，persona 再去评"对它没差别的路线"，信号很弱。**造冷启动训练数据时 `profileConfidence` 取正值（建议 0.6~0.9）。**

完整闭环（X / 选点 / Y 同一个人）：

```text
persona P 的画像 -> 喂 POI Linear 选点、生成路线
                 -> 写入 candidate set 的 context_json.userPreferenceProfile = P
                 -> 派生为 context_cross_vector_json 中的固定交叉特征
                 -> 同一个 P 作为 LLM 模拟用户评价这批路线
```

"本地 / 经典 / 拍照 / 夜"等偏好不丢，全部表达在 `tagAffinities` 的标签上，不需要独立标量。pace 已在 POI 阶段定为交给 Sampler / 编排、不入画像；risk 无数据源，均不作为 persona 字段。

## 10. 样本权重

不同来源的判断不能同权。每条 judgment 的权重由其 `judge_type` 决定。v1（方案 B）训练时由 Python 即时按 `judge_type` 算，不回填 `training_samples.sample_weight`（该列与 label 列一并留空）。当前后端常量（`RoutePreferenceTrainingServiceImpl`）：

```text
REAL_USER         1.00
HUMAN_ANNOTATOR   0.70
LLM_SIM_USER      0.60
HEURISTIC_JUDGE   0.10
```

LLM 模拟用户冷启动期可作为主要监督来源之一，但不应长期压过真实用户行为；`confidence` 可作为二级权重乘子。

## 11. 校验规则（流程内、落库前）

LLM 输出必须经规则校验后才能 append judgment：

```text
ranking 是本批 routeCode 全排列（无缺 / 无重 / 无外来码）。
acceptedRouteCodes / rejectedRouteCodes 是本批子集，且互斥。
reasonCodes 的 key ∈ 本批 rejected routeCode；value ∈ 固定 9 码（越界即判失败）。
被硬拒绝路线不得进入 acceptedRouteCodes。
confidence ∈ [0,1]。
```

失败处理：

```text
不写 judgment，记录失败原因。
当前 runner 会尝试 primary LLM；失败后按配置池选择最多 3 个 fallback LLM 继续尝试。
所有 fallback 仍失败 -> 跳过该 judge，不阻塞其他 judge、不阻塞整批。
训练侧遇到未知 reason code 默认报错；如果显式开启 `--skip-invalid-judgments`，则跳过该 judgment 并记录原因。
```

## 12. 流程内调用 / 保存链路

```text
[离线 / 批量生成流程，路线在内存]
目标流程：
1. 生成 N 条路线 -> candidate_sets 落 1 行
     generation_source=OFFLINE_BATCH, route_count=N,
     target_judgment_count=配置(模型×persona)数, status=JUDGING
2. training_samples 落 N 行（X） —— 已实现
3. for each judge in 配置列表 (responseModel × persona):
     构 prompt -> 调 LLM -> 解析 JSON -> 校验
     成功 -> append 1 行 judgments
            (judge_type=LLM_SIM_USER, judge_model, judge_prompt_version,
             judge_run_key, ranking/accepted/rejected/reasonCodes/confidence)
            + 同事务原子 current_judgment_count += 1
     失败 -> 记 sim_judge_error，跳过
4. current_judgment_count >= target_judgment_count
     -> candidate_sets.status = TRAIN_READY
   0 < current < target -> PARTIAL_JUDGED

当前代码已实现的最小闭环：
1. 路线生成后，training_samples 落 N 行（X）。
2. 外部调用 `/api/route-preferences/judgments` 保存 1 次 judgment。
3. 后端当前会整批回填 training_samples 的 label/weight 并置为 TRAIN_READY。
```

要点：

- 线上用户请求（`generation_source=ONLINE_USER`）**不走** LLM 评价，只生成并返回；评价交给离线 / 异步批处理，不阻塞用户拿路线。
- 路线生成和 LLM judge 可以分离线程池执行。当前 runner 使用路线生成线程池和 judge 线程池；主流程会等待所有 judgment 保存成功或失败后再返回统计，不会在 LLM 还没返回时退出。
- LLM timeout 默认 300 秒，不应随意降低；请求已经产生 token 成本，过早超时只会放大浪费。
- 候选路线少于 2 条时跳过 LLM judge 并记录原因，不生成 judgment。
- 多家可并行调用提速；每个 judgment 各自独立事务 + 原子自增计数，避免覆盖 / 漂移。
- `judge_prompt_version` 改动即升版本，便于 Python 按版本切片 / 隔离。

## 13. 质量控制

- 每个 candidate set 用 1~3 个 persona / 模型评价，避免单一模拟偏好。
- 定期与真实用户排序对齐评估。
- synthetic 样本权重低于真实用户样本；真实数据足够后逐步降低 synthetic 占比。
- 不让自由文本进入训练标签；当前 `personalReview` 只用于人工检查和 dry-run，不写库、不入训。LLM 不得新造 reason code。

重点防止：

```text
模型学到的是 LLM judge 的偏见，而不是真实用户偏好。
```

## 14. 与 Route Judge 的关系

Route Judge（路线级裁判 MLP，见《路线裁判与软拒绝设计》）是**未来**的线上裁判，当前未上线。LLM 模拟用户是离线 / 异步数据增强：

```text
已生成候选集（内存） -> 偏好判断 -> 训练样本
```

二者关系：

- Route Judge 上线后，LLM 模拟用户可读取其可解释维度与 reason codes 作为锚点；当前 v1 不依赖它。
- LLM 模拟用户不能覆盖线上判断，输出不能直接作为线上排序结果，样本只能低权重进入训练。

## 15. 已确认决策

```text
1. MinIO 分工确定：candidate-set ingest 保存 raw snapshot + X，judgment ingest 保存 Y，dataset manifest 固化训练版本。
2. 首轮 LLM 评价在生成流程内完成，读内存路线，不重新规划；补 k 只读 MinIO frozen raw snapshot。
3. 一次生成 = 一个 candidate set；补评不改写候选路线和 X。
4. 落库和训练只使用 5 个字段（ranking/accepted/rejected/reasonCodes/confidence），
   `personalReview` 仅用于人工检查和 dry-run；candidateSetId/judgeType/judgeModel/judgePromptVersion 由编排层注入。
5. reasonCodes 固定为 9 个，落库前白名单校验；未知 code 判失败或由训练脚本显式跳过该 judgment 并记录原因。
6. 每条评价的权重按 judge_type 决定；judgments 一次评价一个对象 append，互不覆盖。
7. MinIO ingest 是写入区；versioned dataset 是训练读取区。dataset builder 成功写出并校验 manifest 后，只删除已处理 ingest 对象。
8. Python 训练时直接读 `judgments.parquet` 当 Y，按 candidate_set_id + route_code 关联 X；不再有 PG label/weight 回填路径。
```

待定：

```text
1. 是否需要在 judgment 对象中增加 judgeRunKey / personaId 以便更强幂等。
2. synthetic persona v1 的数量与覆盖范围，每批跑几个 persona。
3. acceptedRouteCodes 的判定口径（仅前 1~2 条，还是按绝对质量自由选）。
4. 是否需要单独的离线 sim_judge_batch manifest 记录批次模型 / prompt / persona 分布与成本。
```

## 16. 相关文档

```text
《路线裁判与软拒绝设计》
《路线偏好排序模型训练设计》
《推荐路径算法》
```
