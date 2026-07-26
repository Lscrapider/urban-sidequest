package com.urbansidequest.app.data.auth

import com.urbansidequest.app.data.api.AuthApi
import com.urbansidequest.app.data.api.AuthUserResponse

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

    suspend fun fetchCurrentUser(): AuthUserResponse {
        val authorizationHeader = authSessionStore.requireAuthorizationHeader()
        return authApi.fetchCurrentUser(authorizationHeader = authorizationHeader)
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, contentType: String): AuthUserResponse {
        val authorizationHeader = authSessionStore.requireAuthorizationHeader()
        return authApi.uploadAvatar(
            authorizationHeader = authorizationHeader,
            imageBytes = imageBytes,
            contentType = contentType
        )
    }

    fun isLoggedIn(): Boolean {
        return authSessionStore.hasAccessToken()
    }

    fun currentUserId(): String? {
        return authSessionStore.currentUserId()
    }

    fun clearSession() {
        authSessionStore.clear()
    }
}
