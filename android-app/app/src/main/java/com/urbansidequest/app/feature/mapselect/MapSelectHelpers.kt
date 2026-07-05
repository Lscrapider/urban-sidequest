package com.urbansidequest.app.feature.mapselect

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.feature.routeconfig.CodeOption
import com.urbansidequest.app.feature.routeconfig.FoodInterestGroups
import com.urbansidequest.app.feature.routeconfig.RouteConfigUiState
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun RouteConfigUiState.validateMapRouteCondition(): String? {
    val foodCodes = FoodInterestGroups
        .flatMap { group -> listOf(group.option.code) + group.children.map { it.code } }
        .toSet()
    if (selectedInterestTags.any { it in foodCodes } && selectedMealWindows.isEmpty()) {
        return "选择餐饮偏好时，请至少选择午餐或晚餐"
    }
    return null
}

internal fun previewSearchRadiusMeters(uiState: RouteConfigUiState): Int {
    val bucket = when {
        uiState.selectedDuration.minutes <= 120 -> PreviewDurationBucket.Short
        uiState.selectedDuration.minutes <= 300 -> PreviewDurationBucket.HalfDay
        else -> PreviewDurationBucket.FullDay
    }
    return PreviewRadiusMetersByTransport[uiState.selectedTransport.code]
        ?.get(bucket)
        ?: PreviewRadiusMetersByTransport.getValue("WALK_SUBWAY").getValue(bucket)
}

internal fun formatPreviewArea(radiusMeters: Int): String {
    val squareKilometers = (radiusMeters * 2.0 * radiusMeters * 2.0) / 1_000_000.0
    return "约 ${String.format("%.1f", squareKilometers)} km²"
}

internal fun CodeOption.shortTransportLabel(): String {
    return when (code) {
        "WALK_ONLY" -> "只步行"
        "WALK_SUBWAY" -> "步行+地铁"
        "WALK_BUS" -> "步行+公交"
        "WALK_TRANSIT" -> "混合交通"
        "BIKE_SUBWAY" -> "骑车+地铁"
        "WALK_TAXI" -> "步行+打车"
        else -> label
    }
}

internal fun GeoPoint.toLatLng(): LatLng {
    return LatLng(latitudeGcj02, longitudeGcj02)
}

internal fun manualRangePreviewPoints(center: LatLng, radiusMeters: Int): List<LatLng> {
    val bearings = listOf(-92.0, -28.0, 18.0, 76.0, 136.0, 178.0, -144.0)
    val scales = listOf(0.86, 1.08, 0.94, 1.05, 0.92, 1.00, 0.90)
    return bearings.mapIndexed { index, bearing ->
        offsetLatLng(center = center, distanceMeters = radiusMeters * scales[index], bearingDegrees = bearing)
    }
}

internal fun offsetLatLng(center: LatLng, distanceMeters: Double, bearingDegrees: Double): LatLng {
    val bearingRadians = Math.toRadians(bearingDegrees)
    val latitudeRadians = Math.toRadians(center.latitude)
    val latitudeOffset = distanceMeters * cos(bearingRadians) / EARTH_RADIUS_METERS
    val longitudeOffset = distanceMeters * sin(bearingRadians) / (EARTH_RADIUS_METERS * cos(latitudeRadians))
    return LatLng(
        center.latitude + Math.toDegrees(latitudeOffset),
        center.longitude + Math.toDegrees(longitudeOffset)
    )
}

internal fun List<RouteStop>.toLatLngBounds(): LatLngBounds? {
    if (isEmpty()) {
        return null
    }
    val builder = LatLngBounds.builder()
    forEach { stop -> builder.include(stop.location.toLatLng()) }
    return builder.build()
}

internal fun distanceMeters(origin: LatLng, destination: LatLng): Int {
    val originLatitude = Math.toRadians(origin.latitude)
    val destinationLatitude = Math.toRadians(destination.latitude)
    val latitudeDelta = destinationLatitude - originLatitude
    val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
    val haversine = (sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(originLatitude) * cos(destinationLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)).coerceIn(0.0, 1.0)
    val centralAngle = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return (EARTH_RADIUS_METERS * centralAngle).roundToInt()
}

internal fun buildEstimatedSegmentPath(
    segment: RouteSegment,
    stopsById: Map<String, RouteStop>
): List<LatLng> {
    val origin = stopsById[segment.originStopId]?.location?.toLatLng()
    val destination = stopsById[segment.destinationStopId]?.location?.toLatLng()
    return listOfNotNull(origin, destination)
}

internal fun buildRailSegmentPayload(
    routeIndex: Int,
    route: GeneratedRoute,
    originStop: RouteStop,
    destinationStop: RouteStop
): RouteSegmentPolylinePayload {
    val segment = route.segments.firstOrNull { segment ->
        segment.originStopId == originStop.id && segment.destinationStopId == destinationStop.id
    } ?: RouteSegment(
        order = originStop.order,
        originStopId = originStop.id,
        destinationStopId = destinationStop.id,
        mode = originStop.transportToNext ?: "WALK",
        distanceMeters = originStop.distanceToNextMeters
            ?: distanceMeters(originStop.location.toLatLng(), destinationStop.location.toLatLng()),
        durationMinutes = originStop.durationToNextMinutes ?: 0,
        summary = "从${originStop.name}前往${destinationStop.name}，按当前路线推荐方式前往。"
    )
    return RouteSegmentPolylinePayload(
        routeIndex = routeIndex,
        routeCode = route.routeCode,
        segment = segment,
        originStop = originStop,
        destinationStop = destinationStop,
        isEstimated = segment.polyline.size < 2
    )
}

