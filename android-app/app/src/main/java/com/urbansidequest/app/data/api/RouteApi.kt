package com.urbansidequest.app.data.api

import com.urbansidequest.app.BuildConfig
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteArea
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteHistoryRouteSummary
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteShare
import com.urbansidequest.app.domain.model.RouteStep
import com.urbansidequest.app.domain.model.RouteStop
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class RouteApi {

    suspend fun fetchRouteHistory(authorizationHeader: String): List<RouteHistoryGroup> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history")
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
                JSONArray(responseBody).mapObjects(::parseRouteHistoryGroup)
            }.getOrElse { throwable ->
                throw IllegalStateException("路线历史响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun fetchRouteShares(authorizationHeader: String): List<RouteShare> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/shares")
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
                JSONArray(responseBody).mapObjects(::parseRouteShare)
            }.getOrElse { throwable ->
                throw IllegalStateException("路线分享响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线分享服务：${exception.message}", exception)
        }
    }

    suspend fun fetchSharedRoute(
        shareId: String,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/shares/$shareId/route")
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
                parseRouteGeneration(JSONObject(responseBody))
            }.getOrElse { throwable ->
                throw IllegalStateException("分享路线详情解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线分享服务：${exception.message}", exception)
        }
    }

    suspend fun fetchRouteSharePreviewMap(
        requestId: String,
        routeCode: String,
        authorizationHeader: String
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL(
                "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId/routes/$routeCode/share-preview-map"
            )
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "image/png")
            connection.setRequestProperty("Authorization", authorizationHeader)

            val responseCode = connection.responseCode
            val responseBytes = readBytes(connection)
            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw RouteApiException(
                    responseCode = responseCode,
                    message = parseErrorMessage(responseCode, responseBytes.toString(StandardCharsets.UTF_8))
                )
            }
            responseBytes
        } catch (exception: IOException) {
            throw IllegalStateException("无法生成分享地图：${exception.message}", exception)
        }
    }

    suspend fun shareCompletedRoute(
        requestId: String,
        routeCode: String,
        shareText: String,
        imageBytes: ByteArray,
        authorizationHeader: String
    ): RouteShare = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId/routes/$routeCode/share")
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
                writeMultipartText(outputStream, boundary, "shareText", shareText)
                writeMultipartFile(outputStream, boundary, "image", "route-share.jpg", "image/jpeg", imageBytes)
                outputStream.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
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
                parseRouteShare(JSONObject(responseBody))
            }.getOrElse { throwable ->
                throw IllegalStateException("路线分享保存响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线分享服务：${exception.message}", exception)
        }
    }

    suspend fun fetchRouteInteractions(authorizationHeader: String): List<RouteInteractionResponse> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/interactions")
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
                JSONArray(responseBody).mapObjects(::parseRouteInteraction)
            }.getOrElse { throwable ->
                throw IllegalStateException("路线互动状态解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun saveRouteInteraction(
        requestId: String,
        routeCode: String,
        interaction: RouteInteractionState,
        authorizationHeader: String
    ): RouteInteractionResponse = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL(
                "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId/routes/$routeCode/interaction"
            )
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true

            val requestBody = JSONObject()
                .put("favorite", interaction.isFavorite)
                .put("reaction", interaction.reaction.toApiValue() ?: JSONObject.NULL)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
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
                parseRouteInteraction(JSONObject(responseBody))
            }.getOrElse { throwable ->
                throw IllegalStateException("路线互动状态保存响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun fetchRouteHistoryDetail(
        requestId: String,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId")
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
                parseRouteGeneration(JSONObject(responseBody))
            }.getOrElse { throwable ->
                throw IllegalStateException("路线历史详情解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun fetchActiveRoute(authorizationHeader: String): RouteGeneration? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/active")
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
            if (responseBody.isBlank()) {
                null
            } else {
                runCatching {
                    parseRouteGeneration(JSONObject(responseBody))
                }.getOrElse { throwable ->
                    throw IllegalStateException("进行中路线解析失败：${throwable.message}", throwable)
                }
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun activateRoute(
        requestId: String,
        routeCode: String,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId/active-route")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true

            val requestBody = JSONObject()
                .put("routeCode", routeCode)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
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
                throw IllegalStateException("进行中路线响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun completeActiveRoute(
        requestId: String,
        routeCode: String,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/history/$requestId/active-route/complete")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true

            val requestBody = JSONObject()
                .put("routeCode", routeCode)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
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
                throw IllegalStateException("路线完成响应解析失败：${throwable.message}", throwable)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("无法连接路线服务：${exception.message}", exception)
        }
    }

    suspend fun generateRoute(
        request: RouteGenerateRequest,
        authorizationHeader: String
    ): RouteGeneration = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/routes/requests")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = ROUTE_GENERATION_READ_TIMEOUT_MILLIS
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
            candidateSetId = json.getString("candidateSetId"),
            status = json.getString("status"),
            area = parseRouteArea(json.getJSONObject("area")),
            routes = json.getJSONArray("routes").mapObjects(::parseGeneratedRoute),
            warnings = json.optJSONArray("warnings").orEmptyStringList(),
            generationStage = json.optNullableString("generationStage"),
            activeRouteCode = json.optNullableString("activeRouteCode"),
            executionStatus = json.optString("executionStatus").ifBlank { "GENERATED" }
        )
    }

    private fun parseRouteHistoryGroup(json: JSONObject): RouteHistoryGroup {
        return RouteHistoryGroup(
            requestId = json.getString("requestId"),
            candidateSetId = json.getString("candidateSetId"),
            areaLabel = json.getString("areaLabel"),
            createdAt = json.optString("createdAt"),
            generationStatus = json.optString("generationStatus").ifBlank { "SUCCESS" },
            generationStage = json.optNullableString("generationStage"),
            activeRouteCode = json.optNullableString("activeRouteCode"),
            executionStatus = json.optString("executionStatus").ifBlank { "GENERATED" },
            routes = json.getJSONArray("routes").mapObjects(::parseRouteHistoryRouteSummary)
        )
    }

    private fun parseRouteShare(json: JSONObject): RouteShare {
        return RouteShare(
            shareId = json.getString("shareId"),
            requestId = json.getString("requestId"),
            routeCode = json.getString("routeCode"),
            routeTitle = json.optString("routeTitle").ifBlank { "城市路线" },
            cityName = json.optString("cityName").ifBlank { "城市" },
            totalDurationMinutes = json.optNullableInt("totalDurationMinutes"),
            totalDistanceMeters = json.optNullableInt("totalDistanceMeters"),
            stopCount = json.optNullableInt("stopCount"),
            shareText = json.optString("shareText"),
            imageUrl = json.optString("imageUrl"),
            createdAt = json.optString("createdAt")
        )
    }

    private fun parseRouteHistoryRouteSummary(json: JSONObject): RouteHistoryRouteSummary {
        return RouteHistoryRouteSummary(
            routeCode = json.getString("routeCode"),
            title = json.getString("title"),
            totalDurationMinutes = json.getInt("totalDurationMinutes"),
            totalDistanceMeters = json.getInt("totalDistanceMeters"),
            riskLevel = json.getString("riskLevel")
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
            stops = json.getJSONArray("stops").mapObjects(::parseRouteStop),
            segments = json.optJSONArray("segments")?.mapObjects(::parseRouteSegment).orEmpty()
        )
    }

    private fun parseRouteStop(json: JSONObject): RouteStop {
        return RouteStop(
            id = json.getString("stopId"),
            order = json.getInt("order"),
            name = json.getString("name"),
            location = parseGeoPoint(json.getJSONObject("location")),
            reason = json.optNullableString("reason"),
            slotLabel = json.optNullableString("slotLabel"),
            description = json.optNullableString("description"),
            imageUrls = json.optJSONArray("imageUrls").orEmptyStringList(),
            category = json.optNullableString("category"),
            rating = json.optNullableDouble("rating"),
            stayMinutes = json.optNullableInt("stayMinutes"),
            transportToNext = json.optNullableString("transportToNext"),
            distanceToNextMeters = json.optNullableInt("distanceToNextMeters"),
            durationToNextMinutes = json.optNullableInt("durationToNextMinutes"),
            riskNote = json.optNullableString("riskNote")
        )
    }

    private fun parseRouteSegment(json: JSONObject): RouteSegment {
        return RouteSegment(
            order = json.getInt("order"),
            originStopId = json.getString("originStopId"),
            destinationStopId = json.getString("destinationStopId"),
            mode = json.getString("mode"),
            distanceMeters = json.getInt("distanceMeters"),
            durationMinutes = json.getInt("durationMinutes"),
            polyline = json.optJSONArray("polyline")?.mapObjects(::parseGeoPoint).orEmpty(),
            steps = json.optJSONArray("steps")?.mapObjects(::parseRouteStep).orEmpty(),
            summary = json.optString("summary").ifBlank { "查看这一段怎么去" }
        )
    }

    private fun parseRouteStep(json: JSONObject): RouteStep {
        return RouteStep(
            order = json.getInt("order"),
            instruction = json.optString("instruction").ifBlank { "继续前行" },
            roadName = json.optNullableString("roadName"),
            distanceMeters = json.optInt("distanceMeters"),
            durationMinutes = json.optInt("durationMinutes"),
            polyline = json.optJSONArray("polyline")?.mapObjects(::parseGeoPoint).orEmpty()
        )
    }

    private fun parseGeoPoint(json: JSONObject): GeoPoint {
        return GeoPoint(
            longitudeGcj02 = json.getDouble("longitudeGcj02"),
            latitudeGcj02 = json.getDouble("latitudeGcj02")
        )
    }

    private fun parseRouteInteraction(json: JSONObject): RouteInteractionResponse {
        return RouteInteractionResponse(
            candidateSetId = json.getString("candidateSetId"),
            routeCode = json.getString("routeCode"),
            state = RouteInteractionState(
                isFavorite = json.optBoolean("favorite", false),
                reaction = json.optNullableString("reaction").toRouteReaction()
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

    private fun readBytes(connection: HttpURLConnection): ByteArray {
        val inputStream = if (connection.responseCode in HTTP_SUCCESS_RANGE) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ByteArray(0)
        return inputStream.use { stream ->
            val output = ByteArrayOutputStream()
            stream.copyTo(output)
            output.toByteArray()
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
        private const val ROUTE_GENERATION_READ_TIMEOUT_MILLIS = 5 * 60 * 1000
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
    val radiusMeters: Int?,
    val areaPolygonGcj02: List<GeoPoint>,
    val adminAdcodes: List<String>,
    val routeCityName: String?,
    val routeCityAdcode: String?,
    val departureTime: String,
    val durationMinutes: Int,
    val transportProfile: String,
    val routeGoal: String,
    val budgetLevel: String,
    val interestTags: List<String>,
    val mealWindows: List<String>,
    val mustVisitPoints: List<MustVisitPointRequest>
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("areaMode", areaMode)
            .put("areaLabel", areaLabel)
            .put("center", center.toJson())
            .put("radiusMeters", radiusMeters ?: JSONObject.NULL)
            .put("areaPolygonGcj02", areaPolygonGcj02.toGeoPointArray())
            .put("adminAdcodes", JSONArray(adminAdcodes))
            .put("routeCityName", routeCityName ?: JSONObject.NULL)
            .put("routeCityAdcode", routeCityAdcode ?: JSONObject.NULL)
            .put("departureTime", departureTime)
            .put("durationMinutes", durationMinutes)
            .put("transportProfile", transportProfile)
            .put("routeGoal", routeGoal)
            .put("budgetLevel", budgetLevel)
            .put("interestTags", JSONArray(interestTags))
            .put("mealWindows", JSONArray(mealWindows))
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

data class RouteInteractionResponse(
    val candidateSetId: String,
    val routeCode: String,
    val state: RouteInteractionState
)

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

private fun JSONObject.optNullableString(name: String): String? {
    return if (isNull(name)) {
        null
    } else {
        optString(name).ifBlank { null }
    }
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    return if (isNull(name)) {
        null
    } else {
        optDouble(name)
    }
}

private fun RouteReaction?.toApiValue(): String? {
    return when (this) {
        RouteReaction.Liked -> "LIKED"
        RouteReaction.Disliked -> "DISLIKED"
        null -> null
    }
}

private fun String?.toRouteReaction(): RouteReaction? {
    return when (this) {
        "LIKED" -> RouteReaction.Liked
        "DISLIKED" -> RouteReaction.Disliked
        else -> null
    }
}

private fun writeMultipartText(
    outputStream: OutputStream,
    boundary: String,
    name: String,
    value: String
) {
    outputStream.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
    outputStream.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
    outputStream.write(value.toByteArray(StandardCharsets.UTF_8))
    outputStream.write("\r\n".toByteArray(StandardCharsets.UTF_8))
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
