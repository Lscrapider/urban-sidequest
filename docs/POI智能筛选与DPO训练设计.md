# POI 智能筛选与 DPO-like 训练设计

本文单独记录 POI 候选池筛选算法的实现设计。它服务于路线生成链路中的 `SelectPoiPoolStep`，目标是把较大的真实 POI 候选池，例如 100 个 POI，筛成适合 LLM 编排路线的小池，例如 20/30/40 个 POI。

## 1. 总体目标

POI 筛选不是简单 TopK，也不是随机截断。它需要同时满足：

- 结果合理：不能把明显不可执行、过远、缺信息或同质化严重的 POI 塞给 LLM。
- 结果个性化：根据路线目标、兴趣偏好、交通组合、用户历史行为变化。
- 结果可变化：同一个请求多次生成时可以有差异，但差异要在合理范围内。
- 结果可学习：用户选择、替换、完成、跳过等行为会反向影响后续筛选。

推荐整体结构：

```text
POI candidates
  -> Hard Constraint Gate
  -> Feature Extractor
  -> Linear Ranker
  -> NeuralRanker residual
  -> Diversity-aware Sampler
  -> selected POI pool
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

## 2. Feature Extractor

每个 POI 会被展开成一个总特征向量 `X`。这个向量不是只描述 POI 自己，还要把当前请求和用户偏好纳入计算。

输入来源：

```text
poiFeature:
  POI 自身信息，如类别、角色、评分、图片、描述、交通可达性、距离、预算等。

requestFeature:
  当前请求信息，如路线目标、兴趣标签、出发时间、路线时长、交通组合、区域半径等。

userPreferenceVector:
  用户长期偏好，如兴趣权重、距离敏感度、预算敏感度、交通偏好、探索倾向等。
```

所有 feature 必须规约化，避免不同量纲直接参与计算。建议范围：

```text
布尔特征：0 或 1
比例特征：0 到 1
惩罚特征：0 到 1，最终由负权重表达扣分
少数溢出特征：允许到 1.5 或 2.0，但必须有上限
```

示例：

```text
ratingNorm = rating / 5
distanceNorm = min(distanceMeters / radiusMeters, 1.5)
budgetNorm = min(avgPriceCent / budgetCapCent, 2.0)
hasImage = imageUrls 非空 ? 1 : 0
transitHigh = transitAccessibility == HIGH ? 1 : 0
```

## 3. Linear Ranker

Linear Ranker 是第一套主评分逻辑。它使用特征向量和权重矩阵计算一个可解释的 `linearScore`。

每个 POI 被展开成：

```text
X = [
  interestMatchRatio,
  interestMatchCountNorm,
  userHistoricalTagAffinity,
  goalClassicMatch,
  goalLocalMatch,
  goalLowBudgetMatch,
  goalNightMatch,
  goalPhotoMatch,
  ratingNorm,
  hasImage,
  hasDescription,
  transitHigh,
  transitMedium,
  transitLow,
  distanceNorm,
  isolatedDistanceNorm,
  budgetNorm,
  isBackup,
  isMustVisit
]
```

权重矩阵 `M` 的每一行代表一个维度：

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
linearScore = sum(S)
```

注意：

- 不属于当前维度的列权重为 `0`，不是 `1`。
- 惩罚项也在矩阵里，只是对应权重为负数。
- `distanceCost`、`budgetCost`、`riskCost` 虽然名字是 cost，但本身已经是负数，可以直接加进 `linearScore`。

示意矩阵：

```text
X:
[
  interestMatchRatio,
  interestMatchCountNorm,
  goalLocalMatch,
  ratingNorm,
  hasImage,
  transitHigh,
  distanceNorm,
  budgetNorm,
  isBackup
]

M:
[
  [ 0.30,  0.12,  0.00,  0.00, 0.00, 0.00,  0.00,  0.00,  0.00 ],  # interestScore
  [ 0.00,  0.00,  0.35,  0.00, 0.00, 0.00,  0.00,  0.00,  0.00 ],  # goalScore
  [ 0.00,  0.00,  0.00,  0.12, 0.04, 0.00,  0.00,  0.00,  0.00 ],  # qualityScore
  [ 0.00,  0.00,  0.00,  0.00, 0.00, 0.12,  0.00,  0.00,  0.00 ],  # transportScore
  [ 0.00,  0.00,  0.00,  0.00, 0.00, 0.00, -0.25,  0.00,  0.00 ],  # distanceCost
  [ 0.00,  0.00,  0.00,  0.00, 0.00, 0.00,  0.00, -0.10,  0.00 ],  # budgetCost
  [ 0.00,  0.00,  0.00,  0.00, 0.00, 0.00,  0.00,  0.00, -0.18 ]   # riskCost
]
```

