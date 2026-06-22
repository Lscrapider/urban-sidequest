# Route X 高德 Typecode 多样性特征改动清单

> 归档状态：问题解决记录，日期 2026-06-22。当前实现口径请以 `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RouteInputFeatureExtractor.java` 和 `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceFeatureSchema.java` 为准。
>
> 本清单定义的 4 个 `typecode` 特征已落入当前 `route_pref_v3` 训练输入；它不改变线上 serving 排序。

本文记录 Route X 增加高德 `typecode` 级路线同质性特征的精确落地方案。目标是补足 `primaryCategoryGroup` 粒度过粗的问题，让训练输入能区分：

```text
5 个 SCENIC，但分别是公园 / 纪念馆 / 景区 / 水族馆 / 城市广场
5 个 SCENIC，且全部是城市广场 110105
```

该改动服务路线偏好模型训练链路，不直接改变线上 serving 排序。

## 0. 决策状态

已冻结：

- Route X 增加 4 个高德 `typecode` 相关特征。
- 使用完整高德 `typecode` 作为 v1 相同类型判断依据。
- 空 `typecode` 使用逐 stop 唯一 sentinel，避免缺失值互相合并。
- 正式进入训练样本时必须处理 `featureSchemaVersion`。
- 本文只定义训练输入 X，不改 serving 排序。

待确认：

- 线上短期治理“五个同 typecode 连续堆叠”时，是直接进入 route scoring 扣分，还是作为独立 rerank penalty。
- 这两种做法都属于 serving 链路，不在本文 Route X 特征清单内冻结。

## 1. 当前事实

当前 `RouteInputFeatureExtractor` 已在 `routeDerivedVector` 里计算 category 级多样性：

```text
categoryDiversityRatio
dominantCategoryRatio
consecutiveSameCategoryMaxNorm
```

这些字段基于 `PoiSemanticProfile.primaryCategoryGroup`，因此只能看到 `SCENIC / FOOD / COFFEE` 等大类。对“5 个城市广场”这类路线，它已经能给出：

```text
dominantCategoryRatio = 1.0
consecutiveSameCategoryMaxNorm = 1.0
```

但它不能区分“5 个不同类型景点”和“5 个同一高德细类景点”。所以新增字段是细粒度补强，不是替代 category 三件套。

`RouteStopDTO` 不包含 `typecode`，但 extractor 已能通过候选池取回：

```text
stop.stopId()
  -> RouteStopIdSupport.poiIdFromStopId(stopId, routeCode)
  -> FeatureSource.candidatesByPoiId().get(poiId)
  -> PoiCandidateDTO.typecode()
```

因此不需要修改 `RouteStopDTO`、前端 VO 或路线展示 DTO。

## 2. 改动范围

正式实现时，核心逻辑放在：

```text
backend/src/main/java/com/urbansidequest/backend/handler/route/training/RouteInputFeatureExtractor.java
```

需要注意：新增训练输入字段会改变 `routeInput` shape。正式生成训练样本时必须同步 bump：

```text
backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceFeatureSchema.java
```

这和“只改 extractor”存在边界冲突。若只是本地试算，可以暂不 bump；若进入训练样本或持久化数据，必须 bump schema version，避免新旧样本混训。

本次方案不包含：

- 不修改 `RouteStopDTO`。
- 不修改 serving 排序、composer、召回或 rerank。
- 不新增兴趣标签。
- 不把高德 xlsx 纳入改动。
- 不把 debug 统计写入训练 X。

## 3. 新增字段

新增字段放入 `routeDerivedVector`，字段命名与现有 category 三件套保持同构：

| 字段 | 含义 | 取值 |
| --- | --- | --- |
| `amapTypecodeDiversityRatio` | 路线内不同高德 `typecode` key 数 / stop 数 | 0.0 - 1.0 |
| `dominantAmapTypecodeRatio` | 出现最多的高德 `typecode` key 数 / stop 数 | 0.0 - 1.0 |
| `consecutiveSameAmapTypecodeMaxNorm` | 最长连续相同高德 `typecode` key 数 / stop 数 | 0.0 - 1.0 |
| `missingAmapTypecodeRatio` | 缺失 `typecode` 的 stop 数 / stop 数 | 0.0 - 1.0 |

示例：

```text
110105, 110105, 110105, 110105, 110105
amapTypecodeDiversityRatio = 0.2
dominantAmapTypecodeRatio = 1.0
consecutiveSameAmapTypecodeMaxNorm = 1.0
missingAmapTypecodeRatio = 0.0
```

```text
110105, 110101, 110204, 110208, 110102
amapTypecodeDiversityRatio = 1.0
dominantAmapTypecodeRatio = 0.2
consecutiveSameAmapTypecodeMaxNorm = 0.2
missingAmapTypecodeRatio = 0.0
```

## 4. Typecode key 规则

v1 使用完整高德 `typecode` 判断是否相同，不使用前缀。

原因：

- 完整 6 位 `typecode` 能直接捕捉 `110105` 城市广场连续堆叠。
- 前缀会把城市广场、公园、水族馆等归到更粗粒度，容易误伤。
- 若未来需要“近似同类”能力，再单独增加 prefix 级特征，不在本次混入。

本特征的唯一来源是：

```text
PoiCandidateDTO.typecode()
```

也就是高德返回的 POI 实际 `typecode`。禁止使用以下字段替代：

- 召回计划里的 `amapTypeCodes`。
- 兴趣标签目录里的 `amapTypeCodes`。
- 语义映射里的 `exactTypecodes` / `amapTypePrefixes`。
- `request.interestTags` 或 POI 的 `matchedInterestTags`。

