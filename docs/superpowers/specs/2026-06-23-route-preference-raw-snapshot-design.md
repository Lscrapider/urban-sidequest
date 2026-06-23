# Route Preference Raw Snapshot Design

## 背景

当前路线偏好训练样本在路线生成 pipeline 末尾直接写入 `route_preference_training_samples`。`SaveRoutePreferenceTrainingSamplesStep` 会遍历最终返回的 selected routes，调用 `RouteInputFeatureExtractor` 生成 route X，再通过 `RoutePreferenceTrainingSampleManage` upsert 到 samples 表。

这个结构的问题是 samples 表同时承载了三类职责：

1. 当次路线生成可用于 route X 的输入材料。
2. 当前 `feature_schema_version` 下的 route X 派生结果。
3. judgment 写入后的 label、weight 和训练可用状态。

由于 route X 的维度、特征和规则会持续变化，历史样本一旦只保存派生后的 X，就会因为 schema 更新而作废。用户明确要求：所有会影响 route X 的数据都必须冻结，后续只改通用转换方法和版本号，即可从同一份原始数据重新生成最新 route X。

## 目标

- 新增一张 raw snapshot 表，冻结所有影响 route X 的输入。
- 保留 `route_preference_training_samples` 作为可重建的派生训练样本表。
- 提供通用重建方法：从 raw snapshot 还原 route X 输入，重新生成 samples，并更新 `feature_schema_version` 到当前版本。
- 支持通过 samples 表版本号发现过期训练样本，并通过 `candidate_set_id` 找到 raw snapshot 后重建。
- raw snapshot 写入必须受配置控制，便于在不同环境按需开启或关闭。
- 新增单元测试：在 route X 算法版本一致时，同一条结果路线在线写入 samples 的特征，必须与从 raw snapshot 恢复后生成的特征完全一致。

## 非目标

- 不重写路线生成 pipeline 的核心编排逻辑。
- 不改变现有 route X 特征算法的业务含义。
- 不改变 judgment 保存接口的请求结构。
- 不把已有 `route_requests` / `generated_routes` 表补齐为完整业务持久化链路。
- 不提供对外 HTTP 修复接口；修复 samples 特征的方法先作为内部服务方法，并通过单元测试执行。

## 数据模型

新增表：`route_preference_raw_snapshots`。

建议字段：

- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- `candidate_set_id UUID NOT NULL`
- `request_id UUID NOT NULL`
- `user_id UUID REFERENCES users(id)`
- `raw_schema_version VARCHAR(64) NOT NULL`
- `generate_param_json JSONB NOT NULL`
- `area_json JSONB`
- `weather_json JSONB NOT NULL`
- `user_preference_profile_json JSONB NOT NULL`
- `interest_tag_catalog_json JSONB NOT NULL`
- `interest_tags_json JSONB NOT NULL`
- `poi_semantic_mappings_json JSONB NOT NULL`
- `poi_candidates_json JSONB NOT NULL`
- `poi_linear_traces_json JSONB NOT NULL`
- `selected_routes_json JSONB NOT NULL`
- `segment_costs_json JSONB NOT NULL`
- `warnings_json JSONB NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`

约束和索引：

- `UNIQUE (candidate_set_id)`：一个 candidate set 对应一份冻结输入。
- `INDEX (request_id)`：方便按请求排查。
- `INDEX (user_id)`：方便按用户排查。
- `INDEX (raw_schema_version)`：方便后续 raw schema 迁移排查。

`route_preference_training_samples` 保持当前核心结构不变。它的 `feature_schema_version` 是判断 route X 是否最新的来源；当它不等于 `RoutePreferenceFeatureSchema.VERSION` 时，该 candidate set 的样本需要从 raw snapshot 重建。

## Raw Snapshot 内容边界

raw snapshot 必须保存所有会影响 `RouteInputFeatureExtractor.extract(route, context)` 输出的数据。

