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
  由五组输入特征拼接而成。单 POI 下 X 是列向量，批量 POI 时堆叠成 X_batch。

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

## 2. 五组输入特征

输入特征是 `X` 的来源分组，不直接等于评分行。`X` 共五组:前四组是各自独立的 raw 输入，
第五组 `derivedFeature` 是 extractor 把前四组交叉/池内派生出来的特征。

```text
X = concat(
  poiFeature,
  requestFeature,
  userPreferenceVector,
  environmentFeature,
  derivedFeature
)
```

单 POI 下 `X` 是列向量，批量 POI 时堆叠成 `X_batch`。

`poiFeature` 严格只装"POI 自身事实"。凡是"POI 相对本次区域/候选池"的派生(距离、孤立度、
同质化、簇连接、步行压力等)都不进 `poiFeature`，归入第五组 `derivedFeature`(标记 poolDerived)，
否则会破坏 `poiFeature = raw: POI 自身事实` 的定义。

### 2.1 poiFeature

描述 POI 自身是什么。字段已对齐真实数据源:线上 POI 来自高德 `/v5/place` 系列搜索，
扩展字段 `show_fields = business,photos`，再经 `AmapTransitPoiDetailProvider` 补最近交通设施。
因此字段表只收录"高德链路真实拿得到或可本地派生"的特征，拿不到的一律不进 v1。

数据可得性分四档:

```text
档 0:已在 PoiCandidateDTO 里，直接用。
档 1(待确认):疑似在高德响应里，但当前代码未结构化 parse，能否稳定随
            /v5/place + show_fields=business,photos 返回需先用响应样本或官方文档确认。
档 2:候选池/区域派生，或由档 0/1 字段合成，无额外调用。
档 3:当前高德链路拿不到，v1 砍掉(排队、人流、门票、评论数等)。
```

> 当前代码确认已读到的扩展字段:`business.rating`、`business.cost`、`business.business_area`、
> `photos[].url`、`type`(目前 `type` 只进了 description 字符串，未结构化)。
>
> 已确认可得、仅需补 parse(档 1，来自真实响应样本):
> - `typecode`(顶层，稳定分类编码，如 "050300")
> - `business.opentime_today` / `business.opentime_week`(营业时间，string，如 "08:00-16:30")
> - `business.keytag` / `business.rectag`(分类/特色标签，如 "小吃快餐")
> - `distance`(顶层，米，AROUND 搜索直接返回，可替代 GeoMath 算距离)
> 落地时给 `PoiCandidateDTO` 补对应字段即可，零额外调用;typecode/opentime 不再降级。
>
> 可能缺失、弱信号:`business.tel`——真实样本中部分 POI 的 business 块不含 tel，
> 故 tel 只作"有则加分"的弱信号，不得作为必备项。

字段表(取值范围 / 默认值 / missing indicator 在第 3、6 节按 step 2 细化):

#### W_quality(信息质量与可信度)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `ratingNorm` | 高德评分规约 | `ratio` | 0 | `business.rating / 5` |
| `isRatingMissing` | 评分缺失指示 | `bool(missing)` | 0 | `rating == null` |
| `hasImage` | 是否有图 | `bool` | 0 | `photos` 非空 |

> 说明:`description` 是后端 `buildPoiDescription(...)` 合成、几乎永远非空，
> 不能用"描述是否为空"做质量信号，故删除 hasDescription / isDescriptionMissing。
> 已删除 `sourceCompleteness`(原 brandTrust 的完整度合成):其分量 ratingExists/hasImage/hasAddress
> 与 `isRatingMissing`/`hasImage`/`missingInfoRisk` 三重重叠，且含 avgPriceExists 会系统性误伤
> 无 cost 的景点。质量缺失统一由 `isRatingMissing`、`hasImage`(具体)与 `missingInfoRisk`(综合负向)承担。

#### W_budget(预算压力)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `avgPriceNorm` | 人均规约 | `overflow` | 0 | `business.cost` |
| `isPriceMissing` | 人均缺失指示 | `bool(missing)` | 0 | `cost == null` |
| `isFree` | 是否免费 | `bool` | 2 | 消费类且 `cost == 0` 才置 1(见下文) |
| `expensivePoiRisk` | 高消费风险 | `ratio` | 2 | 由 `avgPriceNorm` 派生 |

> 预算特征按类别条件生效:`cost`(人均)主要出现在餐饮/购物等消费类 POI;景点、公园、
> 免费场所的 business 通常无 cost(真实样本:景点 typecode 110000 无 cost)。因此:
> - 非消费类 cost 缺失是常态，按 `isPriceMissing` 中性处理，不扣分、不臆测低价。
> - `isFree` 仅在"消费类 + cost==0"时可信;非消费类的"免费"无法从数据判定，一律不置 1
>   (景点 null cost 不代表免费，故宫就有票但高德无 cost)。
> - 高德无门票字段，景点票价一律拿不到(`ticketPriceNorm` 已剔除)。
> 落地时用 `categoryGroup` 把 W_budget 主要作用在消费类 POI 上，避免对景点误判预算。

#### W_transport(POI 自身交通可达性)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `transitHigh` | 交通可达高档(`<=300m`) | `bool(one-hot)` | 0 | `transitAccessibility` 展开 |
| `transitMedium` | 交通可达中档(`<=800m`) | `bool(one-hot)` | 0 | `transitAccessibility` 展开 |
| `transitLow` | 交通可达低档(`>800m`) | `bool(one-hot)` | 0 | `transitAccessibility` 展开 |
| `nearestTransitDistanceNorm` | 最近地铁/公交距离规约 | `overflow` | 0 | `nearestTransit[0].distanceMeters` |

#### W_risk(POI 自身风险)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `missingInfoRisk` | 信息缺失综合风险 | `ratio` | 2 | 缺图 + 缺评分 + 缺地址聚合 |
| `weatherSensitive` | 天气敏感(室外类，**原料**) | `ratio` | 2 | `typecode` 规则;不直接打分，喂 `weatherOutdoorRisk` cross |

> 进 W_risk 直接打分的 POI 自身列只有 `missingInfoRisk`;`weatherSensitive` 晴雨不分，
> 只作原料,真正进 W_risk 的是 `weatherOutdoorRisk = badWeatherSeverity * weatherSensitive`(derivedFeature)。
> `closeRisk`(POI.opentime ⊗ 请求时间窗)、`categoryDuplicateRisk`(候选池同类计数)均属 derivedFeature。

#### 身份标识(POI 自身 raw，进 poiFeature，给保守的小解释性权重)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `candidateRole` | 候选角色 one-hot | `bool(one-hot)` | 0 | `role(MUST_VISIT/ANCHOR/MEAL/REST/LOCAL/BACKUP)` |
| `isMustVisit` | 是否必去点 | `bool` | 0 | `mustVisit` |

