package com.urbansidequest.backend.service.route.step;

import cn.hutool.core.collection.CollUtil;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Order(10)
@Component
public class ValidateRouteRequestStep implements RouteGenerationStep {

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
    }
}
