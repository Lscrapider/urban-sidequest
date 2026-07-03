# Route Preference Model

路线偏好模型当前包含训练与预测两部分：

- `training/`：从 MinIO 读取指定 `ROUTE_PREF_DATASET_VERSION` 的 `manifest.json`、Parquet 和 raw snapshot 对象，训练路线偏好模型。
- `predict/`：加载训练产物，对单条或同一个 candidate set 的多条路线输出排序分、好坏概率和 reason code 分数。

## 输入 X

模型输入只包含五块：

```text
stop_matrix_json
segment_matrix_json
route_derived_vector_json
context_cross_vector_json
intra_set_vector_json
```

`context_json` 是原始上下文快照，用于审计、诊断和重建特征，不直接进入模型。`ranking_json`、`accepted_route_codes_json`、`rejected_route_codes_json`、`reason_codes_json`、`confidence`、`personalReview` 都是监督或调试信息，也不进入 X。

## 数据集与监督 Y

训练数据来自 MinIO 中的版本化 dataset：

```text
route-preference/
  datasets/
    {ROUTE_PREF_DATASET_VERSION}/
      manifest.json
      training_samples.parquet
      judgments.parquet
      raw_snapshot_index.parquet
      raw_snapshots/
```

`training_samples.parquet` 提供 route X，`judgments.parquet` 提供监督 Y：

```text
ranking_json
accepted_route_codes_json
rejected_route_codes_json
reason_codes_json
confidence
judge_type
judge_model
judge_prompt_version
```

训练按 `candidate_set_id` 对齐 route X 与 judgment。每条 completed judgment 会生成一组 `LabeledCandidateSet`；pair 只在同一 `candidate_set_id` 内构造，train/valid/test 按 `candidate_set_id` 切分。旧 PostgreSQL 三张训练表只作为一次性迁移来源，线上和本地训练入口不再直接读取产品库。

## 输出

每条路线预测输出：

```text
routePreferenceScore   # 原始排序分，不做 sigmoid
routeGoodnessProb      # sigmoid(routeGoodnessLogit)
reasonCodeScores       # sigmoid(reasonCodeLogits)，固定 9 个 reason code
```

预测结果按 `routePreferenceScore` 降序排列。`routeGoodnessProb` 表示路线值得推荐的概率；`reasonCodeScores` 用于解释问题原因和生成端自我修正。

## Reason Codes

顺序固定为：

```text
LOW_INTEREST_COVERAGE
WEAK_GOAL_FIT
BAD_TIME_STRUCTURE
HIGH_FATIGUE
BAD_SPATIAL_FLOW
LOW_ROUTE_DIVERSITY
REPETITIVE_POI_TYPE
BUDGET_MISMATCH
HIGH_ROUTE_RISK
```

未知 reason code 默认报错；如需跳过非法 judgment，在 `training/train.py` 顶部的 `TRAIN_CONFIG.skip_invalid_judgments` 改为 `True`。

## 训练

当前训练入口面向本地 PyCharm 直接运行，不再通过 CLI 参数传配置。运行前在
`training/train.py` 顶部修改静态配置：

`TRAIN_CONFIG` 里的常改字段：

```text
RUN_MODE = "train"        # 真实训练，读取 MinIO dataset
# RUN_MODE = "self-check" # 自检，不连接数据库

feature_schema_version = "route_pref_v5"
output_dir = PROJECT_ROOT / "tmp" / "route-pref-training-output"
epochs = 12
batch_candidate_sets = 12
lr = 8e-4
weight_decay = 1e-3
dropout = 0.30
lambda_goodness = 0.30
lambda_reason = 0.10
best_metric = "valid/ndcg@3"
patience = 3
reason_pos_weight_cap = 6.0
reason_pos_weight_min_support = 40
goodness_pos_weight_cap = 0.0
```

