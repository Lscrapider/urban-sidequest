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
import androidx.compose.runtime.DisposableEffect
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
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
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
internal fun MapSelectScreen(
    discoverMapLaunchRequest: DiscoverMapLaunchRequest? = null,
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
    onDiscoverMapLaunchConsumed: () -> Unit = {},
    routeConfigViewModel: RouteConfigViewModel = viewModel(),
    mapSelectViewModel: MapSelectViewModel = viewModel()
) {
    val context = LocalContext.current
    val routeConfigUiState by routeConfigViewModel.uiState.collectAsStateWithLifecycle()
    val mapUiState by mapSelectViewModel.uiState.collectAsStateWithLifecycle()
    val configSubmitScope = rememberCoroutineScope()
    var mapController by remember { mutableStateOf<AMap?>(null) }
    val focusManager = LocalFocusManager.current
    val generationPanelMode = mapUiState.generationPanelMode
    val rangeSelectionMode = mapUiState.rangeSelectionMode
    val generationPanelMessage = mapUiState.generationPanelMessage
    val currentLocation = mapUiState.currentLocation
    val deviceLocation = mapUiState.deviceLocation
    val manualRangeVertices = mapUiState.manualRangeVertices
    val isSearchActive = mapUiState.isSearchActive
    val searchText = mapUiState.searchText
    val searchSuggestions = mapUiState.searchSuggestions
    val isSearching = mapUiState.isSearching
    val selectedRouteIndex = mapUiState.selectedRouteIndex
    val visibleRouteIndexes = mapUiState.visibleRouteIndexes
    val routeSheetProgress = mapUiState.routeSheetProgress
    val routeSheetHiddenProgress = mapUiState.routeSheetHiddenProgress
    val selectedStopPayload = mapUiState.selectedStopPayload
    val selectedSegmentPayload = mapUiState.selectedSegmentPayload
    val completedStopIds = mapUiState.completedStopIds
    val dismissedCheckInStopId = mapUiState.dismissedCheckInStopId
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
        deviceLocation?.let { location -> distanceMeters(location, stop.location.toLatLng()) }
    }
    val shouldShowCheckInPrompt = currentTargetStop != null &&
        distanceToTargetMeters != null &&
        distanceToTargetMeters <= CHECK_IN_RADIUS_METERS &&
        dismissedCheckInStopId != currentTargetStop.id

    DisposableEffect(mapSelectViewModel) {
        onDispose {
            mapSelectViewModel.resetUiState()
        }
    }

    fun resetRouteSheet() {
        mapSelectViewModel.resetRouteSheet()
    }

    fun hideRouteSheet() {
        mapSelectViewModel.hideRouteSheet()
    }

    fun dragRouteSheet(drag: Float) {
        mapSelectViewModel.dragRouteSheet(drag)
    }

    fun settleRouteSheet() {
        mapSelectViewModel.settleRouteSheet()
    }

    fun moveMapCenter(location: LatLng, zoom: Float = 16f) {
        mapSelectViewModel.moveMapCenter(location)
        mapController?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }

    fun updateDeviceLocation(location: LatLng, zoom: Float = 16f) {
        mapSelectViewModel.updateDeviceLocation(location)
        mapController?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }

    fun focusStop(routeIndex: Int, stop: RouteStop) {
        mapSelectViewModel.focusStop(routeIndex, stop)
        moveMapCenter(stop.location.toLatLng(), 17f)
    }

    fun focusSegment(payload: RouteSegmentPolylinePayload) {
        mapSelectViewModel.focusSegment(payload)
    }

    fun checkInStop(stop: RouteStop) {
        val nextCompletedStopIds = completedStopIds + stop.id
        mapSelectViewModel.completeStop(stop)
        if (selectedRouteStops.lastOrNull()?.id == stop.id) {
            routeGeneration?.requestId?.let { requestId ->
                selectedRoute?.routeCode?.let { routeCode ->
                    onCompleteRoute(requestId, routeCode)
                }
            }
        } else {
            val nextStop = selectedRouteStops.firstOrNull { routeStop ->
                routeStop.id != stop.id && routeStop.id !in nextCompletedStopIds
            }
            if (nextStop != null && selectedRoutePosition != null) {
                mapSelectViewModel.selectRoute(selectedRoutePosition)
                moveMapCenter(nextStop.location.toLatLng(), 16f)
            }
        }
    }

    fun requestCurrentLocation() {
        startSingleAmapLocation(
            context = context,
            onLocated = ::updateDeviceLocation
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

    var appliedDiscoverMapLaunchId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(discoverMapLaunchRequest?.launchId) {
        val request = discoverMapLaunchRequest ?: return@LaunchedEffect
        if (appliedDiscoverMapLaunchId != request.launchId) {
            mapSelectViewModel.applyDiscoverMapLaunch(request)
            if (request.shouldApplyRandomPreset) {
                routeConfigViewModel.applyDiscoverRandomPreset()
            }
            appliedDiscoverMapLaunchId = request.launchId
        }
    }

    LaunchedEffect(discoverMapLaunchRequest?.launchId, mapController) {
        val request = discoverMapLaunchRequest ?: return@LaunchedEffect
        val map = mapController ?: return@LaunchedEffect
        val center = LatLng(
            request.anchor.center.latitudeGcj02,
            request.anchor.center.longitudeGcj02
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
        onDiscoverMapLaunchConsumed()
    }

    LaunchedEffect(
        routeGeneration?.requestId,
        isRouteExecutionMode,
        mapUiState.hasExplicitExploreAnchor,
        discoverMapLaunchRequest?.launchId
    ) {
        if ((routeGeneration == null || isRouteExecutionMode) &&
            !mapUiState.hasExplicitExploreAnchor &&
            discoverMapLaunchRequest == null
        ) {
            locateWithPermission()
        }
    }

    LaunchedEffect(routeConfigViewModel) {
        routeConfigViewModel.events.collectLatest { event ->
            when (event) {
                is RouteConfigEvent.RouteGenerationSubmitted -> {
                    mapSelectViewModel.setGenerationPanelMessage(null)
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

    LaunchedEffect(routeGeneration?.requestId, routes.size, initialVisibleRouteCode, activeRouteIndex, isRouteExecutionMode) {
        val initialRouteIndex = initialVisibleRouteCode
            ?.let { routeCode -> routes.indexOfFirst { route -> route.routeCode == routeCode } }
            ?.takeIf { it >= 0 }
            ?: activeRouteIndex
            ?: DEFAULT_VISIBLE_ROUTE_INDEX
        mapSelectViewModel.syncRouteGeneration(
            routeGeneration = routeGeneration,
            routeCount = routes.size,
            initialRouteIndex = initialRouteIndex
        )
    }

    LaunchedEffect(searchText, currentLocation) {
        val keyword = searchText.trim()
        if (!isSearchActive || keyword.length < 2) {
            mapSelectViewModel.clearSearchSuggestions()
            return@LaunchedEffect
        }
        mapSelectViewModel.startSearch()
        delay(250)
        searchAmapInputTips(
            context = context,
            keyword = keyword,
            location = currentLocation,
            onResult = { resultKeyword, suggestions ->
                mapSelectViewModel.applySearchSuggestions(resultKeyword, suggestions)
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
            deviceLocation = deviceLocation,
            routeGeneration = routeGeneration,
            selectedRouteIndex = selectedRouteIndex,
            visibleRouteIndexes = visibleRouteIndexes,
            previewRadiusMeters = previewRadiusMeters,
            rangeSelectionMode = rangeSelectionMode,
            manualRangeVertices = manualRangeVertices,
            onMapReady = { mapController = it },
            onRouteStopClick = { payload ->
                focusStop(payload.routeIndex, payload.stop)
            },
            onRouteSegmentClick = { payload ->
                focusSegment(payload)
            },
            onManualRangeVertexAdded = mapSelectViewModel::addManualRangeVertex
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
            onSearchFocus = mapSelectViewModel::activateSearch,
            onSearchTextChange = mapSelectViewModel::changeSearchText,
            onCancelSearch = {
                mapSelectViewModel.cancelSearch()
                focusManager.clearFocus()
            },
            onSelectSuggestion = { suggestion ->
                moveMapCenter(suggestion.location)
                mapSelectViewModel.selectSuggestion(suggestion)
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
                    mapSelectViewModel.selectRoute(routeIndex)
                }
            )
        }

        if (routes.isEmpty() && !isSearchActive) {
            MapSelectionLockPulse(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isRouteExecutionMode && !isSearchActive) {
            MapExecutionControlStack(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
                onCurrentLocation = ::locateWithPermission,
                onLayers = {},
                onMore = {}
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (!isSearchActive && !isRouteExecutionMode) {
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
                        onClose = mapSelectViewModel::closeSegmentPopup
                    )
                }
                selectedStopPayload?.let { payload ->
                    PoiDetailPopup(
                        routeCode = routes.getOrNull(payload.routeIndex)?.routeCode.orEmpty(),
                        routeColor = routeColor(payload.routeIndex),
                        stop = payload.stop,
                        distanceMetersOverride = if (isRouteExecutionMode) {
                            executionLegDistance(selectedRouteStops, payload.stop)
                        } else {
                            null
                        },
                        showStopDistanceFallback = !isRouteExecutionMode,
                        onLocate = { focusStop(payload.routeIndex, payload.stop) },
                        onClose = mapSelectViewModel::closeStopPopup
                    )
                }
                if (isRouteExecutionMode) {
                    if (currentTargetStop != null) {
                        val finishActiveRoute: () -> Unit = {
                            routeGeneration?.requestId?.let { requestId ->
                                onCompleteRoute(requestId, selectedRoute.routeCode)
                            }
                        }
                        val selectedStop = selectedStopPayload?.stop
                        if (selectedStop != null) {
                            RouteExecutionCompactPanel(
                                route = selectedRoute,
                                currentStop = currentTargetStop,
                                selectedStop = selectedStop,
                                completedStopIds = completedStopIds,
                                distanceMeters = executionLegDistance(selectedRouteStops, selectedStop),
                                durationMinutes = executionLegDuration(selectedRouteStops, selectedStop),
                                canCheckIn = selectedStop.id == currentTargetStop.id && shouldShowCheckInPrompt,
                                onConfirmCheckIn = { checkInStop(selectedStop) },
                                onFinishEarly = finishActiveRoute,
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
                                }
                            )
                        } else {
                            RouteExecutionPanel(
                                route = selectedRoute,
                                stop = currentTargetStop,
                                completedStopIds = completedStopIds,
                                distanceMeters = executionLegDistance(selectedRouteStops, currentTargetStop),
                                durationMinutes = executionLegDuration(selectedRouteStops, currentTargetStop),
                                canCheckIn = shouldShowCheckInPrompt,
                                onShowDetail = { focusStop(selectedRoutePosition, currentTargetStop) },
                                onCheckIn = { checkInStop(currentTargetStop) },
                                onUnableToArrive = { mapSelectViewModel.dismissCheckIn(currentTargetStop.id) },
                                onFinishEarly = finishActiveRoute,
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
                                }
                            )
                        }
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
                            mapSelectViewModel.closeStopPopup()
                            mapSelectViewModel.closeSegmentPopup()
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
                        mapSelectViewModel.showRouteDetailPanel()
                    },
                    onSubmit = {
                        val message = routeConfigUiState.validateMapRouteCondition()
                        mapSelectViewModel.setGenerationPanelMessage(message)
                        if (message == null) {
                            configSubmitScope.launch {
                                routeConfigViewModel.submitRouteGeneration(
                                    routeRepositoryAvailable = routeRepositoryAvailable,
                                    selectedCenter = selectedCenter,
                                    isManualRange = rangeSelectionMode == RangeSelectionMode.Manual,
                                    manualRangeVertices = manualRangeVertices.map { vertex ->
                                        GeoPoint(
                                            longitudeGcj02 = vertex.longitude,
                                            latitudeGcj02 = vertex.latitude
                                        )
                                    },
                                    routeCityInfo = mapUiState.routeCityInfo
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
                                    mapSelectViewModel.showRangePanel()
                                },
                                onSubmit = {
                                    val message = routeConfigUiState.validateMapRouteCondition()
                                    mapSelectViewModel.setGenerationPanelMessage(message)
                                    if (message == null) {
                                        configSubmitScope.launch {
                                            routeConfigViewModel.submitRouteGeneration(
                                                routeRepositoryAvailable = routeRepositoryAvailable,
                                                selectedCenter = selectedCenter,
                                                isManualRange = rangeSelectionMode == RangeSelectionMode.Manual,
                                                manualRangeVertices = manualRangeVertices.map { vertex ->
                                                    GeoPoint(
                                                        longitudeGcj02 = vertex.longitude,
                                                        latitudeGcj02 = vertex.latitude
                                                    )
                                                },
                                                routeCityInfo = mapUiState.routeCityInfo
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
                                manualVertexCount = manualRangeVertices.size,
                                previewAreaText = previewAreaText,
                                message = generationPanelMessage,
                                isSubmitting = isRouteGenerationSubmitting,
                                onOpenConditions = {
                                    if (rangeSelectionMode == RangeSelectionMode.Manual &&
                                        manualRangeVertices.size < MIN_MANUAL_POLYGON_VERTEX_COUNT
                                    ) {
                                        mapSelectViewModel.setGenerationPanelMessage(
                                            "请至少绘制 ${MIN_MANUAL_POLYGON_VERTEX_COUNT} 个顶点"
                                        )
                                    } else {
                                        mapSelectViewModel.showConditionsPanel()
                                    }
                                },
                                onSelectAutoRange = {
                                    mapSelectViewModel.selectAutoRange()
                                },
                                onSelectManualRange = {
                                    mapSelectViewModel.selectManualRange()
                                },
                                onUndoManualPoint = {
                                    mapSelectViewModel.undoManualRangeVertex()
                                },
                                onResetManualRange = {
                                    mapSelectViewModel.resetManualRangeVertices()
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
