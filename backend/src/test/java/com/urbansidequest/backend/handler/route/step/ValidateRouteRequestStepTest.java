package com.urbansidequest.backend.handler.route.step;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.InterestTagCatalogManage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ValidateRouteRequestStepTest {

    @Test
    void rejectsMealWindowThatRouteTimeCannotCover() {
        InterestTagCatalogManage interestTagCatalogManage = mock(InterestTagCatalogManage.class);
        ValidateRouteRequestStep step = new ValidateRouteRequestStep(interestTagCatalogManage);
        RouteGenerateParam param = baseParam(LocalDateTime.of(2026, 6, 22, 9, 0), 180);
        param.setMealWindows(List.of(MealWindow.DINNER));

        assertThatThrownBy(() -> step.execute(context(param)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("mealWindows 包含当前路线时间不可安排的饭点");
    }

    @Test
    void rejectsFoodInterestWhenNoMealWindowSelected() {
        InterestTagCatalogManage interestTagCatalogManage = mock(InterestTagCatalogManage.class);
        when(interestTagCatalogManage.findEnabled()).thenReturn(List.of(
                tag("FOOD", null, false, "FOOD"),
                tag("FOOD_SICHUAN", "FOOD", true, "FOOD")
        ));
        ValidateRouteRequestStep step = new ValidateRouteRequestStep(interestTagCatalogManage);
        RouteGenerateParam param = baseParam(LocalDateTime.of(2026, 6, 22, 10, 0), 360);
        param.setInterestTags(List.of("FOOD_SICHUAN"));

        assertThatThrownBy(() -> step.execute(context(param)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("未选择午餐或晚餐时不能选择 FOOD 餐饮偏好");
    }

    private static RouteGenerateParam baseParam(LocalDateTime departureTime, int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setRadiusMeters(5000);
        param.setDepartureTime(departureTime);
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(TransportProfile.WALK_TAXI);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static RouteGenerationContext context(RouteGenerateParam param) {
        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), UUID.randomUUID(), param);
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                5000,
                List.of()
        ));
        return context;
    }

    private static InterestTagCatalogPO tag(String tagCode, String parentTagCode, boolean selectable, String categoryGroup) {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode(tagCode);
        tag.setDisplayName(tagCode);
        tag.setParentTagCode(parentTagCode);
        tag.setSelectable(selectable);
        tag.setCategoryGroup(categoryGroup);
        return tag;
    }

    private static GeoPointParam center() {
        GeoPointParam center = new GeoPointParam();
        center.setLongitudeGcj02(new BigDecimal("121.4737"));
        center.setLatitudeGcj02(new BigDecimal("31.2304"));
        return center;
    }
}
