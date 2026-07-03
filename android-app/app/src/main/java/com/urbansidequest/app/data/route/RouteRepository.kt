package com.urbansidequest.app.data.route

import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteInteractionState

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

    suspend fun fetchRouteInteractions(): Map<String, RouteInteractionState> {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.fetchRouteInteractions(authorizationHeader = authorizationHeader)
            .associate { interaction ->
                "${interaction.candidateSetId}:${interaction.routeCode}" to interaction.state
            }
    }

    suspend fun saveRouteInteraction(
        requestId: String,
        routeCode: String,
        interaction: RouteInteractionState
    ): RouteInteractionState {
        val authorizationHeader = authSessionStore.getAuthorizationHeader()
            ?: throw IllegalStateException("登录状态已失效，请重新登录")
        return routeApi.saveRouteInteraction(
            requestId = requestId,
            routeCode = routeCode,
            interaction = interaction,
            authorizationHeader = authorizationHeader
        ).state
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
