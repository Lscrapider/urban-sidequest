package com.urbansidequest.app

import com.urbansidequest.app.data.api.AuthUserResponse
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.feature.profile.ExplorationPreferenceAnswers
import java.time.LocalDate

internal data class UrbanSidequestUiState(
    val isLoggedIn: Boolean = false,
    val screenStack: List<AppScreen> = listOf(AppScreen.Discover),
    val latestRouteGeneration: RouteGeneration? = null,
    val mapInitialRouteCode: String? = null,
    val routeHistoryGroups: List<RouteHistoryGroup> = emptyList(),
    val isRouteHistoryLoading: Boolean = false,
    val routeHistoryError: String? = null,
    val selectedCenter: GeoPoint? = null,
    val discoverMapLaunchRequest: DiscoverMapLaunchRequest? = null,
    val routeGenerationNotice: RouteGenerationNotice? = null,
    val isRouteGenerationSubmitting: Boolean = false,
    val routeShareNotice: RouteShareNotice? = null,
    val isRouteShareSubmitting: Boolean = false,
    val currentUser: AuthUserResponse? = null,
    val routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    val explorationPreferenceAnswers: ExplorationPreferenceAnswers? = null,
    val explorationStreakDays: Int = 0,
    val lastProfileVisitDate: LocalDate? = null
) {
    fun clearMapSelectionState(): UrbanSidequestUiState {
        return copy(
            latestRouteGeneration = null,
            mapInitialRouteCode = null,
            selectedCenter = null,
            discoverMapLaunchRequest = null
        )
    }

    fun appendScreen(screen: AppScreen): UrbanSidequestUiState {
        return if (screenStack.lastOrNull() == screen) {
            this
        } else {
            copy(screenStack = screenStack + screen)
        }
    }
}
