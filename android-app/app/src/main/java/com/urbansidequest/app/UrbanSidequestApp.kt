package com.urbansidequest.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbansidequest.app.data.api.AuthApi
import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.data.discover.DiscoverRepository
import com.urbansidequest.app.data.route.RouteErrorMapper
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.feature.discover.DiscoverRoute
import com.urbansidequest.app.feature.execution.RouteExecutionScreen
import com.urbansidequest.app.feature.login.LoginRoute
import com.urbansidequest.app.feature.mapselect.MapSelectScreen
import com.urbansidequest.app.feature.poi.PoiExplanationScreen
import com.urbansidequest.app.feature.profile.ProfileQuestionnaireScreen
import com.urbansidequest.app.feature.profile.ProfileScreen
import com.urbansidequest.app.feature.routeconfig.RouteConfigScreen
import com.urbansidequest.app.feature.routes.FavoriteRoutesScreen
import com.urbansidequest.app.feature.routes.RoutesScreen
import com.urbansidequest.app.ui.components.UrbanQuestNoticeOverlay
import com.urbansidequest.app.ui.components.UrbanQuestNoticeTone

@Composable
internal fun UrbanSidequestApp() {
    val context = LocalContext.current.applicationContext
    val authSessionStore = remember { AuthSessionStore(context) }
    val authRepository = remember {
        AuthRepository(
            authApi = AuthApi(),
            authSessionStore = authSessionStore
        )
    }
    val routeRepository = remember {
        RouteRepository(
            routeApi = RouteApi(),
            authSessionStore = authSessionStore
        )
    }
    val discoverRepository = remember {
        DiscoverRepository(
            context = context,
            routeRepository = routeRepository
        )
    }
    val mainViewModel: UrbanSidequestViewModel = viewModel(
        factory = UrbanSidequestViewModelFactory(
            context = context,
            authRepository = authRepository,
            routeRepository = routeRepository
        )
    )
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (uiState.isLoggedIn) {
            BackHandler(enabled = uiState.screenStack.size > 1) {
                mainViewModel.popScreen()
            }
            val currentScreen = uiState.screenStack.last()
            LaunchedEffect(uiState.isLoggedIn) {
                mainViewModel.onLoggedIn()
            }
            LaunchedEffect(currentScreen) {
                mainViewModel.onScreenVisible(currentScreen)
            }
            when (currentScreen) {
                AppScreen.Discover -> DiscoverRoute(
                    discoverRepository = discoverRepository,
                    onOpenSharedRoute = mainViewModel::openSharedRouteOnMap,
                    onAuthenticationExpired = mainViewModel::expireAuth,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenRoutes = { mainViewModel.pushScreen(AppScreen.Routes) },
                    onOpenProfile = { mainViewModel.pushScreen(AppScreen.Profile) }
                )

                AppScreen.Map -> MapSelectScreen(
                    routeGeneration = uiState.latestRouteGeneration,
                    initialVisibleRouteCode = uiState.mapInitialRouteCode,
                    routeInteractions = uiState.routeInteractions,
                    routeInteractionKey = mainViewModel::routeInteractionKey,
                    onToggleRouteFavorite = mainViewModel::toggleRouteFavorite,
                    onReactToRoute = mainViewModel::reactToRoute,
                    onOpenRouteConfig = mainViewModel::openRouteConfig,
                    routeRepositoryAvailable = true,
                    isRouteGenerationSubmitting = uiState.isRouteGenerationSubmitting,
                    onSubmitRouteGeneration = mainViewModel::submitRouteGeneration,
                    onStartRoute = mainViewModel::startRoute,
                    onCompleteRoute = mainViewModel::completeActiveRoute,
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenRoutes = mainViewModel::openRoutesFromMap,
                    onOpenProfile = mainViewModel::openProfileFromMap
                )

                AppScreen.RouteConfig -> RouteConfigScreen(
                    routeRepository = routeRepository,
                    selectedCenter = uiState.selectedCenter,
                    onBack = mainViewModel::popScreen,
                    onSubmitRouteGeneration = mainViewModel::submitRouteGeneration
                )

                AppScreen.Routes -> RoutesScreen(
                    historyGroups = uiState.routeHistoryGroups,
                    isLoading = uiState.isRouteHistoryLoading,
                    errorMessage = uiState.routeHistoryError,
                    onOpenHistoryGroup = { requestId -> mainViewModel.openHistoryOnMap(requestId, null) },
                    onOpenHistoryRoute = { requestId, routeCode -> mainViewModel.openHistoryOnMap(requestId, routeCode) },
                    onShareWalkedRoute = mainViewModel::shareCompletedRoute,
                    onRefreshHistory = mainViewModel::requestRouteHistoryRefresh,
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenProfile = { mainViewModel.pushScreen(AppScreen.Profile) }
                )

                AppScreen.FavoriteRoutes -> FavoriteRoutesScreen(
                    historyGroups = uiState.routeHistoryGroups,
                    routeInteractions = uiState.routeInteractions,
                    routeInteractionKey = mainViewModel::routeInteractionKey,
                    isLoading = uiState.isRouteHistoryLoading,
                    errorMessage = uiState.routeHistoryError,
                    onOpenFavoriteRoute = { requestId, routeCode -> mainViewModel.openHistoryOnMap(requestId, routeCode) },
                    onRefreshHistory = mainViewModel::requestRouteHistoryRefresh,
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenRoutes = { mainViewModel.pushScreen(AppScreen.Routes) },
                    onOpenProfile = { mainViewModel.pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteExecution -> RouteExecutionScreen(
                    onBackToRoutes = mainViewModel::popScreen,
                    onOpenPoi = { mainViewModel.pushScreen(AppScreen.PoiExplanation) },
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenProfile = { mainViewModel.pushScreen(AppScreen.Profile) }
                )

                AppScreen.PoiExplanation -> PoiExplanationScreen(
                    onBack = mainViewModel::popScreen,
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenRoutes = { mainViewModel.pushScreen(AppScreen.Routes) },
                    onOpenProfile = { mainViewModel.pushScreen(AppScreen.Profile) }
                )

                AppScreen.Profile -> ProfileScreen(
                    nickname = uiState.currentUser?.nickname.orEmpty(),
                    avatarUrl = uiState.currentUser?.avatarUrl.orEmpty(),
                    completedRouteCount = uiState.currentUser?.completedRouteCount ?: 0,
                    travelDistanceMeters = uiState.currentUser?.travelDistanceMeters ?: 0L,
                    routeInteractions = uiState.routeInteractions,
                    explorationStreakDays = uiState.explorationStreakDays,
                    preferenceAnswers = uiState.explorationPreferenceAnswers,
                    onAvatarSelected = mainViewModel::uploadProfileAvatar,
                    onOpenQuestionnaire = { mainViewModel.pushScreen(AppScreen.ProfileQuestionnaire) },
                    onOpenFavoriteRoutes = { mainViewModel.pushScreen(AppScreen.FavoriteRoutes) },
                    onOpenDiscover = mainViewModel::replaceWithDiscover,
                    onOpenMap = mainViewModel::openMapWithActiveRouteFallback,
                    onOpenRoutes = { mainViewModel.pushScreen(AppScreen.Routes) },
                    onLogout = mainViewModel::expireAuth
                )

                AppScreen.ProfileQuestionnaire -> ProfileQuestionnaireScreen(
                    answers = uiState.explorationPreferenceAnswers,
                    onBack = mainViewModel::popScreen,
                    onSave = mainViewModel::saveProfileQuestionnaire
                )
            }
            uiState.routeGenerationNotice?.let { notice ->
                RouteGenerationNoticeOverlay(
                    notice = notice,
                    onDismiss = mainViewModel::dismissRouteGenerationNotice,
                    onOpenRoutes = mainViewModel::openRoutesFromGenerationNotice,
                    onOpenRoute = mainViewModel::openGeneratedRouteNotice
                )
            }
            uiState.routeShareNotice?.let { notice ->
                RouteShareNoticeOverlay(
                    notice = notice,
                    onDismiss = mainViewModel::dismissRouteShareNotice
                )
            }
        } else {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = mainViewModel::onLoginSuccess
            )
        }
    }
}

