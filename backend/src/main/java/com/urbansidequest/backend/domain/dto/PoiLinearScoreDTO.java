package com.urbansidequest.backend.domain.dto;

/**
 * Linear Ranker 对单个 POI 的打分结果：8 个可解释子分数 + 最终 linearScore（已 clamp 到 [-1,1]）。
 *
 * <p>保留 8 个子分数便于按设计文档 §9 sanity 预期逐条核对、定位哪行权重/常量偏了。</p>
 */
public record PoiLinearScoreDTO(
        String poiId,
        String name,
        double interestScore,
        double goalScore,
        double qualityScore,
        double transportScore,
        double distanceCost,
        double budgetCost,
        double riskCost,
        double personalizationScore,
        double linearScore
) {
}
