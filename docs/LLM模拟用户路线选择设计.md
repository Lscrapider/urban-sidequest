# LLM 模拟用户路线选择设计

本文设计 LLM 模拟用户选择模块。它用于真实用户数据不足时，对同一批候选路线做偏好判断，生成冷启动训练信号。

一句话：

```text
LLM 模拟用户不伪装成真实点击行为。
它只写偏好判断，用来生成低权重训练样本。
```

## 1. 这个模块解决什么问题

Route Judge 需要训练 `P(accept)`，但早期真实用户反馈不足。

真实用户数据包括：

- 用户看到了哪些路线。
- 用户选了哪条。
- 用户是否排序。
- 用户是否收藏、开始、完成或跳过。

这些数据质量高，但积累慢。LLM 模拟用户用于冷启动阶段补充：

```text
给定一次请求 + K 条候选路线 + Route Judge trace
  -> 模拟一个符合请求画像的用户
  -> 判断哪些路线更可能被喜欢
  -> 输出排序、可接受路线、明显拒绝路线和固定 reason codes
```

这个模块只补“偏好判断”，不补“真实行为事件”。

## 2. 与真实反馈事件的边界

需要拆成两层数据：

```text
route_feedback_event
  真实用户/客户端行为日志。

route_preference_judgment
  偏好判断日志。
  真实用户排序、LLM 模拟用户、人工标注、离线 heuristic judge 都可以写。
```

LLM 模拟用户只能写：

```text
route_preference_judgment
```

不能写：

```text
route_feedback_event
```

原因：

- LLM 没有真实点击、收藏、开始路线、完成路线这些行为。
- 如果把 LLM 判断伪装成真实事件，会污染真实行为分布。
- 后续训练需要区分样本来源，并给 synthetic 样本更低权重。

还需要区分 `userPreferenceProfile` 和反馈/judgment：

```text
userPreferenceProfile:
  用户偏好表，是 Route Judge 在线推理输入的一部分，进入 contextCrossVector。

route_feedback_event / route_preference_judgment:
  用户行为或模拟判断，是后续训练监督来源，不是当前推理输入维度。
```

因此不需要为 LLM 模拟用户或用户主动反馈新增第五块路线输入。Route Judge 的第四块 `contextCrossVector` 已经负责描述“这条路线对这个用户/这次请求怎么样”；LLM 模拟用户输出只用于生成训练样本，不反写 `userPreferenceProfile`。

## 3. 输入来源

LLM 模拟用户选择不重新生成路线，只读取已经存下来的路线生成快照。

### 3.1 请求上下文

来源：`route_request_trace` 或路线生成上下文快照。

```text
requestId
userId / syntheticPersonaId
routeGoal
transportProfile
budgetLevel
interestTags
departureTime
durationMinutes
routeTimeStructure
area
weather
userPreferenceProfile
```

这部分回答：

```text
这个用户当时想要什么？
```

### 3.2 候选路线集合

来源：`route_candidate_set` + `route_candidate`。

```text
candidateSetId
candidateRouteIds
routeCode
title
summary
totalDurationMinutes
totalDistanceMeters
budgetCent
riskLevel
stops
segments
LLM route raw json
```

这部分回答：

```text
系统当时给了哪些路线？
```

候选集合必须包含：

- 展示给用户的路线。
- 被软拒绝但仍有分析价值的路线。
- 排名靠后的路线。
- 必要时包含被硬拒绝路线的摘要，但硬拒绝路线默认不参与模拟用户偏好排序。

### 3.3 Route Judge trace

来源：`route_judge_trace`。

```text
featureSchemaVersion
thresholdsVersion
modelVersion
stopMatrix
segmentMatrix
routeDerivedVector
contextCrossVector
dimensionScores
routeScore
acceptProbability
hardValidationResult
softRejected
reasonCodes
rawMetrics
trace
```

这部分回答：

```text
系统当时为什么觉得这条路线好或差？
```

LLM 模拟用户可以看到可解释结果，但不直接改写它。reason codes 只能从固定集合里选择。

## 4. 输出表：route_preference_judgment

建议新增偏好判断表：

```text
route_preference_judgment:
  judgmentId
  requestId
  candidateSetId

  judgeType
  judgeId
  judgeVersion
  promptVersion
  modelProvider
  modelName

  inputContextJson
  candidateRouteIds

  ranking
  pairwisePreferences
  acceptedRouteIds
  rejectedRouteIds
  routeReasonCodes
  freeTextReason

  confidence
  sampleWeight
  createdAt
```

字段含义：

