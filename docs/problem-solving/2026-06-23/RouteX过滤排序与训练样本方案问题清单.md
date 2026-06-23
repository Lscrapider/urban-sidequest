# Route X 过滤排序与训练样本方案问题清单

## 背景

当前方案方向是：后端从“评价路线好坏”退回到“只挡非法路线”，体验质量交给 Route X / judge 学习。这个方向可以降低粗规则对训练样本的污染，避免模型学到“stop 越多越好”这类伪偏好。

但方案落地会影响当前路线生成链路、Route X 特征 schema 和训练样本持久化方式。本文记录已核对的代码事实、必须修正的问题、需要产品/算法拍板的决策项，以及后续建议执行顺序。

## 当前代码事实

当前主链路顺序如下：

```text
BuildCandidateRoutesStep
  -> ScoreAndSelectRoutesStep
  -> CalibrateSelectedRouteSegmentsStep
  -> SaveRoutePreferenceTrainingSamplesStep
```

当前 `ScoreAndSelectRoutesStep` 在校准之前运行，执行 3 类约束：

- `DurationConstraint`：`route.totalDurationMinutes > request.durationMinutes` 直接过滤。
- `MustVisitConstraint`：必去点缺失直接过滤。
- `DistrictBudgetConstraint`：跨片区数超过预算直接过滤。

当前 `SaveRoutePreferenceTrainingSamplesStep` 只保存校准后的 `selectedRoutes`，不会保存：

- LLM 原始候选路线全集。
- 被约束过滤掉的路线。
- 过滤原因。
- 未入选但仍合法、且未来可能获得偏好标签的候选路线。

当前 Route X 特征在 `RouteInputFeatureExtractor` 中抽取，主要用于训练样本保存和手动全链路测试输出；它不参与 `ScoreAndSelectRoutesStep` 的线上排序。

## 现存 LLM 输出校验盘点

原始方案里的硬过滤清单不只当前 3 个 `RouteConstraint`。实际落地前还需要盘点 LLM 输出映射和校准阶段已有的软处理，否则只改 `DurationConstraint`、`MustVisitConstraint`、`DistrictBudgetConstraint` 会漏掉一半合法性问题。

### 当前 composer 阶段行为

`LlmRouteCandidateComposer` 当前会把 LLM 输出映射成 `CandidateRouteDTO`，主要行为如下：

- `route.stops == null`：整条路线跳过，写 warning。
- stop 引用不存在的 `poiId`：写 warning，跳过该 stop，保留路线继续处理。
- 重复引用 POI：写 warning，跳过重复 stop，保留路线继续处理。
- `routeRole` 不在允许枚举中：写 warning，跳过该 stop，保留路线继续处理。
- 有效 stop 数量不足：整条路线跳过，写 warning。

这和“stopId 不在当前 POI pool 就硬过滤整条路线”的原始设想不完全一致。当前代码是 `warn + skip stop`，只有 skip 后有效 stop 不足时才跳过整条路线。

已确认决策：

- `poiId` 不存在：整条路线硬过滤，不进入展示，不进入 Route X 训练集。
- 非法 `routeRole`：保留 stop，但把无法识别的角色降级为 `BACKUP`，不因为角色名非法丢整条路线；`routeRole == null` 同样降级为 `BACKUP`。
- 重复 POI：保留当前 `warn + skip stop`，skip 后有效 stop 数不足再丢整条路线。

这里的非法 `routeRole` 指的是 LLM 输出了后端不认识的路线角色名，例如曾经在手动全链路测试里出现过 `SCENIC`，但当前允许的角色只有：

```text
MUST_VISIT / ANCHOR / MEAL / REST / LOCAL / PHOTO / BACKUP
```

这种情况说明“角色分类名不合法或缺失”，不代表地点本身非法，所以降级为 `BACKUP` 更符合“只挡不可展示路线”的原则。

### 当前校准阶段行为

`CalibrateSelectedRouteSegmentsStep` 当前只校准 selected 路线。已存在的硬/软处理如下：

- 某段路径规划失败且超过本地步行兜底上限：丢弃该候选路线。
- 校准后真实时长超过用户时长：只 addWarning，不丢弃。
- 校准后成功的路线会重算真实 `totalDurationMinutes` 和 `totalDistanceMeters`。

