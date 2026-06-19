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

2. 一次生成 = 一次评价批。
   这次调用了哪几家 LLM / 哪几个 persona，就只有这几个评价。
   之后不再让新的 LLM 回头评价这批老路线。
   因此候选路线的"人类可读正文"不需要落库。

3. 三张表只服务 Python 训练，不是 LLM 的输入源。
   LLM 的输入来自流程内存，不是来自这三张表。
```

第 2 条带来一个被接受的取舍：将来接入新的 LLM 厂家，无法回头评价历史 candidate set（内存已释放、库里也没存可读路线）。对冷启动批量造数据的用法，这个取舍可接受。

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

## 2. 三张表与各自边界

路线偏好训练数据由三张表承载，1 → N → N 关系：

```text
route_preference_candidate_sets   一次路线生成一行    批次生命周期 / 评价调度
  1 -> N route_preference_training_samples   一条候选路线一行    训练输入 X（特征）
  1 -> N route_preference_judgments          一次评价一行        训练监督 Y（偏好）
```

举例：一次生成 5 条路线，3 个 LLM 评价。

```text
candidate_sets : 1 行
  set_001, route_count=5, status=TRAIN_READY

training_samples : 5 行
  set_001 + A / B / C / D / E，各存该路线的 stop/segment/derived + context

judgments : 3 行
  set_001 + GPT    + ranking/reasonCodes/confidence
  set_001 + Claude + ranking/reasonCodes/confidence
  set_001 + Gemini + ranking/reasonCodes/confidence
