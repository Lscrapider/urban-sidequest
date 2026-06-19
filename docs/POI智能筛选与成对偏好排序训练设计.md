# POI 智能筛选与成对偏好排序训练设计

本文记录 POI 候选池筛选算法的当前设计。它服务于路线生成链路中的 POI 预筛阶段，目标是把较大的真实 POI 候选池，例如 100 个 POI，筛成适合后续路线编排的小池，例如 20/30/40 个 POI。

本文只定义 POI 预筛和预筛模型训练。路线生成后的整条路线评估、路线结构打分、点位衔接打分，后续单独设计，不混入本文主线。

## 1. 核心边界

POI 预筛发生在路线生成之前。此时系统只有候选 POI、用户请求、用户画像和环境信息，还没有一条完整路线。

因此，线上预筛模型输入只能包含：

```text
poiFeature
requestFeature
userPreferenceVector
environmentFeature
```

不能依赖“这个点在路线里的前后文”，因为预筛阶段还没有路线，也就不知道它前一个点、后一个点、到达时间、路线位置和衔接成本。

本文中的训练上下文只用于判断两个点位样本是否可比，以及训练权重应该多高。它不作为线上预筛模型输入。

## 2. 线上流程

推荐整体结构：

```text
100 个真实 POI 候选
  -> Hard Constraint Gate
  -> Feature Extractor
  -> Linear Ranker
  -> Neural Residual Scorer
  -> Diversity-aware Sampler
  -> 20/30/40 个 POI 小池
  -> 后续路线编排
```

最终分数：

```text
finalScore = linearScore + neuralResidual
```

冷启动时：

```text
neuralResidual = 0
finalScore = linearScore
```

职责边界：

```text
Hard Constraint Gate:
  过滤明显不可执行或明显错误的 POI。

Linear Ranker:
  提供可解释的基础排序分。

Neural Residual Scorer:
  学习 Linear 难以表达的非线性残差。

Diversity-aware Sampler:
  从高分候选中抽出结构均衡、可变化、能编路线的小池。
```

## 3. 前端偏好协议

前端不暴露算法内部字段，只暴露用户能理解的偏好。首版建议先稳定以下维度：

```text
routeGoal:
  CLASSIC
  LOCAL
  PHOTO
  NIGHT
  LOW_BUDGET
  RELAXED

interestTags:
  SCENIC
  MUSEUM
  LOCAL_FOOD
  COFFEE
  PARK
  SHOPPING
  OLD_STREET
  HIDDEN_GEM

transportProfile:
  WALK_ONLY
  WALK_SUBWAY
  BIKE_SUBWAY
  WALK_TAXI

paceLevel:
  RELAXED
  BALANCED
  COMPACT

budgetLevel:
  LOW
  NORMAL
  FLEXIBLE
```

后续可评估是否纳入：

```text
explorationLevel
timeStructure
qualityPreference
accessibilityPreference
crowdTolerance
```

每个前端选项都必须能映射到后端 feature 或权重增量。例如：

```text
routeGoal = LOCAL
  -> goalLocalMatch
  -> localCategoryMatch
  -> localFoodBoost
  -> chainStorePenalty

transportProfile = WALK_ONLY
  -> distanceNorm
  -> isolatedDistanceNorm
  -> walkingSegmentRisk
  -> clusterConnectivity

budgetLevel = LOW
  -> budgetNorm
  -> freePoiMatch
  -> expensivePoiPenalty
```

## 4. 预筛特征协议

后续训练数据、Linear 矩阵、Neural 输入和 ONNX 输入都必须使用稳定特征协议。首版按四类输入组织。

### 4.1 poiFeature

描述 POI 自身是什么，至少覆盖：

