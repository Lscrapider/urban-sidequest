package com.urbansidequest.backend.handler.route.linear;

import java.util.Set;

/**
 * 由 POI 的 typecode/keytag/rectag 经语义映射表解析出的 POI 自身语义事实。
 *
 * <p>多条映射行命中时：categoryGroups 取并集（multi-hot），语义 bool 取 OR，
 * weatherSensitive 取最大值（户外占优），poiTagHits 取 interest_tag_codes 并集。</p>
 */
public record PoiSemanticProfile(
        Set<String> categoryGroups,
        boolean classic,
        boolean local,
        boolean photoFriendly,
        boolean nightFriendly,
        boolean quiet,
        boolean hiddenGem,
        double weatherSensitive,
        Set<String> poiTagHits
) {

    public static PoiSemanticProfile empty() {
        return new PoiSemanticProfile(Set.of(), false, false, false, false, false, false, 0d, Set.of());
    }

    /** 是否消费类（W_budget 门控）：命中消费类大类组即视为消费类。 */
    public boolean isConsumable() {
        for (String group : this.categoryGroups) {
            if (LinearScoreConstants.CONSUMABLE_CATEGORY_GROUPS.contains(group)) {
                return true;
            }
        }
        return false;
    }

    /** 是否餐饮候选（mealMatch 用）：归入 FOOD 大类组即可。 */
    public boolean isMealCandidate() {
        return this.categoryGroups.contains("FOOD");
    }
}