```

边界一句话：

```text
candidate_sets   管"这一批数据走到哪一步了"
training_samples 存"每条路线长什么样"（只存 X，不存 label）
judgments        存"谁觉得哪条更好"（一次评价一行，append）
```

LLM 模拟用户**只写** `route_preference_judgments`（`judge_type = LLM_SIM_USER`），不写另外两张表，也不写任何真实行为事件表（`route_feedback` 等）。原因：

- LLM 没有真实点击、收藏、开始、完成这些行为；把判断伪装成真实事件会污染真实行为分布。
- 训练时需要按来源区分样本，并给 synthetic 样本更低权重。

还要区分 `userPreferenceProfile` 与 judgment：

```text
userPreferenceProfile : 用户偏好画像，是路线生成/评价的输入之一，进入 context。
judgments             : 模拟或真实的偏好判断，是后续训练的监督来源，不是推理输入。
```

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

v1 让 LLM 像"读行程的用户"一样判断。若后续发现 LLM 对折返 / 超时 / 预算不敏感，再作为 prompt v2 旋钮，**少量**补几个锚点指标（它们与 8 个 reason code 大致一一对应）：

```text
interestCoverageRatio     <-> LOW_INTEREST_COVERAGE
avgGoalScore              <-> WEAK_GOAL_FIT
categoryDiversityRatio    <-> LOW_DIVERSITY
backtrackingSegmentRatio  <-> BAD_SPATIAL_FLOW
timeBudgetUsageRatio / missingRequiredMealFlag <-> BAD_TIME_STRUCTURE / HIGH_FATIGUE
budgetPressure            <-> BUDGET_MISMATCH
highRiskStopRatio         <-> HIGH_ROUTE_RISK
```

不建议输入：完整矩阵、大量 POI 原始详情、真实用户行为标签、历史 judgment、训练 label。后两者会造成标签泄漏。

## 4. 表结构

### 4.1 route_preference_candidate_sets（批次 / 生命周期表）

不存路线特征，也不存评价正文，只记录这一批的生命周期。已在 V8 迁移落地，列以 DDL 为准：

```text
id
request_id               -- NOT NULL（V8 暂未加 FK 到 route_requests）
user_id                  -- FK users(id)
generation_source        -- VARCHAR(64) DEFAULT 'ONLINE'；离线批量可用单独取值
route_count              -- DEFAULT 0
target_judgment_count    -- 期望评价数，可空
current_judgment_count   -- DEFAULT 0
status                   -- VARCHAR(32) DEFAULT 'GENERATED'
created_at / updated_at
```

用处：找哪些批次还没评价；判断某批是否够训练；区分线上 / 离线数据；避免每次 group by training_samples 扫批次。

> 取值约定（待落库口径确认）：`status` 建议 `GENERATED / JUDGING / PARTIAL_JUDGED / TRAIN_READY / FAILED`；`generation_source` 当前默认 `ONLINE`，离线批量造数据建议用一个独立值（如 `OFFLINE_BATCH`）以便切片。若担心 `target` 永远凑不齐，可再加一个"最少评价数"阈值（V8 暂无此列），或直接用 `current_judgment_count >= target_judgment_count` 判定。

### 4.2 route_preference_training_samples（路线特征表，v1 只存 X）

一次生成 5 条路线写 5 行。列以 V8 DDL 为准：

```text
id
candidate_set_id
request_id
user_id
route_code               -- VARCHAR(16)，A / B / C / D / E
generated_route_id       -- FK generated_routes(id)，可空
feature_schema_version
stop_matrix_json          -- 逐路线不同
segment_matrix_json       -- 逐路线不同
route_derived_vector_json -- 逐路线不同
context_cross_vector_json -- 逐路线不同（路线 × 上下文）
context_json              -- 同批相同（请求上下文 + 用户偏好原始值）
label_source              -- 见下方语义说明
label_json
sample_weight             -- NUMERIC(8,4)
sample_status            -- VARCHAR(32) DEFAULT 'GENERATED'
created_at / updated_at
唯一键 (candidate_set_id, route_code)
```

label 三列的用法（v1 已定，方案 B）：

```text
judgments 表   = 每个 judge 的原始评价，一次评价一行，append，互不覆盖（唯一真源）。
本表 label 列  = v1 留空，不写。
```

v1 不做离线融合、不回填 label。训练时 Python 直接读 judgments 当 Y，按 `candidate_set_id` 拉同批全部 judgments、再按 `route_code` 与本表的 X 关联，pairwise 样本即可生成（见 §8.1）。这样彻底避开"多 judge 覆盖"问题，也不必现在就定融合策略。

> 待修正的实现：当前 `markCandidateSetTrainReady` 按 `candidate_set_id` 整批 `UPDATE` 回填 label，会被第二个 judge 覆盖。方案 B 下应**停用这段整批回填**（改 no-op，label 列保持为空）。三列暂留作未来融合标签的预留位，不删。`context_json` 同批重复，后续可选上移到 candidate_sets。

### 4.3 route_preference_judgments（评价表，一次评价一行）

一个 LLM / 用户 / 标注员对同一批评价一次写一行；多家评价同一批就多行 append。

```text
id
candidate_set_id
judge_type               -- LLM_SIM_USER / REAL_USER / HUMAN_ANNOTATOR / HEURISTIC_JUDGE
judge_model              -- 模型名，如 gpt-4o / claude-... / gemini-...
judge_prompt_version     -- prompt 版本，如 llm-sim-user-v1
judge_run_key            -- 建议新增：幂等键，重试 / 区分重复运行
ranking_json             -- ["C","A","B","E","D"]
accepted_route_codes_json -- ["C","A"]
rejected_route_codes_json -- ["E","D"]
reason_codes_json        -- {"D":["BAD_SPATIAL_FLOW"], "E":["LOW_INTEREST_COVERAGE"]}
confidence               -- LLM 自评，仅参考
status
created_at / completed_at / updated_at
```

> 现状与建议：`judge_type / judge_model / judge_prompt_version / ranking_json / accepted_route_codes_json / rejected_route_codes_json / reason_codes_json / confidence / status` 已在 V8 落地并与接口对齐。`judge_run_key`、可选的 `persona_id` 为建议新增；幂等建议落成 `UNIQUE(candidate_set_id, judge_type, judge_model, judge_prompt_version, judge_run_key)`，一家失败可整批安全重跑而不灌重复行。

`judge_type` 取值：`REAL_USER / LLM_SIM_USER / HUMAN_ANNOTATOR / HEURISTIC_JUDGE`。LLM 模拟用户写 `LLM_SIM_USER`。

## 5. LLM 输出 JSON schema

LLM 只输出偏好判断，**不输出** `candidateSetId / judgeType / judgeModel / judgePromptVersion`——这些服务端已知，由编排层注入，避免模型编错 set 或伪造来源。

LLM 实际返回：

```json
{
  "ranking": ["C", "A", "B", "E", "D"],
  "acceptedRouteCodes": ["C", "A"],
  "rejectedRouteCodes": ["D"],
  "reasonCodes": { "D": ["BAD_SPATIAL_FLOW", "HIGH_FATIGUE"], "E": ["LOW_INTEREST_COVERAGE"] },
  "confidence": 0.65
}
```

约束：

- 只输出这 5 个字段，不要附带任何自然语言理由（不要 freeText / explanation 之类）。
- `ranking` 必须是本批 `routeCode`（A/B/C/D/E）的**全排列**。
- `acceptedRouteCodes` / `rejectedRouteCodes` 必须是本批 `routeCode` 子集。
- 同一条路线不能同时出现在 accepted 和 rejected。
- `reasonCodes` 的 key 必须是本批 `routeCode`；value 只能用固定 8 个 reason code。
- `confidence` 取 `[0,1]`，训练时不直接等同真实置信度。

编排层拿到上面 5 个字段后，注入 `candidateSetId`、`judgeType=LLM_SIM_USER`、`judgeModel`、`judgePromptVersion`，组成完整 `RoutePreferenceJudgmentParam` 落库（字段名严格对齐接口：`acceptedRouteCodes / rejectedRouteCodes / reasonCodes`）。

固定 reason codes（仅此 8 个，建议落成 Java enum 复用）：

```text
LOW_INTEREST_COVERAGE   兴趣覆盖不足
WEAK_GOAL_FIT           与本次目标不贴合
LOW_DIVERSITY           类型太单一 / 重复
BAD_SPATIAL_FLOW        走法绕路 / 折返
BAD_TIME_STRUCTURE      时段安排不合理（缺正餐 / 节奏乱）
HIGH_FATIGUE            太累 / 距离体力压力大
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

