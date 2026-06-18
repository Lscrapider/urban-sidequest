# POI 线性打分矩阵取值设计

本文专门记录 POI 预筛阶段 Linear Ranker 的特征取值、矩阵行含义、权重初始化和分值尺度设计。

本文只讨论 Linear Ranker，不讨论成对偏好排序训练、Neural residual、路线生成后的结构评分。

## 1. 目标和边界

Linear Ranker 的目标是提供一个可解释、可控、冷启动可用的基础分：

```text
linearScore = sum(M * X)
```

其中：

```text
X:
  由四组输入特征拼接而成。

M:
  线性权重矩阵。

M * X:
  输出多个可解释子分数。
```

首版约束：

```text
Linear 是主判断。
Neural residual 只做补偿。
Linear 权重首版人工配置，不参与训练。
Linear 分值尺度必须和 neuralResidual 尺度匹配。
```

## 2. 四组输入特征

四组输入特征是 `X` 的来源分组，不直接等于四个分数。

```text
X = concat(
  poiFeature,
  requestFeature,
  userPreferenceVector,
  environmentFeature
)
```

### 2.1 poiFeature

描述 POI 自身是什么。字段已对齐真实数据源:线上 POI 来自高德 `/v5/place` 系列搜索，
扩展字段 `show_fields = business,photos`，再经 `AmapTransitPoiDetailProvider` 补最近交通设施。
因此字段表只收录"高德链路真实拿得到或可本地派生"的特征，拿不到的一律不进 v1。

数据可得性分四档:

```text
档 0:已在 PoiCandidateDTO 里，直接用。
档 1:已在高德响应里(business/photos/type)，但当前只进了 description 字符串，
     需要补 parse 才能结构化拿到，零额外调用。
档 2:候选池内本地派生或由档 0/1 字段合成，无额外调用。
档 3:当前高德链路拿不到，v1 砍掉(排队、人流、门票、评论数等)。
```

> 前置依赖:档 1 字段(typecode、opentime、tel)落地时需要给 `PoiCandidateDTO`
> 补 `typecode`、`opentime`、`tel` 三个字段。属于 v1 实现前的 schema 改动，本设计阶段不动代码。

字段表(取值范围 / 默认值 / missing indicator 在第 3、6 节按 step 2 细化):

```text
字段名 | 含义 | 类型 | 数据档 | 来源/合成 | 归属行
```

#### W_quality(信息质量与可信度)

```text
ratingNorm          | 高德评分规约           | ratio        | 0 | business.rating / 5
isRatingMissing     | 评分缺失指示           | bool(missing)| 0 | rating == null
hasImage            | 是否有图               | bool         | 0 | photos 非空
hasDescription      | 是否有描述             | bool         | 0 | description 非空
isDescriptionMissing| 描述缺失指示           | bool(missing)| 0 | description 为空
addressCompleteness | 地址完整度             | ratio        | 0 | address
brandTrust          | 商家可信度(信息完整度合成)| ratio       | 2 | 加权(hasTel, ratingExists, opentimeExists, businessTagExists)
```

#### W_budget(预算压力)

```text
avgPriceNorm   | 人均规约       | overflow     | 0 | business.cost
isPriceMissing | 人均缺失指示   | bool(missing)| 0 | cost == null
isFree         | 是否免费       | bool         | 2 | cost == 0(注意 null != free)
expensivePoiRisk| 高消费风险    | ratio        | 2 | 由 avgPriceNorm 派生
```

#### W_distance(距离 / 绕行 / 体力)

```text
distanceNorm        | 到区域中心距离规约 | overflow | 2 | location vs 区域中心 / radius
isolatedDistanceNorm| 孤立远点程度       | overflow | 2 | 候选池派生(到其他候选最近距离)
walkingSegmentRisk  | 步行段体力风险     | ratio    | 2 | 距离 + 体力派生
clusterConnectivity | 与候选簇可连接性   | ratio    | 2 | 候选池派生(邻域候选数)
```

#### W_transport(当前交通组合下可达性)

```text
transitHigh                | 交通可达高档(<=300m)  | bool(one-hot) | 0 | transitAccessibility 展开
transitMedium              | 交通可达中档(<=800m)  | bool(one-hot) | 0 | transitAccessibility 展开
transitLow                 | 交通可达低档(>800m)   | bool(one-hot) | 0 | transitAccessibility 展开
nearestTransitDistanceNorm | 最近地铁/公交距离规约 | overflow      | 0 | nearestTransit[0].distanceMeters
walkingAccessibility       | 步行可达性            | ratio         | 0 | 由 distance + transit 派生
```

