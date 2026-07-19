# 路线 X 特征定义（route_pref_v5）

本文只回答一件事：**喂给路线偏好模型的 X 里到底有哪些值、每个值是什么、怎么算、范围多大**。

这是一份「跟着代码走」的字典，不是设计推演。事实来源是后端 `RouteInputFeatureExtractor` 和 `RoutePreferenceFeatureSchema.VERSION`（当前 `route_pref_v5`），与导出的 `feature_schema.json` 字段一一对应。代码改了、版本 bump 了，这份要同步。

> 模型为什么要这些特征、软硬裁判怎么用它们，见《路线裁判与软拒绝设计》；训练怎么用 X/Y，见《路线偏好排序模型训练设计》。本文不重复那些。

## 0. 先说清楚边界

**X = 五块：**

```text
X = stopMatrix + segmentMatrix + routeDerivedVector + contextCrossVector + intraSetVector
```

- 前四块是逐路线可算的输入；第五块 `intraSetVector` 是同一 candidate set 内组级相对输入，见 §8。
- §1–§6 描述前四块；§8 描述 v5 新增的维度分和第五块。
- raw snapshot（见 §6）和 judgment 里的 `ranking / accepted / rejected / reasonCodes / confidence` **都不进 X**——前者只做审计和补 k，后者是训练标签 Y。
- 当前 feature schema version 固定存为 `route_pref_v5`；Java extractor 与 Python 训练侧均按该完整版本匹配，当前未实现追加 LLM 模型后缀或按 `@` 归并。

**矩阵 padding：** `stopMatrix` 最多 8 行（`MAX_STOPS`），`segmentMatrix` 最多 7 行（`MAX_SEGMENTS = MAX_STOPS - 1`），不足补零行。空位靠行内 `isLastStop`、`segmentEstimateMissing` 等标志识别。

**怎么读「范围/归一化」列：**

| 标记 | 含义 |
| --- | --- |
| `bit` | 取 0 或 1 |
| `[0,1]` | 已 clamp 到 0–1 |
| `≥0 比值` | 归一化比值，正常 0–1，超舒适阈可 >1 |
| `clamp` | 截断到固定上下界（界值在配置常量里，不写死本文） |
| `原始分·无界` | 未归一化的原始线性子分；同 scorer 版本内输出一致，量级随命中项数变化（属正常信号，非问题） |

---

## 1. stopMatrix —— 每个 stop 一行（≤8 行）

一行描述「路线里的一个点本身怎么样、它在这条路线里扮演什么角色」。按来源分 7 组。

### 1.1 POI 语义 bit（来自 POI 语义画像）

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `isClassic` | 是否经典/必看类 | bit |
| `isLocal` | 是否本地生活类 | bit |
| `isPhotoFriendly` | 是否出片 | bit |
| `isNightFriendly` | 是否适合夜间 | bit |
| `isQuiet` | 是否安静 | bit |
| `isHiddenGem` | 是否小众宝藏 | bit |

### 1.2 质量

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `ratingNorm` | 评分归一（评分/满分），缺失给中性默认 | [0,1] |
| `hasImage` | 是否有图 | bit |
| `isRatingMissing` | 评分是否缺失 | bit |

### 1.3 价格

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `avgPriceNorm` | 人均价格归一（价格/预算上限，封顶），缺失给默认 | clamp |
| `isPriceMissing` | 价格是否缺失 | bit |
| `isFree` | 是否免费 | bit |
| `expensivePoiRisk` | 偏贵风险（价格超偏置后部分） | [0,1] |

### 1.4 风险

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `closeRisk` | 有风险备注时给常量，否则 0 | 0 或常量 |
| `missingInfoRisk` | 候选缺失或描述为空 | bit |

### 1.5 公交可达（来自最近公交设施）

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `transitHigh` | 最近公交很近 | bit |
| `transitMedium` | 中等 | bit |
| `transitLow` | 较远/无设施 | bit |
| `nearestTransitDistanceNorm` | 最近公交距离归一（封顶） | clamp |