需要补齐：

- 校准后时间硬超限检查。
- 校准后空 segments / stops 与 segments 数量不匹配的不可展示路线处理。
- 校准失败原因要能进入后续训练样本或 warning 记录。

### 尚未明确 inventory 的硬过滤候选

以下硬过滤项仍需逐项落地定义：

- 空 stops：composer 已跳过整条路线。
- stopId / poiId 不在当前 POI pool：当前是 `warn + skip stop`，已确认要升级为整条路线硬过滤。
- mode 非法：当前 LLM 不直接输出 segment mode，主链路由后端 `SegmentModeResolver` / 校准阶段决定；非法 mode 更多出现在内部枚举扩展或 route stop `transportToNext` 后处理，需要补结构校验。
- segments/stops 结构不匹配：校准后应校验 `segments.size() == stops.size() - 1`。单 stop 路线边界要注意，`stops.size() == 1` 时 `segments.size() == 0` 是合法结构，不应误判。
- 校准后不可展示：路径规划失败且无兜底时已有丢弃；还需明确空 polyline / 0 距离 / 0 时长是否过滤。

## 必须处理的问题

### 1. 时间硬超限不能继续卡在校准前

方案里的时间硬超限表达的是“真实路线总耗时超过用户可用时间太多”。但当前 `DurationConstraint` 在 `CalibrateSelectedRouteSegmentsStep` 之前运行，校验的是 LLM 自报的 `totalDurationMinutes`。

这会导致两个问题：

- LLM 自报时间偏短、真实校准后严重超时的路线会被放过。
- LLM 自报时间偏长、真实校准后可接受的路线会被误杀。

结论：

- 普通超时不应在校准前过滤。
- 时间硬超限判断必须挪到校准后，或在校准后补一道硬超限检查。
- 校准后的 `totalDurationMinutes` 才应该作为硬超限依据。

已确认硬超限公式：

```text
hardLimit = durationMinutes + max(durationHardOverrunMinutes, durationMinutes * durationHardOverrunRatio)
```

已确认初始私有配置：

```yaml
route:
  scoring:
    route-constraints:
      duration-hard-overrun-minutes: 30
      duration-hard-overrun-ratio: 0.15
```

如果校准后所有候选都被硬超限过滤，已确认返回空路线列表，并在 response warning 中明确说明：`所有候选路线均因校准后硬超限被过滤`。不回退展示硬违规路线。

### 2. DistrictBudgetConstraint 应从硬过滤退役

`DistrictBudgetConstraint` 本质是空间动线/跨片区体验启发式，不是路线合法性。按“后端只挡非法，体验交给 Route X / judge”的原则，它不应该继续作为硬过滤。

建议：

- 从硬约束列表中移除 `DistrictBudgetConstraint`。
- 将跨片区使用情况改为 Route X 特征或 warning。
- 保留 district 信息用于 prompt、解释和训练特征，不再直接丢弃路线。

可考虑后续 Route X 特征：

```text
districtCountUsedNorm
districtOverBudgetCountNorm
districtTransitionPressure
```

是否新增这些特征可后续单独决策，不阻塞先移除硬过滤。

### 3. Route X 新特征必须升级 schema

如果新增以下特征，Route X 的输入 shape 会改变：

- `timeBudgetUnderuse`
- `timeBudgetOveruse`
- `physicalTravelDistanceRatio`
- `scheduledTravelDistanceRatio`
- `privateMotorTravelDistanceRatio`
- `physicalTravelDistanceNorm`
- `physicalTravelMaxSegmentDurationNorm`
- `scheduledTravelDistanceNorm`
- `scheduledTravelMaxSegmentDurationNorm`
- `privateMotorTravelDistanceNorm`
- `privateMotorTravelMaxSegmentDurationNorm`
- `travelBucketSwitchCountNorm`

当前 schema 版本为 `route_pref_v3`。新增特征后必须升级：

```text
route_pref_v3 -> route_pref_v4
```

同时需要决定旧样本处理策略：