> `isMustVisit`/`candidateRole` 的真正保留由 Hard Constraint Gate 保证;这里只给很小的
> 解释性加分，权重保守，避免角色直接主导排序。

#### POI 语义标签(POI 自身派生，进 poiFeature，可被 W_goal / W_personalization 消费)

由 typecode/keytag/rectag 映射得到的 POI 语义 bool，是稳定的 POI 自身事实，作 poiFeature 直接列。
W_goal 消费这些语义列，权重由 `Delta_goal(routeGoal)` 切换(方案 A，见第 7 节);
不做 `goalXxxMatch` cross。

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 | 归属行 |
| --- | --- | --- | --- | --- | --- |
| `categoryGroup` | POI 大类组 one-hot | `bool(multi-hot)` | 1 | `typecode` 映射到有限大类组 | gating/原料(W_budget 门控、categoryDuplicate) |
| `isClassic` | 是否经典 | `bool` | 1 | `typecode + keytag/rectag` 映射表 | `W_goal` |
| `isLocal` | 是否本地 | `bool` | 1 | 同上 | `W_goal` |
| `isPhotoFriendly` | 是否适合拍照 | `bool` | 1 | 同上 | `W_goal` |
| `isNightFriendly` | 是否适合夜游 | `bool` | 1 | 同上 | `W_goal`(并供 `nightMatch` cross) |
| `isQuiet` | 是否安静/休息 | `bool` | 1 | 同上 | `W_goal` |
| `isHiddenGem` | 是否小众 | `bool` | 1 | 同上 | `W_goal`(并供 `personalizedExplorationMatch` cross) |

#### extractor 原料(不直接进 M*X)

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 |
| --- | --- | --- | --- | --- |
| `rawType` | 原始 `typecode/type` | `enum(原料)` | 1 | 仅供映射成 `categoryGroup` / 语义 bool，不直接进 `M * X` |

> 类别没有大小顺序，禁止把 typecode/type 当 ordinal 直接进 `M * X`(会引入错误的大小含义)。
> 真要进 X，用 `categoryGroup` one-hot 或语义 bool(isLocal/isClassic/isNightFriendly 等)表达。
> 语义标签与 weatherSensitive 都依赖一张"高德 typecode/keytag/rectag -> 语义"映射表，
> typecode 是稳定 6 位编码(如 050300=快餐厅)，按大类组(前 4 位或大类前缀)映射，
> 不逐码维护;待四组 raw 全部定义完成后作为附录单独起草。

#### poolDerived 特征(POI 相对本次区域/候选池，归 derivedFeature，非 poiFeature)

以下特征虽被距离/交通/风险行消费，但不是 POI 自身事实，属第五组 `derivedFeature`，
此处先登记，正式定义在 derivedFeature 小节。

| 字段名 | 含义 | 类型 | 数据档 | 来源/合成 | 归属行 |
| --- | --- | --- | --- | --- | --- |
| `distanceNorm` | 到区域中心距离规约 | `overflow` | 2 | 高德 `distance`(`AROUND`)或 `location` 算 / 有效半径(`transportProfile` 按时长分档) | `W_distance` |
| `isolatedDistanceNorm` | 孤立远点程度 | `overflow` | 2 | 到其他候选最近距离 | `W_distance` |
| `clusterConnectivity` | 与候选簇可连接性 | `ratio` | 2 | 邻域候选数 | `W_distance` |
| `distanceFatiguePressure` | 距离体力压力 | `ratio` | 2 | 由 `distance` 派生(预筛无前后点 segment) | `W_distance` |
| `walkingAccessibility` | 步行可达性 | `ratio` | 2 | 由 `distance + transit` 派生 | `W_transport` |
| `categoryDuplicateRisk` | 同质化风险 | `ratio` | 2 | 候选池内同 `type` 计数 | `W_risk` |

被剔除的字段(档 3，v1 不收录):

```text
queueRisk / crowdRisk | 新版 POI search 无排队、人流数据
ticketPriceNorm       | 仅有人均 cost，无门票字段
reviewCountNorm       | 新版 search 的 business 不含评论数
brandTrust / sourceCompleteness | 无品牌权威分;完整度合成与 isRatingMissing/hasImage/missingInfoRisk 三重重叠，且 avgPriceExists 误伤景点，删除
hasDescription 等     | description 后端合成、恒非空，不能做质量信号
```

### 2.2 requestFeature

描述这次用户想要什么路线。字段对齐真实请求参数 `RouteGenerateParam`。

核心原则:

```text
requestFeature v1 不提供独立打分列。
预筛在同一请求内对所有候选排序，而请求级标量对每个 POI 取值相同，
作为线性列只会给所有候选加同一常数偏移，不改变相对排序，故不进 X。
它只承担五种角色:
  Delta selector       切换 W
  normalization basis  当 distanceNorm / avgPriceNorm 等的分母或时间窗
  Gate input           供 Hard Constraint Gate 过滤/保留
  derivedFeature input 与 POI 叉乘成 per-POI 的 derivedFeature 列(才真正进 X)
  metadata             仅展示/配置，不参与打分
```

> 数学上:v1 中 `requestFeature` 在最终 `X` 中可为空段(维度为 0)，它是"逻辑来源组"，
> 不一定有直接列;其字段主要通过 Delta / basis / Gate / derivedFeature 生效。

`RouteGenerateParam` 逐字段判断:

| 字段 | 角色 | 去向 |
| --- | --- | --- |
| `routeGoal(STEADY/CLASSIC/LOCAL/LOW_BUDGET/NIGHT/PHOTO)` | `Delta selector` | 切 W |
| `transportProfile(WALK_ONLY/WALK_SUBWAY/BIKE_SUBWAY/WALK_TAXI)` | `Delta selector + basis` | 切 W + `effectiveRadius` 上限 |
| `budgetLevel(LOW/NORMAL/FLEXIBLE，待前端/param 新增)` | `Delta selector + basis` | 切 `W_budget` + `budgetCap` |
| `interestTags(List<String>)` | `derivedFeature input` | -> `interestMatchRatio(POI.matchedInterestTags)` 现成 |
| `departureTime(Instant)` | `basis + derived input` | `routeTimeWindow` -> `routeTimeStructure(2.4)` + `closeRisk` |
| `durationMinutes(Integer)` | `basis` | `durationBucket`(选 transport 半径档) + `routeTimeWindow` 终点 |
| `center(GeoPointParam)` | `basis` | `distanceOrigin(AUTO_RADIUS)` |
| `radiusMeters(Integer)` | `basis` | 收窄 `effectiveRadius`(不能放宽) |
| `areaPolygonGcj02(List)` | `basis` | `distanceOrigin + polygonRadius(MANUAL_POLYGON)` |
| `adminAdcodes(List)` | `basis + Gate input` | 区域 + 越界过滤(未来 ADMIN 模式启用后;v1 未开放) |
| `areaMode(AUTO_RADIUS/MANUAL_POLYGON/ADMIN_DISTRICTS)` | `basis selector` | 选 `distanceOrigin` / 半径算法 |
| `mustVisitPoints(List)` | `Gate input` | Gate 保留必去点;POI 侧已有 `isMustVisit` |
| `routeCityName/routeCityAdcode/areaLabel` | `metadata` | 不进 X |

