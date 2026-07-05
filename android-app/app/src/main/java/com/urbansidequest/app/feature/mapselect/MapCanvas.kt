package com.urbansidequest.app.feature.mapselect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import kotlin.math.roundToInt

@Composable
internal fun AMapCanvas(
    modifier: Modifier = Modifier,
    currentLocation: LatLng,
    routeGeneration: RouteGeneration?,
    selectedRouteIndex: Int?,
    visibleRouteIndexes: Set<Int>,
    previewRadiusMeters: Int,
    rangeSelectionMode: RangeSelectionMode,
    onMapReady: (AMap) -> Unit,
    onRouteStopClick: (RouteStopMarkerPayload) -> Unit,
    onRouteSegmentClick: (RouteSegmentPolylinePayload) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var currentLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var previewAreaCircle by remember { mutableStateOf<Circle?>(null) }
    var previewAreaPolygon by remember { mutableStateOf<Polygon?>(null) }
    val routePolylines = remember { mutableListOf<Polyline>() }
    val routeMarkers = remember { mutableListOf<Marker>() }
    val routeSegmentPayloads = remember { mutableMapOf<String, RouteSegmentPolylinePayload>() }
    var lastRenderedRouteKey by remember { mutableStateOf<String?>(null) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                val aMap = map
                aMap.uiSettings.isZoomControlsEnabled = false
                aMap.uiSettings.isCompassEnabled = false
                aMap.uiSettings.isScaleControlsEnabled = true
                currentLocationMarker = aMap.addMarker(
                    MarkerOptions()
                        .position(currentLocation)
                        .anchor(0.5f, 0.5f)
                        .icon(createCurrentLocationIcon(context))
                        .zIndex(10f)
                )
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14f))
                aMap.setOnMarkerClickListener { marker ->
                    val payload = marker.`object` as? RouteStopMarkerPayload
                    if (payload != null) {
                        onRouteStopClick(payload)
                        true
                    } else {
                        false
                    }
                }
                aMap.setOnPolylineClickListener { polyline ->
                    routeSegmentPayloads[polyline.id]?.let(onRouteSegmentClick)
                }
                onMapReady(aMap)
            }
        },
        update = {
            currentLocationMarker?.position = currentLocation
            val aMap = mapView.map
            val routes = routeGeneration?.routes.orEmpty()
            if (routeGeneration == null) {
                if (rangeSelectionMode == RangeSelectionMode.Auto) {
                    previewAreaPolygon?.remove()
                    previewAreaPolygon = null
                    val circle = previewAreaCircle
                    if (circle == null) {
                        previewAreaCircle = aMap.addCircle(
                            CircleOptions()
                                .center(currentLocation)
                                .radius(previewRadiusMeters.toDouble())
                                .strokeColor(AndroidColor.argb(190, 19, 115, 230))
                                .fillColor(AndroidColor.argb(34, 19, 115, 230))
                                .strokeWidth(4f)
                                .zIndex(2f)
                        )
                    } else {
                        circle.center = currentLocation
                        circle.radius = previewRadiusMeters.toDouble()
                    }
                } else {
                    previewAreaCircle?.remove()
                    previewAreaCircle = null
                    val polygonPoints = manualRangePreviewPoints(currentLocation, previewRadiusMeters)
                    val polygon = previewAreaPolygon
                    if (polygon == null) {
                        previewAreaPolygon = aMap.addPolygon(
                            PolygonOptions()
                                .addAll(polygonPoints)
                                .strokeColor(AndroidColor.argb(230, 19, 115, 230))
                                .fillColor(AndroidColor.argb(38, 19, 115, 230))
                                .strokeWidth(5f)
                                .zIndex(2f)
                        )
                    } else {
                        polygon.points = polygonPoints
                    }
                }
            } else {
                previewAreaCircle?.remove()
                previewAreaCircle = null
                previewAreaPolygon?.remove()
                previewAreaPolygon = null
            }
            val renderKey = "${routeGeneration?.requestId}:$selectedRouteIndex:${visibleRouteIndexes.sorted().joinToString(",")}:$previewRadiusMeters:$rangeSelectionMode:${currentLocation.latitude}:${currentLocation.longitude}"
            if (renderKey == lastRenderedRouteKey) {
                return@AndroidView
            }
            routePolylines.forEach { polyline -> polyline.remove() }
            routeMarkers.forEach { marker -> marker.remove() }
            routePolylines.clear()
            routeMarkers.clear()
            routeSegmentPayloads.clear()
            routes.forEachIndexed { index, route ->
                if (index !in visibleRouteIndexes) {
                    return@forEachIndexed
                }
                val isSelected = index == selectedRouteIndex
                val sortedStops = route.stops.sortedBy(RouteStop::order)
                val stopsById = route.stops.associateBy(RouteStop::id)
                if (route.segments.isNotEmpty()) {
                    route.segments.forEach { segment ->
                        val isEstimated = segment.polyline.size < 2
                        val path = if (isEstimated) {
                            buildEstimatedSegmentPath(segment, stopsById)
                        } else {
                            segment.polyline.map { point -> point.toLatLng() }
                        }
                        if (path.size < 2) {
                            return@forEach
                        }
                        val polyline = aMap.addPolyline(
                            PolylineOptions()
                                .addAll(path)
                                .color(
                                    routeLineColor(
                                        index = index,
                                        selected = isSelected,
                                        estimated = isEstimated
                                    )
                                )
                                .width(routeLineWidth(selected = isSelected, estimated = isEstimated))
                                .zIndex(routeLineZIndex(selected = isSelected, estimated = isEstimated))
                                .setDottedLine(isEstimated)
                                .setDottedLineType(PolylineOptions.DOTTEDLINE_TYPE_CIRCLE)
                        )
                        routePolylines.add(polyline)
                        routeSegmentPayloads[polyline.id] = RouteSegmentPolylinePayload(
                            routeIndex = index,
                            routeCode = route.routeCode,
                            segment = segment,
                            originStop = stopsById[segment.originStopId],
                            destinationStop = stopsById[segment.destinationStopId],
                            isEstimated = isEstimated
                        )
                    }
                } else {
                    val path = sortedStops.map { stop -> stop.location.toLatLng() }
                    if (path.size >= 2) {
                        routePolylines.add(
                            aMap.addPolyline(
                                PolylineOptions()
                                    .addAll(path)
                                    .color(
                                        routeLineColor(
                                            index = index,
                                            selected = isSelected,
                                            estimated = true
                                        )
                                    )
                                    .width(routeLineWidth(selected = isSelected, estimated = true))
                                    .zIndex(routeLineZIndex(selected = isSelected, estimated = true))
                                    .setDottedLine(true)
                                    .setDottedLineType(PolylineOptions.DOTTEDLINE_TYPE_CIRCLE)
                            )
                        )
                    }
                }
                sortedStops.forEach { stop ->
                    val marker = aMap.addMarker(
                        MarkerOptions()
                            .position(stop.location.toLatLng())
                            .anchor(0.5f, 0.5f)
                            .icon(
                                createRouteStopIcon(
                                    context = context,
                                    order = stop.order,
                                    routeColor = routeColor(index),
                                    selected = isSelected
                                )
                            )
                            .zIndex(if (isSelected) 12f else 7f)
                    )
                    marker.`object` = RouteStopMarkerPayload(routeIndex = index, stop = stop)
                    routeMarkers.add(marker)
                }
            }
            val selectedRoute = selectedRouteIndex?.let { routes.getOrNull(it) }
            if (selectedRoute != null) {
                val bounds = selectedRoute.stops.toLatLngBounds()
                if (bounds != null) {
                    aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
            }
            lastRenderedRouteKey = renderKey
        }
    )
}

