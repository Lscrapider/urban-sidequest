package com.urbansidequest.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.map.resolveRouteCityInfo
import com.urbansidequest.app.data.profile.AVATAR_JPEG_CONTENT_TYPE
import com.urbansidequest.app.data.profile.ProfileAvatarImageEncoder
import com.urbansidequest.app.data.route.RouteErrorMapper
import com.urbansidequest.app.data.route.RouteExecutionProgressKey
import com.urbansidequest.app.data.route.RouteExecutionProgressStore
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.onlyRoute
import com.urbansidequest.app.feature.profile.ExplorationPreferenceAnswers
import com.urbansidequest.app.feature.profile.ExplorationPreferenceStore
import com.urbansidequest.app.feature.profile.ExplorationStreakStore
import com.urbansidequest.app.feature.profile.toUserPreferenceProfileOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

internal class UrbanSidequestViewModel(
    context: Context,
    private val authRepository: AuthRepository,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private companion object {
        private const val ROUTE_HISTORY_PAGE_SIZE = 20
    }

    private val appContext = context.applicationContext
    private val explorationPreferenceStore = ExplorationPreferenceStore(appContext)
    private val explorationStreakStore = ExplorationStreakStore(appContext)
    private val routeExecutionProgressStore = RouteExecutionProgressStore(appContext)
    private val mutableUiState = MutableStateFlow(
        UrbanSidequestUiState(isLoggedIn = authRepository.isLoggedIn())
    )
    val uiState: StateFlow<UrbanSidequestUiState> = mutableUiState.asStateFlow()

    fun pushScreen(screen: AppScreen) {
        mutableUiState.update { state ->
            if (state.screenStack.lastOrNull() == screen) {
                state
            } else {
                state.copy(screenStack = state.screenStack + screen)
            }
        }
    }

    fun popScreen() {
        mutableUiState.update { state ->
            if (state.screenStack.size > 1) {
                state.copy(screenStack = state.screenStack.dropLast(1))
            } else {
                state
            }
        }
    }

    fun replaceWithDiscover() {
        mutableUiState.update {
            it.clearMapSelectionState().copy(screenStack = listOf(AppScreen.Discover))
        }
    }

    fun expireAuth() {
        authRepository.clearSession()
        mutableUiState.update {
            it.clearMapSelectionState().copy(
                routeHistoryGroups = emptyList(),
                isRouteHistoryLoading = false,
                isRouteHistoryLoadingMore = false,
                hasMoreRouteHistory = false,
                nextRouteHistoryPage = FIRST_ROUTE_HISTORY_PAGE,
                routeShareNotice = null,
                currentUser = null,
                routeInteractions = emptyMap(),
                explorationPreferenceAnswers = null,
                explorationStreakDays = 0,
                lastProfileVisitDate = null,
                screenStack = listOf(AppScreen.Discover),
                isLoggedIn = false
            )
        }
    }

    fun onLoginSuccess() {
        mutableUiState.update {
            it.copy(
                isLoggedIn = true,
                screenStack = listOf(AppScreen.Discover)
            )
        }
    }

    fun onLoggedIn() {
        restoreExplorationPreferenceAnswers()
        restoreExplorationStreak()
        viewModelScope.launch {
            refreshCurrentUser()
            refreshRouteInteractions()
        }
    }

    fun onScreenVisible(screen: AppScreen) {
        viewModelScope.launch {
            if (screen == AppScreen.Routes || screen == AppScreen.FavoriteRoutes || screen == AppScreen.Profile) {
                refreshRouteHistory()
                refreshRouteInteractions()
            }
            if (screen == AppScreen.Profile) {
                refreshCurrentUser()
                markProfileVisited()
            }
        }
    }

    fun openRouteConfig(center: GeoPoint) {
        mutableUiState.update {
            it.copy(
                selectedCenter = center,
                screenStack = it.screenStack + AppScreen.RouteConfig
            )
        }
    }

    fun openRoutesFromMap() {
        mutableUiState.update {
            it.clearMapSelectionState().appendScreen(AppScreen.Routes)
        }
    }

    fun openProfileFromMap() {
        mutableUiState.update {
            it.clearMapSelectionState().appendScreen(AppScreen.Profile)
        }
    }

    fun routeInteractionKey(candidateSetId: String, routeCode: String): String {
        return "$candidateSetId:$routeCode"
    }

    fun toggleRouteFavorite(requestId: String, candidateSetId: String, routeCode: String) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        val current = mutableUiState.value.routeInteractions[key] ?: RouteInteractionState()
        val nextFavorite = !current.isFavorite
        val next = current.copy(
            isFavorite = nextFavorite,
            reaction = if (nextFavorite && current.reaction == RouteReaction.Disliked) null else current.reaction
        )
        mutableUiState.update { state ->
            state.copy(routeInteractions = state.routeInteractions + (key to next))
        }
        saveRouteInteraction(requestId, candidateSetId, routeCode, next)
    }

    fun reactToRoute(requestId: String, candidateSetId: String, routeCode: String, reaction: RouteReaction) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        val current = mutableUiState.value.routeInteractions[key] ?: RouteInteractionState()
        val nextReaction = if (current.reaction == reaction) null else reaction
        val next = current.copy(
            isFavorite = if (nextReaction == RouteReaction.Disliked) false else current.isFavorite,
            reaction = nextReaction
        )
        mutableUiState.update { state ->
            state.copy(routeInteractions = state.routeInteractions + (key to next))
        }
        saveRouteInteraction(requestId, candidateSetId, routeCode, next)
    }

    fun openMapWithActiveRouteFallback() {
        viewModelScope.launch {
            runCatching {
                routeRepository.fetchActiveRoute()
            }.onSuccess { activeRoute ->
                mutableUiState.update { state ->
                    if (activeRoute == null) {
                        state.clearMapSelectionState().copy(screenStack = listOf(AppScreen.Map))
                    } else {
                        state.copy(
                            latestRouteGeneration = activeRoute,
                            mapInitialRouteCode = activeRoute.activeRouteCode,
                            screenStack = listOf(AppScreen.Map)
                        )
                    }
                }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    mutableUiState.update {
                        it.clearMapSelectionState().copy(screenStack = listOf(AppScreen.Map))
                    }
                }
            }
        }
    }

    fun openDiscoverExploreMap(request: DiscoverMapLaunchRequest) {
        mutableUiState.update { state ->
            state.clearMapSelectionState().copy(
                discoverMapLaunchRequest = request,
                screenStack = listOf(AppScreen.Map)
            )
        }
    }

    fun consumeDiscoverMapLaunchRequest() {
        mutableUiState.update { it.copy(discoverMapLaunchRequest = null) }
    }

    fun requestRouteHistoryRefresh() {
        viewModelScope.launch {
            refreshRouteHistory()
        }
    }

    fun requestMoreRouteHistory() {
        viewModelScope.launch {
            loadMoreRouteHistory()
        }
    }

    fun openHistoryOnMap(requestId: String, routeCode: String?) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(routeHistoryError = null) }
            runCatching {
                routeRepository.fetchRouteHistoryDetail(requestId)
            }.onSuccess { routeGeneration ->
                mutableUiState.update {
                    it.copy(
                        latestRouteGeneration = routeGeneration.onlyRoute(routeCode),
                        mapInitialRouteCode = routeCode,
                        screenStack = listOf(AppScreen.Map)
                    )
                }
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun openSharedRouteOnMap(routeGeneration: RouteGeneration, routeCode: String) {
        mutableUiState.update {
            it.copy(
                latestRouteGeneration = routeGeneration,
                mapInitialRouteCode = routeCode,
                screenStack = listOf(AppScreen.Map)
            )
        }
    }

    fun shareCompletedRoute(requestId: String, routeCode: String, shareText: String) {
        if (mutableUiState.value.isRouteShareSubmitting) {
            return
        }
        mutableUiState.update { it.copy(routeShareNotice = RouteShareNotice.Submitting) }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRouteShareSubmitting = true) }
            runCatching {
                routeRepository.shareCompletedRoute(requestId, routeCode, shareText)
            }.onSuccess {
                mutableUiState.update { it.copy(routeShareNotice = RouteShareNotice.Completed) }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    mutableUiState.update { it.copy(routeShareNotice = RouteShareNotice.Failed) }
                }
            }
            mutableUiState.update { it.copy(isRouteShareSubmitting = false) }
        }
    }

    fun startRoute(requestId: String, routeCode: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(routeHistoryError = null) }
            runCatching {
                routeRepository.activateRoute(requestId, routeCode)
            }.onSuccess { routeGeneration ->
                mutableUiState.update {
                    it.copy(
                        latestRouteGeneration = routeGeneration,
                        mapInitialRouteCode = routeCode,
                        screenStack = listOf(AppScreen.Map)
                    )
                }
                refreshRouteHistory()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun completeActiveRoute(requestId: String, routeCode: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(routeHistoryError = null) }
            runCatching {
                routeRepository.completeActiveRoute(requestId, routeCode)
            }.onSuccess { routeGeneration ->
                routeExecutionProgressStore.clear(RouteExecutionProgressKey(requestId, routeCode))
                mutableUiState.update {
                    it.copy(
                        latestRouteGeneration = routeGeneration,
                        mapInitialRouteCode = routeCode
                    )
                }
                refreshRouteHistory()
                refreshCurrentUser()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun submitRouteGeneration(request: RouteGenerateRequest) {
        if (mutableUiState.value.isRouteGenerationSubmitting) {
            return
        }
        mutableUiState.update { it.copy(routeGenerationNotice = RouteGenerationNotice.Submitted) }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRouteGenerationSubmitting = true) }
            runCatching {
                routeRepository.generateRoute(enrichRouteGenerationRequest(request))
            }.onSuccess {
                mutableUiState.update {
                    it.copy(
                        routeGenerationNotice = RouteGenerationNotice.Submitted
                    )
                }
                refreshRouteHistory()
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    mutableUiState.update { it.copy(routeGenerationNotice = RouteGenerationNotice.Failed) }
                }
            }
            mutableUiState.update { it.copy(isRouteGenerationSubmitting = false) }
        }
    }

    fun uploadProfileAvatar(avatarUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val avatarBytes = ProfileAvatarImageEncoder.encodeJpeg(appContext, avatarUri)
                authRepository.uploadAvatar(
                    imageBytes = avatarBytes,
                    contentType = AVATAR_JPEG_CONTENT_TYPE
                )
            }.onSuccess { user ->
                mutableUiState.update { it.copy(currentUser = user) }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                }
            }
        }
    }

    fun saveProfileQuestionnaire(answers: ExplorationPreferenceAnswers) {
        authRepository.currentUserId()?.let { userId ->
            explorationPreferenceStore.save(userId, answers)
        }
        mutableUiState.update {
            it.copy(
                explorationPreferenceAnswers = answers,
                screenStack = if (it.screenStack.size > 1) it.screenStack.dropLast(1) else it.screenStack
            )
        }
    }

    fun dismissRouteGenerationNotice() {
        mutableUiState.update { it.copy(routeGenerationNotice = null) }
    }

    fun openRoutesFromGenerationNotice() {
        mutableUiState.update {
            it.clearMapSelectionState().copy(
                routeGenerationNotice = null,
                screenStack = it.screenStack + AppScreen.Routes
            )
        }
    }

    fun openGeneratedRouteNotice(routeGeneration: RouteGeneration) {
        mutableUiState.update {
            it.copy(
                routeGenerationNotice = null,
                latestRouteGeneration = routeGeneration,
                mapInitialRouteCode = null,
                screenStack = listOf(AppScreen.Map)
            )
        }
    }

    fun dismissRouteShareNotice() {
        mutableUiState.update { it.copy(routeShareNotice = null) }
    }

    private fun saveRouteInteraction(
        requestId: String,
        candidateSetId: String,
        routeCode: String,
        interaction: RouteInteractionState
    ) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        viewModelScope.launch {
            runCatching {
                routeRepository.saveRouteInteraction(requestId, routeCode, interaction)
            }.onSuccess { savedInteraction ->
                mutableUiState.update { state ->
                    state.copy(routeInteractions = state.routeInteractions + (key to savedInteraction))
                }
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    mutableUiState.update {
                        it.copy(routeHistoryError = "路线互动状态保存失败，请稍后重试")
                    }
                }
            }
        }
    }

    private fun markProfileVisited() {
        val today = LocalDate.now(PROFILE_ZONE)
        val state = mutableUiState.value
        if (state.lastProfileVisitDate == today) {
            return
        }
        val nextStreakDays = if (state.lastProfileVisitDate == today.minusDays(1)) {
            state.explorationStreakDays + 1
        } else {
            1
        }
        mutableUiState.update {
            it.copy(
                explorationStreakDays = nextStreakDays,
                lastProfileVisitDate = today
            )
        }
        authRepository.currentUserId()?.let { userId ->
            explorationStreakStore.save(
                userId = userId,
                streakDays = nextStreakDays,
                lastVisitDate = today
            )
        }
    }

    private fun handleRouteFailure(throwable: Throwable, fallbackMessage: String) {
        if (RouteErrorMapper.isAuthenticationError(throwable)) {
            expireAuth()
        } else {
            mutableUiState.update { it.copy(routeHistoryError = fallbackMessage) }
        }
    }

    private suspend fun enrichRouteGenerationRequest(request: RouteGenerateRequest): RouteGenerateRequest {
        val requestWithCity = if (!request.routeCityName.isNullOrBlank() || !request.routeCityAdcode.isNullOrBlank()) {
            request
        } else {
            val routeCityInfo = resolveRouteCityInfo(
                context = appContext,
                location = LatLng(request.center.latitudeGcj02, request.center.longitudeGcj02)
            )
            request.copy(
                routeCityName = routeCityInfo?.cityName,
                routeCityAdcode = routeCityInfo?.cityAdcode
            )
        }
        return requestWithCity.copy(
            userPreferenceProfileOverride = mutableUiState.value.explorationPreferenceAnswers
                .toUserPreferenceProfileOverride()
                ?: requestWithCity.userPreferenceProfileOverride
        )
    }

    private fun restoreExplorationPreferenceAnswers() {
        val userId = authRepository.currentUserId() ?: return
        mutableUiState.update { state ->
            state.copy(explorationPreferenceAnswers = explorationPreferenceStore.read(userId))
        }
    }

    private fun restoreExplorationStreak() {
        val userId = authRepository.currentUserId() ?: return
        val progress = explorationStreakStore.read(userId)
        mutableUiState.update { state ->
            state.copy(
                explorationStreakDays = progress.streakDays,
                lastProfileVisitDate = progress.lastVisitDate
            )
        }
    }

    private suspend fun refreshRouteHistory() {
        if (mutableUiState.value.isRouteHistoryLoading) {
            return
        }
        mutableUiState.update {
            it.copy(
                isRouteHistoryLoading = true,
                routeHistoryError = null
            )
        }
        runCatching {
            routeRepository.fetchRouteHistory(
                pageNum = FIRST_ROUTE_HISTORY_PAGE,
                pageSize = ROUTE_HISTORY_PAGE_SIZE
            )
        }.onSuccess { groups ->
            mutableUiState.update {
                it.copy(
                    routeHistoryGroups = groups.distinctBy { group -> group.requestId },
                    hasMoreRouteHistory = groups.size >= ROUTE_HISTORY_PAGE_SIZE,
                    nextRouteHistoryPage = FIRST_ROUTE_HISTORY_PAGE + 1
                )
            }
        }.onFailure { throwable ->
            handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
        }
        mutableUiState.update { it.copy(isRouteHistoryLoading = false) }
    }

    private suspend fun loadMoreRouteHistory() {
        val currentState = mutableUiState.value
        if (
            currentState.isRouteHistoryLoading ||
            currentState.isRouteHistoryLoadingMore ||
            !currentState.hasMoreRouteHistory
        ) {
            return
        }
        val nextPage = currentState.nextRouteHistoryPage
        mutableUiState.update {
            it.copy(
                isRouteHistoryLoadingMore = true,
                routeHistoryError = null
            )
        }
        runCatching {
            routeRepository.fetchRouteHistory(
                pageNum = nextPage,
                pageSize = ROUTE_HISTORY_PAGE_SIZE
            )
        }.onSuccess { groups ->
            mutableUiState.update { state ->
                state.copy(
                    routeHistoryGroups = (state.routeHistoryGroups + groups)
                        .distinctBy { group -> group.requestId },
                    hasMoreRouteHistory = groups.size >= ROUTE_HISTORY_PAGE_SIZE,
                    nextRouteHistoryPage = nextPage + 1
                )
            }
        }.onFailure { throwable ->
            handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
        }
        mutableUiState.update { it.copy(isRouteHistoryLoadingMore = false) }
    }

    private suspend fun refreshRouteInteractions() {
        runCatching {
            routeRepository.fetchRouteInteractions()
        }.onSuccess { interactions ->
            mutableUiState.update { it.copy(routeInteractions = interactions) }
        }.onFailure { throwable ->
            if (RouteErrorMapper.isAuthenticationError(throwable)) {
                expireAuth()
            }
        }
    }

    private suspend fun refreshCurrentUser() {
        runCatching {
            authRepository.fetchCurrentUser()
        }.onSuccess { user ->
            mutableUiState.update { it.copy(currentUser = user) }
        }.onFailure { throwable ->
            if (RouteErrorMapper.isAuthenticationError(throwable)) {
                expireAuth()
            }
        }
    }
}

private val PROFILE_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
