package com.urbansidequest.app.feature.mapselect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.searchAmapInputTips
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanSearchField
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.net.URL

private val DefaultMapCenter = LatLng(39.908722, 116.397499)
private val HorizontalScreenPadding = 16.dp
private val FloatingMapControlGap = 20.dp
private const val DEFAULT_VISIBLE_ROUTE_INDEX = 0
private const val MAX_VISIBLE_ROUTE_STEPS = 4
private val ROUTE_A_COLOR = AndroidColor.rgb(19, 115, 230)
private val ROUTE_B_COLOR = AndroidColor.rgb(96, 125, 139)
private val ROUTE_C_COLOR = AndroidColor.rgb(85, 105, 150)
private const val ROUTE_SELECTED_ALPHA = 255
private const val ROUTE_ALTERNATIVE_ALPHA = 156
private const val ROUTE_ESTIMATED_ALPHA = 112
private const val ROUTE_SELECTED_WIDTH = 13f
private const val ROUTE_ALTERNATIVE_WIDTH = 7f
private const val ROUTE_ESTIMATED_WIDTH_DELTA = 1.5f
private const val ROUTE_SHEET_DRAG_RANGE_PX = 720f
private const val ROUTE_SHEET_HIDE_DRAG_RANGE_PX = 220f
private const val ROUTE_SHEET_SNAP_THRESHOLD = 0.5f
private const val ROUTE_SHEET_COLLAPSE_DRAG_PX = 32f
private const val ROUTE_SHEET_PEEK_HANDLE_HEIGHT_PX = 48f
private const val IMAGE_CONNECT_TIMEOUT_MILLIS = 3_000
private const val IMAGE_READ_TIMEOUT_MILLIS = 8_000
private val RouteSwitcherShape = RoundedCornerShape(14.dp)
private val RouteSwitcherSegmentShape = RoundedCornerShape(12.dp)
private val RouteSwitcherSegmentWidth = 42.dp
private val RouteSwitcherSegmentHeight = 24.dp

private data class RouteStopMarkerPayload(
    val routeIndex: Int,
    val stop: RouteStop
)