- 丢弃旧样本。
- 回填旧样本为 v4。
- 按 schema 版本分桶训练。

建议：短期按版本分桶，v4 训练只使用 v4 样本；旧 v3 不混入 v4 训练集。

### 4. Route X 特征新增不等于数据库迁移

本轮真正要进入 Route X 的是路线特征，例如时间预算、交通压力和 mode bucket。它们应该进入现有特征向量 JSON，并通过 `featureSchemaVersion = route_pref_v4` 区分，不需要为每个新特征单独新增数据库列。

以下内容不是 Route X 特征：

```text
selected
calibrated
sampleStage
rejectionReasons
calibrationStatus
```

这些只属于样本管理/排查元信息，不应该直接喂给模型学习。模型应该学习“路线本身的 X + judge/偏好标签”，而不是学习“这条路线为什么被当前流程选中或过滤”。

结论：

- 新增 v4 特征本身不要求数据库迁移。
- 优先复用现有训练样本表里的特征 JSON、label/score、schema version 和已有 metadata/extra 结构。
- 只有未来明确要把样本流程状态长期落库，且现有 metadata/extra 放不下时，才需要新增手写迁移。

如果确实进入这类离线样本治理阶段，再考虑新增迁移，例如：

```text
database/migrations/V12__route_preference_training_sample_metadata.sql
```

但这不是 Route X v4 特征升级的前置条件。

## 已确认设计口径

### 1. 训练样本只来自可展示路线

已确认口径：

- 硬过滤路线：违反硬规则、无论如何不能给用户展示，直接丢弃，不进入 Route X 训练集，也不作为负样本。
- 可展示路线：通过硬过滤，真实返回给用户，并获得 judge/人工偏好标签后，才进入 Route X 训练集。

因此当前阶段不做“被硬过滤路线训练”，也不做“过滤原因训练”。Route X 只学习：

```text
X = 路线自身特征
Y = judge / 人工偏好标签
```

当前不考虑“合法但未展示路线”。现阶段路线链路是有几条可展示路线就输出几条，不存在一批合法路线被系统隐藏、但仍要进入训练集的机制。

结论：

- 当前训练集继续只收可展示、已校准、已获得偏好标签的路线。
- 不把 `selected`、`rejectionReasons`、`sampleStage`、`calibrated` 放入 Route X 特征。
- 不为了这些流程状态新增数据库迁移。
- 硬过滤原因只进入日志、warning 或排查 metadata。

### 2. 新交通 bucket 特征吸收旧 per-mode 比例

当前 `RouteInputFeatureExtractor` 已有类似：

- `walkDistanceRatio`
- `bikeDistanceRatio`
- `busDistanceRatio`
- `subwayDistanceRatio`
- `taxiDistanceRatio`
- `profileActualModeFitRatio`

新增 bucket 特征后会和这些 per-mode ratio 存在语义重叠。这里的“替换”不是丢掉原有交通距离信号，而是把具体交通方式的距离信号聚合进稳定 bucket。

旧 per-mode ratio 要先合并成 bucket ratio，承接原来的“交通构成占比”信号：

```text
physicalTravelDistanceRatio = (WALK + BIKE 距离) / totalSegmentDistance
scheduledTravelDistanceRatio = (BUS + SUBWAY + TRANSIT 距离) / totalSegmentDistance
privateMotorTravelDistanceRatio = (TAXI + DRIVE 距离) / totalSegmentDistance
```

然后再额外计算 bucket pressure，表达“这类交通本身累积压力有多大”：

```text
physicalTravelDistanceNorm = physicalTravelDistanceMeters / physical-distance-ref-meters
scheduledTravelDistanceNorm = scheduledTravelDistanceMeters / scheduled-distance-ref-meters
privateMotorTravelDistanceNorm = privateMotorTravelDistanceMeters / private-motor-distance-ref-meters
```

也就是说，旧特征的占比信息继续存在，只是不再按具体交通方式给模型单独开列；新增的 `*DistanceNorm` 是压力信号，不拿它替代 ratio 信号。

曾评估过两个方案：

#### 方案 A：bucket 吸收 per-mode ratio

优点：

