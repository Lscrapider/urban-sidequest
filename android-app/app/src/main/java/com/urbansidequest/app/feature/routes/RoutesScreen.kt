package com.urbansidequest.app.feature.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

@Composable
fun RoutesScreen(
    routeGeneration: RouteGeneration? = null,
    onContinueRoute: () -> Unit = {},
    onOpenRouteOnMap: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(subtitle = "继续当前路线，或查看已生成方案")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "当前路线",
                color = AppText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val primaryRoute = routeGeneration?.routes?.firstOrNull()
            if (primaryRoute == null) {
                EmptyState(
                    title = "暂无进行中的路线",
                    description = "从地图页选择范围并生成路线后，会在这里显示当前路线。"
                )
            } else {
                RouteListItem(
                    route = primaryRoute,
                    titleSuffix = "当前路线",
                    onClick = onOpenRouteOnMap
                )
            }

            Text(
                text = "已生成路线",
                color = AppText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val routes = routeGeneration?.routes.orEmpty()
            if (routes.isEmpty()) {
                EmptyState(
                    title = "暂无已生成路线",
                    description = "生成后的路线方案会按时间倒序展示。"
                )
            } else {
                routes.forEach { route ->
                    RouteListItem(
                        route = route,
                        titleSuffix = "查看地图",
                        onClick = onOpenRouteOnMap
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onMapClick = onOpenMap,
            onRoutesClick = {},
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun RouteListItem(
    route: GeneratedRoute,
    titleSuffix: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = DeepTeal
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = route.routeCode,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = route.title,
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatDuration(route.totalDurationMinutes)} · ${formatDistance(route.totalDistanceMeters)} · $titleSuffix",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Surface(
                shape = CircleShape,
                color = AppSurfaceMuted,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    text = formatRiskLevel(route.riskLevel),
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours} 小时 ${restMinutes} 分钟"
        hours > 0 -> "${hours} 小时"
        else -> "${minutes} 分钟"
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${meters / 1000}.${meters % 1000 / 100} 公里"
    } else {
        "${meters} 米"
    }
}

private fun formatRiskLevel(riskLevel: String): String {
    return when (riskLevel) {
        "LOW" -> "风险低"
        "MEDIUM" -> "需留意"
        "HIGH" -> "风险高"
        else -> "风险待确认"
    }
}
