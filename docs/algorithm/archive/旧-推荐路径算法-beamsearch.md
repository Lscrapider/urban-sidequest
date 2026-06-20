# 旧-推荐路径算法-beamsearch

本文档记录旧版 Beam Search 推荐路径算法，用于和当前 LLM 路线编排主线对照。当前主方案见 `docs/algorithm/推荐路径算法.md`。

## 目标

本设计用于路线生成的下一阶段：后端接入高德 Web 服务 POI 搜索，使用本地交通成本先完成选点和排序，再只对最终路线段调用高德路径规划做校准。

目标不是把高德路径规划用于全量候选点搜索，而是控制 API 调用量，同时让路线结果优先匹配用户选择。

## 总体流程

```text
提交结构化路线请求
  -> 校验请求
  -> 解析区域
  -> 加载兴趣标签映射
  -> 高德 POI 搜索生成候选池
  -> 本地启发式构建两点交通成本图
  -> Beam Search 搜索候选路线 A/B/C
  -> 高德路径规划校准最终路线段
  -> 返回路线结果和 warning
```

## 高德 POI 查询

高德文档：<https://lbs.amap.com/api/webservice/guide/api-advanced/newpoisearch>

按用户选择的区域类型选择接口：

| 区域类型 | 高德接口 | 说明 |
| --- | --- | --- |
| `AUTO_RADIUS` | `/v5/place/around` | 使用中心点和半径搜索。 |
| `MANUAL_POLYGON` | `/v5/place/polygon` | 使用用户手动框选 polygon 搜索。 |

后端不让 Android App 直接调用高德 Web 服务。高德 Web Key 只放后端配置，通过环境变量注入。

建议配置：

```yaml
amap:
  web:
    key: ${AMAP_WEB_KEY:}
    base-url: https://restapi.amap.com
```

## 代码边界

建议新增或调整以下组件：

```text
api/amap/
  AmapPoiSearchApi
  AmapRoutePlanningApi

config/
  AmapWebProperties

provider/route/
  AmapPoiCandidateProvider
  LocalPoiCandidateProvider

handler/route/search/
  BeamSearchRouteSelector
  RouteSearchState

handler/route/scoring/
  RouteScoreCalculator

handler/route/constraint/
  RouteConstraintChecker
```

职责边界：

- `api/amap`：只负责高德 HTTP 请求、响应解析、错误码归一化。
- `provider/route`：把高德 POI 结果转成内部 `PoiCandidateDTO`，并保留本地 fallback。
- `handler/route/search`：负责 Beam Search 选点和排序。
- `handler/route/scoring`：负责计算路线分数。
- `handler/route/constraint`：负责硬约束判断。
- `service`：只保留服务入口调用，不放真实生成逻辑。

## 候选 POI 生成规则

候选池先满足用户意图，再考虑路线结构。

优先级：

```text
MUST_VISIT 必去点
  > 用户选择的兴趣点
  > 系统补充的 MEAL / REST
  > BACKUP 兜底点
```

候选角色：

- `MUST_VISIT`：用户明确指定必须去的点，全部进入候选池。
- `ANCHOR`：路线核心点，例如景点、展馆、地标、街区、拍照点。
- `MEAL`：午餐、晚餐、夜宵等饭点。
- `REST`：咖啡、甜品、商场、公园等休息点。
- `LOCAL`：本地生活体验点，例如老街、菜市场、小吃街。
- `BACKUP`：生成失败、候选不足、点位过远时使用的兜底点。

用户选择的兴趣标签要单独查询和标记。比如用户选择“景点”和“咖啡”，候选池里必须保留对应标签命中的 POI；最终路线也应优先覆盖这些兴趣。

如果用户选择的兴趣太多，单条路线容量不足以全部覆盖，则按路线容量尽量覆盖，并通过路线 B/C 扩展覆盖范围。

## MEAL 和 REST 动态需求

`MEAL` 和 `REST` 不使用固定配额，而是按路线实际情况动态决定。

饭点需求：

```text
覆盖 11:30-13:30 -> 尝试补 1 个午餐点
覆盖 17:30-20:00 -> 尝试补 1 个晚餐点
同时覆盖午餐和晚餐 -> 最多补 2 个饭点
```