internal fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

internal fun startSingleAmapLocation(
    context: Context,
    onLocated: (LatLng) -> Unit
) {
    val locationClient = AMapLocationClient(context.applicationContext)
    val locationOption = AMapLocationClientOption().apply {
        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        isOnceLocation = true
        isOnceLocationLatest = true
        isNeedAddress = false
        httpTimeOut = LOCATION_TIMEOUT_MILLIS
    }

    locationClient.setLocationOption(locationOption)
    locationClient.setLocationListener { location ->
        if (location != null && location.errorCode == 0) {
            onLocated(LatLng(location.latitude, location.longitude))
        }
        locationClient.stopLocation()
        locationClient.onDestroy()
    }
    locationClient.startLocation()
}

internal fun createCurrentLocationIcon(context: Context): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (46 * density).roundToInt()
    val outerRadius = size / 2f
    val whiteRadius = 13 * density
    val innerRadius = 8 * density
    val strokeWidth = 2 * density
    val center = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(42, 19, 115, 230)
    canvas.drawCircle(center, center, outerRadius, paint)

    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, whiteRadius, paint)

    paint.color = ROUTE_A_COLOR
    canvas.drawCircle(center, center, innerRadius, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, whiteRadius, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal fun createRouteStopIcon(
    context: Context,
    order: Int,
    routeColor: Int,
    selected: Boolean
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = ((if (selected) 34 else 26) * density).roundToInt()
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(if (selected) 150 else 96, 255, 255, 255)
    canvas.drawCircle(center, center, center, paint)

    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, center - 2 * density, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = if (selected) 2.4f * density else 1.4f * density
    paint.color = if (selected) routeColor else routeColor.withAlpha(176)
    canvas.drawCircle(center, center, center - 3.5f * density, paint)

    paint.style = Paint.Style.FILL
    paint.color = if (selected) routeColor else routeColor.withAlpha(192)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = if (selected) 12 * density else 10 * density
    paint.isFakeBoldText = true
    val baseline = center - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(order.toString(), center, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal const val LOCATION_TIMEOUT_MILLIS = 8_000L
