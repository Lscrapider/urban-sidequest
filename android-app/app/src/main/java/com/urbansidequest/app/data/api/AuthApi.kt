package com.urbansidequest.app.data.api

import com.urbansidequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class AuthApi {

    suspend fun login(phone: String, code: String): AuthLoginResponse = withContext(Dispatchers.IO) {
        val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/auth/login")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true

        val requestBody = JSONObject()
            .put("phone", phone)
            .put("code", code)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        connection.outputStream.use { outputStream ->
            outputStream.write(requestBody)
        }

        val responseBody = readBody(connection)
        if (connection.responseCode !in HTTP_SUCCESS_RANGE) {
            throw IllegalStateException(parseErrorMessage(responseBody))
        }

        val json = JSONObject(responseBody)
        val userJson = json.getJSONObject("user")
        AuthLoginResponse(
            tokenType = json.getString("tokenType"),
            accessToken = json.getString("accessToken"),
            expiresIn = json.getLong("expiresIn"),
            user = AuthUserResponse(
                id = userJson.getString("id"),
                phone = userJson.getString("phone"),
                nickname = userJson.optString("nickname"),
                avatarUrl = userJson.optString("avatarUrl")
            )
        )
    }

    private fun readBody(connection: HttpURLConnection): String {
        val inputStream = if (connection.responseCode in HTTP_SUCCESS_RANGE) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        if (inputStream == null) {
            return ""
        }
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun parseErrorMessage(responseBody: String): String {
        if (responseBody.isBlank()) {
            return "登录失败，请稍后重试"
        }
        return runCatching {
            val json = JSONObject(responseBody)
            json.optString("detail")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { "登录失败，请稍后重试" }
        }.getOrDefault("登录失败，请稍后重试")
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 10_000
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}

data class AuthLoginResponse(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Long,
    val user: AuthUserResponse
)

data class AuthUserResponse(
    val id: String,
    val phone: String,
    val nickname: String,
    val avatarUrl: String
)
