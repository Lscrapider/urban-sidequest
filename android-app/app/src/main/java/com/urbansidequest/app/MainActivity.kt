package com.urbansidequest.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.AuthApi
import com.urbansidequest.app.data.api.AuthUserResponse
import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.data.map.resolveRouteCityInfo
import com.urbansidequest.app.data.route.RouteErrorMapper
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.RouteShare
import com.urbansidequest.app.feature.discover.DiscoverScreen
import com.urbansidequest.app.feature.execution.RouteExecutionScreen
import com.urbansidequest.app.feature.login.LoginRoute
import com.urbansidequest.app.feature.mapselect.MapSelectScreen
import com.urbansidequest.app.feature.poi.PoiExplanationScreen
import com.urbansidequest.app.feature.profile.ExplorationPreferenceAnswers
import com.urbansidequest.app.feature.profile.ProfileQuestionnaireScreen
import com.urbansidequest.app.feature.profile.ProfileScreen
import com.urbansidequest.app.feature.routeconfig.RouteConfigScreen
import com.urbansidequest.app.feature.routes.FavoriteRoutesScreen
import com.urbansidequest.app.feature.routes.RoutesScreen
import com.urbansidequest.app.ui.components.UrbanQuestNoticeOverlay
import com.urbansidequest.app.ui.components.UrbanQuestNoticeTone
import com.urbansidequest.app.ui.theme.UrbanSidequestTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UrbanSidequestTheme {
                UrbanSidequestApp()
            }
        }
    }
}

