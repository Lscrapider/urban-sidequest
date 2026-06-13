# Android 前端模块

城市副本 Android 前端使用 Kotlin + Jetpack Compose。这里的“前端”就是原生 Android App，不是 H5。

## 模块职责

- 页面 UI：Compose。
- 地图展示、定位、POI 搜索、marker、polyline：高德 Android SDK。
- 后端请求：后续使用 Retrofit 或 Ktor Client。
- 本地缓存：后续使用 Room。
- 状态管理：ViewModel + StateFlow。

## 页面包

- `feature.login`：登录页。
- `feature.mapselect`：地图选区页。
- `feature.routeconfig`：条件配置页。
- `feature.routeresult`：路线结果页。
- `feature.poi`：POI 解释卡。
- `feature.execution`：路线执行页。
- `feature.profile`：我的页。

## 高德 SDK 接入

当前只建立 `data.map` 适配边界，不写入高德 key。后续接入时：

1. 在高德开放平台创建 Android Key，绑定包名和签名。
2. 将 key 放入本地 Gradle 或安全配置，不提交到仓库。
3. 在 `data.map` 下封装 MapView、定位、POI 搜索和路线 overlay。
4. 页面层只调用封装后的地图能力。

