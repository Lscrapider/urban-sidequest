package com.urbansidequest.backend.handler.route.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.pipeline.RouteGenerationPipeline;
import com.urbansidequest.backend.handler.route.support.MealWindowSupport;
import com.urbansidequest.backend.service.RoutePreferenceFeatureRebuildService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 手动集成测试：真实调用高德和 LLM，验证 sample 特征可从冻结原始数据恢复到破坏前内容。
 *
 * <p>默认不启用，避免普通测试消耗外部额度。需要验证真实链路时显式加
 * {@code -Durban.route-x.raw-snapshot.full-chain.enabled=true}。</p>
 */
@Tag("manual")
@SpringBootTest(properties = "route.preference.training.raw-snapshot-enabled=true")
@EnabledIfSystemProperty(named = "urban.route-x.raw-snapshot.full-chain.enabled", matches = "true")
class RoutePreferenceRawSnapshotFullChainManualTest {

    private static final UUID USER_ID = UUID.fromString("9f3dbdb7-15a9-4643-9bf1-baae06a6cf9c");

    private static final String BROKEN_FEATURE_SCHEMA_VERSION = "broken-route-x-rebuild-probe";

    private final RouteGenerationPipeline routeGenerationPipeline;

    private final RoutePreferenceFeatureRebuildService routePreferenceFeatureRebuildService;

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    @Autowired
    RoutePreferenceRawSnapshotFullChainManualTest(
            RouteGenerationPipeline routeGenerationPipeline,
            RoutePreferenceFeatureRebuildService routePreferenceFeatureRebuildService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.routeGenerationPipeline = routeGenerationPipeline;
        this.routePreferenceFeatureRebuildService = routePreferenceFeatureRebuildService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Test
    void restoresSampleFeatureContentFromFrozenRawSnapshotAfterRealPipeline() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID candidateSetId = UUID.randomUUID();
        RouteGenerationContext context = new RouteGenerationContext(requestId, candidateSetId, USER_ID, fixedParam());

        try {
            this.routeGenerationPipeline.execute(context);

            assertThat(context.getSelectedRoutes()).isNotEmpty();
            assertThat(this.rawSnapshotCount(candidateSetId)).isOne();

            List<FeatureRow> originalRows = this.featureRows(candidateSetId);
            assertThat(originalRows).hasSameSizeAs(context.getSelectedRoutes());

            this.breakSampleFeatures(candidateSetId);
            List<FeatureRow> brokenRows = this.featureRows(candidateSetId);
            assertThat(brokenRows).hasSameSizeAs(originalRows);
            assertThat(brokenRows)
                    .allSatisfy(row -> assertThat(row.featureSchemaVersion())
                            .isEqualTo(BROKEN_FEATURE_SCHEMA_VERSION));

            int rebuiltCount = this.routePreferenceFeatureRebuildService.rebuildByCandidateSetId(candidateSetId);

            assertThat(rebuiltCount).isEqualTo(originalRows.size());
            List<FeatureRow> restoredRows = this.featureRows(candidateSetId);
            assertSameFeatureRows(restoredRows, originalRows);
        } finally {
            this.cleanup(candidateSetId);
        }
    }

