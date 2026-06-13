# 城市副本

城市副本是一个基于中国城市地图的路线生成产品。用户选择一个地图区域，补充基础出行条件后，系统生成一条可执行、可解释、可调整的主推路线 A，并提供两条额外路线作为备选。

第一阶段直接以 Android APK 承载核心体验，后续预留 iOS App，Web 负责产品介绍和后台维护。

## 当前阶段

当前已完成产品设计、核心 UI 设计和第一阶段技术选型，下一步进入开发模块骨架和接口数据结构设计。

推进顺序：

1. UI 设计和页面信息架构。
2. UI 设计过程中回写产品设计文档。
3. 技术架构和技术选型。
4. 开发模块构建。
5. 按页面逐步开发。

## 文档索引

- `PRODUCT.md`：项目级产品上下文，供设计和开发流程读取。
- `docs/product-brief.md`：早期产品 brief。
- `docs/product-design.md`：当前产品设计基准。
- `docs/ui-inspiration.md`：UI 启发、参考方向和探索图。
- `docs/page-design.md`：Android App 页面信息架构、Stitch UI 方案和本地导出记录。
- `docs/technical-design.md`：技术选型、路线生成链路、并发、隔离和安全约束。
- `docs/project-workflow.md`：项目推进流程。

## 核心产品方向

- 首要用户：旅游用户。
- 第一阶段平台：Android APK。
- 主体验：地图选区、条件输入、路线 A、两条额外路线、POI 解释卡、打卡反馈。
- Web：产品介绍页和后台维护。
- 后续：iOS App 承接同等移动端能力。

## 开发约束

- 每次处理项目任务先阅读 `AGENTS.md`。
- 产品设计和技术设计分文档维护。
- UI 设计产生的新产品判断需要回写产品设计文档。
- 未经确认不提交、不推送、不建分支。
- 未经确认不新增单元测试。

## 下一步

基于 `docs/page-design.md` 和 `docs/technical-design.md` 中已确认的 Android UI 与技术选型，进入 Android App、Spring Boot 后端、数据结构和 API 契约的开发拆分。
