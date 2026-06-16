package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.support.RouteAreaPolicy;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class ResolveAreaStep implements RouteGenerationStep {

    private final RouteAreaPolicy routeAreaPolicy;

    public ResolveAreaStep(RouteAreaPolicy routeAreaPolicy) {
        this.routeAreaPolicy = routeAreaPolicy;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setArea(this.routeAreaPolicy.resolve(context.getGenerateParam()));
    }
}
