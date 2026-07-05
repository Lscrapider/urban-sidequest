package com.urbansidequest.app.feature.mapselect

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.searchAmapInputTips
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.feature.routeconfig.RouteConfigEvent
import com.urbansidequest.app.feature.routeconfig.RouteConfigViewModel
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanMotion
import com.urbansidequest.app.ui.components.urbanMotionDuration
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.sin

@Composable
fun MapSelectScreen(
    routeGeneration: RouteGeneration? = null,
    initialVisibleRouteCode: String? = null,
    routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    routeInteractionKey: (String, String) -> String = { candidateSetId, routeCode -> "$candidateSetId:$routeCode" },
    onToggleRouteFavorite: (String, String, String) -> Unit = { _, _, _ -> },
    onReactToRoute: (String, String, String, RouteReaction) -> Unit = { _, _, _, _ -> },
    onOpenRouteConfig: (GeoPoint) -> Unit = {},
    routeRepositoryAvailable: Boolean = true,
    isRouteGenerationSubmitting: Boolean = false,
    onSubmitRouteGeneration: (RouteGenerateRequest) -> Unit = {},
    onStartRoute: (String, String) -> Unit = { _, _ -> },
    onCompleteRoute: (String, String) -> Unit = { _, _ -> },
    onOpenDiscover: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    routeConfigViewModel: RouteConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val routeConfigUiState by routeConfigViewModel.uiState.collectAsStateWithLifecycle()
    val configSubmitScope = rememberCoroutineScope()
    var generationPanelMode by remember { mutableStateOf(MapGenerationPanelMode.Range) }
    var rangeSelectionMode by remember { mutableStateOf(RangeSelectionMode.Auto) }
    var generationPanelMessage by remember { mutableStateOf<String?>(null) }
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
    var completedStopIds by remember(routeGeneration?.requestId, routeGeneration?.activeRouteCode) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var dismissedCheckInStopId by remember(routeGeneration?.requestId, routeGeneration?.activeRouteCode) {
        mutableStateOf<String?>(null)
    }
    val focusManager = LocalFocusManager.current
    val routes = routeGeneration?.routes.orEmpty()
    val selectedCenter = GeoPoint(
        longitudeGcj02 = currentLocation.longitude,
        latitudeGcj02 = currentLocation.latitude
    )
    val previewRadiusMeters = remember(routeConfigUiState.selectedTransport, routeConfigUiState.selectedDuration) {
        previewSearchRadiusMeters(routeConfigUiState)
    }
    val previewAreaText = remember(previewRadiusMeters) { formatPreviewArea(previewRadiusMeters) }
    val activeRouteIndex = routeGeneration?.activeRouteCode
        ?.let { activeRouteCode -> routes.indexOfFirst { route -> route.routeCode == activeRouteCode } }
        ?.takeIf { it >= 0 }
    val isRouteExecutionMode = routeGeneration?.executionStatus == "IN_PROGRESS" && activeRouteIndex != null
    val selectedRoutePosition = if (isRouteExecutionMode) activeRouteIndex else selectedRouteIndex
    val selectedRoute = selectedRoutePosition?.let { routes.getOrNull(it) }
    val selectedRouteStops = selectedRoute?.stops.orEmpty().sortedBy(RouteStop::order)
    val currentTargetStop = if (isRouteExecutionMode) {
        selectedRouteStops.firstOrNull { stop -> stop.id !in completedStopIds }
    } else {
        null
    }
    val distanceToTargetMeters = currentTargetStop?.let { stop ->
        distanceMeters(currentLocation, stop.location.toLatLng())
    }
    val shouldShowCheckInPrompt = currentTargetStop != null &&
        distanceToTargetMeters != null &&
        distanceToTargetMeters <= CHECK_IN_RADIUS_METERS &&
        dismissedCheckInStopId != currentTargetStop.id

    fun resetRouteSheet() {
        routeSheetProgress = 0f
        routeSheetHiddenProgress = 0f
        routeSheetDragOffset = 0f
    }

    fun hideRouteSheet() {
        routeSheetProgress = 0f
        routeSheetHiddenProgress = 1f
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
        visibleRouteIndexes = setOf(routeIndex)
        generationPanelMode = MapGenerationPanelMode.RouteDetail
        selectedStopPayload = RouteStopMarkerPayload(routeIndex = routeIndex, stop = stop)
        selectedSegmentPayload = null
        hideRouteSheet()
        moveToLocation(stop.location.toLatLng(), 17f)
    }

    fun focusSegment(payload: RouteSegmentPolylinePayload) {
        selectedRouteIndex = payload.routeIndex
        visibleRouteIndexes = setOf(payload.routeIndex)
        selectedSegmentPayload = payload
        selectedStopPayload = null
        hideRouteSheet()
    }

    fun checkInStop(stop: RouteStop) {
        completedStopIds = completedStopIds + stop.id
        dismissedCheckInStopId = null
        if (selectedRouteStops.lastOrNull()?.id == stop.id) {
            routeGeneration?.requestId?.let { requestId ->
                selectedRoute?.routeCode?.let { routeCode ->
                    onCompleteRoute(requestId, routeCode)
                }
            }
        } else {
            val nextStop = selectedRouteStops.firstOrNull { routeStop ->
                routeStop.id != stop.id && routeStop.id !in completedStopIds
            }
            if (nextStop != null && selectedRoutePosition != null) {
                focusStop(selectedRoutePosition, nextStop)
            }
        }
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

    LaunchedEffect(routeGeneration?.requestId, isRouteExecutionMode) {
        if (routeGeneration == null || isRouteExecutionMode) {
            locateWithPermission()
        }
    }

    LaunchedEffect(routeConfigViewModel) {
        routeConfigViewModel.events.collectLatest { event ->
            when (event) {
                is RouteConfigEvent.RouteGenerationSubmitted -> {
                    generationPanelMessage = null
                    onSubmitRouteGeneration(event.request)
                }
            }
        }
    }

    LaunchedEffect(routeConfigUiState.mustVisitSearchText, selectedCenter.longitudeGcj02, selectedCenter.latitudeGcj02, generationPanelMode) {
        if (generationPanelMode == MapGenerationPanelMode.Conditions) {
            routeConfigViewModel.searchMustVisitSuggestions(
                context = context,
                selectedCenter = selectedCenter
            )
        }
    }

    LaunchedEffect(isRouteExecutionMode, currentTargetStop?.id) {
        if (!isRouteExecutionMode || currentTargetStop == null) {
            return@LaunchedEffect
        }
        // 开发期间不启用
        while (false) {
            if (context.hasLocationPermission()) {
                requestCurrentLocation()
            }
            delay(ROUTE_LOCATION_REFRESH_MILLIS)
        }
    }

    LaunchedEffect(routeGeneration?.requestId, routes.size, initialVisibleRouteCode, activeRouteIndex, isRouteExecutionMode) {
        val initialRouteIndex = initialVisibleRouteCode
            ?.let { routeCode -> routes.indexOfFirst { route -> route.routeCode == routeCode } }
            ?.takeIf { it >= 0 }
            ?: activeRouteIndex
            ?: DEFAULT_VISIBLE_ROUTE_INDEX
        selectedRouteIndex = if (routes.isNotEmpty()) initialRouteIndex else null
        visibleRouteIndexes = if (routes.isNotEmpty()) setOf(initialRouteIndex) else emptySet()
        resetRouteSheet()
        selectedStopPayload = null
        selectedSegmentPayload = null
        if (routeGeneration != null) {
            generationPanelMode = if (routes.isEmpty()) {
                MapGenerationPanelMode.Range
            } else {
                MapGenerationPanelMode.RouteDetail
            }
            generationPanelMessage = null
        } else if (generationPanelMode == MapGenerationPanelMode.RouteDetail) {
            generationPanelMode = MapGenerationPanelMode.Range
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
            previewRadiusMeters = previewRadiusMeters,
            rangeSelectionMode = rangeSelectionMode,
            onMapReady = { mapController = it },
            onRouteStopClick = { payload ->
                focusStop(payload.routeIndex, payload.stop)
            },
            onRouteSegmentClick = { payload ->
                focusSegment(payload)
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

        if (routes.isEmpty() && !isSearchActive) {
            MapShortcutRow(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 72.dp, end = 20.dp),
                onOpenRoutes = onOpenRoutes,
                onOpenFavorites = onOpenRoutes
            )
        }

        if (routes.isNotEmpty() && !isSearchActive && !isRouteExecutionMode) {
            RouteSwitcher(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp),
                routes = routes,
                selectedRouteIndex = selectedRouteIndex,
                visibleRouteIndexes = visibleRouteIndexes,
                onSelectRoute = { routeIndex ->
                    visibleRouteIndexes = setOf(routeIndex)
                    selectedRouteIndex = routeIndex
                    generationPanelMode = MapGenerationPanelMode.RouteDetail
                    resetRouteSheet()
                    selectedStopPayload = null
                    selectedSegmentPayload = null
                }
            )
        }

        if (routes.isEmpty() && !isSearchActive) {
            MapSelectionLockPulse(
                modifier = Modifier.align(Alignment.Center)
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

            if (selectedRoute != null && (isRouteExecutionMode ||
                    generationPanelMode == MapGenerationPanelMode.RouteDetail)
            ) {
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
                if (isRouteExecutionMode) {
                    if (currentTargetStop != null) {
                        RouteCheckInPrompt(
                            route = selectedRoute,
                            stop = currentTargetStop,
                            completedCount = completedStopIds.size,
                            totalCount = selectedRouteStops.size,
                            distanceMeters = distanceToTargetMeters,
                            canCheckIn = shouldShowCheckInPrompt,
                            onCheckIn = { checkInStop(currentTargetStop) },
                            onDismiss = { dismissedCheckInStopId = currentTargetStop.id }
                        )
                    } else {
                        RouteCompletionPendingPanel(route = selectedRoute)
                    }
                } else if (routeSheetHiddenProgress >= 0.99f) {
                    RouteDetailCollapsedStrip(
                        route = selectedRoute,
                        routeColor = routeColor(selectedRoutePosition).toComposeColor(),
                        selectedStopId = selectedStopPayload?.stop?.id,
                        onSelectStop = { stop -> focusStop(selectedRoutePosition, stop) },
                        onSelectSegment = { originStop, destinationStop ->
                            focusSegment(
                                buildRailSegmentPayload(
                                    routeIndex = selectedRoutePosition,
                                    route = selectedRoute,
                                    originStop = originStop,
                                    destinationStop = destinationStop
                                )
                            )
                        },
                        onExpand = {
                            selectedStopPayload = null
                            selectedSegmentPayload = null
                            resetRouteSheet()
                        },
                        onDrag = { drag -> dragRouteSheet(drag) },
                        onDragEnd = { settleRouteSheet() }
                    )
                } else {
                    RouteDetailSheet(
                        route = selectedRoute,
                        routeIndex = selectedRoutePosition,
                        routeCount = routes.size,
                        isRouteCompleted = routeGeneration?.executionStatus == "COMPLETED",
                        interaction = routeGeneration?.candidateSetId
                            ?.let { candidateSetId -> routeInteractions[routeInteractionKey(candidateSetId, selectedRoute.routeCode)] }
                            ?: RouteInteractionState(),
                        sheetProgress = routeSheetProgress,
                        hiddenProgress = routeSheetHiddenProgress,
                        onDrag = { drag -> dragRouteSheet(drag) },
                        onDragEnd = { settleRouteSheet() },
                        onLocateStop = { stop -> focusStop(selectedRoutePosition, stop) },
                        onLocateSegment = ::focusSegment,
                        onStartRoute = {
                            routeGeneration?.requestId?.let { requestId ->
                                onStartRoute(requestId, selectedRoute.routeCode)
                            } ?: onOpenRoutes()
                        },
                        onToggleFavorite = {
                            routeGeneration?.let { generation ->
                                onToggleRouteFavorite(generation.requestId, generation.candidateSetId, selectedRoute.routeCode)
                            }
                        },
                        onReact = { reaction ->
                            routeGeneration?.let { generation ->
                                onReactToRoute(generation.requestId, generation.candidateSetId, selectedRoute.routeCode, reaction)
                            }
                        }
                    )
                }
            } else if (routes.isNotEmpty() && !isRouteExecutionMode && generationPanelMode == MapGenerationPanelMode.Conditions) {
                RouteGenerationConditionSheet(
                    uiState = routeConfigUiState,
                    selectedCenter = selectedCenter,
                    previewAreaText = previewAreaText,
                    message = generationPanelMessage,
                    isSubmitting = isRouteGenerationSubmitting,
                    routeConfigViewModel = routeConfigViewModel,
                    onClose = {
                        generationPanelMode = MapGenerationPanelMode.RouteDetail
                        generationPanelMessage = null
                    },
                    onSubmit = {
                        generationPanelMessage = routeConfigUiState.validateMapRouteCondition()
                        if (generationPanelMessage == null) {
                            configSubmitScope.launch {
                                routeConfigViewModel.submitRouteGeneration(
                                    routeRepositoryAvailable = routeRepositoryAvailable,
                                    selectedCenter = selectedCenter
                                )
                            }
                        }
                    }
                )
            } else {
                AnimatedVisibility(
                    visible = !isSearchActive,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = urbanMotionDuration(UrbanMotion.SheetMillis),
                            easing = FastOutSlowInEasing
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = urbanMotionDuration(UrbanMotion.SheetMillis),
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetY = { height -> height / 4 }
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = urbanMotionDuration(UrbanMotion.SheetMillis),
                            easing = FastOutSlowInEasing
                        )
                    ) + slideOutVertically(
                        animationSpec = tween(
                            durationMillis = urbanMotionDuration(UrbanMotion.SheetMillis),
                            easing = FastOutSlowInEasing
                        ),
                        targetOffsetY = { height -> height / 4 }
                    )
                ) {
                    when (generationPanelMode) {
                        MapGenerationPanelMode.Conditions -> {
                            RouteGenerationConditionSheet(
                                uiState = routeConfigUiState,
                                selectedCenter = selectedCenter,
                                previewAreaText = previewAreaText,
                                message = generationPanelMessage,
                                isSubmitting = isRouteGenerationSubmitting,
                                routeConfigViewModel = routeConfigViewModel,
                                onClose = {
                                    generationPanelMode = MapGenerationPanelMode.Range
                                    generationPanelMessage = null
                                },
                                onSubmit = {
                                    generationPanelMessage = routeConfigUiState.validateMapRouteCondition()
                                    if (generationPanelMessage == null) {
                                        configSubmitScope.launch {
                                            routeConfigViewModel.submitRouteGeneration(
                                                routeRepositoryAvailable = routeRepositoryAvailable,
                                                selectedCenter = selectedCenter
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        else -> {
                            MapRangeSheet(
                                uiState = routeConfigUiState,
                                rangeSelectionMode = rangeSelectionMode,
                                previewAreaText = previewAreaText,
                                message = generationPanelMessage,
                                isSubmitting = isRouteGenerationSubmitting,
                                onOpenConditions = {
                                    generationPanelMode = MapGenerationPanelMode.Conditions
                                    generationPanelMessage = null
                                },
                                onSelectAutoRange = {
                                    rangeSelectionMode = RangeSelectionMode.Auto
                                    generationPanelMessage = null
                                },
                                onSelectManualRange = {
                                    rangeSelectionMode = RangeSelectionMode.Manual
                                    generationPanelMessage = null
                                },
                                onUndoManualPoint = {
                                    rangeSelectionMode = RangeSelectionMode.Manual
                                    generationPanelMessage = "手绘点位调整稍后接入，当前展示预览范围。"
                                },
                                onResetManualRange = {
                                    rangeSelectionMode = RangeSelectionMode.Manual
                                    generationPanelMessage = "已重置手绘预览范围。"
                                }
                            )
                        }
                    }
                }
            }
            UrbanBottomNavigationBar(
                selectedDestination = UrbanDestination.Map,
                onDiscoverClick = onOpenDiscover,
                onMapClick = {},
                onRoutesClick = onOpenRoutes,
                onProfileClick = onOpenProfile
            )
        }
    }
}
