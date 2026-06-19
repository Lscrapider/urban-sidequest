package com.urbansidequest.backend.handler.route.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmBackendReviewHintDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmComposedRouteDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmComposedStopDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmRouteComposeResponseDTO;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

@Component
public class LlmRouteCandidateComposer implements RouteCandidateComposer {

    private static final String TRANSIT_TYPE_BUS = "BUS";

    private static final String TRANSIT_TYPE_SUBWAY = "SUBWAY";

    private static final int DEFAULT_STAY_MINUTES = 45;

    private static final int MIN_STOP_COUNT = 2;

    private static final int MAX_ROUTE_COUNT = 5;

    private static final Set<String> ALLOWED_ROUTE_ROLES = Set.of(
            "MUST_VISIT",
            "ANCHOR",
            "MEAL",
            "REST",
            "LOCAL",
            "PHOTO",
            "BACKUP"
    );

    private static final String SYSTEM_PROMPT = """
            你是 Urban Sidequest 的路线编排助手。你只根据输入 JSON 工作，必须返回合法 JSON，不要输出 Markdown、解释文字或代码块。
            你的任务是从后端筛选过的真实 poiPool 中生成 5 条路线草案。
            你不能编造 POI、坐标、距离、交通耗时、评分、营业信息或图片。
            所有路线 stop 必须引用 poiPool 中存在的 poiId。你不能删除 request.mustVisitPoiIds 中的必去点，不能改变用户选择的城市、出发时间、路线时长、交通方式和路线目标。
            距离、交通耗时和真实路径由后端/地图服务计算，你只能决定选点、排序、停留时间、路线主题、路线说明、节点说明和 warning。
            每条路线的停留时间总和不得超过 request.durationMinutes 的 85%，因为后端还需要预留交通时间；不要为了贴近请求时长而吃满上限，跨区域路线应预留更充足交通余量。
            每个 stop 的 routeRole 只能取 MUST_VISIT、ANCHOR、MEAL、REST、LOCAL、PHOTO、BACKUP 之一，不能输出其他枚举值。
            每个 stop 的 stayMinutes 必须符合用户 prompt 中的停留时间参考；必去点也要按其 POI category 选择合理停留时间，除非 route.warnings 明确说明原因。
            午餐或晚餐 stop 的 routeRole 必须是 MEAL，并且必须优先选择 category=FOOD 或 role=MEAL 的 POI；category=FOOD 或 role=MEAL 就视为可用于午餐/晚餐，不要求 tags 中额外包含午餐或晚餐标签，也不要因为缺少这类标签产生 warning。
            只有候选池没有合适 FOOD/MEAL 时才允许使用其他 POI，并必须在 route.warnings 中说明。每个 MEAL stop 必须填写 intendedMealWindow：LUNCH、DINNER 或 OTHER。
            WALK_TAXI 可以跨区域，但应按空间相近性组织 stop 顺序，避免远距离片区之间来回跳转；如果路线存在明显折返风险，必须在 backendReviewHints 中说明。
            若无法满足某个需求，返回 warnings 说明原因，不要编造地点。
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            请基于下面的真实 POI 候选池生成路线草案。

            目标：
            1. 从 poiPool 中生成 5 条高质量路线，数量固定为 5 条，不能多也不能少。
            2. 每条路线都必须包含 request.mustVisitPoiIds 中的所有必去点。
            3. 每条路线只能引用 poiPool 中存在的 poiId。
            4. 根据 request.durationMinutes、departureTime、mealWindows 安排午饭、晚饭和咖啡/休息点。
            5. 8 小时路线应有跨区域感，避免所有 stop 过度聚集在同一小片区。
            6. 根据 category、role、tags、features、rating、avgPriceCent、nearestTransit 和 transitAccessibility 选择 POI。
            7. 每条路线需要有明确主题，A/B/C/D/E 路线应有差异，不要只是换顺序。
            8. 输出路线草案即可，距离、交通耗时和真实路径由后端之后调用高德路线 API 计算。

            硬约束：
            - 只能引用 poiPool 中存在的 poiId。
            - 不能新增虚构地点。
            - 每条路线都必须包含全部 request.mustVisitPoiIds。
            - 不能返回自然语言说明，只能返回 JSON。
            - 每条路线 stop 只能为 6 个。
            - 每条路线停留时间总和不得超过 request.durationMinutes 的 85%%。
            - estimatedStayMinutes 必须等于该路线所有 stops.stayMinutes 的总和。
            - routeRole 只能取 MUST_VISIT、ANCHOR、MEAL、REST、LOCAL、PHOTO、BACKUP，不能输出 SCENIC、CULTURE、FOOD、COFFEE 等 schema 外枚举。
            - 每个 stop 的 stayMinutes 必须符合下面“停留时间参考”；如果确实需要超出参考范围，必须在 route.warnings 中说明原因。
            - 如果覆盖午饭窗口，优先安排 FOOD/MEAL stop；如果覆盖晚饭窗口，也优先安排 FOOD/MEAL stop。
            - 如果 stop 用作午餐或晚餐，routeRole 必须是 MEAL，且应优先选择 category=FOOD 或 role=MEAL 的 POI。
            - category=FOOD 或 role=MEAL 的 POI 可以直接作为午餐/晚餐候选，不要求 tags 额外包含 LUNCH 或 DINNER，不要因此产生 warning。
            - 每个 routeRole=MEAL 的 stop 必须填写 intendedMealWindow，取值为 LUNCH、DINNER 或 OTHER。
            - 如果没有安排某个饭点，必须在 route.warnings 中说明原因。
            - WALK_TAXI 模式下可以跨区域，但路线顺序应符合城市移动常识。
            - 避免远距离片区之间来回折返。路线可以跨多个片区，但同一片区内的 stop 应尽量连续安排；如果必须折返，backendReviewHints 必须说明原因。
            - nearestTransit 只能作为可达性参考，不要把它当作已经计算好的路线耗时。

            饭点判断：
            - 午饭窗口：11:30-13:30。
            - 晚饭窗口：17:30-20:00。
            - 路线时间段与饭点窗口有交集，则认为覆盖饭点。

            停留时间参考：
            - 文化展馆/博物馆：60-90 分钟。
            - 公园/景点：45-75 分钟。
            - 正餐餐饮：45-75 分钟。
            - 咖啡/休息：20-40 分钟。
            - 拍照点/轻量打卡：15-30 分钟。
            - 普通街区体验：30-60 分钟。

            请按以下 JSON Schema 返回：
            {
              "overallVerdict": "COMPOSED | PARTIAL | FAILED",
              "globalWarnings": ["string"],
              "routes": [
                {
                  "routeCode": "A",
                  "title": "string",
                  "theme": "string",
                  "summary": "string",
                  "explanation": "string",
                  "estimatedStayMinutes": 0,
                  "qualityScore": 0,
                  "routeTags": ["string"],
                  "stops": [
                    {
                      "order": 1,
                      "poiId": "string",
                      "routeRole": "MUST_VISIT | ANCHOR | MEAL | REST | LOCAL | PHOTO | BACKUP",
                      "intendedMealWindow": "LUNCH | DINNER | OTHER | null",
                      "stayMinutes": 0,
                      "description": "string",
                      "reason": "string"
                    }
                  ],
                  "warnings": ["string"],
                  "backendReviewHints": [
                    {
                      "type": "TIME_WINDOW | ROUTE_DISTANCE | TRANSIT | BUDGET | OTHER",
                      "message": "string"
                    }
                  ],
                  "needsBackendReview": true
                }
              ]
            }

            输入数据：
            %s
            """;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    public LlmRouteCandidateComposer(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder
                .defaultTemplateRenderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CandidateRouteDTO> composeRoutes(RouteGenerationContext context) {
        String responseContent;
        try {
            responseContent = this.chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(this.buildUserPrompt(context))
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            context.addWarning("大模型路线编排调用失败：" + exception.getMessage());
            return List.of();
        }
        return this.toCandidateRoutes(context, responseContent);
    }

    private String buildUserPrompt(RouteGenerationContext context) {
        try {
            return USER_PROMPT_TEMPLATE.formatted(this.objectMapper.writeValueAsString(this.toPromptPayload(context)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化路线编排输入", exception);
        }
    }

    private Map<String, Object> toPromptPayload(RouteGenerationContext context) {
        return Map.of(
                "request", this.requestPayload(context),
                "mealWindows", List.of(
                        Map.of("type", "LUNCH", "start", "11:30", "end", "13:30"),
                        Map.of("type", "DINNER", "start", "17:30", "end", "20:00")
                ),
                "transportPolicy", Map.of(
                        "profile", context.getGenerateParam().getTransportProfile(),
                        "notes", "交通设施和空间距离只作为可达性参考，真实路线由后端调用高德路线 API 计算。WALK_BUS 只参考 BUS 设施，WALK_SUBWAY/BIKE_SUBWAY 只参考 SUBWAY 设施，WALK_TRANSIT 可混合参考 BUS/SUBWAY。"
                ),
                "poiPool", context.getPoiCandidates().stream()
                        .map(candidate -> this.poiPayload(candidate, context.getGenerateParam().getTransportProfile()))
                        .toList()
        );
    }

    private Map<String, Object> requestPayload(RouteGenerationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("areaMode", context.getGenerateParam().getAreaMode());
        payload.put("areaLabel", context.getArea().areaLabel());
        payload.put("routeCityName", context.getGenerateParam().getRouteCityName());
        payload.put("routeCityAdcode", context.getGenerateParam().getRouteCityAdcode());
        payload.put("departureTime", context.getGenerateParam().getDepartureTime());
        payload.put("durationMinutes", context.getGenerateParam().getDurationMinutes());
        payload.put("transportProfile", context.getGenerateParam().getTransportProfile());
        payload.put("routeGoal", context.getGenerateParam().getRouteGoal());
        payload.put("interestTags", context.getGenerateParam().getInterestTags());
        payload.put("mustVisitPoiIds", context.getPoiCandidates().stream()
                .filter(PoiCandidateDTO::mustVisit)
                .map(PoiCandidateDTO::poiId)
                .toList());
        payload.put("routeCountRange", Map.of("min", MAX_ROUTE_COUNT, "max", MAX_ROUTE_COUNT));
        return payload;
    }

    private Map<String, Object> poiPayload(PoiCandidateDTO candidate, TransportProfile transportProfile) {
        List<TransitFacilityDTO> nearestTransit = this.relevantTransit(candidate, transportProfile);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiId", candidate.poiId());
        payload.put("amapPoiId", candidate.amapPoiId());
        payload.put("name", candidate.name());
        payload.put("category", candidate.category());
        payload.put("role", candidate.role());
        payload.put("location", this.locationPayload(candidate.location()));
        payload.put("address", candidate.address());
        payload.put("description", candidate.description());
        payload.put("rating", candidate.amapRating());
        payload.put("avgPriceCent", candidate.avgPriceCent());
        payload.put("tags", candidate.matchedInterestTags());
        payload.put("features", this.featuresPayload(candidate));
        payload.put("nearestTransit", nearestTransit);
        payload.put("transitAccessibility", this.transitAccessibility(candidate, nearestTransit));
        payload.put("mustVisit", candidate.mustVisit());
        payload.put("reasonSeed", candidate.reasonSeed());
        return payload;
    }

    private List<String> featuresPayload(PoiCandidateDTO candidate) {
        Set<String> features = new LinkedHashSet<>(candidate.matchedInterestTags());
        if (candidate.keytag() != null && !candidate.keytag().isBlank()) {
            features.add(candidate.keytag());
        }
        if (candidate.rectag() != null && !candidate.rectag().isBlank()) {
            features.add(candidate.rectag());
        }
        if (candidate.rawType() != null && !candidate.rawType().isBlank()) {
            features.add(candidate.rawType());
        }
        if (candidate.mustVisit()) {
            features.add("用户指定必去点");
        }
        return List.copyOf(features);
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

    private List<CandidateRouteDTO> toCandidateRoutes(RouteGenerationContext context, String responseContent) {
        LlmRouteComposeResponseDTO response;
        try {
            response = this.objectMapper.readValue(this.cleanJsonContent(responseContent), LlmRouteComposeResponseDTO.class);
        } catch (JsonProcessingException exception) {
            context.addWarning("大模型路线编排返回 JSON 解析失败");
            return List.of();
        }
        this.appendWarnings(context, "大模型全局提示", response.globalWarnings());
        if (response.routes() == null || response.routes().isEmpty()) {
            context.addWarning("大模型没有返回可用路线");
            return List.of();
        }

        Map<String, PoiCandidateDTO> candidatesByPoiId = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesByPoiId.put(candidate.poiId(), candidate);
        }

        List<CandidateRouteDTO> routes = new ArrayList<>();
        int index = 0;
        for (LlmComposedRouteDTO route : response.routes()) {
            CandidateRouteDTO candidateRoute = this.toCandidateRoute(context, route, candidatesByPoiId, index);
            if (candidateRoute != null) {
                routes.add(candidateRoute);
                index++;
            }
            if (routes.size() >= MAX_ROUTE_COUNT) {
                break;
            }
        }
        if (routes.isEmpty()) {
            context.addWarning("大模型返回的路线都无法映射到当前 POI 池");
        }
        return routes;
    }

    private CandidateRouteDTO toCandidateRoute(
            RouteGenerationContext context,
            LlmComposedRouteDTO route,
            Map<String, PoiCandidateDTO> candidatesByPoiId,
            int routeIndex
    ) {
        String routeCode = this.resolveRouteCode(route.routeCode(), routeIndex);
        List<RouteStopDTO> stops = new ArrayList<>();
        Set<String> usedPoiIds = new LinkedHashSet<>();
        if (route.stops() == null) {
            context.addWarning(routeCode + " 线没有返回 stop，已跳过");
            return null;
        }
        for (LlmComposedStopDTO stop : route.stops()) {
            PoiCandidateDTO candidate = candidatesByPoiId.get(stop.poiId());
            if (candidate == null) {
                context.addWarning(routeCode + " 线引用了不存在的 POI：" + stop.poiId());
                continue;
            }
            if (!usedPoiIds.add(candidate.poiId())) {
                context.addWarning(routeCode + " 线重复引用 POI：" + candidate.name());
                continue;
            }
            if (!this.validRouteRole(stop.routeRole())) {
                context.addWarning(routeCode + " 线 POI " + candidate.name() + " 的 routeRole 不合法：" + stop.routeRole());
                continue;
            }
            stops.add(this.toRouteStop(routeCode, stops.size() + 1, candidate, stop));
        }
        if (stops.size() < MIN_STOP_COUNT) {
            context.addWarning(routeCode + " 线有效 stop 数量不足，已跳过");
            return null;
        }
        this.appendWarnings(context, routeCode + " 线模型提示", route.warnings());
        this.appendBackendReviewHints(context, routeCode, route.backendReviewHints());

        int stayDurationMinutes = stops.stream().mapToInt(RouteStopDTO::stayMinutes).sum();
        int budgetCent = stops.stream()
                .map(stop -> candidatesByPoiId.get(this.poiIdFromStopId(stop.stopId(), routeCode)))
                .filter(candidate -> candidate != null && candidate.avgPriceCent() != null)
                .mapToInt(PoiCandidateDTO::avgPriceCent)
                .sum();
        return new CandidateRouteDTO(
                routeCode,
                this.defaultText(route.title(), "路线 " + routeCode),
                this.defaultText(route.summary(), "大模型基于 POI 池生成的路线草案"),
                stayDurationMinutes,
                0,
                budgetCent == 0 ? null : budgetCent,
                Boolean.TRUE.equals(route.needsBackendReview()) ? RiskLevel.MEDIUM : RiskLevel.LOW,
                this.defaultText(route.explanation(), "路线经后端校验后会继续调用高德计算真实交通距离和耗时。"),
                stops,
                List.of(),
                route.qualityScore() == null ? 0 : route.qualityScore()
        );
    }

    private RouteStopDTO toRouteStop(
            String routeCode,
            int order,
            PoiCandidateDTO candidate,
            LlmComposedStopDTO stop
    ) {
        int stayMinutes = stop.stayMinutes() == null || stop.stayMinutes() <= 0
                ? this.defaultStayMinutes(candidate)
                : stop.stayMinutes();
        return new RouteStopDTO(
                candidate.poiId() + "-" + routeCode,
                order,
                candidate.name(),
                this.slotLabel(candidate, stop.routeRole()),
                candidate.category(),
                candidate.location(),
                candidate.amapRating(),
                stayMinutes,
                null,
                null,
                null,
                this.defaultText(stop.description(), this.defaultText(candidate.description(), candidate.name())),
                candidate.imageUrls(),
                this.defaultText(stop.reason(), candidate.reasonSeed()),
                candidate.mustVisit() ? "必去点优先保证，若营业状态异常需要替换" : null
        );
    }

    private int defaultStayMinutes(PoiCandidateDTO candidate) {
        return switch (this.defaultText(candidate.category(), "")) {
            case "CULTURE" -> 75;
            case "SCENIC" -> 60;
            case "FOOD" -> 60;
            case "REST" -> 30;
            case "LOCAL" -> 45;
            default -> DEFAULT_STAY_MINUTES;
        };
    }

    private String slotLabel(PoiCandidateDTO candidate, String routeRole) {
        if ("MUST_VISIT".equals(routeRole) || PoiCandidateRole.MUST_VISIT == candidate.role()) {
            return "必去点";
        }
        if ("MEAL".equals(routeRole) || PoiCandidateRole.MEAL == candidate.role()) {
            return "餐饮";
        }
        if ("REST".equals(routeRole) || PoiCandidateRole.REST == candidate.role()) {
            return "休息";
        }
        if ("LOCAL".equals(routeRole) || PoiCandidateRole.LOCAL == candidate.role()) {
            return "本地体验";
        }
        if ("PHOTO".equals(routeRole)) {
            return "拍照点";
        }
        if ("BACKUP".equals(routeRole) || PoiCandidateRole.BACKUP == candidate.role()) {
            return "备选";
        }
        return this.categoryLabel(candidate.category());
    }

    private String categoryLabel(String category) {
        return switch (this.defaultText(category, "")) {
            case "CULTURE" -> "文化展馆";
            case "SCENIC" -> "景点";
            case "FOOD" -> "餐饮";
            case "REST" -> "休息";
            case "LOCAL" -> "本地街区";
            case "NIGHT" -> "夜游点";
            case "MUST_VISIT" -> "必去点";
            default -> "地点";
        };
    }

    private boolean validRouteRole(String routeRole) {
        return routeRole != null && ALLOWED_ROUTE_ROLES.contains(routeRole);
    }

    private String resolveRouteCode(String routeCode, int routeIndex) {
        if (routeCode != null && !routeCode.isBlank()) {
            return routeCode;
        }
        return switch (routeIndex) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            case 3 -> "D";
            default -> "E";
        };
    }

    private void appendWarnings(RouteGenerationContext context, String prefix, List<String> warnings) {
        if (warnings == null) {
            return;
        }
        for (String warning : warnings) {
            if (warning != null && !warning.isBlank()) {
                context.addWarning(prefix + "：" + warning);
            }
        }
    }

    private void appendBackendReviewHints(
            RouteGenerationContext context,
            String routeCode,
            List<LlmBackendReviewHintDTO> reviewHints
    ) {
        if (reviewHints == null) {
            return;
        }
        for (LlmBackendReviewHintDTO reviewHint : reviewHints) {
            if (reviewHint.message() != null && !reviewHint.message().isBlank()) {
                context.addWarning(routeCode + " 线后端复核提示：" + reviewHint.message());
            }
        }
    }

    private String poiIdFromStopId(String stopId, String routeCode) {
        String suffix = "-" + routeCode;
        return stopId.endsWith(suffix) ? stopId.substring(0, stopId.length() - suffix.length()) : stopId;
    }

    private String cleanJsonContent(String content) {
        if (content == null) {
            return "{}";
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstLineBreak = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                cleaned = cleaned.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