本次边界包括：

- `RouteGenerateParam`：路线目标、交通偏好、预算、兴趣标签、饭点、出发时间、区域输入等。
- `RouteAreaDTO`：解析后的实际路线区域。
- `RouteWeatherDTO`：当次天气输入。
- `UserPreferenceProfileDTO`：当次用户画像。
- `interestTagCatalog` 和 `interestTags`：当次兴趣标签目录与请求命中的标签。
- `poiSemanticMappings`：当次 POI 语义映射规则。
- `poiCandidates`：当次大 POI 候选池。
- `poiLinearTraces`：当次 Linear Ranker trace。
- `selectedRoutes`：最终返回并参与 judgment 的路线。
- `segmentCosts`：当次已计算的路段成本材料。
- `warnings`：当次上下文警告，主要用于完整排查。

不依赖当前数据库重新查询这些材料；否则历史 route X 会被后续标签目录、语义映射、POI 缓存、天气、用户画像变化污染。

## 代码结构

新增或调整以下后端对象：

- `RoutePreferenceRawSnapshotPO`
  - 映射 `route_preference_raw_snapshots`。
- `RoutePreferenceRawSnapshotMapper`
  - 提供按 `candidate_set_id` upsert、查询 raw snapshot 的数据库方法。
- `RoutePreferenceRawSnapshotManage`
  - 只封装简单数据库操作，符合现有 manage 层风格。
- `RoutePreferenceRawSnapshotPayload`
  - 一个内部 DTO，承载冻结数据 JSON 反序列化后的结构。
- `RoutePreferenceRawSnapshotBuilder`
  - 从运行时 `RouteGenerationContext` 构建 raw snapshot payload。
- `RoutePreferenceFeatureRebuildService`
  - 提供通用方法：
    - `rebuildByCandidateSetId(UUID candidateSetId)`
    - `rebuildOutdatedSamples()`

调整以下现有类：

- `SaveRoutePreferenceTrainingSamplesStep`
  - 如果配置开启，先保存 raw snapshot。
  - 再从当前 context 生成 route X 并 upsert samples。
  - 这样新生成数据同时具备冻结输入和当前版本 X。
- `RoutePreferenceTrainingSampleMapper`
  - 增加按当前 `feature_schema_version` 查询过期 candidate set 的方法。
  - 保留现有 upsert samples 和 mark train ready 逻辑。
- `RoutePreferenceTrainingProperties`
  - 增加 raw snapshot 开关配置，默认开启，保证新生成的训练样本默认具备可重建能力。
  - 配置关闭时只跳过 raw snapshot 写入，不影响当前 samples 写入和 judgment 保存。
  - `SaveRoutePreferenceTrainingSamplesStep` 只通过配置对象判断是否写冻结表，不在 step 中硬编码环境策略。
  - 使用 Spring `@ConfigurationProperties(prefix = "route.preference.training")`，避免把布尔开关混入 `RouteScoringProperties` 的打分 YAML 必填数值体系。

## 流程设计

### 新路线生成流程

1. 路线生成 pipeline 完成筛选和校准后，进入 `saveRoutePreferenceTrainingSamples`。
2. 如果 raw snapshot 配置开启，`RoutePreferenceRawSnapshotBuilder` 从 `RouteGenerationContext` 组装 raw payload。
3. 如果 raw snapshot 配置开启，`RoutePreferenceRawSnapshotManage` 以 `candidate_set_id` upsert raw snapshot。
4. 对每条 selected route 调用 `RouteInputFeatureExtractor.extract(route, context)`。
5. `RoutePreferenceTrainingSampleManage.upsertGeneratedSample(...)` 写入 samples，版本为当前 `RoutePreferenceFeatureSchema.VERSION`。

### Judgment 保存流程

1. `RoutePreferenceTrainingServiceImpl.saveJudgment(...)` 保存 judgment。
2. 继续用当前逻辑把同一 `candidate_set_id` 下 samples 标记为 `TRAIN_READY`。
3. 不修改 judgment API，不把 label 写入 raw snapshot。

