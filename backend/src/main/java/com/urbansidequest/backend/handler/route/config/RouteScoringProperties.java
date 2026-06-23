package com.urbansidequest.backend.handler.route.config;

import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RouteScoringProperties {

    private static final String PREFIX = "route.scoring.";

    private final Map<String, Double> doubleValues;

    private final Map<String, Integer> intValues;

    public RouteScoringProperties() {
        this(resolveConfigResource());
    }

    public RouteScoringProperties(Path configPath) {
        this(new FileSystemResource(configPath));
    }

    private RouteScoringProperties(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException("路线打分配置文件不存在：" + resource.getDescription());
        }
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        Properties loaded = factory.getObject();
        if (loaded == null || loaded.isEmpty()) {
            throw new IllegalStateException("路线打分配置文件为空：" + resource.getDescription());
        }
        this.validate(loaded);
        this.doubleValues = buildDoubleValues(loaded);
        this.intValues = buildIntValues(loaded);
    }

    public double linearWeight(String path) {
        return this.requiredDouble("linear-weights." + path);
    }

    public double goalDelta(RouteGoal goal, String target) {
        return this.optionalDouble("goal-delta." + goal.name() + "." + target);
    }

    public double transportDelta(TransportProfile profile, String target) {
        return this.optionalDouble("transport-delta." + profile.name() + "." + target);
    }

    public double budgetDelta(BudgetLevel budgetLevel, String target) {
        return this.optionalDouble("budget-delta." + budgetLevel.name() + "." + target);
    }

    public double linearConstantDouble(String path) {
        return this.requiredDouble("linear-constants." + path);
    }

    public int linearConstantInt(String path) {
        return this.requiredInt("linear-constants." + path);
    }

    public double linearRankerDouble(String path) {
        return this.requiredDouble("linear-ranker." + path);
    }

    public int linearRankerInt(String path) {
        return this.requiredInt("linear-ranker." + path);
    }

    public double featureExtractorDouble(String path) {
        return this.requiredDouble("feature-extractor." + path);
    }

    public int featureExtractorInt(String path) {
        return this.requiredInt("feature-extractor." + path);
    }

    public int transportProfileMeters(TransportProfile profile, String bandGroup, DurationBucket bucket) {
        return this.requiredInt("transport-profile." + profile.name() + "." + bandGroup + "." + bucketKey(bucket));
    }

    public DurationBucket durationBucket(int durationMinutes) {
        if (durationMinutes <= this.requiredInt("duration-bucket.short-max-minutes")) {
            return DurationBucket.SHORT;
        }
        if (durationMinutes <= this.requiredInt("duration-bucket.half-day-max-minutes")) {
            return DurationBucket.HALF_DAY;
        }
        return DurationBucket.FULL_DAY;
    }

    public int districtBudget(TransportProfile profile, DurationBucket bucket) {
        String profileKey = profile == null ? "null-default" : profile.name();
        return this.requiredInt("district-planner.budget." + profileKey + "." + bucketKey(bucket));
    }

    public int diversitySamplerInt(String path) {
        return this.requiredInt("diversity-sampler." + path);
    }

    public double diversitySamplerDouble(String path) {
        return this.requiredDouble("diversity-sampler." + path);
    }

    public double diversityTemperature(TransportProfile profile) {
        String profileKey = profile == null ? "null-default" : profile.name();
        return this.requiredDouble("diversity-sampler.temperature." + profileKey);
    }

    public int midFarQuota(TransportProfile profile) {
        return profile == null ? 0 : this.requiredInt("diversity-sampler.mid-far-quota." + profile.name());
    }

    public int routeXInt(String path) {
        return this.requiredInt("route-x." + path);
    }

    public double routeXDouble(String path) {
        return this.requiredDouble("route-x." + path);
    }

    public double routeConstraintDouble(String path) {
        return this.requiredDouble("route-constraints." + path);
    }

    public int routeConstraintInt(String path) {
        return this.requiredInt("route-constraints." + path);
    }

    public double routeXBudgetCap(BudgetLevel budgetLevel) {
        return this.requiredDouble("route-x.budget-cap." + budgetLevel.name());
    }

    public double estimateDurationDouble(String path) {
        return this.requiredDouble("estimate-duration." + path);
    }

    public int routeGoalScoringInt(String path) {
        return this.requiredInt("route-goal-scoring." + path);
    }

    public double clampScore(double score) {
        return Math.max(
                this.linearConstantDouble("score-min"),
                Math.min(this.linearConstantDouble("score-max"), score)
        );
    }

    public static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static Resource resolveConfigResource() {
        String override = System.getProperty("route.scoring.config.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("ROUTE_SCORING_CONFIG_PATH");
        }
        if (override != null && !override.isBlank()) {
            return new FileSystemResource(Path.of(override.trim()));
        }
        Path backendRelative = Path.of("config/private/route-scoring.yml");
        if (Files.isRegularFile(backendRelative)) {
            return new FileSystemResource(backendRelative);
        }
        Path repoRelative = Path.of("backend/config/private/route-scoring.yml");
        if (Files.isRegularFile(repoRelative)) {
            return new FileSystemResource(repoRelative);
        }
        return new FileSystemResource(repoRelative);
    }

    private void validate(Properties properties) {
        List<String> missing = new ArrayList<>();
        this.requirePaths(properties, missing,
                "linear-weights.interest-match",
                "linear-weights.goal.classic",
                "linear-weights.goal.local",
                "linear-weights.goal.photo",
                "linear-weights.goal.night-friendly",
                "linear-weights.goal.quiet",
                "linear-weights.goal.hidden",
                "linear-weights.goal.night-match",
                "linear-weights.goal.meal-match",
                "linear-weights.quality.rating",
                "linear-weights.quality.has-image",
                "linear-weights.quality.rating-missing",
                "linear-weights.transit.high",
                "linear-weights.transit.medium",
                "linear-weights.transit.low",
                "linear-weights.transit.dist",
                "linear-weights.transit.walk-access",
                "linear-weights.transit.rain-risk",
                "linear-weights.distance.norm",
                "linear-weights.distance.isolated",
                "linear-weights.distance.cluster",
                "linear-weights.distance.fatigue",
                "linear-weights.distance.heat-fatigue",
                "linear-weights.budget.avg-price",
                "linear-weights.budget.price-missing",
                "linear-weights.budget.free",
                "linear-weights.budget.expensive",
                "linear-weights.risk.close",
                "linear-weights.risk.weather-outdoor",
                "linear-weights.risk.category-dup",
                "linear-weights.risk.missing-info",
                "linear-weights.personalization.user-affinity",
                "linear-weights.personalization.dist-pressure",
                "linear-weights.personalization.budget-pressure",
                "linear-weights.personalization.transit-pressure",
                "linear-weights.personalization.exploration",
                "linear-weights.night-delta.nearest-transit-distance-norm",
                "linear-weights.night-delta.distance-norm",
                "linear-weights.night-delta.isolated",
                "linear-constants.budget-cap-cent",
                "linear-constants.transit-ref-meters",
                "linear-constants.walk-ref-meters",
                "linear-constants.short-walk-segment-meters",
                "linear-constants.subway-min-segment-meters",
                "linear-constants.mid-distance-segment-meters",
                "linear-constants.transit-access-max-meters",
                "linear-constants.max-walk-fallback-meters",
                "linear-constants.neighbor-radius-meters",
                "linear-constants.connect-full",
                "linear-constants.dup-full",
                "linear-constants.distance-norm-cap",
                "linear-constants.price-norm-cap",
                "linear-constants.transit-dist-cap",
                "linear-constants.rating-missing-default",
                "linear-constants.price-missing-default",
                "linear-constants.close-risk-missing-default",
                "linear-constants.rating-full",
                "linear-constants.score-min",
                "linear-constants.score-max",
                "linear-ranker.default-duration-minutes",
                "linear-ranker.null-transport.poi-distance-ref",
                "linear-ranker.null-transport.fatigue-ref",
                "linear-ranker.weather-severity.rain",
                "linear-ranker.weather-severity.snow",
                "linear-ranker.weather-severity.extreme",
                "linear-ranker.rain-level.rain",
                "linear-ranker.rain-level.snow",
                "linear-ranker.heat-level.hot",
                "feature-extractor.missing-info.image-weight",
                "feature-extractor.missing-info.rating-weight",
                "feature-extractor.unknown-distance.norm",
                "feature-extractor.unknown-distance.fatigue-pressure",
                "feature-extractor.isolation-ref-divisor",
                "feature-extractor.expensive-risk-offset",
                "feature-extractor.transit-local-fail.medium",
                "feature-extractor.transit-local-fail.distance-norm",
                "feature-extractor.transit-local-fail.score",
                "feature-extractor.transit-empty.low",
                "feature-extractor.transit-empty.distance-norm",
                "feature-extractor.transit-empty.score",
                "feature-extractor.transit-high-meters",
                "feature-extractor.transit-medium-meters",
                "feature-extractor.walk-access.walk-weight",
                "feature-extractor.walk-access.transit-weight",
                "feature-extractor.walk-access.unknown-walk-part",
                "duration-bucket.short-max-minutes",
                "duration-bucket.half-day-max-minutes",
                "diversity-sampler.max-llm-poi-count",
                "diversity-sampler.min.meal",
                "diversity-sampler.min.rest",
                "diversity-sampler.min.anchor",
                "diversity-sampler.max-category-ratio",
                "diversity-sampler.backup-ratio",
                "diversity-sampler.category-penalty",
                "diversity-sampler.backup-penalty",
                "diversity-sampler.mid-distance-ratio",
                "diversity-sampler.clean-quality-min",
                "diversity-sampler.floors.backup",
                "diversity-sampler.floors.category",
                "route-x.max-stop-count",
                "route-x.top-tag-k",
                "route-x.stay-budget-ratio",
                "route-x.segment-comfort-duration-minutes",
                "route-x.min-segment-comfort-distance-meters",
                "route-x.long-segment-pressure-threshold",
                "route-x.visited-vicinity-threshold-meters",
                "route-x.risk-cost-threshold",
                "route-x.distance-pressure-clamp-max",
                "route-x.budget-pressure.offset",
                "route-x.budget-pressure.clamp-max",
                "route-x.expensive-risk-offset",
                "route-x.rating-missing-default",
                "route-x.avg-price-missing-default",
                "route-x.time-budget.target-usage-ratio",
                "route-x.time-budget.max-comfort-usage-ratio",
                "route-x.travel-pressure.physical-distance-ref-meters",
                "route-x.travel-pressure.physical-segment-comfort-minutes",
                "route-x.travel-pressure.scheduled-distance-ref-meters",
                "route-x.travel-pressure.scheduled-segment-comfort-minutes",
                "route-x.travel-pressure.private-motor-distance-ref-meters",
                "route-x.travel-pressure.private-motor-segment-comfort-minutes",
                "route-x.travel-pressure.bucket-switch-ref-count",
                "route-x.travel-pressure.norm-clamp-max",
                "route-x.transit.no-facility.medium",
                "route-x.transit.no-facility.distance-norm",
                "route-x.transit.has-facility-no-distance.low",
                "route-x.transit.has-facility-no-distance.distance-norm",
                "route-x.transit.high-meters",
                "route-x.transit.medium-meters",
                "estimate-duration.bike.speed",
                "estimate-duration.bike.base",
                "estimate-duration.transit.speed",
                "estimate-duration.transit.base",
                "estimate-duration.taxi.speed",
                "estimate-duration.taxi.base",
                "estimate-duration.walk.speed",
                "route-goal-scoring.base-score",
                "route-goal-scoring.per-stop",
                "route-goal-scoring.low-risk-bonus",
                "route-goal-scoring.steady-distance-divisor",
                "route-goal-scoring.quiet-low-risk-bonus",
                "route-goal-scoring.classic-per-stop",
                "route-goal-scoring.local-hit-bonus",
                "route-goal-scoring.low-budget-missing-bonus",
                "route-goal-scoring.low-budget-divisor",
                "route-goal-scoring.night-hit-bonus",
                "route-goal-scoring.photo-hit-bonus",
                "route-constraints.duration-hard-overrun-minutes",
                "route-constraints.duration-hard-overrun-ratio"
        );
        this.validateGoalDelta(properties, missing);
        this.validateTransportDelta(properties, missing);
        this.validateBudgetDelta(properties, missing);
        this.validateTransportProfile(properties, missing);
        this.validateDistrictBudget(properties, missing);
        this.validateDiversityMaps(properties, missing);
        this.validateRouteXBudgetCap(properties, missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("路线打分配置缺少字段：" + String.join(", ", missing));
        }
    }

    private void validateGoalDelta(Properties properties, List<String> missing) {
        this.requireEnumSections(properties, missing, "goal-delta", RouteGoal.values());
        this.requirePaths(properties, missing,
                "goal-delta.STEADY.quiet",
                "goal-delta.STEADY.hidden",
                "goal-delta.QUIET.quiet",
                "goal-delta.QUIET.nightFriendly",
                "goal-delta.CLASSIC.classic",
                "goal-delta.CLASSIC.hidden",
                "goal-delta.LOCAL.local",
                "goal-delta.LOCAL.hidden",
                "goal-delta.LOCAL.classic",
                "goal-delta.NIGHT.nightFriendly",
                "goal-delta.PHOTO.photo",
                "goal-delta.PHOTO.classic"
        );
    }

    private void validateTransportDelta(Properties properties, List<String> missing) {
        for (TransportProfile profile : TransportProfile.values()) {
            this.requirePaths(properties, missing,
                    "transport-delta." + profile.name() + ".distanceNorm",
                    "transport-delta." + profile.name() + ".isolated",
                    "transport-delta." + profile.name() + ".fatigue"
            );
        }
    }

    private void validateBudgetDelta(Properties properties, List<String> missing) {
        this.requireEnumSections(properties, missing, "budget-delta", BudgetLevel.values());
        this.requirePaths(properties, missing,
                "budget-delta.LOW.price",
                "budget-delta.LOW.expensive",
                "budget-delta.LOW.free",
                "budget-delta.FLEXIBLE.price",
                "budget-delta.FLEXIBLE.expensive"
        );
    }

    private void validateTransportProfile(Properties properties, List<String> missing) {
        for (TransportProfile profile : TransportProfile.values()) {
            for (String group : List.of("search-radius", "poi-distance-ref", "route-distance-ref", "walking-fatigue-ref")) {
                for (DurationBucket bucket : DurationBucket.values()) {
                    this.requirePaths(properties, missing,
                            "transport-profile." + profile.name() + "." + group + "." + bucketKey(bucket));
                }
            }
        }
    }

    private void validateDistrictBudget(Properties properties, List<String> missing) {
        for (TransportProfile profile : TransportProfile.values()) {
            for (DurationBucket bucket : DurationBucket.values()) {
                this.requirePaths(properties, missing,
                        "district-planner.budget." + profile.name() + "." + bucketKey(bucket));
            }
        }
        for (DurationBucket bucket : DurationBucket.values()) {
            this.requirePaths(properties, missing,
                    "district-planner.budget.null-default." + bucketKey(bucket),
                    "district-planner.budget.walk-only." + bucketKey(bucket)
            );
        }
    }

    private void validateDiversityMaps(Properties properties, List<String> missing) {
        this.requirePaths(properties, missing, "diversity-sampler.temperature.null-default");
        for (TransportProfile profile : TransportProfile.values()) {
            this.requirePaths(properties, missing,
                    "diversity-sampler.temperature." + profile.name(),
                    "diversity-sampler.mid-far-quota." + profile.name()
            );
        }
    }

    private void validateRouteXBudgetCap(Properties properties, List<String> missing) {
        for (BudgetLevel budgetLevel : BudgetLevel.values()) {
            this.requirePaths(properties, missing, "route-x.budget-cap." + budgetLevel.name());
        }
    }

    private <E extends Enum<E>> void requireEnumSections(
            Properties properties,
            List<String> missing,
            String section,
            E[] enumValues
    ) {
        for (E enumValue : enumValues) {
            String pathPrefix = section + "." + enumValue.name() + ".";
            String keyPrefix = PREFIX + pathPrefix;
            boolean exists = propertyKeys(properties).stream()
                    .anyMatch(key -> key.startsWith(keyPrefix));
            if (!exists) {
                missing.add(section + "." + enumValue.name());
            }
        }
    }

    private void requirePaths(Properties properties, List<String> missing, String... paths) {
        for (String path : paths) {
            String key = PREFIX + path;
            if (!properties.containsKey(key)) {
                missing.add(path);
            }
        }
    }

    private double requiredDouble(String path) {
        Double value = this.doubleValues.get(path);
        if (value == null) {
            throw new IllegalStateException("路线打分配置缺少字段：" + path);
        }
        return value;
    }

    private int requiredInt(String path) {
        Integer value = this.intValues.get(path);
        if (value == null) {
            if (this.doubleValues.containsKey(path)) {
                throw new IllegalStateException("路线打分配置字段不是整数：" + path);
            }
            throw new IllegalStateException("路线打分配置缺少字段：" + path);
        }
        return value;
    }

    private double optionalDouble(String path) {
        return this.doubleValues.getOrDefault(path, 0d);
    }

    private static Map<String, Double> buildDoubleValues(Properties properties) {
        Map<String, Double> values = new HashMap<>();
        for (String key : propertyKeys(properties)) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String path = key.substring(PREFIX.length());
            String raw = String.valueOf(properties.get(key));
            try {
                values.put(path, Double.parseDouble(raw));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("路线打分配置字段不是数字：" + path, exception);
            }
        }
        return Map.copyOf(values);
    }

    private static Map<String, Integer> buildIntValues(Properties properties) {
        Map<String, Integer> values = new HashMap<>();
        for (String key : propertyKeys(properties)) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String path = key.substring(PREFIX.length());
            String raw = String.valueOf(properties.get(key));
            try {
                values.put(path, Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                // 非整数字段仍可作为 double 配置使用；int accessor 会在误用时报告字段类型错误。
            }
        }
        return Map.copyOf(values);
    }

    private static List<String> propertyKeys(Properties properties) {
        return properties.keySet().stream()
                .map(String::valueOf)
                .toList();
    }

    private static String bucketKey(DurationBucket bucket) {
        return switch (bucket) {
            case SHORT -> "short";
            case HALF_DAY -> "half-day";
            case FULL_DAY -> "full-day";
        };
    }
}
