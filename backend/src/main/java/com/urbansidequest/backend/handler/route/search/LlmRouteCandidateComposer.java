package com.urbansidequest.backend.handler.route.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmBackendReviewHintDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmComposedRouteDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmComposedStopDTO;
import com.urbansidequest.backend.domain.dto.llm.LlmRouteComposeResponseDTO;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.support.RouteStopIdSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmRouteCandidateComposer implements RouteCandidateComposer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmRouteCandidateComposer.class);

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
            你是 Urban Sidequest 的路线编排助手。
            你只能根据输入 JSON 中的真实 districts[].pois 候选池生成路线，不能编造 POI、坐标、距离、交通耗时、评分、营业信息或图片。
            你必须保持用户请求不变：城市、出发时间、路线时长、交通方式、路线目标、必去点和用户选择都不能更改。
            你只能决定选点、排序、停留时间、路线主题、路线说明、节点理由、warnings 和 backendReviewHints；真实路径、距离和交通耗时由后端/地图服务计算。
            request.routeGoalPolicy 和 request.budgetPolicy 是偏好；USER prompt 中的硬约束优先于这些偏好。
            如果无法满足某个需求，只能在 warnings 或 backendReviewHints 中说明原因，不能编造地点或数据。
            你必须返回合法 JSON，不要输出 Markdown、解释文字或代码块。
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            请基于下面的真实 POI 候选池生成路线草案。

            == 任务 ==
            1. 从 districts[].pois 中选点，生成恰好 5 条彼此有实质差异的路线（主题、选点不同，不能只换顺序）。
            2. 按 request.mealWindows 安排正餐；按 request.durationMinutes 与候选语义安排咖啡/休息点。
            3. 在 districtBudget 上限内组织片区；近片区能组好就不跨，点不够时再按 districtOrder 扩展。
            4. 用 request.routeGoalPolicy 和 request.budgetPolicy 调整选点、排序、主题与节点理由（倾向，不得违反硬约束）。
            5. 选点依据：primaryCategoryGroup、poiTagHits、semanticTags、rating、avgPriceCent、nearestTransit、transitAccessibility；用 mealCandidate / restCandidate / localExperienceCandidate 判断能否承担饭点/休息/本地体验；routeRoleHints 仅为角色建议。

            == 硬约束 ==
            POI 与引用：
            - 只能引用 districts[].pois 中存在的 poiId，不能新增虚构地点。
            - 每条路线必须包含全部 request.mustVisitPoiIds。
            - 只能返回 JSON，不能返回自然语言说明。
            - 无法满足某个需求时，在 warnings 说明原因，不要编造地点。

            片区与空间：
            - 每条路线使用的 district 数不得超过 districtBudget；若因必去点导致超出，以输入的 districtBudget 为准。
            - 跨多个 district 时必须保持 districtOrder 的相对顺序，同一 district 内的 stop 尽量连续；不要自行用经纬度推断远近或片区顺序，districtOrder 是唯一顺序参考。
            - 避免远距离片区之间来回折返；若必须折返，在 backendReviewHints 说明。WALK_TAXI 可跨区域，但顺序须符合城市移动常识。
            - nearestTransit 仅作可达性参考，不是已算好的交通耗时。

            时间与停留：
            - 每条路线所有 stops.stayMinutes 之和不得超过 request.durationMinutes 的 85%%（需预留交通时间，跨区域路线留更多余量，不要为贴近时长吃满上限）。
            - estimatedStayMinutes 必须等于该路线所有 stops.stayMinutes 之和。
            - 每个 stop 的 stayMinutes 须符合下方「停留时间参考」；确需超出时在 warnings 说明原因。

            stop 数量：
            - 每条路线 stop 最多 8 个；具体数量按 durationMinutes、交通方式、饭点需求、停留价值、districtBudget 和节奏综合决定。
            - 可接受较短路线，但不得为省事默认走短；条件允许时应让内容完整。短路线每个 stop 都要有明确价值，长路线须保证节奏不赶、交通可控、体验不碎片化。
            - 不要为凑满 stop 数量而加入低质量、重复或绕路的 POI。

            饭点（只以 request.mealWindows 为准）：
            - 只安排 request.mealWindows 中包含的饭点；不要因为 departureTime + durationMinutes 覆盖某窗口就额外新增饭点。
            - mealWindows 含 LUNCH 或 DINNER 时，对应饭点须安排一个 routeRole=MEAL 的 stop，优先选 mealCandidate=true 或 routeRoleHints 含 MEAL 的 POI（满足其一即可作正餐，不要求 poiTagHits 含餐点标签，也不要因此产生 warning）。
            - 仅当候选池没有合适 mealCandidate 时，才用其他 POI 充当并在 warnings 说明。
            - 每个 routeRole=MEAL 的 stop 必须填写 intendedMealWindow，取值为 LUNCH 或 DINNER，且与其所属饭点一致；非正餐 stop 的 intendedMealWindow 填 null。
            - 未能安排 mealWindows 中的某个饭点时，必须在 warnings 说明原因。

            路线多样性（高优先级质量约束，优先于 routeGoalPolicy 和 budgetPolicy 的主题偏好；候选池高度同质时允许例外并写 warning）：
            - 同质 POI（相同 typecode、相同 rawType，或明显属于同一种体验，如连续广场、连续公园、连续商场）原则上最多连续 2 个，避免连续 3 个及以上。
            - 候选充足时，应在同质 POI 之间穿插不同 primaryCategoryGroup、routeRole 或体验类型的 stop。
            - 仅当候选池本身高度同质、无法避免时，才允许连续同质，并在 warnings 说明“候选池同质，路线多样性受限”。

            角色与枚举：
            - routeRole 只能取 MUST_VISIT、ANCHOR、MEAL、REST、LOCAL、PHOTO、BACKUP，不能输出 SCENIC、CULTURE、FOOD、COFFEE 等枚举外值。
            - 休息/补给点优先选 restCandidate=true；本地体验点优先选 localExperienceCandidate=true 或 semanticTags 含 LOCAL。

            == 参考 ==
            饭点定义：request.mealWindows 只含 LUNCH / DINNER；mealWindowDefinitions 仅用于理解 LUNCH / DINNER 的时间含义，不据此自行推断额外饭点。

            停留时间参考：
            - 文化展馆/博物馆：60-90 分钟
            - 公园/景点：45-75 分钟
            - 正餐餐饮：45-75 分钟
            - 咖啡/休息：20-40 分钟
            - 拍照点/轻量打卡：15-30 分钟
            - 普通街区体验：30-60 分钟

            == 输出 JSON Schema ==
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
                      "intendedMealWindow": "LUNCH | DINNER | null",
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

    private final LlmRoutePromptPayloadFactory promptPayloadFactory;

    public LlmRouteCandidateComposer(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            LlmRoutePromptPayloadFactory promptPayloadFactory
    ) {
        this.chatClient = chatClientBuilder
                .defaultTemplateRenderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .build();
        this.objectMapper = objectMapper;
        this.promptPayloadFactory = promptPayloadFactory;
    }

    @Override
    public List<CandidateRouteDTO> composeRoutes(RouteGenerationContext context) {
        String responseContent;
        String userPrompt = this.buildUserPrompt(context);
        long startedAt = System.nanoTime();
        LOGGER.info(
                "LLM 路线编排开始，candidateSetId={}，poiPoolSize={}，systemPromptChars={}，userPromptChars={}，targetRouteCount={}",
                context.getCandidateSetId(),
                context.getPoiCandidates().size(),
                SYSTEM_PROMPT.length(),
                userPrompt.length(),
                MAX_ROUTE_COUNT
        );
        try {
            ChatResponse chatResponse = this.chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            responseContent = this.responseContent(chatResponse);
            context.setLlmRouteComposeModelId(this.responseModelId(chatResponse));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "LLM 路线编排失败，candidateSetId={}，elapsedMs={}，poiPoolSize={}，userPromptChars={}",
                    context.getCandidateSetId(),
                    this.elapsedMillis(startedAt),
                    context.getPoiCandidates().size(),
                    userPrompt.length(),
                    exception
            );
            context.addWarning("大模型路线编排调用失败：" + exception.getMessage());
            return List.of();
        }
        LOGGER.info(
                "LLM 路线编排返回，candidateSetId={}，elapsedMs={}，responseChars={}",
                context.getCandidateSetId(),
                this.elapsedMillis(startedAt),
                responseContent == null ? 0 : responseContent.length()
        );
        List<CandidateRouteDTO> routes = this.toCandidateRoutes(context, responseContent);
        LOGGER.info(
                "LLM 路线编排解析完成，candidateSetId={}，mappedRoutes={}，warnings={}",
                context.getCandidateSetId(),
                routes.size(),
                context.getWarnings().size()
        );
        return routes;
    }

    private String responseContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private String responseModelId(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getModel() == null) {
            return null;
        }
        String modelId = chatResponse.getMetadata().getModel().trim();
        return modelId.isBlank() ? null : modelId;
    }

    String buildUserPrompt(RouteGenerationContext context) {
        try {
            return USER_PROMPT_TEMPLATE.formatted(
                    this.objectMapper.writeValueAsString(this.promptPayloadFactory.toPromptPayload(context))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化路线编排输入", exception);
        }
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
                context.addWarning(routeCode + " 线引用了不存在的 POI：" + stop.poiId() + "，已丢弃该候选路线");
                return null;
            }
            if (!usedPoiIds.add(candidate.poiId())) {
                context.addWarning(routeCode + " 线重复引用 POI：" + candidate.name());
                continue;
            }
            LlmComposedStopDTO normalizedStop = stop;
            if (!this.validRouteRole(stop.routeRole())) {
                context.addWarning(routeCode + " 线 POI " + candidate.name() + " 的 routeRole 不合法：" + stop.routeRole() + "，已降级为 BACKUP");
                normalizedStop = new LlmComposedStopDTO(
                        stop.order(),
                        stop.poiId(),
                        "BACKUP",
                        stop.intendedMealWindow(),
                        stop.stayMinutes(),
                        stop.description(),
                        stop.reason()
                );
            }
            stops.add(this.toRouteStop(routeCode, stops.size() + 1, candidate, normalizedStop));
        }
        if (stops.size() < MIN_STOP_COUNT) {
            context.addWarning(routeCode + " 线有效 stop 数量不足，已跳过");
            return null;
        }
        this.appendWarnings(context, routeCode + " 线模型提示", route.warnings());
        this.appendBackendReviewHints(context, routeCode, route.backendReviewHints());

        int stayDurationMinutes = stops.stream().mapToInt(RouteStopDTO::stayMinutes).sum();
        int budgetCent = stops.stream()
                .map(stop -> candidatesByPoiId.get(RouteStopIdSupport.poiIdFromStopId(stop.stopId(), routeCode)))
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
                stop.routeRole(),
                stop.intendedMealWindow(),
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
        if ("BACKUP".equals(routeRole)) {
            return "备选";
        }
        if ("LOCAL".equals(routeRole) || PoiCandidateRole.LOCAL == candidate.role()) {
            return "本地体验";
        }
        if ("PHOTO".equals(routeRole)) {
            return "拍照点";
        }
        if (PoiCandidateRole.BACKUP == candidate.role()) {
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

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