### 1.6 POI 线性子分（来自 PoiLinearScorer trace，未归一化）

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `interestScore` | 兴趣匹配线性分量（1 项加权） | 原始分·无界 |
| `goalScore` | 目标契合线性分量（多项加权和） | 原始分·无界 |
| `qualityScore` | 质量线性分量 | 原始分·无界 |
| `transportScore` | 交通线性分量 | 原始分·无界 |
| `distanceCost` | 距离成本分量 | 原始分·无界 |
| `budgetCost` | 预算成本分量 | 原始分·无界 |
| `riskCost` | 风险成本分量 | 原始分·无界 |
| `poiLinearTraceMissing` | 该 stop 没有线性 trace（上面子分按 0 计） | bit |

> 说明：这 7 个分量是线性器「M·X 后、求和成总分前」的子分，未 clamp，量级不在 [0,1]——`goalScore`（多项和）通常比 `interestScore`（单项）大。但**同一个 LinearScorer 版本下输出确定一致**，大小差异是「该点命中得多/少」的有效信号、不是噪声；encoder 的 LayerNorm + AdamW 也能吸收静态尺度，**不需要为它归一化**。唯一要留意的是：若 POI 线性权重改过且没全量 rebuild，老/新样本会混版尺度——这靠 `RoutePreferenceFeatureRebuildService` 全量重建解决，是数据卫生、不是字段问题。另外此处 `qualityScore` 是 **stop 级线性分**，与 LLM 对整条路线打的 `qualityScore`（**不进 X**）同名但无关。

### 1.7 路线角色与位置

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `routeRole_MUST_VISIT` / `_ANCHOR` / `_MEAL` / `_REST` / `_LOCAL` / `_PHOTO` / `_BACKUP` | 该 stop 在路线里的角色（优先用 LLM 给的 routeRole，否则按语义/位置推断） | bit |
| `stayMinutesNorm` | 停留分钟 / 60（即小时数，未 clamp） | ≥0 |
| `orderPositionNorm` | 位置 index/(n-1) | [0,1] |
| `isFirstStop` / `isLastStop` | 首/末站 | bit |

---

## 2. segmentMatrix —— 相邻两 stop 之间一行（≤7 行）

一行描述「这两点之间走起来顺不顺、累不累、用什么方式」。优先用校准后真实段（高德），没有则用直线距离+方式估算。

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `straightDistanceNorm` | 段距离/单段舒适距离；缺失填 1 | ≥0 比值 |
| `estimatedDurationNorm` | 段耗时/单段舒适耗时；缺失填 1 | ≥0 比值 |
| `transportMode_WALK` / `_BIKE` / `_BUS` / `_SUBWAY` / `_TRANSIT` / `_TAXI` / `_DRIVE` | 单段交通方式 one-hot（7 种） | bit |
| `isBacktracking` | 是否明显折返（看与上一段方向、是否回到已访问点附近） | bit |
| `distancePressure` | 距离压力 = clamp(段距离/舒适距离) | clamp |
| `segmentCalibrated` | 是否高德校准段 | bit |
| `segmentAmapFallback` | 是否高德降级段 | bit |
| `segmentStraightLineFallback` | 是否直线兜底段 | bit |
| `segmentEstimateMissing` | 坐标缺失无法估算（距离/时长归一字段填 1、压力填 1） | bit |

> 说明：原始距离米数、耗时分钟只做中间量，**不进** segmentMatrix——原始值尺度大、缺失填 0 易造噪声，所以只保留 norm/pressure/mode/missing。（stop 的线性子分按 §1.6 说明保留原值，同 scorer 版本内一致，不需要同样处理。）

---

## 3. routeDerivedVector —— 整条路线一行（聚合统计）

从 stopMatrix / segmentMatrix / 请求参数聚合出来，回答「整条路线整体什么结构」。按主题分组。

