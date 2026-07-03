package com.urbansidequest.backend.domain.param;

import jakarta.validation.constraints.NotBlank;

public class RouteActiveParam {

    @NotBlank
    private String routeCode;

    public String getRouteCode() {
        return this.routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }
}