### 过期样本重建流程

1. 查询 `route_preference_training_samples` 中 `feature_schema_version <> RoutePreferenceFeatureSchema.VERSION` 的 candidate set。
2. 对每个 candidate set 查询 `route_preference_raw_snapshots`。
3. 如果 raw snapshot 存在：
   - 反序列化 raw payload。
   - 构造一个只用于 route X 的冻结上下文。
   - 对 `selected_routes_json` 中每条 route 重新调用 `RouteInputFeatureExtractor`。
   - upsert samples，版本写入当前 `RoutePreferenceFeatureSchema.VERSION`。
   - 保留同 candidate set 已有 label、sample weight 和 train-ready 状态。
4. 如果 raw snapshot 缺失：
   - 不从当前数据库猜测补数据。
   - 记录日志并跳过该 candidate set。
   - 后续可单独决定是否把该 candidate set 标记为 `FAILED`。

### 内部修复方法

新增内部方法用于修复 samples 表特征：

- `rebuildByCandidateSetId(UUID candidateSetId)`：按一个 candidate set 从 raw snapshot 重建 samples。
- `rebuildOutdatedSamples()`：扫描 samples 表中过期的 feature schema version，并逐个调用重建。

这两个方法不暴露为 controller API。本次通过单元测试直接调用内部 service，验证核心修复能力。

## 版本策略

- `RoutePreferenceFeatureSchema.VERSION` 表示 route X 派生结果版本。
- 新增 `RoutePreferenceRawSnapshotSchema.VERSION` 表示 raw snapshot JSON 结构版本。
- 每次 route X 维度、特征、归一化、阈值引用逻辑变化，都应更新 `RoutePreferenceFeatureSchema.VERSION`。
- 只有 raw payload 字段结构或语义发生不可兼容变化时，才更新 raw schema version。
- 过期判断以 samples 表的 `feature_schema_version` 为准，符合用户要求。

## 错误处理

- raw snapshot 序列化失败：路线生成应失败，不写半套 samples。
- samples 重建时 raw snapshot 缺失：跳过并记录 warning/error 日志。
- raw snapshot 反序列化失败：跳过该 candidate set，记录 candidate set 和 raw schema version。
- selected routes 为空：不写 samples；是否写 raw snapshot 可保持与当前 step 的行为一致，推荐不写。
- judgment 先到但 samples 缺失：保持现有风险，不在本次扩大处理范围。

## 验证方案

本次用户已明确允许新增单元测试。必须新增一个核心一致性测试：

- 构造同一份 `RouteGenerationContext` 和同一条 selected route。
- 用在线流程生成一次 route X snapshot。
- 用 raw snapshot builder 冻结该 context。
- 再从 raw snapshot 恢复并重建 route X snapshot。
- 断言两份 `RouteInputFeatureSnapshot` 的 `featureSchemaVersion`、`stopMatrixJson`、`segmentMatrixJson`、`routeDerivedVectorJson`、`contextCrossVectorJson`、`contextJson` 完全一致。

本次实现后至少运行：

- `./mvnw test` 或更窄的后端测试命令，取决于当前后端 wrapper/环境可用性。
- 针对已有 `RouteInputFeatureExtractorTest` 的测试命令，确保 route X 输出逻辑未破坏。
- Flyway migration 语法检查或后端启动级别检查，确保新增表 SQL 可执行。

## 兼容性

历史上没有 raw snapshot 的 candidate set 无法可靠重建 route X。本设计不会假装修复这些历史数据；它只保证新生成数据从引入 raw snapshot 后可重建。

后续如果必须处理旧数据，只能在明确接受“不完全复现”的前提下写一次性迁移脚本，从现有 samples 或业务表尽量回填 raw snapshot。该工作不纳入本次设计。