- schema 更稳定。
- 未来新增交通方式只改 `mode -> bucket` 映射，不改模型特征。
- 减少共线和冗余。

缺点：

- 与旧 v3 样本不兼容，需要 schema 升级。

#### 方案 B：bucket 与 per-mode ratio 并存

优点：

- 保留细粒度 mode 信息。
- 迁移风险小。

缺点：

- 特征冗余。
- 未来新增交通方式仍会诱导继续加列。
- 容易违背“扩展交通组合不改 schema”的初衷。

已确认：

- v4 中用 bucket ratio 吸收旧 per-mode ratio。
- “替换/删除”只表示 v4 的特征 JSON 不再输出这些旧 key，不是删除数据库字段。
- 原有交通方式距离占比信号不能丢，必须计入对应 bucket ratio。
- 旧 v3 样本仍按 `route_pref_v3` 保留；v4 训练不混用 v3。

v4 不再输出的旧 route-level 具体交通方式比例：

```text
walkDistanceRatio
bikeDistanceRatio
busDistanceRatio
subwayDistanceRatio
taxiDistanceRatio
```

`profileActualModeFitRatio` 可以继续作为 `contextCrossVector` 里的一个标量特征保留，但实现必须改为基于 bucket 距离计算，不再依赖上述具体交通方式比例。

`TransportProfile -> bucket` 全集映射：

```text
WALK_ONLY -> physicalTravel
WALK_BUS -> physicalTravel + scheduledTravel
WALK_SUBWAY -> physicalTravel + scheduledTravel
WALK_TRANSIT -> physicalTravel + scheduledTravel
BIKE_SUBWAY -> physicalTravel + scheduledTravel
WALK_TAXI -> physicalTravel + privateMotorTravel
```

计算公式：

```text
profileActualModeFitRatio = sum(该 TransportProfile 允许 bucket 的 bucketDistanceRatio)
```

### 3. mode 切换按 bucket 统计

`travelBucketSwitchCountNorm` 需要定义“切换”口径。

已确认按 bucket 变化统计：

```text
physicalTravel -> scheduledTravel 算切换
scheduledTravel -> privateMotorTravel 算切换
BUS -> SUBWAY 不算切换，二者都属于 scheduledTravel
WALK -> BIKE 不算切换，二者都属于 physicalTravel
```

原因：

- 该特征表达的是体验割裂感，不是交通枚举变化次数。
- BUS 和 SUBWAY 的切换可能是同一公共交通体系，不一定像“步行转地铁再打车”那样割裂。

### 4. SegmentTransportMode 到 bucket 的完整映射

当前 `SegmentTransportMode` 有 7 个值：

```text
WALK / SUBWAY / BUS / TRANSIT / BIKE / TAXI / DRIVE
```

v4 需要一次性定义全集映射，避免 day-1 歧义：

```text
WALK -> physicalTravel
BIKE -> physicalTravel

BUS -> scheduledTravel
SUBWAY -> scheduledTravel
TRANSIT -> scheduledTravel

TAXI -> privateMotorTravel
DRIVE -> privateMotorTravel
```

未来新增交通方式时，只允许先映射到既有 bucket；除非确认现有 bucket 无法表达，才进入 schema 升级讨论。

### 5. 归一化是否 clamp

新增特征可能出现大于 1 或大于 2 的值，例如 10km 步行：

```text
physicalTravelDistanceNorm = 10000 / 4000 = 2.5
```

建议：

- 距离/时间压力类特征保留上限信息，但做温和 clamp。
- 初始可统一 clamp 到 `[0, 3]`，避免极端值污染训练。
- 是否 clamp 以及 clamp 上限放入私有配置。

示例：

```yaml
route:
  scoring:
    route-x:
      travel-pressure:
        norm-clamp-max: 3
```

## 建议新增 Route X v4 特征

### 时间预算

保留已有：

```text
timeBudgetUsageRatio
```

该字段口径必须钉死为：

```text
timeBudgetUsageRatio = calibratedRouteTotalDurationMinutes / requestDurationMinutes
```

实现上优先直接读取校准后的 `route.totalDurationMinutes`，不要在已校准路线里再从 `segmentMatrix` 重新累加。原因是时间硬超限也读取同一个校准后总分钟字段，两个逻辑必须同源。

