# 路线生成设计

## 目标

第一版路线生成先稳定建立后端流程和代码边界，不直接让 App 调高德，也不让 LLM 决定路线。后端负责把区域、交通组合、路线目标、兴趣点和必去点编排成可解释的路线 A/B/C。

## 核心流程

```text
提交结构化路线请求
  -> 校验请求
  -> 解析区域
  -> 加载兴趣标签映射
  -> 查询 POI 候选池
  -> 补 POI 详情
  -> 构建两点交通成本图
  -> 生成候选路线
  -> 约束过滤和路线目标评分
  -> 选择 A/B/C
  -> 返回路线结果
```

## 模式边界

- Pipeline：`RouteGenerationStep` 串起生成流程，每一步只读写 `RouteGenerationContext`。
- Strategy：交通成本和路线目标评分独立成策略，后续接高德路径规划时替换策略实现。
- Specification：路线可执行约束独立成 `RouteConstraint`，用于解释路线为什么失败。
- Provider / Adapter：POI 候选和 POI 详情通过 provider 隔离，后续对接高德 Web API。
- Cache Aside：POI 和交通成本先查本地缓存，缺失或过期时再调用高德。

## 策略配置

出行组合放后端枚举 `TransportProfile`。枚举内同时定义允许的分段交通方式和默认范围半径：

| 出行组合 | 短时 | 半日 | 一日 |
| --- | ---: | ---: | ---: |
| `WALK_ONLY` | 1500m | 2500m | 4000m |
| `WALK_SUBWAY` | 2500m | 4000m | 8000m |
| `BIKE_SUBWAY` | 3000m | 5000m | 10000m |
| `WALK_TAXI` | 4000m | 6000m | 12000m |

路线目标放后端枚举 `RouteGoal`：

- `STEADY`：稳妥省心。
- `CLASSIC`：经典必看。
- `LOCAL`：地道烟火。
- `LOW_BUDGET`：低预算。
- `NIGHT`：夜游。
- `PHOTO`：拍照出片。

兴趣标签放数据库表 `interest_tag_catalog`，用于维护兴趣标签和高德 `types` / `keywords` 的映射。

## 当前实现边界

当前代码已经建立接口和 Pipeline 骨架：

- `POST /api/routes/requests`
- 请求参数：`RouteGenerateParam`
- 返回结果：`RouteGenerationVO`
- 区域策略：自动范围和手动 polygon，行政区组合暂未开放。
- POI：本地确定性候选 provider，后续替换为高德 polygon 搜索和 detail 补全。
- 交通：本地启发式成本策略，后续替换为高德 walking / transit / bicycling / driving。
- 路线：先生成确定性 A/B/C，后续接入真实候选池和交通成本缓存。

## 后续落地顺序

1. 接入高德 `/v3/place/polygon`，用 `interest_tag_catalog` 生成 POI 候选池。
2. 接入高德 `/v3/place/detail`，补营业时间、评分、人均和风险信息。
3. 增加 `route_segment_cost_cache` 的读写，接入高德路径规划接口。
4. 将路线生成从同步返回升级为异步任务，落库 `route_requests`、`generated_routes` 和 `route_stops`。
5. 用模板或 LLM 对解释文案做润色，但路线决策仍由规则和策略决定。
