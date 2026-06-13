package com.urbansidequest.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors: ColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = AppSurface,
    secondary = RouteSecondary,
    onSecondary = AppSurface,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceMuted,
    onSurfaceVariant = AppTextMuted,
    outline = AppBorder,
    error = WarningAmber,
    onError = AppText
)

@Composable
fun UrbanSidequestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}

