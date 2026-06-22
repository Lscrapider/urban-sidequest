package com.urbansidequest.backend.handler.route.search;

import com.urbansidequest.backend.domain.constant.DateTimeFormatConstant;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrict;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlan;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticProfile;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.support.MealWindowSupport;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LlmRoutePromptPayloadFactory {

    private static final String TRANSIT_TYPE_BUS = "BUS";

    private static final String TRANSIT_TYPE_SUBWAY = "SUBWAY";

    private static final int MAX_ROUTE_COUNT = 5;

    private final RouteDistrictPlanner routeDistrictPlanner;

    private final PoiSemanticResolver poiSemanticResolver;

    public LlmRoutePromptPayloadFactory(RouteDistrictPlanner routeDistrictPlanner, PoiSemanticResolver poiSemanticResolver) {
        this.routeDistrictPlanner = routeDistrictPlanner;
        this.poiSemanticResolver = poiSemanticResolver;
    }

    Map<String, Object> toPromptPayload(RouteGenerationContext context) {
        RouteDistrictPlan districtPlan = this.routeDistrictPlanner.plan(context);
        Map<String, PoiCandidateDTO> candidatesById = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesById.put(candidate.poiId(), candidate);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", this.requestPayload(context));
        payload.put("mealWindowDefinitions", Arrays.stream(MealWindow.values())
                .map(mealWindow -> this.mealWindowPayload(
                        mealWindow.name(),
                        MealWindowSupport.startText(mealWindow),
                        MealWindowSupport.endText(mealWindow)
                ))
                .toList());
        Map<String, Object> transportPolicy = new LinkedHashMap<>();
        transportPolicy.put("profile", context.getGenerateParam().getTransportProfile());
        transportPolicy.put("districtBudget", districtPlan.effectiveDistrictBudget());
        transportPolicy.put("notes", "交通设施和空间距离只作为可达性参考，真实路线由后端调用高德路线 API 计算。WALK_BUS 只参考 BUS 设施，WALK_SUBWAY/BIKE_SUBWAY 只参考 SUBWAY 设施，WALK_TRANSIT 可混合参考 BUS/SUBWAY。");
        payload.put("transportPolicy", transportPolicy);
        payload.put("districtBudget", districtPlan.effectiveDistrictBudget());
        payload.put("districtOrder", districtPlan.districtOrder());
        payload.put("districtDistanceMatrix", this.districtDistanceMatrix(districtPlan));
        payload.put("districts", this.districtPayloads(districtPlan, candidatesById, context));
        return payload;
    }

    private Map<String, Object> requestPayload(RouteGenerationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("areaMode", context.getGenerateParam().getAreaMode());
        payload.put("areaLabel", context.getArea().areaLabel());
        payload.put("routeCityName", context.getGenerateParam().getRouteCityName());
        payload.put("routeCityAdcode", context.getGenerateParam().getRouteCityAdcode());
        payload.put("departureTime", this.localDateTimeText(context.getGenerateParam().getDepartureTime()));
        payload.put("durationMinutes", context.getGenerateParam().getDurationMinutes());
        payload.put("transportProfile", context.getGenerateParam().getTransportProfile());
        payload.put("routeGoal", context.getGenerateParam().getRouteGoal());
        payload.put("routeGoalPolicy", this.routeGoalPolicy(context.getGenerateParam().getRouteGoal()));
        payload.put("interestTags", context.getGenerateParam().getInterestTags());
        payload.put("mealWindows", context.getGenerateParam().getMealWindows());
        payload.put("mustVisitPoiIds", context.getPoiCandidates().stream()
                .filter(PoiCandidateDTO::mustVisit)
                .map(PoiCandidateDTO::poiId)
                .toList());
        Map<String, Integer> routeCountRange = new LinkedHashMap<>();
        routeCountRange.put("min", MAX_ROUTE_COUNT);
        routeCountRange.put("max", MAX_ROUTE_COUNT);
        payload.put("routeCountRange", routeCountRange);
        return payload;
    }

    private Map<String, Object> routeGoalPolicy(RouteGoal routeGoal) {
        if (routeGoal == null) {
            return this.routeGoalPolicy(
                    "均衡安排路线节奏、体验密度和交通余量。",
                    "不要为了单一主题牺牲必去点、饭点和片区顺序。",
                    "路线主题保持清晰但不过度偏向某一类 POI。"
            );
        }
        return switch (routeGoal) {
            case STEADY -> this.routeGoalPolicy(
                    "优先选择节奏稳定、风险低、交通余量充足的 POI 和顺序。",
                    "避免远距离跳转、过多备选点、营业不确定或信息缺失严重的点。",
                    "路线说明强调轻松、稳妥和顺路。"
            );
            case QUIET -> this.routeGoalPolicy(
                    "优先选择安静、低拥挤感、适合慢逛或停留的 POI。",
                    "少选强娱乐、夜场、嘈杂商业和高刺激活动点，除非它是用户必去点。",
                    "路线主题和节点理由突出安静、松弛、舒适。"
            );
            case CLASSIC -> this.routeGoalPolicy(
                    "优先选择经典地标、景点、文化展馆和城市代表性体验。",
                    "避免过多小众或只适合补位的点稀释经典主题。",
                    "路线主题突出第一次来城市也值得走的代表性。"
            );
            case LOCAL -> this.routeGoalPolicy(
                    "优先选择本地生活、街区、小吃、老字号、社区感和非游客化体验。",
                    "避免路线只由热门地标或纯观光点构成。",
                    "路线主题突出本地日常、烟火气和街区体验。"
            );
            case LOW_BUDGET -> this.routeGoalPolicy(
                    "该目标仅兼容历史样本；新请求应使用 budgetLevel 表达预算。",
                    "不要因为该目标改变召回。",
                    "按普通路线处理。"
            );
            case NIGHT -> this.routeGoalPolicy(
                    "优先选择夜间友好、晚间营业、夜景、演出或夜生活相关 POI。",
                    "避免安排明显只适合白天或夜间风险较高的点。",
                    "路线主题突出夜间氛围、灯光和晚间体验。"
            );
            case PHOTO -> this.routeGoalPolicy(
                    "优先选择拍照友好、视觉辨识度高、地标感或场景感强的 POI。",
                    "避免把路线做成纯餐饮或纯补给，除非饭点需要。",
                    "路线主题和节点理由突出取景、打卡和视觉记忆点。"
            );
        };
    }

    private Map<String, Object> mealWindowPayload(String type, String start, String end) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("start", start);
        payload.put("end", end);
        return payload;
    }

    private Map<String, Object> routeGoalPolicy(String focus, String avoid, String themeInstruction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("focus", focus);
        payload.put("avoid", avoid);
        payload.put("themeInstruction", themeInstruction);
        return payload;
    }

    private String localDateTimeText(LocalDateTime localDateTime) {
        return localDateTime == null ? null : DateTimeFormatConstant.BEIJING_LOCAL_DATE_TIME_FORMATTER.format(localDateTime);
    }

    private List<Map<String, Object>> districtPayloads(
            RouteDistrictPlan districtPlan,
            Map<String, PoiCandidateDTO> candidatesById,
            RouteGenerationContext context
    ) {
        Map<String, RouteDistrict> districtsById = new LinkedHashMap<>();
        for (RouteDistrict district : districtPlan.districts()) {
            districtsById.put(district.districtId(), district);
        }
        return districtPlan.districtOrder().stream()
                .map(districtsById::get)
                .filter(district -> district != null)
                .map(district -> this.districtPayload(district, candidatesById, context))
                .toList();
    }

    private Map<String, Object> districtPayload(
            RouteDistrict district,
            Map<String, PoiCandidateDTO> candidatesById,
            RouteGenerationContext context
    ) {
        List<PoiCandidateDTO> districtCandidates = district.poiIds().stream()
                .map(candidatesById::get)
                .filter(candidate -> candidate != null)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("districtId", district.districtId());
        payload.put("centroid", this.locationPayload(district.centroid()));
        payload.put("poiCount", districtCandidates.size());
        payload.put("dominantInterests", this.dominantInterests(districtCandidates, context));
        payload.put("pois", districtCandidates.stream()
                .map(candidate -> this.poiPayload(
                        candidate,
                        context.getGenerateParam().getTransportProfile(),
                        this.poiSemanticResolver.resolve(candidate, context.getPoiSemanticMappings())
                ))
                .toList());
        return payload;
    }

    private List<String> dominantInterests(List<PoiCandidateDTO> candidates, RouteGenerationContext context) {
        return candidates.stream()
                .flatMap(candidate -> this.poiSemanticResolver.resolve(candidate, context.getPoiSemanticMappings()).poiTagHits().stream())
                .distinct()
                .limit(3)
                .toList();
    }

    private Map<String, Object> poiPayload(
            PoiCandidateDTO candidate,
            TransportProfile transportProfile,
            PoiSemanticProfile semantic
    ) {
        List<TransitFacilityDTO> nearestTransit = this.relevantTransit(candidate, transportProfile);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiId", candidate.poiId());
        payload.put("name", candidate.name());
        payload.put("primaryCategoryGroup", semantic.primaryCategoryGroup());
        payload.put("categoryGroups", semantic.categoryGroups());
        payload.put("semanticTags", semantic.semanticTags());
        payload.put("poiTagHits", semantic.poiTagHits());
        payload.put("routeRoleHints", this.routeRoleHints(candidate, semantic));
        payload.put("recallSources", candidate.reasonSeed() == null ? List.of() : List.of(candidate.reasonSeed()));
        payload.put("rawType", candidate.rawType());
        payload.put("typecode", candidate.typecode());
        payload.put("keytag", candidate.keytag());
        payload.put("rectag", candidate.rectag());
        payload.put("mealCandidate", semantic.isMealCandidate());
        payload.put("restCandidate", semantic.isRestCandidate());
        payload.put("localExperienceCandidate", semantic.localExperienceCandidate());
        payload.put("location", this.locationPayload(candidate.location()));
        payload.put("description", candidate.description());
        payload.put("rating", candidate.amapRating());
        payload.put("avgPriceCent", candidate.avgPriceCent());
        payload.put("nearestTransit", nearestTransit);
        payload.put("transitAccessibility", this.transitAccessibility(candidate, nearestTransit));
        payload.put("mustVisit", candidate.mustVisit());
        return payload;
    }

    private List<String> routeRoleHints(PoiCandidateDTO candidate, PoiSemanticProfile semantic) {
        Set<String> hints = new LinkedHashSet<>();
        if (candidate.mustVisit()) {
            hints.add("MUST_VISIT");
        }
        if (semantic.isMealCandidate()) {
            hints.add("MEAL");
        }
        if (semantic.isRestCandidate()) {
            hints.add("REST");
        }
        if (semantic.localExperienceCandidate()) {
            hints.add("LOCAL");
        }
        if (semantic.photoFriendly()) {
            hints.add("PHOTO");
        }
        if (candidate.role() != null && PoiCandidateRole.ANCHOR != candidate.role()) {
            hints.add(candidate.role().name());
        }
        if (hints.isEmpty()) {
            hints.add(PoiCandidateRole.ANCHOR.name());
        }
        return List.copyOf(hints);
    }

    private Map<String, Integer> districtDistanceMatrix(RouteDistrictPlan districtPlan) {
        Map<String, Integer> matrix = new LinkedHashMap<>();
        List<RouteDistrict> districts = districtPlan.districts();
        for (int i = 0; i < districts.size(); i++) {
            for (int j = i + 1; j < districts.size(); j++) {
                RouteDistrict left = districts.get(i);
                RouteDistrict right = districts.get(j);
                matrix.put(
                        left.districtId() + "-" + right.districtId(),
                        com.urbansidequest.backend.handler.route.support.GeoMath.distanceMeters(left.centroid(), right.centroid())
                );
            }
        }
        return matrix;
    }

    private Map<String, Object> locationPayload(GeoPointDTO location) {
        return Map.of(
                "longitudeGcj02", location.longitudeGcj02(),
                "latitudeGcj02", location.latitudeGcj02()
        );
    }

    private List<TransitFacilityDTO> relevantTransit(PoiCandidateDTO candidate, TransportProfile transportProfile) {
        List<TransitFacilityDTO> nearestTransit = candidate.nearestTransit();
        if (nearestTransit == null || nearestTransit.isEmpty()) {
            return List.of();
        }
        String requiredType = this.requiredTransitType(transportProfile);
        if (requiredType == null) {
            return nearestTransit;
        }
        return nearestTransit.stream()
                .filter(transit -> requiredType.equals(transit.type()))
                .toList();
    }

    private String transitAccessibility(PoiCandidateDTO candidate, List<TransitFacilityDTO> nearestTransit) {
        if (nearestTransit == null || nearestTransit.isEmpty()) {
            return this.transportUnavailable(candidate) ? "UNKNOWN" : "LOW";
        }
        Integer nearestMeters = nearestTransit.get(0).distanceMeters();
        if (nearestMeters == null) {
            return this.transportUnavailable(candidate) ? "UNKNOWN" : "LOW";
        }
        if (nearestMeters <= 300) {
            return "HIGH";
        }
        if (nearestMeters <= 800) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean transportUnavailable(PoiCandidateDTO candidate) {
        return candidate.transitLookupStatus() == TransitLookupStatus.UNAVAILABLE
                || candidate.transitLookupStatus() == TransitLookupStatus.FAILED;
    }

    private String requiredTransitType(TransportProfile transportProfile) {
        if (transportProfile == null) {
            return null;
        }
        return switch (transportProfile) {
            case WALK_BUS -> TRANSIT_TYPE_BUS;
            case WALK_SUBWAY, BIKE_SUBWAY -> TRANSIT_TYPE_SUBWAY;
            case WALK_ONLY, WALK_TRANSIT, WALK_TAXI -> null;
        };
    }
}
