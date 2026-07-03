# Route History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保存每次生成的 3-5 条路线为一行历史记录，支持按用户回显、设置唯一进行中路线，并在地图中渲染整组或单条进行中路线。

**Architecture:** 后端在现有 raw snapshot 入库步骤附近同步写入产品侧路线历史快照表，保存校准后和硬拒绝过滤后的最终 `RouteGenerationVO`。Android 继续复用 `RouteGeneration.routes` 的地图渲染结构，新增历史列表、历史详情加载和按 `activeRouteCode` 单条渲染的入口。

**Tech Stack:** Spring Boot + MyBatis Plus + PostgreSQL JSONB；Android Kotlin + Jetpack Compose + AMap；不新增单元测试，使用现有编译/检查命令验证。

---

## File Structure

- Create `database/migrations/V14__route_generation_history.sql`: 新建产品侧路线历史快照表和唯一进行中索引。
- Create `backend/src/main/java/com/urbansidequest/backend/domain/enums/RouteExecutionStatus.java`: 历史执行状态枚举。
- Create `backend/src/main/java/com/urbansidequest/backend/domain/po/RouteGenerationHistoryPO.java`: 历史快照持久化对象，负责 JSON 序列化/反序列化。
- Create `backend/src/main/java/com/urbansidequest/backend/mapper/RouteGenerationHistoryMapper.java`: insert/upsert、分页查询、详情查询、设置进行中。
- Create `backend/src/main/java/com/urbansidequest/backend/manage/RouteGenerationHistoryManage.java`: 数据访问封装。
- Create `backend/src/main/java/com/urbansidequest/backend/domain/param/RouteActiveParam.java`: 设置进行中路线请求。
- Create `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteHistoryGroupVO.java`: 历史列表行 VO。
- Create `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteHistoryRouteSummaryVO.java`: 历史行内路线摘要 VO。
- Create `backend/src/main/java/com/urbansidequest/backend/service/RouteHistoryService.java` and `backend/src/main/java/com/urbansidequest/backend/service/impl/RouteHistoryServiceImpl.java`: 历史查询、详情、设置进行中。
- Modify `backend/src/main/java/com/urbansidequest/backend/controller/RouteGenerationController.java`: 增加历史 API。
- Modify `backend/src/main/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStep.java`: 在 raw snapshot 入库附近同步保存路线历史。
- Modify `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteGenerationVO.java`: 增加 `activeRouteCode` 和 `executionStatus`，默认生成态可为空/`GENERATED`。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/domain/model/RouteModels.kt`: 增加历史模型和 `RouteGeneration.activeRouteCode/executionStatus`。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/data/api/RouteApi.kt`: 增加历史 API 调用与解析。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/data/route/RouteRepository.kt`: 暴露历史查询、详情、设置进行中。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/MainActivity.kt`: 管理历史地图打开模式和异步加载。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/feature/routes/RoutesScreen.kt`: 改为历史一行包含 3-5 条路线。
- Modify `android-app/app/src/main/java/com/urbansidequest/app/feature/mapselect/MapSelectScreen.kt`: 支持按 `initialVisibleRouteCode` 单条渲染。

---

### Task 1: Backend Schema

**Files:**
- Create: `database/migrations/V14__route_generation_history.sql`

- [ ] **Step 1: Add route history table**

```sql
CREATE TABLE route_generation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    candidate_set_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    area_label VARCHAR(256) NOT NULL,
    route_count INTEGER NOT NULL DEFAULT 0,
    active_route_code VARCHAR(16),
    execution_status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    generation_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_generation_history_request UNIQUE (request_id),
    CONSTRAINT ck_route_generation_history_active_status CHECK (
        execution_status <> 'IN_PROGRESS' OR active_route_code IS NOT NULL
    )
);

CREATE INDEX idx_route_generation_history_user_created_at
    ON route_generation_history (user_id, created_at DESC);

CREATE INDEX idx_route_generation_history_candidate_set
    ON route_generation_history (candidate_set_id);

CREATE UNIQUE INDEX uk_route_generation_history_user_in_progress
    ON route_generation_history (user_id)
    WHERE execution_status = 'IN_PROGRESS';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_generation_history TO urban_sidequest;
    END IF;
END $$;
```

- [ ] **Step 2: Verify migration naming**

Run: `ls database/migrations`

Expected: `V14__route_generation_history.sql` is the highest version and no duplicate `V14` exists.

---

### Task 2: Backend Domain And Persistence

**Files:**
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/enums/RouteExecutionStatus.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/po/RouteGenerationHistoryPO.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/mapper/RouteGenerationHistoryMapper.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/manage/RouteGenerationHistoryManage.java`