### 3.1 时间结构

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `stopCountNorm` | stop 数 / 最大 stop 数 | [0,1] |
| `stayBudgetUsageRatio` | 停留总时长 / (时长×停留预算比) | ≥0 比值 |
| `estimatedTravelMinutesNorm` | 估算通行时长 / 用户时长 | ≥0 比值 |
| `timeBudgetUsageRatio` | (停留+通行) / 用户时长 | ≥0 比值 |
| `timeBudgetUnderuse` | max(0, 目标用时比 − 实际) 偏空 | ≥0 |
| `timeBudgetOveruse` | max(0, 实际 − 舒适上限比) 偏满 | ≥0 |

### 3.2 饭点与角色覆盖

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `requiresLunchFlag` / `requiresDinnerFlag` | 时间窗是否要求午/晚餐 | bit |
| `mealStopCountNorm` / `restStopCountNorm` | 餐/休息点数 / 最大 stop 数 | [0,1] |
| `lunchCoveredFlag` / `dinnerCoveredFlag` | 是否覆盖午/晚餐 | bit |
| `missingRequiredMealFlag` | 该吃却没排 | bit |

### 3.3 距离与空间流

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `totalDistanceNorm` | 总距离 / 路线舒适距离 | ≥0 比值 |
| `maxSegmentDistanceNorm` | 最长段归一 | ≥0 比值 |
| `avgSegmentDistanceNorm` | 平均段归一 | ≥0 比值 |
| `longSegmentRatio` | 长段（压力超阈）占比 | [0,1] |
| `backtrackingSegmentRatio` | 折返段占比 | [0,1] |
| `transferSegmentRatio` | 公交/地铁段占比 | [0,1] |
| `transferDistancePressure` | 公交/地铁段距离压力均摊 | ≥0 |
| `fallbackAmapRatio` | 高德降级段占比 | [0,1] |
| `straightLineFallbackRatio` | 直线兜底段占比 | [0,1] |
| `missingSegmentRatio` | 缺失段占比 | [0,1] |

### 3.4 交通桶占比与强度（步行/骑行=physical，公交/地铁=scheduled，打车/驾车=private_motor）

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `physicalTravelDistanceRatio` / `scheduledTravelDistanceRatio` / `privateMotorTravelDistanceRatio` | 三类交通的距离占比 | [0,1] |
| `physicalTravelDistanceNorm` / `scheduledTravelDistanceNorm` / `privateMotorTravelDistanceNorm` | 三类距离相对各自参考的归一 | clamp |
| `physicalTravelMaxSegmentDurationNorm` / `scheduledTravelMaxSegmentDurationNorm` / `privateMotorTravelMaxSegmentDurationNorm` | 三类最长单段耗时归一 | clamp |
| `travelBucketSwitchCountNorm` | 交通桶切换次数归一 | clamp |

### 3.5 兴趣与语义覆盖

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `interestCoverageRatio` | 命中的请求兴趣标签 / 请求兴趣标签数（无兴趣标签则 0） | [0,1] |
| `localStopRatio` / `classicStopRatio` / `photoFriendlyStopRatio` / `nightFriendlyStopRatio` / `quietStopRatio` / `hiddenGemStopRatio` | 各语义 stop 占比（对应 §1.1 bit 的均值） | [0,1] |

### 3.6 多样性（类目 + 高德 typecode）

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `categoryDiversityRatio` | 不同类目数 / stop 数 | [0,1] |
| `dominantCategoryRatio` | 最多类目占比 | [0,1] |
| `consecutiveSameCategoryMaxNorm` | 最长连续同类目 / stop 数 | [0,1] |
| `amapTypecodeDiversityRatio` | 不同 typecode 数 / stop 数 | [0,1] |
| `dominantAmapTypecodeRatio` | 最多 typecode 占比 | [0,1] |
| `consecutiveSameAmapTypecodeMaxNorm` | 最长连续同 typecode / stop 数 | [0,1] |
| `missingAmapTypecodeRatio` | 缺 typecode 占比 | [0,1] |

