# 文档分类

本目录按文档用途分层维护，避免长期设计文档、问题解决记录和原型资产混在一起。

## 长期设计文档

- `overview/`：产品与技术总览。允许记录目标态，但需要标注当前实现状态。
- `algorithm/`：当前算法、训练、标签、路线生成和用户画像的长期口径。
- `user/`：用户画像、问卷和画像表设计。

## 问题解决文档

- `problem-solving/YYYY-MM-DD/`：按日期归档一次性问题定位、修复方案、改动清单和落地记录。
- 这类文档保留历史决策过程，不作为长期算法主入口；当前实现口径以 `algorithm/` 和代码为准。

## UI 原型与设计交付物

- `ui/img/`：关键 UI 图片。
- `ui/stitch/`：Stitch 导出的目标态 HTML 与图片。它们是设计稿，不等同于 Android 当前已落地功能。

## Superpowers 计划

- `superpowers/plans/`：Superpowers implementation plan 归档，保留插件约定目录，不迁入 `problem-solving/`。

## 所有文档

| 分类 | 文档 | 说明 |
| --- | --- | --- |
| 项目入口 | [根目录 README](../README.md) | 项目概览、当前阶段、模块结构和文档入口。 |
| 项目入口 | [PRODUCT](../PRODUCT.md) | 项目级产品上下文，供设计和开发流程读取。 |
| 项目入口 | [DESIGN](../DESIGN.md) | Android UI 设计系统和视觉规范。 |
| 总览 | [产品设计](overview/product-design.md) | 产品定位、平台分工、核心体验闭环和当前 Android 落地边界。 |
| 总览 | [技术设计](overview/technical-design.md) | 技术选型、路线生成链路、容量、隔离和当前实现边界。 |
| 算法 | [推荐路径算法](algorithm/推荐路径算法.md) | 当前路线生成主链路、POI 预筛、LLM 编排、后端复核和高德校准。 |
| 算法 | [POI 线性打分矩阵取值设计](algorithm/poi/POI线性打分矩阵取值设计.md) | POI Linear Ranker 的特征、权重、规约、动态增量和 trace 口径。 |
| 算法 | [路线裁判与软拒绝设计](algorithm/route/路线裁判与软拒绝设计.md) | Route Judge、路线级可解释评分、soft reject 和后续 accept model 设计。 |
| 算法 | [路线偏好排序模型训练设计](algorithm/route/路线偏好排序模型训练设计.md) | 路线级偏好模型离线训练、pair 构造、loss 和导出设计。 |
| 算法 | [LLM 模拟用户路线选择设计](algorithm/route/LLM模拟用户路线选择设计.md) | 冷启动 LLM 模拟用户评价、judgments 表和 synthetic 训练信号。 |
| 算法归档 | [旧推荐路径算法 Beam Search](algorithm/archive/旧-推荐路径算法-beamsearch.md) | 旧 Beam Search 方案归档，用于和当前 LLM 编排主线对照。 |
| 用户 | [用户画像问卷与画像表设计](user/用户画像问卷与画像表设计.md) | 用户画像问卷、画像表、读取流程和 POI Linear 个性化输入边界。 |
| 问题解决 | [问题解决文档索引](problem-solving/README.md) | 按日期归档问题定位、修复方案、改动清单和落地记录。 |
| 问题解决 | [段级交通方式选择与校准修改方案](problem-solving/2026-06-21/段级交通方式选择与校准修改方案.md) | 段级交通方式预选择、真实路径校准、降级链和训练特征污染修复。 |
| 问题解决 | [非步行交通画像距离展开方案](problem-solving/2026-06-21/非步行交通画像距离展开方案.md) | 非步行交通下的 POI 入池、片区组织、距离口径和 route x 解耦。 |
| 问题解决 | [Route X 高德 Typecode 多样性特征改动清单](problem-solving/2026-06-22/RouteX高德Typecode多样性特征改动清单.md) | Route X 增加高德 `typecode` 多样性特征的落地清单。 |
| 问题解决 | [标签体系调整草案](problem-solving/2026-06-22/标签体系调整草案.md) | 标签体系重构、召回计划、POI 语义和训练 schema 迁移过程记录。 |
| 问题解决 | [饭点选择与 FOOD 标签 Gate 设计](problem-solving/2026-06-22/饭点选择与FOOD标签Gate设计.md) | `mealWindows` 与 `FOOD_*` 兴趣标签 gate 的契约和实现影响。 |
| 问题解决 | [高德 POI 分类与编码表](problem-solving/2026-06-22/高德POI分类与编码（中英文）_V1.06_20230208%202.xlsx) | 高德 POI 分类与编码参考表。 |
| UI | [UI 图片目录](ui/img/) | 核心 UI 方案图片。 |
| Superpowers | [模块骨架搭建实施计划](superpowers/plans/2026-06-13-module-scaffold.md) | 模块骨架搭建 implementation plan 归档。 |
