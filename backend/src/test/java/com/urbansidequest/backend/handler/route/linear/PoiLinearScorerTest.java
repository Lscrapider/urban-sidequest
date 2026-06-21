package com.urbansidequest.backend.handler.route.linear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoiLinearScorerTest {

    @Test
    void softensDistanceCostByTransportProfileDeltaTable() {
        PoiLinearScorer scorer = new PoiLinearScorer();
        PoiLinearFeatures distanceOnly = distanceOnlyFeatures();

        PoiLinearScoreDTO taxiScore = scorer.score(poi(), distanceOnly, context(TransportProfile.WALK_TAXI));
        PoiLinearScoreDTO busScore = scorer.score(poi(), distanceOnly, context(TransportProfile.WALK_BUS));
        PoiLinearScoreDTO walkOnlyScore = scorer.score(poi(), distanceOnly, context(TransportProfile.WALK_ONLY));

        assertThat(taxiScore.distanceCost()).isCloseTo(-0.15d, within(0.000001d));
        assertThat(busScore.distanceCost()).isCloseTo(-0.30d, within(0.000001d));
        assertThat(walkOnlyScore.distanceCost()).isCloseTo(-0.46d, within(0.000001d));
    }

    @Test
    void keepsTransitScoreIndependentFromTransportProfileDelta() {
        PoiLinearScorer scorer = new PoiLinearScorer();
        PoiLinearFeatures transitOnly = transitOnlyFeatures();

        PoiLinearScoreDTO taxiScore = scorer.score(poi(), transitOnly, context(TransportProfile.WALK_TAXI));
        PoiLinearScoreDTO walkOnlyScore = scorer.score(poi(), transitOnly, context(TransportProfile.WALK_ONLY));

        assertThat(taxiScore.transportScore()).isCloseTo(0.08d, within(0.000001d));
        assertThat(walkOnlyScore.transportScore()).isCloseTo(0.08d, within(0.000001d));
    }

    @Test
    void keepsClusterConnectivityIndependentFromTransportProfileDelta() {
        PoiLinearScorer scorer = new PoiLinearScorer();
        PoiLinearFeatures clusterOnly = clusterOnlyFeatures();

        PoiLinearScoreDTO taxiScore = scorer.score(poi(), clusterOnly, context(TransportProfile.WALK_TAXI));
        PoiLinearScoreDTO walkOnlyScore = scorer.score(poi(), clusterOnly, context(TransportProfile.WALK_ONLY));

        assertThat(taxiScore.distanceCost()).isCloseTo(0.03d, within(0.000001d));
        assertThat(walkOnlyScore.distanceCost()).isCloseTo(0.03d, within(0.000001d));
    }

    private static PoiLinearFeatures distanceOnlyFeatures() {
        return new PoiLinearFeatures(
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1d,
                1d,
                0d,
                1d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1000d,
                5000d
        );
    }

    private static PoiLinearFeatures clusterOnlyFeatures() {
        return new PoiLinearFeatures(
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1000d,
                5000d
        );
    }

    private static PoiLinearFeatures transitOnlyFeatures() {
        return new PoiLinearFeatures(
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1d,
                0d,
                1d,
                1d,
                1d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1000d,
                5000d
        );
    }

    private static LinearScoringContext context(TransportProfile transportProfile) {
        return new LinearScoringContext(
                RouteGoal.STEADY,
                transportProfile,
                BudgetLevel.NORMAL,
                RouteTimeStructure.AFTERNOON,
                5000d,
                5000d,
                600,
                0d,
                0d,
                0d,
                true,
                UserPreferenceProfileDTO.empty(),
                0d
        );
    }

    private static PoiCandidateDTO poi() {
        return new PoiCandidateDTO(
                "poi-1",
                "amap-1",
                "测试 POI",
                "LOCAL",
                PoiCandidateRole.LOCAL,
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                "address",
                "description",
                new BigDecimal("4.6"),
                null,
                List.of(),
                List.of(),
                List.of(),
                "MEDIUM",
                false,
                "reason"
        );
    }
}