当前 v5 定稿默认值集中定义在 `training/schema.py`，`training/train.py`
只负责组装 `TRAIN_CONFIG`。goodness 线上概率使用
`sigmoid(routeGoodnessLogit / goodnessTemperature)`，当前
`goodnessTemperature=1.41`，`GOOD_ROUTE_THRESHOLD=0.5` 是校准后概率上的默认业务阈值。

PyCharm 中直接运行 `training/train.py` 即可。真实训练默认输出到项目根目录下的
`tmp/route-pref-training-output`；自检默认输出到 `tmp/route-pref-training-self-check`。

命令行运行示例：

```bash
PYTHONPATH=ai-python/src \
ROUTE_PREF_MINIO_ENDPOINT=http://localhost:9000 \
ROUTE_PREF_MINIO_ACCESS_KEY=urban_sidequest \
ROUTE_PREF_MINIO_SECRET_KEY=urban_sidequest_dev_password \
ROUTE_PREF_MINIO_BUCKET=urban-sidequest-training \
ROUTE_PREF_MINIO_PREFIX=route-preference \
ROUTE_PREF_DATASET_VERSION=2026-07-03-v1 \
python3 -m urban_sidequest_ai.models.route_preference.training
```

`ROUTE_PREF_DATASET_VERSION` 只用于离线读取固定版本；线上创建路线、保存 LLM judgment 或用户后补评价时不需要传版本号。线上后端永远写入 MinIO `ingest/` 区，离线任务再把 ingest 冻结成新的 dataset 版本。

第一次迁移旧 PG 训练表时使用一次性导出脚本：

```bash
PYTHONPATH=ai-python/src \
ROUTE_PREF_DATASET_VERSION=2026-07-03-v1 \
python3 -m urban_sidequest_ai.models.route_preference.training.export_pg_to_minio_dataset
```

该脚本需要同时配置 `ROUTE_PREF_DB_*` 和 `ROUTE_PREF_MINIO_*`，只负责导出，不删除 PG 表。迁移验证完成后再执行数据库 migration 删除旧训练表。

日常增量构建下一版 dataset 时，如果要在已有 dataset 基础上合并线上新增 ingest，使用：

```bash
PYTHONPATH=ai-python/src \
ROUTE_PREF_MINIO_ENDPOINT=http://localhost:9000 \
ROUTE_PREF_MINIO_ACCESS_KEY=urban_sidequest \
ROUTE_PREF_MINIO_SECRET_KEY=urban_sidequest_dev_password \
ROUTE_PREF_MINIO_BUCKET=urban-sidequest-training \
ROUTE_PREF_MINIO_PREFIX=route-preference \
ROUTE_PREF_BASE_DATASET_VERSION=2026-07-03-v1 \
ROUTE_PREF_DATASET_VERSION=2026-07-03-v2 \
python3 -m urban_sidequest_ai.models.route_preference.training.dataset_builder
```

其中 `ROUTE_PREF_BASE_DATASET_VERSION` 是上一版训练集，`ROUTE_PREF_DATASET_VERSION` 是要生成的新版本。构建成功并写出 manifest 后，脚本会删除已处理的 ingest 对象，保持写入区干净。

训练产物包括：

```text
history.jsonl
route-preference.pt
feature_schema.json
reason_codes.json
model_card.json
route-preference.onnx          # 未传 --skip-onnx 时
loss_curves.png
ranking_metrics.png
goodness_metrics.png
reason_metrics.png
```

## 预测

对同一个 candidate set 的多条路线一起预测：

```bash
PYTHONPATH=ai-python/src python3 -m urban_sidequest_ai.models.route_preference.predict \
  --model-dir tmp/route-pref-training-output \
  --candidate-set-id <candidate-set-id> \
  --feature-schema-version route_pref_v5
```

预测按 `--candidate-set-id` 从 `ROUTE_PREF_DATASET_VERSION` 指向的 MinIO dataset 读取样本；如果传 `--input`，则不访问 MinIO。

也可以通过 `--input` 传入单条 routeInput、routeInput 数组，或包含 `rows` / `data` / `route_preference_training_samples` 的 JSON 对象。