private data class RouteSegmentPolylinePayload(
    val routeIndex: Int,
    val routeCode: String,
    val segment: RouteSegment,
    val originStop: RouteStop?,
    val destinationStop: RouteStop?,
    val isEstimated: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapSelectScreen(
    routeGeneration: RouteGeneration? = null,
    onOpenRouteConfig: (GeoPoint) -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    var isSelectionExpanded by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<AMap?>(null) }
    var currentLocation by remember { mutableStateOf(DefaultMapCenter) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<PlaceSearchSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedRouteIndex by remember { mutableStateOf<Int?>(DEFAULT_VISIBLE_ROUTE_INDEX) }
    var visibleRouteIndexes by remember { mutableStateOf(setOf(DEFAULT_VISIBLE_ROUTE_INDEX)) }
    var routeSheetProgress by remember { mutableStateOf(0f) }
    var routeSheetHiddenProgress by remember { mutableStateOf(0f) }
    var routeSheetDragOffset by remember { mutableStateOf(0f) }
    var selectedStopPayload by remember { mutableStateOf<RouteStopMarkerPayload?>(null) }
    var selectedSegmentPayload by remember { mutableStateOf<RouteSegmentPolylinePayload?>(null) }
    val focusManager = LocalFocusManager.current
    val routes = routeGeneration?.routes.orEmpty()
    val selectedRoutePosition = selectedRouteIndex
    val selectedRoute = selectedRoutePosition?.let { routes.getOrNull(it) }

    fun resetRouteSheet() {
        routeSheetProgress = 0f
        routeSheetHiddenProgress = 0f
        routeSheetDragOffset = 0f
    }

    fun dragRouteSheet(drag: Float) {
        routeSheetDragOffset += drag
        if (drag > 0f) {
            if (routeSheetProgress > 0f) {
                routeSheetProgress = (routeSheetProgress - drag / ROUTE_SHEET_DRAG_RANGE_PX)
                    .coerceIn(0f, 1f)
            } else {
                routeSheetHiddenProgress = (routeSheetHiddenProgress + drag / ROUTE_SHEET_HIDE_DRAG_RANGE_PX)
                    .coerceIn(0f, 1f)
            }
        } else {
            if (routeSheetHiddenProgress > 0f) {
                routeSheetHiddenProgress = (routeSheetHiddenProgress + drag / ROUTE_SHEET_HIDE_DRAG_RANGE_PX)
                    .coerceIn(0f, 1f)
            } else {
                routeSheetProgress = (routeSheetProgress - drag / ROUTE_SHEET_DRAG_RANGE_PX)
                    .coerceIn(0f, 1f)
            }
        }
    }

    fun settleRouteSheet() {
        when {
            routeSheetDragOffset > ROUTE_SHEET_COLLAPSE_DRAG_PX && routeSheetProgress > 0.02f -> {
                routeSheetProgress = 0f
                routeSheetHiddenProgress = 0f
            }
            routeSheetDragOffset > ROUTE_SHEET_COLLAPSE_DRAG_PX -> {
                routeSheetProgress = 0f
                routeSheetHiddenProgress = 1f
            }
            routeSheetDragOffset < -ROUTE_SHEET_COLLAPSE_DRAG_PX && routeSheetHiddenProgress > 0f -> {
                routeSheetHiddenProgress = 0f
                routeSheetProgress = 0f
            }
            routeSheetHiddenProgress >= ROUTE_SHEET_SNAP_THRESHOLD -> {
                routeSheetHiddenProgress = 1f
                routeSheetProgress = 0f
            }
            routeSheetProgress >= ROUTE_SHEET_SNAP_THRESHOLD -> {
                routeSheetHiddenProgress = 0f
                routeSheetProgress = 1f
            }
            else -> {
                routeSheetHiddenProgress = 0f
                routeSheetProgress = 0f
            }
        }
        routeSheetDragOffset = 0f
    }

    fun moveToLocation(location: LatLng, zoom: Float = 16f) {
        currentLocation = location
        mapController?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }

    fun focusStop(routeIndex: Int, stop: RouteStop) {
        selectedRouteIndex = routeIndex
        visibleRouteIndexes = visibleRouteIndexes + routeIndex
        selectedStopPayload = RouteStopMarkerPayload(routeIndex = routeIndex, stop = stop)
        selectedSegmentPayload = null
        resetRouteSheet()
        moveToLocation(stop.location.toLatLng(), 17f)
    }

    fun requestCurrentLocation() {
        startSingleAmapLocation(
            context = context,
            onLocated = ::moveToLocation
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestCurrentLocation()
        }
    }

    fun locateWithPermission() {
        if (context.hasLocationPermission()) {
            requestCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(routeGeneration?.requestId) {
        if (routeGeneration == null) {
            locateWithPermission()
        }
    }

    LaunchedEffect(routeGeneration?.requestId, routes.size) {
        selectedRouteIndex = if (routes.isNotEmpty()) DEFAULT_VISIBLE_ROUTE_INDEX else null
        visibleRouteIndexes = if (routes.isNotEmpty()) setOf(DEFAULT_VISIBLE_ROUTE_INDEX) else emptySet()
        resetRouteSheet()
        selectedStopPayload = null
        selectedSegmentPayload = null
        if (routeGeneration != null) {
            isSelectionExpanded = false
        }
    }

    LaunchedEffect(searchText, currentLocation) {
        val keyword = searchText.trim()
        if (!isSearchActive || keyword.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        searchAmapInputTips(
            context = context,
            keyword = keyword,
            location = currentLocation,
            onResult = { resultKeyword, suggestions ->
                if (resultKeyword == searchText.trim()) {
                    searchSuggestions = suggestions
                    isSearching = false
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppSurfaceMuted)
    ) {
        AMapCanvas(
            modifier = Modifier.fillMaxSize(),
            currentLocation = currentLocation,
            routeGeneration = routeGeneration,
            selectedRouteIndex = selectedRouteIndex,
            visibleRouteIndexes = visibleRouteIndexes,
            onMapReady = { mapController = it },
            onRouteStopClick = { payload ->
                focusStop(payload.routeIndex, payload.stop)
            },
            onRouteSegmentClick = { payload ->
                selectedRouteIndex = payload.routeIndex
                visibleRouteIndexes = visibleRouteIndexes + payload.routeIndex
                selectedSegmentPayload = payload
                selectedStopPayload = null
                resetRouteSheet()
            }
        )

        MapTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            isSearchActive = isSearchActive,
            searchText = searchText,
            suggestions = searchSuggestions,
            isSearching = isSearching,
            onSearchFocus = { isSearchActive = true },
            onSearchTextChange = { searchText = it },
            onCancelSearch = {
                isSearchActive = false
                searchText = ""
                searchSuggestions = emptyList()
                isSearching = false
                focusManager.clearFocus()
            },
            onSelectSuggestion = { suggestion ->
                moveToLocation(suggestion.location)
                searchText = suggestion.name
                searchSuggestions = emptyList()
                isSearchActive = false
                focusManager.clearFocus()
            }
        )

        if (routes.isNotEmpty() && !isSearchActive) {
            RouteSwitcher(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp),
                routes = routes,
                selectedRouteIndex = selectedRouteIndex,
                visibleRouteIndexes = visibleRouteIndexes,
                onToggleRoute = { routeIndex ->
                    val nextVisibleRouteIndexes = if (routeIndex in visibleRouteIndexes) {
                        visibleRouteIndexes - routeIndex
                    } else {
                        visibleRouteIndexes + routeIndex
                    }
                    visibleRouteIndexes = nextVisibleRouteIndexes
                    selectedRouteIndex = routeIndex
                    resetRouteSheet()
                    selectedStopPayload = null
                    selectedSegmentPayload = null
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (!isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = HorizontalScreenPadding),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MapLocationButton(onClick = ::locateWithPermission)
                }
                Spacer(modifier = Modifier.height(FloatingMapControlGap))
            }

            if (selectedRoute != null) {
                selectedSegmentPayload?.let { payload ->
                    RouteSegmentPopup(
                        routeColor = routeColor(payload.routeIndex),
                        payload = payload,
                        onClose = { selectedSegmentPayload = null }
                    )
                }
                selectedStopPayload?.let { payload ->
                    PoiDetailPopup(
                        routeCode = routes.getOrNull(payload.routeIndex)?.routeCode.orEmpty(),
                        routeColor = routeColor(payload.routeIndex),
                        stop = payload.stop,
                        onLocate = { focusStop(payload.routeIndex, payload.stop) },
                        onClose = { selectedStopPayload = null }
                    )
                }
                if (routeSheetHiddenProgress >= 0.99f) {
                    RouteDetailPeekHandle(
                        onDrag = { drag -> dragRouteSheet(drag) },
                        onDragEnd = { settleRouteSheet() }
                    )
                } else {
                    RouteDetailSheet(
                        route = selectedRoute,
                        routeIndex = selectedRoutePosition,
                        routeCount = routes.size,
                        sheetProgress = routeSheetProgress,
                        hiddenProgress = routeSheetHiddenProgress,
                        onDrag = { drag -> dragRouteSheet(drag) },
                        onDragEnd = { settleRouteSheet() },
                        onLocateStop = { stop -> focusStop(selectedRoutePosition, stop) },
                        onAdjustRoute = {
                            onOpenRouteConfig(
                                GeoPoint(
                                    longitudeGcj02 = currentLocation.longitude,
                                    latitudeGcj02 = currentLocation.latitude
                                )
                            )
                        }
                    )
                }
            } else {
                if (isSelectionExpanded) {
                    MapSelectionSheet(
                        onNext = {
                            onOpenRouteConfig(
                                GeoPoint(
                                    longitudeGcj02 = currentLocation.longitude,
                                    latitudeGcj02 = currentLocation.latitude
                                )
                            )
                        },
                        onManualSelect = {}
                    )
                } else {
                    MapHomeActionSheet(
                        onGenerateRoute = { isSelectionExpanded = true }
                    )
                }
            }
            UrbanBottomNavigationBar(
                selectedDestination = UrbanDestination.Map,
                onMapClick = {},
                onRoutesClick = onOpenRoutes,
                onProfileClick = onOpenProfile
            )
        }
    }
}

@Composable
private fun AMapCanvas(
    modifier: Modifier = Modifier,
    currentLocation: LatLng,
    routeGeneration: RouteGeneration?,
    selectedRouteIndex: Int?,
    visibleRouteIndexes: Set<Int>,
    onMapReady: (AMap) -> Unit,
    onRouteStopClick: (RouteStopMarkerPayload) -> Unit,
    onRouteSegmentClick: (RouteSegmentPolylinePayload) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var currentLocationMarker by remember { mutableStateOf<Marker?>(null) }
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
            val renderKey = "${routeGeneration?.requestId}:$selectedRouteIndex:${visibleRouteIndexes.sorted().joinToString(",")}"
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

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

private fun startSingleAmapLocation(
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

private fun createCurrentLocationIcon(context: Context): BitmapDescriptor {
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

private fun createRouteStopIcon(
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

private const val LOCATION_TIMEOUT_MILLIS = 8_000L

@Composable
private fun MapTopBar(
    modifier: Modifier = Modifier,
    isSearchActive: Boolean,
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    onSearchFocus: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(8.dp), clip = false)
        ) {
            UrbanSearchField(
                value = searchText,
                onValueChange = {
                    onSearchFocus()
                    onSearchTextChange(it)
                },
                placeholder = "搜索起点、区域或必去点",
                containerColor = AppSurface.copy(alpha = 0.86f),
                borderColor = AppBorder.copy(alpha = 0.58f),
                onFocus = onSearchFocus,
                leadingIcon = if (isSearchActive) {
                    {
                    IconButton(onClick = onCancelSearch) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出搜索",
                            tint = AppTextMuted
                        )
                    }
                    }
                } else {
                    null
                },
                trailingIcon = {
                    if (isSearchActive && searchText.isNotBlank()) {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空搜索",
                                tint = AppTextMuted
                            )
                        }
                    } else if (!isSearchActive) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "路线条件",
                            tint = DeepTeal
                        )
                    }
                }
            )
        }

        if (isSearchActive) {
            SearchSuggestionsPanel(
                searchText = searchText,
                suggestions = suggestions,
                isSearching = isSearching,
                onSelectSuggestion = onSelectSuggestion
            )
        }
    }
}