@Composable
private fun UrbanSidequestApp() {
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
    val routeActionScope = rememberCoroutineScope()
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var screenStack by remember { mutableStateOf(listOf(AppScreen.Discover)) }
    var latestRouteGeneration by remember { mutableStateOf<RouteGeneration?>(null) }
    var mapInitialRouteCode by remember { mutableStateOf<String?>(null) }
    var routeHistoryGroups by remember { mutableStateOf<List<RouteHistoryGroup>>(emptyList()) }
    var isRouteHistoryLoading by remember { mutableStateOf(false) }
    var routeHistoryError by remember { mutableStateOf<String?>(null) }
    var routeShares by remember { mutableStateOf<List<RouteShare>>(emptyList()) }
    var isRouteSharesLoading by remember { mutableStateOf(false) }
    var routeSharesError by remember { mutableStateOf<String?>(null) }
    var selectedCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var routeGenerationNotice by remember { mutableStateOf<RouteGenerationNotice?>(null) }
    var isRouteGenerationSubmitting by remember { mutableStateOf(false) }
    var routeShareNotice by remember { mutableStateOf<RouteShareNotice?>(null) }
    var isRouteShareSubmitting by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<AuthUserResponse?>(null) }
    var routeInteractions by remember { mutableStateOf<Map<String, RouteInteractionState>>(emptyMap()) }
    var explorationPreferenceAnswers by remember { mutableStateOf<ExplorationPreferenceAnswers?>(null) }
    var explorationStreakDays by remember { mutableStateOf(0) }
    var lastProfileVisitDate by remember { mutableStateOf<LocalDate?>(null) }

    fun pushScreen(screen: AppScreen) {
        screenStack = if (screenStack.lastOrNull() == screen) {
            screenStack
        } else {
            screenStack + screen
        }
    }

    fun replaceWithMap() {
        screenStack = listOf(AppScreen.Map)
    }

    fun clearMapSelectionState() {
        latestRouteGeneration = null
        mapInitialRouteCode = null
        selectedCenter = null
    }

    fun replaceWithFreshMap() {
        clearMapSelectionState()
        replaceWithMap()
    }

    fun replaceWithDiscover() {
        clearMapSelectionState()
        screenStack = listOf(AppScreen.Discover)
    }

    fun popScreen() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        }
    }

    fun expireAuth() {
        authRepository.clearSession()
        clearMapSelectionState()
        routeHistoryGroups = emptyList()
        routeShares = emptyList()
        routeSharesError = null
        routeShareNotice = null
        currentUser = null
        routeInteractions = emptyMap()
        explorationPreferenceAnswers = null
        explorationStreakDays = 0
        lastProfileVisitDate = null
        screenStack = listOf(AppScreen.Discover)
        isLoggedIn = false
    }

    fun routeInteractionKey(candidateSetId: String, routeCode: String): String {
        return "$candidateSetId:$routeCode"
    }

    fun saveRouteInteraction(requestId: String, candidateSetId: String, routeCode: String, interaction: RouteInteractionState) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        routeActionScope.launch {
            runCatching {
                routeRepository.saveRouteInteraction(requestId, routeCode, interaction)
            }.onSuccess { savedInteraction ->
                routeInteractions = routeInteractions + (key to savedInteraction)
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    routeHistoryError = "路线互动状态保存失败，请稍后重试"
                }
            }
        }
    }

    fun toggleRouteFavorite(requestId: String, candidateSetId: String, routeCode: String) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        val current = routeInteractions[key] ?: RouteInteractionState()
        val nextFavorite = !current.isFavorite
        val next = current.copy(
            isFavorite = nextFavorite,
            reaction = if (nextFavorite && current.reaction == RouteReaction.Disliked) null else current.reaction
        )
        routeInteractions = routeInteractions + (key to next)
        saveRouteInteraction(requestId, candidateSetId, routeCode, next)
    }

    fun reactToRoute(requestId: String, candidateSetId: String, routeCode: String, reaction: RouteReaction) {
        val key = routeInteractionKey(candidateSetId, routeCode)
        val current = routeInteractions[key] ?: RouteInteractionState()
        val nextReaction = if (current.reaction == reaction) null else reaction
        val next = current.copy(
            isFavorite = if (nextReaction == RouteReaction.Disliked) false else current.isFavorite,
            reaction = nextReaction
        )
        routeInteractions = routeInteractions + (key to next)
        saveRouteInteraction(requestId, candidateSetId, routeCode, next)
    }

    fun markProfileVisited() {
        val today = LocalDate.now(PROFILE_ZONE)
        if (lastProfileVisitDate == today) {
            return
        }
        explorationStreakDays = if (lastProfileVisitDate == today.minusDays(1)) {
            explorationStreakDays + 1
        } else {
            1
        }
        lastProfileVisitDate = today
    }

    fun openMapWithActiveRouteFallback() {
        routeActionScope.launch {
            runCatching {
                routeRepository.fetchActiveRoute()
            }.onSuccess { activeRoute ->
                if (activeRoute == null) {
                    clearMapSelectionState()
                } else {
                    latestRouteGeneration = activeRoute
                    mapInitialRouteCode = activeRoute.activeRouteCode
                }
                replaceWithMap()
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    clearMapSelectionState()
                    replaceWithMap()
                }
            }
        }
    }

    fun handleRouteFailure(throwable: Throwable, fallbackMessage: String) {
        if (RouteErrorMapper.isAuthenticationError(throwable)) {
            expireAuth()
        } else {
            routeHistoryError = fallbackMessage
        }
    }

    suspend fun enrichRouteCityInfo(request: RouteGenerateRequest): RouteGenerateRequest {
        val routeCityInfo = resolveRouteCityInfo(
            context = context,
            location = LatLng(request.center.latitudeGcj02, request.center.longitudeGcj02)
        )
        return request.copy(
            routeCityName = routeCityInfo?.cityName,
            routeCityAdcode = routeCityInfo?.cityAdcode
        )
    }

    suspend fun refreshRouteHistory() {
        isRouteHistoryLoading = true
        routeHistoryError = null
        runCatching {
            routeRepository.fetchRouteHistory()
        }.onSuccess { groups ->
            routeHistoryGroups = groups
        }.onFailure { throwable ->
            handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
        }
        isRouteHistoryLoading = false
    }

    suspend fun refreshRouteInteractions() {
        runCatching {
            routeRepository.fetchRouteInteractions()
        }.onSuccess { interactions ->
            routeInteractions = interactions
        }.onFailure { throwable ->
            if (RouteErrorMapper.isAuthenticationError(throwable)) {
                expireAuth()
            }
        }
    }

    suspend fun refreshRouteShares() {
        isRouteSharesLoading = true
        routeSharesError = null
        runCatching {
            routeRepository.fetchRouteShares()
        }.onSuccess { shares ->
            routeShares = shares
        }.onFailure { throwable ->
            if (RouteErrorMapper.isAuthenticationError(throwable)) {
                expireAuth()
            } else {
                routeSharesError = "路线分享加载失败，请稍后重试"
            }
        }
        isRouteSharesLoading = false
    }

    suspend fun refreshCurrentUser() {
        runCatching {
            authRepository.fetchCurrentUser()
        }.onSuccess { user ->
            currentUser = user
        }.onFailure { throwable ->
            if (RouteErrorMapper.isAuthenticationError(throwable)) {
                expireAuth()
            }
        }
    }

    fun uploadProfileAvatar(avatarUri: Uri) {
        routeActionScope.launch {
            runCatching {
                val avatarBytes = buildProfileAvatarJpeg(context, avatarUri)
                authRepository.uploadAvatar(
                    imageBytes = avatarBytes,
                    contentType = AVATAR_JPEG_CONTENT_TYPE
                )
            }.onSuccess { user ->
                currentUser = user
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                }
            }
        }
    }

    fun requestRouteHistoryRefresh() {
        routeActionScope.launch {
            refreshRouteHistory()
        }
    }

    fun openHistoryOnMap(requestId: String, routeCode: String?) {
        routeActionScope.launch {
            routeHistoryError = null
            runCatching {
                routeRepository.fetchRouteHistoryDetail(requestId)
            }.onSuccess { routeGeneration ->
                latestRouteGeneration = routeGeneration.onlyRoute(routeCode)
                mapInitialRouteCode = routeCode
                replaceWithMap()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun openSharedRouteOnMap(share: RouteShare) {
        routeActionScope.launch {
            routeSharesError = null
            runCatching {
                routeRepository.fetchSharedRoute(share.shareId)
            }.onSuccess { routeGeneration ->
                latestRouteGeneration = routeGeneration.onlyRoute(share.routeCode)
                mapInitialRouteCode = share.routeCode
                replaceWithMap()
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    routeSharesError = "分享路线打开失败，请稍后重试"
                }
            }
        }
    }

    fun shareCompletedRoute(requestId: String, routeCode: String, shareText: String) {
        if (isRouteShareSubmitting) {
            return
        }
        routeShareNotice = RouteShareNotice.Submitting
        routeActionScope.launch {
            isRouteShareSubmitting = true
            runCatching {
                routeRepository.shareCompletedRoute(requestId, routeCode, shareText)
            }.onSuccess {
                routeShareNotice = RouteShareNotice.Completed
                refreshRouteShares()
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    routeShareNotice = RouteShareNotice.Failed
                }
            }
            isRouteShareSubmitting = false
        }
    }

    fun startRoute(requestId: String, routeCode: String) {
        routeActionScope.launch {
            routeHistoryError = null
            runCatching {
                routeRepository.activateRoute(requestId, routeCode)
            }.onSuccess { routeGeneration ->
                latestRouteGeneration = routeGeneration
                mapInitialRouteCode = routeCode
                replaceWithMap()
                refreshRouteHistory()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun completeActiveRoute(requestId: String, routeCode: String) {
        routeActionScope.launch {
            routeHistoryError = null
            runCatching {
                routeRepository.completeActiveRoute(requestId, routeCode)
            }.onSuccess { routeGeneration ->
                latestRouteGeneration = routeGeneration
                mapInitialRouteCode = routeCode
                refreshRouteHistory()
                refreshCurrentUser()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
        }
    }

    fun submitRouteGeneration(request: RouteGenerateRequest) {
        if (isRouteGenerationSubmitting) {
            return
        }
        routeGenerationNotice = RouteGenerationNotice.Submitted
        routeActionScope.launch {
            isRouteGenerationSubmitting = true
            runCatching {
                routeRepository.generateRoute(enrichRouteCityInfo(request))
            }.onSuccess { routeGeneration ->
                latestRouteGeneration = routeGeneration
                mapInitialRouteCode = null
                routeGenerationNotice = RouteGenerationNotice.Completed(routeGeneration)
                refreshRouteHistory()
            }.onFailure { throwable ->
                if (RouteErrorMapper.isAuthenticationError(throwable)) {
                    expireAuth()
                } else {
                    routeGenerationNotice = RouteGenerationNotice.Failed
                }
            }
            isRouteGenerationSubmitting = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoggedIn) {
            BackHandler(enabled = screenStack.size > 1) {
                popScreen()
            }
            val currentScreen = screenStack.last()
            LaunchedEffect(isLoggedIn) {
                refreshCurrentUser()
                refreshRouteInteractions()
            }
            LaunchedEffect(currentScreen) {
                if (currentScreen == AppScreen.Discover) {
                    refreshRouteShares()
                }
                if (currentScreen == AppScreen.Routes || currentScreen == AppScreen.FavoriteRoutes || currentScreen == AppScreen.Profile) {
                    refreshRouteHistory()
                    refreshRouteInteractions()
                }
                if (currentScreen == AppScreen.Profile) {
                    refreshCurrentUser()
                    markProfileVisited()
                }
            }
            when (currentScreen) {
                AppScreen.Discover -> DiscoverScreen(
                    nickname = currentUser?.nickname.orEmpty(),
                    completedRouteCount = currentUser?.completedRouteCount ?: 0,
                    travelDistanceMeters = currentUser?.travelDistanceMeters ?: 0L,
                    explorationStreakDays = explorationStreakDays,
                    routeShares = routeShares,
                    isRouteSharesLoading = isRouteSharesLoading,
                    routeSharesError = routeSharesError,
                    onOpenShare = ::openSharedRouteOnMap,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.Map -> MapSelectScreen(
                    routeGeneration = latestRouteGeneration,
                    initialVisibleRouteCode = mapInitialRouteCode,
                    routeInteractions = routeInteractions,
                    routeInteractionKey = ::routeInteractionKey,
                    onToggleRouteFavorite = ::toggleRouteFavorite,
                    onReactToRoute = ::reactToRoute,
                    onOpenRouteConfig = { center ->
                        selectedCenter = center
                        pushScreen(AppScreen.RouteConfig)
                    },
                    routeRepositoryAvailable = true,
                    isRouteGenerationSubmitting = isRouteGenerationSubmitting,
                    onSubmitRouteGeneration = ::submitRouteGeneration,
                    onStartRoute = ::startRoute,
                    onCompleteRoute = ::completeActiveRoute,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenRoutes = {
                        clearMapSelectionState()
                        pushScreen(AppScreen.Routes)
                    },
                    onOpenProfile = {
                        clearMapSelectionState()
                        pushScreen(AppScreen.Profile)
                    }
                )

                AppScreen.RouteConfig -> RouteConfigScreen(
                    routeRepository = routeRepository,
                    selectedCenter = selectedCenter,
                    onBack = ::popScreen,
                    onSubmitRouteGeneration = ::submitRouteGeneration
                )

                AppScreen.Routes -> RoutesScreen(
                    historyGroups = routeHistoryGroups,
                    isLoading = isRouteHistoryLoading,
                    errorMessage = routeHistoryError,
                    onOpenHistoryGroup = { requestId -> openHistoryOnMap(requestId, null) },
                    onOpenHistoryRoute = { requestId, routeCode -> openHistoryOnMap(requestId, routeCode) },
                    onShareWalkedRoute = ::shareCompletedRoute,
                    onRefreshHistory = ::requestRouteHistoryRefresh,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.FavoriteRoutes -> FavoriteRoutesScreen(
                    historyGroups = routeHistoryGroups,
                    routeInteractions = routeInteractions,
                    routeInteractionKey = ::routeInteractionKey,
                    isLoading = isRouteHistoryLoading,
                    errorMessage = routeHistoryError,
                    onOpenFavoriteRoute = { requestId, routeCode -> openHistoryOnMap(requestId, routeCode) },
                    onRefreshHistory = ::requestRouteHistoryRefresh,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteExecution -> RouteExecutionScreen(
                    onBackToRoutes = ::popScreen,
                    onOpenPoi = { pushScreen(AppScreen.PoiExplanation) },
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.PoiExplanation -> PoiExplanationScreen(
                    onBack = ::popScreen,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.Profile -> ProfileScreen(
                    nickname = currentUser?.nickname.orEmpty(),
                    avatarUrl = currentUser?.avatarUrl.orEmpty(),
                    completedRouteCount = currentUser?.completedRouteCount ?: 0,
                    travelDistanceMeters = currentUser?.travelDistanceMeters ?: 0L,
                    routeInteractions = routeInteractions,
                    explorationStreakDays = explorationStreakDays,
                    preferenceAnswers = explorationPreferenceAnswers,
                    onAvatarSelected = ::uploadProfileAvatar,
                    onOpenQuestionnaire = { pushScreen(AppScreen.ProfileQuestionnaire) },
                    onOpenFavoriteRoutes = { pushScreen(AppScreen.FavoriteRoutes) },
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onLogout = ::expireAuth
                )

                AppScreen.ProfileQuestionnaire -> ProfileQuestionnaireScreen(
                    answers = explorationPreferenceAnswers,
                    onBack = ::popScreen,
                    onSave = { answers ->
                        explorationPreferenceAnswers = answers
                        popScreen()
                    }
                )
            }
            routeGenerationNotice?.let { notice ->
                RouteGenerationNoticeOverlay(
                    notice = notice,
                    onDismiss = { routeGenerationNotice = null },
                    onOpenRoutes = {
                        routeGenerationNotice = null
                        clearMapSelectionState()
                        pushScreen(AppScreen.Routes)
                    },
                    onOpenRoute = { routeGeneration ->
                        routeGenerationNotice = null
                        latestRouteGeneration = routeGeneration
                        mapInitialRouteCode = null
                        replaceWithMap()
                    }
                )
            }
            routeShareNotice?.let { notice ->
                RouteShareNoticeOverlay(
                    notice = notice,
                    onDismiss = { routeShareNotice = null }
                )
            }
        } else {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = {
                    isLoggedIn = true
                    screenStack = listOf(AppScreen.Discover)
                    routeActionScope.launch {
                        refreshCurrentUser()
                        refreshRouteInteractions()
                    }
                }
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

private sealed interface RouteGenerationNotice {
    data object Submitted : RouteGenerationNotice
    data class Completed(val routeGeneration: RouteGeneration) : RouteGenerationNotice
    data object Failed : RouteGenerationNotice
}

private sealed interface RouteShareNotice {
    data object Submitting : RouteShareNotice
    data object Completed : RouteShareNotice
    data object Failed : RouteShareNotice
}

private enum class AppScreen {
    Discover,
    Map,
    RouteConfig,
    Routes,
    RouteExecution,
    PoiExplanation,
    Profile,
    FavoriteRoutes,
    ProfileQuestionnaire
}

private val PROFILE_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

private fun RouteGeneration.onlyRoute(routeCode: String?): RouteGeneration {
    if (routeCode.isNullOrBlank()) {
        return this
    }
    val route = this.routes.firstOrNull { candidate -> candidate.routeCode == routeCode }
        ?: return this
    return this.copy(
        routes = listOf(route),
        activeRouteCode = routeCode
    )
}

private fun buildProfileAvatarJpeg(context: Context, avatarUri: Uri): ByteArray {
    val bitmap = context.contentResolver.openInputStream(avatarUri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    } ?: throw IllegalStateException("头像图片解析失败")
    val maxDimension = max(bitmap.width, bitmap.height)
    val avatarBitmap = if (maxDimension > AVATAR_MAX_PIXEL_SIZE) {
        val scale = AVATAR_MAX_PIXEL_SIZE.toFloat() / maxDimension.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    return ByteArrayOutputStream().use { outputStream ->
        avatarBitmap.compress(Bitmap.CompressFormat.JPEG, ROUTE_SHARE_JPEG_QUALITY, outputStream)
        outputStream.toByteArray()
    }
}

private const val AVATAR_MAX_PIXEL_SIZE = 512
private const val AVATAR_JPEG_CONTENT_TYPE = "image/jpeg"
private const val ROUTE_SHARE_JPEG_QUALITY = 86
private const val ROUTE_NOTICE_DISMISS_MS = 2400L
