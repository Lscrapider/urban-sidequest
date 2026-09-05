# 路线生成异步化设计

## 目标

将 `POST /api/routes/requests` 从最长数分钟的同步请求改为快速受理的异步任务，避免 Android 客户端超时后服务端在写回响应时出现 `Broken pipe`。用户仍可在路线库查看生成进度和最终结果。

## 范围

- 保持请求地址、请求体和最终 `RouteGenerationVO` 数据结构不变。
- `POST /api/routes/requests` 成功受理后返回 HTTP `202 Accepted`，响应为状态 `PENDING` 的 `RouteGenerationVO`。
- 复用现有 `route_generation_history`、状态枚举、`GET /api/routes/history` 与 `GET /api/routes/history/{requestId}`，不增加消息队列或任务表。
- Android 提交后展示“路线生成已提交”，路线库继续使用现有的 `PENDING`、`GENERATING`、`SUCCESS`、`FAILED` 展示能力。

不包含：服务重启后的任务续跑、任务取消、推送通知、多实例分布式调度或消息队列。

## 方案选择

当前部署为单个 Spring Boot 服务，采用受限的进程内任务执行器：同时执行 2 个路线生成任务，队列最多容纳 8 个待执行任务。队列满时拒绝受理并返回 `503 Service Unavailable`，不让超长任务无限堆积。

该方案的任务状态会立即持久化，因此用户可看到已提交或生成中的记录；但服务在执行期间重启时，内存中的任务不会续跑。持久化队列与重启续跑属于后续独立改造。

## 接口契约

### 创建请求

```http
POST /urban-api/api/routes/requests
Content-Type: application/json
Authorization: Bearer <token>
```

成功受理：

```http
202 Accepted
```

```json
{
  "requestId": "<uuid>",
  "candidateSetId": "<uuid>",
  "status": "PENDING",
  "area": {
    "areaMode": "RADIUS",
    "areaLabel": "待生成路线区域",
    "cityName": "北京市",
    "center": { "longitudeGcj02": 116.397, "latitudeGcj02": 39.908 },
    "radiusMeters": 3000,
    "polygonGcj02": [],
    "description": "正在生成路线"
  },
  "routes": [],
  "warnings": [],
  "generationStage": "queued",
  "activeRouteCode": null,
  "executionStatus": "GENERATED"
}
```

任务队列已满：

```http
503 Service Unavailable
```

接口不再等待路线生成完成，也不再把最终路线放在该次 POST 响应中。

### 查询进度与结果

继续使用现有接口，无新增端点：

- `GET /api/routes/history`：显示历史列表中的生成中记录。
- `GET /api/routes/history/{requestId}`：返回当前状态、阶段和最终路线；生成中时 `routes` 为空。

## 状态与数据流

```text
POST 受理
  -> 写入空路线历史占位记录（PENDING / queued）
  -> 提交到 routeGenerationTaskExecutor
  -> 立即返回 202

后台任务
  -> GENERATING / 各 pipeline stage
  -> SUCCESS + 真实路线记录
     或 FAILED + 最后失败阶段

Android
  -> 显示“已提交”提示
  -> 刷新路线库
  -> 使用既有历史详情接口查看进度或结果
```

## 后端设计

### 任务执行边界

新增一个具备明确技术职责的 `RouteGenerationTaskRunner`：它只接收已创建的 `RouteGenerationContext` 并调用现有 `RouteGenerationPipeline`。Controller 不持有任务线程，业务入口仍由 `RouteGenerationService` 负责。

新增受限的 `routeGenerationTaskExecutor`：

- 核心线程数：2
- 最大线程数：2
- 队列容量：8
- 队满时拒绝提交

`RouteGenerationServiceImpl` 的提交流程：

1. 创建 `candidateSetId` 与 `RouteGenerationContext`。
2. 用既有 `RouteGenerationConverter` 生成 `PENDING / queued` 的空路线响应。
3. 在 `route_generation_history` 写入一条 `route_code = NULL` 的占位记录。
4. 提交后台任务；若执行器拒绝，删除该占位记录并以可识别的服务不可用异常结束请求。
5. 返回已受理响应。

### 历史记录持久化

现有表允许 `route_code` 为空，可不做数据库迁移。

- 新增 Mapper/Manage 的“插入生成占位记录”能力，写入请求标识、用户、区域、状态、阶段和完整的空路线 JSON 快照。
- 现有 pipeline 在每个阶段调用的 `updateGenerationState` 将更新该占位记录的状态和阶段。
- pipeline 成功后沿用现有真实路线 upsert；清理无效记录时一并删除 `route_code IS NULL` 占位记录。
- 生成失败且没有路线时保留占位记录，以便路线库显示失败状态。

### 错误处理

- 执行器拒绝：返回 503，且不会留下无任务对应的 PENDING 记录。
- pipeline 内部异常：沿用已有状态写入逻辑，最终在历史记录中显示 `FAILED`。
- 后台任务只记录带 `requestId` 的服务端异常，不尝试向原始 HTTP 连接写错误响应。
- 不修改全局 `Broken pipe` 处理策略；异步受理后该长连接写回场景不再发生。

## Android 设计

- `RouteApi.generateRoute` 使用既有常规读取超时；移除路线生成专用的 5 分钟读取超时。
- `UrbanSidequestViewModel.submitRouteGeneration` 收到 `202 / PENDING` 后保留 `RouteGenerationNotice.Submitted`，不写入 `latestRouteGeneration`，也不显示“今日路线已生成”。
- 提交成功后刷新路线历史；现有 `RoutesScreen` 已能安全渲染空路线的 `PENDING` / `GENERATING` 记录和阶段文案，因此不增加新的页面或视觉资产。
- 用户打开路线库后，由既有的历史刷新和详情接口查看实时状态与最终结果。

## 兼容性

- Android 的 HTTP 成功范围已接受全部 `2xx`，因此 `202` 无需新增网络库或协议分支。
- 已有最终路线、历史详情、开始路线和完成路线接口不改变。
- 已有 `RouteGenerationVO`、`RouteRequestStatus` 及路线库状态文案复用，不新增同义状态或前端参数。

## 验证标准

1. 提交接口在不执行完整 pipeline 的情况下返回 `202` 与 `PENDING / queued` 响应。
2. 受理后历史详情能够读取空路线占位记录。
3. 后台 pipeline 运行时，历史状态可从 `PENDING` 更新为 `GENERATING` 及具体阶段。
4. 成功后历史详情只包含真实路线记录，不包含空占位记录。
5. 执行器拒绝时返回 503，且不残留占位记录。
6. Android 收到受理响应后展示“已提交”而非“已完成”，并能在路线库中查看生成中状态。