@Composable
private fun RouteGenerationNoticeOverlay(
    notice: RouteGenerationNotice,
    onDismiss: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRoute: (RouteGeneration) -> Unit
) {
    val spec = notice.toNoticeSpec()
    UrbanQuestNoticeOverlay(
        visible = true,
        title = spec.title,
        message = spec.message,
        tone = spec.tone,
        actionText = spec.actionText,
        onAction = when (notice) {
            is RouteGenerationNotice.Completed -> {
                { onOpenRoute(notice.routeGeneration) }
            }
            RouteGenerationNotice.Submitted -> {
                { onOpenRoutes() }
            }
            RouteGenerationNotice.Failed -> null
        },
        autoDismissMillis = spec.autoDismissMillis,
        onDismiss = onDismiss
    )
}

@Composable
private fun RouteShareNoticeOverlay(
    notice: RouteShareNotice,
    onDismiss: () -> Unit
) {
    val spec = notice.toNoticeSpec()
    UrbanQuestNoticeOverlay(
        visible = true,
        title = spec.title,
        message = spec.message,
        tone = spec.tone,
        autoDismissMillis = spec.autoDismissMillis,
        onDismiss = onDismiss
    )
}

private fun RouteShareNotice.toNoticeSpec(): RouteNoticeSpec {
    return when (this) {
        RouteShareNotice.Submitting -> RouteNoticeSpec(
            title = "正在生成分享图",
            message = "完成后会同步到发现页。",
            tone = UrbanQuestNoticeTone.Info,
            autoDismissMillis = null
        )
        RouteShareNotice.Completed -> RouteNoticeSpec(
            title = "路线已分享",
            message = "图片和文字已保存到发现页。",
            tone = UrbanQuestNoticeTone.Success,
            autoDismissMillis = ROUTE_NOTICE_DISMISS_MS
        )
        RouteShareNotice.Failed -> RouteNoticeSpec(
            title = "路线分享失败",
            message = "分享图没有生成成功，请稍后重试。",
            tone = UrbanQuestNoticeTone.Error,
            autoDismissMillis = ROUTE_NOTICE_DISMISS_MS
        )
    }
}