```text
基础信息:
  类别、子类别、候选角色、是否兜底。
  注:`是否必去(isMustVisit)` 不作训练特征——它恒被 Hard Constraint Gate 强保留,
  当特征会造成目标泄漏(模型学成 mustVisit→必留 的循环),无信息增益;
  必去保留逻辑见下文 mustVisit 保留小节,与 Linear 文档 §2.1 口径一致。

质量信息:
  评分、图片、描述完整度、地址完整度、品牌可信度、信息缺失风险。

成本信息:
  预算、人均、门票、是否免费、是否高消费。

交通信息:
  到区域中心距离、最近地铁/公交、交通可达等级、步行可达性。

内容语义:
  是否经典、是否本地、是否适合拍照、是否适合夜游、是否小众、是否适合安静/休息。

风险信息:
  闭店风险、排队风险、天气敏感、重复同质化风险、孤立远点风险。
```

### 4.2 requestFeature

描述这次用户想要什么路线，至少覆盖：

```text
路线目标:
  routeGoal、目标强度、是否允许多目标。

兴趣偏好:
  interestTags、兴趣权重、是否有强偏好。

交通组合:
  transportProfile、最大可接受步行、地铁/打车/骑行容忍。

时间结构:
  出发时间、路线时长、是否跨午饭/晚饭/夜间、闭店风险窗口。

节奏预算:
  paceLevel、budgetLevel、explorationLevel、crowdTolerance。

区域约束:
  自动半径、手动画区、起点、终点、必去点。
```

### 4.3 userPreferenceVector

描述用户长期偏好，至少覆盖：

```text
兴趣长期权重:
  景点、展馆、本地餐饮、咖啡、公园、商圈、老街、小众点等偏好。

成本敏感度:
  距离敏感、预算敏感、换乘敏感、排队敏感、拥挤敏感。

探索倾向:
  热门/小众偏好、稳定/变化偏好、随机采样温度倾向。

行为反馈:
  收藏、完成、跳过、替换、dislike、提前结束等反馈聚合。

用户状态:
  是否新用户、画像置信度、最近偏好变化、长期偏好和短期偏好权重。
```

### 4.4 environmentFeature

描述本次请求的外部环境，至少覆盖：

```text
当前时间:
  上午、午饭、下午、晚饭、夜间、是否临近闭店窗口。

天气:
  晴、雨、热、冷、风、空气质量、户外敏感度。

城市状态:
  节假日、周末、工作日、热门区域拥挤风险。
```

## 5. 特征规约化规则

所有进入矩阵和神经网络的 feature 必须先规约化，不能把不同量纲直接相加或训练。

最低规则：

```text
布尔特征:
  0 或 1。

比例特征:
  0 到 1。

惩罚特征:
  0 到 1，由负权重表达扣分。

溢出特征:
  允许到 1.5 或 2.0，但必须有明确上限。

枚举特征:
  使用 one-hot、multi-hot 或稳定 ordinal 编码，不能直接使用业务字符串。

缺失特征:
  必须有默认值，并额外提供 missing indicator，避免把未知误当成低质量。
```

示例：

```text
ratingNorm = rating / 5
distanceNorm = min(distanceMeters / radiusMeters, 1.5)
budgetNorm = min(avgPriceCent / budgetCapCent, 2.0)
hasImage = imageUrls 非空 ? 1 : 0
isRatingMissing = rating 为空 ? 1 : 0
```

## 6. Hard Constraint Gate

Hard Constraint Gate 是资格判断层，不是智能排序层。

过滤或保留规则：

```text
mustVisit 保留:
  用户必去点原则上强制保留，除非坐标或基础信息完全不可用。

基础有效性过滤:
  坐标为空、名称为空、类别异常、重复严重、明显无效的 POI 直接过滤。

区域和交通硬门槛:
  根据区域、transportProfile、路线时长判断是否超出可执行范围。

MEAL / REST / ANCHOR 最低保留:
  如果路线跨午餐、晚餐或长时段，必须保证餐饮、咖啡/休息、主锚点候选数量。

信息缺失降级:
  缺图片、缺描述、缺地址、评分弱的 POI 不一定直接过滤，但要写入 risk feature。

同质化预过滤:
  同一品牌、同一类别、同一小片区过多时，先做聚合或降级。
```

交通组合对硬门槛的影响：