@Composable
private fun SearchSuggestionsPanel(
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(8.dp), clip = false),
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            when {
                searchText.trim().length < 2 -> SearchPanelHint(text = "输入至少 2 个字搜索地点")
                isSearching -> SearchPanelHint(text = "正在搜索")
                suggestions.isEmpty() -> SearchPanelHint(text = "没有找到可定位的地点")
                else -> suggestions.forEach { suggestion ->
                    SearchSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onSelectSuggestion(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanelHint(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SearchSuggestionRow(
    suggestion: PlaceSearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = DeepTeal
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (suggestion.description.isNotBlank()) {
                Text(
                    text = suggestion.description,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MapLocationButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppSurface,
            contentColor = DeepTeal
        ),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = Icons.Filled.GpsFixed,
            contentDescription = "回到当前位置",
            tint = DeepTeal
        )
    }
}

@Composable
private fun RouteSwitcher(
    modifier: Modifier = Modifier,
    routes: List<GeneratedRoute>,
    selectedRouteIndex: Int?,
    visibleRouteIndexes: Set<Int>,
    onToggleRoute: (Int) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RouteSwitcherShape,
        color = AppSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.46f))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            routes.forEachIndexed { index, route ->
                val visible = index in visibleRouteIndexes
                val selected = index == selectedRouteIndex
                val color = routeColor(index).toComposeColor()
                Surface(
                    modifier = Modifier
                        .width(RouteSwitcherSegmentWidth)
                        .height(RouteSwitcherSegmentHeight)
                        .semantics {
                            role = Role.Checkbox
                            this.selected = visible
                        }
                        .clickable { onToggleRoute(index) },
                    shape = RouteSwitcherSegmentShape,
                    color = when {
                        selected && visible -> color.copy(alpha = 0.18f)
                        visible -> color.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    },
                    border = BorderStroke(
                        1.dp,
                        if (visible) color.copy(alpha = 0.86f) else Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = route.routeCode,
                            color = if (visible) color else AppTextMuted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteSegmentPopup(
    routeColor: Int,
    payload: RouteSegmentPolylinePayload,
    onClose: () -> Unit
) {
    val color = routeColor.toComposeColor()
    val segment = payload.segment
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${payload.routeCode} 线第 ${segment.order} 段怎么去",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildRouteSegmentTitle(payload),
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭路线段说明",
                        tint = AppTextMuted
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrbanChip(text = formatTransportMode(segment.mode), selected = true)
                UrbanChip(text = "${segment.durationMinutes} 分钟")
                UrbanChip(text = formatDistance(segment.distanceMeters))
                if (payload.isEstimated) {
                    UrbanChip(text = "估算路线")
                }
            }

            if (payload.isEstimated) {
                Text(
                    text = "当前路段没有拿到真实路径规划，地图上以低透明虚线显示估算路线。",
                    color = WarningAmber,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = segment.summary,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segment.steps.take(MAX_VISIBLE_ROUTE_STEPS).forEach { step ->
                    Text(
                        text = "${step.order}. ${step.instruction}",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoiDetailPopup(
    routeCode: String,
    routeColor: Int,
    stop: RouteStop,
    onLocate: () -> Unit,
    onClose: () -> Unit
) {
    val color = routeColor.toComposeColor()
    var selectedImageIndex by remember(stop.id, stop.imageUrls) { mutableStateOf<Int?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = color
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stop.order.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stop.name,
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$routeCode 线 · ${formatStopLabel(stop)}",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭地点详情",
                        tint = AppTextMuted
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stop.rating?.let { rating ->
                    UrbanChip(text = "评分 ${formatRating(rating)}")
                }
                stop.stayMinutes?.let { minutes ->
                    UrbanChip(text = "停留 ${minutes} 分钟", selected = true)
                }
                if (!stop.transportToNext.isNullOrBlank() && stop.durationToNextMinutes != null) {
                    UrbanChip(text = "下一段 ${formatTransportMode(stop.transportToNext)} ${stop.durationToNextMinutes} 分钟")
                }
                stop.distanceToNextMeters?.let { meters ->
                    UrbanChip(text = "距离 ${formatDistance(meters)}")
                }
            }

            if (stop.imageUrls.isNotEmpty()) {
                PoiImageCarousel(
                    poiName = stop.name,
                    imageUrls = stop.imageUrls,
                    onImageClick = { index -> selectedImageIndex = index }
                )
            }

            if (!stop.description.isNullOrBlank()) {
                Text(
                    text = stop.description,
                    color = AppText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!stop.reason.isNullOrBlank()) {
                Text(
                    text = stop.reason,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!stop.riskNote.isNullOrBlank()) {
                Text(
                    text = stop.riskNote,
                    color = RouteTeal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onClick = onLocate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "定位到这个地点", fontWeight = FontWeight.Bold)
            }
        }
    }

    selectedImageIndex?.let { imageIndex ->
        PoiImageDialog(
            imageUrls = stop.imageUrls,
            selectedIndex = imageIndex.coerceIn(stop.imageUrls.indices),
            onSelectIndex = { selectedImageIndex = it },
            onDismiss = { selectedImageIndex = null }
        )
    }
}

@Composable
private fun PoiImageCarousel(
    poiName: String,
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        imageUrls.forEachIndexed { index, imageUrl ->
            RemotePoiImage(
                modifier = Modifier
                    .width(132.dp)
                    .height(86.dp)
                    .clickable { onImageClick(index) },
                imageUrl = imageUrl,
                contentDescription = "$poiName 图片 ${index + 1}"
            )
        }
    }
}

@Composable
private fun RemotePoiImage(
    modifier: Modifier = Modifier,
    imageUrl: String,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = AppSurfaceMuted,
    placeholderTextColor: Color = AppTextMuted
) {
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoadFinished by remember(imageUrl) { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        isLoadFinished = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(imageUrl).openConnection().apply {
                    connectTimeout = IMAGE_CONNECT_TIMEOUT_MILLIS
                    readTimeout = IMAGE_READ_TIMEOUT_MILLIS
                }
                connection.getInputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }.getOrNull()
        }
        isLoadFinished = true
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(placeholderColor)
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale
            )
        } else {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = if (isLoadFinished) "图片加载失败" else "图片加载中",
                color = placeholderTextColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PoiImageDialog(
    imageUrls: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            RemotePoiImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 560.dp)
                    .padding(horizontal = 20.dp),
                imageUrl = imageUrls[selectedIndex],
                contentDescription = "地点图片 ${selectedIndex + 1}",
                contentScale = ContentScale.Fit,
                placeholderColor = Color.Transparent,
                placeholderTextColor = Color.White
            )

            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                onClick = onDismiss
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭大图",
                    tint = Color.White
                )
            }

            if (selectedIndex > 0) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                    onClick = { onSelectIndex(selectedIndex - 1) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一张图片",
                        tint = Color.White
                    )
                }
            }

            if (selectedIndex < imageUrls.lastIndex) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    onClick = { onSelectIndex(selectedIndex + 1) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一张图片",
                        tint = Color.White
                    )
                }
            }

            if (imageUrls.size > 1) {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    text = "${selectedIndex + 1} / ${imageUrls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteDetailSheet(
    route: GeneratedRoute,
    routeIndex: Int,
    routeCount: Int,
    sheetProgress: Float,
    hiddenProgress: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onLocateStop: (RouteStop) -> Unit,
    onAdjustRoute: () -> Unit
) {
    val routeColor = routeColor(routeIndex).toComposeColor()
    val detailHeight = 300.dp * sheetProgress.coerceIn(0f, 1f)
    var sheetHeightPx by remember { mutableStateOf(0f) }
    val hiddenOffsetPx = ((sheetHeightPx - ROUTE_SHEET_PEEK_HANDLE_HEIGHT_PX).coerceAtLeast(0f) *
        hiddenProgress.coerceIn(0f, 1f)).roundToInt()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .onSizeChanged { size -> sheetHeightPx = size.height.toFloat() }
            .offset { IntOffset(x = 0, y = hiddenOffsetPx) }
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RouteSheetHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onDrag = onDrag,
                onDragEnd = onDragEnd
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = route.title,
                        color = AppText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${routeCount} 条路线可切换，当前高亮 ${route.routeCode} 线",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "详情",
                    color = routeColor.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrbanChip(text = formatDuration(route.totalDurationMinutes), selected = true)
                UrbanChip(text = formatDistance(route.totalDistanceMeters))
                UrbanChip(text = formatBudget(route.budgetCent))
                UrbanChip(text = formatRiskLevel(route.riskLevel))
            }

            Text(
                text = route.summary,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )

            if (sheetProgress > 0.02f) {
                Column(
                    modifier = Modifier
                        .height(detailHeight)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    route.stops.sortedBy(RouteStop::order).forEach { stop ->
                        RouteStopDetailRow(
                            stop = stop,
                            routeColor = routeColor,
                            onLocate = { onLocateStop(stop) }
                        )
                    }
                    Text(
                        text = route.explanation,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            UrbanPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "调整路线条件",
                onClick = onAdjustRoute,
            )
        }
    }
}