### 3.7 预算、线性聚合、风险

| 字段 | 含义 | 范围 |
| --- | --- | --- |
| `budgetTotalNorm` | 消费类 stop 总价 / 预算上限 | ≥0 比值 |
| `budgetPressure` | clamp(budgetTotalNorm − 偏置) | clamp |
| `missingPriceRatio` | 消费类 stop 缺价占比 | [0,1] |
| `avgInterestScore` | §1.6 `interestScore` 的均值 | 原始分·无界 |
| `avgGoalScore` | §1.6 `goalScore` 的均值 | 原始分·无界 |
| `avgQualityScore` | §1.6 `qualityScore` 的均值 | 原始分·无界 |
| `avgRiskCost` | §1.6 `riskCost` 的均值 | 原始分·无界 |
| `highRiskStopRatio` | riskCost 低于阈值的 stop 占比 | [0,1] |

> 这 4 个 `avg*` 是 §1.6 原始线性子分的均值，同样「同 scorer 版本内一致」，处理口径见 §1.6 说明（无需归一）。

---

## 4. contextCrossVector —— 用户/请求/环境 × 路线（一行交叉）

回答「这条路线对这个用户、这次请求来说怎么样」。不塞原始画像/请求对象，只放固定维度的交叉项。画像类一般乘了 `profileConfidence` 收缩。

### 4.1 画像 × 路线压力

| 字段 | 含义 |
| --- | --- |
| `profileDistanceTotalPressure` | 置信×距离敏感×总距离归一 |
| `profileDistanceMaxSegmentPressure` | 置信×距离敏感×最长段归一 |
| `profileTransferPressure` | 置信×换乘敏感×换乘距离压力 |
| `profileBudgetPressure` | 置信×预算敏感×预算压力 |
| `profileHiddenGemMatch` | 置信×小众偏好×小众 stop 占比 |

### 4.2 画像标签匹配（均 ×置信 后 clamp 到 [0,1]）

| 字段 | 含义 |
| --- | --- |
| `profileTagAffinityCoverage` | 路线标签覆盖了多少画像偏好权重 |
| `profileTagAffinityPrecision` | 路线标签里命中画像的精度 |
| `profileTagAffinityJaccard` | 画像权重 vs 路线权重的 Jaccard |
| `profileTopTagHitRatio` | 画像 topK 标签被路线命中的比例 |

### 4.3 路线目标 × 语义覆盖（目标命中才非零）

| 字段 | 含义 |
| --- | --- |
| `goalLocalMatch` / `goalClassicMatch` / `goalQuietMatch` / `goalPhotoMatch` / `goalNightMatch` | 目标=对应类时取该语义 stop 占比 |
| `goalSteadyDistancePressure` | 目标=STEADY 时取总距离归一 |
| `goalSteadyRiskPressure` | 目标=STEADY 时取高风险占比 |

### 4.4 交通画像 × 路线压力（用户选的 profile 命中才非零）

| 字段 | 含义 |
| --- | --- |
| `walkOnlyTotalDistancePressure` / `walkOnlyMaxSegmentPressure` | WALK_ONLY × 总/最长段距离 |
| `walkBusDistancePressure` / `walkSubwayDistancePressure` / `walkTransitDistancePressure` | 对应 profile × 总距离 |
| `bikeSubwayDistancePressure` | BIKE_SUBWAY × 总距离 |
| `walkTaxiBudgetPressure` | WALK_TAXI × 预算压力 |
| `profileActualModeFitRatio` | 实际交通桶占比是否符合用户所选 profile | 

### 4.5 时间结构 × 饭点/夜游

| 字段 | 含义 |
| --- | --- |
| `lunchRequiredMissingMeal` | 要求午餐却没覆盖 |
| `dinnerRequiredMissingMeal` | 要求晚餐却没覆盖 |
| `nightRouteNightFriendlyMatch` | 夜间行程 × 夜友好 stop 占比 |

---

## 5. 字段数量一览