```text
WALK_ONLY:
  更严格过滤远距离和孤立点。

WALK_SUBWAY:
  放宽直线距离，但要求地铁/公交可达性。

BIKE_SUBWAY:
  中距离可接受，但仍要过滤过度绕行和孤立点。

WALK_TAXI:
  距离门槛最宽，但预算风险和路线总时长必须进入硬检查。
```

## 7. Linear Ranker

Linear Ranker 使用特征向量和权重矩阵计算可解释的 `linearScore`。

最低版本权重矩阵：

```text
M = [
  W_interest,
  W_goal,
  W_quality,
  W_transport,
  W_distance,
  W_budget,
  W_risk,
  W_personalization
]
```

矩阵计算：

```text
S = M * X
```

其中：

```text
X = concat(
  poiFeature,
  requestFeature,
  userPreferenceVector,
  environmentFeature
)
```

子分数：

```text
S = [
  interestScore,
  goalScore,
  qualityScore,
  transportScore,
  distanceCost,
  budgetCost,
  riskCost,
  personalizationScore
]
```

总线性分：

```text
linearScore =
    interestScore
  + goalScore
  + qualityScore
  + transportScore
  + distanceCost
  + budgetCost
  + riskCost
  + personalizationScore
```

注意：

- 不属于某一行的 feature 列权重为 `0`，不是 `1`。
- `distanceCost`、`budgetCost`、`riskCost` 虽然叫 cost，但矩阵行里通常是负权重，最后仍然直接加进 `linearScore`。
- 这 8 行是最低版本，不代表后续不能扩展。

8 个矩阵行职责：

```text
W_interest:
  衡量当前 POI 是否命中用户兴趣，以及是否符合用户长期兴趣画像。

W_goal:
  衡量当前 POI 对本次路线目标的贡献。

W_quality:
  衡量 POI 自身信息质量和可信度。

W_transport:
  衡量 POI 在当前交通组合下是否可达。

W_distance:
  衡量距离、绕行、孤立点和体力成本。

W_budget:
  衡量当前 POI 对预算的压力。

W_risk:
  衡量闭店、排队、拥挤、天气、同质化、缺信息等风险。

W_personalization:
  衡量用户长期画像和短期行为对当前 POI 的个性化偏置。
```

动态权重：

```text
M_final =
  M_base
  + Delta_goal(routeGoal)
  + Delta_transport(transportProfile)
  + Delta_pace(paceLevel)
  + Delta_budget(budgetLevel)
  + Delta_time(timeStructure)
  + Delta_quality(qualityPreference)
  + Delta_accessibility(accessibilityPreference)
  + Delta_crowd(crowdTolerance)
  + Delta_userProfile(userPreferenceVector)
```

示例：

```text
transportProfile = WALK_ONLY:
  W_distance.distanceNorm 更负
  W_distance.isolatedDistanceNorm 更负
  W_transport.transitHigh 加分降低
  W_risk.walkingFatigueRisk 更负

routeGoal = LOCAL:
  W_goal.goalLocalMatch 更正
  W_interest.localFoodAffinity 更正
  W_risk.chainStorePenalty 更负
  W_goal.touristLandmarkPenalty 更负

budgetLevel = LOW:
  W_budget.budgetNorm 更负
  W_budget.freePoiMatch 更正
  W_risk.expensivePoiPenalty 更负

timeStructure = NIGHT:
  W_goal.goalNightMatch 更正
  W_transport.transitHigh 更正
  W_risk.closeRisk 更负
  W_risk.safetyRisk 更负
```

## 8. Neural Residual Scorer

Neural Residual Scorer 不是替代 Linear Ranker，而是学习 Linear 难以表达的非线性残差。

线上输入：

```text
input = concat(
  poiFeature,
  requestFeature,
  userPreferenceVector,
  environmentFeature
)
```

输出：

```text
neuralResidual = NeuralResidualScorer.predict(input)
finalScore = linearScore + neuralResidual
```

模型结构建议：

```text
input
  -> Linear(inputDim, 64)
  -> SiLU
  -> Dropout(0.1)
  -> Linear(64, 32)
  -> SiLU
  -> Linear(32, 1)
  -> tanh
  -> residualScale
```

其中：

```text
residualScale = 0.15
neuralResidual = tanh(output) * residualScale
```

