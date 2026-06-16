package com.urbansidequest.backend.handler.route.pipeline;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.step.BuildCandidateRoutesStep;
import com.urbansidequest.backend.handler.route.step.BuildSegmentCostGraphStep;
import com.urbansidequest.backend.handler.route.step.EnrichPoiDetailsStep;
import com.urbansidequest.backend.handler.route.step.LoadInterestTagsStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiCandidatesStep;
import com.urbansidequest.backend.handler.route.step.ResolveAreaStep;
import com.urbansidequest.backend.handler.route.step.ScoreAndSelectRoutesStep;
import com.urbansidequest.backend.handler.route.step.ValidateRouteRequestStep;
import org.springframework.stereotype.Component;

@Component
public class RouteGenerationPipeline {

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final BuildSegmentCostGraphStep buildSegmentCostGraphStep;

    private final BuildCandidateRoutesStep buildCandidateRoutesStep;

    private final ScoreAndSelectRoutesStep scoreAndSelectRoutesStep;

    public RouteGenerationPipeline(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            BuildSegmentCostGraphStep buildSegmentCostGraphStep,
            BuildCandidateRoutesStep buildCandidateRoutesStep,
            ScoreAndSelectRoutesStep scoreAndSelectRoutesStep
    ) {
        this.validateRouteRequestStep = validateRouteRequestStep;
        this.resolveAreaStep = resolveAreaStep;
        this.loadInterestTagsStep = loadInterestTagsStep;
        this.loadPoiCandidatesStep = loadPoiCandidatesStep;
        this.enrichPoiDetailsStep = enrichPoiDetailsStep;
        this.buildSegmentCostGraphStep = buildSegmentCostGraphStep;
        this.buildCandidateRoutesStep = buildCandidateRoutesStep;
        this.scoreAndSelectRoutesStep = scoreAndSelectRoutesStep;
    }

    public void execute(RouteGenerationContext context) {
        this.validateRouteRequestStep.execute(context);
        this.resolveAreaStep.execute(context);
        this.loadInterestTagsStep.execute(context);
        this.loadPoiCandidatesStep.execute(context);
        this.enrichPoiDetailsStep.execute(context);
        this.buildSegmentCostGraphStep.execute(context);
        this.buildCandidateRoutesStep.execute(context);
        this.scoreAndSelectRoutesStep.execute(context);
    }
}
