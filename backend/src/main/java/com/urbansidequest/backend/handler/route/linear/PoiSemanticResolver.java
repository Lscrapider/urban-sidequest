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
 * 把单个 POI 映射成 {@link PoiSemanticProfile}：优先按 typecode / 精确 typecode 匹配；
 * keywordPatterns 只用于少量 EVENT 特例。命中多行时语义并集（见 PoiSemanticProfile）。
 */
@Component
public class PoiSemanticResolver {

    private static final String AMAP_TYPECODE_SEPARATOR_REGEX = "\\|";

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
        boolean mealCandidate = false;
        boolean restCandidate = false;
        boolean localExperienceCandidate = false;
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
            if (StrUtil.isNotBlank(mapping.getPrimaryCategoryGroup())) {
                categoryGroups.add(mapping.getPrimaryCategoryGroup());
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
            mealCandidate |= Boolean.TRUE.equals(mapping.getMealCandidate());
            restCandidate |= Boolean.TRUE.equals(mapping.getRestCandidate());
            localExperienceCandidate |= Boolean.TRUE.equals(mapping.getLocalExperienceCandidate());
            BigDecimal sensitivity = mapping.getWeatherSensitivity();
            if (sensitivity != null) {
                weatherSensitive = Math.max(weatherSensitive, sensitivity.doubleValue());
            }
        }

        if (!matchedAny) {
            return PoiSemanticProfile.empty();
        }
        return new PoiSemanticProfile(
                PoiSemanticProfile.resolvePrimaryCategoryGroup(categoryGroups),
                categoryGroups,
                classic,
                local,
                photoFriendly,
                nightFriendly,
                quiet,
                hiddenGem,
                mealCandidate,
                restCandidate,
                localExperienceCandidate,
                weatherSensitive,
                poiTagHits
        );
    }

    private boolean matches(PoiSemanticMappingPO mapping, String typecode, String haystack) {
        List<String> typecodeTokens = this.typecodeTokens(typecode);
        if (StrUtil.isNotBlank(typecode) && mapping.getExactTypecodes() != null) {
            for (String exactTypecode : mapping.getExactTypecodes()) {
                if (StrUtil.isNotBlank(exactTypecode)
                        && (exactTypecode.equals(typecode) || typecodeTokens.contains(exactTypecode))) {
                    return true;
                }
            }
        }
        if (!typecodeTokens.isEmpty() && mapping.getAmapTypePrefixes() != null) {
            for (String prefix : mapping.getAmapTypePrefixes()) {
                if (StrUtil.isBlank(prefix)) {
                    continue;
                }
                for (String token : typecodeTokens) {
                    if (token.startsWith(prefix)) {
                        return true;
                    }
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

    private List<String> typecodeTokens(String typecode) {
        if (StrUtil.isBlank(typecode)) {
            return List.of();
        }
        return java.util.Arrays.stream(typecode.split(AMAP_TYPECODE_SEPARATOR_REGEX))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
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
