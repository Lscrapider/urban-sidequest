# Route Preference Raw Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保存 route X 的完整冻结输入，并支持从冻结表重建 samples 表特征。

**Architecture:** 新增 raw snapshot 表和 PO/Mapper/Manage 负责冻结数据持久化；新增 payload/builder/restorer/service 负责在线冻结、恢复上下文和内部重建；`SaveRoutePreferenceTrainingSamplesStep` 受配置开关控制是否写 raw snapshot，但 samples 原有写入不受影响。

**Tech Stack:** Spring Boot 3.4, Java 17, MyBatis Plus, PostgreSQL JSONB, JUnit 5, Mockito, AssertJ.

---

## Files

- Create: `database/migrations/V12__route_preference_raw_snapshot.sql`
- Create: `backend/src/main/java/com/urbansidequest/backend/config/RoutePreferenceTrainingProperties.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/domain/po/RoutePreferenceRawSnapshotPO.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/mapper/RoutePreferenceRawSnapshotMapper.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/manage/RoutePreferenceRawSnapshotManage.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotSchema.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotPayload.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotBuilder.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotRestorer.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/RoutePreferenceFeatureRebuildService.java`
- Create: `backend/src/main/java/com/urbansidequest/backend/service/impl/RoutePreferenceFeatureRebuildServiceImpl.java`
- Create: `backend/src/test/java/com/urbansidequest/backend/handler/route/training/RoutePreferenceRawSnapshotRebuildTest.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/handler/route/step/SaveRoutePreferenceTrainingSamplesStep.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/manage/RoutePreferenceTrainingSampleManage.java`
- Modify: `backend/src/main/java/com/urbansidequest/backend/mapper/RoutePreferenceTrainingSampleMapper.java`
- Modify: `backend/src/main/resources/application.yml`

## Task 1: TDD 一致性测试

- [ ] 新增 `RoutePreferenceRawSnapshotRebuildTest`，构造一个带 selected route、POI candidate、semantic mapping、用户画像和 segment 的 `RouteGenerationContext`。
- [ ] 在测试中先用 `RouteInputFeatureExtractor.extract(route, context)` 生成在线 snapshot。
- [ ] 调用尚未实现的 `RoutePreferenceRawSnapshotBuilder.build(context)` 和 `RoutePreferenceFeatureRebuildServiceImpl.rebuildByCandidateSetId(candidateSetId)`。
- [ ] 使用 Mockito stub raw snapshot manage 返回冻结数据，capture training sample manage 的 upsert 参数。
- [ ] 断言在线 snapshot 与重建 snapshot 的六个字段完全一致。
- [ ] 运行：
  - `cd backend && mvn -Dtest=RoutePreferenceRawSnapshotRebuildTest test`
  - 预期第一次失败，失败原因是新增类或方法不存在。

## Task 2: Raw Snapshot 表与持久化层

- [ ] 新增 migration `V12__route_preference_raw_snapshot.sql`，创建 `route_preference_raw_snapshots`，包含 spec 中列出的 JSONB 字段、唯一约束和索引。
- [ ] 新增 `RoutePreferenceRawSnapshotPO`，字段与表一一对应，并提供 getter/setter。
- [ ] 新增 `RoutePreferenceRawSnapshotMapper.upsertSnapshot(...)` 和 `selectByCandidateSetId(...)`。
- [ ] 新增 `RoutePreferenceRawSnapshotManage.upsertSnapshot(...)` 和 `findByCandidateSetId(...)`。

## Task 3: 冻结与恢复模型

- [ ] 新增 `RoutePreferenceRawSnapshotSchema.VERSION = "route_pref_raw_v1"`。
- [ ] 新增 `RoutePreferenceRawSnapshotPayload` record，字段使用现有 DTO 类型承载冻结数据。
- [ ] 新增 `RoutePreferenceRawSnapshotBuilder`，用 `ObjectMapper` 从 `RouteGenerationContext` 序列化每个 JSON 字段。
- [ ] 新增 `RoutePreferenceRawSnapshotRestorer`，从 PO JSON 字段恢复 payload，并创建只用于 route X 的 `RouteGenerationContext`。

## Task 4: 内部修复服务

- [ ] 新增 `RoutePreferenceFeatureRebuildService` 接口。
- [ ] 新增 `RoutePreferenceFeatureRebuildServiceImpl`：
  - `rebuildByCandidateSetId(UUID candidateSetId)` 从 raw snapshot 恢复 context，逐条 route 重新抽特征并 upsert samples。
  - `rebuildOutdatedSamples()` 查询过期 candidate set 后逐个修复。
- [ ] 修改 `RoutePreferenceTrainingSampleMapper` 和 manage，增加 `findOutdatedCandidateSetIds(String currentFeatureSchemaVersion)`。
- [ ] 重建时保留 label 相关字段，依赖现有 upsert SQL 不覆盖 label。

## Task 5: 在线冻结开关接入

- [ ] 新增 `RoutePreferenceTrainingProperties`，字段 `rawSnapshotEnabled` 默认 `true`。
- [ ] 在 `application.yml` 增加 `route.preference.training.raw-snapshot-enabled`，默认走环境变量，缺省为 `true`。
- [ ] 修改 `SaveRoutePreferenceTrainingSamplesStep` 注入 properties、builder、raw snapshot manage。
- [ ] 当 `rawSnapshotEnabled == true` 且 selected routes 非空时，先写 raw snapshot，再写 samples；关闭时只写 samples。

## Task 6: 验证

- [ ] 运行聚焦测试：
  - `cd backend && mvn -Dtest=RoutePreferenceRawSnapshotRebuildTest test`
  - 预期通过。
- [ ] 运行已有 route X 测试：
  - `cd backend && mvn -Dtest=RouteInputFeatureExtractorTest test`
  - 预期通过。
- [ ] 运行后端测试：
  - `cd backend && mvn test`
  - 预期通过；如果环境缺少服务或配置导致失败，记录具体失败原因。

## Notes

- 本项目规则要求未经同意不提交 git，因此本计划不包含 commit 步骤。
- 本次新增单元测试已得到用户明确同意。
- 不新增对外 controller；修复 samples 特征的方法只作为内部 service 暴露，并通过单元测试执行。
