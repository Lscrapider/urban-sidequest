# 模块骨架搭建 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建城市副本第一阶段 Android 前端、Spring Boot 后端和数据库本地环境的基础模块边界。

**Architecture:** 使用 monorepo，`android-app/` 承载 Kotlin + Jetpack Compose Android 前端，`backend/` 承载 Java + Spring Boot 后端，`database/` 承载 PostGIS 本地环境和迁移脚本。根目录 `docker-compose.yml` 启动完整依赖，`database/docker-compose.yml` 只启动数据库，两者使用相同 compose name 和数据库配置，方便本地调试。

**Tech Stack:** Kotlin、Jetpack Compose、Java、Spring Boot、PostgreSQL/PostGIS、Redis、Docker Compose。

---

### Task 1: 本地依赖环境

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `database/docker-compose.yml`
- Create: `database/.env.example`

- [ ] **Step 1: 创建根目录依赖环境**

写入根目录 `docker-compose.yml`，包含同名 compose 项目、PostGIS 和 Redis。

- [ ] **Step 2: 创建数据库调试环境**

写入 `database/docker-compose.yml`，只包含同名 compose 项目和 PostGIS 服务。

- [ ] **Step 3: 写入示例环境变量**

写入根目录和 `database/` 下的 `.env.example`，保持库名、用户名、端口一致。

### Task 2: 数据库迁移

**Files:**
- Create: `database/init/01-enable-postgis.sql`
- Create: `database/migrations/V1__init_core_schema.sql`
- Create: `database/README.md`

- [ ] **Step 1: 启用 PostGIS**

创建初始化 SQL，确保数据库启动后具备 PostGIS 扩展。

- [ ] **Step 2: 定义核心表**

创建用户、偏好、POI 缓存、路线请求、路线结果、路线节点、收藏、打卡和反馈基础表。

- [ ] **Step 3: 写入数据库 README**

说明如何从根目录或 `database/` 目录启动数据库环境。

### Task 3: Spring Boot 后端骨架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/urbansidequest/backend/UrbanSidequestBackendApplication.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/controller/HealthController.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/SystemStatusService.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/impl/SystemStatusServiceImpl.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/vo/SystemStatusVO.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/README.md`

- [ ] **Step 1: 创建 Maven Spring Boot 项目**

使用 Java 21、Spring Web、Validation、Actuator、Redis、PostgreSQL、Flyway。

- [ ] **Step 2: 创建分层包**

按 Controller -> Service -> Domain 的最小链路放置健康检查接口，后续业务继续沿用 controller、service、service.impl、domain.param、domain.vo、domain.po、api、config 分层。

- [ ] **Step 3: 写入后端 README**

说明本地依赖、启动命令和模块边界。

### Task 4: Android 前端骨架

**Files:**
- Create: `android-app/settings.gradle.kts`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/UrbanSidequestApplication.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/MainActivity.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/ui/theme/Color.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/ui/theme/Theme.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/data/map/MapSdkFacade.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/data/api/BackendApi.kt`
- Create: `android-app/app/src/main/java/com/urbansidequest/app/domain/model/RouteModels.kt`
- Create: `android-app/app/src/main/res/values/strings.xml`
- Create: `android-app/app/src/main/res/values/styles.xml`
- Create: `android-app/README.md`

- [ ] **Step 1: 创建 Gradle Android 工程**

使用 Kotlin + Jetpack Compose，先保留高德 SDK 适配层边界，不写入真实 key。

- [ ] **Step 2: 创建页面 feature 目录**

预留 login、mapselect、routeconfig、routeresult、poi、execution、profile 页面模块。

- [ ] **Step 3: 写入 Android README**

说明 Android 前端就是 Kotlin/Compose 工程，并说明高德 SDK 后续接入位置。

### Task 5: 文档同步和验证

**Files:**
- Modify: `README.md`
- Modify: `docs/overview/technical-design.md`

- [ ] **Step 1: 更新 README 模块结构**

补充 `android-app/`、`backend/`、`database/` 和 compose 文件用途。

- [ ] **Step 2: 更新技术设计落地边界**

补充本次实际 scaffold 的模块路径。

- [ ] **Step 3: 运行验证**

运行 `git diff --check`。如果本地存在 Maven、Gradle 或 Docker，再运行对应轻量检查；如果不可用，明确说明未验证原因。