`tanh * residualScale` 用来限制 Neural 对最终排序的影响，避免模型推翻硬约束和 Linear 的可解释基础分。

## 9. 成对偏好排序训练

当前训练方式统一命名为：

```text
中文名:
  成对偏好排序训练。

英文名:
  Pairwise Preference Ranking。

loss:
  Pairwise logistic ranking loss。
```

不再使用旧的类比命名，避免误解为严格复用大语言模型偏好优化的完整假设。这里使用路线偏好数据构造 chosen/rejected pair，再用 pairwise logistic loss 学习可比较的排序分数。

### 9.1 训练样本

一条点位 pair 样本包含：

```text
userPreferenceVector
requestFeature
environmentFeature
chosenPoiFeature
rejectedPoiFeature
chosenLinearScore
rejectedLinearScore
sampleWeight
sampleSource
feedbackType
```

其中 `chosenLinearScore` 和 `rejectedLinearScore` 是 Linear Ranker 计算出的固定基线分。首版训练时它们参与最终分数比较，但 Linear 权重本身不参与反向传播，也不被 Neural 训练过程改写。

> 口径对齐:这两个分必须是与**线上同口径的 clamped linearScore**,即 `clamp(Σ(M_final*X), -1, +1)`
> (见 Linear 文档《POI线性打分矩阵取值设计》§7/§8:含 Delta 叠加 + 安全 clamp)。训练样本若用未 clamp
> 的原始分,会与线上 `finalScore = clamp 后 linearScore + neuralResidual` 错位,导致训练目标和上线行为不一致。

训练目标不是让 `chosenResidual > rejectedResidual`，而是让最终线上使用的分数满足：

```text
chosenFinalScore > rejectedFinalScore
```

训练时两路输入共用同一个模型参数：

```text
chosenInput = concat(chosenPoiFeature, requestFeature, userPreferenceVector, environmentFeature)
rejectedInput = concat(rejectedPoiFeature, requestFeature, userPreferenceVector, environmentFeature)
```

Shared Tower：

```text
chosenResidual = NeuralResidualScorer(chosenInput; theta)
rejectedResidual = NeuralResidualScorer(rejectedInput; theta)
```

不能为 chosen 和 rejected 使用两套模型，否则线上面对单个 POI 输入时分数空间不可比。

### 9.2 路线级 pair 生成

pair 必须只在同一个 user、同一个 request、同一次候选路线批次内部构造，不能跨请求、跨用户或跨候选池比较。

如果本批次路线排序为：

```text
A > B > C > D > E
```

首版优先使用强差距 route pair：

```text
A > C
B > D
C > E
A > E
```

其中 `A > E` 必须保留。后续如需提高数据量，可以扩展为所有 `rankGap >= 2` 的 route pair。

路线差距权重：

```text
rankGap = rejectedRank - chosenRank
routeRankWeight = rankGap / (routeCount - 1)
```

5 条路线时：

```text
A > C: rankGap = 2, routeRankWeight = 0.5
B > D: rankGap = 2, routeRankWeight = 0.5
C > E: rankGap = 2, routeRankWeight = 0.5
A > E: rankGap = 4, routeRankWeight = 1.0
```

含义：

```text
排序差距越大，偏好越明确，loss 权重越高。
排序差距越小，越可能是用户或 LLM 的轻微抖动，loss 权重越低。
```

### 9.3 点位 pair 对齐

把 route pair 拆成 POI pair 时，只允许同位置对齐。

以 route pair `A > E` 为例：

```text
A.position_1 > E.position_1
A.position_2 > E.position_2
A.position_3 > E.position_3
A.position_4 > E.position_4
A.position_5 > E.position_5
A.position_6 > E.position_6
```

不允许跨位置任意配对：

```text
A.position_2 > E.position_4  不允许。
A.position_4 > E.position_1  不允许。
```

首版硬过滤规则：

```text
如果 chosen.position_i.type == rejected.position_i.type:
  保留这个 POI pair。

如果 type 不同:
  丢弃这个 POI pair。
```