`typecode` key 只做最小规范化：

```text
key = trim(candidate.typecode())
```

不拆分、不选代表 token、不按父级归并、不按前缀归并、不用语义映射补值。如果高德返回值为空，按第 5 节的缺失策略处理。

## 5. 空值策略

不能把空 `typecode` 全部当成同一类，否则数据缺失会被误判为“高度重复”。

推荐策略：

```text
每个空 typecode 使用唯一 sentinel：
__MISSING_AMAP_TYPECODE__<stopIndex>
```

这样空值之间永远不相等：

```text
空, 空, 空
amapTypecodeDiversityRatio = 1.0
dominantAmapTypecodeRatio = 1 / 3
consecutiveSameAmapTypecodeMaxNorm = 1 / 3
missingAmapTypecodeRatio = 1.0
```

这表示“无法证明它重复”，同时通过 `missingAmapTypecodeRatio` 暴露数据质量风险。

混合示例：

```text
110105, 空, 110105
amapTypecodeDiversityRatio = 2 / 3
dominantAmapTypecodeRatio = 2 / 3
consecutiveSameAmapTypecodeMaxNorm = 1 / 3
missingAmapTypecodeRatio = 1 / 3
```

## 6. 实现清单

### 6.1 RouteInputFeatureExtractor

在 `routeDerivedVector(...)` 内 category 三件套附近增加 typecode 三件套和缺失率：

```text
List<String> amapTypecodeKeys = this.amapTypecodeKeys(route, source);
long missingCount = amapTypecodeKeys.stream()
        .filter(key -> key.startsWith(MISSING_AMAP_TYPECODE_PREFIX))
        .count();
vector.put("amapTypecodeDiversityRatio", stopCount == 0 ? 0d : new LinkedHashSet<>(amapTypecodeKeys).size() / (double) stopCount);
vector.put("dominantAmapTypecodeRatio", stopCount == 0 ? 0d : this.dominantValueCount(amapTypecodeKeys) / (double) stopCount);
vector.put("consecutiveSameAmapTypecodeMaxNorm", stopCount == 0 ? 0d : this.maxConsecutiveValueCount(amapTypecodeKeys) / (double) stopCount);
vector.put("missingAmapTypecodeRatio", stopCount == 0 ? 0d : missingCount / (double) stopCount);
```

`missingAmapTypecodeRatio` 必须从 `amapTypecodeKeys` 里数 sentinel（`MISSING_AMAP_TYPECODE_PREFIX` 开头）得到，**不要**另写一个独立的 `missingAmapTypecodeCount(route, source)` 再判一次"空"——否则两处"什么算空"的定义会漂移、缺失率和 sentinel 数对不上。"是否为空"只在 `amapTypecodeKeys` 里判一次，作为单一来源。

不要复制一套 typecode 专用计数逻辑。原有：

```text
dominantCategoryCount(...)
maxConsecutiveCategoryCount(...)
```

建议泛化为：

```text
dominantValueCount(List<String> values)
maxConsecutiveValueCount(List<String> values)
```

然后 category 和 typecode 共用同一套计数 helper。算法不变，只改内部私有 helper 名称。

新增 `amapTypecodeKeys(...)`：

```text
定义常量：MISSING_AMAP_TYPECODE_PREFIX = "__MISSING_AMAP_TYPECODE__"

for each stop (index):
  poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode())
  candidate = source.candidatesByPoiId().get(poiId)
  key = candidate == null ? null : trim(candidate.typecode())
  if key 为空:
    key = MISSING_AMAP_TYPECODE_PREFIX + index   // 唯一 sentinel，永不互相分组
  add key
```

缺失数不另建方法，直接在调用处数 `amapTypecodeKeys` 里以 `MISSING_AMAP_TYPECODE_PREFIX` 开头的 key（见 6.1 上方代码），"是否为空"只在这里判一次。

### 6.2 Feature schema

当前确认该 schema 尚未上线，也没有形成可训练样本，因此本次新增字段直接折入当前版本：

```text
RoutePreferenceFeatureSchema.VERSION
```

保持：

```text
route_pref_v3
```

后续如果已经有同名 schema 的样本进入训练池，再新增或调整字段时必须提升到下一版本，避免不同 shape 的样本混训。

## 7. 验证清单

优先使用现有测试文件，不新建测试文件。新增测试方法前按 AGENTS.md 先与负责人确认。

确认后，在 `RouteInputFeatureExtractorTest` 内补充测试方法，覆盖：

```text
case 1: 5 个 stop 全是 110105
  amapTypecodeDiversityRatio = 0.2
  dominantAmapTypecodeRatio = 1.0
  consecutiveSameAmapTypecodeMaxNorm = 1.0
  missingAmapTypecodeRatio = 0.0

case 2: 5 个 stop 全是 SCENIC，但 typecode 不同
  category 级 dominant 仍可能是 1.0
  dominantAmapTypecodeRatio 明显低于 1.0
  consecutiveSameAmapTypecodeMaxNorm 明显低于 1.0

case 3: 多个 stop 缺失 typecode
  缺失项不互相合并
  missingAmapTypecodeRatio 正确反映缺失比例
```

最小验证命令：

```bash
mvn -Dtest=RouteInputFeatureExtractorTest test
```

如需回放大雁塔场景，可再跑已有 full-chain manual 方法，重点看 `08-route-x-features.json` 中新字段是否把 5 广场线标为：

```text
amapTypecodeDiversityRatio = 0.2
dominantAmapTypecodeRatio = 1.0
consecutiveSameAmapTypecodeMaxNorm = 1.0
missingAmapTypecodeRatio = 0.0
```
