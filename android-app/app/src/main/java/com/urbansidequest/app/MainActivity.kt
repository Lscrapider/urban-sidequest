package com.urbansidequest.app

import android.os.Bundle
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
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.auth.AuthSessionStore
import com.urbansidequest.app.feature.execution.RouteExecutionScreen
import com.urbansidequest.app.feature.login.LoginRoute
import com.urbansidequest.app.feature.mapselect.MapSelectScreen
import com.urbansidequest.app.feature.poi.PoiExplanationScreen
import com.urbansidequest.app.feature.profile.ProfileScreen
import com.urbansidequest.app.feature.routeconfig.RouteConfigScreen
import com.urbansidequest.app.feature.routeresult.RouteResultScreen
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
    val authRepository = remember {
        AuthRepository(
            authApi = AuthApi(),
            authSessionStore = AuthSessionStore(context)
        )
    }
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf(AppScreen.Map) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoggedIn) {
            when (currentScreen) {
                AppScreen.Map -> MapSelectScreen(
                    onOpenRouteConfig = { currentScreen = AppScreen.RouteConfig },
                    onOpenRoutes = { currentScreen = AppScreen.Routes },
                    onOpenProfile = { currentScreen = AppScreen.Profile }
                )

                AppScreen.RouteConfig -> RouteConfigScreen(
                    onBack = { currentScreen = AppScreen.Map },
                    onGenerateRoute = { currentScreen = AppScreen.RouteResult }
                )

                AppScreen.RouteResult -> RouteResultScreen(
                    onAdjustRoute = { currentScreen = AppScreen.RouteConfig },
                    onStartRoute = { currentScreen = AppScreen.RouteExecution },
                    onOpenPoi = { currentScreen = AppScreen.PoiExplanation },
                    onOpenMap = { currentScreen = AppScreen.Map },
                    onOpenRoutes = { currentScreen = AppScreen.Routes },
                    onOpenProfile = { currentScreen = AppScreen.Profile }
                )

                AppScreen.Routes -> RoutesScreen(
                    onContinueRoute = { currentScreen = AppScreen.RouteExecution },
                    onOpenRouteResult = { currentScreen = AppScreen.RouteResult },
                    onOpenMap = { currentScreen = AppScreen.Map },
                    onOpenProfile = { currentScreen = AppScreen.Profile }
                )

                AppScreen.RouteExecution -> RouteExecutionScreen(
                    onBackToRoutes = { currentScreen = AppScreen.Routes },
                    onOpenPoi = { currentScreen = AppScreen.PoiExplanation },
                    onOpenMap = { currentScreen = AppScreen.Map },
                    onOpenProfile = { currentScreen = AppScreen.Profile }
                )

                AppScreen.PoiExplanation -> PoiExplanationScreen(
                    onBack = { currentScreen = AppScreen.RouteExecution },
                    onOpenMap = { currentScreen = AppScreen.Map },
                    onOpenRoutes = { currentScreen = AppScreen.Routes },
                    onOpenProfile = { currentScreen = AppScreen.Profile }
                )

                AppScreen.Profile -> ProfileScreen(
                    onOpenMap = { currentScreen = AppScreen.Map },
                    onOpenRoutes = { currentScreen = AppScreen.Routes }
                )
            }
        } else {
            LoginRoute(
                authRepository = authRepository,
                onLoginSuccess = {
                    isLoggedIn = true
                    currentScreen = AppScreen.Map
                }
            )
        }
    }
}

private enum class AppScreen {
    Map,
    RouteConfig,
    RouteResult,
    Routes,
    RouteExecution,
    PoiExplanation,
    Profile
}