#### effectiveRadiusMeters(distanceNorm 的分母,按 areaMode 解析)

`distanceNorm` 必须同时定义 `distanceOrigin` 与 `effectiveRadiusMeters`。交通档半径是上限，
请求半径只能收窄、不能放宽(否则 WALK_ONLY 下距离尺度会被大半径撑失真)。

```text
transportRadius = transportProfile.defaultRadiusMeters(durationBucket)

AUTO_RADIUS:
  distanceOrigin = request.center
  effectiveRadiusMeters = request.radiusMeters 存在 ? min(request.radiusMeters, transportRadius) : transportRadius

MANUAL_POLYGON:
  distanceOrigin = polygon centroid 或 bbox center
  polygonRadius  = centroid 到顶点最大距离，或 bbox 半径
  effectiveRadiusMeters = min(polygonRadius, transportRadius)

ADMIN_DISTRICTS:
  当前代码未开放，v1 不启用。
  若开放，优先不使用"到中心距离"(行政区形状会让中心距离误导)，
  改用 区内 Gate + poolDerived(isolatedDistanceNorm / clusterConnectivity)表达空间合理性。
```

`durationBucket`:由 `durationMinutes` 落到 transportProfile 的 short / halfDay / fullDay 三档半径。

> MANUAL_POLYGON 只定义搜索/Gate 边界;`effectiveRadius` 仍按交通可执行性封顶，用于距离惩罚尺度。
> 因此 polygon 远端 POI 的 `distanceNorm` 可能 > 1(被距离行压低),但不一定被 Gate 掉——
> 这是有意的:边界归边界,距离惩罚尺度归交通可执行性。

#### 其余角色约定

```text
interestTags:
  -> derivedFeature.interestMatchRatio。
  注意数据语义:POI.matchedInterestTags 首版表示"命中的搜索计划标签"，
  由搜索计划赋值，不是 POI 完整内容标签;不能误以为它表达了 POI 的全量兴趣覆盖。

departureTime / durationMinutes:
  routeTimeWindow = [departureTime, departureTime + durationMinutes]
  routeTimeStructure 由路线时间窗派生，不是当前系统时间(详见 2.4 routeTimeStructure)。
  同时作为 closeRisk 的时间窗输入。requestFeature 不重复登记时段列。

routeCityAdcode / budgetCap:
  v1 不做城市价差，budgetCap 用全局常量，routeCityAdcode 不进 X(仅元信息/未来城市级配置 key)。
  已知限制:不同城市消费水平不可比，但预筛只在同一请求、同城候选池内排序，
  v1 全局 budgetCap 不影响同请求内相对排序的主逻辑。

mustVisitPoints:
  请求侧不进 X。Gate 保留必去点，POI 侧已有 isMustVisit。
  不做"到必去点距离"cross，避免预筛提前偏向路线结构;点间几何关系交给采样/路线编排。

paceLevel:
  不属于 Linear Ranker。点位数量/松紧交给 Diversity-aware Sampler 和路线编排层决定，
  Linear 预筛不设 Delta_pace。

budgetLevel:
  当前 RouteGenerateParam 尚无，需前端/param 新增;落地前 budgetCap 用全局常量降级，
  Delta_budget 暂不生效。

budgetLevel vs routeGoal=LOW_BUDGET(职责边界，防重复调 W_budget):
  budgetLevel 负责预算强度和 budgetCap / W_budget;
  routeGoal=LOW_BUDGET 只表达路线目标偏好，不重复调 W_budget，或 v1 不启用该 routeGoal。
```

### 2.3 userPreferenceVector

描述用户长期偏好。v1 来源是**问卷**(显式冷启动画像)，不是行为学习画像。

定性:

```text
userPreferenceVector v1 是问卷来源的逻辑来源组，不提供独立打分列;
它只作为 derivedFeature 的 cross 原料，由 extractor 生成固定维度的 personalization 特征。
```

核心原则(同 request/environment):

```text
同一个用户在一次请求里画像对所有 POI 都相同，直接进 X 只是常数偏移，不改排序。
所以 userPreferenceVector:
  - 不进 X，不设独立列
  - 不走 Delta(连续画像改 W 违反铁律，这正是已删除 Delta_userProfile 的原因)
  - 只走 cross:画像 ⊗ POI -> derivedFeature -> W_personalization
```

#### 两条硬约束(写死，防 schema 崩)

```text
约束 1:X 维度固定。
  tagAffinity[tagCode] 是 map/source，不是一堆动态列。
  Linear 的 X 维度必须固定，不能随兴趣标签目录增删而变。
  extractor 必须把 Σ(tagAffinity[tag] * poiTagHit[tag]) 收敛成"固定个数"的 personalization cross，
  personalization 特征数量与标签目录大小无关。

约束 2:单一标签体系。
  问卷兴趣项必须对齐 InterestTagCatalogPO.tagCode;新增/下线标签走 catalog 版本管理。
  否则 tagAffinity、typecode 映射、request.interestTags 会分裂成三套标签。
```

#### v1 字段表(来源=问卷;均为 cross 原料，不直接进 M*X)

| 字段 | 含义 | 类型 | 来源 | 备注 |
| --- | --- | --- | --- | --- |
| `tagAffinity[tagCode]` | 各兴趣长期权重 | `map<tagCode, ratio>` | 问卷 | 键对齐 `InterestTagCatalogPO.tagCode`，不另造 |
| `distanceSensitivity` | 距离敏感度 | `ratio` | 问卷 | |
| `budgetSensitivity` | 预算敏感度 | `ratio` | 问卷 | |
| `transferSensitivity` | 换乘敏感度 | `ratio` | 问卷 | |
| `hiddenGemAffinity` | 小众偏好 | `ratio[0,1]` | 问卷 | 0=不偏小众,1=强偏小众 |
| `profileConfidence` | 画像置信度 | `ratio` | 派生 | 问卷完成度;作全局收缩乘子，不进 W |
| `isNewUser` | 是否新用户 | `bool` | 派生 | 是否填过问卷 |
| `crowdSensitivity` | 拥挤敏感度 | `ratio` | 问卷 | **reserved**:POI 无 crowd 数据，叉不出来 |
| `favorite/completed/skipped/replaced/dislike aggregation` | 行为聚合 | — | 行为 | **v2 reserved**:问卷阶段无，不进 v1 X |

#### W_interest vs W_personalization 边界

