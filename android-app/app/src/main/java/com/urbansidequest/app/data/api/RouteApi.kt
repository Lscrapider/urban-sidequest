package com.urbansidequest.app.data.api

import com.urbansidequest.app.BuildConfig
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteArea
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class RouteApi {

    suspend fun generateRoute(
        request: RouteGenerateRequest,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/requests")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true

            val requestBody = request.toJson().toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody)
            }

            val responseBody = readBody(connection)
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw RouteApiException(
                    responseCode = responseCode,
                    message = parseErrorMessage(responseCode, responseBody)
                )
            }
            runCatching {
                parseRouteGeneration(JSONObject(responseBody))
            }.getOrElse { throwable ->
                throw IllegalStateException("路线响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    private fun parseRouteGeneration(json: JSONObject): RouteGeneration {
        return RouteGeneration(
            requestId = json.getString("requestId"),
            status = json.getString("status"),
            area = parseRouteArea(json.getJSONObject("area")),
            routes = json.getJSONArray("routes").mapObjects(::parseGeneratedRoute),
            warnings = json.optJSONArray("warnings").orEmptyStringList()
        )
    }

    private fun parseRouteArea(json: JSONObject): RouteArea {
        return RouteArea(
            areaMode = json.getString("areaMode"),
            areaLabel = json.getString("areaLabel"),
            center = parseGeoPoint(json.getJSONObject("center")),
            radiusMeters = json.getInt("radiusMeters"),
            description = json.optString("description").ifBlank { null }
        )
    }

    private fun parseGeneratedRoute(json: JSONObject): GeneratedRoute {
        return GeneratedRoute(
            routeCode = json.getString("routeCode"),
            title = json.getString("title"),
            summary = json.getString("summary"),
            totalDurationMinutes = json.getInt("totalDurationMinutes"),
            totalDistanceMeters = json.getInt("totalDistanceMeters"),
            budgetCent = json.optNullableInt("budgetCent"),
            riskLevel = json.getString("riskLevel"),
            explanation = json.getString("explanation"),
            stops = json.getJSONArray("stops").mapObjects(::parseRouteStop)
        )
    }

    private fun parseRouteStop(json: JSONObject): RouteStop {
        return RouteStop(
            id = json.getString("stopId"),
            order = json.getInt("order"),
            name = json.getString("name"),
            location = parseGeoPoint(json.getJSONObject("location")),
            reason = json.optString("reason").ifBlank { null },
            category = json.optString("category").ifBlank { null },
            stayMinutes = json.optNullableInt("stayMinutes"),
            transportToNext = json.optString("transportToNext").ifBlank { null },
            distanceToNextMeters = json.optNullableInt("distanceToNextMeters"),
            durationToNextMinutes = json.optNullableInt("durationToNextMinutes"),
            riskNote = json.optString("riskNote").ifBlank { null }
        )
    }

    private fun parseGeoPoint(json: JSONObject): GeoPoint {
        return GeoPoint(
            longitudeGcj02 = json.getDouble("longitudeGcj02"),
            latitudeGcj02 = json.getDouble("latitudeGcj02")
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
            return "登录状态已失效，请重新登录"
        }
        if (responseBody.isBlank()) {
            return "路线生成失败，请稍后重试"
        }
        return runCatching {
            val json = JSONObject(responseBody)
            json.optString("detail")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { "路线生成失败，请稍后重试" }
        }.getOrDefault("路线生成失败，请稍后重试")
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 20_000
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}

class RouteApiException(
    val responseCode: Int,
    message: String
) : IllegalStateException(message) {

    val isAuthenticationError: Boolean = responseCode == HTTP_UNAUTHORIZED || responseCode == HTTP_FORBIDDEN

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}

data class RouteGenerateRequest(
    val areaMode: String,
    val areaLabel: String,
    val center: GeoPoint,
    val areaPolygonGcj02: List<GeoPoint>,
    val departureTime: String,
    val durationMinutes: Int,
    val transportProfile: String,
    val routeGoal: String,
    val interestTags: List<String>,
    val mustVisitPoints: List<MustVisitPointRequest>
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("areaMode", areaMode)
            .put("areaLabel", areaLabel)
            .put("center", center.toJson())
            .put("areaPolygonGcj02", areaPolygonGcj02.toGeoPointArray())
            .put("departureTime", departureTime)
            .put("durationMinutes", durationMinutes)
            .put("transportProfile", transportProfile)
            .put("routeGoal", routeGoal)
            .put("interestTags", JSONArray(interestTags))
            .put("mustVisitPoints", JSONArray(mustVisitPoints.map { it.toJson() }))
    }
}

data class MustVisitPointRequest(
    val name: String,
    val amapPoiId: String?,
    val location: GeoPoint,
    val priority: String
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("amapPoiId", amapPoiId ?: JSONObject.NULL)
            .put("location", location.toJson())
            .put("priority", priority)
    }
}

private fun GeoPoint.toJson(): JSONObject {
    return JSONObject()
        .put("longitudeGcj02", longitudeGcj02)
        .put("latitudeGcj02", latitudeGcj02)
}

private fun List<GeoPoint>.toGeoPointArray(): JSONArray {
    return JSONArray(this.map { it.toJson() })
}

private fun JSONArray?.orEmptyStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }
    return List(length()) { index -> getString(index) }
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    return List(length()) { index -> transform(getJSONObject(index)) }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (isNull(name)) {
        null
    } else {
        optInt(name)
    }
}