| 块 | 字段数 | padding |
| --- | --- | --- |
| stopMatrix | 38 / 行 | ≤8 行 |
| segmentMatrix | 15 / 行 | ≤7 行 |
| routeDerivedVector | 61 | 1 行 |
| contextCrossVector | 27 | 1 行 |
| intraSetVector | 15 | 1 行 |

（以 `feature_schema.json` 为准；本表随 `route_pref_v5` 当前实现。）

---

## 6. 不进 X：raw snapshot（审计快照）

MinIO candidate-set ingest 和 dataset raw snapshot 里会保存原始上下文快照，但它**不是 X**，只用于审计、诊断、补 k 和按原始快照重建特征。内容：

```text
routeGoal, transportProfile, budgetLevel, interestTags, mealWindows,
departureTime, durationMinutes, routeTimeStructure, weather, userPreferenceProfile
```

LLM 对整条路线的自评分（`CandidateRouteDTO.qualityScore`）既不在五块 X、也不在本快照里——它只活在路线生成阶段，不参与训练输入。

---

## 7. 改 X 的规矩

- 五块的**字段名、顺序、shape、padding/mask、缺失默认值**任何变化 → bump `RoutePreferenceFeatureSchema.VERSION`（如后续 `route_pref_v5` → `route_pref_v6`），并用「原始快照重建」把历史样本重算到新版本，再重训。
- 只改阈值/权重不改字段形状 → 走 `thresholdsVersion`，不 bump featureSchemaVersion（见《路线裁判与软拒绝设计》§5.5）。
- 已落地：§8 的维度信号 + 组内相对特征（§1.6 原始线性分经核实非问题，无需归一，见该节说明）。

---

## 8. v5：维度分（进第三块）+ 第五块 `intraSetVector`（实现规格）

v5 为了拉开相近路线，改两处，**全部在 Java extractor 按 candidate set 组级计算**：

1. **§8.1**：6 个结构维度分 → 加进第三块 `routeDerivedVector`（绝对、逐路线可算，作训练辅助信号）。
2. **§8.2**：新增**第五块 `intraSetVector`**（15 字段，组内相对）→ X 从四块变五块，这是拉开相邻的主力。

当前已 bump `RoutePreferenceFeatureSchema.VERSION` → `route_pref_v5`，训练侧默认读取该版本；后续改字段需继续 bump 到新版本。

### 8.1 维度分（加进 routeDerivedVector，6 个）

只加**带非线性、有训练价值**的 6 个复合维度；`interestCoverageScore`(= interestCoverageRatio)、`goalFitScore`(≈ contextCross 的 goalXMatch) 是现有字段的复读，**不加**。

每个 clamp 到 [0,1]，1=好。系数为初值，**进 X 即契约，改系数要 bump featureSchemaVersion**。输入全来自 §3 现有 routeDerived 字段：

| 字段 | 公式（初值系数） |
| --- | --- |
| `diversityScore` | `clamp(0.5*categoryDiversityRatio + 0.3*(1-dominantCategoryRatio) + 0.2*(1-consecutiveSameCategoryMaxNorm), 0,1)` |
| `flowScore` | `clamp(1 - 0.5*backtrackingSegmentRatio - 0.3*longSegmentRatio - 0.2*clamp(maxSegmentDistanceNorm-1,0,1), 0,1)` |
| `timeFitScore` | `clamp(1 - 0.35*timeTightPressure - 0.35*missingRequiredMealFlag - 0.30*timeSparsePressure, 0,1)`；`timeTightPressure=clamp((timeBudgetUsageRatio-0.95)/0.05,0,1)`，`timeSparsePressure=clamp((0.60-timeBudgetUsageRatio)/0.60,0,1)` |
| `fatigueScore` | `clamp(1 - 0.5*clamp(totalDistanceNorm-1,0,1) - 0.3*longSegmentRatio - 0.2*clamp(maxSegmentDistanceNorm-1,0,1), 0,1)` |
| `budgetFitScore` | `clamp(1 - 0.8*clamp(budgetPressure,0,1) - 0.2*missingPriceRatio, 0,1)` |
| `riskScore` | `clamp(1 - 0.6*highRiskStopRatio - 0.4*avgRiskPressure, 0,1)`；`avgRiskPressure=clamp(-avgRiskCost/abs(riskCostThreshold),0,1)` |

