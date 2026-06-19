package com.urbansidequest.backend.handler.route.linear;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 把单个 POI 映射成 {@link PoiSemanticProfile}：先按 typecode 前缀匹配，再按 keytag/rectag/name/category
 * 关键词补充。命中多行时语义并集（见 PoiSemanticProfile）。
 */
@Component
public class PoiSemanticResolver {

    public PoiSemanticProfile resolve(PoiCandidateDTO candidate, List<PoiSemanticMappingPO> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return PoiSemanticProfile.empty();
        }
        Set<String> categoryGroups = new LinkedHashSet<>();
        Set<String> poiTagHits = new LinkedHashSet<>();
        boolean classic = false;
        boolean local = false;
        boolean photoFriendly = false;
        boolean nightFriendly = false;
        boolean quiet = false;
        boolean hiddenGem = false;
        double weatherSensitive = 0d;
        boolean matchedAny = false;

        String typecode = candidate.typecode();
        String haystack = this.keywordHaystack(candidate);

        for (PoiSemanticMappingPO mapping : mappings) {
            if (!this.matches(mapping, typecode, haystack)) {
                continue;
            }
            matchedAny = true;
            if (StrUtil.isNotBlank(mapping.getCategoryGroup())) {
                categoryGroups.add(mapping.getCategoryGroup());
            }
            if (mapping.getInterestTagCodes() != null) {
                poiTagHits.addAll(mapping.getInterestTagCodes());
            }
            classic |= Boolean.TRUE.equals(mapping.getClassic());
            local |= Boolean.TRUE.equals(mapping.getLocal());
            photoFriendly |= Boolean.TRUE.equals(mapping.getPhotoFriendly());
            nightFriendly |= Boolean.TRUE.equals(mapping.getNightFriendly());
            quiet |= Boolean.TRUE.equals(mapping.getQuiet());
            hiddenGem |= Boolean.TRUE.equals(mapping.getHiddenGem());
            BigDecimal sensitivity = mapping.getWeatherSensitivity();
            if (sensitivity != null) {
                weatherSensitive = Math.max(weatherSensitive, sensitivity.doubleValue());
            }
        }

        if (!matchedAny) {
            return PoiSemanticProfile.empty();
        }
        return new PoiSemanticProfile(
                categoryGroups,
                classic,
                local,
                photoFriendly,
                nightFriendly,
                quiet,
                hiddenGem,
                weatherSensitive,
                poiTagHits
        );
    }

    private boolean matches(PoiSemanticMappingPO mapping, String typecode, String haystack) {
        if (StrUtil.isNotBlank(typecode) && mapping.getAmapTypePrefixes() != null) {
            for (String prefix : mapping.getAmapTypePrefixes()) {
                if (StrUtil.isNotBlank(prefix) && typecode.startsWith(prefix)) {
                    return true;
                }
            }
        }
        if (StrUtil.isNotBlank(haystack) && mapping.getKeywordPatterns() != null) {
            for (String keyword : mapping.getKeywordPatterns()) {
                if (StrUtil.isNotBlank(keyword) && haystack.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String keywordHaystack(PoiCandidateDTO candidate) {
        StringBuilder builder = new StringBuilder();
        this.appendIfPresent(builder, candidate.keytag());
        this.appendIfPresent(builder, candidate.rectag());
        this.appendIfPresent(builder, candidate.name());
        this.appendIfPresent(builder, candidate.category());
        this.appendIfPresent(builder, candidate.rawType());
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (StrUtil.isNotBlank(value)) {
            builder.append(value).append(' ');
        }
    }
}
