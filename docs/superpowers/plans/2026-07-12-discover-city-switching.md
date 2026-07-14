# 发现页城市切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 发现页默认按定位确定城市，同时允许用户随时手动切换地区；拒绝定位时展示“无权限”而非默认北京。

**Architecture:** 在 `DiscoverViewModel` 中新增“城市切换面板”和“定位被拒绝”两个纯 UI 状态，保持定位、地区选择和天气加载仍由现有 Repository 驱动。手选地区是持久化覆盖项；只有用户在切换面板中成功选择“使用当前位置”后才清除该覆盖项。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、StateFlow、SharedPreferences、现有高德定位与地区 API。

## Global Constraints

- 保持 Android 原生 Material 3 交互、最小 48dp 触控目标和既有发现页视觉基线。
- 不修改地图入口、路线参数、地区 API 或两小时天气缓存契约。
- 用户未授权新增单元测试；不得创建测试文件，使用已有 Kotlin 编译与 diff 检查验证。
- 不启动前端服务、浏览器或真机；不提交、推送或创建 Git 分支。
- 代码注释与文案使用中文；避免新增同义常量和业务 magic number。

---

### Task 1: 持久化手选覆盖项并更新发现页状态机

**Files:**
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/data/discover/DiscoverLocalStore.kt:54-105`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/data/discover/DiscoverRepository.kt:51-57`
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/feature/discover/DiscoverViewModel.kt:39-136,219-308,360-378`

**Interfaces:**
- Produces: `DiscoverLocalStore.clearManualAnchor(): Unit` and `DiscoverRepository.clearSavedManualAnchor(): Unit`.
- Produces: `DiscoverViewModel.openCitySwitcher()`, `dismissCitySwitcher()`, `useCurrentLocation(hasLocationPermission: Boolean)`.
- Produces: `DiscoverUiState.isCitySwitcherVisible` and `DiscoverUiState.isLocationPermissionDenied`.

- [x] **Step 1: 增加清除手选地区记录的本地存储接口**

在 `DiscoverLocalStore` 增加仅清除 `KEY_MANUAL_*` 的方法，不触碰天气缓存：

```kotlin
fun clearManualAnchor() {
    sharedPreferences.edit()
        .remove(KEY_MANUAL_CITY_NAME)
        .remove(KEY_MANUAL_CITY_ADCODE)
        .remove(KEY_MANUAL_ROUTE_CITY_NAME)
        .remove(KEY_MANUAL_ROUTE_CITY_ADCODE)
        .remove(KEY_MANUAL_LONGITUDE)
        .remove(KEY_MANUAL_LATITUDE)
        .apply()
}
```

在 `DiscoverRepository` 暴露同名语义的 `clearSavedManualAnchor()`，仅委托给 `localStore.clearManualAnchor()`。

- [x] **Step 2: 将初始化和城市入口改为“默认定位 + 显式切换”**

在 `initialize()` 中先恢复 `savedManualAnchor`；只有不存在手选覆盖项时才根据权限定位或弹出授权提示：

```kotlin
when {
    savedManualAnchor != null -> applyAnchor(savedManualAnchor)
    hasLocationPermission -> resolveDeviceAnchor()
    else -> mutableUiState.update { it.copy(showLocationPermissionPrompt = true) }
}
```

将现有 `onCitySelectorClick(hasLocationPermission)` 替换为只打开面板的 `openCitySwitcher()`，并新增 `dismissCitySwitcher()`。新增 `useCurrentLocation(hasLocationPermission)`：关闭切换面板；已授权时调用定位；未授权时显示现有授权提示。

新增 `isLocationPermissionRequestPending` 状态。用户点击授权提示的“开启定位”时，先关闭应用内提示并将该状态置为 `true`；Screen 使用 `LaunchedEffect` 在重组后启动系统权限请求，结果回调必须将其复位。永久拒绝选择“去设置”时记录一次待检查标记，Screen 在 `ON_RESUME` 重新读取权限并继续既有的授权结果处理，保证成功授权后能够定位并按条件清除手选覆盖项。

- [x] **Step 3: 只在成功回到当前位置后清除手选覆盖项**

使用 ViewModel 私有布尔状态记录“本次定位成功后清除手选地区”。`useCurrentLocation()` 在已授权时将其传给 `resolveDeviceAnchor(clearManualAnchorOnSuccess = true)`；未授权时保留该意图直到 `onLocationPermissionResult(true, ...)`。

定位成功分支必须先清除持久化的手选地区，再 `applyAnchor(deviceAnchor)`；定位失败、取消或拒绝授权时不得清除手选地区。初始化定位和“从当前位置开始”保持现有行为，不清除用户的手选覆盖项。

- [x] **Step 4: 将拒绝与关闭授权提示收敛为无权限状态**

在 `DiscoverUiState` 增加：

```kotlin
val isCitySwitcherVisible: Boolean = false,
val isLocationPermissionDenied: Boolean = false,
```

系统权限回调拒绝时，将 `isLocationPermissionDenied` 设为 `true`，保留授权提示中的“选择地区”入口，并清除待处理的“切回当前位置”意图；不得清除探索动作的 `pendingExploreAction`，以便用户选完地区后仍能继续原动作。关闭授权提示但未授权时保留“无权限”状态。手动选中地区或设备定位成功时将该标志重置为 `false`；已有手选锚点始终优先展示锚点，不受该标志影响。

`openRegionPicker()` 同时关闭城市切换面板；`applyAnchor()` 同时关闭授权提示、地区选择和城市切换面板，避免多个弹层重叠。

- [x] **Step 5: 静态核对状态转移**

检查以下路径均不丢失手选地区：

1. 手选地区 → 点击“使用当前位置” → 拒绝授权；
2. 手选地区 → 点击“使用当前位置” → 定位失败；
3. 没有锚点 → 拒绝授权；
4. 手选地区 → 点击“使用当前位置” → 定位成功。

预期只有第 4 条清除本地手选地区；第 3 条进入无权限展示状态。

### Task 2: 渲染切换面板与无权限展示状态

**Files:**
- Modify: `android-app/app/src/main/java/com/urbansidequest/app/feature/discover/DiscoverScreen.kt:112-192,195-555`

**Interfaces:**
- Consumes: `DiscoverUiState.isCitySwitcherVisible`、`DiscoverUiState.isLocationPermissionDenied` 与 Task 1 的三个 ViewModel 事件。
- Produces: 顶部城市入口始终提供“使用当前位置 / 手动选择地区”的切换路径。

- [x] **Step 1: 将城市点击回调接到城市切换面板**

在 `DiscoverRoute` 中将顶部入口绑定为 `discoverViewModel::openCitySwitcher`。在地区选择 Bottom Sheet 之前，按 `uiState.isCitySwitcherVisible` 渲染新的 `DiscoverCitySwitcherSheet`，并传入：

```kotlin
onDismiss = discoverViewModel::dismissCitySwitcher,
onUseCurrentLocation = {
    discoverViewModel.useCurrentLocation(context.hasDiscoverLocationPermission())
},
onChooseRegion = discoverViewModel::openRegionPicker,
```

- [x] **Step 2: 实现轻量的 Material 3 城市切换 Bottom Sheet**

复用现有 `ModalBottomSheet`、`Button` 与 `TextButton`，不新增图标或新依赖。面板包含标题“切换城市”、说明“默认使用当前位置，你也可以手动选择地区。”、主操作“使用当前位置”与次操作“手动选择地区”。两个操作都通过 ViewModel 回调处理，不在 Composable 内做权限或持久化判断。

- [x] **Step 3: 消除无锚点时的北京默认展示**

在 `DiscoverScreen` 依据 `selectedAnchor`、`isAnchorLoading` 和 `isLocationPermissionDenied` 计算展示文案，不能在没有锚点时读取 `DiscoverCityWeather` 的默认北京值：

```kotlin
val cityName = when {
    uiState.selectedAnchor != null -> uiState.selectedAnchor.cityName
    uiState.isAnchorLoading -> "正在定位"
    uiState.isLocationPermissionDenied -> "无权限"
    else -> "暂未定位"
}
val weatherText = when {
    uiState.selectedAnchor != null -> uiState.cityWeather.weatherText
    uiState.isAnchorLoading -> "正在定位城市"
    uiState.isLocationPermissionDenied -> "选择地区后显示天气"
    else -> "请开启定位或选择地区"
}
```

将这两个展示值传给现有顶部胶囊与城市卡片。保留定位中的转圈状态与 48dp 可点区域；仅在 `isAnchorLoading` 时禁用重复点击。

- [x] **Step 4: 静态检查 UI 边界**

确认城市切换面板与地区选择器不会重叠；拒绝授权时，授权提示仍提供“选择地区”，关闭该提示后展示“无权限”；确认“无权限”状态下右上角入口仍可点击并可直达地区选择器；确认不新增手绘图标、颜色或页面布局结构。

### Task 3: 编译与交付核对

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-discover-city-switching.md`

**Interfaces:**
- Consumes: Task 1、Task 2 的完成代码。
- Produces: 可编译的 Android Debug Kotlin 代码与明确的手工验收清单。

- [x] **Step 1: 运行格式检查**

Run: `git diff --check`

Expected: 退出码为 0，且没有空白错误输出。

- [x] **Step 2: 编译 Android Kotlin**

Run: `./gradlew :app:compileDebugKotlin`（工作目录：`android-app`）

Expected: `BUILD SUCCESSFUL`。

- [x] **Step 3: 记录未自动验证项**

不启动 App。交付时注明真机需要核对：首次拒绝授权是否显示“无权限”；手动城市是否在重启后保留；选择当前位置成功后是否清除手选覆盖项；永久拒绝时是否跳转系统设置。

## Plan Self-Review

- 规格覆盖：默认定位、随时切换、手选持久化、成功回到当前位置、拒绝后的“无权限”展示均有对应任务。
- 占位符：没有未完成标记或模糊实现。
- 类型一致性：计划中的状态字段、ViewModel 事件与 Repository 清除接口均在前序任务定义；UI 只消费这些公开状态和回调。
