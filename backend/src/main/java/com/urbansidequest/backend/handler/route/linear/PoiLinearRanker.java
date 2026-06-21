package com.urbansidequest.backend.handler.route.linear;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.enums.WeatherBucket;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Linear Ranker 编排：解析语义、规约特征、叠 M_base+Delta 打分，返回与候选顺序对齐的子分数列表。
 */
@Component
public class PoiLinearRanker {

    private static final int DEFAULT_DURATION_MINUTES = 180;

    private final PoiSemanticResolver semanticResolver;

    private final PoiLinearFeatureExtractor featureExtractor;

    private final PoiLinearScorer scorer;

    public PoiLinearRanker(
            PoiSemanticResolver semanticResolver,
            PoiLinearFeatureExtractor featureExtractor,
            PoiLinearScorer scorer
    ) {
        this.semanticResolver = semanticResolver;
        this.featureExtractor = featureExtractor;
        this.scorer = scorer;
    }

    public List<PoiLinearScoreDTO> score(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        LinearScoringContext env = this.buildContext(context);
        List<String> requestTags = context.getGenerateParam().getInterestTags();

        List<PoiSemanticProfile> semanticProfiles = new ArrayList<>(candidates.size());
        double[] distanceToCenter = new double[candidates.size()];
        RouteAreaDTO area = context.getArea();
        GeoPointDTO center = area == null ? null : area.center();
        for (int i = 0; i < candidates.size(); i++) {
            PoiCandidateDTO candidate = candidates.get(i);
            semanticProfiles.add(this.semanticResolver.resolve(candidate, context.getPoiSemanticMappings()));
            distanceToCenter[i] = this.distanceToCenter(candidate, center);
        }

        List<PoiLinearScoreDTO> scores = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            PoiLinearFeatures features = this.featureExtractor.extract(
                    i, candidates, semanticProfiles, distanceToCenter, requestTags, env);
            scores.add(this.scorer.score(candidates.get(i), features, env));
        }
        return scores;
    }

    private LinearScoringContext buildContext(RouteGenerationContext context) {
        RouteGenerateParam param = context.getGenerateParam();
        TransportProfile transport = param.getTransportProfile();
        int durationMinutes = param.getDurationMinutes() == null ? DEFAULT_DURATION_MINUTES : param.getDurationMinutes();
        DurationBucket bucket = DurationBucket.fromMinutes(durationMinutes);

        double poiDistanceRef = transport == null ? 2500d : transport.poiDistanceRefMeters(bucket);
        double requestRadius = context.getArea() == null ? poiDistanceRef : context.getArea().radiusMeters();
        double effectiveRadius = Math.min(requestRadius <= 0 ? poiDistanceRef : requestRadius, poiDistanceRef);
        double fatigueRef = transport == null ? 1500d : transport.walkingFatigueRefMeters(bucket);

        RouteWeatherDTO weather = context.getRouteWeather();
        RouteTimeStructure timeStructure = RouteTimeStructure.fromWindow(param.getDepartureTime(), durationMinutes);
        int routeStartMinutes = this.routeStartMinutes(param.getDepartureTime());

        UserPreferenceProfileDTO profile = context.getUserPreferenceProfile();
        double affinityNorm = 0d;
        for (BigDecimal affinity : profile.tagAffinities().values()) {
            if (affinity != null) {
                affinityNorm += affinity.doubleValue();
            }
        }

        return new LinearScoringContext(
                param.getRouteGoal(),
                transport,
                param.getBudgetLevel(),
                timeStructure,
                effectiveRadius,
                fatigueRef,
                routeStartMinutes,
                this.badWeatherSeverity(weather),
                this.rainLevel(weather),
                this.heatLevel(weather),
                context.isTransportSignalAvailable(),
                profile,
                affinityNorm
        );
    }

    private int routeStartMinutes(java.time.Instant departureTime) {
        if (departureTime == null) {
            return -1;
        }
        java.time.ZonedDateTime zoned = departureTime.atZone(java.time.ZoneId.of("Asia/Shanghai"));
        return zoned.getHour() * 60 + zoned.getMinute();
    }

    private double distanceToCenter(PoiCandidateDTO candidate, GeoPointDTO center) {
        if (candidate.amapDistanceMeters() != null) {
            return candidate.amapDistanceMeters();
        }
        if (candidate.location() != null && center != null) {
            return GeoMath.distanceMeters(candidate.location(), center);
        }
        return -1d;
    }

    private double badWeatherSeverity(RouteWeatherDTO weather) {
        WeatherBucket bucket = weather == null ? WeatherBucket.UNKNOWN : weather.weatherBucket();
        return switch (bucket) {
            case RAIN -> 0.6d;
            case SNOW -> 0.8d;
            case EXTREME -> 1.0d;
            default -> 0d;
        };
    }

    private double rainLevel(RouteWeatherDTO weather) {
        WeatherBucket bucket = weather == null ? WeatherBucket.UNKNOWN : weather.weatherBucket();
        return switch (bucket) {
            case RAIN -> 0.6d;
            case SNOW -> 0.4d;
            default -> 0d;
        };
    }

    private double heatLevel(RouteWeatherDTO weather) {
        if (weather == null || weather.temperatureLevel() == null) {
            return 0d;
        }
        return weather.temperatureLevel() == com.urbansidequest.backend.domain.enums.TemperatureLevel.HOT ? 0.7d : 0d;
    }
}
