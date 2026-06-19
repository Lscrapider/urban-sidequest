package com.urbansidequest.backend.provider.route;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.api.baidu.BaiduPlaceSearchApi;
import com.urbansidequest.backend.domain.dto.BaiduPlaceSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.param.MustVisitPointParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class BaiduPoiCandidateProvider implements PoiCandidateProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduPoiCandidateProvider.class);

    private static final String SEARCH_TYPE_AROUND = "AROUND";

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final int FIRST_PAGE_NUM = 0;

    private static final int PAGE_SIZE = 20;

    private static final int MAX_PAGE_NUM = 3;

    private static final int MAX_CANDIDATE_COUNT = 100;

    private static final int MIN_CANDIDATE_COUNT = 3;

    private static final ZoneId ROUTE_ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);

    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);

    private static final LocalTime DINNER_START = LocalTime.of(17, 30);

    private static final LocalTime DINNER_END = LocalTime.of(20, 0);

    private static final int SHORT_ROUTE_MINUTES = 180;

    private static final int HALF_DAY_ROUTE_MINUTES = 360;

    private final BaiduPlaceSearchApi baiduPlaceSearchApi;

    private final BaiduPoiCandidateMapper baiduPoiCandidateMapper;

    public BaiduPoiCandidateProvider(
            BaiduPlaceSearchApi baiduPlaceSearchApi,
            BaiduPoiCandidateMapper baiduPoiCandidateMapper
    ) {
        this.baiduPlaceSearchApi = baiduPlaceSearchApi;
        this.baiduPoiCandidateMapper = baiduPoiCandidateMapper;
    }

    @Override
    public List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context) {
        if (!this.baiduPlaceSearchApi.isAvailable()) {
            throw new IllegalStateException("未配置百度地图 AK，无法使用百度 POI 生成训练候选池");
        }
        try {
            return this.loadBaiduCandidates(context);
        } catch (RestClientException exception) {
            throw new IllegalStateException("百度 POI 查询失败，请稍后重试", exception);
        }
    }

    private List<PoiCandidateDTO> loadBaiduCandidates(RouteGenerationContext context) {
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
                    response = this.baiduPlaceSearchApi.search(pagedPlan.query());
                } catch (RestClientException exception) {
                    context.addWarning("部分百度 POI 查询失败，已跳过搜索计划：" + pagedPlan.reasonSeed());
                    LOGGER.warn(
                            "百度 POI 查询计划失败，searchType={}，query={}，tag={}，pageNum={}，pageSize={}",
                            pagedPlan.query().searchType(),
                            pagedPlan.query().query(),
                            pagedPlan.query().tag(),
                            pagedPlan.query().pageNum(),
                            pagedPlan.query().pageSize(),
                            exception
                    );
                    continue;
                }
                if (!this.isSuccess(response)) {
                    context.addWarning("百度 POI 查询返回失败，已跳过搜索计划：" + pagedPlan.reasonSeed());
                    LOGGER.warn(
                            "百度 POI 查询返回失败，status={}，message={}，query={}，tag={}",
                            response == null ? null : response.path("status").asText(),
                            response == null ? null : response.path("message").asText(),
                            pagedPlan.query().query(),
                            pagedPlan.query().tag()
                    );
                    continue;
                }
                JsonNode results = response.path("results");
                if (!results.isArray() || results.isEmpty()) {
                    continue;
                }
                for (JsonNode poiNode : results) {
                    this.baiduPoiCandidateMapper.toPoiCandidate(
                            poiNode,
                            pagedPlan.role(),
                            pagedPlan.category(),
                            pagedPlan.matchedInterestTags(),
                            pagedPlan.reasonSeed()
                    ).ifPresent(candidate -> this.putCandidate(candidates, candidate));
                    if (candidates.size() >= MAX_CANDIDATE_COUNT) {
                        break;
                    }
                }
                if (results.size() == PAGE_SIZE) {
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
            throw new IllegalStateException("百度未返回可用 POI，请调整区域或兴趣条件后重试");
        }
        if (candidates.size() < MIN_CANDIDATE_COUNT) {
            throw new IllegalStateException("百度返回的候选点不足，请扩大区域或减少限制后重试");
        }
        return this.limitCandidates(candidates.values().stream().toList());
    }

    private List<PoiSearchPlan> buildSearchPlans(RouteGenerationContext context) {
        List<PoiSearchPlan> plans = new ArrayList<>();
        List<InterestTagCatalogPO> tags = context.getInterestTags();
        if (tags.isEmpty()) {
            tags = List.of(
                    this.defaultTag("SCENIC", "景点", "SCENIC", List.of("景点", "公园")),
                    this.defaultTag("MUSEUM", "展馆", "CULTURE", List.of("博物馆", "展览馆", "美术馆"))
            );
        }

        for (InterestTagCatalogPO tag : tags) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, tag.getCategoryGroup(), this.keywordsFor(tag)),
                    this.resolveInterestRole(tag),
                    tag.getCategoryGroup(),
                    List.of(tag.getTagCode()),
                    "匹配兴趣：" + tag.getDisplayName()
            ));
        }

        if (this.overlaps(context, LUNCH_START, LUNCH_END)) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, "FOOD", List.of("午餐", "本地菜", "小吃")),
                    PoiCandidateRole.MEAL,
                    "FOOD",
                    List.of("FOOD"),
                    "路线覆盖午餐时间，适合作为用餐停留"
            ));
        }
        if (this.overlaps(context, DINNER_START, DINNER_END)) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, "FOOD", List.of("晚餐", "本地菜", "小吃")),
                    PoiCandidateRole.MEAL,
                    "FOOD",
                    List.of("FOOD"),
                    "路线覆盖晚餐时间，适合作为用餐停留"
            ));
        }
        if (this.resolveRestNeed(context) > 0) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, "REST", List.of("咖啡", "甜品")),
                    PoiCandidateRole.REST,
                    "REST",
                    List.of("COFFEE"),
                    "适合控制路线节奏，中途休息补给"
            ));
        }
        if (RouteGoal.LOCAL == context.getGenerateParam().getRouteGoal()) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, "LOCAL", List.of("老街", "夜市", "小吃街")),
                    PoiCandidateRole.LOCAL,
                    "LOCAL",
                    List.of("LOCAL"),
                    "匹配路线目标：地道烟火"
            ));
        }
        if (RouteGoal.NIGHT == context.getGenerateParam().getRouteGoal()) {
            plans.add(new PoiSearchPlan(
                    this.queryFor(context, "NIGHT", List.of("夜景", "夜市")),
                    PoiCandidateRole.ANCHOR,
                    "NIGHT",
                    List.of("NIGHT"),
                    "匹配路线目标：夜游"
            ));
        }
        plans.add(new PoiSearchPlan(
                this.queryFor(context, "SCENIC", List.of("公园", "景点")),
                PoiCandidateRole.BACKUP,
                "SCENIC",
                List.of("SCENIC"),
                "用于异常替换和路线兜底"
        ));
        return plans;
    }

    private BaiduPlaceSearchQueryDTO queryFor(
            RouteGenerationContext context,
            String categoryGroup,
            List<String> keywords
    ) {
        String searchType = AreaMode.AUTO_RADIUS == context.getArea().areaMode() ? SEARCH_TYPE_AROUND : SEARCH_TYPE_POLYGON;
        List<String> normalizedKeywords = this.normalize(keywords);
        String tag = this.baiduTagFor(categoryGroup);
        String query = String.join("$", normalizedKeywords);
        if (StrUtil.isBlank(query)) {
            query = StrUtil.blankToDefault(tag, "景点");
        }
        return new BaiduPlaceSearchQueryDTO(
                searchType,
                context.getArea().center(),
                context.getArea().radiusMeters(),
                context.getArea().polygonGcj02(),
                query,
                tag,
                FIRST_PAGE_NUM,
                PAGE_SIZE
        );
    }

    private BaiduPlaceSearchQueryDTO withPageNum(BaiduPlaceSearchQueryDTO query, int pageNum) {
        return new BaiduPlaceSearchQueryDTO(
                query.searchType(),
                query.center(),
                query.radiusMeters(),
                query.polygon(),
                query.query(),
                query.tag(),
                pageNum,
                query.pageSize()
        );
    }

    private boolean isSuccess(JsonNode response) {
        return response != null && response.path("status").asInt(-1) == 0;
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
        candidates.putIfAbsent(key, candidate);
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

    private List<String> keywordsFor(InterestTagCatalogPO tag) {
        List<String> keywords = new ArrayList<>();
        if (CollUtil.isNotEmpty(tag.getAmapKeywords())) {
            keywords.addAll(tag.getAmapKeywords());
        }
        if (StrUtil.isNotBlank(tag.getDisplayName())) {
            keywords.add(tag.getDisplayName());
        }
        return keywords;
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

    private String baiduTagFor(String categoryGroup) {
        if ("FOOD".equals(categoryGroup) || "REST".equals(categoryGroup)) {
            return "美食";
        }
        if ("CULTURE".equals(categoryGroup) || "SCENIC".equals(categoryGroup) || "PHOTO".equals(categoryGroup)) {
            return "旅游景点";
        }
        if ("SHOPPING".equals(categoryGroup)) {
            return "购物";
        }
        return "";
    }

    private InterestTagCatalogPO defaultTag(
            String tagCode,
            String displayName,
            String categoryGroup,
            List<String> keywords
    ) {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode(tagCode);
        tag.setDisplayName(displayName);
        tag.setCategoryGroup(categoryGroup);
        tag.setAmapKeywords(keywords);
        return tag;
    }

    private record PoiSearchPlan(
            BaiduPlaceSearchQueryDTO query,
            PoiCandidateRole role,
            String category,
            List<String> matchedInterestTags,
            String reasonSeed
    ) {

        private PoiSearchPlan withQuery(BaiduPlaceSearchQueryDTO query) {
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
