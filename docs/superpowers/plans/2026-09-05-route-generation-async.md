# 路线生成异步化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将路线生成请求快速受理为异步任务，并复用已有路线历史向 Android 展示进度和最终结果。

**Architecture:** `POST /api/routes/requests` 先创建一条 `route_code IS NULL` 的 PENDING 历史占位记录，再交由受限的 `TaskExecutor` 执行既有 pipeline，立即返回 `202 Accepted` 与既有 `RouteGenerationVO`。最终路线仍由 pipeline 写入现有历史表并移除占位记录；Android 仅将成功受理解释为“已提交”。

**Tech Stack:** Spring Boot 3.4、MyBatis Plus、PostgreSQL、JUnit 5 / Mockito、Android Kotlin、StateFlow、Jetpack Compose。

**Spec:** `docs/superpowers/specs/2026-09-05-route-generation-async-design.md`

## Global Constraints

- 保持 `POST /api/routes/requests` 的路径、请求体和 `RouteGenerationVO` 响应结构；成功受理改为 HTTP `202 Accepted`、`PENDING`、`queued`。
- 只复用 `route_generation_history`；不新增数据库迁移、消息队列、任务表、轮询端点或 Android UI 页面。
- 任务执行器固定为 2 个并发线程、队列容量 8；拒绝受理时必须清理占位记录并返回 HTTP 503。
- 真实路线成功落库后必须删除 `route_code IS NULL` 的占位记录；生成失败时保留占位记录供历史页显示失败状态。
- Android 不新增测试依赖或测试基础设施；后端只使用已存在的 JUnit/Mockito 体系添加聚焦测试。
- 不提交、推送、建分支、暂存或覆盖用户已有未提交改动；使用 `apply_patch` 进行文件编辑。
- 代码注释、文档与用户可见文案使用中文；不改变已有业务默认值和枚举含义。

---

### Task 1: 持久化 PENDING 占位记录并用测试锁定其生命周期

**Files:**
- Modify: `backend/src/main/java/com/urbansidequest/backend/domain/po/RouteGenerationHistoryPO.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/mapper/RouteGenerationHistoryMapper.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/manage/RouteGenerationHistoryManage.java`
- Create: `backend/src/test/java/com/urbansidequest/backend/manage/RouteGenerationHistoryManageTest.java`

**Interfaces:**
- Produces: `RouteGenerationHistoryManage.createPendingHistory(RouteGenerationVO)`，写入一条 `route_code = NULL` 的历史占位记录。
- Produces: `RouteGenerationHistoryManage.deletePendingHistory(UUID candidateSetId, UUID userId)`，仅删除当前任务的空路线占位记录。
- Preserves: `upsertHistory(RouteGenerationVO)`；当最终路由非空时，`deleteRoutesNotIn` 也会删除空路线占位记录。

- [ ] **Step 1: 写入失败测试，表达“待生成记录可查询”的行为**

```java
@Test
void createsPendingHistoryWithEmptyRoutesAndQueuedStage() {
    Fixture fixture = fixture();
    RouteGenerationVO pending = pendingGeneration();

    fixture.manage().createPendingHistory(pending);

    verify(fixture.mapper()).insertPendingHistory(argThat(history ->
            history.getCandidateSetId().equals(pending.candidateSetId())
                    && history.getUserId().equals(pending.userId())
                    && history.getRouteCode() == null
                    && history.getRouteCount() == 0
                    && history.getGenerationStatus() == RouteRequestStatus.PENDING
                    && "queued".equals(history.getGenerationStage())
                    && history.getGenerationJson().contains("\\\"status\\\":\\\"PENDING\\\"")
    ));
}
```

- [ ] **Step 2: 运行失败测试并确认失败原因是方法尚不存在**

Run: `mvn -f backend/pom.xml -Dtest=RouteGenerationHistoryManageTest test`

Expected: FAIL，指出 `createPendingHistory` 或 `insertPendingHistory` 不存在。

- [ ] **Step 3: 实现最小的 PO、Mapper 与 Manage 能力**

```java
public static RouteGenerationHistoryPO fromPendingGeneration(
        RouteGenerationVO routeGeneration,
        ObjectMapper objectMapper
) {
    RouteGenerationHistoryPO po = new RouteGenerationHistoryPO();
    po.setCandidateSetId(routeGeneration.candidateSetId());
    po.setUserId(routeGeneration.userId());
    po.setAreaLabel(routeGeneration.area().areaLabel());
    po.setRouteCount(0);
    po.setGenerationStatus(routeGeneration.status());
    po.setGenerationStage(routeGeneration.generationStage());
    po.setGenerationJson(writeJson(objectMapper, routeGeneration));
    return po;
}
```

