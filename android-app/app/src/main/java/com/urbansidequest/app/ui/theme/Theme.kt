package com.urbansidequest.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    error = ErrorRed,
    onError = AppSurface,
    errorContainer = ErrorSurface,
    onErrorContainer = ErrorRed
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = RouteTeal,
    onPrimary = DarkAppText,
    secondary = RouteSecondary,
    onSecondary = DarkAppText,
    background = DarkAppBackground,
    onBackground = DarkAppText,
    surface = DarkAppSurface,
    onSurface = DarkAppText,
    surfaceVariant = DarkAppSurfaceMuted,
    onSurfaceVariant = DarkAppTextMuted,
    outline = AppBorder,
    error = ErrorRed,
    onError = DarkAppText,
    errorContainer = ErrorSurface,
    onErrorContainer = ErrorRed
)

private val UrbanTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
)

private val UrbanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun UrbanSidequestTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = UrbanTypography,
        shapes = UrbanShapes,
        content = content
    )
}
