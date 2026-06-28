# Route Preference Model

路线偏好模型当前包含训练与预测两部分：

- `training/`：从 PostgreSQL 读取 `route_preference_training_samples` 的四块 routeInput，并从 `route_preference_judgments` 读取监督信号训练模型。
- `predict/`：加载训练产物，对单条或同一个 candidate set 的多条路线输出排序分、好坏概率和 reason code 分数。

## 输入 X

模型输入只包含四块：

```text
stop_matrix_json
segment_matrix_json
route_derived_vector_json
context_cross_vector_json
```

`context_json` 是原始上下文快照，用于审计、诊断和重建特征，不直接进入模型。`ranking_json`、`accepted_route_codes_json`、`rejected_route_codes_json`、`reason_codes_json`、`confidence`、`personalReview` 都是监督或调试信息，也不进入 X。

## 监督 Y

训练监督来自 `route_preference_judgments`：

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

训练按 `candidate_set_id` 对齐 route X 与 judgment。每条 completed judgment 会生成一组 `LabeledCandidateSet`；pair 只在同一 `candidate_set_id` 内构造，train/valid/test 也按 `candidate_set_id` 稳定哈希切分，避免新增数据后旧 candidate set 在验证集和测试集之间漂移。

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
RUN_MODE = "train"        # 真实训练，读取 PostgreSQL
# RUN_MODE = "self-check" # 自检，不连接数据库

feature_schema_version = "route_pref_v4"
output_dir = PROJECT_ROOT / "tmp" / "route-pref-training-output"
epochs = 12
batch_candidate_sets = 12
lr = 8e-4
weight_decay = 1e-3
dropout = 0.25
lambda_goodness = 0.60
lambda_reason = 0.025
best_metric = "valid/weightedPairwiseAccuracy"
patience = 3
reason_pos_weight_cap = 6.0
reason_pos_weight_min_support = 40
goodness_pos_weight_cap = 0.0
```

PyCharm 中直接运行 `training/train.py` 即可。真实训练默认输出到项目根目录下的
`tmp/route-pref-training-output`；自检默认输出到 `tmp/route-pref-training-self-check`。

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
  --feature-schema-version route_pref_v4
```

数据库连接只读取根目录 `.env` / `.env.dev` 中的 `ROUTE_PREF_DB_*` 或 `DATABASE_URL` 变量，不再支持 JSON fallback。可以使用 `ROUTE_PREF_DB_DSN` / `DATABASE_URL`，也可以使用 `ROUTE_PREF_DB_HOST`、`ROUTE_PREF_DB_PORT`、`ROUTE_PREF_DB_NAME`、`ROUTE_PREF_DB_USER`、`ROUTE_PREF_DB_PASSWORD` 和 `ROUTE_PREF_DB_CONNECT_TIMEOUT`。缺少数据库环境变量时直接报错。

也可以通过 `--input` 传入单条 routeInput、routeInput 数组，或包含 `rows` / `data` / `route_preference_training_samples` 的 JSON 对象。
