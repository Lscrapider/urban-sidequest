package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.test.util.ReflectionTestUtils;

class LlmRouteCandidateComposerTest {

    @Test
    void dropsWholeRouteWhenAnyStopReferencesPoiOutsidePool() {
        RouteGenerationContext context = contextWithCandidates();
        String response = """
                {
                  "routes": [
                    {
                      "routeCode": "A",
                      "title": "路线 A",
                      "summary": "summary",
                      "explanation": "explanation",
                      "qualityScore": 90,
                      "stops": [
                        {"poiId": "p1", "routeRole": "LOCAL", "stayMinutes": 30},
                        {"poiId": "missing", "routeRole": "LOCAL", "stayMinutes": 30},
                        {"poiId": "p2", "routeRole": "LOCAL", "stayMinutes": 30}
                      ]
                    },
                    {
                      "routeCode": "B",
                      "title": "路线 B",
                      "summary": "summary",
                      "explanation": "explanation",
                      "qualityScore": 80,
                      "stops": [
                        {"poiId": "p1", "routeRole": "LOCAL", "stayMinutes": 30},
                        {"poiId": "p2", "routeRole": "PHOTO", "stayMinutes": 30}
                      ]
                    }
                  ]
                }
                """;

        List<CandidateRouteDTO> routes = toCandidateRoutes(context, response);

        assertThat(routes)
                .extracting(CandidateRouteDTO::routeCode)
                .containsExactly("B");
        assertThat(context.getWarnings())
                .anyMatch(warning -> warning.contains("A 线引用了不存在的 POI：missing"));
    }

    @Test
    void downgradesInvalidAndNullRouteRoleToBackup() {
        RouteGenerationContext context = contextWithCandidates();
        String response = """
                {
                  "routes": [
                    {
                      "routeCode": "A",
                      "title": "路线 A",
                      "summary": "summary",
                      "explanation": "explanation",
                      "qualityScore": 90,
                      "stops": [
                        {"poiId": "p1", "routeRole": "SCENIC", "stayMinutes": 30},
                        {"poiId": "p2", "routeRole": null, "stayMinutes": 30}
                      ]
                    }
                  ]
                }
                """;

        List<CandidateRouteDTO> routes = toCandidateRoutes(context, response);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).stops())
                .extracting(stop -> stop.routeRole())
                .containsExactly("BACKUP", "BACKUP");
        assertThat(routes.get(0).stops())
                .extracting(stop -> stop.slotLabel())
                .containsExactly("备选", "备选");
        assertThat(context.getWarnings())
                .anyMatch(warning -> warning.contains("routeRole 不合法"));
    }

    @SuppressWarnings("unchecked")
    private static List<CandidateRouteDTO> toCandidateRoutes(RouteGenerationContext context, String response) {
        return (List<CandidateRouteDTO>) ReflectionTestUtils.invokeMethod(
                composer(),
                "toCandidateRoutes",
                context,
                response
        );
    }

    private static LlmRouteCandidateComposer composer() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultTemplateRenderer(any(TemplateRenderer.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new LlmRouteCandidateComposer(builder, new ObjectMapper().findAndRegisterModules(), mock(LlmRoutePromptPayloadFactory.class));
    }

    private static RouteGenerationContext contextWithCandidates() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );
        context.setPoiCandidates(List.of(candidate("p1"), candidate("p2")));
        return context;
    }

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(120);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static PoiCandidateDTO candidate(String poiId) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "LOCAL",
                PoiCandidateRole.LOCAL,
                new GeoPointDTO(new BigDecimal("121.0000"), new BigDecimal("31.0000")),
                "address",
                "description",
                new BigDecimal("4.6"),
                null,
                List.of("LOCAL"),
                List.of(),
                List.of(),
                "MEDIUM",
                false,
                "reason"
        );
    }
}