reasonCodes 只能从下面 8 个里选，不许自创：
LOW_INTEREST_COVERAGE / WEAK_GOAL_FIT / LOW_DIVERSITY / BAD_SPATIAL_FLOW /
BAD_TIME_STRUCTURE / HIGH_FATIGUE / BUDGET_MISMATCH / HIGH_ROUTE_RISK

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
要求模型只回 §5 的 5 字段 JSON。
```

- `judgePromptVersion`：标识**模板**版本，如 `llm-sim-user-v1`；prompt 文案（6.1/6.3/6.4 任一）改动即升版，落进 judgments，便于 Python 按版本切片。
- persona 内容版本走 persona 的 `questionnaireVersion`（如 `sim-persona-v1`），与 prompt 模板版本相互独立。

## 7. 为什么不能只输出排序

排序只能表达相对偏好（C > A > B > E > D），不能表达"C 和 A 真的可以给用户、B 只是比 E/D 好、D 绝对不行"。所以必须同时输出 `ranking + acceptedRouteCodes + rejectedRouteCodes`，才能同时支撑：

- v1 的 pairwise 训练样本；
- 后续 pointwise accept / reject 与概率校准；
- 后续 listwise 排序样本。

## 8. 训练样本派生与读取

`route_preference_judgments` 是原始偏好判断，不是最终训练样本。离线 Python 任务从它派生。

### 8.1 读取路径

```text
1. 选 status = TRAIN_READY 的 candidate_sets
2. 用 candidate_set_id 拉 training_samples，得到每条路线的 X（按 route_code）
3. 用 candidate_set_id 拉该批全部 judgments，得到各 judge 的 ranking / reason / confidence
4. 用 route_code 对齐 X 与评价
5. 展开 pairwise 训练样本
```

一组路线 × N 个评价 = N 套监督信号。`training_samples` 只提供 X，监督 Y 全部来自 judgments，互不覆盖。

### 8.2 Pairwise 样本

由每条 judgment 的 `ranking` 展开：

```text
ranking = [C, A, B, E, D]
=> C>A, C>B, C>E, C>D, A>B, A>E, A>D, B>E, B>D, E>D
```

### 8.3 后续 Pointwise / Listwise

`acceptedRouteCodes` -> label.accept=1，`rejectedRouteCodes` -> label.accept=0，中间路线不强行打硬标签（弱标签或仅参与 pairwise）。完整 `ranking` 用于 listwise。v1 主目标是 pairwise，pointwise accept 留后续 P(accept) 校准。

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
   tagAffinities       -> userInterestAffinity
   旧 persona 的 classicAffinity/photoAffinity/pacePreference/riskTolerance 等一个都不读，是死字段。
2. 模型训练时"用户是谁"来自 context_json.userPreferenceProfile，就是这套结构。
   打标签的 persona 必须就是模型条件的那个用户，X 与 Y 才同一个人。
```

persona 字段（= `UserPreferenceProfileDTO`）：

```text
distanceSensitivity   0~1
budgetSensitivity     0~1
transferSensitivity   0~1
hiddenGemAffinity     0~1
profileConfidence     0~1   -- 见下方旋钮
tagAffinities         Map<tagCode, 0~1>，key 必须是 interest_tag_catalog 的合法 tag_code
                            （如 LOCAL / CLASSIC / PHOTO / NIGHT_MARKET_VIEW / FOOD / COFFEE）
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
  "tagAffinities": { "LOCAL": 0.9, "FOOD": 0.8, "COFFEE": 0.7, "CLASSIC": 0.3 },
  "newUser": false,
  "questionnaireVersion": "sim-persona-v1"
}
```

