package com.urbansidequest.backend.handler.route.step;

import cn.hutool.core.collection.CollUtil;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.support.MealWindowSupport;
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

    private static final int MAX_GLOBAL_INTEREST_BUCKET_COUNT = 5;

    private static final int MAX_FOOD_INTEREST_TAG_COUNT = 3;

    private static final String FOOD_ROOT_TAG = "FOOD";

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
        this.validateMealWindows(context);
        this.validateInterestTags(context);
    }

    private void validateMealWindows(RouteGenerationContext context) {
        List<MealWindow> mealWindows = context.getGenerateParam().getMealWindows();
        if (mealWindows == null || mealWindows.isEmpty()) {
            return;
        }
        Set<MealWindow> uniqueWindows = new LinkedHashSet<>();
        List<String> duplicateWindows = new ArrayList<>();
        for (MealWindow mealWindow : mealWindows) {
            if (mealWindow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mealWindows 不能包含空饭点");
            }
            if (!uniqueWindows.add(mealWindow)) {
                duplicateWindows.add(mealWindow.name());
            }
        }
        if (!duplicateWindows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mealWindows 不能重复：" + String.join(",", duplicateWindows));
        }

        Set<MealWindow> feasibleWindows = new LinkedHashSet<>(MealWindowSupport.feasibleMealWindows(context.getGenerateParam()));
        List<String> infeasibleWindows = uniqueWindows.stream()
                .filter(mealWindow -> !feasibleWindows.contains(mealWindow))
                .map(MealWindow::name)
                .toList();
        if (!infeasibleWindows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "mealWindows 包含当前路线时间不可安排的饭点：" + String.join(",", infeasibleWindows)
            );
        }
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

        List<InterestTagCatalogPO> enabledTags = this.interestTagCatalogManage.findEnabled();
        Map<String, InterestTagCatalogPO> enabledTagByCode = new LinkedHashMap<>();
        for (InterestTagCatalogPO tag : enabledTags) {
            enabledTagByCode.put(tag.getTagCode(), tag);
        }
        Map<String, InterestTagCatalogPO> tagByCode = new LinkedHashMap<>();
        for (String tagCode : requestedTags) {
            InterestTagCatalogPO tag = enabledTagByCode.get(tagCode);
            if (tag != null && Boolean.TRUE.equals(tag.getSelectable())) {
                tagByCode.put(tagCode, tag);
            }
        }
        List<String> invalidTags = requestedTags.stream()
                .filter(tag -> !tagByCode.containsKey(tag))
                .toList();
        if (!invalidTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interestTags 包含未知或不可选标签：" + String.join(",", invalidTags));
        }

        this.validateFoodInterestRequiresMealWindow(context, tagByCode, enabledTagByCode);
        this.validateGlobalInterestBucketCount(tagByCode, enabledTagByCode);
        this.validateFoodInterestTagCount(tagByCode, enabledTagByCode);
        this.validateFoodParentChildExclusive(tagByCode, enabledTagByCode);
        this.validateMaxSiblingSelected(tagByCode);
    }

    private void validateFoodInterestRequiresMealWindow(
            RouteGenerationContext context,
            Map<String, InterestTagCatalogPO> tagByCode,
            Map<String, InterestTagCatalogPO> enabledTagByCode
    ) {
        if (CollUtil.isNotEmpty(context.getGenerateParam().getMealWindows())) {
            return;
        }
        boolean hasFoodInterest = tagByCode.values().stream()
                .anyMatch(tag -> this.isFoodTag(tag, enabledTagByCode));
        if (hasFoodInterest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未选择午餐或晚餐时不能选择 FOOD 餐饮偏好");
        }
    }

    private void validateGlobalInterestBucketCount(
            Map<String, InterestTagCatalogPO> tagByCode,
            Map<String, InterestTagCatalogPO> enabledTagByCode
    ) {
        Set<String> interestBuckets = new LinkedHashSet<>();
        for (InterestTagCatalogPO tag : tagByCode.values()) {
            interestBuckets.add(this.globalInterestBucket(tag, enabledTagByCode));
        }
        if (interestBuckets.size() > MAX_GLOBAL_INTEREST_BUCKET_COUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "interestTags 最多选择 " + MAX_GLOBAL_INTEREST_BUCKET_COUNT + " 个兴趣大类"
            );
        }
    }

    private void validateFoodInterestTagCount(
            Map<String, InterestTagCatalogPO> tagByCode,
            Map<String, InterestTagCatalogPO> enabledTagByCode
    ) {
        long foodTagCount = tagByCode.values().stream()
                .filter(tag -> this.isFoodTag(tag, enabledTagByCode))
                .count();
        if (foodTagCount > MAX_FOOD_INTEREST_TAG_COUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FOOD 餐厅偏好最多选择 " + MAX_FOOD_INTEREST_TAG_COUNT + " 个"
            );
        }
    }

    private void validateFoodParentChildExclusive(
            Map<String, InterestTagCatalogPO> tagByCode,
            Map<String, InterestTagCatalogPO> enabledTagByCode
    ) {
        for (InterestTagCatalogPO tag : tagByCode.values()) {
            if (!this.isFoodTag(tag, enabledTagByCode)) {
                continue;
            }
            String ancestorCode = tag.getParentTagCode();
            while (ancestorCode != null && !ancestorCode.isBlank()) {
                if (tagByCode.containsKey(ancestorCode)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "FOOD 同一分支父级和子级不能同时选择：" + ancestorCode + "," + tag.getTagCode()
                    );
                }
                InterestTagCatalogPO ancestor = enabledTagByCode.get(ancestorCode);
                ancestorCode = ancestor == null ? null : ancestor.getParentTagCode();
            }
        }
    }

    private String globalInterestBucket(InterestTagCatalogPO tag, Map<String, InterestTagCatalogPO> enabledTagByCode) {
        if (this.isFoodTag(tag, enabledTagByCode)) {
            return FOOD_ROOT_TAG;
        }
        return tag.getTagCode();
    }

    private boolean isFoodTag(InterestTagCatalogPO tag, Map<String, InterestTagCatalogPO> enabledTagByCode) {
        String tagCode = tag == null ? null : tag.getTagCode();
        while (tagCode != null && !tagCode.isBlank()) {
            if (FOOD_ROOT_TAG.equals(tagCode)) {
                return true;
            }
            InterestTagCatalogPO current = enabledTagByCode.get(tagCode);
            tagCode = current == null ? null : current.getParentTagCode();
        }
        return false;
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
