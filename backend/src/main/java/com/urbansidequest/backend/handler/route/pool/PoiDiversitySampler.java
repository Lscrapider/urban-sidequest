package com.urbansidequest.backend.handler.route.pool;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * POI 预筛多样性采样层：在 Linear Ranker 的可解释分数之上做受控随机。
 *
 * <p>它不重新打分，只负责让进入 LLM 的 POI 池保持必去点、餐饮/休息补位和类别多样性，
 * 避免每次都退化为完全相同的 TopK。</p>
 */
@Component
public class PoiDiversitySampler {

    private static final int MIN_MEAL_COUNT = 2;

    private static final int MIN_REST_COUNT = 1;

    private static final int MIN_ANCHOR_COUNT = 10;

    private static final double MAX_CATEGORY_RATIO = 0.35d;

    private static final double BACKUP_RATIO = 0.15d;

    private static final double CATEGORY_PENALTY = 0.12d;

    private static final double BACKUP_PENALTY = 0.18d;

    private static final double MID_DISTANCE_RATIO = 0.45d;

    private static final double CLEAN_QUALITY_MIN = 0.20d;

    public List<RankedPoi> sample(RouteGenerationContext context, List<RankedPoi> rankedCandidates, int maxCount) {
        if (rankedCandidates.size() <= maxCount) {
            return rankedCandidates;
        }

        Map<String, RankedPoi> selected = new LinkedHashMap<>();
        int maxBackupCount = Math.max(2, (int) Math.ceil(maxCount * BACKUP_RATIO));
        int maxCategoryCount = Math.max(3, (int) Math.ceil(maxCount * MAX_CATEGORY_RATIO));

        this.addRequired(selected, rankedCandidates, maxCount, candidate -> candidate.candidate().mustVisit());
        this.addRequired(selected, rankedCandidates, maxCount, candidate -> candidate.candidate().role() == PoiCandidateRole.MEAL, MIN_MEAL_COUNT);
        this.addRequired(selected, rankedCandidates, maxCount, candidate -> candidate.candidate().role() == PoiCandidateRole.REST, MIN_REST_COUNT);
        this.addRequired(selected, rankedCandidates, maxCount, candidate -> candidate.candidate().role() == PoiCandidateRole.ANCHOR, MIN_ANCHOR_COUNT);
        this.addMidFarReserved(context, selected, rankedCandidates, maxCount, maxCategoryCount, maxBackupCount);

        List<RankedPoi> pool = new ArrayList<>(rankedCandidates);
        while (selected.size() < maxCount) {
            RankedPoi next = this.pickNext(pool, selected, this.temperature(context), maxCategoryCount, maxBackupCount, true);
            if (next == null) {
                next = this.pickNext(pool, selected, this.temperature(context), maxCategoryCount, maxBackupCount, false);
            }
            if (next == null) {
                break;
            }
            selected.put(next.candidate().poiId(), next);
        }

        return new ArrayList<>(selected.values());
    }

    private void addRequired(
            Map<String, RankedPoi> selected,
            List<RankedPoi> rankedCandidates,
            int maxCount,
            java.util.function.Predicate<RankedPoi> predicate
    ) {
        this.addRequired(selected, rankedCandidates, maxCount, predicate, Integer.MAX_VALUE);
    }

    private void addRequired(
            Map<String, RankedPoi> selected,
            List<RankedPoi> rankedCandidates,
            int maxCount,
            java.util.function.Predicate<RankedPoi> predicate,
            int limit
    ) {
        int added = 0;
        for (RankedPoi candidate : rankedCandidates) {
            if (selected.size() >= maxCount || added >= limit) {
                return;
            }
            if (predicate.test(candidate)) {
                selected.putIfAbsent(candidate.candidate().poiId(), candidate);
                added++;
            }
        }
    }

    private void addMidFarReserved(
            RouteGenerationContext context,
            Map<String, RankedPoi> selected,
            List<RankedPoi> rankedCandidates,
            int maxCount,
            int maxCategoryCount,
            int maxBackupCount
    ) {
        int quota = this.midFarQuota(context);
        if (quota <= 0 || selected.size() >= maxCount) {
            return;
        }
        int selectedMidFarCount = (int) this.countSelected(selected, candidate -> this.isMidFarCandidate(context, candidate));
        if (selectedMidFarCount >= quota) {
            return;
        }
        double cleanQualityThreshold = Math.max(CLEAN_QUALITY_MIN, this.cleanQualityP50(rankedCandidates));
        rankedCandidates.stream()
                .filter(candidate -> !selected.containsKey(candidate.candidate().poiId()))
                .filter(candidate -> this.isMidFarCandidate(context, candidate))
                .filter(candidate -> this.cleanQuality(candidate) >= cleanQualityThreshold)
                .sorted(Comparator.comparingDouble(this::cleanQuality).reversed())
                .forEach(candidate -> {
                    if (selected.size() >= maxCount) {
                        return;
                    }
                    int currentMidFarCount = (int) this.countSelected(selected, item -> this.isMidFarCandidate(context, item));
                    if (currentMidFarCount >= quota) {
                        return;
                    }
                    if (this.canSelect(candidate, selected, maxCategoryCount, maxBackupCount)) {
                        selected.putIfAbsent(candidate.candidate().poiId(), candidate);
                    }
                });
    }