    private static RouteGenerateParam fixedParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.MANUAL_POLYGON);
        param.setAreaLabel("上海中心城区 Route X 冻结恢复真实链路测试范围");
        param.setAreaPolygonGcj02(List.of(
                point("121.440000", "31.200000"),
                point("121.525000", "31.200000"),
                point("121.525000", "31.275000"),
                point("121.440000", "31.275000"),
                point("121.440000", "31.200000")
        ));
        param.setRouteCityName("上海市");
        param.setRouteCityAdcode("310000");
        param.setDepartureTime(LocalDateTime.of(2026, 6, 20, 14, 30));
        param.setDurationMinutes(420);
        param.setTransportProfile(TransportProfile.BIKE_SUBWAY);
        param.setRouteGoal(RouteGoal.PHOTO);
        param.setBudgetLevel(BudgetLevel.NORMAL);
        param.setInterestTags(List.of("PHOTO", "SCENIC", "CULTURE", "COFFEE"));
        param.setMealWindows(MealWindowSupport.feasibleMealWindows(param));
        return param;
    }

    private static GeoPointParam point(String longitude, String latitude) {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal(longitude));
        point.setLatitudeGcj02(new BigDecimal(latitude));
        return point;
    }

    private List<FeatureRow> featureRows(UUID candidateSetId) {
        return this.jdbcTemplate.query("""
                SELECT
                    route_code,
                    feature_schema_version,
                    stop_matrix_json::text AS stop_matrix_json,
                    segment_matrix_json::text AS segment_matrix_json,
                    route_derived_vector_json::text AS route_derived_vector_json,
                    context_cross_vector_json::text AS context_cross_vector_json,
                    context_json::text AS context_json
                FROM route_preference_training_samples
                WHERE candidate_set_id = ?
                ORDER BY route_code
                """, this::featureRow, candidateSetId);
    }

    private FeatureRow featureRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FeatureRow(
                resultSet.getString("route_code"),
                resultSet.getString("feature_schema_version"),
                resultSet.getString("stop_matrix_json"),
                resultSet.getString("segment_matrix_json"),
                resultSet.getString("route_derived_vector_json"),
                resultSet.getString("context_cross_vector_json"),
                resultSet.getString("context_json")
        );
    }

    private int rawSnapshotCount(UUID candidateSetId) {
        Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM route_preference_raw_snapshots WHERE candidate_set_id = ?",
                Integer.class,
                candidateSetId
        );
        return count == null ? 0 : count;
    }

    private void breakSampleFeatures(UUID candidateSetId) {
        int updatedRows = this.jdbcTemplate.update("""
                UPDATE route_preference_training_samples
                SET feature_schema_version = ?,
                    stop_matrix_json = '[]'::jsonb,
                    segment_matrix_json = '[]'::jsonb,
                    route_derived_vector_json = '{"broken": true}'::jsonb,
                    context_cross_vector_json = '{"broken": true}'::jsonb,
                    context_json = '{"broken": true}'::jsonb,
                    updated_at = now()
                WHERE candidate_set_id = ?
                """, BROKEN_FEATURE_SCHEMA_VERSION, candidateSetId);
        assertThat(updatedRows).isGreaterThan(0);
    }

    private void cleanup(UUID candidateSetId) {
        this.jdbcTemplate.update("DELETE FROM route_preference_training_samples WHERE candidate_set_id = ?", candidateSetId);
        this.jdbcTemplate.update("DELETE FROM route_preference_raw_snapshots WHERE candidate_set_id = ?", candidateSetId);
    }

    private void assertSameFeatureRows(List<FeatureRow> actualRows, List<FeatureRow> expectedRows) throws Exception {
        assertThat(actualRows).hasSameSizeAs(expectedRows);
        for (int index = 0; index < expectedRows.size(); index++) {
            FeatureRow actual = actualRows.get(index);
            FeatureRow expected = expectedRows.get(index);
            assertThat(actual.routeCode()).isEqualTo(expected.routeCode());
            assertThat(actual.featureSchemaVersion()).isEqualTo(expected.featureSchemaVersion());
            assertJsonEquals(actual.stopMatrixJson(), expected.stopMatrixJson());
            assertJsonEquals(actual.segmentMatrixJson(), expected.segmentMatrixJson());
            assertJsonEquals(actual.routeDerivedVectorJson(), expected.routeDerivedVectorJson());
            assertJsonEquals(actual.contextCrossVectorJson(), expected.contextCrossVectorJson());
            assertJsonEquals(actual.contextJson(), expected.contextJson());
        }
    }

    private void assertJsonEquals(String actualJson, String expectedJson) throws Exception {
        JsonNode actual = this.objectMapper.readTree(actualJson);
        JsonNode expected = this.objectMapper.readTree(expectedJson);
        assertThat(actual).isEqualTo(expected);
    }

    private record FeatureRow(
            String routeCode,
            String featureSchemaVersion,
            String stopMatrixJson,
            String segmentMatrixJson,
            String routeDerivedVectorJson,
            String contextCrossVectorJson,
            String contextJson
    ) {
    }
}
