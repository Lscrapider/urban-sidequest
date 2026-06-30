# k Judgment Ablation 实验报告

## 目的

这次实验不是继续补判真实 `k=1`，而是把当前数据库里已经有 3 个 completed judgment 的 candidate set 当作“已经补到 k=3 的样本”，再反向构造 `pseudo-k1` 和 `pseudo-k2`，观察单票/双票标签相对三票共识的稳定性。

这个实验回答的是：在同一批 candidate set 上，`k=1 -> k=2 -> k=3` 对标签稳定性的边际收益有多大。它不直接回答“那 1102 个真实 k=1 全补后模型会涨多少”，但能先判断继续堆 LLM judge 是否有足够大信号。

## 执行口径

- 数据源：本机 PostgreSQL 中的 `route_preference_training_samples` 与 `route_preference_judgments`。
- feature schema：`route_pref_v5`。
- 样本筛选：completed judgment 数量恰好为 3，且存在 `TRAIN_READY` 训练样本。
- 命中样本：标签层最近一次运行快照为 896 个 candidate set；该数量会随数据库继续补判、重跑或样本状态变化而变化。
- 聚合逻辑：复用训练代码里的 `_parse_judgment` 与 `_build_labeled_candidate_set`，pair 权重仍为方向净差。
- 比较基准：同一 candidate set 的 `full-k3` 共识标签。
- 指标来源：`k_judgment_ablation_metrics.json`，脚本只输出结构化指标；本文档为人工整理和判断。

三组定义：

| 组 | 定义 | 本次 projection 数 |
|---|---|---:|
| `pseudo-k1` | 每个 full-k3 set 的 3 个单 judgment 投影 | 2688 |
| `pseudo-k2` | 每个 full-k3 set 的 3 个二 judgment 组合 | 2688 |
| `full-k3` | 完整 3 judgment 聚合 | 896 |

## 组级排序结果

| variant | candidate sets（本次快照） | routes/set | pairs/projection | pair weight/projection | top1 agreement | top2 hit | ndcg@3 |
|---|---:|---:|---:|---:|---:|---:|---:|
| `pseudo-k1` | 896 | 4.75 | 7.33 | 3.755 | 75.63% | 94.72% | 0.9519 |
| `pseudo-k2` | 896 | 4.75 | 7.80 | 6.761 | 84.00% | 97.66% | 0.9705 |
| `full-k3` | 896 | 4.75 | 8.35 | 9.803 | 100.00% | 100.00% | 1.0000 |

读数：

- 从 `pseudo-k1` 到 `pseudo-k2`，top1 对 full-k3 的一致率提升约 8.37pp。
- `pseudo-k2` 仍然只有 84.00% top1 agreement，说明第三票不只是轻微平滑；它会改变一批头名共识。
- 但 top2 hit 已经很高：`pseudo-k1` 是 94.72%，`pseudo-k2` 是 97.66%。也就是说，多票主要修的是相邻头名顺序，不太改变“正确答案是否在前二”。

## Pair 标签一致性

下表以 `full-k3` 中存在的 pair 为分母。`missing` 表示对应 pair 在降采样投影聚合后没有保留，通常来自方向冲突、margin 不足或单票没有生成该 pair。

| variant | gap | same | flipped | missing | weighted same | weighted flipped | weighted missing |
|---|---|---:|---:|---:|---:|---:|---:|
| `pseudo-k1` | all | 77.88% | 7.71% | 14.40% | 88.50% | 3.15% | 8.35% |
| `pseudo-k1` | gap=1 | 63.78% | 14.41% | 21.81% | 72.60% | 9.00% | 18.41% |
| `pseudo-k1` | gap=2 | 79.20% | 5.75% | 15.05% | 85.97% | 3.30% | 10.72% |
| `pseudo-k1` | gap=3 | 90.96% | 2.59% | 6.46% | 94.01% | 1.40% | 4.59% |
| `pseudo-k1` | gap>=4 | 97.06% | 0.67% | 2.27% | 98.17% | 0.31% | 1.51% |
| `pseudo-k2` | all | 88.08% | 3.15% | 8.77% | 95.97% | 0.81% | 3.22% |
| `pseudo-k2` | gap=1 | 74.78% | 6.23% | 18.99% | 84.52% | 2.75% | 12.74% |
| `pseudo-k2` | gap=2 | 92.69% | 2.14% | 5.17% | 96.76% | 0.73% | 2.51% |
| `pseudo-k2` | gap=3 | 98.32% | 0.82% | 0.86% | 99.37% | 0.25% | 0.39% |
| `pseudo-k2` | gap>=4 | 99.73% | 0.13% | 0.13% | 99.93% | 0.03% | 0.04% |

核心读数：