| 字段 | 含义 |
| --- | --- |
| `judgeType` | 判断来源，例如 `REAL_USER`、`LLM_SIM_USER`、`HUMAN_ANNOTATOR`、`HEURISTIC_JUDGE` |
| `judgeVersion` | judge 版本，例如 `llm-sim-user-v1` |
| `promptVersion` | 模拟用户 prompt 版本 |
| `inputContextJson` | 本次判断看到的请求、画像、候选摘要和 trace 摘要 |
| `candidateRouteIds` | 参与判断的候选路线 |
| `ranking` | 路线从好到差的排序 |
| `pairwisePreferences` | 可由 `ranking` 自动展开，也可离线生成 |
| `acceptedRouteIds` | 模拟用户认为愿意推荐/接受的路线 |
| `rejectedRouteIds` | 模拟用户认为明显不该推荐的路线 |
| `routeReasonCodes` | 每条低质路线对应的固定 reason codes |
| `confidence` | LLM 自评置信度，只作参考 |
| `sampleWeight` | 进入训练时的默认样本权重 |

`judgeType` 取值：

```text
REAL_USER
LLM_SIM_USER
HUMAN_ANNOTATOR
HEURISTIC_JUDGE
```

LLM 模拟用户写入：

```text
judgeType = LLM_SIM_USER
```

## 5. LLM 输出 JSON schema

LLM 模拟用户必须输出结构化 JSON，不允许自由发挥字段。

```json
{
  "ranking": ["routeC", "routeA", "routeB", "routeE", "routeD"],
  "acceptedRouteIds": ["routeC", "routeA"],
  "rejectedRouteIds": ["routeD"],
  "routeReasonCodes": {
    "routeD": ["BAD_SPATIAL_FLOW", "HIGH_FATIGUE"],
    "routeE": ["LOW_INTEREST_COVERAGE"]
  },
  "confidence": 0.65,
  "freeTextReason": "C 更符合本地生活和低预算，D 折返和距离压力明显。"
}
```

约束：

- `ranking` 必须是 `candidateRouteIds` 的全排列。
- `acceptedRouteIds` 和 `rejectedRouteIds` 必须是 `candidateRouteIds` 的子集。
- 同一条路线不能同时出现在 `acceptedRouteIds` 和 `rejectedRouteIds`。
- `routeReasonCodes` 只能使用 Route Judge 固定 reason codes。
- `confidence` 取 `[0,1]`，但训练时不能直接等同于真实置信度。
- `freeTextReason` 只用于人工检查和 prompt 调试，不进入模型标签。

固定 reason codes：

```text
LOW_INTEREST_COVERAGE
WEAK_GOAL_FIT
LOW_DIVERSITY
BAD_SPATIAL_FLOW
BAD_TIME_STRUCTURE
HIGH_FATIGUE
BUDGET_MISMATCH
HIGH_ROUTE_RISK
```

## 6. 为什么不能只输出排序

排序只能表达相对偏好：

```text
C > A > B > E > D
```

它不能表达：

```text
C 和 A 是真的可以给用户。
B 只是比 E、D 好，但不一定值得推荐。
D 是绝对不行。
```

因此 LLM 模拟用户必须同时输出：

```text
ranking
acceptedRouteIds
rejectedRouteIds
```

这样才能同时生成：

- pairwise 训练样本。
- pointwise accept/reject 训练样本。
- listwise 排序样本。

## 7. 训练样本派生

`route_preference_judgment` 是原始偏好判断，不是最终训练样本。

离线任务从它派生训练样本。

### 7.1 Pairwise 样本

由 `ranking` 展开：

```text
ranking = [C, A, B, E, D]

pairwise:
  C > A
  C > B
  C > E
  C > D
  A > B
  A > E
  A > D
  B > E
  B > D
  E > D
```

用途：

```text
训练路线间相对偏好。
```

### 7.2 Pointwise accept 样本

由 `acceptedRouteIds` / `rejectedRouteIds` 生成：

```text
route in acceptedRouteIds:
  label.accept = 1

route in rejectedRouteIds:
  label.accept = 0
```

中间路线如果没有明确 accept/reject，不强行打硬标签，可以作为弱标签或只参与 pairwise。

用途：

```text
训练 P(accept)。
```

### 7.3 Listwise 样本

由完整 `ranking` 生成：

```text
candidateSetId
candidateRouteIds
ranking
```

用途：

```text
后续训练 listwise reranker 或评估排序指标。
```

## 8. 样本权重

不同来源的判断不能同权。

建议冷启动权重：

```text
REAL_USER explicit ranking:
  sampleWeight = 1.00

REAL_USER selected / completed / favorited:
  sampleWeight = 0.80 ~ 1.00

HUMAN_ANNOTATOR:
  sampleWeight = 0.70

REAL_USER implicit skip / displayed-not-selected:
  sampleWeight = 0.20 ~ 0.40

LLM_SIM_USER:
  sampleWeight = 0.10 ~ 0.30

HEURISTIC_JUDGE:
  sampleWeight = 0.05 ~ 0.20
```

LLM 模拟用户只用于冷启动和数据增强，不应该长期压过真实用户行为。

## 9. LLM 模拟用户输入 Prompt 原则

LLM 模拟用户 prompt 不应该让模型“重新规划路线”，只让它扮演用户选择。

输入应包括：

