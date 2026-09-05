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
2. 将 key 放入 `android-app/local.properties.dev` 或 CI 环境变量，不提交到仓库。
3. 在 `data.map` 下封装 MapView、定位、POI 搜索和路线 overlay。
4. 页面层只调用封装后的地图能力。

## 本地配置

`android-app/local.properties` 保留给 Android Studio / SDK 本地用途。
业务 dev 配置写入 `android-app/local.properties.dev`，该文件不提交：

```properties
backend.base.url=http://10.0.2.2:8082/urban-api
minio.image.base.url=http://10.0.2.2:9000
amap.api.key=你的高德 Android Key
```

`debug` 包的服务地址读取优先级：

```text
BACKEND_BASE_URL / MINIO_IMAGE_BASE_URL 环境变量 > local.properties.dev > local.properties > 构建默认值
```

`release` 包固定使用以下线上地址：

```text
backend.base.url=https://urban.scrapider.cloud/urban-api
minio.image.base.url=https://urban.scrapider.cloud/images/
```

高德 Key 在两个构建类型中共用，读取优先级为：

```text
AMAP_API_KEY 环境变量 > local.properties.dev > local.properties > 空值
```

## 发布签名

`release` 构建必须使用本机的 `keystore.properties`。该文件和 keystore 均被 Git 忽略，不能提交或分享：

```properties
storeFile=keystore/urban-sidequest-release.jks
storePassword=本机保存的密钥库密码
keyAlias=urban-sidequest
keyPassword=本机保存的私钥密码
```

丢失 release keystore 或密码后，无法继续为同一应用签发更新包；请将它们备份到安全位置。