```text
interestMatchRatio:
  来源 = 本次请求 request.interestTags ⊗ POI
  归属 = W_interest
  语义 = 这趟临时想要什么(短期)

userInterestAffinity:
  来源 = 问卷长期 tagAffinity ⊗ POI
  归属 = W_personalization
  语义 = 用户长期口味
```

> 长期画像**不要复用 `POI.matchedInterestTags`**:它现在是"搜索计划命中的标签"，受本次 request 影响。
> 长期亲和应用 `POI.typecode/keytag/rectag/categoryGroup` 对齐
> `InterestTagCatalogPO.tagCode/amapTypeCodes/amapKeywords` **重新算 `poiTagHit[tagCode]`**。

#### cross 预登记(derivedFeature → W_personalization;均乘 profileConfidence 收缩)

| cross | 合成 |
| --- | --- |
| `userInterestAffinity` | `profileConfidence * Σ(tagAffinity[tag] * poiTagHit[tag])` |
| `personalizedDistancePressure` | `profileConfidence * distanceSensitivity * distanceNorm` |
| `personalizedBudgetPressure` | `profileConfidence * budgetSensitivity * avgPriceNorm` |
| `personalizedTransitPressure` | `profileConfidence * transferSensitivity * nearestTransitDistanceNorm` |
| `personalizedExplorationMatch` | `profileConfidence * hiddenGemAffinity * isHiddenGem` |

> 全部乘 `profileConfidence`:新用户/跳过问卷/问卷稀疏时个性化自然收缩到≈0，不凭空影响排序。
> 这些 cross 个数固定(与标签目录大小无关)，满足"X 维度固定"约束。

### 2.4 environmentFeature

描述本次请求的外部环境。

核心原则(同 requestFeature):

```text
environmentFeature v1 不提供独立打分列。
同一次请求里天气、温度、时段、是否节假日对所有 POI 都相同，
直接进 X 只会给所有 POI 加同一常数，不改变相对排序。
它只通过两条路起作用:
  Delta selector       仅 routeTimeStructure(属允许切 W 的小枚举)可驱动 Delta_time
  derivedFeature input 与 POI 侧字段叉乘成 per-POI 列(才真正进 X)

铁律:允许切 W 的小枚举只有 routeGoal / transportProfile / budgetLevel / routeTimeStructure。
weatherBucket / temperatureLevel 不在内，不得驱动 Delta，只能走 cross，
否则等于用未授权的量改 W。
```

字段表:

| 字段名 | 含义 | 类型 | 数据档 | 来源 |
| --- | --- | --- | --- | --- |
| `routeTimeStructure` | 路线时段(morning/lunch/afternoon/dinner/night) | `enum` | derived | `departureTime + durationMinutes` |
| `weatherBucket` | 天气桶(clear/rain/snow/extreme…) | `enum` | env-1 | `lives.weather` 归一化 |
| `temperatureLevel` | 温度档(cold/mild/hot) | `ordinal` | env-1 | `lives.temperature` |
| `windLevel` | 风力档(可选,弱) | `ordinal` | env-1 | `lives.windpower` |
| `humidityLevel` | 湿度档(可选,弱) | `ordinal` | env-1 | `lives.humidity` |
| `isHoliday` | 是否节假日 | `bool` | calendar-1 | 节假日表/算法(代码日历) |
| `isWeekend` | 是否周末 | `bool` | derived | `departureTime` 星期 |

> 数据档说明:`env-1` = 需新增高德 `lives` 天气源(每请求一次,可按 city/adcode 缓存),
> 与 poiFeature 的"档 1(零额外调用)"不同体系;`calendar-1` = 节假日表/算法(本地,无外部调用);
> `derived` = 由请求字段本地派生,无外部调用。

> 前置依赖:`weatherBucket/temperatureLevel/windLevel/humidityLevel` 需新增高德 `lives` 实况
> 天气 provider(`weather/temperature/winddirection/windpower/humidity`)。这是**额外调用**
> (1 次/请求，按 city/adcode 缓存),不同于 poiFeature 的零额外调用。当前代码无天气 provider。

#### 怎么起作用

```text
routeTimeStructure:
  - 驱动 Delta_time(它是允许切 W 的小枚举)
  - 参与 derivedFeature 的 cross:closeRisk、nightMatch、mealMatch
  注意:Delta_time 与时段相关 cross 不要双算同一效果——
        POI 语义匹配(夜游/饭点)走 cross;非语义的整体权重位移(如夜间距离更敏感)走 Delta_time。

weatherBucket / temperatureLevel(只走 cross，不进 Delta):
  badWeatherSeverity / heatLevel / rainLevel 由 extractor 从天气桶+温度合成，
  再与 POI 侧字段叉乘:
    weatherOutdoorRisk = badWeatherSeverity * POI.weatherSensitive        -> W_risk
    heatFatigueRisk    = heatLevel * distanceFatiguePressure              -> W_distance
    rainTransportRisk  = rainLevel * (1 - walkingAccessibility)           -> W_transport

windLevel / humidityLevel:
  v1 并入 badWeatherSeverity，不单独成列(单列权重太碎，解释收益低)。

isHoliday / isWeekend:
  v1 metadata / reserved。其主要用途是拥挤，而 crowd 无数据源;
  硬打分会变成"节假日一律加/扣"，没有 POI 区分度，故 v1 不打分。
```

#### derivedFeature 预登记(环境驱动的 cross，归第五组)

| 字段名 | 合成 | 归属行 |
| --- | --- | --- |
| `weatherOutdoorRisk` | `badWeatherSeverity * POI.weatherSensitive` | `W_risk` |
| `heatFatigueRisk` | `heatLevel * distanceFatiguePressure` | `W_distance` |
| `rainTransportRisk` | `rainLevel * (1 - walkingAccessibility)` | `W_transport` |
| `closeRisk` | `f(routeTimeWindow, POI.opentime)` | `W_risk` |
| `nightMatch` | `routeTimeStructure.isNight * POI.isNightFriendly` | `W_goal` |
| `mealMatch` | `routeTimeStructure.mealWindow * POI.isMealCandidate` | `W_goal` |

> `isMealCandidate` = `candidateRole == MEAL` 或 `categoryGroup.food`,由 extractor 判定，避免半自然语言表达。

被剔除(无数据源,v1 不收录):

```text
airQuality | 高德 lives 实况天气无 AQI/PM2.5，需另接空气质量源;v1 砍，最多 reserved
crowd 拥挤 | 无任何数据源，和 POI 的 crowdRisk 一并砍
```

### 2.5 derivedFeature

extractor 把前四组交叉/池内派生出来的 **per-POI** 列。除 `poiFeature` 外，它是 `X` 中
**唯一随 POI 变化**的组;request / environment / userPreference 三组本身在 X 里是空段，
全部信号都在这里落成可打分的列。

```text
维度固定原则:
  derivedFeature 列数固定，与兴趣标签目录大小无关。
  涉及标签的 cross(interestMatchRatio / userInterestAffinity)必须由 extractor
  收敛成固定个数的标量，不能展开成随 catalog 增删的动态列。
```

