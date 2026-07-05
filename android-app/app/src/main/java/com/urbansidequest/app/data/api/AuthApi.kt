package com.urbansidequest.app.data.api

import com.urbansidequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
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
        AuthLoginResponse(
            tokenType = json.getString("tokenType"),
            accessToken = json.getString("accessToken"),
            expiresIn = json.getLong("expiresIn"),
            user = parseAuthUser(json.getJSONObject("user"))
        )
    }

    suspend fun fetchCurrentUser(authorizationHeader: String): AuthUserResponse = withContext(Dispatchers.IO) {
        val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/auth/me")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", authorizationHeader)

        val responseBody = readBody(connection)
        if (connection.responseCode !in HTTP_SUCCESS_RANGE) {
            throw IllegalStateException(parseErrorMessage(responseBody))
        }
        parseAuthUser(JSONObject(responseBody))
    }

    suspend fun uploadAvatar(
        authorizationHeader: String,
        imageBytes: ByteArray,
        contentType: String
    ): AuthUserResponse = withContext(Dispatchers.IO) {
        val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/auth/me/avatar")
        val boundary = "UrbanSidequestBoundary${System.currentTimeMillis()}"
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", authorizationHeader)
        connection.doOutput = true

        connection.outputStream.use { outputStream ->
            writeMultipartFile(outputStream, boundary, "avatar", "avatar.jpg", contentType, imageBytes)
            outputStream.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }

        val responseBody = readBody(connection)
        if (connection.responseCode !in HTTP_SUCCESS_RANGE) {
            throw IllegalStateException(parseErrorMessage(responseBody, "头像更新失败，请稍后重试"))
        }
        parseAuthUser(JSONObject(responseBody))
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

    private fun parseErrorMessage(responseBody: String, fallback: String = "登录失败，请稍后重试"): String {
        if (responseBody.isBlank()) {
            return fallback
        }
        return runCatching {
            val json = JSONObject(responseBody)
            json.optString("detail")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    private fun writeMultipartFile(
        outputStream: OutputStream,
        boundary: String,
        name: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ) {
        outputStream.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        outputStream.write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n".toByteArray(StandardCharsets.UTF_8)
        )
        outputStream.write("Content-Type: $contentType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        outputStream.write(bytes)
        outputStream.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun parseAuthUser(json: JSONObject): AuthUserResponse {
        return AuthUserResponse(
            id = json.getString("id"),
            phone = json.getString("phone"),
            nickname = json.optString("nickname"),
            avatarUrl = json.optString("avatarUrl"),
            completedRouteCount = json.optInt("completedRouteCount", 0),
            travelDistanceMeters = json.optLong("travelDistanceMeters", 0L)
        )
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
    val avatarUrl: String,
    val completedRouteCount: Int = 0,
    val travelDistanceMeters: Long = 0L
)
