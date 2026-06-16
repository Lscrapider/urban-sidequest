package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

public interface RouteGenerationStep {

    void execute(RouteGenerationContext context);
}
