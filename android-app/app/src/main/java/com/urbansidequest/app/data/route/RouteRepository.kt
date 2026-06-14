package com.urbansidequest.app.data.route

import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.domain.model.RouteGeneration

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
}
