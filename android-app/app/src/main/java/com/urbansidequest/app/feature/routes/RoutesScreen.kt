package com.urbansidequest.app.feature.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteHistoryRouteSummary
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteTeal

@Composable
fun RoutesScreen(
    historyGroups: List<RouteHistoryGroup> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onOpenHistoryGroup: (String) -> Unit = {},
    onOpenHistoryRoute: (String, String) -> Unit = { _, _ -> },
    onRefreshHistory: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                UrbanScreenTitle(
                    eyebrow = "路线记录",
                    title = "进行中与历史路线",
                    trailing = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新路线历史",
                                tint = AppText
                            )
                        }
                    }
                )
            }

            if (isLoading) {
                item {
                    EmptyState(
                        title = "正在加载路线历史",
                        description = "正在同步你已生成的城市副本路线。"
                    )
                }
            } else if (errorMessage != null) {
                item {
                    EmptyState(
                        title = "路线历史加载失败",
                        description = errorMessage
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "重新加载", onClick = onRefreshHistory)
                }
            } else if (historyGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "当前没有历史路线",
                        description = "每次生成的 3 到 5 条路线会按一行保存，之后可以回到地图继续查看。"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "去地图选点", onClick = onOpenMap)
                }
            } else {
                val activeGroup = historyGroups.firstOrNull { it.executionStatus == "IN_PROGRESS" && it.activeRouteCode != null }
                if (activeGroup != null) {
                    item(key = "active_${activeGroup.requestId}") {
                        ActiveRoutePanel(
                            group = activeGroup,
                            onOpenActiveRoute = {
                                onOpenHistoryRoute(activeGroup.requestId, activeGroup.activeRouteCode.orEmpty())
                            }
                        )
                    }
                }
                item {
                    SectionHeader(title = "已生成路线", badge = "${historyGroups.size} 组")
                }
                items(
                    items = historyGroups,
                    key = { it.requestId }
                ) { group ->
                    RouteHistoryGroupRow(
                        group = group,
                        onOpenGroup = { onOpenHistoryGroup(group.requestId) },
                        onOpenRoute = { routeCode -> onOpenHistoryRoute(group.requestId, routeCode) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onDiscoverClick = onOpenDiscover,
            onMapClick = onOpenMap,
            onRoutesClick = {},
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun ActiveRoutePanel(
    group: RouteHistoryGroup,
    onOpenActiveRoute: () -> Unit
) {
    val activeRoute = group.routes.firstOrNull { it.routeCode == group.activeRouteCode }
    UrbanTaskCard(
        modifier = Modifier.clickable(onClick = onOpenActiveRoute),
        highlighted = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("当前有路线正在进行", color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = activeRoute?.title ?: group.areaLabel,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            UrbanBadge(text = "继续", style = UrbanBadgeStyle.RouteA)
        }
        if (activeRoute != null) {
            Text(
                text = "${formatDuration(activeRoute.totalDurationMinutes)} · ${formatDistance(activeRoute.totalDistanceMeters)} · ${formatRiskLevel(activeRoute.riskLevel)}",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        UrbanBadge(text = badge, style = UrbanBadgeStyle.Reward)
    }
}

@Composable
private fun RouteHistoryGroupRow(
    group: RouteHistoryGroup,
    onOpenGroup: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenGroup),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = group.areaLabel,
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatCreatedAt(group.createdAt)} · ${group.routes.size} 条路线",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                UrbanBadge(
                    text = formatExecutionStatus(group.executionStatus),
                    style = if (group.executionStatus == "IN_PROGRESS") UrbanBadgeStyle.RouteA else UrbanBadgeStyle.Default
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.routes.forEach { route ->
                    RouteHistoryRouteChip(
                        route = route,
                        isActive = route.routeCode == group.activeRouteCode && group.executionStatus == "IN_PROGRESS",
                        onClick = {
                            if (route.routeCode == group.activeRouteCode && group.executionStatus == "IN_PROGRESS") {
                                onOpenRoute(route.routeCode)
                            } else {
                                onOpenGroup()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteHistoryRouteChip(
    route: RouteHistoryRouteSummary,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) AppSurface else AppSurfaceMuted,
        border = BorderStroke(1.dp, if (isActive) RouteTeal else AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.routeCode,
                    color = if (isActive) RouteTeal else AppText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = RouteTeal
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            text = "进行中",
                            color = AppSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = route.title,
                color = AppText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatDuration(route.totalDurationMinutes)} · ${formatDistance(route.totalDistanceMeters)}",
                color = AppTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        "${String.format("%.1f", meters / 1000.0)} 公里"
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

private fun formatExecutionStatus(status: String): String {
    return when (status) {
        "IN_PROGRESS" -> "进行中"
        "COMPLETED" -> "已完成"
        "ABANDONED" -> "已中止"
        else -> "已生成"
    }
}

private fun formatCreatedAt(createdAt: String): String {
    return createdAt
        .substringBefore(".")
        .replace("T", " ")
        .ifBlank { "刚刚生成" }
}
