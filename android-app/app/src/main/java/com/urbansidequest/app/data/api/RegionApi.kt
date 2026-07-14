package com.urbansidequest.app.data.api

import com.urbansidequest.app.BuildConfig
import com.urbansidequest.app.data.auth.AuthErrorMessages
import com.urbansidequest.app.domain.model.DiscoverRegion
import com.urbansidequest.app.domain.model.GeoPoint
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RegionApi {

    suspend fun fetchRegions(
        parentAdcode: String?,
        authorizationHeader: String
    ): List<DiscoverRegion> = withContext(Dispatchers.IO) {
        try {
            val endpoint = buildEndpoint(parentAdcode)
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)

            val responseBody = readBody(connection)
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw RouteApiException(
                    responseCode = responseCode,
                    message = parseErrorMessage(responseCode, responseBody)
                )
            }
            runCatching {
                val json = JSONArray(responseBody)
                List(json.length()) { index -> parseRegion(json.getJSONObject(index)) }
            }.getOrElse { throwable ->
                throw IllegalStateException("地区数据响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接地区服务：${exception.message}", exception)
        }
    }

    private fun buildEndpoint(parentAdcode: String?): URL {
        val baseUrl = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/regions"
        if (parentAdcode.isNullOrBlank()) {
            return URL(baseUrl)
        }
        val encodedParentAdcode = URLEncoder.encode(parentAdcode, StandardCharsets.UTF_8)
        return URL("$baseUrl?parentAdcode=$encodedParentAdcode")
    }

    private fun parseRegion(json: JSONObject): DiscoverRegion {
        val center = json.getJSONObject("center")
        return DiscoverRegion(
            adcode = json.getString("adcode"),
            parentAdcode = json.optString("parentAdcode").ifBlank { null },
            name = json.getString("name"),
            level = json.optString("level").ifBlank { "DISTRICT" },
            selectable = json.optBoolean("selectable", true),
            hasChildren = json.optBoolean("hasChildren", false),
            routeCityName = json.optString("routeCityName").ifBlank { json.getString("name") },
            routeCityAdcode = json.optString("routeCityAdcode").ifBlank { json.getString("adcode") },
            center = GeoPoint(
                longitudeGcj02 = center.getDouble("longitudeGcj02"),
                latitudeGcj02 = center.getDouble("latitudeGcj02")
            )
        )
    }

    private fun readBody(connection: HttpURLConnection): String {
        val inputStream = if (connection.responseCode in HTTP_SUCCESS_RANGE) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun parseErrorMessage(responseCode: Int, responseBody: String): String {
        if (responseCode == HTTP_UNAUTHORIZED || responseCode == HTTP_FORBIDDEN) {
            return AuthErrorMessages.AUTH_EXPIRED
        }
        return runCatching {
            JSONObject(responseBody).optString("detail")
        }.getOrDefault("").ifBlank { "地区数据加载失败，请稍后重试" }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 20_000
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}
