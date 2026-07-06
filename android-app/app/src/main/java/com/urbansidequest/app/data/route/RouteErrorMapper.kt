package com.urbansidequest.app.data.route

import com.urbansidequest.app.data.api.RouteApiException
import com.urbansidequest.app.data.auth.AuthErrorMessages

object RouteErrorMapper {

    const val ROUTE_LOAD_FAILED_MESSAGE = "路线加载失败，请稍后重试"
    const val ROUTE_GENERATION_FAILED_MESSAGE = "路线生成失败，请稍后重试"

    fun isAuthenticationError(throwable: Throwable): Boolean {
        return (throwable as? RouteApiException)?.isAuthenticationError == true ||
            throwable.message == AuthErrorMessages.AUTH_EXPIRED
    }
}