关键旋钮 `profileConfidence`：它是 POI 个性化的总闸。设 0（或 `newUser=true` 的空画像）会把个性化全关——选出的路线对 persona 偏好不敏感，persona 再去评"对它没差别的路线"，信号很弱。**造冷启动训练数据时 `profileConfidence` 取正值（建议 0.6~0.9）。**

完整闭环（X / 选点 / Y 同一个人）：

```text
persona P 的画像 -> 喂 POI Linear 选点、生成路线
                 -> 写入 candidate set 的 context_json.userPreferenceProfile = P
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
reasonCodes 的 key ∈ 本批 routeCode；value ∈ 固定 8 码（越界即剔除或判失败）。
被硬拒绝路线不得进入 acceptedRouteCodes。
confidence ∈ [0,1]。
```

失败处理：

```text
不写 judgment，记 sim_judge_error。
按 maxRetry 重试一次（重新 prompt）。
仍失败 -> 跳过该 judge，不阻塞其他 judge、不阻塞整批。
```

## 12. 流程内调用 / 保存链路

```text
[离线 / 批量生成流程，路线在内存]
1. 生成 N 条路线 -> candidate_sets 落 1 行
     generation_source=OFFLINE_BATCH, route_count=N,
     target_judgment_count=配置(模型×persona)数, status=JUDGING
2. training_samples 落 N 行（X） —— 已实现
3. for each judge in 配置列表 (judgeModel × persona):
     构 prompt -> 调 LLM -> 解析 JSON -> 校验
     成功 -> append 1 行 judgments
            (judge_type=LLM_SIM_USER, judge_model, judge_prompt_version,
             judge_run_key, ranking/accepted/rejected/reasonCodes/confidence)
            + 同事务原子 current_judgment_count += 1
     失败 -> 记 sim_judge_error，跳过
4. current_judgment_count >= target_judgment_count
     -> candidate_sets.status = TRAIN_READY
   0 < current < target -> PARTIAL_JUDGED
```

要点：

- 线上用户请求（`generation_source=ONLINE_USER`）**不走** LLM 评价，只生成并返回；评价交给离线 / 异步批处理，不阻塞用户拿路线。
- 多家可并行调用提速；每个 judgment 各自独立事务 + 原子自增计数，避免覆盖 / 漂移。
- `judge_prompt_version` 改动即升版本，便于 Python 按版本切片 / 隔离。

## 13. 质量控制

- 每个 candidate set 用 1~3 个 persona / 模型评价，避免单一模拟偏好。
- 定期与真实用户排序对齐评估。
- synthetic 样本权重低于真实用户样本；真实数据足够后逐步降低 synthetic 占比。
- 不让 LLM 输出自由文本理由（只要结构化的 5 字段）；不让 LLM 新造 reason code。

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
1. 三张表分工确定：candidate_sets（批次） / training_samples（X） / judgments（Y，append）。
2. LLM 评价在生成流程内完成，读内存路线，不读库、不重新规划。
3. 一次生成 = 一次评价批，之后不追评；候选路线可读正文不落库。
4. LLM 只输出 5 字段（ranking/accepted/rejected/reasonCodes/confidence），
   其余由编排层注入；字段名对齐接口。
5. reasonCodes 固定为 8 个，落库前白名单校验。
6. 每条评价的权重按 judge_type 决定；judgments 一次评价一行 append，互不覆盖。
7. 三张表（candidate_sets / training_samples / judgments）已在 V8 落地。
8. 方案 B：training_samples 的 label/weight 列 v1 留空，Python 训练时直接读
   judgments 当 Y、按 candidate_set_id + route_code 关联 X；停用整批回填。
```

待定：

```text
1. judge_run_key / persona_id 是否加入 judgments 表与接口（V8 暂无）。
2. status / generation_source 的取值口径定稿。
3. synthetic persona v1 的数量与覆盖范围，每批跑几个 persona。
4. acceptedRouteCodes 的判定口径（仅前 1~2 条，还是按绝对质量自由选）。
5. 是否需要 sim_judge_batch 记录批次模型 / prompt / persona 分布与成本，
   或直接用 candidate_sets + judgments 派生。
```

## 16. 相关文档

```text
《路线裁判与软拒绝设计》
《路线偏好排序模型训练设计》
《推荐路径算法》
```
