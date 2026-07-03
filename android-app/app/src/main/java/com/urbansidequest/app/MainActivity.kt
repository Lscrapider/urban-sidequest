package com.urbansidequest.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.AuthApi
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
import com.urbansidequest.app.feature.discover.DiscoverScreen
import com.urbansidequest.app.feature.execution.RouteExecutionScreen
import com.urbansidequest.app.feature.login.LoginRoute
import com.urbansidequest.app.feature.mapselect.MapSelectScreen
import com.urbansidequest.app.feature.poi.PoiExplanationScreen
import com.urbansidequest.app.feature.profile.ProfileScreen
import com.urbansidequest.app.feature.routeconfig.RouteConfigScreen
import com.urbansidequest.app.feature.routes.RoutesScreen
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanSecondaryButton
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.ErrorRed
import com.urbansidequest.app.ui.theme.ErrorSurface
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.UrbanSidequestTheme
import kotlinx.coroutines.launch

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
    var selectedCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var routeGenerationNotice by remember { mutableStateOf<RouteGenerationNotice?>(null) }
    var isRouteGenerationSubmitting by remember { mutableStateOf(false) }

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
        screenStack = listOf(AppScreen.Discover)
        isLoggedIn = false
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
                latestRouteGeneration = routeGeneration
                mapInitialRouteCode = routeCode
                replaceWithMap()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, RouteErrorMapper.ROUTE_LOAD_FAILED_MESSAGE)
            }
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
            LaunchedEffect(currentScreen) {
                if (currentScreen == AppScreen.Routes) {
                    refreshRouteHistory()
                }
            }
            when (currentScreen) {
                AppScreen.Discover -> DiscoverScreen(
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.Map -> MapSelectScreen(
                    routeGeneration = latestRouteGeneration,
                    initialVisibleRouteCode = mapInitialRouteCode,
                    onOpenRouteConfig = { center ->
                        selectedCenter = center
                        pushScreen(AppScreen.RouteConfig)
                    },
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
                    onRefreshHistory = ::requestRouteHistoryRefresh,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
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
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::openMapWithActiveRouteFallback,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) }
                )
            }
            routeGenerationNotice?.let { notice ->
                RouteGenerationNoticeDialog(
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
        } else {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = {
                    isLoggedIn = true
                    screenStack = listOf(AppScreen.Discover)
                }
            )
        }
    }
}

@Composable
private fun RouteGenerationNoticeDialog(
    notice: RouteGenerationNotice,
    onDismiss: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenRoute: (RouteGeneration) -> Unit
) {
    val spec = notice.toNoticeSpec()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(18.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RouteGenerationNoticeHeader(spec = spec)
                    Text(
                        text = spec.message,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UrbanPrimaryButton(
                            text = spec.primaryActionText,
                            onClick = {
                                when (notice) {
                                    is RouteGenerationNotice.Completed -> onOpenRoute(notice.routeGeneration)
                                    RouteGenerationNotice.Failed -> onDismiss()
                                    RouteGenerationNotice.Submitted -> onOpenRoutes()
                                }
                            }
                        )
                        UrbanSecondaryButton(
                            text = spec.secondaryActionText,
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteGenerationNoticeHeader(spec: RouteGenerationNoticeSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = spec.accentSurface,
            border = BorderStroke(1.dp, spec.accent.copy(alpha = 0.28f))
        ) {
            RouteGenerationStatusMark(
                tone = spec.tone,
                color = spec.accent,
                modifier = Modifier.padding(9.dp)
            )
        }
        Text(
            text = spec.title,
            color = AppText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RouteGenerationStatusMark(
    tone: RouteGenerationNoticeTone,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.12f
        when (tone) {
            RouteGenerationNoticeTone.Info -> {
                drawCircle(color = color, radius = size.minDimension * 0.26f, center = center)
            }
            RouteGenerationNoticeTone.Success -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.52f),
                    end = Offset(size.width * 0.42f, size.height * 0.74f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.42f, size.height * 0.74f),
                    end = Offset(size.width * 0.82f, size.height * 0.26f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            RouteGenerationNoticeTone.Error -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.28f, size.height * 0.28f),
                    end = Offset(size.width * 0.72f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.72f, size.height * 0.28f),
                    end = Offset(size.width * 0.28f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(
            color = color,
            radius = size.minDimension * 0.44f,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun RouteGenerationNotice.toNoticeSpec(): RouteGenerationNoticeSpec {
    return when (this) {
        RouteGenerationNotice.Submitted -> RouteGenerationNoticeSpec(
            title = "路线生成已提交",
            message = "路线会在后台生成，完成后会通知你。也可以稍后到路线记录查看生成进度。",
            primaryActionText = "查看路线记录",
            secondaryActionText = "知道了",
            tone = RouteGenerationNoticeTone.Info,
            accent = InfoCyan,
            accentSurface = InfoCyanSurface
        )
        is RouteGenerationNotice.Completed -> RouteGenerationNoticeSpec(
            title = "路线已生成",
            message = "新的路线已经保存到历史路线，也可以现在打开地图查看。",
            primaryActionText = "查看路线",
            secondaryActionText = "稍后查看",
            tone = RouteGenerationNoticeTone.Success,
            accent = RouteTeal,
            accentSurface = DeepTeal.copy(alpha = 0.08f)
        )
        RouteGenerationNotice.Failed -> RouteGenerationNoticeSpec(
            title = "路线生成失败",
            message = RouteErrorMapper.ROUTE_GENERATION_FAILED_MESSAGE,
            primaryActionText = "知道了",
            secondaryActionText = "关闭",
            tone = RouteGenerationNoticeTone.Error,
            accent = ErrorRed,
            accentSurface = ErrorSurface
        )
    }
}

private data class RouteGenerationNoticeSpec(
    val title: String,
    val message: String,
    val primaryActionText: String,
    val secondaryActionText: String,
    val tone: RouteGenerationNoticeTone,
    val accent: Color,
    val accentSurface: Color
)

private enum class RouteGenerationNoticeTone {
    Info,
    Success,
    Error
}

private sealed interface RouteGenerationNotice {
    data object Submitted : RouteGenerationNotice
    data class Completed(val routeGeneration: RouteGeneration) : RouteGenerationNotice
    data object Failed : RouteGenerationNotice
}

private enum class AppScreen {
    Discover,
    Map,
    RouteConfig,
    Routes,
    RouteExecution,
    PoiExplanation,
    Profile
}
