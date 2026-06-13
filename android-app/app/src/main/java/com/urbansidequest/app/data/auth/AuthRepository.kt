package com.urbansidequest.app.data.auth

import com.urbansidequest.app.data.api.AuthApi

class AuthRepository(
    private val authApi: AuthApi,
    private val authSessionStore: AuthSessionStore
) {

    suspend fun login(phone: String, code: String) {
        val response = authApi.login(phone = phone, code = code)
        authSessionStore.save(
            AuthSession(
                tokenType = response.tokenType,
                accessToken = response.accessToken,
                expiresIn = response.expiresIn,
                userId = response.user.id,
                phone = response.user.phone,
                nickname = response.user.nickname
            )
        )
    }

    fun isLoggedIn(): Boolean {
        return authSessionStore.hasAccessToken()
    }
}
