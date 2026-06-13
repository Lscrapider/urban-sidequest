package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.service.route.RouteAreaPolicy;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(20)
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