#### A. poolDerived(POI ⊗ 本次区域/候选池)

| 字段 | 合成 | 数据档 | 归属行 |
| --- | --- | --- | --- |
| `distanceNorm` | 高德 `distance`(AROUND)或 `location` 算 / `effectiveRadius` | 派生 | `W_distance` |
| `isolatedDistanceNorm` | 到其他候选最近距离 | 派生 | `W_distance` |
| `clusterConnectivity` | 邻域候选数 | 派生 | `W_distance` |
| `distanceFatiguePressure` | 由 `distance` 派生(预筛无前后点 segment) | 派生 | `W_distance` |
| `walkingAccessibility` | 由 `distance + transit` 派生 | 派生 | `W_transport` |
| `categoryDuplicateRisk` | 候选池内同 `type` 计数 | 派生 | `W_risk` |

#### B. requestCross(POI ⊗ 本次请求)

| 字段 | 合成 | 数据档 | 归属行 |
| --- | --- | --- | --- |
| `interestMatchRatio` | `request.interestTags` ⊗ `POI.matchedInterestTags`(本次搜索计划命中标签) | 派生 | `W_interest` |

#### C. envCross(POI ⊗ 环境 / 路线时间)

| 字段 | 合成 | 数据档 | 归属行 |
| --- | --- | --- | --- |
| `weatherOutdoorRisk` | `badWeatherSeverity * POI.weatherSensitive` | env-1 + POI | `W_risk` |
| `heatFatigueRisk` | `heatLevel * distanceFatiguePressure` | env-1 + 派生 | `W_distance` |
| `rainTransportRisk` | `rainLevel * (1 - walkingAccessibility)` | env-1 + 派生 | `W_transport` |
| `closeRisk` | `f(routeTimeWindow, POI.opentime)` | derived + 档1 | `W_risk` |
| `nightMatch` | `routeTimeStructure.isNight * POI.isNightFriendly` | derived + 档1 | `W_goal` |
| `mealMatch` | `routeTimeStructure.mealWindow * POI.isMealCandidate` | derived + 档1 | `W_goal` |

#### D. profileCross(POI ⊗ 问卷画像，均乘 profileConfidence)

| 字段 | 合成 | 数据档 | 归属行 |
| --- | --- | --- | --- |
| `userInterestAffinity` | `profileConfidence * Σ(tagAffinity[tag] * poiTagHit[tag])` | 问卷 + POI | `W_personalization` |
| `personalizedDistancePressure` | `profileConfidence * distanceSensitivity * distanceNorm` | 问卷 + 派生 | `W_personalization` |
| `personalizedBudgetPressure` | `profileConfidence * budgetSensitivity * avgPriceNorm` | 问卷 + POI | `W_personalization` |
| `personalizedTransitPressure` | `profileConfidence * transferSensitivity * nearestTransitDistanceNorm` | 问卷 + POI | `W_personalization` |
| `personalizedExplorationMatch` | `profileConfidence * hiddenGemAffinity * isHiddenGem` | 问卷 + POI | `W_personalization` |

#### goal 语义匹配的归属(已定:方案 A)

```text
goal 语义匹配采用方案 A:
  routeGoal 只通过 Delta_goal 改 W_goal 权重;
  POI 语义 bool(isClassic/isLocal/...)是 poiFeature 直接列，由 W_goal 消费;
  不新增 goalXxxMatch cross。
```

> 不做 `goalXxxMatch = isLocal * (routeGoal==LOCAL)`:那会让 routeGoal 同时承担"切 W"和
> "生成 X cross"两个职责，解释、调参、训练都会变乱。本节的 `nightMatch/mealMatch` 是
> **时间窗口 cross**(表达"这趟时间适不适合"),不是 routeGoal cross，故仍保留在 derivedFeature。

## 3. 特征规约化规则

所有进入 `X` 的特征必须先规约化。本节是 step 2 的产物:在第 2 节"哪些字段存在"的基础上，
逐字段定 **取值范围 / 规约化公式 / 默认值 / missing indicator**。第 2 节里 request /
environment / userPreference 是空段，本节只规约 **真正进 X 的两组:poiFeature 与 derivedFeature**。

### 3.0 类型约定

```text
布尔(bool):        {0, 1}。one-hot/multi-hot 的每个分量也是 {0,1}。
比例(ratio):        [0, 1] clamp。
惩罚(penalty):      [0, 1]，由负权重表达扣分(值越大扣得越多)。
溢出(overflow):     [0, cap]，cap 必须显式写明(本文用 1.5 或 2.0)。
枚举(enum):         one-hot / multi-hot / 稳定 ordinal，禁止把无序类别当 ordinal。
缺失(missing):      必须给默认值 + 额外 missing indicator 列(见 3.1)。
```

### 3.1 缺失值总则(四条铁律，逐字段不再重复解释)

```text
1. 缺失默认 = 中性先验，不是最差值。
   连续/比例字段缺失取"量纲中点或不奖不罚点"，绝不取 0 当扣分，也不臆测有利值
   (如 cost 缺失不臆测低价、不臆测免费)。

2. missing indicator 只配"小折扣"权重。
   缺失本身已由默认值温和体现不确定性;indicator 再叠一个极小负权即可，
   不得让 "缺失" 在数值上等同 "最差"。

3. 缺失重叠不重复放大(对齐第 6 节)。
   同一份缺失只允许被主权重打一次:综合缺失走 W_risk.missingInfoRisk(主),
   W_quality 的 isRatingMissing / hasImage 给"具体但极小"的权重，避免三重计分。

4. "事实性差" ≠ "信息缺失"。
   无最近交通站、距离远、确定闭店是真实负向事实，可正当扣分(可取 cap),
   不受第 1 条保护;只有"源里没这个字段"才按中性先验处理。
```

### 3.2 参考尺度常量(v1 默认，待 sanity check 校准)

规约化分母集中在此，避免散落。这些是 v1 经验初值，第 9 节样例回归后再调。

```text
budgetCap        = 150 元/人        # avgPriceNorm 分母(全局常量，v1 不做城市价差)
effectiveRadius  = 见 2.2 解析       # distanceNorm 分母:transportProfile 档半径封顶，request 半径只能收窄
transitRef       = 800 m            # nearestTransitDistanceNorm 分母(对齐交通档阈值)
walkRef          = 1000 m           # walkingAccessibility 步行舒适上限
fatigueRef       = 随 transportProfile # distanceFatiguePressure 分母(WALK_ONLY 最小→更易疲劳)
isolationRef     = effectiveRadius / 3 # isolatedDistanceNorm 分母
neighborR        = 300 m            # clusterConnectivity / 邻域计数半径
connectFull      = 5 个             # clusterConnectivity 饱和邻域数
dupFull          = 5 个             # categoryDuplicateRisk 饱和同类数
affinityNorm     = Σ tagAffinity    # userInterestAffinity 归一分母(按用户问卷权重和);<=0(空问卷)时该列直接取 0,不除零
```

