package com.urbansidequest.backend.handler.route.linear;

import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用户长期画像的标签匹配层归一化。
 *
 * <p>FOOD v1 按二级匹配层计算，避免菜系叶子和 POI 粗粒度解析之间漏匹配，
 * 同时保证同一 FOOD 祖先链最多贡献一次 affinity。</p>
 */
public final class InterestTagNormalizer {

    private static final String FOOD_ROOT = "FOOD";

    private InterestTagNormalizer() {
    }

    public static Map<String, BigDecimal> normalizedAffinities(
            Map<String, BigDecimal> affinities,
            Collection<InterestTagCatalogPO> catalog
    ) {
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        if (affinities == null || affinities.isEmpty()) {
            return normalized;
        }
        Map<String, InterestTagCatalogPO> catalogByCode = catalogByCode(catalog);
        for (Map.Entry<String, BigDecimal> entry : affinities.entrySet()) {
            String tag = normalizeForAffinity(entry.getKey(), catalogByCode);
            BigDecimal affinity = entry.getValue();
            if (tag == null || affinity == null) {
                continue;
            }
            normalized.merge(tag, affinity, BigDecimal::max);
        }
        return normalized;
    }

    public static Set<String> normalizedPoiTags(Set<String> tags, Collection<InterestTagCatalogPO> catalog) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags == null || tags.isEmpty()) {
            return normalized;
        }
        Map<String, InterestTagCatalogPO> catalogByCode = catalogByCode(catalog);
        for (String tag : tags) {
            String normalizedTag = normalizeForAffinity(tag, catalogByCode);
            if (normalizedTag != null) {
                normalized.add(normalizedTag);
            }
        }
        return normalized;
    }

    private static String normalizeForAffinity(String tag, Map<String, InterestTagCatalogPO> catalogByCode) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String tagCode = tag.trim();
        if (FOOD_ROOT.equals(tagCode)) {
            return null;
        }
        InterestTagCatalogPO tagMeta = catalogByCode.get(tagCode);
        if (!isFoodTag(tagCode, tagMeta)) {
            return tagCode;
        }
        if (tagMeta == null) {
            return null;
        }
        String parent = tagMeta.getParentTagCode();
        if (FOOD_ROOT.equals(parent)) {
            return tagCode;
        }
        if (parent != null && parent.startsWith(FOOD_ROOT + "_")) {
            return parent;
        }
        if (tagMeta.getRollupTagCodes() != null) {
            for (String rollup : tagMeta.getRollupTagCodes()) {
                InterestTagCatalogPO rollupMeta = catalogByCode.get(rollup);
                if (rollupMeta != null && FOOD_ROOT.equals(rollupMeta.getParentTagCode())) {
                    return rollup;
                }
            }
        }
        return null;
    }

    private static boolean isFoodTag(String tagCode, InterestTagCatalogPO tagMeta) {
        if (tagCode == null) {
            return false;
        }
        if (tagCode.startsWith(FOOD_ROOT + "_")) {
            return true;
        }
        if (tagMeta == null) {
            return false;
        }
        String parent = tagMeta.getParentTagCode();
        if (FOOD_ROOT.equals(parent) || (parent != null && parent.startsWith(FOOD_ROOT + "_"))) {
            return true;
        }
        return tagMeta.getRollupTagCodes() != null
                && tagMeta.getRollupTagCodes().stream().anyMatch(code -> FOOD_ROOT.equals(code) || code.startsWith(FOOD_ROOT + "_"));
    }

    private static Map<String, InterestTagCatalogPO> catalogByCode(Collection<InterestTagCatalogPO> catalog) {
        Map<String, InterestTagCatalogPO> byCode = new LinkedHashMap<>();
        if (catalog == null || catalog.isEmpty()) {
            return byCode;
        }
        for (InterestTagCatalogPO tag : catalog) {
            if (tag != null && tag.getTagCode() != null && !tag.getTagCode().isBlank()) {
                byCode.put(tag.getTagCode(), tag);
            }
        }
        return byCode;
    }
}
