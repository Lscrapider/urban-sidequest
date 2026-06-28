package com.urbansidequest.backend.handler.route.pipeline;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.step.BuildCandidateRoutesStep;
import com.urbansidequest.backend.handler.route.step.CalibrateSelectedRouteSegmentsStep;
import com.urbansidequest.backend.handler.route.step.EnrichPoiDetailsStep;
import com.urbansidequest.backend.handler.route.step.FilterCalibratedRoutesStep;
import com.urbansidequest.backend.handler.route.step.LoadInterestTagsStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiCandidatesStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiSemanticMappingsStep;
import com.urbansidequest.backend.handler.route.step.LoadRouteWeatherStep;
import com.urbansidequest.backend.handler.route.step.LoadUserPreferenceProfileStep;
import com.urbansidequest.backend.handler.route.step.ResolveAreaStep;
import com.urbansidequest.backend.handler.route.step.SaveRoutePreferenceTrainingSamplesStep;
import com.urbansidequest.backend.handler.route.step.ScoreAndSelectRoutesStep;
import com.urbansidequest.backend.handler.route.step.SelectPoiPoolStep;
import com.urbansidequest.backend.handler.route.step.RouteGenerationStep;
import com.urbansidequest.backend.handler.route.step.ValidateRouteRequestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RouteGenerationPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteGenerationPipeline.class);

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadUserPreferenceProfileStep loadUserPreferenceProfileStep;

    private final LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep;

    private final LoadRouteWeatherStep loadRouteWeatherStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final SelectPoiPoolStep selectPoiPoolStep;

    private final BuildCandidateRoutesStep buildCandidateRoutesStep;

    private final SaveRoutePreferenceTrainingSamplesStep saveRoutePreferenceTrainingSamplesStep;

    private final ScoreAndSelectRoutesStep scoreAndSelectRoutesStep;

    private final CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep;

    private final FilterCalibratedRoutesStep filterCalibratedRoutesStep;

    public RouteGenerationPipeline(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadUserPreferenceProfileStep loadUserPreferenceProfileStep,
            LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep,
            LoadRouteWeatherStep loadRouteWeatherStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            SelectPoiPoolStep selectPoiPoolStep,
            BuildCandidateRoutesStep buildCandidateRoutesStep,
            SaveRoutePreferenceTrainingSamplesStep saveRoutePreferenceTrainingSamplesStep,
            ScoreAndSelectRoutesStep scoreAndSelectRoutesStep,
            CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep,
            FilterCalibratedRoutesStep filterCalibratedRoutesStep
    ) {
        this.validateRouteRequestStep = validateRouteRequestStep;
        this.resolveAreaStep = resolveAreaStep;
        this.loadInterestTagsStep = loadInterestTagsStep;
        this.loadUserPreferenceProfileStep = loadUserPreferenceProfileStep;
        this.loadPoiSemanticMappingsStep = loadPoiSemanticMappingsStep;
        this.loadRouteWeatherStep = loadRouteWeatherStep;
        this.loadPoiCandidatesStep = loadPoiCandidatesStep;
        this.enrichPoiDetailsStep = enrichPoiDetailsStep;
        this.selectPoiPoolStep = selectPoiPoolStep;
        this.buildCandidateRoutesStep = buildCandidateRoutesStep;
        this.saveRoutePreferenceTrainingSamplesStep = saveRoutePreferenceTrainingSamplesStep;
        this.scoreAndSelectRoutesStep = scoreAndSelectRoutesStep;
        this.calibrateSelectedRouteSegmentsStep = calibrateSelectedRouteSegmentsStep;
        this.filterCalibratedRoutesStep = filterCalibratedRoutesStep;
    }

    public void execute(RouteGenerationContext context) {
        long pipelineStartedAt = System.nanoTime();
        LOGGER.info(
                "路线生成 pipeline 开始，requestId={}，candidateSetId={}，city={}，goal={}，transport={}，durationMinutes={}",
                context.getRequestId(),
                context.getCandidateSetId(),
                context.getGenerateParam().getRouteCityName(),
                context.getGenerateParam().getRouteGoal(),
                context.getGenerateParam().getTransportProfile(),
                context.getGenerateParam().getDurationMinutes()
        );

        // 校验请求基础结构，避免后续 POI 查询和模型编排处理无效输入。
        this.executeStep("validateRouteRequest", context, this.validateRouteRequestStep);

        // 统一解析用户选择的路线区域，后续 POI 搜索都依赖 context.area。
        this.executeStep("resolveArea", context, this.resolveAreaStep);

        // 加载兴趣标签映射，用于 POI 搜索计划和候选点标签标记。
        this.executeStep("loadInterestTags", context, this.loadInterestTagsStep);

        // 加载用户问卷画像，用于后续 Linear Ranker 个性化 cross。
        this.executeStep("loadUserPreferenceProfile", context, this.loadUserPreferenceProfileStep);

        // 加载 POI 语义映射规则，供后续 Linear Ranker extractor 使用。
        this.executeStep("loadPoiSemanticMappings", context, this.loadPoiSemanticMappingsStep);

        // 加载天气环境原料；缺失时后续 Linear 特征默认不扣分。
        this.executeStep("loadRouteWeather", context, this.loadRouteWeatherStep);

        // 拉取较大的真实 POI 候选池，包含必去点、兴趣点、餐饮、休息和兜底点。
        this.executeStep("loadPoiCandidates", context, this.loadPoiCandidatesStep);

        // 批量增强 POI 详情和交通可达性；不能逐个 POI 调外部详情接口。
        this.executeStep("enrichPoiDetails", context, this.enrichPoiDetailsStep);

        // 将大 POI 池筛成适合 LLM 编排的小池；当前实现先占位透传/限量。
        this.executeStep("selectPoiPool", context, this.selectPoiPoolStep);

        // 调用 LLM 从 POI 池生成路线草案；真实交通距离和耗时不在这里计算。
        this.executeStep("buildCandidateRoutes", context, this.buildCandidateRoutesStep);

        // 后端复核模型草案，只执行必去点等不可展示硬约束，并最多选出 5 条路线。
        this.executeStep("scoreAndSelectRoutes", context, this.scoreAndSelectRoutesStep);

        // 对最终选中的路线逐段调用高德路线规划，补真实距离、耗时、polyline 和 steps。
        this.executeStep("calibrateSelectedRouteSegments", context, this.calibrateSelectedRouteSegmentsStep);

        // 校准完成后只挡不可展示路线，普通体验质量交给 Route X / judge。
        this.executeStep("filterCalibratedRoutes", context, this.filterCalibratedRoutesStep);

        // 保存最终返回路线的训练特征快照，确保 X 与后续 LLM judgment 看到的路线一致。
        this.executeStep("saveRoutePreferenceTrainingSamples", context, this.saveRoutePreferenceTrainingSamplesStep);

        LOGGER.info(
                "路线生成 pipeline 完成，requestId={}，candidateSetId={}，elapsedMs={}，poiCandidates={}，candidateRoutes={}，selectedRoutes={}，segmentCosts={}，warnings={}",
                context.getRequestId(),
                context.getCandidateSetId(),
                this.elapsedMillis(pipelineStartedAt),
                context.getPoiCandidates().size(),
                context.getCandidateRoutes().size(),
                context.getSelectedRoutes().size(),
                context.getSegmentCosts().size(),
                context.getWarnings().size()
        );
    }

    private void executeStep(String stepName, RouteGenerationContext context, RouteGenerationStep step) {
        long startedAt = System.nanoTime();
        try {
            step.execute(context);
        } finally {
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