private fun RouteGenerationNotice.toNoticeSpec(): RouteNoticeSpec {
    return when (this) {
        RouteGenerationNotice.Submitted -> RouteNoticeSpec(
            title = "路线生成已提交",
            message = "正在装载路线，也可以去路线库查看进度。",
            actionText = "路线库",
            tone = UrbanQuestNoticeTone.Info,
            autoDismissMillis = null
        )
        is RouteGenerationNotice.Completed -> RouteNoticeSpec(
            title = "今日路线已生成",
            message = "已保存到路线库，可直接打开地图查看。",
            actionText = "查看",
            tone = UrbanQuestNoticeTone.Success,
            autoDismissMillis = ROUTE_NOTICE_DISMISS_MS
        )
        RouteGenerationNotice.Failed -> RouteNoticeSpec(
            title = "路线生成失败",
            message = RouteErrorMapper.ROUTE_GENERATION_FAILED_MESSAGE,
            tone = UrbanQuestNoticeTone.Error,
            autoDismissMillis = ROUTE_NOTICE_DISMISS_MS
        )
    }
}

private data class RouteNoticeSpec(
    val title: String,
    val message: String,
    val tone: UrbanQuestNoticeTone,
    val actionText: String? = null,
    val autoDismissMillis: Long? = ROUTE_NOTICE_DISMISS_MS
)

private const val ROUTE_NOTICE_DISMISS_MS = 2400L
