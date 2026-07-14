package com.urbansidequest.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbansidequest.app.data.discover.DiscoverRepository
import com.urbansidequest.app.data.route.RouteErrorMapper
import com.urbansidequest.app.domain.model.DiscoverAnchor
import com.urbansidequest.app.domain.model.DiscoverCityWeather
import com.urbansidequest.app.domain.model.DiscoverExploreAction
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
import com.urbansidequest.app.domain.model.DiscoverMapRangeMode
import com.urbansidequest.app.domain.model.DiscoverRegion
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteShare
import com.urbansidequest.app.domain.model.onlyRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<DiscoverEvent>()
    val events: SharedFlow<DiscoverEvent> = mutableEvents.asSharedFlow()

    private var initialized = false
    private var isResolvingDeviceAnchor = false
    private var shouldClearManualAnchorAfterPermissionGrant = false
    private var shouldRefreshLocationPermissionAfterSettings = false

    fun initialize(hasLocationPermission: Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        refreshRouteShares()
        val savedManualAnchor = discoverRepository.loadSavedManualAnchor()
        when {
            savedManualAnchor != null -> applyAnchor(savedManualAnchor)
            hasLocationPermission -> resolveDeviceAnchor()
            else -> mutableUiState.update {
                it.copy(
                    showLocationPermissionPrompt = true,
                    isLocationPermissionDenied = false
                )
            }
        }
    }

    fun refreshRouteShares() {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isRouteSharesLoading = true,
                    routeSharesError = null,
                    openShareError = null
                )
            }
            runCatching {
                discoverRepository.fetchRouteShares()
            }.onSuccess { shares ->
                mutableUiState.update {
                    it.copy(
                        routeShares = shares,
                        isRouteSharesLoading = false,
                        routeSharesError = null,
                        openShareError = null
                    )
                }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    mutableUiState.update { it.copy(isRouteSharesLoading = false) }
                    mutableEvents.emit(DiscoverEvent.AuthenticationExpired)
                } else {
                    mutableUiState.update {
                        it.copy(
                            isRouteSharesLoading = false,
                            routeSharesError = "路线分享加载失败，请稍后重试"
                        )
                    }
                }
            }
        }
    }

    fun openCitySwitcher() {
        mutableUiState.update { it.copy(isCitySwitcherVisible = true) }
    }

    fun dismissCitySwitcher() {
        mutableUiState.update { it.copy(isCitySwitcherVisible = false) }
    }

    fun useCurrentLocation(hasLocationPermission: Boolean) {
        mutableUiState.update { it.copy(isCitySwitcherVisible = false) }
        if (hasLocationPermission) {
            resolveDeviceAnchor(clearManualAnchorOnSuccess = true)
        } else {
            shouldClearManualAnchorAfterPermissionGrant = true
            mutableUiState.update {
                it.copy(
                    showLocationPermissionPrompt = true,
                    isLocationPermissionRequestPending = false
                )
            }
        }
    }

    fun requestLocationPermission() {
        mutableUiState.update {
            it.copy(
                showLocationPermissionPrompt = false,
                isLocationPermissionRequestPending = true
            )
        }
    }

    fun openLocationSettings() {
        shouldRefreshLocationPermissionAfterSettings = true
        mutableUiState.update {
            it.copy(
                showLocationPermissionPrompt = false,
                isLocationPermissionRequestPending = false
            )
        }
    }

    fun onLocationSettingsReturned(hasLocationPermission: Boolean) {
        if (!shouldRefreshLocationPermissionAfterSettings) {
            return
        }
        shouldRefreshLocationPermissionAfterSettings = false
        onLocationPermissionResult(
            granted = hasLocationPermission,
            permanentlyDenied = !hasLocationPermission
        )
    }

    fun onLocationPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        val clearManualAnchorOnSuccess = shouldClearManualAnchorAfterPermissionGrant
        shouldClearManualAnchorAfterPermissionGrant = false
        shouldRefreshLocationPermissionAfterSettings = false
        if (granted) {
            mutableUiState.update {
                it.copy(
                    showLocationPermissionPrompt = false,
                    isLocationPermissionPermanentlyDenied = false,
                    isLocationPermissionDenied = false,
                    isLocationPermissionRequestPending = false
                )
            }
            resolveDeviceAnchor(clearManualAnchorOnSuccess)
        } else {
            mutableUiState.update {
                it.copy(
                    showLocationPermissionPrompt = true,
                    isLocationPermissionPermanentlyDenied = permanentlyDenied,
                    isLocationPermissionDenied = true,
                    isLocationPermissionRequestPending = false
                )
            }
        }
    }

    fun dismissLocationPermissionPrompt() {
        shouldClearManualAnchorAfterPermissionGrant = false
        shouldRefreshLocationPermissionAfterSettings = false
        mutableUiState.update {
            it.copy(
                showLocationPermissionPrompt = false,
                pendingExploreAction = null,
                isLocationPermissionDenied = true,
                isLocationPermissionRequestPending = false
            )
        }
    }

    fun openRegionPicker() {
        shouldClearManualAnchorAfterPermissionGrant = false
        shouldRefreshLocationPermissionAfterSettings = false
        mutableUiState.update {
            it.copy(
                showLocationPermissionPrompt = false,
                isCitySwitcherVisible = false,
                isLocationPermissionRequestPending = false,
                isRegionPickerVisible = true,
                regionPath = emptyList(),
                regions = emptyList(),
                regionError = null
            )
        }
        loadRegions(parentAdcode = null, nextPath = emptyList())
    }

    fun dismissRegionPicker() {
        mutableUiState.update { it.copy(isRegionPickerVisible = false) }
    }

    fun openRegion(region: DiscoverRegion) {
        if (!region.hasChildren) {
            selectRegion(region)
            return
        }
        val currentPath = mutableUiState.value.regionPath
        loadRegions(
            parentAdcode = region.adcode,
            nextPath = currentPath + region,
            fallbackRegion = region
        )
    }

    fun navigateUpRegion() {
        val currentPath = mutableUiState.value.regionPath
        if (currentPath.isEmpty()) {
            return
        }
        val nextPath = currentPath.dropLast(1)
        loadRegions(
            parentAdcode = nextPath.lastOrNull()?.adcode,
            nextPath = nextPath
        )
    }

    fun selectCurrentRegion() {
        mutableUiState.value.regionPath.lastOrNull()?.let(::selectRegion)
    }

    fun onExploreAction(action: DiscoverExploreAction, hasLocationPermission: Boolean) {
        val anchor = mutableUiState.value.selectedAnchor
        if (action == DiscoverExploreAction.StartFromCurrent && hasLocationPermission) {
            mutableUiState.update { it.copy(pendingExploreAction = action) }
            resolveDeviceAnchor()
            return
        }
        if (anchor != null) {
            openExploreMap(action, anchor)
            return
        }
        if (hasLocationPermission) {
            mutableUiState.update { it.copy(pendingExploreAction = action) }
            resolveDeviceAnchor()
        } else {
            mutableUiState.update {
                it.copy(
                    pendingExploreAction = action,
                    showLocationPermissionPrompt = true
                )
            }
        }
    }

    fun openShare(share: RouteShare) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(openShareError = null) }
            runCatching {
                discoverRepository.fetchSharedRoute(share.shareId)
            }.onSuccess { routeGeneration ->
                mutableEvents.emit(
                    DiscoverEvent.OpenSharedRoute(
                        routeGeneration = routeGeneration.onlyRoute(share.routeCode),
                        routeCode = share.routeCode
                    )
                )
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    mutableEvents.emit(DiscoverEvent.AuthenticationExpired)
                } else {
                    mutableUiState.update {
                        it.copy(openShareError = "分享路线打开失败，请稍后重试")
                    }
                }
            }
        }
    }

    private fun resolveDeviceAnchor(clearManualAnchorOnSuccess: Boolean = false) {
        if (isResolvingDeviceAnchor) {
            return
        }
        isResolvingDeviceAnchor = true
        viewModelScope.launch {
            mutableUiState.update { it.copy(isAnchorLoading = true, anchorError = null) }
            runCatching {
                discoverRepository.locateDeviceAnchor()
            }.onSuccess { anchor ->
                if (clearManualAnchorOnSuccess) {
                    discoverRepository.clearSavedManualAnchor()
                }
                applyAnchor(anchor)
                mutableUiState.value.pendingExploreAction?.let { action ->
                    openExploreMap(action, anchor)
                }
            }.onFailure {
                val fallbackAnchor = mutableUiState.value.selectedAnchor
                val pendingAction = mutableUiState.value.pendingExploreAction
                if (fallbackAnchor != null && pendingAction != null) {
                    openExploreMap(pendingAction, fallbackAnchor)
                } else {
                    mutableUiState.update {
                        it.copy(anchorError = "暂时无法定位，请开启定位后重试或手动选择地区")
                    }
                }
            }
            mutableUiState.update { it.copy(isAnchorLoading = false) }
            isResolvingDeviceAnchor = false
        }
    }

    private fun loadRegions(
        parentAdcode: String?,
        nextPath: List<DiscoverRegion>,
        fallbackRegion: DiscoverRegion? = null
    ) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRegionsLoading = true, regionError = null) }
            runCatching {
                discoverRepository.fetchRegions(parentAdcode)
            }.onSuccess { regions ->
                if (regions.isEmpty() && fallbackRegion != null) {
                    selectRegion(fallbackRegion)
                } else {
                    mutableUiState.update {
                        it.copy(
                            regionPath = nextPath,
                            regions = regions,
                            isRegionsLoading = false
                        )
                    }
                }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    mutableEvents.emit(DiscoverEvent.AuthenticationExpired)
                } else {
                    mutableUiState.update {
                        it.copy(
                            isRegionsLoading = false,
                            regionError = "地区数据加载失败，请稍后重试"
                        )
                    }
                }
            }
        }
    }

    fun selectRegion(region: DiscoverRegion) {
        if (!region.selectable) {
            return
        }
        val anchor = region.toAnchor()
        discoverRepository.saveManualAnchor(anchor)
        applyAnchor(anchor)
        mutableUiState.value.pendingExploreAction?.let { action ->
            openExploreMap(action, anchor)
        }
    }

    private fun applyAnchor(anchor: DiscoverAnchor) {
        mutableUiState.update {
            it.copy(
                selectedAnchor = anchor,
                isRegionPickerVisible = false,
                isRegionsLoading = false,
                showLocationPermissionPrompt = false,
                isCitySwitcherVisible = false,
                isLocationPermissionDenied = false,
                isLocationPermissionRequestPending = false,
                anchorError = null
            )
        }
        loadWeather(anchor)
    }

    private fun loadWeather(anchor: DiscoverAnchor) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isWeatherLoading = true) }
            runCatching {
                discoverRepository.loadCityWeather(anchor)
            }.onSuccess { weather ->
                mutableUiState.update { state ->
                    if (state.selectedAnchor?.weatherCacheKey == anchor.weatherCacheKey) {
                        state.copy(cityWeather = weather, isWeatherLoading = false)
                    } else {
                        state
                    }
                }
            }.onFailure {
                mutableUiState.update { state ->
                    if (state.selectedAnchor?.weatherCacheKey == anchor.weatherCacheKey) {
                        state.copy(isWeatherLoading = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun openExploreMap(action: DiscoverExploreAction, anchor: DiscoverAnchor) {
        val request = when (action) {
            DiscoverExploreAction.StartFromCurrent -> DiscoverMapLaunchRequest(
                anchor = anchor,
                rangeMode = DiscoverMapRangeMode.Auto,
                shouldApplyRandomPreset = false
            )
            DiscoverExploreAction.ManualDraw -> DiscoverMapLaunchRequest(
                anchor = anchor,
                rangeMode = DiscoverMapRangeMode.Manual,
                shouldApplyRandomPreset = false
            )
            DiscoverExploreAction.RandomExplore -> DiscoverMapLaunchRequest(
                anchor = anchor,
                rangeMode = DiscoverMapRangeMode.Auto,
                shouldApplyRandomPreset = true
            )
        }
        mutableUiState.update { it.copy(pendingExploreAction = null) }
        viewModelScope.launch {
            mutableEvents.emit(DiscoverEvent.OpenExploreMap(request))
        }
    }
}

data class DiscoverUiState(
    val cityWeather: DiscoverCityWeather = DiscoverCityWeather(),
    val selectedAnchor: DiscoverAnchor? = null,
    val isAnchorLoading: Boolean = false,
    val anchorError: String? = null,
    val isWeatherLoading: Boolean = false,
    val showLocationPermissionPrompt: Boolean = false,
    val isLocationPermissionRequestPending: Boolean = false,
    val isLocationPermissionPermanentlyDenied: Boolean = false,
    val isLocationPermissionDenied: Boolean = false,
    val pendingExploreAction: DiscoverExploreAction? = null,
    val isCitySwitcherVisible: Boolean = false,
    val isRegionPickerVisible: Boolean = false,
    val regions: List<DiscoverRegion> = emptyList(),
    val regionPath: List<DiscoverRegion> = emptyList(),
    val isRegionsLoading: Boolean = false,
    val regionError: String? = null,
    val routeShares: List<RouteShare> = emptyList(),
    val isRouteSharesLoading: Boolean = false,
    val routeSharesError: String? = null,
    val openShareError: String? = null
)

sealed interface DiscoverEvent {
    data class OpenSharedRoute(
        val routeGeneration: RouteGeneration,
        val routeCode: String
    ) : DiscoverEvent

    data class OpenExploreMap(val request: DiscoverMapLaunchRequest) : DiscoverEvent

    data object AuthenticationExpired : DiscoverEvent
}

class DiscoverViewModelFactory(
    private val discoverRepository: DiscoverRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiscoverViewModel::class.java)) {
            return DiscoverViewModel(discoverRepository) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类型: ${modelClass.name}")
    }
}