### 3.3 poiFeature 逐字段规约

#### W_quality

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `ratingNorm` | ratio | [0,1] | `clamp(business.rating / 5, 0, 1)` | `0.5`(量纲中点) | `isRatingMissing` |
| `isRatingMissing` | bool | {0,1} | `rating == null ? 1 : 0` | `0` | 自身即指示列 |
| `hasImage` | bool | {0,1} | `photos 非空 ? 1 : 0` | `0`(无图) | 无独立列(无图即 0) |

> `ratingNorm` 缺失给中点 0.5 已是相对真实均值(≈0.8)的温和折扣，故 `isRatingMissing`
> 权重设极小或仅供 `missingInfoRisk` 聚合，**不与 0.5 折扣双算**(铁律 2、3)。

#### W_budget(整组先经 categoryGroup 消费类门控,非消费类四列全 0)

`isConsumable = categoryGroup ∈ 消费类(餐饮/购物/休闲娱乐等)`。**门控前置:非消费类
(景点/公园/免费场所)`isConsumable=0`,本组四列一律 0(含 isPriceMissing=0),不参与价格评估。**
仅当 `isConsumable=1` 时才按下表评估;下面公式均已隐含 `isConsumable=1` 的前提。

| 字段 | 类型 | 取值范围 | 规约化公式(仅消费类) | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `avgPriceNorm` | overflow | [0,2.0] | `min(business.cost / budgetCap, 2.0)` | `0.5`(中性，不臆测低价) | `isPriceMissing` |
| `isPriceMissing` | bool | {0,1} | `(isConsumable && cost == null) ? 1 : 0` | `0` | 自身即指示列 |
| `isFree` | bool | {0,1} | `(isConsumable && cost == 0) ? 1 : 0` | `0` | 非消费类恒 0 |
| `expensivePoiRisk` | ratio | [0,1] | `clamp((avgPriceNorm - 1.0) / 1.0, 0, 1)`(超 budgetCap 后线性升至 2×cap=1) | `0` | 随 `isPriceMissing` 置 0 |

> 关键:`isPriceMissing` **不是** `cost == null`,而是门控后的 `isConsumable && cost == null`。
> 否则景点/公园(typecode 110000 等本就无 cost)会被误标价格缺失,再叠 `avgPriceNorm=0.5` 当成
> 中等消费惩罚,与"非消费类 cost 缺失中性、不扣分"自相矛盾。
> 消费类内部 cost 缺失才取中点 0.5(价格未知,不奖不罚;绝不取 0 被当免费误加分)。

#### W_transport

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `transitHigh` | one-hot | {0,1} | `nearestTransit ≤ 300m ? 1 : 0` | — | 见下 |
| `transitMedium` | one-hot | {0,1} | `300m < nearestTransit ≤ 800m ? 1 : 0` | — | 见下 |
| `transitLow` | one-hot | {0,1} | `nearestTransit > 800m 或 provider 成功返回空 ? 1 : 0` | 见下区分 | 见下 |
| `nearestTransitDistanceNorm` | overflow | [0,1.5] | `min(nearestTransit[0].distanceMeters / transitRef, 1.5)` | 见下区分 | 无独立列 |

> 三档严格 one-hot(恰一个为 1)。规约化按 provider 的**结构化状态** `transitLookupStatus` 分流,
> 不得只看 `nearestTransit` 是否为空(空列表会把"真无站"和"没查到"混为一谈)。
> 关键区分:**事实性差(扣分)走 POI 列,系统级能力缺失(行失效)走请求级开关**,二者不混用——
> 系统没拿到交通信号时不能伪装成 `transitMedium=1`(那是一个虚假常数,对全体 POI 同值只是噪音)。
>
> | `transitLookupStatus` | 语义 | 规约化处理 |
> | --- | --- | --- |
> | `SUCCESS` | 查到站点 | 正常使用交通列:按距离落 High/Medium/Low + `nearestTransitDistanceNorm` |
> | `SUCCESS_EMPTY` | 成功但范围内确无公交/地铁 | 铁律 4 事实性差:`transitLow=1`、`nearestTransitDistanceNorm=1.5`(cap) |
> | `UNAVAILABLE` | API 未配置/未跑(系统级能力缺失) | 请求级 `transportSignalAvailable=false`,触发 **transit mask**(见下) |
> | `FAILED` | 调用异常/超时 | **批量整次失败**→同 `UNAVAILABLE`,触发 transit mask;**(未来)单 POI 局部失败**→仅对失败 POI 中性降级(`transitMedium=1`、distNorm=1.0)并保留其余 POI 的真实交通信号 |
>
> **transit mask(`transportSignalAvailable=false` 时的横切门控,跨行生效)**:
> X 维度固定,不能真的删列。失效时**所有相关列仍保留固定维度、取默认值,由 mask 关闭其对 linearScore
> 的贡献**(实现上等价于把这些列乘 0 / 把相关权重本次置 0),绝不是真不写列。被 mask 的列**横跨多行**,
> 凡依赖 `nearestTransit / transitScore / transitHigh|Medium|Low` 的都要关,否则交通维度会从别的行旁路漏回来:
>
> ```text
> 被 mask 关闭(保留维度、置默认、不贡献):
>   W_transport:       transitHigh / transitMedium / transitLow / nearestTransitDistanceNorm
>                      / walkingAccessibility / rainTransportRisk
>   W_personalization: personalizedTransitPressure   ← 关键:它也吃 nearestTransitDistanceNorm，
>                                                       不 mask 就成了交通维度的旁路漏点
> 不受 mask 影响、仍正常工作(纯距离/非交通):
>   W_distance:        distanceNorm / distanceFatiguePressure / heatFatigueRisk
>                      / isolatedDistanceNorm / clusterConnectivity
> ```
>
> 排序因此退化到"无交通维度"的其余行,而不是给每个 POI 顶一个假的中性档。当前 provider 的失败/未配置
> 都是**批量级**(`loadTransitPoints` 抛异常或 `!isAvailable()` 时整批返回),故 v1 的 `FAILED` 等同请求级
> 失效;单 POI 局部失败是未来 provider 支持逐点查询后才出现的情形,届时才走"失败 POI 中性降级、其余保真"。
>
> **落地前置依赖(当前代码缺,实现 Linear Ranker 前必须补):**
> [`AmapTransitPoiDetailProvider`](../backend/src/main/java/com/urbansidequest/backend/provider/route/AmapTransitPoiDetailProvider.java)
> 现有四个出口(`!isAvailable()` 未配置 / `catch RestClientException` 异常 / `transitPoints.isEmpty()` 真无站 /
> 正常增强)目前都直接返回原 `candidates`,只靠 `addWarning` 文案区分,下游拿不到状态。需:
> 1. POI 级 `transitLookupStatus`(SUCCESS / SUCCESS_EMPTY)写入 `PoiCandidateDTO`,用于 transit 列分流;
> 2. 请求级 `transportSignalAvailable`(false ⟸ UNAVAILABLE 或批量 FAILED)写入 `RouteGenerationContext`
>    /增强结果,用于跨行 transit mask(关 W_transport 的交通列 + W_personalization 的 personalizedTransitPressure);
> 3. 规约层据此分流,不再依赖 warning 文案或空 `nearestTransit` 列表。