```text
用户请求摘要
用户画像 / synthetic persona
userPreferenceProfile 快照（如果是真实用户）
候选路线摘要
每条路线的 stop 摘要
每条路线的 Route Judge dimensionScores
每条路线的 reasonCodes
必要 rawMetrics
```

不建议输入：

- 完整 `stopMatrix` / `segmentMatrix` 大矩阵。
- 大量 POI 原始详情。
- 真实用户行为标签。
- route_preference_judgment 历史结果。
- 训练 label。

原因：

- LLM 模拟用户应该判断路线体验，不应该复算底层特征。
- 输入太长会增加成本和不稳定性。
- 不能把未来标签泄露给模拟 judge。
- `userPreferenceProfile` 是可用画像输入；真实反馈事件和历史 judgment 是监督信号，不能作为本次模拟判断的输入标签。

## 10. Synthetic persona

如果没有真实用户画像，可以生成 synthetic persona。

建议 persona 字段：

```text
personaId
personaVersion
budgetSensitivity
distanceSensitivity
transferSensitivity
hiddenGemAffinity
classicAffinity
localLifeAffinity
photoAffinity
nightAffinity
pacePreference
riskTolerance
interestTags
negativePreferences
```

示例：

```json
{
  "personaId": "persona_low_budget_local_001",
  "budgetSensitivity": 0.9,
  "distanceSensitivity": 0.6,
  "hiddenGemAffinity": 0.7,
  "localLifeAffinity": 0.9,
  "photoAffinity": 0.3,
  "pacePreference": "RELAXED",
  "riskTolerance": 0.4,
  "interestTags": ["LOCAL", "FOOD", "COFFEE"],
  "negativePreferences": ["long walking", "expensive restaurant", "tourist crowd"]
}
```

persona 不是用户真实画像。它只用于扩充冷启动场景覆盖。

## 11. 校验规则

LLM 输出必须经过规则校验后才能落库。

校验项：

```text
ranking 是否包含全部 candidateRouteIds，且无重复。
acceptedRouteIds / rejectedRouteIds 是否是 candidateRouteIds 子集。
acceptedRouteIds 与 rejectedRouteIds 是否互斥。
routeReasonCodes 是否来自固定枚举。
被硬拒绝路线是否没有进入 acceptedRouteIds。
confidence 是否在 [0,1]。
```

校验失败处理：

```text
不写 route_preference_judgment。
记录 sim_judge_error trace。
可按 maxRetryCount 重试一次。
```

## 12. 质量控制

LLM 模拟用户有偏差，必须限制使用方式。

建议：

- 每个 candidate set 可以用 1-3 个 persona / judge 版本判断，避免单一模拟偏好。
- 定期和真实用户排序对齐评估。
- synthetic 样本训练权重低于真实用户样本。
- 当真实用户数据足够后，逐步降低 synthetic 样本占比。
- 不把 LLM 的自由文本原因当作标签。
- 不让 LLM 新造 reason code。

需要重点防止：

```text
模型学习到 LLM judge 的偏见，而不是真实用户偏好。
```

## 13. Pipeline

推荐流程：

```text
路线生成完成并落库
  -> 读取 request trace / candidate set / route judge trace
  -> 选择真实用户画像或 synthetic persona
  -> 构造 LLM 模拟用户 prompt
  -> LLM 输出 ranking + accepted/rejected + reason codes
  -> 校验输出
  -> 写 route_preference_judgment
  -> 离线派生 route_accept_training_sample / route_pairwise_training_sample
```

这个流程不阻塞用户拿路线。它可以异步执行，也可以只对采样请求执行。

## 14. 与 Route Judge 的关系

Route Judge 是线上裁判：

```text
候选路线 -> 特征 -> 评分 -> 软拒绝 / 排序 / reason codes
```

LLM 模拟用户是离线或异步数据增强：

```text
已生成候选集 + trace -> 偏好判断 -> 训练样本
```

二者关系：

- LLM 模拟用户可以读取 Route Judge 的可解释维度和 reason codes。
- LLM 模拟用户不能覆盖 Route Judge 的线上判断。
- LLM 模拟用户输出不能直接作为线上排序结果。
- LLM 模拟用户样本只能低权重进入训练。

## 15. 待确认

1. `route_preference_judgment` 是否作为统一偏好判断表，承载真实用户排序、LLM 模拟用户和人工标注。
2. LLM 模拟用户 v1 是否只做异步离线，不进入线上请求链路。
3. LLM 模拟用户默认 `sampleWeight` 初值。
4. synthetic persona v1 的数量和覆盖范围。
5. 是否每个 candidate set 只跑一个 persona，还是按场景采样多个 persona。
6. `acceptedRouteIds` 的判断标准：只允许前 1-2 条，还是允许 LLM 按绝对质量自由选择。
7. 是否需要单独的 `sim_judge_batch` 表记录批次、模型、prompt、persona 分布和成本。

## 16. 相关文档

```text
《路线裁判与软拒绝设计》
《POI智能筛选与成对偏好排序训练设计》
《推荐路径算法》
```