不要因为某组 route pair 中可用位置少就整组丢弃。应该按位置筛选，能保留几条就保留几条。

为了控制单个 route pair 贡献的样本量，可以设置目标保留数量：

```text
routeLength = 6:
  最多保留 3 个高质量 POI pair。

routeLength = 5:
  最多保留 2 到 3 个高质量 POI pair，具体值后续按训练数据规模确认。

routeLength = 4:
  最多保留 2 个高质量 POI pair。
```

如果同 type 的位置超过目标数量，不随机丢弃，而是优先保留对齐质量更高的 pair。

### 9.4 alignmentContext 和 alignmentWeight

`alignmentContext` 只用于训练样本构造和权重计算，不进入线上预筛模型输入。

它表达“两个点位是否真的在相似位置上被比较”。首版可以参考：

```text
type
routeRole
prevPoiType
nextPoiType
prevRouteRole
nextRouteRole
arrivalTimeWindow
mealWindowMatch
restSlotMatch
```

建议先定义 `alignmentWeight`：

```text
type 不同:
  丢弃，不进入训练。

type 相同:
  alignmentWeight = 1.0

type 相同 + routeRole 相同:
  alignmentWeight = 1.2

type 相同 + routeRole 相同 + 上文类型相同:
  alignmentWeight = 1.35

type 相同 + routeRole 相同 + 上下文类型都相同:
  alignmentWeight = 1.5
```

不要求上下文完全一致才保留 pair。上下文一致性只影响 `alignmentWeight`。

### 9.5 sampleWeight

点位 pair 的最终训练权重：

```text
sampleWeight =
    routeRankWeight
  * alignmentWeight
  * negativeAlpha
  * feedbackConfidence
  * sourceWeight
```

含义：

```text
routeRankWeight:
  来自路线排序差距，例如 A > E 权重高于 A > C。

alignmentWeight:
  来自 POI type、routeRole、上下文一致性。

negativeAlpha:
  表示负反馈行为本身有多强，回答“这个行为有多负”。
  它只看反馈类型，不判断这个负反馈是不是能准确归因到当前 POI pair。

feedbackConfidence:
  表示当前训练样本的归因可信度，回答“这个反馈能不能放心归因到当前 POI pair”。
  它看的是 pair 是否可比、理由是否指向点位、多 LLM judge 是否一致、是否存在路线结构干扰。

sourceWeight:
  真实用户反馈高于 synthetic LLM 数据。
```

反馈强弱建议：

```text
EXPLICIT_DISLIKE:
  negativeAlpha = 1.0

REPLACED_POI:
  negativeAlpha = 0.8

SKIPPED_POI:
  negativeAlpha = 0.6

ABANDONED_ROUTE:
  negativeAlpha = 0.5

EXPOSED_NOT_SELECTED:
  negativeAlpha = 0.1 到 0.2

NOT_EXPOSED:
  negativeAlpha = 0
```

`feedbackConfidence` 的判断依据：

```text
高 confidence:
  chosen/rejected 的 POI type 相同。
  routeRole 相同。
  上下文相近。
  拒绝理由明确指向点位本身，例如太贵、质量低、重复、评分差、不是用户兴趣。
  多 LLM judge 对排序和理由一致。

中 confidence:
  type 相同，但 routeRole 或上下文不完全一致。
  拒绝理由既包含点位问题，也包含部分路线结构问题。
  LLM judge 大体一致，但理由有轻微分歧。

低 confidence:
  拒绝理由主要是路线结构问题，例如太绕、太赶、饭点错误、缺休息、交通衔接差。
  POI pair 虽然 type 相同，但上下文差异较大。
  LLM judge 排序或理由分歧明显。

0 confidence:
  没有曝光。
  type 不同，pair 不可比。
  反馈无法和当前 request 或 route batch 对齐。
```

二者的区别：

```text
negativeAlpha:
  衡量反馈力度。
  例如 dislike 比 exposed_not_selected 更强。

feedbackConfidence:
  衡量归因可信度。
  例如“路线太绕”不能强归因到某个 POI 差。
```

典型例子：

