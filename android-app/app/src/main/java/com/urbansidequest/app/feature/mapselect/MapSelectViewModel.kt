package com.urbansidequest.app.feature.mapselect

import androidx.lifecycle.ViewModel
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.RouteCityInfo
import com.urbansidequest.app.domain.model.DiscoverAnchorSource
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
import com.urbansidequest.app.domain.model.DiscoverMapRangeMode
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteStop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class MapSelectViewModel : ViewModel() {

    private val mutableUiState = MutableStateFlow(MapSelectUiState())
    val uiState: StateFlow<MapSelectUiState> = mutableUiState.asStateFlow()

    fun resetUiState() {
        mutableUiState.value = MapSelectUiState()
    }

    fun setGenerationPanelMessage(message: String?) {
        mutableUiState.update { it.copy(generationPanelMessage = message) }
    }

    fun showConditionsPanel() {
        mutableUiState.update {
            it.copy(
                generationPanelMode = MapGenerationPanelMode.Conditions,
                generationPanelMessage = null
            )
        }
    }

    fun showRouteDetailPanel() {
        mutableUiState.update {
            it.copy(
                generationPanelMode = MapGenerationPanelMode.RouteDetail,
                generationPanelMessage = null
            )
        }
    }

    fun showRangePanel() {
        mutableUiState.update {
            it.copy(
                generationPanelMode = MapGenerationPanelMode.Range,
                generationPanelMessage = null
            )
        }
    }

    fun selectAutoRange() {
        mutableUiState.update {
            it.copy(
                rangeSelectionMode = RangeSelectionMode.Auto,
                generationPanelMessage = null
            )
        }
    }

    fun selectManualRange(message: String? = null) {
        mutableUiState.update {
            it.copy(
                rangeSelectionMode = RangeSelectionMode.Manual,
                generationPanelMessage = message
            )
        }
    }

    fun applyDiscoverMapLaunch(request: DiscoverMapLaunchRequest) {
        val center = LatLng(
            request.anchor.center.latitudeGcj02,
            request.anchor.center.longitudeGcj02
        )
        mutableUiState.update {
            it.copy(
                generationPanelMode = MapGenerationPanelMode.Range,
                rangeSelectionMode = when (request.rangeMode) {
                    DiscoverMapRangeMode.Auto -> RangeSelectionMode.Auto
                    DiscoverMapRangeMode.Manual -> RangeSelectionMode.Manual
                },
                generationPanelMessage = null,
                currentLocation = center,
                deviceLocation = if (request.anchor.source == DiscoverAnchorSource.DeviceLocation) center else null,
                routeCityInfo = request.anchor.routeCityAdcode
                    ?.let { adcode -> RouteCityInfo(request.anchor.routeCityName, adcode) },
                manualRangeVertices = emptyList(),
                hasExplicitExploreAnchor = true
            )
        }
    }

    fun moveMapCenter(location: LatLng) {
        mutableUiState.update { it.copy(currentLocation = location) }
    }

    fun updateDeviceLocation(location: LatLng) {
        mutableUiState.update { it.copy(currentLocation = location, deviceLocation = location) }
    }

    fun addManualRangeVertex(location: LatLng) {
        mutableUiState.update { state ->
            if (state.rangeSelectionMode != RangeSelectionMode.Manual) {
                state
            } else {
                state.copy(
                    manualRangeVertices = state.manualRangeVertices + location,
                    generationPanelMessage = null
                )
            }
        }
    }

    fun undoManualRangeVertex() {
        mutableUiState.update { state ->
            if (state.manualRangeVertices.isEmpty()) {
                state.copy(generationPanelMessage = "还没有可撤销的手绘顶点")
            } else {
                state.copy(
                    manualRangeVertices = state.manualRangeVertices.dropLast(1),
                    generationPanelMessage = null
                )
            }
        }
    }

    fun resetManualRangeVertices() {
        mutableUiState.update {
            it.copy(manualRangeVertices = emptyList(), generationPanelMessage = null)
        }
    }

    fun activateSearch() {
        mutableUiState.update { it.copy(isSearchActive = true) }
    }

    fun changeSearchText(value: String) {
        mutableUiState.update { it.copy(searchText = value) }
    }

    fun cancelSearch() {
        mutableUiState.update {
            it.copy(
                isSearchActive = false,
                searchText = "",
                searchSuggestions = emptyList(),
                isSearching = false
            )
        }
    }

    fun startSearch() {
        mutableUiState.update { it.copy(isSearching = true) }
    }

    fun clearSearchSuggestions() {
        mutableUiState.update {
            it.copy(
                searchSuggestions = emptyList(),
                isSearching = false
            )
        }
    }

    fun applySearchSuggestions(resultKeyword: String, suggestions: List<PlaceSearchSuggestion>) {
        mutableUiState.update {
            if (resultKeyword == it.searchText.trim()) {
                it.copy(
                    searchSuggestions = suggestions,
                    isSearching = false
                )
            } else {
                it
            }
        }
    }

    fun selectSuggestion(suggestion: PlaceSearchSuggestion) {
        mutableUiState.update {
            it.copy(
                currentLocation = suggestion.location,
                searchText = suggestion.name,
                searchSuggestions = emptyList(),
                isSearchActive = false,
                isSearching = false
            )
        }
    }

    fun selectRoute(routeIndex: Int) {
        mutableUiState.update {
            it.copy(
                selectedRouteIndex = routeIndex,
                visibleRouteIndexes = setOf(routeIndex),
                generationPanelMode = MapGenerationPanelMode.RouteDetail,
                selectedStopPayload = null,
                selectedSegmentPayload = null
            ).resetRouteSheet()
        }
    }

    fun focusStop(routeIndex: Int, stop: RouteStop) {
        mutableUiState.update {
            it.copy(
                selectedRouteIndex = routeIndex,
                visibleRouteIndexes = setOf(routeIndex),
                generationPanelMode = MapGenerationPanelMode.RouteDetail,
                selectedStopPayload = RouteStopMarkerPayload(routeIndex = routeIndex, stop = stop),
                selectedSegmentPayload = null
            ).hideRouteSheet()
        }
    }

    fun focusSegment(payload: RouteSegmentPolylinePayload) {
        mutableUiState.update {
            it.copy(
                selectedRouteIndex = payload.routeIndex,
                visibleRouteIndexes = setOf(payload.routeIndex),
                selectedSegmentPayload = payload,
                selectedStopPayload = null
            ).hideRouteSheet()
        }
    }

    fun closeStopPopup() {
        mutableUiState.update { it.copy(selectedStopPayload = null) }
    }

    fun closeSegmentPopup() {
        mutableUiState.update { it.copy(selectedSegmentPayload = null) }
    }

    fun resetRouteSheet() {
        mutableUiState.update { it.resetRouteSheet() }
    }

    fun hideRouteSheet() {
        mutableUiState.update { it.hideRouteSheet() }
    }

    fun dragRouteSheet(drag: Float) {
        mutableUiState.update { state ->
            var routeSheetProgress = state.routeSheetProgress
            var routeSheetHiddenProgress = state.routeSheetHiddenProgress
            val routeSheetDragOffset = state.routeSheetDragOffset + drag
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
            state.copy(
                routeSheetProgress = routeSheetProgress,
                routeSheetHiddenProgress = routeSheetHiddenProgress,
                routeSheetDragOffset = routeSheetDragOffset
            )
        }
    }

    fun settleRouteSheet() {
        mutableUiState.update { state ->
            when {
                state.routeSheetDragOffset > ROUTE_SHEET_COLLAPSE_DRAG_PX && state.routeSheetProgress > 0.02f -> {
                    state.copy(
                        routeSheetProgress = 0f,
                        routeSheetHiddenProgress = 0f,
                        routeSheetDragOffset = 0f
                    )
                }
                state.routeSheetDragOffset > ROUTE_SHEET_COLLAPSE_DRAG_PX -> {
                    state.copy(
                        routeSheetProgress = 0f,
                        routeSheetHiddenProgress = 1f,
                        routeSheetDragOffset = 0f
                    )
                }
                state.routeSheetDragOffset < -ROUTE_SHEET_COLLAPSE_DRAG_PX && state.routeSheetHiddenProgress > 0f -> {
                    state.copy(
                        routeSheetHiddenProgress = 0f,
                        routeSheetProgress = 0f,
                        routeSheetDragOffset = 0f
                    )
                }
                state.routeSheetHiddenProgress >= ROUTE_SHEET_SNAP_THRESHOLD -> {
                    state.copy(
                        routeSheetHiddenProgress = 1f,
                        routeSheetProgress = 0f,
                        routeSheetDragOffset = 0f
                    )
                }
                state.routeSheetProgress >= ROUTE_SHEET_SNAP_THRESHOLD -> {
                    state.copy(
                        routeSheetHiddenProgress = 0f,
                        routeSheetProgress = 1f,
                        routeSheetDragOffset = 0f
                    )
                }
                else -> state.resetRouteSheet()
            }
        }
    }

    fun completeStop(stop: RouteStop) {
        mutableUiState.update {
            it.copy(
                completedStopIds = it.completedStopIds + stop.id,
                dismissedCheckInStopId = null,
                selectedStopPayload = null,
                selectedSegmentPayload = null
            )
        }
    }

    fun dismissCheckIn(stopId: String) {
        mutableUiState.update { it.copy(dismissedCheckInStopId = stopId) }
    }

    fun syncRouteGeneration(
        routeGeneration: RouteGeneration?,
        routeCount: Int,
        initialRouteIndex: Int?
    ) {
        val routeIdentity = "${routeGeneration?.requestId}:${routeGeneration?.activeRouteCode}"
        mutableUiState.update { state ->
            val shouldResetExecution = state.routeIdentity != routeIdentity
            val resolvedInitialRouteIndex = initialRouteIndex ?: DEFAULT_VISIBLE_ROUTE_INDEX
            val nextState = state.copy(
                routeIdentity = routeIdentity,
                completedStopIds = if (shouldResetExecution) emptySet() else state.completedStopIds,
                dismissedCheckInStopId = if (shouldResetExecution) null else state.dismissedCheckInStopId,
                selectedRouteIndex = if (routeCount > 0) resolvedInitialRouteIndex else null,
                visibleRouteIndexes = if (routeCount > 0) setOf(resolvedInitialRouteIndex) else emptySet(),
                selectedStopPayload = null,
                selectedSegmentPayload = null,
                generationPanelMessage = if (routeGeneration != null) null else state.generationPanelMessage,
                generationPanelMode = when {
                    routeGeneration != null && routeCount == 0 -> MapGenerationPanelMode.Range
                    routeGeneration != null -> MapGenerationPanelMode.RouteDetail
                    state.generationPanelMode == MapGenerationPanelMode.RouteDetail -> MapGenerationPanelMode.Range
                    else -> state.generationPanelMode
                }
            ).resetRouteSheet()
            nextState
        }
    }
}

