package com.urbansidequest.backend.handler.route.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteScoringPropertiesTest {

    @Test
    void loadsStandaloneYamlAndValidatesEnumBackedMaps() {
        RouteScoringProperties properties = RouteScoringTestSupport.properties();

        assertThat(properties.durationBucket(100)).isEqualTo(DurationBucket.SHORT);
        assertThat(properties.durationBucket(101)).isEqualTo(DurationBucket.HALF_DAY);
        assertThat(properties.durationBucket(301)).isEqualTo(DurationBucket.FULL_DAY);
        assertThat(properties.goalDelta(RouteGoal.LOCAL, "hidden")).isEqualTo(0.08d);
        assertThat(properties.transportDelta(TransportProfile.WALK_TAXI, "fatigue")).isEqualTo(0.18d);
        assertThat(properties.budgetDelta(BudgetLevel.FLEXIBLE, "expensive")).isEqualTo(0.05d);
        assertThat(properties.transportProfileMeters(TransportProfile.WALK_ONLY, "search-radius", DurationBucket.SHORT))
                .isEqualTo(1000);
        assertThat(properties.midFarQuota(TransportProfile.WALK_TAXI)).isEqualTo(6);
        assertThat(properties.routeXBudgetCap(BudgetLevel.FLEXIBLE)).isEqualTo(40000d);
        assertThat(properties.routeConstraintDouble("duration-hard-overrun-ratio")).isEqualTo(0.15d);
        assertThat(properties.routeXDouble("time-budget.target-usage-ratio")).isEqualTo(0.75d);
        assertThat(properties.routeXInt("travel-pressure.bucket-switch-ref-count")).isEqualTo(3);
    }

    @Test
    void failsWhenStandaloneYamlIsMissing() {
        assertThatThrownBy(() -> new RouteScoringProperties(Path.of("src/test/resources/missing-route-scoring.yml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("路线打分配置文件不存在");
    }

    @Test
    void failsWhenRequiredFieldIsMissing(@TempDir Path tempDir) throws IOException {
        Path configPath = writeConfig(
                tempDir,
                Files.readString(Path.of("src/test/resources/route-scoring-test.yml"))
                        .replace("      interest-match: 0.11\n", "")
        );

        assertThatThrownBy(() -> new RouteScoringProperties(configPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("linear-weights.interest-match");
    }

    @Test
    void failsWhenEnumBackedDeltaSectionIsMissing(@TempDir Path tempDir) throws IOException {
        Path configPath = writeConfig(
                tempDir,
                Files.readString(Path.of("src/test/resources/route-scoring-test.yml"))
                        .replace("      LOW_BUDGET: { noop: 0 }\n", "")
        );

        assertThatThrownBy(() -> new RouteScoringProperties(configPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("goal-delta.LOW_BUDGET");
    }

    @Test
    void failsWhenNewRouteXTravelPressureFieldIsMissing(@TempDir Path tempDir) throws IOException {
        Path configPath = writeConfig(
                tempDir,
                Files.readString(Path.of("src/test/resources/route-scoring-test.yml"))
                        .replace("        bucket-switch-ref-count: 3\n", "")
        );

        assertThatThrownBy(() -> new RouteScoringProperties(configPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("route-x.travel-pressure.bucket-switch-ref-count");
    }

    private static Path writeConfig(Path tempDir, String yaml) throws IOException {
        Path configPath = tempDir.resolve("route-scoring.yml");
        Files.writeString(configPath, yaml);
        return configPath;
    }
}
