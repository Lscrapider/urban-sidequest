package com.urbansidequest.backend.handler.route.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.service.RoutePreferenceFeatureRebuildService;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 手动修复入口：用冻结原始数据按当前 Route X 算法重建 sample 特征。
 *
 * <p>这个测试会写真实数据库，默认不启用。执行全量过期 sample 修复时显式加
 * {@code -Durban.route-x.raw-snapshot.repair.enabled=true}。</p>
 *
 * <p>如只修复一个候选集，再额外加
 * {@code -Durban.route-x.raw-snapshot.repair.candidate-set-id=<candidateSetId>}。</p>
 */
@Tag("manual")
@SpringBootTest
@EnabledIfSystemProperty(named = "urban.route-x.raw-snapshot.repair.enabled", matches = "true")
class RoutePreferenceRawSnapshotRepairManualTest {

    private static final String CANDIDATE_SET_ID_PROPERTY = "urban.route-x.raw-snapshot.repair.candidate-set-id";

    private final RoutePreferenceFeatureRebuildService routePreferenceFeatureRebuildService;

    @Autowired
    RoutePreferenceRawSnapshotRepairManualTest(RoutePreferenceFeatureRebuildService routePreferenceFeatureRebuildService) {
        this.routePreferenceFeatureRebuildService = routePreferenceFeatureRebuildService;
    }

    @Test
    void rebuildsRoutePreferenceSamplesFromFrozenRawSnapshots() {
        String candidateSetId = System.getProperty(CANDIDATE_SET_ID_PROPERTY);
        int rebuiltCount;
        if (candidateSetId == null || candidateSetId.isBlank()) {
            rebuiltCount = this.routePreferenceFeatureRebuildService.rebuildOutdatedSamples();
            System.out.printf("Route X sample 修复完成：rebuiltCount=%d%n", rebuiltCount);
        } else {
            UUID parsedCandidateSetId = UUID.fromString(candidateSetId.trim());
            rebuiltCount = this.routePreferenceFeatureRebuildService.rebuildByCandidateSetId(parsedCandidateSetId);
            System.out.printf(
                    "Route X sample 修复完成：candidateSetId=%s, rebuiltCount=%d%n",
                    parsedCandidateSetId,
                    rebuiltCount
            );
        }
        assertThat(rebuiltCount).isGreaterThanOrEqualTo(0);
    }
}