只有在未校准兜底场景下，才允许退回：

```text
(estimatedStayMinutes + estimatedTravelMinutes) / requestDurationMinutes
```

它不是 `stayBudgetUsageRatio`。`stayBudgetUsageRatio` 的分母是 `duration * stay-budget-ratio`，且只表达停留时间预算，不适合作为 underuse / overuse 的基础。

`timeBudgetUnderuse` / `timeBudgetOveruse` 必须基于校准后总分钟口径，且和时间硬超限使用同一套校准后 `totalDurationMinutes`，避免过滤和特征口径不一致。

新增：

```text
timeBudgetUnderuse
timeBudgetOveruse
```

公式：

```text
timeBudgetUnderuse = max(0, targetUsageRatio - timeBudgetUsageRatio)
timeBudgetOveruse = max(0, timeBudgetUsageRatio - maxComfortUsageRatio)
```

建议私有配置：

```yaml
route:
  scoring:
    route-x:
      time-budget:
        target-usage-ratio: 0.75
        max-comfort-usage-ratio: 0.9
```

### 交通 bucket 构成与压力

新增：

```text
physicalTravelDistanceRatio
scheduledTravelDistanceRatio
privateMotorTravelDistanceRatio
physicalTravelDistanceNorm
physicalTravelMaxSegmentDurationNorm
scheduledTravelDistanceNorm
scheduledTravelMaxSegmentDurationNorm
privateMotorTravelDistanceNorm
privateMotorTravelMaxSegmentDurationNorm
travelBucketSwitchCountNorm
```

bucket 定义：

- `physicalTravel`：体力型移动 bucket，如当前的 WALK、BIKE。未来新增滑板车、共享单车等，只要语义仍是体力/低速近距移动，就映射到这里。
- `scheduledTravel`：班次/线路型交通 bucket，如当前的 BUS、SUBWAY、TRANSIT。未来新增城铁、渡轮、城际铁路等，只要依赖固定线路或班次，就映射到这里。
- `privateMotorTravel`：点到点机动交通 bucket，如当前的 TAXI、DRIVE。未来新增网约车、接驳车、自驾等，只要是点到点机动出行，就映射到这里。

当前 7 个 `SegmentTransportMode` 的映射必须按上文全集定义实现：`TRANSIT` 归 `scheduledTravel`，`DRIVE` 归 `privateMotorTravel`。

计算时先按 segment 的 `mode` 映射 bucket，再聚合该 segment 的 `distanceMeters` 和 `durationMinutes`：

- `bucketDistanceMeters`：该 bucket 下所有 segment 的距离总和。
- `bucketDistanceRatio`：`bucketDistanceMeters / totalSegmentDistanceMeters`，用于承接旧 per-mode ratio；如果 `totalSegmentDistanceMeters <= 0`，三个 bucket ratio 都取 0。
- `bucketMaxSegmentMinutes`：该 bucket 下最长单段耗时，用于表达单段压力。
- `bucketSwitchCount`：相邻 segment 的 bucket 发生变化时加 1；同 bucket 内 mode 变化不计切换，例如 BUS -> SUBWAY 不计切换。

建议私有配置：

```yaml
route:
  scoring:
    route-x:
      travel-pressure:
        physical-distance-ref-meters: 4000
        physical-segment-comfort-minutes: 25
        scheduled-distance-ref-meters: 10000
        scheduled-segment-comfort-minutes: 35
        private-motor-distance-ref-meters: 12000
        private-motor-segment-comfort-minutes: 30
        bucket-switch-ref-count: 3
        norm-clamp-max: 3
```

公式：