@Composable
private fun RouteDetailPeekHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 8.dp)
            .height(38.dp)
            .shadow(6.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = AppSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .background(AppBorder, CircleShape)
            )
        }
    }
}

@Composable
private fun RouteSheetHandle(
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = modifier
            .width(64.dp)
            .height(20.dp)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .background(AppBorder, CircleShape)
        )
    }
}

private fun Modifier.routeSheetDragGesture(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return pointerInput(onDrag, onDragEnd) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, drag ->
                change.consume()
                onDrag(drag)
            },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd
        )
    }
}

@Composable
private fun RouteStopDetailRow(
    stop: RouteStop,
    routeColor: Color,
    onLocate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLocate)
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = routeColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stop.order.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stop.name,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatStopLabel(stop),
                color = AppTextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!stop.description.isNullOrBlank()) {
                Text(
                    text = stop.description,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!stop.reason.isNullOrBlank()) {
                Text(
                    text = stop.reason,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val nextText = buildString {
                if (stop.stayMinutes != null) {
                    append("停留 ${stop.stayMinutes} 分钟")
                }
                if (!stop.transportToNext.isNullOrBlank() && stop.durationToNextMinutes != null) {
                    if (isNotEmpty()) {
                        append(" · ")
                    }
                    append("${formatTransportMode(stop.transportToNext)} ${stop.durationToNextMinutes} 分钟")
                }
            }
            if (nextText.isNotBlank()) {
                Text(
                    text = nextText,
                    color = routeColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!stop.riskNote.isNullOrBlank()) {
                Text(
                    text = stop.riskNote,
                    color = RouteTeal,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        IconButton(onClick = onLocate) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "定位到${stop.name}",
                tint = routeColor
            )
        }
    }
}

@Composable
private fun MapHomeActionSheet(onGenerateRoute: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onGenerateRoute,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepTeal,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "生成副本",
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "选择区域后生成今天的城市副本",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MapSelectionSheet(
    onNext: () -> Unit,
    onManualSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "待选择范围",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "确认区域后再进入路线条件配置",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapChip(text = "范围待定")
                MapChip(text = "交通待选")
                MapChip(text = "目标待选")
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onNext,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepTeal,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "下一步配置路线",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                onClick = onManualSelect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DeepTeal
                ),
                border = BorderStroke(1.dp, DeepTeal)
            ) {
                Text(
                    text = "手动框选区域",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MapChip(text: String) {
    Surface(
        shape = CircleShape,
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = text,
            color = AppTextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun GeoPoint.toLatLng(): LatLng {
    return LatLng(latitudeGcj02, longitudeGcj02)
}

private fun List<RouteStop>.toLatLngBounds(): LatLngBounds? {
    if (isEmpty()) {
        return null
    }
    val builder = LatLngBounds.builder()
    forEach { stop -> builder.include(stop.location.toLatLng()) }
    return builder.build()
}

private fun buildEstimatedSegmentPath(
    segment: RouteSegment,
    stopsById: Map<String, RouteStop>
): List<LatLng> {
    val origin = stopsById[segment.originStopId]?.location?.toLatLng()
    val destination = stopsById[segment.destinationStopId]?.location?.toLatLng()
    return listOfNotNull(origin, destination)
}

private fun routeLineColor(index: Int, selected: Boolean, estimated: Boolean): Int {
    val color = routeColor(index)
    val alpha = when {
        estimated -> ROUTE_ESTIMATED_ALPHA
        selected -> ROUTE_SELECTED_ALPHA
        else -> ROUTE_ALTERNATIVE_ALPHA
    }
    return color.withAlpha(alpha)
}

private fun routeLineWidth(selected: Boolean, estimated: Boolean): Float {
    val width = if (selected) ROUTE_SELECTED_WIDTH else ROUTE_ALTERNATIVE_WIDTH
    return if (estimated) width - ROUTE_ESTIMATED_WIDTH_DELTA else width
}

private fun routeLineZIndex(selected: Boolean, estimated: Boolean): Float {
    return when {
        selected && !estimated -> 8f
        selected -> 6f
        !estimated -> 4f
        else -> 3f
    }
}

private fun routeColor(index: Int): Int {
    return when (index % 3) {
        1 -> ROUTE_B_COLOR
        2 -> ROUTE_C_COLOR
        else -> ROUTE_A_COLOR
    }
}

private fun Int.withAlpha(alpha: Int): Int {
    return AndroidColor.argb(
        alpha,
        AndroidColor.red(this),
        AndroidColor.green(this),
        AndroidColor.blue(this)
    )
}

private fun Int.toComposeColor(): Color {
    return Color(this)
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours} 小时 ${restMinutes} 分钟"
        hours > 0 -> "${hours} 小时"
        else -> "${minutes} 分钟"
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${meters / 1000}.${meters % 1000 / 100} 公里"
    } else {
        "${meters} 米"
    }
}

private fun formatBudget(budgetCent: Int?): String {
    return if (budgetCent == null) {
        "预算待定"
    } else {
        "约 ${budgetCent / 100} 元"
    }
}

private fun formatRating(rating: Double): String {
    return String.format("%.1f", rating)
}

private fun formatRiskLevel(riskLevel: String): String {
    return when (riskLevel) {
        "LOW" -> "风险低"
        "MEDIUM" -> "需留意"
        "HIGH" -> "风险高"
        else -> "风险待确认"
    }
}

private fun formatTransportMode(mode: String): String {
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

private fun buildRouteSegmentTitle(payload: RouteSegmentPolylinePayload): String {
    val originName = payload.originStop?.name ?: "上一站"
    val destinationName = payload.destinationStop?.name ?: "下一站"
    return "$originName → $destinationName"
}

private fun formatCategory(category: String?): String {
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

private fun formatStopLabel(stop: RouteStop): String {
    return stop.slotLabel ?: formatCategory(stop.category)
}
