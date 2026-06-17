package com.urbansidequest.app.feature.routeresult

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.MetricRow
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.TimelineItem
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanSecondaryButton
import com.urbansidequest.app.ui.components.WarningBanner
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteSecondary

@Composable
fun RouteResultScreen(
    routeGeneration: RouteGeneration? = null,
    onAdjustRoute: () -> Unit = {},
    onStartRoute: () -> Unit = {},
    onOpenPoi: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val primaryRoute = routeGeneration?.routes?.firstOrNull()
    val alternativeRoutes = routeGeneration?.routes.orEmpty().drop(1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        RouteMapPreview(
            label = primaryRoute?.title ?: "路线结果",
            showAlternative = primaryRoute != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(328.dp)
                .statusBarsPadding()
        )

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (primaryRoute == null) {
                    EmptyState(
                        title = "暂无路线结果",
                        description = "从条件页生成路线后，会在这里展示路线摘要、节点和备选方案。"
                    )
                } else {
                    RouteContent(
                        routeGeneration = routeGeneration,
                        primaryRoute = primaryRoute,
                        alternativeRoutes = alternativeRoutes,
                        onOpenPoi = onOpenPoi
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UrbanSecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "调整路线",
                        onClick = onAdjustRoute,
                    )
                    UrbanPrimaryButton(
                        modifier = Modifier.weight(1f),
                        text = "开始路线",
                        onClick = onStartRoute,
                        enabled = primaryRoute != null,
                    )
                }
            }
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun RouteContent(
    routeGeneration: RouteGeneration?,
    primaryRoute: GeneratedRoute,
    alternativeRoutes: List<GeneratedRoute>,
    onOpenPoi: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = primaryRoute.title,
            color = AppText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = primaryRoute.summary,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    MetricRow(
        items = listOf(
            "时长" to formatDuration(primaryRoute.totalDurationMinutes),
            "距离" to formatDistance(primaryRoute.totalDistanceMeters),
            "预算" to formatBudget(primaryRoute.budgetCent)
        )
    )

    val warningText = routeGeneration?.warnings?.firstOrNull()
        ?: primaryRoute.stops.firstNotNullOfOrNull(RouteStop::riskNote)
    if (warningText != null) {
        WarningBanner(text = warningText)
    }

    UrbanSection {
        Text(
            text = "为什么这样安排",
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = primaryRoute.explanation,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    UrbanSection {
        Text(
            text = "路线节点",
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.clickable(onClick = onOpenPoi)) {
            primaryRoute.stops.forEachIndexed { index, stop ->
                TimelineItem(
                    title = stop.name,
                    description = stop.reason ?: buildStopDescription(stop),
                    isLast = index == primaryRoute.stops.lastIndex
                )
            }
        }
    }

    if (alternativeRoutes.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            alternativeRoutes.take(2).forEach { route ->
                AlternativeRouteCard(
                    route = route,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AlternativeRouteCard(
    route: GeneratedRoute,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "路线 ${route.routeCode}",
                color = RouteSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = route.title,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(1.dp))
            Text(
                text = route.summary,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(minutes: Int?): String {
    if (minutes == null) {
        return "-"
    }
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours} 小时 ${restMinutes} 分钟"
        hours > 0 -> "${hours} 小时"
        else -> "${restMinutes} 分钟"
    }
}

private fun formatDistance(distanceMeters: Int?): String {
    if (distanceMeters == null) {
        return "-"
    }
    return if (distanceMeters >= 1000) {
        "${String.format("%.1f", distanceMeters / 1000.0)} 公里"
    } else {
        "${distanceMeters} 米"
    }
}

private fun formatBudget(budgetCent: Int?): String {
    if (budgetCent == null) {
        return "-"
    }
    return "¥${budgetCent / 100}"
}

private fun buildStopDescription(stop: RouteStop): String {
    val stayText = stop.stayMinutes?.let { "停留 ${it} 分钟" }
    val nextText = stop.durationToNextMinutes?.let { "下一段约 ${it} 分钟" }
    return listOfNotNull(stayText, nextText).joinToString(" · ").ifBlank { "路线节点" }
}
