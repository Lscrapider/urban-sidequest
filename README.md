# 城市副本

城市副本是一个基于中国城市地图的路线生成产品。用户选择一个地图区域，补充基础出行条件后，系统生成一条可执行、可解释、可调整的主推路线 A，并提供两条额外路线作为备选。

第一阶段直接以 Android APK 承载核心体验，后续预留 iOS App，Web 负责产品介绍和后台维护。

## 算法内核

城市副本的算法分成两层：POI 层负责把城市里的点筛好，路线集层负责在同一批候选路线里判断哪条更值得推荐。POI 层不直接决定最终路线，路线集层也不重新发明 POI 质量判断；两层通过 `PoiLinearTraceDTO` 和 `routeInput` 衔接，形成从点到路线的可解释训练链路。

### POI 层

POI 层解决的是“哪些点值得进入路线编排”的问题。高德 / 百度召回的是一个噪声很高的城市候选池，里面既有热门景点、餐饮、休息点，也有位置不合适、语义不稳定、体验风险高或和用户目标不匹配的点。POI Linear 的职责是在进入 LLM 之前完成候选空间压缩：过滤明显不适合的点，抬高高潜力点，保留必要的餐饮 / 休息 / 本地特色供给，并用多样性采样避免候选池被单一热门类别占满。

这里不是把搜索回来的 POI 简单打分、排序，然后把前 N 个点原样交给大模型生成路线；POI 层更像一个可解释的候选池治理层，先把点位按角色、质量、风险、交通和用户意图重组，再把一个结构更干净、类型更均衡、约束更明确的候选空间交给后续路线编排。

它的做法是把每个 POI 规约成稳定特征：兴趣匹配、路线目标适配、POI 质量、交通可达性、距离压力、预算压力、风险因子、环境上下文和用户画像交叉特征。`PoiLinearScorer` 用线性权重矩阵输出总分和分项分，`PoiDiversitySampler` 再做受控随机与类别均衡。每个候选点都会保留 `PoiLinearTraceDTO`，所以后续可以解释“这个点为什么进池”“它是质量高、个性化强，还是只是作为补充类型被保留”。

POI 层的训练 / 校准重点不是一开始就上黑盒模型，而是先把特征、权重、阈值和 trace 口径稳定下来。真实路线样本、用户反馈和 LLM judgment 回流后，可以按 POI Linear trace 的分布校准风险、距离、预算等惩罚项，也可以把被高质量路线频繁采用的 POI 反向作为弱监督信号，逐步调整线性权重。这样 POI 层保持可解释、可回放、可人工干预，同时为路线级模型提供干净的输入。

### 路线集层

路线集层解决的是“同一批候选路线里，哪一条更适合当前用户和当前请求”的问题。LLM 可以基于 POI 池生成多条路线，但路线好坏不等于单个 POI 分数相加：它还取决于节奏、顺路程度、交通负担、饭点完整性、类别变化、时间预算、用户偏好和整条路线的叙事连贯性。因此路线集层关注的是候选路线之间的相对优劣，以及第一名是否真的值得推荐。

它的输入是每条候选路线的 `routeInput`。`RouteInputFeatureExtractor` 会把路线拆成四块：`stopMatrixJson` 描述停靠点序列和 POI Linear 摘要；`segmentMatrixJson` 描述点到点之间的距离、耗时、交通方式和 fallback 来源；`routeDerivedVectorJson` 汇总路线级节奏、覆盖度、多样性、饭点完整性、换乘压力和校准质量；`contextCrossVectorJson` 表达用户画像、出行方式、预算、时间和路线目标之间的交叉关系。当前 schema 为 `route_pref_v3`，核心目标是让每条路线都能被稳定地数值化、比较和回放。

路线集层的训练以 `candidateSetId` 为边界：一次生成得到一组候选路线，每条路线保存一条 X，即 `route_preference_training_samples` 中的四块 `routeInput`；LLM 模拟用户和后续真实用户反馈提供 Y，即 `route_preference_judgments` 中的排序、接受 / 拒绝和原因码。离线训练时只在同一个候选集内构造 chosen / rejected route pair，训练 `RoutePreferenceModel` 学习“哪条路线更应该排在前面”。MLP 结构上可以用 stop / segment 处理局部体验，用 routeDerived / contextCross 处理全局适配，最后通过 fusion MLP 输出路线偏好分。后续真实 accept/reject 数据足够后，同一套 `routeInput` 还可以继续派生 pointwise accept model 或 Route Judge，而不需要另建一套特征体系。

## 文档索引

完整分类入口见 [docs/README.md](docs/README.md)。

项目级入口：

- [PRODUCT.md](PRODUCT.md)：项目级产品上下文，供设计和开发流程读取。
- [DESIGN.md](DESIGN.md)：Android UI 设计系统和视觉规范。
- [产品设计](docs/overview/product-design.md)：当前产品设计基准。
- [技术设计](docs/overview/technical-design.md)：技术选型、路线生成链路、并发、隔离和安全约束。

算法文档：

- [推荐路径算法](docs/algorithm/推荐路径算法.md)：当前路线生成主链路、POI 预筛、LLM 编排、后端复核和高德校准。
- [POI 线性打分矩阵取值设计](docs/algorithm/poi/POI线性打分矩阵取值设计.md)：POI Linear Ranker 的特征、权重、规约、动态增量和 trace 口径。
- [路线裁判与软拒绝设计](docs/algorithm/route/路线裁判与软拒绝设计.md)：Route Judge、路线级可解释评分、soft reject 和后续 accept model 设计；当前未接入线上主链路。
- [路线偏好排序模型训练设计](docs/algorithm/route/路线偏好排序模型训练设计.md)：路线级偏好模型离线训练、pair 构造、loss 和导出设计；当前只落地样本与 judgment 数据采集。
- [LLM 模拟用户路线选择设计](docs/algorithm/route/LLM模拟用户路线选择设计.md)：冷启动 LLM 模拟用户评价、judgments 表和 synthetic 训练信号。
- [旧推荐路径算法 Beam Search](docs/algorithm/archive/旧-推荐路径算法-beamsearch.md)：旧 Beam Search 方案归档，用于和当前 LLM 编排主线对照。

问题解决归档：

- [问题解决文档索引](docs/problem-solving/README.md)：按日期归档一次性问题定位、修复方案、改动清单和落地记录。

用户和 UI：

- [用户画像问卷与画像表设计](docs/user/用户画像问卷与画像表设计.md)：用户画像问卷、画像表、读取流程和 POI Linear 个性化输入边界。
- [UI 图片目录](docs/ui/img/)：核心 UI 方案图片。
- [Stitch 移动端原型](docs/ui/stitch/urban-sidequest-mobile-v2/)：Stitch 移动端原型导出的 HTML 和图片。

执行计划：

- [模块骨架搭建实施计划](docs/superpowers/plans/2026-06-13-module-scaffold.md)：Superpowers implementation plan 归档。

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
- `ai-python/`：Python AI 模块，包含路线偏好模拟用户批量工具和后续模型算法目录。
- `docker-compose.yml`：项目资源初始化服务，连接通用数据库栈。

本地依赖默认配置：

- compose name：`urban-sidequest`
- 通用数据库栈：`/Users/qinzeyu/study/docker-database-common`
- PostgreSQL host：`common-postgres:5432`
- Redis host：`common-redis:6379`
- database：`urban_sidequest`
- user：`urban_sidequest`
- 本地后端运行时可通过通用栈端口映射访问 `localhost:5432` 和 `localhost:6379`