#### W_risk(POI 自身列)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `missingInfoRisk` | ratio | [0,1] | `0.5*(!hasImage) + 0.5*isRatingMissing`(地址恒有，暂不计) | `0` | 综合缺失主列 |
| `weatherSensitive` | ratio | [0,1] | typecode 规则:室外=1 / 半室外=0.5 / 室内=0 | `0`(未知→不敏感) | 仅原料，不直接打分 |

> `weatherSensitive` 缺失默认取 0 而非 0.5:它下游进 `weatherOutdoorRisk = badWeatherSeverity *
> weatherSensitive`,坏天气下被负权扣分。取 0.5 等于让"室内外未知"的 POI 在坏天气里平白吃半档
> 户外风险,违反"缺失不当负例"。故未知按"不敏感(0)"处理,宁可漏扣不可错扣。

#### 身份标识

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `candidateRole` | one-hot | {0,1}×6 | `role ∈ {MUST_VISIT,ANCHOR,MEAL,REST,LOCAL,BACKUP}` 展开 | `BACKUP=1` | 恰一个为 1 |
| `isMustVisit` | bool | {0,1} | `mustVisit ? 1 : 0` | `0` | — |

#### POI 语义标签(由 typecode/keytag/rectag 映射，缺失统一不臆测语义)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 | missing indicator |
| --- | --- | --- | --- | --- | --- |
| `categoryGroup` | multi-hot | {0,1}×N | typecode 前缀映射到有限大类组 | 全 0(未知) | gating/原料，不直接打分 |
| `isClassic` / `isLocal` / `isPhotoFriendly` / `isNightFriendly` / `isQuiet` / `isHiddenGem` | bool | {0,1} | 映射表命中 ? 1 : 0 | `0`(不臆测该语义) | 无独立列 |

### 3.4 derivedFeature 逐字段规约

#### A. poolDerived(POI ⊗ 区域/候选池)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 |
| --- | --- | --- | --- | --- |
| `distanceNorm` | overflow | [0,1.5] | `min(distance / effectiveRadius, 1.5)`，distance 取高德 AROUND 顶层或 location 算 | `1.0`(坐标恒有，理论缺→半径边缘) |
| `isolatedDistanceNorm` | overflow | [0,1.5] | `min(到最近其他候选距离 / isolationRef, 1.5)` | `0`(单点/无邻→不算孤立) |
| `clusterConnectivity` | ratio | [0,1] | `min(neighborR 内**其他**候选数 / connectFull, 1)`(不含自身) | `0`(无邻=不可连接) |
| `distanceFatiguePressure` | ratio | [0,1] | `min(distance / fatigueRef, 1)` | `0` |
| `walkingAccessibility` | ratio | [0,1] | `0.5*clamp(1 - distance/walkRef, 0,1) + 0.5*transitScore`,`transitScore = {High:1, Medium:0.5, Low:0}` | `0.5`(中性) |
| `categoryDuplicateRisk` | ratio | [0,1] | `min((同 categoryGroup 候选数 - 1) / dupFull, 1)`,categoryGroup 全 0(未知类)→`0` | `0` |

> - `clusterConnectivity` / `categoryDuplicateRisk` 的候选计数均**排除自身**。
> - `categoryDuplicateRisk`:`categoryGroup` 全 0 的未知类**显式置 0**,不得把所有未知类别当成
>   同一类互相计数(否则一池未知 POI 会彼此误判"同质化"全体扣分)。同质化只在已知大类内统计。
> - `walkingAccessibility` 公式已闭合:步行距离分量与交通档分量各占 0.5;落地可按数据再调权重。
> - `clusterConnectivity` 是**正向**可达性,W_distance 给正权(连接好→降距离成本);
>   `isolatedDistanceNorm` / `distanceFatiguePressure` / `categoryDuplicateRisk` 是负向,负权扣分。

#### B. requestCross(POI ⊗ 请求)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 |
| --- | --- | --- | --- | --- |
| `interestMatchRatio` | ratio | [0,1] | `|interestTags| == 0 ? 0 : |request.interestTags ∩ POI.matchedInterestTags| / |request.interestTags|` | `0`(无 interestTags→无短期偏好，不加分) |

#### C. envCross(POI ⊗ 环境/路线时间;天气源缺失→severity=0→整列 0，不罚)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 |
| --- | --- | --- | --- | --- |
| `weatherOutdoorRisk` | ratio | [0,1] | `badWeatherSeverity * weatherSensitive` | `0`(无天气源→不罚) |
| `heatFatigueRisk` | ratio | [0,1] | `heatLevel * distanceFatiguePressure` | `0` |
| `rainTransportRisk` | ratio | [0,1] | `rainLevel * (1 - walkingAccessibility)` | `0` |
| `closeRisk` | ratio | [0,1] | 营业内=0 / 确定闭店=1 / opentime 缺失=`0.2`(小先验风险) | `0.2`(opentime 缺失) |
| `nightMatch` | bool | {0,1} | `routeTimeStructure.isNight * isNightFriendly` | `0` |
| `mealMatch` | ratio | [0,1] | `routeTimeStructure.mealWindow * isMealCandidate` | `0` |

> `closeRisk` 的 opentime 缺失按铁律 1 取小先验 0.2(不当全闭=1)，避免缺营业时间的 POI 被打死。

#### D. profileCross(POI ⊗ 问卷画像，全部已乘 profileConfidence;新用户→0)

| 字段 | 类型 | 取值范围 | 规约化公式 | 缺失默认 |
| --- | --- | --- | --- | --- |
| `userInterestAffinity` | ratio | [0,1] | `(profileConfidence<=0 \|\| affinityNorm<=0) ? 0 : profileConfidence * clamp(Σ(tagAffinity[tag]*poiTagHit[tag]) / affinityNorm, 0, 1)` | `0` |
| `personalizedDistancePressure` | overflow | [0,1.5] | `profileConfidence * distanceSensitivity * distanceNorm` | `0` |
| `personalizedBudgetPressure` | overflow | [0,2.0] | `profileConfidence * budgetSensitivity * avgPriceNorm` | `0` |
| `personalizedTransitPressure` | overflow | [0,1.5] | `profileConfidence * transferSensitivity * nearestTransitDistanceNorm`;**`transportSignalAvailable=false` 时被 transit mask 关闭**(见 W_transport 节) | `0` |
| `personalizedExplorationMatch` | ratio | [0,1] | `profileConfidence * hiddenGemAffinity * isHiddenGem` | `0` |