internal data class MapSelectUiState(
    val generationPanelMode: MapGenerationPanelMode = MapGenerationPanelMode.Range,
    val rangeSelectionMode: RangeSelectionMode = RangeSelectionMode.Auto,
    val generationPanelMessage: String? = null,
    val currentLocation: LatLng = DefaultMapCenter,
    val deviceLocation: LatLng? = null,
    val routeCityInfo: RouteCityInfo? = null,
    val manualRangeVertices: List<LatLng> = emptyList(),
    val hasExplicitExploreAnchor: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchText: String = "",
    val searchSuggestions: List<PlaceSearchSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val selectedRouteIndex: Int? = DEFAULT_VISIBLE_ROUTE_INDEX,
    val visibleRouteIndexes: Set<Int> = setOf(DEFAULT_VISIBLE_ROUTE_INDEX),
    val routeSheetProgress: Float = 0f,
    val routeSheetHiddenProgress: Float = 0f,
    val routeSheetDragOffset: Float = 0f,
    val selectedStopPayload: RouteStopMarkerPayload? = null,
    val selectedSegmentPayload: RouteSegmentPolylinePayload? = null,
    val completedStopIds: Set<String> = emptySet(),
    val dismissedCheckInStopId: String? = null,
    val routeIdentity: String? = null
) {
    fun resetRouteSheet(): MapSelectUiState {
        return copy(
            routeSheetProgress = 0f,
            routeSheetHiddenProgress = 0f,
            routeSheetDragOffset = 0f
        )
    }

    fun hideRouteSheet(): MapSelectUiState {
        return copy(
            routeSheetProgress = 0f,
            routeSheetHiddenProgress = 1f,
            routeSheetDragOffset = 0f
        )
    }
}