`riskCostThreshold` 复用 routeX 配置常量，不写死。

### 8.2 第五块 `intraSetVector`（15 字段，组内相对，全部组级计算）

第五块**唯一依赖同组其它路线**，定长一行/路线，在同一 candidate set 内算。记号：一个 candidate set 有 `n` 条真实路线（2–5）；`d` 取 §8.1 的 6 个维度分之一，`d_i` 为第 i 条路线的该维度分。

**Group A — 6 维 × 2 = 12 字段**

维度分都「1=好」，越大越靠前。对每个 `d ∈ {flowScore, diversityScore, timeFitScore, fatigueScore, budgetFitScore, riskScore}`：

| 字段 | 计算 | 范围 |
| --- | --- | --- |
| `<d>RankInSet` | 本组按 `d` **降序**排，本路线位置 `p`（0=最好），`= 1 - p/(n-1)`；并列取**平均位置**；`n=1` → `0.5` | [0,1] |
| `<d>DeltaVsBest` | `d_本 - max(d over set)` | ≤0；`n=1`→0 |

字段名（12 个）：`flowScoreRankInSet`、`flowScoreDeltaVsBest`、`diversityScoreRankInSet`、`diversityScoreDeltaVsBest`、`timeFitScoreRankInSet`、`timeFitScoreDeltaVsBest`、`fatigueScoreRankInSet`、`fatigueScoreDeltaVsBest`、`budgetFitScoreRankInSet`、`budgetFitScoreDeltaVsBest`、`riskScoreRankInSet`、`riskScoreDeltaVsBest`。

**Group B — 重复度 2 字段**（需 POI id / 类目；extractor 在生成期持有真实路线对象，直接取）

| 字段 | 计算 | 范围 |
| --- | --- | --- |
| `intraSetStopOverlapRatio` | 本路线的 POI 中，**在同组至少一条其它路线里也出现**的 POI 数 / 本路线 stop 数 | [0,1]；`n=1`→0 |
| `intraSetCategoryOverlapRatio` | 同上，用 stop 的 `categoryGroup` 代替 POI id | [0,1]；`n=1`→0 |

POI 用 `poiId`（`RouteStopIdSupport.poiIdFromStopId`）；`categoryGroup` 用 §3.6 多样性那套同源值（语义 `primaryCategoryGroup`，缺失走 fallback）。

**Group C — 整体相对序 1 字段**

| 字段 | 计算 | 范围 |
| --- | --- | --- |
| `compositeRankInSet` | 每条路线先算 6 维**无权均值** `m_i`；按 `m` 降序排，`= 1 - p/(n-1)`，并列取平均位置；`n=1`→0.5。**纯名次，不是加权总分** | [0,1] |

**共同口径**
- 全部对该组**真实路线**计算，`n` 随候选条数变。
- 生成期 extract 与 rebuild **必须算出完全一致的值**，两条路径都要拿到同组所有路线（见 §8.4）。

### 8.3 模型接线（第五块分支）

和前四块一样，先过自己的分支再融合：

```text
intraSetVector(15) -> Linear(32) -> LayerNorm -> GELU -> Dropout(p)

concat(64 + 32 + 64 + 32 + 32 = 224)        # 比 v4 多第五块的 32
fusion: Linear(224, 128) -> LayerNorm -> GELU -> Dropout -> Linear(128, hidden_dim)
```

要改：`RouteInput` 加第五块、`dataset` tensor 化、`model` 加 `intra_set_encoder` 分支并改 fusion 输入维、`RoutePreferenceModelConfig` 加 `intra_set_dim`。

