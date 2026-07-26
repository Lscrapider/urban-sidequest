package com.urbansidequest.app.feature.mapselect

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Route
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop

internal val DefaultMapCenter = LatLng(39.908722, 116.397499)
internal val HorizontalScreenPadding = 16.dp
internal val FloatingMapControlGap = 20.dp
internal const val DEFAULT_VISIBLE_ROUTE_INDEX = 0
internal const val MIN_MANUAL_POLYGON_VERTEX_COUNT = 3
internal const val MAX_VISIBLE_ROUTE_STEPS = 4
internal val ROUTE_A_COLOR = AndroidColor.rgb(19, 115, 230)
internal val ROUTE_B_COLOR = AndroidColor.rgb(96, 125, 139)
internal val ROUTE_C_COLOR = AndroidColor.rgb(85, 105, 150)
internal val ROUTE_D_COLOR = AndroidColor.rgb(16, 148, 108)
internal val ROUTE_E_COLOR = AndroidColor.rgb(126, 87, 194)
internal const val ROUTE_SELECTED_ALPHA = 255
internal const val ROUTE_ALTERNATIVE_ALPHA = 156
internal const val ROUTE_ESTIMATED_ALPHA = 112
internal const val ROUTE_SELECTED_WIDTH = 13f
internal const val ROUTE_ALTERNATIVE_WIDTH = 7f
internal const val ROUTE_ESTIMATED_WIDTH_DELTA = 1.5f
internal const val ROUTE_SHEET_DRAG_RANGE_PX = 720f
internal const val ROUTE_SHEET_HIDE_DRAG_RANGE_PX = 220f
internal const val ROUTE_SHEET_SNAP_THRESHOLD = 0.5f
internal const val ROUTE_SHEET_COLLAPSE_DRAG_PX = 32f
internal const val ROUTE_SHEET_PEEK_HANDLE_HEIGHT_PX = 48f
internal const val CHECK_IN_RADIUS_METERS = 200
internal const val EARTH_RADIUS_METERS = 6_371_000.0
internal const val IMAGE_CONNECT_TIMEOUT_MILLIS = 3_000
internal const val IMAGE_READ_TIMEOUT_MILLIS = 8_000
internal val RouteSwitcherShape = RoundedCornerShape(14.dp)
internal val RouteSwitcherSegmentShape = RoundedCornerShape(12.dp)
internal val RouteSwitcherSegmentWidth = 42.dp
internal val RouteSwitcherSegmentHeight = 24.dp
internal val RouteSwitcherTouchTargetSize = 48.dp
internal val RoutePoiRailWidth = 24.dp
internal val RoutePoiRailTouchSize = 24.dp
internal val RoutePoiRailDotSize = 12.dp
internal val RoutePoiRailConnectorHeight = 18.dp
internal enum class MapGenerationPanelMode {
    Range,
    Conditions,
    RouteDetail
}

internal enum class RangeSelectionMode {
    Auto,
    Manual
}

internal enum class PreviewDurationBucket {
    Short,
    HalfDay,
    FullDay
}

internal val PreviewRadiusMetersByTransport = mapOf(
    "WALK_ONLY" to mapOf(
        PreviewDurationBucket.Short to 600,
        PreviewDurationBucket.HalfDay to 760,
        PreviewDurationBucket.FullDay to 1200
    ),
    "WALK_SUBWAY" to mapOf(
        PreviewDurationBucket.Short to 680,
        PreviewDurationBucket.HalfDay to 800,
        PreviewDurationBucket.FullDay to 1400
    ),
    "WALK_BUS" to mapOf(
        PreviewDurationBucket.Short to 660,
        PreviewDurationBucket.HalfDay to 780,
        PreviewDurationBucket.FullDay to 1300
    ),
    "WALK_TRANSIT" to mapOf(
        PreviewDurationBucket.Short to 720,
        PreviewDurationBucket.HalfDay to 920,
        PreviewDurationBucket.FullDay to 1600
    ),
    "BIKE_SUBWAY" to mapOf(
        PreviewDurationBucket.Short to 900,
        PreviewDurationBucket.HalfDay to 1200,
        PreviewDurationBucket.FullDay to 2100
    ),
    "WALK_TAXI" to mapOf(
        PreviewDurationBucket.Short to 1000,
        PreviewDurationBucket.HalfDay to 1500,
        PreviewDurationBucket.FullDay to 2600
    )
)

internal data class RouteStopMarkerPayload(
    val routeIndex: Int,
    val stop: RouteStop
)

internal data class RouteSegmentPolylinePayload(
    val routeIndex: Int,
    val routeCode: String,
    val segment: RouteSegment,
    val originStop: RouteStop?,
    val destinationStop: RouteStop?,
    val isEstimated: Boolean
)