internal fun executionLegDistance(
    stops: List<RouteStop>,
    stop: RouteStop
): Int? {
    val sortedStops = stops.sortedBy(RouteStop::order)
    val index = sortedStops.indexOfFirst { it.id == stop.id }
    if (index <= 0) {
        return null
    }
    return sortedStops[index - 1].distanceToNextMeters
}

internal fun executionLegDuration(
    stops: List<RouteStop>,
    stop: RouteStop
): Int? {
    val sortedStops = stops.sortedBy(RouteStop::order)
    val index = sortedStops.indexOfFirst { it.id == stop.id }
    if (index <= 0) {
        return null
    }
    return sortedStops[index - 1].durationToNextMinutes
}

internal fun routeLineColor(index: Int, selected: Boolean, estimated: Boolean): Int {
    val color = routeColor(index)
    val alpha = when {
        estimated -> ROUTE_ESTIMATED_ALPHA
        selected -> ROUTE_SELECTED_ALPHA
        else -> ROUTE_ALTERNATIVE_ALPHA
    }
    return color.withAlpha(alpha)
}

internal fun routeLineWidth(selected: Boolean, estimated: Boolean): Float {
    val width = if (selected) ROUTE_SELECTED_WIDTH else ROUTE_ALTERNATIVE_WIDTH
    return if (estimated) width - ROUTE_ESTIMATED_WIDTH_DELTA else width
}

internal fun routeLineZIndex(selected: Boolean, estimated: Boolean): Float {
    return when {
        selected && !estimated -> 8f
        selected -> 6f
        !estimated -> 4f
        else -> 3f
    }
}

internal fun routeColor(index: Int): Int {
    return when (index % 5) {
        1 -> ROUTE_B_COLOR
        2 -> ROUTE_C_COLOR
        3 -> ROUTE_D_COLOR
        4 -> ROUTE_E_COLOR
        else -> ROUTE_A_COLOR
    }
}

internal fun Int.withAlpha(alpha: Int): Int {
    return AndroidColor.argb(
        alpha,
        AndroidColor.red(this),
        AndroidColor.green(this),
        AndroidColor.blue(this)
    )
}

internal fun Int.toComposeColor(): Color {
    return Color(this)
}

internal fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours} 小时 ${restMinutes} 分钟"
        hours > 0 -> "${hours} 小时"
        else -> "${minutes} 分钟"
    }
}

internal fun formatCompactDuration(minutes: Int): String {
    return if (minutes >= 60) {
        "${String.format("%.1f", minutes / 60.0)}小时"
    } else {
        "${minutes}分钟"
    }
}

internal fun formatCompactDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${String.format("%.1f", meters / 1000.0)} km"
    } else {
        "${meters} m"
    }
}

internal fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${meters / 1000}.${meters % 1000 / 100} 公里"
    } else {
        "${meters} 米"
    }
}

internal fun formatBudget(budgetCent: Int?): String {
    return if (budgetCent == null) {
        "预算待定"
    } else {
        "约 ${budgetCent / 100} 元"
    }
}

internal fun formatRating(rating: Double): String {
    return String.format("%.1f", rating)
}

internal fun formatRiskLevel(riskLevel: String): String {
    return when (riskLevel) {
        "LOW" -> "风险低"
        "MEDIUM" -> "需留意"
        "HIGH" -> "风险高"
        else -> "风险待确认"
    }
}

internal fun formatTransportMode(mode: String): String {
    return when (mode) {
        "WALK" -> "步行"
        "TRANSIT" -> "公共交通"
        "SUBWAY" -> "地铁"
        "BUS" -> "公交"
        "BIKE" -> "骑行"
        "TAXI" -> "打车"
        "DRIVE" -> "驾车"
        else -> "交通待确认"
    }
}

internal fun buildRouteSegmentTitle(payload: RouteSegmentPolylinePayload): String {
    val originName = payload.originStop?.name ?: "上一站"
    val destinationName = payload.destinationStop?.name ?: "下一站"
    return "$originName → $destinationName"
}

internal fun formatCategory(category: String?): String {
    return when (category) {
        "CULTURE" -> "文化展馆"
        "SCENIC" -> "景点"
        "FOOD" -> "餐饮"
        "REST" -> "休息点"
        "LOCAL" -> "本地街区"
        "NIGHT" -> "夜游点"
        else -> "地点"
    }
}

internal fun formatStopLabel(stop: RouteStop): String {
    return stop.slotLabel ?: formatCategory(stop.category)
}

internal fun buildCheckInDistanceText(distanceMeters: Int?, canCheckIn: Boolean): String {
    if (distanceMeters == null) {
        return "正在获取当前位置，靠近后可确认打卡。"
    }
    return if (canCheckIn) {
        "你已进入 ${CHECK_IN_RADIUS_METERS} 米范围，确认后记录这一站。"
    } else {
        "距离目标点约 ${formatDistance(distanceMeters)}。"
    }
}