- 相邻 pair 是主要噪声区。`pseudo-k1` 的 gap=1 same rate 只有 63.78%，flip 14.41%，missing 21.81%。
- 加到二票后，gap=1 same rate 到 74.78%，flip 降到 6.23%，但 missing 仍有 18.99%。
- 远距离 pair 很稳定。`pseudo-k2` 在 gap>=3 基本已经接近 full-k3，gap>=4 的 same rate 是 99.73%。
- 加权口径比非加权口径好很多，说明训练里方向净差权重确实在把更稳定的 pair 放大，把不稳 pair 压低。

## 模型层 ablation

我追加跑了模型层实验。它和标签层是两次独立执行，数据库快照期间继续变化；模型层运行快照命中 893 个 full-k3 candidate set。

训练设置：

- 三组训练标签：`pseudo-k1`、`pseudo-k2`、`full-k3`。
- 每个 candidate set 在 `pseudo-k1/pseudo-k2` 中只选一个稳定 hash 投影，避免同一 set 多个标签视图同时进训练。
- train seeds：23、29、31。
- projection seed：0。
- 最大 epoch：12，early stopping 仍按各 variant 自己的 `valid/ndcg@3`。
- 统一评估：同一 split 上的 `full-k3` valid/test 标签。
- 指标来源：`k_judgment_model_ablation_metrics.json`。

full-k3 test 结果如下，表内为 mean±std：

| train labels | ndcg@3 | top1 | top2 hit | pair acc | weighted pair acc | gap=1 acc | weighted gap=1 acc |
|---|---:|---:|---:|---:|---:|---:|---:|
| `pseudo-k1` | 0.8468±0.0153 | 39.02%±3.18pp | 70.73%±5.80pp | 68.90%±1.72pp | 75.06%±2.85pp | 58.75%±0.89pp | 62.48%±0.79pp |
| `pseudo-k2` | 0.8529±0.0241 | 40.11%±1.57pp | 68.28%±5.13pp | 70.20%±2.60pp | 77.00%±3.77pp | 58.92%±0.99pp | 63.02%±0.77pp |
| `full-k3` | 0.8584±0.0205 | 44.16%±4.43pp | 70.54%±7.73pp | 70.60%±2.07pp | 77.53%±2.64pp | 58.91%±1.78pp | 62.88%±0.35pp |

模型层读数：

- `pseudo-k2` 相比 `pseudo-k1` 在 full-k3 test 上有小幅提升：ndcg@3 +0.0061，pair acc +1.30pp，weighted pair acc +1.94pp。
- `full-k3` 相比 `pseudo-k2` 继续小幅提升：ndcg@3 +0.0055，pair acc +0.40pp，weighted pair acc +0.53pp。
- top1 的方向是 `full-k3 > pseudo-k2 > pseudo-k1`，但 seed 间波动很大，不能单独据此判断。
- 关键的 gap=1 acc 基本没动：三组都在 58.8%-58.9% 左右。weighted gap=1 也只在 62.5%-63.0% 之间。

模型层结论：

标签层里 k 增加明显提高了相邻标签稳定性，但在当前模型和训练配置下，这个收益没有明显转化成相邻 pair accuracy。它更多体现在整体 pair/weighted pair 和 ndcg@3 的小幅改善上。

这意味着“全补 k=1 到 k=3”现在仍然不划算：即使在已经有 full-k3 的样本上训练，full-k3 相对 pseudo-k1 的 ndcg@3 也只是约 +0.0116，gap=1 几乎没有收益。真正的相邻瓶颈大概率不只是标签票数问题，还包括特征可分性、模型容量/目标函数、LLM 共识偏差或业务本身不可分。

## 判断

这次结果支持两个结论：

1. `k` 增加确实显著改善标签稳定性，尤其是相邻 pair。你的“k↑ 只是噪声里一点小修小补”说法偏保守；在同一批 set 上，单票和三票共识的相邻 pair 差距很大。
2. 收益主要集中在模糊相邻排序。远距离 pair 在 `pseudo-k1` 已经很稳，`pseudo-k2` 基本饱和；继续堆 judge 对这些 pair 没什么价值。

但这仍然不能推出“应该全补真实 k=1 到 k=3”。原因是：

- 这个实验把 `full-k3` 当目标，所以它衡量的是“更接近三票 LLM 共识”，不是“更接近真实用户偏好”。
- 相邻 pair 的 `missing` 很高，说明多票不仅改方向，也改变哪些 pair 被保留。训练收益不一定等于标签一致性收益。
- `pseudo-k2` 到 `full-k3` 仍有差距，但是否值得第三票，要看模型层多 seed 训练是否真的涨，而不是只看标签层。

## 建议

我不建议全补 1102 个真实 k=1 到 k=3。

模型层实验后，当前决策更明确：不全补。

如果还要投数据，我建议只做主动学习式小批量补判：

- 优先补模型 margin 很小、会影响 top3/top1 的真实 k=1 set。
- 不要补远距离明显分层的 set。
- 补完后继续用统一 full-k3 eval 标签看 gap=1 是否真的提升。

如果目标是上线，优先级应转向阈值标定、低置信 tie-band、线上反馈采集和真实用户闭环，而不是继续平均堆 LLM 票。