Mapper 的 `INSERT` 只写入占位记录所需字段，保留 `route_code` 为 `NULL`；`deleteRoutesNotIn` 的条件改为删除 `route_code IS NULL` 或不在最终 route code 集合中的记录。

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn -f backend/pom.xml -Dtest=RouteGenerationHistoryManageTest test`

Expected: PASS。

### Task 2: 受限后台执行器、202 受理与 503 拒绝

**Files:**
- Create: `backend/src/main/java/com/urbansidequest/backend/config/RouteGenerationTaskExecutorConfig.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/service/impl/RouteGenerationServiceImpl.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/controller/RouteGenerationController.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/config/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/urbansidequest/backend/service/impl/RouteGenerationServiceImplTest.java`
- Create: `backend/src/test/java/com/urbansidequest/backend/controller/RouteGenerationControllerTest.java`

**Interfaces:**
- Consumes: Task 1 的 `createPendingHistory` 与 `deletePendingHistory`。
- Produces: `RouteGenerationService.generate(...)` 返回状态为 `PENDING`、阶段为 `queued` 的 `RouteGenerationVO`，并且不在 HTTP 线程运行 pipeline。
- Produces: Controller 返回 `ResponseEntity<RouteGenerationVO>`，状态为 `HttpStatus.ACCEPTED`。
- Produces: `TaskRejectedException` 的统一 503 JSON 错误响应。

- [ ] **Step 1: 写入失败测试，表达快速受理与执行器拒绝的行为**

```java
@Test
void acceptsGenerationBeforeQueuedPipelineRuns() {
    Fixture fixture = fixture(new CapturingTaskExecutor());

    RouteGenerationVO accepted = fixture.service().generate(fixture.user(), fixture.param());

    assertThat(accepted.status()).isEqualTo(RouteRequestStatus.PENDING);
    assertThat(accepted.generationStage()).isEqualTo("queued");
    assertThat(accepted.routes()).isEmpty();
    verify(fixture.historyManage()).createPendingHistory(accepted);
    verifyNoInteractions(fixture.pipeline());
}

@Test
void removesPendingHistoryWhenExecutorRejectsTask() {
    Fixture fixture = fixture(task -> { throw new TaskRejectedException("full"); });

    assertThatThrownBy(() -> fixture.service().generate(fixture.user(), fixture.param()))
            .isInstanceOf(TaskRejectedException.class);

    verify(fixture.historyManage()).deletePendingHistory(any(UUID.class), eq(fixture.user().id()));
}
```

另写 Controller 测试：调用 `generate` 后断言 `response.getStatusCode()` 为 `HttpStatus.ACCEPTED` 且 body 状态为 `PENDING`。

- [ ] **Step 2: 运行失败测试并确认失败来自同步实现或缺失执行器**

Run: `mvn -f backend/pom.xml -Dtest=RouteGenerationServiceImplTest,RouteGenerationControllerTest test`

Expected: FAIL，原因是服务仍同步调用 pipeline 或 Controller 尚未返回 202。

- [ ] **Step 3: 实现受限异步调度与 HTTP 边界**

```java
taskExecutor.execute(() -> {
    try {
        this.routeGenerationPipeline.execute(context);
    } catch (RuntimeException exception) {
        LOGGER.error("异步路线生成任务异常，requestId={}", context.getRequestId(), exception);
    }
});
return pendingGeneration;
```

`RouteGenerationTaskExecutorConfig` 创建名称为 `routeGenerationTaskExecutor` 的 `ThreadPoolTaskExecutor`，核心线程数和最大线程数均为 `2`、队列容量为 `8`，使用拒绝策略抛出 `TaskRejectedException`。Service 在提交被拒绝时删除占位记录后重新抛出异常。Controller 用 `ResponseEntity.status(HttpStatus.ACCEPTED)` 返回；全局异常处理器把 `TaskRejectedException` 映射为 `503` 与中文可恢复提示。

- [ ] **Step 4: 运行聚焦后端测试并确认通过**

Run: `mvn -f backend/pom.xml -Dtest=RouteGenerationHistoryManageTest,RouteGenerationServiceImplTest,RouteGenerationControllerTest test`

Expected: PASS。

### Task 3: Android 将“受理”与“生成完成”分离

**Files:**
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/data/api/RouteApi.kt`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/UrbanSidequestViewModel.kt`

**Interfaces:**
- Consumes: 后端返回的既有 `RouteGeneration`，其初始状态为 `PENDING`。
- Produces: Android 请求读取超时回归既有 `READ_TIMEOUT_MILLIS`；成功受理后只保留 `RouteGenerationNotice.Submitted` 并刷新路线历史。
- Preserves: 既有路线库对 `PENDING` / `GENERATING` 的展示，不新增轮询、页面、图标或网络依赖。

- [ ] **Step 1: 用现有 UI 状态确认预期行为**

确认 `RouteGenerationNotice.Submitted` 的文案为“正在装载路线，也可以去路线库查看进度”，且 `RoutesScreen` 对 `PENDING` / `GENERATING` 已显示进度；不创建 Android 测试基础设施或新增依赖。

- [ ] **Step 2: 实现最小 Android 改动**

```kotlin
connection.readTimeout = READ_TIMEOUT_MILLIS
```

```kotlin
.onSuccess {
    mutableUiState.update { state ->
        state.copy(routeGenerationNotice = RouteGenerationNotice.Submitted)
    }
    refreshRouteHistory()
}
```

删除不再使用的路线生成专用五分钟超时常量；不要在受理成功后设置 `latestRouteGeneration` 或 `RouteGenerationNotice.Completed`。

- [ ] **Step 3: 编译 Android debug 变体**

Run: `./gradlew :app:compileDebugKotlin`

Working directory: `android-app`

Expected: BUILD SUCCESSFUL。

### Task 4: 集成验证与变更边界检查

**Files:**
- Modify: 仅为修复前 3 个任务的验证失败所必需的文件。

**Interfaces:**
- Consumes: Task 1–3 的实现。
- Produces: 可复现的后端测试、Android Kotlin 编译结果及干净的目标差异检查。

- [ ] **Step 1: 运行完整后端测试集**

Run: `mvn -f backend/pom.xml test`

Expected: BUILD SUCCESS，测试均通过。

- [ ] **Step 2: 重新运行 Android Kotlin 编译**

Run: `./gradlew :app:compileDebugKotlin`

Working directory: `android-app`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 审查改动范围与空白错误**

Run: `git diff --check && git status --short && git diff -- backend/src/main/java backend/src/test/java android-app/app/src/main/java docs/superpowers`

Expected: 没有空白错误；变更只涉及本计划、且不包含对已有用户改动文件的覆盖。
