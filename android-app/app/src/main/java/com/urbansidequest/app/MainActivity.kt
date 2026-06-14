package com.urbansidequest.app

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.urbansidequest.app.data.api.AuthApi
import com.urbansidequest.app.data.api.RouteApi
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.feature.execution.RouteExecutionScreen
import com.urbansidequest.app.feature.login.LoginRoute
import com.urbansidequest.app.feature.mapselect.MapSelectScreen
import com.urbansidequest.app.feature.poi.PoiExplanationScreen
import com.urbansidequest.app.feature.profile.ProfileScreen
import com.urbansidequest.app.feature.routeconfig.RouteConfigScreen
import com.urbansidequest.app.feature.routes.RoutesScreen
import com.urbansidequest.app.ui.theme.UrbanSidequestTheme

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
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var screenStack by remember { mutableStateOf(listOf(AppScreen.Map)) }
    var latestRouteGeneration by remember { mutableStateOf<RouteGeneration?>(null) }
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

    fun popScreen() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
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
            when (currentScreen) {
                AppScreen.Map -> MapSelectScreen(
                    routeGeneration = latestRouteGeneration,
                    onOpenRouteConfig = { center ->
                        selectedCenter = center
                        pushScreen(AppScreen.RouteConfig)
                    },
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteConfig -> RouteConfigScreen(
                    routeRepository = routeRepository,
                    selectedCenter = selectedCenter,
                    onBack = ::popScreen,
                    onAuthExpired = {
                        authRepository.clearSession()
                        latestRouteGeneration = null
                        screenStack = listOf(AppScreen.Map)
                        isLoggedIn = false
                    },
                    onGenerateRoute = { routeGeneration ->
                        latestRouteGeneration = routeGeneration
                        replaceWithMap()
                    }
                )

                AppScreen.Routes -> RoutesScreen(
                    routeGeneration = latestRouteGeneration,
                    onContinueRoute = { pushScreen(AppScreen.RouteExecution) },
                    onOpenRouteOnMap = ::replaceWithMap,
                    onOpenMap = ::replaceWithMap,
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.RouteExecution -> RouteExecutionScreen(
                    onBackToRoutes = ::popScreen,
                    onOpenPoi = { pushScreen(AppScreen.PoiExplanation) },
                    onOpenMap = ::replaceWithMap,
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.PoiExplanation -> PoiExplanationScreen(
                    onBack = ::popScreen,
                    onOpenMap = ::replaceWithMap,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) },
                    onOpenProfile = { pushScreen(AppScreen.Profile) }
                )

                AppScreen.Profile -> ProfileScreen(
                    onOpenMap = ::replaceWithMap,
                    onOpenRoutes = { pushScreen(AppScreen.Routes) }
                )
            }
        } else {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = {
                    isLoggedIn = true
                    screenStack = listOf(AppScreen.Map)
                }
            )
        }
    }
}

private enum class AppScreen {
    Map,
    RouteConfig,
    Routes,
    RouteExecution,
    PoiExplanation,
    Profile
}