### 3.1 动态权重

权重矩阵不是完全固定的。交通组合会改变距离、交通和预算相关权重。

示例：

```text
WALK_ONLY:
  W_distance.distanceNorm = -0.45

WALK_SUBWAY:
  W_distance.distanceNorm = -0.22
  W_transport.transitHigh = 0.12

BIKE_SUBWAY:
  W_distance.distanceNorm = -0.18
  W_transport.transitHigh = 0.10

WALK_TAXI:
  W_distance.distanceNorm = -0.12
  W_budget.budgetNorm = -0.16
```

含义：

- 纯步行时，远距离惩罚更重。
- 有地铁时，距离惩罚降低，地铁可达性加分。
- 有骑行时，中距离更可接受。
- 有打车时，距离惩罚降低，但预算风险提高。

## 4. NeuralRanker Residual Model

NeuralRanker 是第二套评分逻辑，但它不是替代 Linear Ranker，而是补偿 Linear Ranker。

```text
neuralResidual = NeuralRanker.predict(poiFeature, requestFeature, userPreferenceVector)
finalScore = linearScore + neuralResidual
```

它学习的是非线性组合关系，例如：

```text
LOCAL 用户 + LOCAL 路线 + 本地餐饮 + 晚饭窗口 => 额外加分
WALK_ONLY + 远距离 + 孤立点 => 额外扣分
PHOTO 路线 + 有图 + 景点 + 高评分 => 额外加分
LOW_BUDGET + 高消费餐厅 => 额外扣分
NIGHT + 地铁近 + 夜景点 => 额外加分
```

### 4.1 模型输入

线上只使用一套共享模型，不做一用户一模型。个性化通过 `userPreferenceVector` 输入表达。

```text
input = concat(
  poiFeature,
  requestFeature,
  userPreferenceVector
)
```

训练时会有两路输入：

```text
chosenInput = concat(chosenPoiFeature, requestFeature, userPreferenceVector)
rejectedInput = concat(rejectedPoiFeature, requestFeature, userPreferenceVector)
```

两路输入中，`requestFeature` 和 `userPreferenceVector` 相同，只有 POI feature 不同。

### 4.2 模型结构

推荐小模型结构：

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
SiLU(x) = x * sigmoid(x)
residualScale = 0.15
neuralResidual = tanh(output) * residualScale
```

选择 `SiLU` 是因为它比 ReLU 更平滑，适合结构化特征上的排序残差模型。`tanh * residualScale` 用来限制模型影响范围，避免 Neural 推翻 Linear 和硬约束。

## 5. DPO-like 训练

DPO-like 不是单独一套模型，而是 NeuralRanker 的训练方式。它不学习“某个 POI 的绝对正确分数”，而是学习：

```text
chosen 应该排在 rejected 前面
```

### 5.1 训练样本

一条训练样本包含：

```text
userPreferenceVector
requestFeature
chosenPoiFeature
rejectedPoiFeature
chosenLinearScore
rejectedLinearScore
feedbackType
feedbackWeight
```

其中：

- `chosenPoiFeature`：用户选择、保留、完成或收藏的 POI。
- `rejectedPoiFeature`：用户未选择、替换掉、跳过或导致负反馈的 POI。
- `feedbackWeight`：反馈强度。

反馈强度建议：

```text
COMPLETE_ROUTE: 1.0
REPLACE_POI: 0.9
START_ROUTE: 0.7
FAVORITE_ROUTE: 0.7
SKIP_POI: 0.6
EXPOSED_NOT_SELECTED: 0.2
```

### 5.2 Pair 生成

用户选择路线 A、未选择路线 B/C：

```text
A 的 ANCHOR > B/C 的 ANCHOR
A 的 MEAL > B/C 的 MEAL
A 的 REST > B/C 的 REST
A 的 LOCAL > B/C 的 LOCAL
```

用户替换 POI：

```text
替换后的 POI > 被替换掉的 POI
```

用户完成路线或打卡：

```text
完成 POI > 同次曝光但未使用 POI
```

用户跳过、差评、提前结束：

```text
同批次更符合后续选择的 POI > 被跳过或导致负反馈的 POI
```

POI 筛选阶段优先使用 POI 级 pair。路线级反馈可以拆成同角色 POI pair，避免一开始就训练复杂 route-level model。

### 5.3 Shared Tower

训练时不需要两套神经网络。chosen 和 rejected 共用同一个 NeuralRanker 参数 `θ`：

```text
chosenResidual = NeuralRanker(chosenInput; θ)
rejectedResidual = NeuralRanker(rejectedInput; θ)
```

如果使用两套模型，线上面对一个普通 POI 时无法判断该使用 chosen 模型还是 rejected 模型，而且两个分数空间不可比。共享参数保证任意 POI 输入都能输出同一空间下可比较的 residual。

### 5.4 Loss

训练分数：

```text
chosenScore = chosenLinearScore + chosenResidual
rejectedScore = rejectedLinearScore + rejectedResidual
```

DPO-like pairwise logistic loss：

```text
loss = feedbackWeight * -log sigmoid(
  beta * (chosenScore - rejectedScore)
)
```

展开：

```text
loss = feedbackWeight * -log sigmoid(
  beta * (
    (chosenLinearScore + NeuralRanker(chosenInput; θ))
    -
    (rejectedLinearScore + NeuralRanker(rejectedInput; θ))
  )
)
```

训练会从 chosen 和 rejected 两路同时反向传播，更新同一套参数 `θ`。如果 chosenScore 没有高于 rejectedScore，loss 会增大，模型会被推动去提高 chosenResidual 或降低 rejectedResidual。

推荐训练配置：

```text
loss: DPO-like pairwise logistic loss
activation: SiLU
optimizer: AdamW
learningRate: 1e-3
batchSize: 128 或 256
beta: 1.0
weightDecay: 1e-4
dropout: 0.1
residualScale: 0.15
```

## 6. 数据冷启动和训练节奏

初期数据不足时，不启用 NeuralRanker：

```text
neuralResidual = 0
finalScore = linearScore
```

早期只做两件事：

- 使用人工设定的 Linear 权重矩阵。
- 在线更新 `userPreferenceVector`，例如兴趣偏好、距离容忍度、预算敏感度、交通偏好。

数据量建议：

```text
几十条 pair:
  不训练 Neural，只更新用户画像。