```text
用户明确 dislike 某个 POI:
  negativeAlpha 高。
  feedbackConfidence 通常也高，因为指向明确。

用户没有选择某条路线:
  negativeAlpha 低。
  feedbackConfidence 取决于拒绝理由和 POI pair 对齐质量。

用户拒绝理由是“这条路线太绕”:
  negativeAlpha 可以表示用户确实拒绝了这条路线。
  feedbackConfidence 要降低，因为问题可能来自路线结构，不一定来自当前 POI pair。

用户拒绝理由是“这家餐厅太贵”，且 chosen/rejected 都是午饭位置餐厅:
  negativeAlpha 较高。
  feedbackConfidence 较高，因为 pair 可比且理由指向点位本身。
```

如果拒绝理由明显是路线结构问题，点位 pair 不直接丢弃，但要降低 `feedbackConfidence`。路线结构问题后续进入独立路线评估设计。

### 9.6 Loss

训练分数必须和线上排序分数保持一致：

```text
chosenFinalScore = chosenLinearScore + chosenResidual
rejectedFinalScore = rejectedLinearScore + rejectedResidual
```

Pairwise logistic ranking loss：

```text
loss = sampleWeight * -log sigmoid(
  beta * (chosenFinalScore - rejectedFinalScore)
)
```

展开：

```text
loss = sampleWeight * -log sigmoid(
  beta * (
    (chosenLinearScore + NeuralResidualScorer(chosenInput; theta))
    -
    (rejectedLinearScore + NeuralResidualScorer(rejectedInput; theta))
  )
)
```

这里 LinearScore 参与 loss 计算，保证训练目标和线上 `finalScore = linearScore + neuralResidual` 一致。但首版只更新 Neural Residual Scorer 的参数 `theta`，不训练 Linear 矩阵权重。

推荐训练配置：

```text
loss: pairwise logistic ranking loss
activation: SiLU
optimizer: AdamW
learningRate: 1e-3
batchSize: 128 或 256
beta: 1.0
weightDecay: 1e-4
dropout: 0.1
residualScale: 0.15
```

## 10. LLM 合成数据

冷启动阶段可以用多家 LLM 模拟真实用户选择，生成结构化 preference data。

建议角色：

```text
Actor LLM:
  扮演指定用户画像，选择路线。

Judge LLM:
  检查选择是否符合用户画像和请求。

Critic LLM:
  指出路线问题，例如太远、太贵、缺饭点、同质化、绕路。

Aggregator:
  汇总多模型投票、理由和一致性，计算 confidence。
```

每条 synthetic sample 至少记录：

```text
sampleSource
llmProvider
llmModel
persona
request
environment
candidateRoutes
routeRanking
chosenReason
rejectedReason
consensus
confidence
每个 stop 的 poiFeature
每个可用 pair 的 alignmentContext
每个可用 pair 的 sampleWeight
```

合成数据权重：

```text
synthetic high-consensus:
  sampleWeight 低于真实强反馈，但可用于冷启动。

synthetic low-consensus:
  只作为弱样本或过滤。

real explicit feedback:
  权重最高。
```

## 11. 训练节奏

初期数据不足时，不启用 Neural Residual Scorer：

```text
neuralResidual = 0
finalScore = linearScore
```

数据量建议：

```text
几十条真实 pair:
  不训练 Neural，只更新用户画像。

几百条真实 pair:
  可以离线试训，但不建议强依赖。

几千条真实 pair:
  可以训练小 residual model，并灰度启用。

上万条 pair:
  可以周期性更新共享模型。
```

不建议每次用户反馈后立即重训模型。正确策略：

```text
用户行为发生后:
  写 route_feedback_event
  写 preference_pair_sample
  更新 user_preference_profile

模型训练:
  离线批量训练
  版本化评估
  灰度或手动切换
```

Java 后端负责线上推理和数据记录；训练任务放在 Python 侧，导出 ONNX 模型：

```text
poi-neural-residual-v1.onnx
```

Java 后端加载 ONNX，只执行：

```text
neuralResidual = NeuralResidualScorer.predict(input)
```

## 12. Diversity-aware Sampler

