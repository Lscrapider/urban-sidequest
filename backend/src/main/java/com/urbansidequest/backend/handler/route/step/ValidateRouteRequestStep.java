package com.urbansidequest.backend.handler.route.step;

import cn.hutool.core.collection.CollUtil;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
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
