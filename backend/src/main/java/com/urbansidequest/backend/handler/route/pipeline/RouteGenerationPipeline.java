package com.urbansidequest.backend.handler.route.pipeline;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.step.BuildCandidateRoutesStep;
import com.urbansidequest.backend.handler.route.step.CalibrateSelectedRouteSegmentsStep;
import com.urbansidequest.backend.handler.route.step.EnrichPoiDetailsStep;
import com.urbansidequest.backend.handler.route.step.LoadInterestTagsStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiCandidatesStep;
import com.urbansidequest.backend.handler.route.step.ResolveAreaStep;
import com.urbansidequest.backend.handler.route.step.ScoreAndSelectRoutesStep;
import com.urbansidequest.backend.handler.route.step.SelectPoiPoolStep;
import com.urbansidequest.backend.handler.route.step.ValidateRouteRequestStep;
import org.springframework.stereotype.Component;

@Component
public class RouteGenerationPipeline {

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final SelectPoiPoolStep selectPoiPoolStep;

    private final BuildCandidateRoutesStep buildCandidateRoutesStep;

    private final ScoreAndSelectRoutesStep scoreAndSelectRoutesStep;

    private final CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep;

    public RouteGenerationPipeline(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            SelectPoiPoolStep selectPoiPoolStep,
            BuildCandidateRoutesStep buildCandidateRoutesStep,
            ScoreAndSelectRoutesStep scoreAndSelectRoutesStep,
            CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep
    ) {
        this.validateRouteRequestStep = validateRouteRequestStep;
        this.resolveAreaStep = resolveAreaStep;
        this.loadInterestTagsStep = loadInterestTagsStep;
        this.loadPoiCandidatesStep = loadPoiCandidatesStep;
        this.enrichPoiDetailsStep = enrichPoiDetailsStep;
        this.selectPoiPoolStep = selectPoiPoolStep;
        this.buildCandidateRoutesStep = buildCandidateRoutesStep;
        this.scoreAndSelectRoutesStep = scoreAndSelectRoutesStep;
        this.calibrateSelectedRouteSegmentsStep = calibrateSelectedRouteSegmentsStep;
    }

    public void execute(RouteGenerationContext context) {
        // 校验请求基础结构，避免后续 POI 查询和模型编排处理无效输入。
        this.validateRouteRequestStep.execute(context);

        // 统一解析用户选择的路线区域，后续 POI 搜索都依赖 context.area。
        this.resolveAreaStep.execute(context);

        // 加载兴趣标签映射，用于 POI 搜索计划和候选点标签标记。
        this.loadInterestTagsStep.execute(context);

        // 拉取较大的真实 POI 候选池，包含必去点、兴趣点、餐饮、休息和兜底点。
        this.loadPoiCandidatesStep.execute(context);

        // 批量增强 POI 详情和交通可达性；不能逐个 POI 调外部详情接口。
        this.enrichPoiDetailsStep.execute(context);

        // 将大 POI 池筛成适合 LLM 编排的小池；当前实现先占位透传/限量。
        this.selectPoiPoolStep.execute(context);

        // 调用 LLM 从 POI 池生成路线草案；真实交通距离和耗时不在这里计算。
        this.buildCandidateRoutesStep.execute(context);

        // 后端复核模型草案，执行必去点、时长等约束，并最多选出 3 条路线。
        this.scoreAndSelectRoutesStep.execute(context);

        // 对最终选中的路线逐段调用高德路线规划，补真实距离、耗时、polyline 和 steps。
        this.calibrateSelectedRouteSegmentsStep.execute(context);
    }
}
