package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.param.MustVisitPointParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocalPoiCandidateProvider implements PoiCandidateProvider {

    private static final ZoneId ROUTE_ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);

    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);

    private static final LocalTime DINNER_START = LocalTime.of(17, 30);

    private static final LocalTime DINNER_END = LocalTime.of(20, 0);

    private static final int SHORT_ROUTE_MINUTES = 180;

    @Override
    public List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context) {
        List<PoiCandidateDTO> candidates = new ArrayList<>();
        for (MustVisitPointParam mustVisitPoint : context.getGenerateParam().getMustVisitPoints()) {
            candidates.add(this.fromMustVisitPoint(mustVisitPoint));
        }
        candidates.addAll(this.buildAnchorCandidates(context));
        candidates.addAll(this.buildFunctionalCandidates(context));
        candidates.addAll(this.buildBackupCandidates(context));
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
                PoiCandidateRole.MUST_VISIT,
                location,
                null,
                "用户指定的必去地点，会优先安排进路线。",
                BigDecimal.valueOf(4.6),
                null,
                List.of("MUST_VISIT"),
                List.of(),
                true,
                "用户标记为" + switch (mustVisitPoint.getPriority()) {
                    case MUST -> "必须到达";
                    case PREFER -> "优先安排";
                }
        );
    }

    private List<PoiCandidateDTO> buildAnchorCandidates(RouteGenerationContext context) {
        GeoPointDTO center = context.getArea().center();
        List<PoiCandidateDTO> candidates = new ArrayList<>();
        List<InterestTagCatalogPO> tags = context.getInterestTags();
        if (tags.isEmpty()) {
            tags = List.of(
                    this.defaultTag("SCENIC", "城市地标", "SCENIC"),
                    this.defaultTag("MUSEUM", "展馆", "CULTURE")
            );
        }

        int index = 0;
        for (InterestTagCatalogPO tag : tags) {
            PoiCandidateRole role = this.resolveInterestRole(tag);
            candidates.add(this.buildCandidate(
                    "seed-" + tag.getTagCode(),
                    this.localNameForTag(tag),
                    tag.getCategoryGroup(),
                    role,
                    center,
                    450 + index * 330,
                    360 - index * 210,
                    BigDecimal.valueOf(4.4 - Math.min(index, 3) * 0.1),
                    "FOOD".equals(tag.getCategoryGroup()) ? 6800 + index * 1200 : null,
                    List.of(tag.getTagCode()),
                    "匹配兴趣：" + tag.getDisplayName()
            ));
            index++;
        }

        if (candidates.stream().noneMatch(candidate -> PoiCandidateRole.ANCHOR == candidate.role())) {
            candidates.add(this.buildCandidate(
                    "seed-anchor-fallback",
                    "城市观景公园",
                    "SCENIC",
                    PoiCandidateRole.ANCHOR,
                    center,
                    -520,
                    -260,
                    BigDecimal.valueOf(4.5),
                    null,
                    List.of("SCENIC"),
                    "用于保证路线有核心游览内容"
            ));
        }
        if (RouteGoal.LOCAL == context.getGenerateParam().getRouteGoal()) {
            candidates.add(this.buildCandidate(
                    "seed-local-street",
                    "本地生活街巷",
                    "LOCAL",
                    PoiCandidateRole.LOCAL,
                    center,
                    720,
                    -540,
                    BigDecimal.valueOf(4.3),
                    5200,
                    List.of("LOCAL"),
                    "匹配路线目标：地道烟火"
            ));
        }
        if (RouteGoal.NIGHT == context.getGenerateParam().getRouteGoal()) {
            candidates.add(this.buildCandidate(
                    "seed-night-view",
                    "城市夜景平台",
                    "NIGHT",
                    PoiCandidateRole.ANCHOR,
                    center,
                    -820,
                    620,
                    BigDecimal.valueOf(4.4),
                    null,
                    List.of("NIGHT"),
                    "匹配路线目标：夜游"
            ));
        }
        return candidates;
    }

    private List<PoiCandidateDTO> buildFunctionalCandidates(RouteGenerationContext context) {
        GeoPointDTO center = context.getArea().center();
        List<PoiCandidateDTO> candidates = new ArrayList<>();
        if (this.overlaps(context, LUNCH_START, LUNCH_END)) {
            candidates.add(this.buildCandidate(
                    "seed-lunch",
                    "附近风味餐厅",
                    "FOOD",
                    PoiCandidateRole.MEAL,
                    center,
                    -260,
                    680,
                    BigDecimal.valueOf(4.2),
                    7200,
                    List.of("FOOD"),
                    "路线覆盖午餐时间，适合作为用餐停留"
            ));
        }
        if (this.overlaps(context, DINNER_START, DINNER_END)) {
            candidates.add(this.buildCandidate(
                    "seed-dinner",
                    "本地晚餐小馆",
                    "FOOD",
                    PoiCandidateRole.MEAL,
                    center,
                    860,
                    260,
                    BigDecimal.valueOf(4.3),
                    8600,
                    List.of("FOOD"),
                    "路线覆盖晚餐时间，适合作为用餐停留"
            ));
        }
        if (context.getGenerateParam().getDurationMinutes() >= SHORT_ROUTE_MINUTES) {
            candidates.add(this.buildCandidate(
                    "seed-rest",
                    "街角咖啡馆",
                    "REST",
                    PoiCandidateRole.REST,
                    center,
                    -650,
                    420,
                    BigDecimal.valueOf(4.3),
                    4200,
                    List.of("COFFEE"),
                    "适合控制路线节奏，中途休息补给"
            ));
        }
        return candidates;
    }

    private List<PoiCandidateDTO> buildBackupCandidates(RouteGenerationContext context) {
        GeoPointDTO center = context.getArea().center();
        return List.of(
                this.buildCandidate(
                        "seed-backup-scenic",
                        "邻近景观公园",
                        "SCENIC",
                        PoiCandidateRole.BACKUP,
                        center,
                        1030,
                        -160,
                        BigDecimal.valueOf(4.1),
                        null,
                        List.of("SCENIC"),
                        "用于异常替换和路线兜底"
                ),
                this.buildCandidate(
                        "seed-backup-rest",
                        "安静咖啡馆",
                        "REST",
                        PoiCandidateRole.BACKUP,
                        center,
                        -980,
                        -380,
                        BigDecimal.valueOf(4.0),
                        3600,
                        List.of("COFFEE"),
                        "用于异常替换和节奏兜底"
                )
        );
    }

    private PoiCandidateDTO buildCandidate(
            String poiId,
            String name,
            String category,
            PoiCandidateRole role,
            GeoPointDTO center,
            int offsetEastMeters,
            int offsetNorthMeters,
            BigDecimal rating,
            Integer avgPriceCent,
            List<String> matchedInterestTags,
            String reasonSeed
    ) {
        return new PoiCandidateDTO(
                poiId,
                null,
                name,
                category,
                role,
                GeoMath.offset(center, offsetEastMeters, offsetNorthMeters),
                null,
                this.localDescription(name, category, reasonSeed, avgPriceCent),
                rating,
                avgPriceCent,
                matchedInterestTags,
                List.of(),
                false,
                reasonSeed
        );
    }

    private String localNameForTag(InterestTagCatalogPO tag) {
        String categoryGroup = tag.getCategoryGroup();
        if ("CULTURE".equals(categoryGroup)) {
            return "城市展览馆";
        }
        if ("SCENIC".equals(categoryGroup)) {
            return "城市观景公园";
        }
        if ("FOOD".equals(categoryGroup)) {
            return "本地风味餐厅";
        }
        if ("LOCAL".equals(categoryGroup)) {
            return "本地生活街巷";
        }
        return tag.getDisplayName() + "精选点";
    }

    private String localDescription(String name, String category, String reasonSeed, Integer avgPriceCent) {
        List<String> parts = new ArrayList<>();
        parts.add(name + "是当前范围内的路线兜底地点");
        parts.add(reasonSeed);
        if (avgPriceCent != null) {
            parts.add("预估人均约 " + avgPriceCent / 100 + " 元");
        }
        if ("FOOD".equals(category)) {
            parts.add("适合补充午晚餐安排");
        } else if ("REST".equals(category)) {
            parts.add("适合短暂停留和补给");
        }
        return String.join("，", parts) + "。";
    }

    private PoiCandidateRole resolveInterestRole(InterestTagCatalogPO tag) {
        if ("FOOD".equals(tag.getCategoryGroup())) {
            return PoiCandidateRole.MEAL;
        }
        return PoiCandidateRole.ANCHOR;
    }

    private boolean overlaps(RouteGenerationContext context, LocalTime windowStart, LocalTime windowEnd) {
        LocalDateTime routeStart = LocalDateTime.ofInstant(context.getGenerateParam().getDepartureTime(), ROUTE_ZONE);
        LocalDateTime routeEnd = routeStart.plusMinutes(context.getGenerateParam().getDurationMinutes());
        LocalDate cursorDate = routeStart.toLocalDate();
        while (!cursorDate.isAfter(routeEnd.toLocalDate())) {
            LocalDateTime candidateStart = LocalDateTime.of(cursorDate, windowStart);
            LocalDateTime candidateEnd = LocalDateTime.of(cursorDate, windowEnd);
            if (routeStart.isBefore(candidateEnd) && routeEnd.isAfter(candidateStart)) {
                return true;
            }
            cursorDate = cursorDate.plusDays(1);
        }
        return false;
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