如果用户已选择美食类兴趣，并且候选点能承担饭点角色，则优先复用用户兴趣点，不额外重复补餐厅。

休息需求：

```text
路线时长 <= 180 分钟 -> 默认不补休息点
181-360 分钟 -> 最多补 1 个休息点
> 360 分钟 -> 最多补 2 个休息点
```

如果用户已选择咖啡、甜品、休闲类兴趣，则优先把该兴趣点视为休息点。

## 候选数量控制

高德 POI 搜索每个查询意图建议只取第一页。

建议初始参数：

```text
page_size = 10
候选池去重后总量 <= 40
```

角色维度的建议上限：

```text
MUST_VISIT：全部保留
用户兴趣点：每个兴趣优先保留若干高质量候选
MEAL：按动态需求保留
REST：按动态需求保留
BACKUP：少量兜底
```

不按固定角色配额硬塞点。最终路线容量由搜索算法和约束决定。

## 本地交通成本

第一阶段不对候选池全量调用高德路径规划。先复用本地启发式成本：

```text
GeoMath.distanceMeters(origin, destination)
  -> SegmentCostStrategy 按交通方式估算耗时
  -> SegmentCostDTO
```

当前估算模型包括步行、骑行、公共交通、打车。Beam Search 只依赖 `SegmentCostDTO`，后续可以把成本来源替换为缓存或高德路径规划，不改变搜索算法结构。

## Beam Search 路线搜索

Beam Search 用于解决“从候选 POI 中选哪些点、按什么顺序走”的问题。它不是高德路径算法。

输入：

- 候选 POI 池。
- 两点交通成本图。
- 用户时长、交通方式、路线目标、兴趣标签、必去点。
- 动态饭点和休息需求。

输出：

- 多条候选路线。
- 每条路线包含 stop 顺序、估算总时长、估算总距离、覆盖兴趣、风险和分数。

基本过程：

```text
从空路线开始
  -> 每一步尝试加入一个未使用 POI
  -> 过滤违反硬约束的路线
  -> 计算 partial route 分数
  -> 每层只保留得分最高的前 N 条
  -> 达到点数或时长边界后生成候选路线
  -> 按分数和多样性选择 A/B/C
```

硬约束：

- 必去点必须优先进入路线。
- 路线总时长不能超过用户可用时长。
- 单条路线点数控制在 3-5 个。
- POI 不能重复。

软约束：

- 用户兴趣覆盖越多越好。
- 饭点窗口匹配越好越好。
- 长路线有合理休息点。
- POI 评分更高、距离更合理、预算更匹配。
- 类别重复要扣分。
- A/B/C 要有差异。

推荐评分结构：

```text
score =
  必去点覆盖分
+ 用户兴趣覆盖分
+ 饭点匹配分
+ 休息节奏分
+ POI 质量分
+ 路线目标匹配分
- 总耗时超预算惩罚
- 绕路惩罚
- 类别重复惩罚
- 路线相似度惩罚
```

权重原则：

```text
必去点：最高，接近硬约束
用户兴趣：高
MEAL / REST：中
BACKUP：低
```

## 最终路线段校准

Beam Search 选出 A/B/C 后，再对最终路线相邻 stop 调用高德路径规划。

调用量估算：

```text
一条路线 5 个点 -> 4 段
三条路线 -> 最多 12 段
```

加上 POI 搜索，一次路线生成通常约：

```text
POI 搜索 4-8 次
路径规划 8-12 次
总计约 12-20 次 API
```

校准后处理：

- 更新每段 `distanceToNextMeters` 和 `durationToNextMinutes`。
- 更新路线总时长和总距离。
- 如果校准后明显超时，优先返回 warning。
- 后续可以增加局部替换，但第一版不强制做复杂回溯。

## PostgreSQL 缓存

高德结果先缓存到 PostgreSQL，不先放 Redis。原因是 POI 和路径成本会影响路线生成决策，放在 PostgreSQL 里更方便审计、回放、调试和后续生成结果落库关联。

缓存分两类：

```text
amap_poi_search_cache
amap_route_cost_cache 或复用 route_segment_cost_cache
```

