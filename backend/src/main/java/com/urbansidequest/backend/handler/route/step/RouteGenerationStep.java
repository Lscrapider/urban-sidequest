package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

/**
 * 推荐路径生成流水线的单个处理步骤。
 *
 * <p>所有 Step 通过 {@link RouteGenerationContext} 传递中间结果，避免在流水线中暴露具体实现细节。</p>
 */
public interface RouteGenerationStep {

    /**
     * 执行当前步骤，并把结果写回上下文。
     *
     * @param context 单次路线生成请求的上下文
     */
    void execute(RouteGenerationContext context);
}
