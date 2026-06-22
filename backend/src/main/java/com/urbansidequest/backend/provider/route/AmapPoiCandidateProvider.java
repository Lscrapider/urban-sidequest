package com.urbansidequest.backend.provider.route;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.AmapPoiSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.param.MustVisitPointParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.domain.po.PoiRecallPlanConfigPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.AmapPoiSearchCacheManage;
import com.urbansidequest.backend.manage.PoiRecallPlanConfigManage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class AmapPoiCandidateProvider implements PoiCandidateProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapPoiCandidateProvider.class);

    private static final String SEARCH_TYPE_AROUND = "AROUND";

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final int FIRST_PAGE_NUM = 1;

    private static final int PAGE_SIZE = 25;

    private static final int MAX_PAGE_NUM = 4;

    private static final int MAX_CANDIDATE_COUNT = 100;

    private static final int MIN_CANDIDATE_COUNT = 3;

    private static final int POI_CACHE_TTL_HOURS = 24;

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);

    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);

    private static final LocalTime DINNER_START = LocalTime.of(17, 30);

    private static final LocalTime DINNER_END = LocalTime.of(20, 0);

    private static final int SHORT_ROUTE_MINUTES = 180;

    private static final int HALF_DAY_ROUTE_MINUTES = 360;

    private static final String PLAN_TYPE_INTEREST_TAG = "INTEREST_TAG";

    private static final String PLAN_TYPE_MEAL_LUNCH = "MEAL_LUNCH";

    private static final String PLAN_TYPE_MEAL_DINNER = "MEAL_DINNER";

    private static final String PLAN_TYPE_REST_NEED = "REST_NEED";

    private static final String PLAN_TYPE_BACKUP = "BACKUP";

    private static final String PLAN_TYPE_DEFAULT = "DEFAULT";

    private static final Set<String> EVENT_KEYWORD_PLAN_CODES = Set.of(
            "INTEREST_EVENT_CONCERT",
            "INTEREST_EVENT_LIVE",
            "INTEREST_EVENT_MUSIC_FESTIVAL"
    );

    private static final List<String> EVENT_KEYWORDS = List.of("演唱会", "live", "音乐节");

    private static final Map<String, List<String>> EVENT_KEYWORD_TYPES = Map.of(
            "演唱会", List.of("220104", "080101", "080105"),
            "live", List.of("220104", "080101", "080105"),
            "音乐节", List.of("220104")
    );

    private final AmapApi amapApi;

    private final AmapPoiSearchCacheManage amapPoiSearchCacheManage;

    private final PoiRecallPlanConfigManage poiRecallPlanConfigManage;

    private final ObjectMapper objectMapper;

    public AmapPoiCandidateProvider(
            AmapApi amapApi,
            AmapPoiSearchCacheManage amapPoiSearchCacheManage,
            PoiRecallPlanConfigManage poiRecallPlanConfigManage,
            ObjectMapper objectMapper
    ) {
        this.amapApi = amapApi;
        this.amapPoiSearchCacheManage = amapPoiSearchCacheManage;
        this.poiRecallPlanConfigManage = poiRecallPlanConfigManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context) {
        if (!this.amapApi.isAvailable()) {
            throw new IllegalStateException("未配置高德 Web Key，无法生成真实路线");
        }
        try {
            return this.loadAmapCandidates(context);
        } catch (RestClientException exception) {
            throw new IllegalStateException("高德 POI 查询失败，请稍后重试", exception);
        }
    }

    private List<PoiCandidateDTO> loadAmapCandidates(RouteGenerationContext context) {
        Map<String, PoiCandidateDTO> candidates = new LinkedHashMap<>();
        for (MustVisitPointParam mustVisitPoint : context.getGenerateParam().getMustVisitPoints()) {
            this.putCandidate(candidates, this.fromMustVisitPoint(mustVisitPoint));
        }

        List<PoiSearchPlan> activePlans = this.buildSearchPlans(context);
        for (int pageNum = FIRST_PAGE_NUM; pageNum <= MAX_PAGE_NUM && candidates.size() < MAX_CANDIDATE_COUNT; pageNum++) {
            List<PoiSearchPlan> nextActivePlans = new ArrayList<>();
            for (PoiSearchPlan plan : activePlans) {
                if (candidates.size() >= MAX_CANDIDATE_COUNT) {
                    break;
                }
                PoiSearchPlan pagedPlan = plan.withQuery(this.withPageNum(plan.query(), pageNum));
                JsonNode response;
                try {
                    response = this.searchWithCache(pagedPlan.query());
                } catch (RestClientException exception) {
                    context.addWarning("部分高德 POI 查询失败，已跳过搜索计划：" + pagedPlan.reasonSeed());
                    LOGGER.warn(
                            "高德 POI 查询计划失败，searchType={}，types={}，keywords={}，pageNum={}，pageSize={}",
                            pagedPlan.query().searchType(),
                            pagedPlan.query().types(),
                            pagedPlan.query().keywords(),
                            pagedPlan.query().pageNum(),
                            pagedPlan.query().pageSize(),
                            exception
                    );
                    continue;
                }
                JsonNode pois = response.path("pois");
                if (!pois.isArray() || pois.isEmpty()) {
                    continue;
                }
                for (JsonNode poiNode : pois) {
                    this.toPoiCandidate(poiNode, pagedPlan).ifPresent(candidate -> this.putCandidate(candidates, candidate));
                    if (candidates.size() >= MAX_CANDIDATE_COUNT) {
                        break;
                    }
                }
                if (pois.size() == PAGE_SIZE) {
                    nextActivePlans.add(plan);
                }
            }
            activePlans = nextActivePlans;
            if (activePlans.isEmpty()) {
                break;
            }
        }

        long nonMustVisitCount = candidates.values().stream().filter(candidate -> !candidate.mustVisit()).count();
        if (nonMustVisitCount == 0) {
            throw new IllegalStateException("高德未返回可用 POI，请调整区域或兴趣条件后重试");
        }
        if (candidates.size() < MIN_CANDIDATE_COUNT) {
            throw new IllegalStateException("高德返回的候选点不足，请扩大区域或减少限制后重试");
        }
        return this.limitCandidates(candidates.values().stream().toList());
    }

    private List<PoiSearchPlan> buildSearchPlans(RouteGenerationContext context) {
        List<PoiSearchPlan> plans = new ArrayList<>();
        List<InterestTagCatalogPO> tags = context.getInterestTags();
        if (tags.isEmpty()) {
            plans.addAll(this.configuredPlans(context, this.poiRecallPlanConfigManage.findEnabledPlansByPlanTypes(List.of(PLAN_TYPE_DEFAULT)), false));
            if (plans.isEmpty()) {
                plans.addAll(this.legacyPlansForTags(context, List.of(
                        this.defaultTag("SCENIC", "景点", "SCENIC",
                                List.of("110101", "110102", "110103", "110104", "110105", "110201", "110202", "110203", "110204", "110208", "110209", "110210"),
                                List.of("景点", "公园")),
                        this.defaultTag("MUSEUM", "展馆", "CULTURE",
                                List.of("140100", "140200", "140300", "140400", "140600", "140700", "110204"),
                                List.of("博物馆", "展览馆", "美术馆"))
                )));
            }
        } else {
            List<String> tagCodes = tags.stream().map(InterestTagCatalogPO::getTagCode).toList();
            plans.addAll(this.configuredPlans(context, this.poiRecallPlanConfigManage.findEnabledInterestPlansByTagCodes(tagCodes), true));
            if (plans.isEmpty()) {
                plans.addAll(this.legacyPlansForTags(context, tags));
            }
        }

        boolean hasFoodInterest = this.hasFoodInterest(context.getGenerateParam().getInterestTags());
        List<String> systemPlanTypes = new ArrayList<>();
        if (!hasFoodInterest && this.overlaps(context, LUNCH_START, LUNCH_END)) {
            systemPlanTypes.add(PLAN_TYPE_MEAL_LUNCH);
        }
        if (!hasFoodInterest && this.overlaps(context, DINNER_START, DINNER_END)) {
            systemPlanTypes.add(PLAN_TYPE_MEAL_DINNER);
        }
        if (this.resolveRestNeed(context) > 0) {
            systemPlanTypes.add(PLAN_TYPE_REST_NEED);
        }
        systemPlanTypes.add(PLAN_TYPE_BACKUP);
        List<PoiSearchPlan> systemPlans = this.configuredPlans(
                context,
                this.poiRecallPlanConfigManage.findEnabledPlansByPlanTypes(systemPlanTypes),
                false
        );
        if (systemPlans.isEmpty()) {
            systemPlans = this.defaultSystemPlans(context, hasFoodInterest);
        }
        plans.addAll(systemPlans);
        return plans;
    }

    private List<PoiSearchPlan> configuredPlans(
            RouteGenerationContext context,
            List<PoiRecallPlanConfigPO> configs,
            boolean explicitInterestPlan
    ) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        List<PoiSearchPlan> plans = new ArrayList<>();
        for (PoiRecallPlanConfigPO config : configs) {
            plans.addAll(this.configuredPlansForConfig(context, config, explicitInterestPlan));
        }
        return plans;
    }

    private List<PoiSearchPlan> configuredPlansForConfig(
            RouteGenerationContext context,
            PoiRecallPlanConfigPO config,
            boolean explicitInterestPlan
    ) {
        List<String> types = this.normalize(config.getAmapTypeCodes());
        if (types.isEmpty()) {
            return List.of();
        }
        List<String> keywords = this.allowedKeywordsForConfig(config);
        List<String> matchedInterestTags = explicitInterestPlan
                ? this.normalize(config.getIntentTags())
                : List.of();
        if (!keywords.isEmpty()) {
            return keywords.stream()
                    .map(keyword -> new PoiSearchPlan(
                            this.queryFor(context, types, List.of(keyword)),
                            this.resolveRoleHint(config.getRoleHint()),
                            StrUtil.blankToDefault(config.getCategoryGroupHint(), "UNKNOWN"),
                            matchedInterestTags,
                            StrUtil.blankToDefault(config.getReasonSeed(), "召回计划：" + config.getPlanCode())
                    ))
                    .toList();
        }
        return List.of(new PoiSearchPlan(
                this.queryFor(context, types, List.of()),
                this.resolveRoleHint(config.getRoleHint()),
                StrUtil.blankToDefault(config.getCategoryGroupHint(), "UNKNOWN"),
                matchedInterestTags,
                StrUtil.blankToDefault(config.getReasonSeed(), "召回计划：" + config.getPlanCode())
        ));
    }

    private List<PoiSearchPlan> legacyPlansForTags(RouteGenerationContext context, List<InterestTagCatalogPO> tags) {
        List<PoiSearchPlan> plans = new ArrayList<>();
        for (InterestTagCatalogPO tag : tags) {
            List<String> types = this.normalize(tag.getAmapTypeCodes());
            if (!types.isEmpty()) {
                plans.add(new PoiSearchPlan(
                        this.queryFor(context, types, List.of()),
                        this.resolveInterestRole(tag),
                        StrUtil.blankToDefault(tag.getCategoryGroup(), "UNKNOWN"),
                        List.of(tag.getTagCode()),
                        "匹配兴趣：" + tag.getDisplayName()
                ));
            }
            if ("EVENT".equals(tag.getTagCode())) {
                for (String keyword : EVENT_KEYWORDS) {
                    plans.add(new PoiSearchPlan(
                            this.queryFor(
                                    context,
                                    EVENT_KEYWORD_TYPES.getOrDefault(keyword, List.of("220104")),
                                    List.of(keyword)
                            ),
                            this.resolveInterestRole(tag),
                            StrUtil.blankToDefault(tag.getCategoryGroup(), "UNKNOWN"),
                            List.of(tag.getTagCode()),
                            "匹配兴趣：" + tag.getDisplayName()
                    ));
                }
            }
        }
        return plans;
    }

    private List<String> allowedKeywordsForConfig(PoiRecallPlanConfigPO config) {
        if (config == null || !EVENT_KEYWORD_PLAN_CODES.contains(config.getPlanCode())) {
            return List.of();
        }
        Set<String> allowedKeywords = new LinkedHashSet<>(EVENT_KEYWORDS);
        return this.normalizeKeywords(config.getAmapKeywords()).stream()
                .filter(allowedKeywords::contains)
                .toList();
    }

    private List<PoiSearchPlan> typeOnlySystemPlan(
            RouteGenerationContext context,
            List<String> types,
            PoiCandidateRole role,
            String category,
            String reasonSeed
    ) {
        List<String> normalizedTypes = this.normalize(types);
        if (normalizedTypes.isEmpty()) {
            return List.of();
        }
        return List.of(new PoiSearchPlan(
                this.queryFor(context, normalizedTypes, List.of()),
                role,
                category,
                List.of(),
                reasonSeed
        ));
    }

    private List<PoiSearchPlan> defaultSystemPlans(RouteGenerationContext context, boolean hasFoodInterest) {
        List<PoiSearchPlan> plans = new ArrayList<>();
        if (!hasFoodInterest && this.overlaps(context, LUNCH_START, LUNCH_END)) {
            plans.addAll(this.typeOnlySystemPlan(
                    context,
                    List.of("050100", "050200", "050300"),
                    PoiCandidateRole.MEAL,
                    "FOOD",
                    "路线覆盖午餐时间，适合作为用餐停留"
            ));
        }
        if (!hasFoodInterest && this.overlaps(context, DINNER_START, DINNER_END)) {
            plans.addAll(this.typeOnlySystemPlan(
                    context,
                    List.of("050100", "050200", "050300"),
                    PoiCandidateRole.MEAL,
                    "FOOD",
                    "路线覆盖晚餐时间，适合作为用餐停留"
            ));
        }
        if (this.resolveRestNeed(context) > 0) {
            plans.addAll(this.typeOnlySystemPlan(
                    context,
                    List.of("050500", "050600", "050700", "050800", "050900"),
                    PoiCandidateRole.REST,
                    "DRINK",
                    "适合控制路线节奏，中途休息补给"
            ));
        }
        plans.addAll(this.typeOnlySystemPlan(
                context,
                List.of("110101", "110102", "110103", "110104", "110105", "110201", "110202", "110203", "110204", "110208", "110209", "110210"),
                PoiCandidateRole.BACKUP,
                "SCENIC",
                "用于异常替换和路线兜底"
        ));
        return plans;
    }

    private AmapPoiSearchQueryDTO queryFor(
            RouteGenerationContext context,
            List<String> types,
            List<String> keywords
    ) {
        String searchType = AreaMode.AUTO_RADIUS == context.getArea().areaMode() ? SEARCH_TYPE_AROUND : SEARCH_TYPE_POLYGON;
        return new AmapPoiSearchQueryDTO(
                searchType,
                context.getArea().center(),
                context.getArea().radiusMeters(),
                context.getArea().polygonGcj02(),
                this.normalize(types),
                this.normalize(keywords),
                FIRST_PAGE_NUM,
                PAGE_SIZE
        );
    }

    private AmapPoiSearchQueryDTO withPageNum(AmapPoiSearchQueryDTO query, int pageNum) {
        return new AmapPoiSearchQueryDTO(
                query.searchType(),
                query.center(),
                query.radiusMeters(),
                query.polygon(),
                query.types(),
                query.keywords(),
                pageNum,
                query.pageSize()
        );
    }

    private JsonNode searchWithCache(AmapPoiSearchQueryDTO query) {
        String areaHash = this.areaHash(query);
        String typesHash = this.hash(String.join("|", query.types()));
        String keywordsHash = this.hash(String.join("|", query.keywords()));
        return this.amapPoiSearchCacheManage.findValidResponseJson(
                        query.searchType(),
                        areaHash,
                        typesHash,
                        keywordsHash,
                        query.pageNum(),
                        query.pageSize()
                )
                .map(this::readTree)
                .orElseGet(() -> this.fetchAndCache(query, areaHash, typesHash, keywordsHash));
    }

    private JsonNode fetchAndCache(
            AmapPoiSearchQueryDTO query,
            String areaHash,
            String typesHash,
            String keywordsHash
    ) {
        JsonNode response = this.amapApi.searchPoi(query);
        if (!this.isSuccess(response)) {
            ObjectNode emptyResponse = this.objectMapper.createObjectNode();
            emptyResponse.putArray("pois");
            return emptyResponse;
        }
        String requestParamsJson = this.writeJson(query.toCacheParams());
        String responseJson = response.toString();
        this.amapPoiSearchCacheManage.upsertResponseJson(
                query.searchType(),
                areaHash,
                typesHash,
                keywordsHash,
                query.pageNum(),
                query.pageSize(),
                requestParamsJson,
                responseJson,
                response.path("pois").size(),
                Instant.now().plus(POI_CACHE_TTL_HOURS, ChronoUnit.HOURS)
        );
        return response;
    }

    private boolean isSuccess(JsonNode response) {
        return response != null && "1".equals(response.path("status").asText("1"));
    }

    private java.util.Optional<PoiCandidateDTO> toPoiCandidate(JsonNode poiNode, PoiSearchPlan plan) {
        String amapPoiId = poiNode.path("id").asText();
        String name = poiNode.path("name").asText();
        String locationText = poiNode.path("location").asText();
        if (StrUtil.isBlank(name) || StrUtil.isBlank(locationText)) {
            return java.util.Optional.empty();
        }
        GeoPointDTO location = this.parseLocation(locationText);
        String poiId = StrUtil.isBlank(amapPoiId) ? "amap-" + name + "-" + locationText : amapPoiId;
        String address = this.firstText(poiNode, "address", "business.business_area", "adname");
        BigDecimal rating = this.parseRating(poiNode);
        Integer avgPriceCent = this.parseAvgPriceCent(poiNode);
        String rawType = this.firstText(poiNode, "type");
        String typecode = this.firstText(poiNode, "typecode");
        String opentimeToday = this.firstText(poiNode, "business.opentime_today", "biz_ext.opentime_today", "opentime_today");
        String opentimeWeek = this.firstText(poiNode, "business.opentime_week", "biz_ext.opentime_week", "opentime_week");
        String keytag = this.firstText(poiNode, "business.keytag", "biz_ext.keytag", "keytag");
        String rectag = this.firstText(poiNode, "business.rectag", "biz_ext.rectag", "rectag");
        return java.util.Optional.of(new PoiCandidateDTO(
                poiId,
                amapPoiId,
                name,
                StrUtil.blankToDefault(plan.category(), plan.role().name()),
                plan.role(),
                location,
                address,
                this.buildPoiDescription(poiNode, plan, address, rating, avgPriceCent),
                rating,
                avgPriceCent,
                plan.matchedInterestTags(),
                this.parsePhotoUrls(poiNode),
                null,
                rawType,
                typecode,
                opentimeToday,
                opentimeWeek,
                keytag,
                rectag,
                this.parseInteger(this.firstText(poiNode, "distance")),
                List.of(),
                "UNKNOWN",
                null,
                false,
                plan.reasonSeed()
        ));
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
                List.of(),
                "UNKNOWN",
                true,
                "用户标记为" + switch (mustVisitPoint.getPriority()) {
                    case MUST -> "必须到达";
                    case PREFER -> "优先安排";
                }
        );
    }

    private void putCandidate(Map<String, PoiCandidateDTO> candidates, PoiCandidateDTO candidate) {
        String key = StrUtil.blankToDefault(candidate.amapPoiId(), candidate.poiId());
        PoiCandidateDTO existing = candidates.get(key);
        if (existing == null) {
            candidates.put(key, candidate);
            return;
        }
        candidates.put(key, this.mergeCandidate(existing, candidate));
    }

    private PoiCandidateDTO mergeCandidate(PoiCandidateDTO existing, PoiCandidateDTO candidate) {
        Set<String> matchedInterestTags = new LinkedHashSet<>(existing.matchedInterestTags());
        matchedInterestTags.addAll(candidate.matchedInterestTags());
        PoiCandidateRole role = this.roleWeight(candidate.role()) < this.roleWeight(existing.role())
                ? candidate.role()
                : existing.role();
        String category = "UNKNOWN".equals(existing.category()) ? candidate.category() : existing.category();
        String reasonSeed = existing.reasonSeed();
        if (StrUtil.isNotBlank(candidate.reasonSeed()) && !candidate.reasonSeed().equals(existing.reasonSeed())) {
            reasonSeed = reasonSeed + "；" + candidate.reasonSeed();
        }
        return new PoiCandidateDTO(
                existing.poiId(),
                existing.amapPoiId(),
                existing.name(),
                category,
                role,
                existing.location(),
                existing.address(),
                existing.description(),
                existing.amapRating(),
                existing.avgPriceCent(),
                List.copyOf(matchedInterestTags),
                existing.imageUrls(),
                existing.imageCount(),
                existing.rawType(),
                existing.typecode(),
                existing.opentimeToday(),
                existing.opentimeWeek(),
                existing.keytag(),
                existing.rectag(),
                existing.amapDistanceMeters(),
                existing.nearestTransit(),
                existing.transitAccessibility(),
                existing.transitLookupStatus(),
                existing.mustVisit(),
                reasonSeed
        );
    }

    private List<PoiCandidateDTO> limitCandidates(List<PoiCandidateDTO> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(PoiCandidateDTO::mustVisit).reversed()
                        .thenComparing(candidate -> this.roleWeight(candidate.role()))
                        .thenComparing(candidate -> candidate.amapRating() == null ? BigDecimal.ZERO : candidate.amapRating(), Comparator.reverseOrder()))
                .limit(MAX_CANDIDATE_COUNT)
                .toList();
    }

    private int roleWeight(PoiCandidateRole role) {
        return switch (role) {
            case MUST_VISIT -> 0;
            case ANCHOR -> 1;
            case LOCAL -> 2;
            case MEAL -> 3;
            case REST -> 4;
            case BACKUP -> 5;
        };
    }

    private PoiCandidateRole resolveInterestRole(InterestTagCatalogPO tag) {
        if ("COFFEE".equals(tag.getTagCode())) {
            return PoiCandidateRole.REST;
        }
        if ("FOOD".equals(tag.getCategoryGroup())) {
            return PoiCandidateRole.MEAL;
        }
        if ("LOCAL".equals(tag.getCategoryGroup())) {
            return PoiCandidateRole.LOCAL;
        }
        return PoiCandidateRole.ANCHOR;
    }

    private PoiCandidateRole resolveRoleHint(String roleHint) {
        if (StrUtil.isBlank(roleHint)) {
            return PoiCandidateRole.ANCHOR;
        }
        try {
            return PoiCandidateRole.valueOf(roleHint);
        } catch (IllegalArgumentException exception) {
            return PoiCandidateRole.ANCHOR;
        }
    }

    private boolean hasFoodInterest(List<String> interestTags) {
        if (interestTags == null || interestTags.isEmpty()) {
            return false;
        }
        return interestTags.stream().anyMatch(tag -> "FOOD".equals(tag) || tag.startsWith("FOOD_"));
    }

    private int resolveRestNeed(RouteGenerationContext context) {
        int durationMinutes = context.getGenerateParam().getDurationMinutes();
        if (durationMinutes <= SHORT_ROUTE_MINUTES) {
            return 0;
        }
        if (durationMinutes <= HALF_DAY_ROUTE_MINUTES) {
            return 1;
        }
        return 2;
    }

    private boolean overlaps(RouteGenerationContext context, LocalTime windowStart, LocalTime windowEnd) {
        LocalDateTime routeStart = context.getGenerateParam().getDepartureTime();
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

    private GeoPointDTO parseLocation(String locationText) {
        String[] parts = locationText.split(",");
        return new GeoPointDTO(new BigDecimal(parts[0]), new BigDecimal(parts[1]));
    }

    private BigDecimal parseRating(JsonNode poiNode) {
        String rating = this.firstText(poiNode, "business.rating", "biz_ext.rating", "rating");
        if (StrUtil.isBlank(rating) || "[]".equals(rating)) {
            return null;
        }
        return new BigDecimal(rating);
    }

    private Integer parseAvgPriceCent(JsonNode poiNode) {
        String cost = this.firstText(poiNode, "business.cost", "biz_ext.cost", "cost");
        if (StrUtil.isBlank(cost) || "[]".equals(cost)) {
            return null;
        }
        try {
            BigDecimal yuan = new BigDecimal(cost);
            if (yuan.signum() < 0) {
                return null;
            }
            // 高德 cost 口径为元；内部 avgPriceCent 统一保存为分。缺失或非法值保持 null，不等同于免费。
            return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String buildPoiDescription(
            JsonNode poiNode,
            PoiSearchPlan plan,
            String address,
            BigDecimal rating,
            Integer avgPriceCent
    ) {
        List<String> parts = new ArrayList<>();
        String type = this.firstText(poiNode, "type");
        if (StrUtil.isNotBlank(address)) {
            parts.add("位于" + address);
        }
        if (StrUtil.isNotBlank(type)) {
            parts.add("类型为" + type);
        }
        if (rating != null) {
            parts.add("高德评分 " + rating.stripTrailingZeros().toPlainString());
        }
        if (avgPriceCent != null) {
            parts.add("人均约 " + avgPriceCent / 100 + " 元");
        }
        parts.add(plan.reasonSeed());
        return String.join("，", parts) + "。";
    }

    private List<String> parsePhotoUrls(JsonNode poiNode) {
        List<String> photoUrls = new ArrayList<>();
        JsonNode photos = poiNode.path("photos");
        if (photos.isArray()) {
            for (JsonNode photo : photos) {
                String url = photo.path("url").asText();
                if (StrUtil.isNotBlank(url)) {
                    photoUrls.add(url);
                }
            }
        }
        return photoUrls.stream().distinct().limit(6).toList();
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                current = current.path(segment);
            }
            if (!current.isMissingNode() && StrUtil.isNotBlank(current.asText())) {
                return current.asText();
            }
        }
        return null;
    }

    private List<String> normalize(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> normalizeKeywords(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(StrUtil::isNotBlank)
                .flatMap(value -> java.util.Arrays.stream(value.split("[,，|]")))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    private String areaHash(AmapPoiSearchQueryDTO query) {
        if (SEARCH_TYPE_AROUND.equals(query.searchType())) {
            return this.hash(query.center() + ":" + query.radiusMeters());
        }
        return this.hash(query.polygon().toString());
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算高德缓存 hash", exception);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return this.objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析高德 POI 缓存", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化高德 POI 请求参数", exception);
        }
    }

    private InterestTagCatalogPO defaultTag(
            String tagCode,
            String displayName,
            String categoryGroup,
            List<String> amapTypeCodes,
            List<String> amapKeywords
    ) {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode(tagCode);
        tag.setDisplayName(displayName);
        tag.setCategoryGroup(categoryGroup);
        tag.setAmapTypeCodes(amapTypeCodes);
        tag.setAmapKeywords(amapKeywords);
        return tag;
    }

    private record PoiSearchPlan(
            AmapPoiSearchQueryDTO query,
            PoiCandidateRole role,
            String category,
            List<String> matchedInterestTags,
            String reasonSeed
    ) {

        private PoiSearchPlan withQuery(AmapPoiSearchQueryDTO query) {
            return new PoiSearchPlan(
                    query,
                    this.role,
                    this.category,
                    this.matchedInterestTags,
                    this.reasonSeed
            );
        }
    }
}
