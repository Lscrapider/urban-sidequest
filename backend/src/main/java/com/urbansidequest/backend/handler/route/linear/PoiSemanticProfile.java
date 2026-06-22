package com.urbansidequest.backend.handler.route.linear;

import java.util.List;
import java.util.Set;

/**
 * 由 POI 的 typecode/keytag/rectag 经语义映射表解析出的 POI 自身语义事实。
 *
 * <p>多条映射行命中时：categoryGroups 取并集（multi-hot），语义 bool 取 OR，
 * weatherSensitive 取最大值（户外占优），poiTagHits 取 interest_tag_codes 并集。</p>
 */
public record PoiSemanticProfile(
        String primaryCategoryGroup,
        Set<String> categoryGroups,
        boolean classic,
        boolean local,
        boolean photoFriendly,
        boolean nightFriendly,
        boolean quiet,
        boolean hiddenGem,
        boolean mealCandidate,
        boolean restCandidate,
        boolean localExperienceCandidate,
        double weatherSensitive,
        Set<String> poiTagHits
) {

    private static final List<String> CATEGORY_GROUP_PRIORITY = List.of(
            "FOOD",
            "DRINK",
            "SCENIC",
            "CULTURE",
            "SHOPPING",
            "MARKET",
            "ENTERTAINMENT",
            "SPORTS_LEISURE",
            "LIFE_SERVICE",
            "TRANSIT_INFRA",
            "EVENT",
            "UNKNOWN"
    );

    public static PoiSemanticProfile empty() {
        return new PoiSemanticProfile("UNKNOWN", Set.of(), false, false, false, false, false, false,
                false, false, false, 0d, Set.of());
    }

    public static String resolvePrimaryCategoryGroup(Set<String> categoryGroups) {
        if (categoryGroups == null || categoryGroups.isEmpty()) {
            return "UNKNOWN";
        }
        for (String group : CATEGORY_GROUP_PRIORITY) {
            if (categoryGroups.contains(group)) {
                return group;
            }
        }
        return categoryGroups.iterator().next();
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
        return this.mealCandidate || this.categoryGroups.contains("FOOD");
    }

    /** 是否休息补给候选：由饮品/咖啡/甜品等明确补给业态派生，不由 quiet 派生。 */
    public boolean isRestCandidate() {
        return this.restCandidate || this.categoryGroups.contains("DRINK");
    }

    public List<String> semanticTags() {
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();
        if (this.classic) {
            tags.add("CLASSIC");
        }
        if (this.local || this.localExperienceCandidate) {
            tags.add("LOCAL");
        }
        if (this.photoFriendly) {
            tags.add("PHOTO_FRIENDLY");
        }
        if (this.nightFriendly) {
            tags.add("NIGHT_FRIENDLY");
        }
        if (this.quiet) {
            tags.add("QUIET");
        }
        if (this.hiddenGem) {
            tags.add("HIDDEN_GEM");
        }
        return List.copyOf(tags);
    }
}
