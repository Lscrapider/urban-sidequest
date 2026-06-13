package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.param.MustVisitPointParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.service.route.GeoMath;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocalPoiCandidateProvider implements PoiCandidateProvider {

    @Override
    public List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context) {
        List<PoiCandidateDTO> candidates = new ArrayList<>();
        for (MustVisitPointParam mustVisitPoint : context.getGenerateParam().getMustVisitPoints()) {
            candidates.add(this.fromMustVisitPoint(mustVisitPoint));
        }
        candidates.addAll(this.buildSeedCandidates(context));
        return candidates;
    }

    private PoiCandidateDTO fromMustVisitPoint(MustVisitPointParam mustVisitPoint) {
        GeoPointDTO location = new GeoPointDTO(
                mustVisitPoint.getLocation().getLongitudeGcj02(),
                mustVisitPoint.getLocation().getLatitudeGcj02()
        );
        return new PoiCandidateDTO(
                "must-" + mustVisitPoint.getName(),
                mustVisitPoint.getAmapPoiId(),
                mustVisitPoint.getName(),
                "MUST_VISIT",
                location,
                BigDecimal.valueOf(4.6),
                null,
                List.of("MUST_VISIT"),
                true,
                "用户标记为" + mustVisitPoint.getPriority().name()
        );
    }

    private List<PoiCandidateDTO> buildSeedCandidates(RouteGenerationContext context) {
        GeoPointDTO center = context.getArea().center();
        List<PoiCandidateDTO> candidates = new ArrayList<>();
        List<InterestTagCatalogPO> tags = context.getInterestTags();
        if (tags.isEmpty()) {
            tags = List.of(this.defaultTag("SCENIC", "景点", "SCENIC"));
        }
        int index = 0;
        for (InterestTagCatalogPO tag : tags) {
            GeoPointDTO location = GeoMath.offset(center, 450 + index * 330, 360 - index * 210);
            String name = tag.getDisplayName() + "候选点";
            candidates.add(new PoiCandidateDTO(
                    "seed-" + tag.getTagCode(),
                    null,
                    name,
                    tag.getCategoryGroup(),
                    location,
                    BigDecimal.valueOf(4.4 - Math.min(index, 3) * 0.1),
                    "FOOD".equals(tag.getCategoryGroup()) ? 6800 + index * 1200 : null,
                    List.of(tag.getTagCode()),
                    false,
                    "匹配兴趣：" + tag.getDisplayName()
            ));
            index++;
        }
        candidates.add(new PoiCandidateDTO(
                "seed-rest",
                null,
                "路线休息点",
                "REST",
                GeoMath.offset(center, -650, 420),
                BigDecimal.valueOf(4.3),
                4200,
                List.of("COFFEE"),
                false,
                "用于控制路线节奏"
        ));
        return candidates;
    }

    private InterestTagCatalogPO defaultTag(String tagCode, String displayName, String categoryGroup) {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode(tagCode);
        tag.setDisplayName(displayName);
        tag.setCategoryGroup(categoryGroup);
        tag.setAmapTypeCodes(List.of());
        tag.setAmapKeywords(List.of());
        return tag;
    }
}
