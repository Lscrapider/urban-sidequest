package com.urbansidequest.backend.handler.route.step;

import cn.hutool.core.collection.CollUtil;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.InterestTagCatalogManage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 校验路线生成请求的基础参数。
 *
 * <p>这个步骤只处理后续流程无法自行恢复的输入错误，例如自动范围缺少中心点、
 * 手动框选缺少多边形；其它业务质量问题会留给后续 Step 通过 warning 表达。</p>
 */
@Component
public class ValidateRouteRequestStep implements RouteGenerationStep {

    private final InterestTagCatalogManage interestTagCatalogManage;

    public ValidateRouteRequestStep(InterestTagCatalogManage interestTagCatalogManage) {
        this.interestTagCatalogManage = interestTagCatalogManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (AreaMode.AUTO_RADIUS == context.getGenerateParam().getAreaMode()
                && context.getGenerateParam().getCenter() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自动范围需要中心点");
        }
        if (AreaMode.MANUAL_POLYGON == context.getGenerateParam().getAreaMode()
                && CollUtil.isEmpty(context.getGenerateParam().getAreaPolygonGcj02())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手动框选范围不能为空");
        }
        if (RouteGoal.LOW_BUDGET == context.getGenerateParam().getRouteGoal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LOW_BUDGET 已退出路线目标，请使用 budgetLevel 表达预算偏好");
        }
        this.validateInterestTags(context);
    }

    private void validateInterestTags(RouteGenerationContext context) {
        List<String> requestedTags = context.getGenerateParam().getInterestTags();
        if (requestedTags == null || requestedTags.isEmpty()) {
            return;
        }
        Set<String> uniqueTags = new LinkedHashSet<>();
        List<String> duplicateTags = new ArrayList<>();
        for (String tag : requestedTags) {
            if (tag == null || tag.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interestTags 不能包含空标签");
            }
            if (!uniqueTags.add(tag)) {
                duplicateTags.add(tag);
            }
        }
        if (!duplicateTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interestTags 不能重复：" + String.join(",", duplicateTags));
        }

        List<InterestTagCatalogPO> enabledTags = this.interestTagCatalogManage.findEnabledByTagCodes(requestedTags);
        Map<String, InterestTagCatalogPO> tagByCode = new LinkedHashMap<>();
        for (InterestTagCatalogPO tag : enabledTags) {
            tagByCode.put(tag.getTagCode(), tag);
        }
        List<String> invalidTags = requestedTags.stream()
                .filter(tag -> !tagByCode.containsKey(tag))
                .toList();
        if (!invalidTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interestTags 包含未知或不可选标签：" + String.join(",", invalidTags));
        }

        this.validateFoodParentChildExclusive(tagByCode);
        this.validateMaxSiblingSelected(tagByCode);
    }

    private void validateFoodParentChildExclusive(Map<String, InterestTagCatalogPO> tagByCode) {
        for (InterestTagCatalogPO tag : tagByCode.values()) {
            String parent = tag.getParentTagCode();
            if (parent == null || parent.isBlank() || !parent.startsWith("FOOD_")) {
                continue;
            }
            if (tagByCode.containsKey(parent)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "FOOD 同一分支二级和三级不能同时选择：" + parent + "," + tag.getTagCode()
                );
            }
        }
    }

    private void validateMaxSiblingSelected(Map<String, InterestTagCatalogPO> tagByCode) {
        Map<String, List<InterestTagCatalogPO>> tagsByParent = new LinkedHashMap<>();
        for (InterestTagCatalogPO tag : tagByCode.values()) {
            String parent = tag.getParentTagCode();
            if (parent == null || parent.isBlank()) {
                continue;
            }
            tagsByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(tag);
        }
        for (Map.Entry<String, List<InterestTagCatalogPO>> entry : tagsByParent.entrySet()) {
            int maxSiblingSelected = entry.getValue().stream()
                    .map(InterestTagCatalogPO::getMaxSiblingSelected)
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(0);
            if (maxSiblingSelected > 0 && entry.getValue().size() > maxSiblingSelected) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "同一标签分支选择数量超过限制：" + entry.getKey()
                );
            }
        }
    }
}
