package com.urbansidequest.backend.handler.route.linear;

import java.util.Set;

/**
 * Linear Ranker 的结构契约常量。
 *
 * <p>打分权重、阈值、规约化参考尺度和缺失默认值均由本地机密配置注入。</p>
 */
public final class LinearScoreConstants {

    private LinearScoreConstants() {
    }

    // —— W_budget 门控的消费类内部业态组（非消费类整组 0，避免误判景点预算）——
    public static final Set<String> CONSUMABLE_CATEGORY_GROUPS = Set.of("FOOD", "DRINK", "SHOPPING", "ENTERTAINMENT");
}
