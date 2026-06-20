# 城市副本

城市副本是一个基于中国城市地图的路线生成产品。用户选择一个地图区域，补充基础出行条件后，系统生成一条可执行、可解释、可调整的主推路线 A，并提供两条额外路线作为备选。

第一阶段直接以 Android APK 承载核心体验，后续预留 iOS App，Web 负责产品介绍和后台维护。

## 当前阶段

当前已完成产品设计、Android 视觉基线、模块骨架和后端路线生成主链路。后端当前已经具备：

- 基于高德 / 百度 POI 的候选池拉取。
- POI Linear Ranker + 多样性采样预筛。
- LLM 从筛选后的 POI 池编排固定 5 条候选路线。
- 后端硬约束复核和启发式规则打分排序。
- 对最终选中路线做高德真实路径校准。
- 路线偏好训练样本 `routeInput` 四块特征保存。
- LLM 模拟用户 judgment 保存接口和 Python 批量工具。

尚未上线的能力包括 Route Judge、MLP / RoutePreferenceModel 在线推理、soft reject、完整离线训练和 candidate_sets 状态机。

## 文档索引

项目级入口：

- `PRODUCT.md`：项目级产品上下文，供设计和开发流程读取。
- `DESIGN.md`：Android UI 设计系统和视觉规范。
- `docs/overview/product-design.md`：当前产品设计基准。
- `docs/overview/technical-design.md`：技术选型、路线生成链路、并发、隔离和安全约束。

算法文档：

- `docs/algorithm/推荐路径算法.md`：当前路线生成主链路、POI 预筛、LLM 编排、后端复核和高德校准。
- `docs/algorithm/poi/POI线性打分矩阵取值设计.md`：POI Linear Ranker 的特征、权重、规约、动态增量和 trace 口径。
- `docs/algorithm/route/路线裁判与软拒绝设计.md`：Route Judge、路线级可解释评分、soft reject 和后续 accept model 设计；当前未接入线上主链路。
- `docs/algorithm/route/路线偏好排序模型训练设计.md`：路线级偏好模型离线训练、pair 构造、loss 和导出设计；当前只落地样本与 judgment 数据采集。
- `docs/algorithm/route/LLM模拟用户路线选择设计.md`：冷启动 LLM 模拟用户评价、judgments 表和 synthetic 训练信号。
- `docs/algorithm/archive/旧-推荐路径算法-beamsearch.md`：旧 Beam Search 方案归档，用于和当前 LLM 编排主线对照。

用户和 UI：

- `docs/user/用户画像问卷与画像表设计.md`：用户画像问卷、画像表、读取流程和 POI Linear 个性化输入边界。
- `docs/ui/img/`：核心 UI 方案图片。
- `docs/ui/stitch/urban-sidequest-mobile-v2/`：Stitch 移动端原型导出的 HTML 和图片。

执行计划：

- `docs/superpowers/plans/2026-06-13-module-scaffold.md`：模块骨架搭建实施计划归档。

## 核心产品方向

- 首要用户：旅游用户。
- 第一阶段平台：Android APK。
- 主体验：地图选区、条件输入、路线 A、两条额外路线、POI 解释卡、打卡反馈。
- Web：产品介绍页和后台维护。
- 后续：iOS App 承接同等移动端能力。

## 模块结构

- `android-app/`：Android 前端，使用 Kotlin + Jetpack Compose，后续接入高德 Android SDK。
- `backend/`：Java + Spring Boot 后端主服务，包含认证、路线生成、POI 候选、偏好训练样本、judgment 保存接口。
- `database/`：PostgreSQL/PostGIS 初始化、迁移脚本和单独数据库调试环境。
- `scripts/route_preference_simulator/`：路线偏好模拟用户批量工具，调用后端生成路线、调用 LLM judge、回写 judgment。
- `docker-compose.yml`：项目资源初始化服务，连接通用数据库栈。

本地依赖默认配置：

- compose name：`urban-sidequest`
- 通用数据库栈：`/Users/qinzeyu/study/docker-database-common`
- PostgreSQL host：`common-postgres:5432`
- Redis host：`common-redis:6379`
- database：`urban_sidequest`
- user：`urban_sidequest`
- 本地后端运行时可通过通用栈端口映射访问 `localhost:5432` 和 `localhost:6379`

## 开发约束

- 每次处理项目任务先阅读 `AGENTS.md`。
- 产品设计和技术设计分文档维护。
- UI 设计产生的新产品判断需要回写产品设计文档。
- 未经确认不提交、不推送、不建分支。
- 未经确认不新增单元测试。

## 下一步

优先补齐当前链路的断点：

1. 对齐 `route_preference_candidate_sets` 的后端写入、计数和状态推进。
2. 明确 `training_samples.label_json/sample_weight` 是否停用整批回填，统一到 judgments 追加式 Y 真源。
3. 补离线训练脚本和评估流程，再决定是否把 RoutePreferenceModel 接入线上重排。
4. 在 Route Judge 上线前，继续以 `ScoreAndSelectRoutesStep` 的硬约束和规则分作为线上排序口径。
