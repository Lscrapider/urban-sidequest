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
avgPriceNorm = min(avgPriceCent / budgetCapCent, 2.0)
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
