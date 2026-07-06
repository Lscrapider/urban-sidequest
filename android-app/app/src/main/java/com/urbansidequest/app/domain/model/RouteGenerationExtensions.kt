package com.urbansidequest.app.domain.model

internal fun RouteGeneration.onlyRoute(routeCode: String?): RouteGeneration {
    if (routeCode.isNullOrBlank()) {
        return this
    }
    val route = routes.firstOrNull { candidate -> candidate.routeCode == routeCode }
        ?: return this
    return copy(
        routes = listOf(route),
        activeRouteCode = routeCode
    )
}