    private int midFarQuota(RouteGenerationContext context) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        if (profile == null) {
            return 0;
        }
        return switch (profile) {
            case WALK_ONLY -> 0;
            case WALK_BUS -> 4;
            case WALK_SUBWAY, WALK_TRANSIT -> 6;
            case BIKE_SUBWAY, WALK_TAXI -> 8;
        };
    }

    private boolean isMidFarCandidate(RouteGenerationContext context, RankedPoi candidate) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        if (profile == null) {
            return false;
        }
        DurationBucket bucket = DurationBucket.fromMinutes(context.getGenerateParam().getDurationMinutes());
        double poiDistanceRef = profile.poiDistanceRefMeters(bucket);
        if (poiDistanceRef <= 0d) {
            return false;
        }
        return candidate.score().distanceMeters() / poiDistanceRef >= MID_DISTANCE_RATIO;
    }

    private double cleanQualityP50(List<RankedPoi> rankedCandidates) {
        if (rankedCandidates.isEmpty()) {
            return 0d;
        }
        List<Double> cleanQualities = rankedCandidates.stream()
                .map(this::cleanQuality)
                .sorted()
                .toList();
        return cleanQualities.get(cleanQualities.size() / 2);
    }

    private double cleanQuality(RankedPoi candidate) {
        PoiLinearScoreDTO score = candidate.score();
        return score.interestScore()
                + score.goalScore()
                + score.qualityScore()
                + score.transportScore()
                + score.budgetCost()
                + score.riskCost();
    }

    private RankedPoi pickNext(
            List<RankedPoi> pool,
            Map<String, RankedPoi> selected,
            double temperature,
            int maxCategoryCount,
            int maxBackupCount,
            boolean enforceDiversity
    ) {
        List<WeightedPoi> weighted = new ArrayList<>();
        double totalWeight = 0d;
        for (RankedPoi candidate : pool) {
            if (selected.containsKey(candidate.candidate().poiId())) {
                continue;
            }
            if (enforceDiversity && !this.canSelect(candidate, selected, maxCategoryCount, maxBackupCount)) {
                continue;
            }
            double adjustedScore = candidate.score().linearScore() - this.diversityPenalty(candidate, selected);
            double weight = Math.exp(adjustedScore / temperature);
            totalWeight += weight;
            weighted.add(new WeightedPoi(candidate, weight));
        }
        if (weighted.isEmpty()) {
            return null;
        }
        double cursor = ThreadLocalRandom.current().nextDouble(totalWeight);
        for (WeightedPoi item : weighted) {
            cursor -= item.weight();
            if (cursor <= 0d) {
                return item.candidate();
            }
        }
        return weighted.get(weighted.size() - 1).candidate();
    }

    private boolean canSelect(
            RankedPoi candidate,
            Map<String, RankedPoi> selected,
            int maxCategoryCount,
            int maxBackupCount
    ) {
        if (candidate.candidate().role() == PoiCandidateRole.BACKUP
                && this.countSelected(selected, item -> item.candidate().role() == PoiCandidateRole.BACKUP) >= maxBackupCount) {
            return false;
        }
        String category = this.categoryKey(candidate);
        return this.countSelected(selected, item -> this.categoryKey(item).equals(category)) < maxCategoryCount;
    }

    private double diversityPenalty(RankedPoi candidate, Map<String, RankedPoi> selected) {
        String category = this.categoryKey(candidate);
        long sameCategoryCount = this.countSelected(selected, item -> this.categoryKey(item).equals(category));
        double penalty = sameCategoryCount * CATEGORY_PENALTY;
        if (candidate.candidate().role() == PoiCandidateRole.BACKUP) {
            penalty += BACKUP_PENALTY;
        }
        return penalty;
    }

    private long countSelected(Map<String, RankedPoi> selected, java.util.function.Predicate<RankedPoi> predicate) {
        return selected.values().stream().filter(predicate).count();
    }

    private String categoryKey(RankedPoi candidate) {
        String category = candidate.candidate().category();
        return category == null || category.isBlank() ? "UNKNOWN" : category;
    }

    private double temperature(RouteGenerationContext context) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        if (profile == null) {
            return 0.24d;
        }
        return switch (profile) {
            case WALK_ONLY -> 0.18d;
            case WALK_SUBWAY, WALK_BUS, WALK_TRANSIT, BIKE_SUBWAY -> 0.34d;
            case WALK_TAXI -> 0.42d;
        };
    }

    public record RankedPoi(PoiCandidateDTO candidate, PoiLinearScoreDTO score) {
    }

    private record WeightedPoi(RankedPoi candidate, double weight) {
    }
}
