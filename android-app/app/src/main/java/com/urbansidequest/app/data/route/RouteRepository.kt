package com.urbansidequest.app.data.route

import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteHistoryGroup

class RouteRepository(
    private val routeApi: RouteApi,
    private val authSessionStore: AuthSessionStore
) {

    suspend fun generateRoute(request: RouteGenerateRequest): RouteGeneration {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.generateRoute(
            request = request,
            authorizationHeader = authorizationHeader
        )
    }

    suspend fun fetchRouteHistory(): List<RouteHistoryGroup> {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.fetchRouteHistory(authorizationHeader = authorizationHeader)
    }

    suspend fun fetchRouteHistoryDetail(requestId: String): RouteGeneration {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.fetchRouteHistoryDetail(
            requestId = requestId,
            authorizationHeader = authorizationHeader
        )
    }

    suspend fun fetchActiveRoute(): RouteGeneration? {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.fetchActiveRoute(authorizationHeader = authorizationHeader)
    }

    suspend fun activateRoute(requestId: String, routeCode: String): RouteGeneration {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.activateRoute(
            requestId = requestId,
            routeCode = routeCode,
            authorizationHeader = authorizationHeader
        )
    }

    suspend fun completeActiveRoute(requestId: String, routeCode: String): RouteGeneration {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.completeActiveRoute(
            requestId = requestId,
            routeCode = routeCode,
            authorizationHeader = authorizationHeader
        )
    }
}