#### W_risk(风险)

```text
closeRisk           | 闭店风险(临近营业结束) | ratio | 1 | business.opentime
weatherSensitive    | 天气敏感(室外类)       | ratio | 2 | typecode 规则
categoryDuplicateRisk| 同质化风险            | ratio | 2 | typecode 候选池计数
missingInfoRisk     | 信息缺失综合风险       | ratio | 2 | 缺图 + 缺描述 + 缺评分聚合
```

#### cross 原料(POI 侧 raw，不直接进任何 W 行)

这些是 POI 自身的 raw 属性，本身不被矩阵打分，只作为第五块 `derivedFeature`
做 POI ⊗ 请求 / POI ⊗ 画像 交叉时的原料(derivedFeature 在后续单独定义)。

```text
poiCategory  | POI 分类(稳定 ordinal)| ordinal | 1 | typecode
candidateRole| 候选角色               | enum    | 0 | role(MUST_VISIT/ANCHOR/MEAL/REST/LOCAL/BACKUP)
isMustVisit  | 是否必去点             | bool    | 0 | mustVisit
isClassic    | 是否经典               | bool    | 1 | typecode + 特色标签映射表
isLocal      | 是否本地               | bool    | 1 | typecode + 特色标签映射表
isPhotoFriendly| 是否适合拍照         | bool    | 1 | typecode + 特色标签映射表
isNightFriendly| 是否适合夜游         | bool    | 1 | typecode + 特色标签映射表
isQuiet      | 是否安静/适合休息       | bool    | 1 | typecode + 特色标签映射表
isHiddenGem  | 是否小众               | bool    | 1 | typecode + 特色标签映射表
```

> 语义标签 ×6 与 weatherSensitive 都依赖一张"高德 typecode/特色标签 -> 语义"映射表，
> 该映射表按大类组(非逐码)维护，待四组 raw 全部定义完成后作为附录单独起草。

被剔除的字段(档 3，v1 不收录):

```text
queueRisk / crowdRisk | 新版 POI search 无排队、人流数据
ticketPriceNorm       | 仅有人均 cost，无门票字段
reviewCountNorm       | 新版 search 的 business 不含评论数
brandTrust(原义)      | 无品牌权威分，改用档 2 信息完整度合成近似
```

### 2.2 requestFeature

描述这次用户想要什么路线。

待定字段表：

```text
字段名 | 含义 | 类型 | 取值范围 | 默认值 | 来源 | 备注
```

至少覆盖：

```text
routeGoal、interestTags、transportProfile、paceLevel、budgetLevel、
出发时间、路线时长、区域约束、起点、终点、必去点。
```

### 2.3 userPreferenceVector

描述用户长期偏好。

待定字段表：

```text
字段名 | 含义 | 类型 | 取值范围 | 默认值 | 画像置信度 | 来源 | 备注
```

至少覆盖：

```text
兴趣长期权重、距离敏感度、预算敏感度、换乘敏感度、拥挤敏感度、
探索倾向、收藏/完成/跳过/替换/dislike 聚合、新用户标记。
```

### 2.4 environmentFeature

描述本次请求的外部环境。

待定字段表：

```text
字段名 | 含义 | 类型 | 取值范围 | 默认值 | 来源 | 备注
```

至少覆盖：

```text
当前时间段、是否临近饭点、是否夜间、天气、温度、降雨、节假日、周末、
热门区域拥挤风险。
```

## 3. 特征规约化规则

所有进入 `X` 的特征必须先规约化。

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
  使用 one-hot、multi-hot 或稳定 ordinal 编码。

缺失特征:
  必须有默认值，并额外提供 missing indicator。
```

示例：

```text
ratingNorm = rating / 5
distanceNorm = min(distanceMeters / radiusMeters, 1.5)
budgetNorm = min(avgPriceCent / budgetCapCent, 2.0)
hasImage = imageUrls 非空 ? 1 : 0
isRatingMissing = rating 为空 ? 1 : 0
```

## 4. 矩阵输出行

Linear 矩阵不是输出四个输入组分数，而是输出可解释评分维度。

首版输出 8 行：

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

对应矩阵：

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

计算：

```text
S = M * X

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

```text
不属于某一行的 feature 列权重为 0。
distanceCost、budgetCost、riskCost 通常是负贡献，但仍直接加进 linearScore。
```

## 5. 八个评分行职责

### 5.1 W_interest

衡量当前 POI 是否命中用户兴趣，以及是否符合用户长期兴趣画像。

