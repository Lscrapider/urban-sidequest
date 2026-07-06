package com.urbansidequest.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbansidequest.app.data.discover.DiscoverRepository
import com.urbansidequest.app.data.route.RouteErrorMapper
import com.urbansidequest.app.domain.model.DiscoverCityWeather
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

    fun refreshCityWeather() {
        viewModelScope.launch {
            runCatching {
                discoverRepository.loadCityWeather()
            }.onSuccess { cityWeather ->
                mutableUiState.update { it.copy(cityWeather = cityWeather) }
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
}

data class DiscoverUiState(
    val cityWeather: DiscoverCityWeather = DiscoverCityWeather(),
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