> **goodness 污染开关**：第五块是相对量，可能把 goodness 头学成「比同组好＝好」、带偏绝对刻度。因它被隔离成独立块，真带偏时可只把它接到 score 头、对 goodness 头 `detach`，不动其它四块。

### 8.4 组级计算与重建的前置（实现关键）

第五块打破 v4「逐路线独立 extract」，落地必须满足：

1. **生成期**：`SaveRoutePreferenceTrainingSamplesStep` 已握有同一 candidate set 全部路线。改成：先逐条算 §8.1 维度分 → 再组级算 §8.2（rank/delta/overlap）→ 每条写自己的第五块。**不要**在逐路线 `extract()` 里算第五块。
2. **重建期**：快照**本就按 `candidate_set` 唯一存储**，`rebuildByCandidateSetId` 一次还原整组（`context.getSelectedRoutes()` = 全部路线）。只需把现在的逐路线 `extract()` 循环改成「先算全组维度分 → 组级算第五块 → 逐条写」。
3. ✅ **前置已确认（已查 V12 迁移 + restorer）**：raw snapshot 存了 `selected_routes_json`（每条路线 stop→`poiId`）、`poi_candidates_json`、`poi_semantic_mappings_json`、`poi_linear_traces_json`，restorer 全部还原进 context。所以 rebuild 时 Group B 的 `poiId`/`categoryGroup`、§8.1 维度分输入**都算得出、且天然组级**。**无需改 snapshot。**

## 9. v5 实现 checklist（路径 B：落库 + 重建）

按顺序执行；生成期与重建期的第五块计算**抽同一个工具方法**，避免口径漂移。

**0. 前置（已确认，无阻塞）**
- [x] raw snapshot 按 `candidate_set` 唯一存储，含 `selected_routes`/`poi_candidates`/`poi_semantic_mappings`/`poi_linear_traces`，足够重建 §8.1 + §8.2（含 Group B）；**无需补 snapshot**。

**1. DB**
- [x] `route_preference_training_samples` 加列 `intra_set_vector_json`。

**2. 后端（Java）**
- [x] `RouteInputFeatureSnapshot` 加第五块字段。
- [x] extractor 在 `routeDerivedVector` 产出 §8.1 的 6 个维度分。
- [x] 组级算第五块：`SaveRoutePreferenceTrainingSamplesStep`（生成期）+ `RoutePreferenceFeatureRebuildService`（重建期）各接一处组级后处理，产出 `intraSetVector`（§8.2）。
- [x] bump `RoutePreferenceFeatureSchema.VERSION` → `route_pref_v5`。

**3. Python**
- [x] `repository.py` 多读 `intra_set_vector_json`，`TrainingSampleRow` 加字段。
- [x] `dataset.py` 解析第五块进 `RouteInput`、`tensorize` 进 batch；`FeatureSpec` + `infer_feature_spec` 加第五块 key。
- [x] `model.py` 加 `intra_set_encoder` 分支并改 fusion 维；`RoutePreferenceModelConfig` 加 `intra_set_dim`。
- [x] `export.py` / feature_schema.json 带上第五块。
- [x] `train.py` 用 `feature_schema_version=route_pref_v5`。

**4. 重建 + 重训**
- [x] 全量重建 v4→v5。
- [x] 重训，并以 9 次 seed benchmark 作为当前 v5 锁定基线。

**5. 验证（重点）**
- [x] 输出 `pairwiseAccuracy@gap1..4`，确认 gap=1 是当前主要瓶颈。
- [x] 以 split_seed=13/17/19 × train_seed=23/29/31 的 9 次 mean±std 作为后续改动唯一判断标准。
- [ ] **goodnessAuc/PrAuc 没被带偏**（被带偏→§8.3 detach 开关）。
- [ ] 记一条 v5 复盘；v5 是新口径，不与 v4 直接横比。

**6. 契约同步**
- [ ] 《路线偏好排序模型训练设计》`X = 四块` → 五块（那份是契约文档，改前确认）。