候选使用字段：

```text
interestTags
interestMatchRatio
interestMatchCountNorm
poiCategory
poiSubCategory
userHistoricalTagAffinity
```

### 5.2 W_goal

衡量当前 POI 对本次路线目标的贡献。

候选使用字段：

```text
routeGoal
goalClassicMatch
goalLocalMatch
goalPhotoMatch
goalNightMatch
goalLowBudgetMatch
goalRelaxedMatch
```

### 5.3 W_quality

衡量 POI 自身信息质量和可信度。

候选使用字段：

```text
ratingNorm
hasImage
hasDescription
addressCompleteness
brandTrust
isRatingMissing
isDescriptionMissing
```

### 5.4 W_transport

衡量 POI 在当前交通组合下是否可达。

候选使用字段：

```text
transportProfile
transitHigh
transitMedium
transitLow
nearestTransitDistanceNorm
walkingAccessibility
```

### 5.5 W_distance

衡量距离、绕行、孤立点和体力成本。

候选使用字段：

```text
distanceNorm
isolatedDistanceNorm
walkingSegmentRisk
clusterConnectivity
```

### 5.6 W_budget

衡量当前 POI 对预算的压力。

候选使用字段：

```text
budgetLevel
budgetNorm
isFree
ticketPriceNorm
avgPriceNorm
expensivePoiRisk
```

### 5.7 W_risk

衡量闭店、排队、拥挤、天气、同质化、缺信息等风险。

候选使用字段：

```text
closeRisk
queueRisk
crowdRisk
weatherSensitive
categoryDuplicateRisk
missingInfoRisk
```

### 5.8 W_personalization

衡量用户长期画像和短期行为对当前 POI 的个性化偏置。

候选使用字段：

```text
userCategoryAffinity
userDistanceTolerance
userBudgetTolerance
userCrowdTolerance
userDislikeAffinity
profileConfidence
isNewUser
```

## 6. M_base 初始权重原则

首版先定义 `M_base`，再叠加动态增量。

原则：

```text
强业务确定性:
  可以给更明确的正负权重。

弱业务确定性:
  权重保守，避免过拟合人工直觉。

缺失或低置信字段:
  不直接当作低质量，必须通过 missing indicator 表达。

成本和风险:
  用负权重表达，不额外改变求和方式。
```

待定权重表：

```text
矩阵行 | feature | baseWeight | 业务解释 | 是否允许动态增量 | 备注
```

## 7. 动态增量

最终矩阵：

```text
M_final =
  M_base
  + Delta_goal(routeGoal)
  + Delta_transport(transportProfile)
  + Delta_pace(paceLevel)
  + Delta_budget(budgetLevel)
  + Delta_time(environmentFeature)
  + Delta_userProfile(userPreferenceVector)
```

动态增量只改变已有行列权重，不新增含义不清的临时参数。

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
```

## 8. 分值尺度

首版建议控制：

```text
linearScore:
  大致落在 -1.0 到 1.0。

常规优质 POI:
  大致落在 0.3 到 0.7。

明显不合适 POI:
  大致落在 -0.5 以下。

强兴趣命中或 mustVisit:
  可以接近 1.0。
```

和 Neural residual 的尺度关系：

```text
neuralResidual = tanh(output) * residualScale

residualScale:
  首版建议 0.15。
```

含义：

```text
Linear 决定主排序。
Neural residual 只补偿 Linear 的非线性不足。
如果 linearScore 尺度扩大，residualScale 必须同步复查。
```

## 9. sanity check 样例

后续每次调整权重后，至少检查以下样例：

```text
LOCAL + 本地餐饮 + 低距离 + 信息完整:
  应明显高于连锁商场普通餐饮。

WALK_ONLY + 远距离孤立点:
  即使 POI 质量高，也应被明显扣分。

PHOTO + 高评分景点 + 有图片:
  应明显高于无图低质量点。

LOW_BUDGET + 高消费餐厅:
  应被预算行和风险行共同压低。

NIGHT + 夜游适配 + 交通可达:
  应高于闭店风险高的普通点。
```

每个样例需要记录：

```text
输入特征
8 个子分数
linearScore
是否符合预期
需要调整的行列权重
```

## 10. 后续待定

下一步需要逐项确认：

```text
1. poiFeature 字段表。
2. requestFeature 字段表。
3. userPreferenceVector 字段表。
4. environmentFeature 字段表。
5. M_base 初始权重。
6. 动态增量规则。
7. linearScore 分值边界。
8. sanity check 样例集。
```
