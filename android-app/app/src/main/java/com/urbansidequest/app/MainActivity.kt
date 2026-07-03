package com.urbansidequest.app

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
import com.urbansidequest.app.data.api.AuthApi
import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.api.RouteApiException
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.auth.AuthSessionStore
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

    fun replaceWithDiscover() {
        screenStack = listOf(AppScreen.Discover)
    }

    fun popScreen() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        }
    }

    fun expireAuth() {
        authRepository.clearSession()
        latestRouteGeneration = null
        mapInitialRouteCode = null
        routeHistoryGroups = emptyList()
        screenStack = listOf(AppScreen.Discover)
        isLoggedIn = false
    }

    fun handleRouteFailure(throwable: Throwable, fallbackMessage: String) {
        if ((throwable as? RouteApiException)?.isAuthenticationError == true) {
            expireAuth()
        } else {
            routeHistoryError = throwable.message ?: fallbackMessage
        }
    }

    suspend fun refreshRouteHistory() {
        isRouteHistoryLoading = true
        routeHistoryError = null
        runCatching {
            routeRepository.fetchRouteHistory()
        }.onSuccess { groups ->
            routeHistoryGroups = groups
        }.onFailure { throwable ->
            handleRouteFailure(throwable, "路线历史加载失败，请稍后重试")
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
                handleRouteFailure(throwable, "路线详情加载失败，请稍后重试")
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
                pushScreen(AppScreen.Routes)
                refreshRouteHistory()
            }.onFailure { throwable ->
                handleRouteFailure(throwable, "路线开始失败，请稍后重试")
            }
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
                    onOpenMap = {
                        mapInitialRouteCode = null
                        replaceWithMap()
                    },
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
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteConfig -> RouteConfigScreen(
                    routeRepository = routeRepository,
                    selectedCenter = selectedCenter,
                    onBack = ::popScreen,
                    onAuthExpired = ::expireAuth,
                    onGenerateRoute = { routeGeneration ->
                        latestRouteGeneration = routeGeneration
                        mapInitialRouteCode = null
                        replaceWithMap()
                    }
                )

                AppScreen.Routes -> RoutesScreen(
                    historyGroups = routeHistoryGroups,
                    isLoading = isRouteHistoryLoading,
                    errorMessage = routeHistoryError,
                    onOpenHistoryGroup = { requestId -> openHistoryOnMap(requestId, null) },
                    onOpenHistoryRoute = { requestId, routeCode -> openHistoryOnMap(requestId, routeCode) },
                    onRefreshHistory = ::requestRouteHistoryRefresh,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = {
                        mapInitialRouteCode = null
                        replaceWithMap()
                    },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteExecution -> RouteExecutionScreen(
                    onBackToRoutes = ::popScreen,
                    onOpenPoi = { pushScreen(AppScreen.PoiExplanation) },
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::replaceWithMap,
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.PoiExplanation -> PoiExplanationScreen(
                    onBack = ::popScreen,
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::replaceWithMap,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.Profile -> ProfileScreen(
                    onOpenDiscover = ::replaceWithDiscover,
                    onOpenMap = ::replaceWithMap,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) }
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

private enum class AppScreen {
    Discover,
    Map,
    RouteConfig,
    Routes,
    RouteExecution,
    PoiExplanation,
    Profile
}