### POI 搜索缓存

缓存一次高德 POI 搜索的请求条件和原始响应。

建议表结构：

```text
amap_poi_search_cache
  id
  search_type          -- AROUND / POLYGON
  area_hash
  types_hash
  keywords_hash
  page_num
  page_size
  request_params_json
  response_json
  poi_count
  expires_at
  created_at
  updated_at
```

唯一键：

```text
(search_type, area_hash, types_hash, keywords_hash, page_num, page_size)
```

缓存 key 含义：

- `search_type`：高德搜索方式，取 `AROUND` 或 `POLYGON`。
- `area_hash`：自动半径用中心点和半径归一化后 hash；手动 polygon 用坐标串归一化后 hash。
- `types_hash`：高德 `types` 参数归一化后 hash。
- `keywords_hash`：高德 `keywords` 参数归一化后 hash。
- `page_num` / `page_size`：分页参数。

建议只命中未过期数据：

```sql
expires_at > now()
```

POI 搜索缓存 TTL 建议为 `6-24` 小时。第一版可以先用 24 小时，后续按城市和 POI 类型调整。

### 路径成本缓存

路径成本缓存用于最终路线段校准，也可以后续用于替换本地交通成本。

项目已有 `route_segment_cost_cache`，优先复用或扩展它，而不是新建重复语义的表。若扩展现有表，建议补充：

```text
provider              -- AMAP
origin_lng
origin_lat
destination_lng
destination_lat
origin_grid
destination_grid
departure_bucket
raw_response_json
expires_at
created_at
updated_at
```

唯一键建议：

```text
(provider, mode, origin_grid, destination_grid, departure_bucket)
```

不要直接用完整经纬度做唯一键，命中率会很低。坐标应先归一化到约 `50-100` 米网格，再生成 `origin_grid` 和 `destination_grid`。

TTL 建议：

```text
步行 / 骑行：1-7 天
公交 / 驾车 / 打车：15-60 分钟
```

公交、驾车和打车受发车、拥堵和时间影响更明显，因此需要更短缓存时间。步行和骑行相对稳定，可以缓存更久。

### 缓存调用边界

API 层不直接操作数据库。建议调用链：

```text
Provider / Step
  -> Manage 查 PostgreSQL 缓存
  -> 缓存命中则返回缓存结果
  -> 缓存未命中则调用 api/amap
  -> Manage 写入缓存
  -> 转成 PoiCandidateDTO / SegmentCostDTO
```

建议组件：

```text
domain/po/
  AmapPoiSearchCachePO
  RouteSegmentCostCachePO

mapper/
  AmapPoiSearchCacheMapper
  RouteSegmentCostCacheMapper

manage/
  AmapPoiSearchCacheManage
  RouteSegmentCostCacheManage
```

`api/amap` 只负责高德 HTTP 请求和响应解析；`manage` 负责缓存读写；`provider` 和路线 step 负责业务决策。

## 失败降级

降级顺序：

```text
高德兴趣 POI 查询失败
  -> 使用其他成功查询结果继续生成
  -> 使用本地 fallback 补足基础候选
  -> 加 warning 返回
```

高德路径规划校准失败：

```text
单段失败
  -> 保留本地估算成本
  -> 加 warning
```

如果所有候选来源都失败，返回可解释错误或空路线，不返回无来源的伪结果。

## 分阶段落地

第一阶段：

- 接入高德 POI 搜索。
- 保留本地交通成本。
- 用 Beam Search 替换当前确定性 A/B/C 构建。
- 不接高德路径规划校准。

第二阶段：

- 对最终 A/B/C 相邻段接入高德路径规划校准。
- 校准失败时回退本地估算。

第三阶段：

- 完善 POI 和路径成本缓存清理策略。
- 加入局部替换和异步生成。

## 关键取舍

不建议一开始对所有候选 POI 两两调用高德路径规划。若候选 40 个、每个点连接 8 个邻居、2 种交通方式，路径规划调用量可能达到：

```text
40 * 8 * 2 = 640 次
```

这不适合同步路线生成。先用本地成本搜索，再校准最终路线段，可以把调用量控制在十几次级别。
