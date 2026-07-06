package com.urbansidequest.app.feature.routes

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class RoutesViewModel : ViewModel() {

    private val mutableUiState = MutableStateFlow(RoutesUiState())
    val uiState: StateFlow<RoutesUiState> = mutableUiState.asStateFlow()

    fun resetUiState() {
        mutableUiState.value = RoutesUiState()
    }

    fun selectTab(tab: RouteLibraryTab) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun selectGeneratedFilter(filter: GeneratedRouteFilter) {
        mutableUiState.update { it.copy(generatedFilter = filter) }
    }

    fun openShareDialog(target: WalkedShareTarget) {
        mutableUiState.update {
            it.copy(
                shareTarget = target,
                shareText = DEFAULT_SHARE_TEXT
            )
        }
    }

    fun changeShareText(value: String) {
        mutableUiState.update { it.copy(shareText = value.take(MAX_SHARE_TEXT_LENGTH)) }
    }

    fun dismissShareDialog() {
        mutableUiState.update { it.copy(shareTarget = null) }
    }
}

internal data class RoutesUiState(
    val selectedTab: RouteLibraryTab = RouteLibraryTab.Generated,
    val generatedFilter: GeneratedRouteFilter = GeneratedRouteFilter.All,
    val shareTarget: WalkedShareTarget? = null,
    val shareText: String = DEFAULT_SHARE_TEXT
)

internal data class WalkedShareTarget(
    val requestId: String,
    val routeCode: String,
    val routeTitle: String
)