- [ ] **Step 1: Add execution status enum**

```java
package com.urbansidequest.backend.domain.enums;

public enum RouteExecutionStatus {
    GENERATED,
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
```

- [ ] **Step 2: Add history PO with JSON helpers**

Use `RoutePreferenceRawSnapshotPO` as the local style reference. The PO must map `route_generation_history`, store `generationJson` as `String`, expose `fromRouteGeneration(RouteGenerationVO, ObjectMapper)` and `toRouteGenerationVO(ObjectMapper)`, and throw `IllegalStateException("路线历史快照序列化失败", exception)` or `IllegalStateException("路线历史快照反序列化失败", exception)` on JSON errors.

- [ ] **Step 3: Add mapper methods**

Required mapper methods:

```java
void upsertHistory(@Param("history") RouteGenerationHistoryPO history);

List<RouteGenerationHistoryPO> selectByUserId(
        @Param("userId") UUID userId,
        @Param("pageSize") int pageSize,
        @Param("offset") int offset
);

RouteGenerationHistoryPO selectByUserAndRequestId(
        @Param("userId") UUID userId,
        @Param("requestId") UUID requestId
);

RouteGenerationHistoryPO selectActiveByUserId(@Param("userId") UUID userId);

int clearInProgressByUserId(@Param("userId") UUID userId);

int setActiveRoute(
        @Param("userId") UUID userId,
        @Param("requestId") UUID requestId,
        @Param("routeCode") String routeCode
);
```

`setActiveRoute` must update `active_route_code = routeCode` and `execution_status = 'IN_PROGRESS'`.

- [ ] **Step 4: Add manage wrapper**

Expose methods with the same business names: `upsertHistory`, `findByUserId`, `findByUserAndRequestId`, `findActiveByUserId`, `activateRoute`. `activateRoute` must call `clearInProgressByUserId` before `setActiveRoute`.

---

### Task 3: Backend Save Point And APIs

**Files:**
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/param/RouteActiveParam.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteHistoryGroupVO.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteHistoryRouteSummaryVO.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/RouteHistoryService.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/impl/RouteHistoryServiceImpl.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/domain/vo/RouteGenerationVO.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/converter/route/RouteGenerationConverter.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStep.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/controller/RouteGenerationController.java`

- [ ] **Step 1: Extend RouteGenerationVO**

Add two trailing fields so existing semantics remain intact:

```java
String activeRouteCode,
RouteExecutionStatus executionStatus
```

`RouteGenerationConverter.toRouteGenerationVO` should pass `null` and `RouteExecutionStatus.GENERATED`.

- [ ] **Step 2: Save product history next to raw snapshot**

Inject `RouteGenerationConverter` and `RouteGenerationHistoryManage` into `SaveRoutePreferenceTrainingSamplesStep`. After raw snapshot upsert and before/after sample upsert, build the final VO from the same `RouteGenerationContext` and call `routeGenerationHistoryManage.upsertHistory(...)`.

- [ ] **Step 3: Add route history query service**

`RouteHistoryService` methods:

```java
List<RouteHistoryGroupVO> listHistory(AuthenticatedUser authenticatedUser, int pageNum, int pageSize);

RouteGenerationVO getHistoryDetail(AuthenticatedUser authenticatedUser, UUID requestId);

RouteGenerationVO getActiveRoute(AuthenticatedUser authenticatedUser);

RouteGenerationVO activateRoute(AuthenticatedUser authenticatedUser, UUID requestId, RouteActiveParam param);
```

Use `pageNum` and `pageSize` naming per project backend rules. Clamp only with existing constants if present; if none exist, keep controller defaults explicit and small.

- [ ] **Step 4: Validate routeCode belongs to request**

In `activateRoute`, load the generation JSON, check `param.routeCode()` is present in `generation.routes()`, then clear other in-progress rows and update this row. If not present, throw `IllegalArgumentException("路线不属于当前历史记录")`.

- [ ] **Step 5: Add controller endpoints**

In `RouteGenerationController`:

```java
@GetMapping("/history")
public List<RouteHistoryGroupVO> history(...)

@GetMapping("/history/{requestId}")
public RouteGenerationVO historyDetail(...)

@GetMapping("/active")
public RouteGenerationVO active(...)

@PostMapping("/history/{requestId}/active-route")
public RouteGenerationVO activateRoute(...)
```

---

### Task 4: Android API And State Contract

**Files:**
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/domain/model/RouteModels.kt`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/data/api/RouteApi.kt`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/data/route/RouteRepository.kt`

- [ ] **Step 1: Add Android models**

Add:

```kotlin
data class RouteHistoryGroup(
    val requestId: String,
    val candidateSetId: String,
    val areaLabel: String,
    val createdAt: String,
    val activeRouteCode: String?,
    val executionStatus: String,
    val routes: List<RouteHistoryRouteSummary>
)

data class RouteHistoryRouteSummary(
    val routeCode: String,
    val title: String,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Int,
    val riskLevel: String
)
```

Extend `RouteGeneration` with nullable `activeRouteCode` and `executionStatus`.

- [ ] **Step 2: Add RouteApi methods**

Add:

```kotlin
suspend fun fetchRouteHistory(authorizationHeader: String): List<RouteHistoryGroup>
suspend fun fetchRouteHistoryDetail(requestId: String, authorizationHeader: String): RouteGeneration
suspend fun fetchActiveRoute(authorizationHeader: String): RouteGeneration?
suspend fun activateRoute(requestId: String, routeCode: String, authorizationHeader: String): RouteGeneration
```

Use the existing `HttpURLConnection`, timeout constants, `readBody`, and error parsing patterns.

- [ ] **Step 3: Add repository wrappers**

`RouteRepository` should get auth header once per call and expose `fetchRouteHistory`, `fetchRouteHistoryDetail`, `fetchActiveRoute`, `activateRoute`.

---

### Task 5: Android History UI And Map Entry

**Files:**
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/MainActivity.kt`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/feature/routes/RoutesScreen.kt`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/feature/mapselect/MapSelectScreen.kt`

- [ ] **Step 1: Add map display mode in MainActivity**

Add state:

```kotlin
var mapInitialRouteCode by remember { mutableStateOf<String?>(null) }
```

When opening an entire history group, set `latestRouteGeneration` from detail and `mapInitialRouteCode = null`. When opening the active route chip, set `mapInitialRouteCode = activeRouteCode`.

- [ ] **Step 2: Load history for RoutesScreen**

Use `LaunchedEffect(Unit)` in `MainActivity` or a small state holder to fetch `routeRepository.fetchRouteHistory()` when entering `AppScreen.Routes`. Keep loading/error states explicit and pass them into `RoutesScreen`.

- [ ] **Step 3: Render each history group as one row**

Replace the current generated-route list with `LazyColumn` using stable keys:

```kotlin
items(historyGroups, key = { it.requestId }) { group ->
    RouteHistoryGroupRow(
        group = group,
        onOpenGroup = { onOpenHistoryGroup(group.requestId) },
        onOpenActiveRoute = { routeCode -> onOpenHistoryRoute(group.requestId, routeCode) }
    )
}
```

Each row shows area/time/status and 3-5 route chips. The chip matching `activeRouteCode` uses the existing Route A/action color vocabulary and label `进行中`.

- [ ] **Step 4: Support single-route map rendering**

In `MapSelectScreen`, add parameter:

```kotlin
initialVisibleRouteCode: String? = null
```

In the `LaunchedEffect(routeGeneration?.requestId, routes.size, initialVisibleRouteCode)`, resolve the route index by route code. If found, set `selectedRouteIndex` and `visibleRouteIndexes` to that one index; otherwise show default route A.

- [ ] **Step 5: Keep start route explicit**

When the user taps “开始路线” from the map sheet, call `activateRoute(requestId, selectedRoute.routeCode)` before navigating to route records/execution. Keep network errors mapped to existing generic user-facing messages.

---

### Task 6: Verification

**Files:**
- No new unit test files unless the user explicitly approves.

- [ ] **Step 1: Backend compile/check**

Run from repo root or backend module:

```bash
./gradlew :backend:compileJava
```

Expected: Java compilation succeeds.

- [ ] **Step 2: Android compile/check**

Run:

```bash
./gradlew :android-app:app:compileDebugKotlin
```

Expected: Kotlin compilation succeeds.

- [ ] **Step 3: Manual contract check**

Read the changed API paths and verify:

- `POST /api/routes/requests` writes `route_generation_history`.
- `GET /api/routes/history` returns one row per generation request.
- `POST /api/routes/history/{requestId}/active-route` clears prior `IN_PROGRESS` rows for that user.
- `GET /api/routes/history/{requestId}` returns full `RouteGenerationVO`.
- Android history row opens all routes on map; active chip opens only the selected route.

---

## Self-Review

- Spec coverage: The plan covers new table, raw-save-adjacent insertion, user-scoped history, unique in-progress route, Android history row, and map rendering modes.
- Placeholder scan: No placeholder tasks are left; implementation choices are constrained to existing project patterns.
- Type consistency: Backend uses `activeRouteCode` and `executionStatus`; Android mirrors the same names and reuses `RouteGeneration.routes`.