几百条 pair:
  可以离线试训，但不建议强依赖。

几千条 pair:
  可以训练小 residual model，并灰度启用。

上万条 pair:
  可以周期性更新共享 NeuralRanker。
```

一次路线反馈可以拆出多条 pair，例如 A/B/C 三条路线每条 5 个 POI，用户选择 A 后可能产生 5 到 15 条 pair。

不建议用户每次选择后立刻重训 NeuralRanker。正确策略：

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

Java 后端负责线上推理和数据记录；训练任务建议放在 Python 侧，导出 ONNX 模型：

```text
neural-ranker-v1.onnx
```

Java 后端加载 ONNX，只执行：

```text
neuralResidual = NeuralRanker.predict(input)
```

## 7. Diversity-aware Sampler

Sampler 不是打分器，而是集合选择器。它使用 `finalScore` 把候选池变成最终 POI 池。

如果直接取 TopK，会导致：

- 每次结果固定。
- 同类 POI 过多。
- 空间过度集中。
- 餐饮/休息被挤掉。
- A/B/C 路线缺少差异。

Sampler 的目标是：

```text
高分优先
结果可变化
集合结构均衡
路线可编排
```

### 7.1 基本流程

```text
1. 硬保留 mustVisit、必要 MEAL、必要 REST、核心 ANCHOR。
2. 对剩余 POI 使用 finalScore 计算 softmax 概率。
3. 根据 temperature 控制随机程度。
4. 抽样时持续检查 category、role、location 多样性。
5. 抽满目标数量，例如 40 个。
```

概率：

```text
P(poi) = exp(finalScore / temperature) / sum(exp(finalScore / temperature))
```

`temperature`：

```text
低 temperature:
  更接近 TopK，更稳定。

高 temperature:
  低分 POI 也有机会进入，更有探索感。
```

### 7.2 多样性约束

每抽一个 POI 前，都要检查它是否破坏最终集合结构：

```text
同一 category 不能超过上限。
同一 role 不能超过上限。
BACKUP 数量受控。
同一空间网格不能过度集中。
太靠近已选 POI 的候选会降权。
MEAL / REST / ANCHOR 配额不能被破坏。
```

动态调整分数：

```text
adjustedScore =
    finalScore
  - categoryDuplicatePenalty
  - geoClusterPenalty
  - roleOverflowPenalty
```

然后再进入 softmax 抽样。

Sampler 解决的是：

```text
Ranker: 单个 POI 有多好
Sampler: 这一批 POI 放在一起是否均衡、有变化、能编路线
```

## 8. 建议后端模块

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
  PoiRankScoreDTO
  PoiSelectionTraceDTO
  PoiPreferencePairDTO
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
  从反馈事件生成的 chosen/rejected pair。

preference_model_version
  NeuralRanker 模型版本、训练数据窗口、上线状态。
```