Sampler 不是打分器，而是集合选择器。它使用 `finalScore` 把候选池变成最终 POI 小池。

如果直接取 TopK，会导致：

- 每次结果固定。
- 同类 POI 过多。
- 空间过度集中。
- 餐饮/休息被挤掉。
- 后续路线缺少差异。

基本流程：

```text
1. 硬保留 mustVisit、必要 MEAL、必要 REST、核心 ANCHOR。
2. 对剩余 POI 使用 adjustedScore 计算 softmax 概率。
3. 根据 temperature 控制随机程度。
4. 抽样时持续检查 category、role、location、budget、timeWindow 多样性。
5. 抽满目标数量，例如 20/30/40 个。
```

概率：

```text
P(poi) = exp(adjustedScore / temperature) / sum(exp(adjustedScore / temperature))
```

动态调整：

```text
adjustedScore =
    finalScore
  - categoryDuplicatePenalty
  - geoClusterPenalty
  - roleOverflowPenalty
  - budgetOverflowPenalty
  - timeWindowConflictPenalty
```

需要继续确定：

- `temperature` 默认值和不同 `explorationLevel` 下的调整规则。
- `MEAL / REST / ANCHOR / BACKUP` 配额。
- 同类别、同空间网格、同品牌或同质 POI 的上限。
- `WALK_ONLY`、`WALK_SUBWAY`、`WALK_TAXI` 下的随机程度差异。

## 13. 分数解释

每个 POI 至少保留：

```text
interestScore
goalScore
qualityScore
transportScore
distanceCost
budgetCost
riskCost
personalizationScore
linearScore
neuralResidual
finalScore
```

需要继续确定：

- 哪些分数只用于后端调试。
- 哪些分数进入 POI 解释卡。
- 分数解释如何转换成自然语言原因。

## 14. 后端模块和持久化

候选模块：

```text
handler/route/pool/
  AdaptivePoiPoolSelector
  PoiHardConstraintGate
  PoiFeatureExtractor
  PoiLinearRanker
  PoiNeuralResidualScorer
  PoiDiversitySampler

domain/dto/route/preference/
  PoiFeatureVectorDTO
  RequestFeatureVectorDTO
  UserPreferenceVectorDTO
  EnvironmentFeatureDTO
  PoiRankScoreDTO
  PoiSelectionTraceDTO
  PoiPreferencePairDTO
  PoiAlignmentContextDTO
```

持久化建议：

```text
user_preference_profile
  用户长期偏好向量。

poi_selection_trace
  每次筛选时所有候选 POI 的 feature、linearScore、neuralResidual、finalScore、是否入选。

route_feedback_event
  用户开始路线、收藏、替换、完成、跳过、差评等行为。

preference_pair_sample
  从反馈事件或 synthetic 数据生成的 chosen/rejected pair。

preference_model_version
  Neural Residual 模型版本、训练数据窗口、上线状态。

synthetic_data_batch
  synthetic 数据批次、LLM provider、模型版本、persona 分布和生成时间。

llm_judge_result
  Actor / Judge / Critic / Aggregator 的选择、理由、一致性和置信度。
```

## 15. 验收标准

需要满足：

- 线上预筛模型输入不依赖路线生成后的前后文。
- 安卓端每个选项都能映射到后端 feature 或权重增量。
- 后端每个矩阵列都有明确来源和规约化方式。
- 每个权重行都有业务含义，不出现无法解释的权重。
- 交通组合能真实改变距离成本，而不是只作为展示字段。
- `LOW_BUDGET`、`LOCAL`、`PHOTO`、`NIGHT` 等目标能明显改变 POI 排序。
- 成对偏好排序训练只在同一批候选路线内构造 pair。
- 点位 pair 只做同位置对齐，type 不同则丢弃该点位 pair。
- 可用点位 pair 少时不整组丢弃，可用点位 pair 多时按 `alignmentWeight` 优先保留。
- Hard Constraint Gate、Linear、Neural Residual、Sampler 的职责边界清晰。
- Java 线上推理、Python 离线训练、ONNX 模型发布边界清晰。
