package com.urbansidequest.app.data.auth

import android.content.Context

class AuthSessionStore(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        sharedPreferences.edit()
            .putString(KEY_TOKEN_TYPE, session.tokenType)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putLong(KEY_EXPIRES_IN, session.expiresIn)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putString(KEY_NICKNAME, session.nickname)
            .apply()
    }

    fun hasAccessToken(): Boolean {
        return !sharedPreferences.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    fun getAuthorizationHeader(): String? {
        val tokenType = sharedPreferences.getString(KEY_TOKEN_TYPE, null)
        val accessToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        if (tokenType.isNullOrBlank() || accessToken.isNullOrBlank()) {
            return null
        }
        return "$tokenType $accessToken"
    }

    private companion object {
        private const val PREFERENCES_NAME = "auth_session"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_IN = "expires_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_NICKNAME = "nickname"
    }
}

data class AuthSession(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Long,
    val userId: String,
    val phone: String,
    val nickname: String
)