> `profileConfidence`(问卷完成度，[0,1])是全局收缩乘子:新用户/跳过问卷→profileConfidence=0
> →整组 profileCross 自然收缩到 0，不凭空影响排序;故 `profileConfidence` / `isNewUser` 不单列。
> 三个 pressure 列继承乘子内 distanceNorm/avgPriceNorm/nearestTransitDistanceNorm 的 overflow 上限。

> 规约化示例(挑三个看公式形态):
> ```text
> ratingNorm                   = clamp(business.rating / 5, 0, 1)            # ratio，缺失→0.5
> distanceNorm                 = min(distance / effectiveRadius, 1.5)        # overflow，分母=有效半径
> personalizedDistancePressure = profileConfidence * distanceSensitivity * distanceNorm  # 乘子收缩
> ```

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

## 5. 八个评分行职责（已对齐 2.x 冻结字段）

### 5.1 W_interest

衡量当前 POI 是否命中**本次请求**的兴趣(短期)。长期口味在 W_personalization。

候选使用字段：

```text
interestMatchRatio
```

> `interestMatchRatio`(request.interestTags ⊗ POI)是 derivedFeature。
> 长期兴趣 `userInterestAffinity` 归 W_personalization，不在本行(避免短期/长期双算)。

### 5.2 W_goal

衡量当前 POI 对本次路线目标的贡献。

候选使用字段：

```text
isClassic
isLocal
isPhotoFriendly
isNightFriendly
isQuiet
isHiddenGem
nightMatch
mealMatch
```

> 语义 bool 是 poiFeature 列，权重由 Delta_goal(routeGoal) 切换;`nightMatch/mealMatch`
> 是 derivedFeature 的时间窗 cross。本行不直接消费 routeGoal(routeGoal 只走 Delta)。

### 5.3 W_quality

衡量 POI 自身信息质量和可信度。

候选使用字段：

```text
ratingNorm
isRatingMissing
hasImage
```

> 已删 hasDescription/isDescriptionMissing(description 恒非空)、brandTrust/sourceCompleteness
> (重叠且误伤景点)。综合缺失走 W_risk.missingInfoRisk，不在本行重复。

### 5.4 W_transport

衡量 POI 在当前交通组合下是否可达。

候选使用字段：

```text
transitHigh
transitMedium
transitLow
nearestTransitDistanceNorm
walkingAccessibility
rainTransportRisk
```

> `transportProfile` 不当列(只走 Delta_transport);`walkingAccessibility` 是 poolDerived，
> `rainTransportRisk` 是 envCross,均归本行。

### 5.5 W_distance

衡量距离、绕行、孤立点和体力成本。

候选使用字段：

```text
distanceNorm
isolatedDistanceNorm
clusterConnectivity
distanceFatiguePressure
heatFatigueRisk
```

> 全部是 derivedFeature(poolDerived + envCross),非 poiFeature。`walkingSegmentRisk`
> 已改名 `distanceFatiguePressure`(预筛无前后点 segment);`heatFatigueRisk` 是 envCross。

### 5.6 W_budget

衡量当前 POI 对预算的压力。

候选使用字段：

```text
avgPriceNorm
isPriceMissing
isFree
expensivePoiRisk
```

> `budgetLevel` 不当列(只走 Delta_budget,并作 avgPriceNorm 的 budgetCap 基准);
> `budgetNorm` 即 avgPriceNorm,`ticketPriceNorm` 已剔除(无门票字段)。
> 本行主要作用在 `categoryGroup` 为消费类的 POI 上,景点 cost 缺失按中性处理。

### 5.7 W_risk

衡量闭店、天气、同质化、缺信息等风险。

候选使用字段：

```text
closeRisk
weatherOutdoorRisk
categoryDuplicateRisk
missingInfoRisk
```

> `weatherSensitive` 只是 POI 侧原料，晴天雨天取值一样;真正进 W_risk 的是
> `weatherOutdoorRisk = badWeatherSeverity * weatherSensitive`(derivedFeature)。

### 5.8 W_personalization

衡量用户长期画像和短期行为对当前 POI 的个性化偏置。

候选使用字段：

```text
userInterestAffinity
personalizedDistancePressure
personalizedBudgetPressure
personalizedTransitPressure
personalizedExplorationMatch
```

> 全部是 2.3/2.5 的 profileCross(画像 ⊗ POI),均已乘 `profileConfidence` 收缩,
> 故 profileConfidence/isNewUser 不单列。`userCrowdTolerance`(无 crowd 数据)、
> 行为聚合(dislike 等)v1/v2 reserved，不在本行。

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

缺失重叠项:
  W_quality 的 hasImage / isRatingMissing(具体缺失)与 W_risk.missingInfoRisk(综合缺失)
  存在重叠，给 missingInfoRisk 主权重、具体列权重小，避免对同一缺失重复放大。
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
  + Delta_budget(budgetLevel)         # budgetLevel 待前端新增，落地前 Delta_budget 不生效
  + Delta_time(routeTimeStructure)
```

动态增量只改变已有行列权重，不新增含义不清的临时参数。

> 机制约束:只有"小基数离散枚举"(routeGoal/transportProfile/budgetLevel/routeTimeStructure)
> 能驱动 Delta 切换 `W`。`paceLevel` 不属于 Linear Ranker(交给 Sampler/路线编排层)，不设 Delta_pace。
> 已删除 `Delta_userProfile`:用户连续画像不改矩阵，一律作为
> `derivedFeature` 的 cross 列(画像 ⊗ POI)进入 `X`，由固定的 `W_personalization` 行打分。
> 原则一句话:小枚举切矩阵，连续与大基数进向量。

示例：

```text
transportProfile = WALK_ONLY:
  W_distance.distanceNorm 更负
  W_distance.isolatedDistanceNorm 更负
  W_distance.distanceFatiguePressure 更负
  W_transport.transitHigh 加分降低

routeGoal = LOCAL:
  W_goal.isLocal 更正
  W_goal.isClassic 可略降或不变
  W_goal.isHiddenGem 可略升

budgetLevel = LOW:
  W_budget.avgPriceNorm 更负
  W_budget.isFree 更正
  W_budget.expensivePoiRisk 更负
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

budgetLevel=LOW + 高消费餐厅:
  应被预算行和风险行共同压低。

NIGHT + 夜游适配 + 交通可达:
  应高于闭店风险高的普通点。

无图无评分但强匹配:
  缺图、缺评分但兴趣/目标强命中的 POI，不应被 missingInfoRisk 过度打死;
  质量缺失只做温和折扣，匹配度仍应让它保持可入选。
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