```text
physicalTravelDistanceRatio = physicalTravelDistanceMeters / totalSegmentDistanceMeters
scheduledTravelDistanceRatio = scheduledTravelDistanceMeters / totalSegmentDistanceMeters
privateMotorTravelDistanceRatio = privateMotorTravelDistanceMeters / totalSegmentDistanceMeters

physicalTravelDistanceNorm = physicalTravelDistanceMeters / physical-distance-ref-meters
physicalTravelMaxSegmentDurationNorm = physicalMaxSegmentMinutes / physical-segment-comfort-minutes

scheduledTravelDistanceNorm = scheduledTravelDistanceMeters / scheduled-distance-ref-meters
scheduledTravelMaxSegmentDurationNorm = scheduledMaxSegmentMinutes / scheduled-segment-comfort-minutes

privateMotorTravelDistanceNorm = privateMotorTravelDistanceMeters / private-motor-distance-ref-meters
privateMotorTravelMaxSegmentDurationNorm = privateMotorMaxSegmentMinutes / private-motor-segment-comfort-minutes

travelBucketSwitchCountNorm = bucketSwitchCount / bucket-switch-ref-count
```

## 推荐落地顺序

### 第一阶段：清理线上过滤/排序职责

目标：后端只挡非法，不用粗规则评好坏。

建议改动：

- `DurationConstraint` 改为普通超时不拦。
- 在 `CalibrateSelectedRouteSegmentsStep` 之后新增一个独立 step，例如 `FilterCalibratedRoutesStep`，专门处理校准后硬过滤。
- 校准后时间硬超限放在这个新 step 中，不折进校准逻辑，避免校准同时承担过滤和路径补全两种职责。
- 如果校准后硬过滤导致 selectedRoutes 为空，返回空路线并写明确 warning：`所有候选路线均因校准后硬约束失败被过滤`。
- `DistrictBudgetConstraint` 从硬过滤移除。
- `ScoreAndSelectRoutesStep` 不再用 `DefaultRouteGoalScoringStrategy` 做质量排序。
- 通过硬过滤后保留 LLM 原始顺序，最多取前 5。
- 明确 `DefaultRouteGoalScoringStrategy` 的处理：要么删除，要么保留但不接入主链路，并在类注释标记为旧规则，不再作为质量分来源。
- 补充 LLM 输出结构校验策略，尤其是 `poiId` 不存在、非法 `routeRole`、重复 POI、有效 stop 数不足、校准后 segments/stops 不匹配。

### 第二阶段：Route X v4 特征升级

目标：补足时间预算和交通压力表达能力。

建议改动：

- `RoutePreferenceFeatureSchema.VERSION` 升为 `route_pref_v4`。
- 新增时间预算 underuse / overuse。
- 新增交通 bucket 构成与压力特征。
- 用 bucket ratio 吸收旧 per-mode ratio，不丢交通构成信号。
- 新增私有配置并严格校验。

### 第三阶段：训练样本口径收紧

目标：保证训练样本只来自可展示路线，避免硬过滤路线污染模型。

建议改动：

- 优先保持现有训练样本表结构，v4 特征进入现有特征 JSON。
- 只把可展示路线的自身特征和 judge/偏好标签用于训练。
- 不把 `selected`、`rejectionReasons`、`sampleStage`、`calibrated` 当作 Route X 特征。
- 硬过滤路线不进入训练集，因为它们不是“体验差”的负样本，而是“不可展示”的非法样本。
- 如果只是保存硬过滤原因，放在日志、warning 或现有 metadata/extra 中用于排查；只有现有结构放不下时才考虑数据库迁移。
- 当前不扩展“合法但未展示候选路线”的训练样本范围，因为现阶段是有几条可展示路线就输出几条。

## 当前建议结论

方向是正确的：后端不应该继续用粗规则评价路线体验，Route X / judge 才应该学习“过犹不及”。

但落地前必须先解决这些事项：

1. 时间硬超限要基于校准后真实时间。
2. `DistrictBudgetConstraint` 要从硬过滤退役。
3. 新特征必须升级到 `route_pref_v4`。
4. 只有可展示且有有效偏好标签的路线才进入训练集；硬过滤路线不进 Route X。
5. v4 用 bucket ratio 吸收旧 per-mode ratio，保留交通构成占比信号，同时用 bucket pressure 表达交通压力，避免 schema 随交通方式扩展而反复变化。
6. 需要补齐 LLM 输出校验盘点，不能只改现有 3 个 `RouteConstraint`。
7. 新增 Route X 特征优先复用现有特征 JSON；数据库迁移不是 v4 的默认前置条件。
