package com.urbansidequest.app.feature.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteHistoryRouteSummary
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanSecondaryButton
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.AreaGreen
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RouteBlue = Color(0xFF0B5CFF)
private val RouteBlueSurface = Color(0xFFEAF3FF)
private val RouteBlueBorder = Color(0xFF78A9FF)

@Composable
fun RoutesScreen(
    historyGroups: List<RouteHistoryGroup> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onOpenHistoryGroup: (String) -> Unit = {},
    onOpenHistoryRoute: (String, String) -> Unit = { _, _ -> },
    onShareWalkedRoute: (String, String, String) -> Unit = { _, _, _ -> },
    onRefreshHistory: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(RouteLibraryTab.Generated) }
    var shareTarget by remember { mutableStateOf<WalkedShareTarget?>(null) }
    var shareText by remember { mutableStateOf(DEFAULT_SHARE_TEXT) }
    val activeGroup = historyGroups.firstOrNull {
        it.generationStatus == "SUCCESS" && it.executionStatus == "IN_PROGRESS" && it.activeRouteCode != null
    }
    val walkedGroups = historyGroups.mapNotNull { group ->
        if (group.generationStatus == "SUCCESS" && group.executionStatus == "COMPLETED") {
            group.withOnlyActiveRoute()
        } else {
            null
        }
    }
    val generatedGroups = historyGroups.filter { group ->
        group.requestId != activeGroup?.requestId && group.executionStatus != "COMPLETED"
    }
    val visibleGroups = when (selectedTab) {
        RouteLibraryTab.Generated -> generatedGroups
        RouteLibraryTab.Walked -> walkedGroups
    }
    val generatedRouteCount = generatedGroups.sumOf { group -> group.routes.size }
    val walkedRouteCount = walkedGroups.sumOf { group -> group.routes.size }
    val latestGeneratedGroup = generatedGroups.firstOrNull { group ->
        group.generationStatus == "SUCCESS" && group.routes.isNotEmpty()
    }
    val generatingCount = generatedGroups.count(::isGeneratingHistory)
    val failedCount = generatedGroups.count { group -> group.generationStatus == "FAILED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RouteLibraryHeader()
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
                        description = "每次生成的 3 到 5 条路线会按一行保存，之后可以回到地图继续查看。",
                        illustrationResId = R.drawable.illustration_empty_routes
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "去地图选点", onClick = onOpenMap)
                }
            } else {
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
                    RouteLibrarySwitcher(
                        selectedTab = selectedTab,
                        generatedCount = generatedRouteCount,
                        walkedCount = walkedRouteCount,
                        onSelectTab = { selectedTab = it }
                    )
                }
                if (visibleGroups.isEmpty()) {
                    item(key = "empty_${selectedTab.name}") {
                        EmptyState(
                            title = selectedTab.emptyTitle,
                            description = selectedTab.emptyDescription,
                            illustrationResId = R.drawable.illustration_empty_routes
                        )
                    }
                } else if (selectedTab == RouteLibraryTab.Walked) {
                    item {
                        WalkedRoutesSummaryCard(groups = walkedGroups)
                    }
                    item {
                        SectionHeader(
                            title = selectedTab.sectionTitle,
                            trailingText = "最近完成",
                            showDropdown = true
                        )
                    }
                    items(
                        items = visibleGroups,
                        key = { group -> "walked_${group.requestId}" }
                    ) { group ->
                        val route = group.routes.firstOrNull()
                        if (route != null) {
                            WalkedRouteRow(
                                group = group,
                                route = route,
                                onOpenRoute = { onOpenHistoryRoute(group.requestId, route.routeCode) },
                                onShareRoute = {
                                    shareTarget = WalkedShareTarget(group.requestId, route.routeCode, route.title)
                                    shareText = DEFAULT_SHARE_TEXT
                                }
                            )
                        }
                    }
                    item {
                        MoreWalkedRoutesButton(onClick = onRefreshHistory)
                    }
                } else {
                    latestGeneratedGroup?.let { group ->
                        item(key = "latest_${group.requestId}") {
                            LatestGeneratedGroupCard(
                                group = group,
                                onOpenGroup = { onOpenHistoryGroup(group.requestId) },
                                onOpenRoute = { routeCode -> onOpenHistoryRoute(group.requestId, routeCode) }
                            )
                        }
                    }
                    item {
                        GenerationStatusRow(
                            generatingCount = generatingCount,
                            failedCount = failedCount,
                            onRegenerate = onOpenMap
                        )
                    }
                    item {
                        SectionHeader(
                            title = selectedTab.sectionTitle,
                            trailingText = "最新生成",
                            showDropdown = true
                        )
                    }
                    items(
                        items = visibleGroups,
                        key = { "${selectedTab.name}_${it.requestId}" }
                    ) { group ->
                        RouteHistoryGroupRow(
                            group = group,
                            onOpenGroup = {
                                if (selectedTab == RouteLibraryTab.Walked) {
                                    group.routes.firstOrNull()?.let { route ->
                                        onOpenHistoryRoute(group.requestId, route.routeCode)
                                    }
                                } else {
                                    onOpenHistoryGroup(group.requestId)
                                }
                            },
                            onOpenRoute = { routeCode -> onOpenHistoryRoute(group.requestId, routeCode) }
                        )
                    }
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

    shareTarget?.let { target ->
        WalkedRouteShareDialog(
            routeTitle = target.routeTitle,
            shareText = shareText,
            onShareTextChange = { value -> shareText = value.take(MAX_SHARE_TEXT_LENGTH) },
            onDismiss = { shareTarget = null },
            onConfirm = {
                onShareWalkedRoute(target.requestId, target.routeCode, shareText)
                shareTarget = null
            }
        )
    }
}

@Composable
private fun WalkedRouteShareDialog(
    routeTitle: String,
    shareText: String,
    onShareTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "分享走过路线",
                            color = AppText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = routeTitle,
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedTextField(
                        value = shareText,
                        onValueChange = onShareTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = {
                            Text(
                                text = "分享文字",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppText,
                            unfocusedTextColor = AppText,
                            focusedContainerColor = AppSurfaceMuted,
                            unfocusedContainerColor = AppSurfaceMuted,
                            focusedBorderColor = RouteTeal,
                            unfocusedBorderColor = AppBorder,
                            focusedLabelColor = RouteTeal,
                            unfocusedLabelColor = AppTextMuted,
                            cursorColor = RouteTeal
                        )
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UrbanPrimaryButton(
                            text = "生成并分享",
                            enabled = shareText.isNotBlank(),
                            onClick = onConfirm
                        )
                        UrbanSecondaryButton(
                            text = "取消",
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteRoutesScreen(
    historyGroups: List<RouteHistoryGroup> = emptyList(),
    routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    routeInteractionKey: (String, String) -> String = { candidateSetId, routeCode -> "$candidateSetId:$routeCode" },
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onOpenFavoriteRoute: (String, String) -> Unit = { _, _ -> },
    onRefreshHistory: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val favoriteGroups = historyGroups.mapNotNull { group ->
        val favoriteRoutes = group.routes.filter { route ->
            routeInteractions[routeInteractionKey(group.candidateSetId, route.routeCode)]?.isFavorite == true
        }
        if (favoriteRoutes.isEmpty()) {
            null
        } else {
            group.copy(routes = favoriteRoutes)
        }
    }
    val favoriteRouteCount = favoriteGroups.sumOf { it.routes.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                UrbanScreenTitle(
                    eyebrow = "我的收藏",
                    title = "收藏路线",
                    trailing = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新收藏路线",
                                tint = AppText
                            )
                        }
                    }
                )
            }

            if (isLoading) {
                item {
                    EmptyState(
                        title = "正在加载收藏路线",
                        description = "正在同步你收藏过的路线。"
                    )
                }
            } else if (errorMessage != null) {
                item {
                    EmptyState(
                        title = "收藏路线加载失败",
                        description = errorMessage
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "重新加载", onClick = onRefreshHistory)
                }
            } else if (favoriteGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有收藏路线",
                        description = "收藏后的路线会单独沉淀到这里，之后可以直接回到地图查看。",
                        illustrationResId = R.drawable.illustration_empty_routes
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "查看生成路线", onClick = onOpenRoutes)
                }
            } else {
                item {
                    SectionHeader(title = "已收藏路线", trailingText = "$favoriteRouteCount 条")
                }
                items(
                    items = favoriteGroups,
                    key = { "favorite_${it.requestId}_${it.routes.joinToString("_") { route -> route.routeCode }}" }
                ) { group ->
                    RouteHistoryGroupRow(
                        group = group,
                        onOpenGroup = {
                            group.routes.firstOrNull()?.let { route ->
                                onOpenFavoriteRoute(group.requestId, route.routeCode)
                            }
                        },
                        onOpenRoute = { routeCode -> onOpenFavoriteRoute(group.requestId, routeCode) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Profile,
            onDiscoverClick = onOpenDiscover,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun RouteLibrarySwitcher(
    selectedTab: RouteLibraryTab,
    generatedCount: Int,
    walkedCount: Int,
    onSelectTab: (RouteLibraryTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            RouteLibraryTab.values().forEach { tab ->
                val selected = tab == selectedTab
                val count = when (tab) {
                    RouteLibraryTab.Generated -> generatedCount
                    RouteLibraryTab.Walked -> walkedCount
                }
                val iconRes = when (tab) {
                    RouteLibraryTab.Generated -> R.drawable.icon_routes_generated
                    RouteLibraryTab.Walked -> R.drawable.icon_routes_walked
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable { onSelectTab(tab) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) RouteBlueSurface else AppSurface,
                    border = BorderStroke(1.dp, if (selected) RouteBlue else AppSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .padding(horizontal = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RouteLibraryImageIcon(
                            iconRes = iconRes,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (selected) RouteBlue else AppText
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = tab.label,
                            color = if (selected) RouteBlue else AppText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        CountPill(text = count.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteLibraryHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "路线",
            color = AppText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "生成结果与走过路线",
            color = AppTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RouteLibraryImageIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = tint?.let(ColorFilter::tint)
    )
}

@Composable
private fun CountPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = AppSurfaceMuted
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            text = text,
            color = AppText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
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
private fun SectionHeader(
    title: String,
    trailingText: String,
    showDropdown: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trailingText,
                color = RouteBlue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (showDropdown) {
                RouteLibraryImageIcon(
                    iconRes = R.drawable.icon_routes_chevron_down,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = RouteBlue
                )
            }
        }
    }
}

@Composable
private fun LatestGeneratedGroupCard(
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
        border = BorderStroke(1.dp, RouteBlueBorder)
    ) {
        Box {
            RecentBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 0.dp, top = 0.dp)
            )
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 34.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RouteLibraryImageIcon(
                                iconRes = R.drawable.icon_routes_location,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = AppText
                            )
                            Text(
                                text = group.areaLabel,
                                color = AppText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${formatCreatedAt(group.createdAt)} · ${group.routes.size} 条路线",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UrbanBadge(text = formatHistoryStatus(group), style = historyStatusBadgeStyle(group))
                        RouteLibraryImageIcon(
                            iconRes = R.drawable.icon_routes_more,
                            contentDescription = "更多生成组操作",
                            modifier = Modifier.size(22.dp),
                            tint = AppText
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    group.routes.forEach { route ->
                        RouteHistoryRouteChip(
                            route = route,
                            isActive = route.routeCode == group.activeRouteCode,
                            onClick = { onOpenRoute(route.routeCode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(topStart = 11.dp, bottomEnd = 8.dp),
        color = RouteBlue
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "最近生成",
                color = AppSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GenerationStatusRow(
    generatingCount: Int,
    failedCount: Int,
    onRegenerate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GenerationMiniStatusCard(
            modifier = Modifier.weight(1f),
            title = "生成中",
            subtitle = if (generatingCount > 0) "${generatingCount} 组路线正在生成" else "暂无生成",
            statusText = if (generatingCount > 0) "等待中" else "空闲",
            iconRes = R.drawable.icon_routes_generated,
            accent = RouteBlue,
            showProgress = generatingCount > 0,
            onClick = {}
        )
        GenerationMiniStatusCard(
            modifier = Modifier.weight(1f),
            title = "生成失败",
            subtitle = if (failedCount > 0) "${failedCount} 组路线生成失败" else "暂无失败",
            statusText = "重新生成",
            iconRes = R.drawable.icon_routes_warning,
            accent = WarningAmber,
            onClick = onRegenerate
        )
    }
}

@Composable
private fun GenerationMiniStatusCard(
    title: String,
    subtitle: String,
    statusText: String,
    @DrawableRes iconRes: Int,
    accent: Color,
    showProgress: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(102.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteLibraryImageIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = accent
                )
                Text(
                    text = title,
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = subtitle,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(RouteBlue.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(4.dp)
                                .background(RouteBlue, RoundedCornerShape(999.dp))
                        )
                    }
                    Text(
                        text = statusText,
                        color = AppText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Text(
                    text = statusText,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WalkedRouteRow(
    group: RouteHistoryGroup,
    route: RouteHistoryRouteSummary,
    onOpenRoute: () -> Unit,
    onShareRoute: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenRoute),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.82f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WalkedRouteMapThumbnail(
                modifier = Modifier.size(width = 118.dp, height = 96.dp),
                routeCode = route.routeCode
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Text(
                            text = route.title,
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatCreatedDate(group.createdAt)} 完成",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    UrbanBadge(text = "已完成", style = UrbanBadgeStyle.Area)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RouteMetaItem(iconRes = R.drawable.icon_routes_location, text = formatCompactDistance(route.totalDistanceMeters))
                    RouteMetaItem(iconRes = R.drawable.icon_routes_clock, text = formatDuration(route.totalDurationMinutes))
                    RouteMetaItem(iconRes = R.drawable.icon_routes_flag, text = "— 个点")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        WalkedRouteAction(
                            iconRes = R.drawable.icon_routes_star,
                            label = "收藏",
                            onClick = {}
                        )
                        WalkedRouteAction(
                            iconRes = R.drawable.icon_routes_share,
                            label = "分享",
                            onClick = onShareRoute
                        )
                    }
                    RouteLibraryImageIcon(
                        iconRes = R.drawable.icon_routes_chevron_right,
                        contentDescription = "查看路线详情",
                        modifier = Modifier.size(22.dp),
                        tint = AppText
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkedRoutesSummaryCard(groups: List<RouteHistoryGroup>) {
    val walkedRoutes = groups.mapNotNull { group -> group.routes.firstOrNull() }
    val totalDistanceMeters = walkedRoutes.sumOf { route -> route.totalDistanceMeters }
    val totalDurationMinutes = walkedRoutes.sumOf { route -> route.totalDurationMinutes }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.70f))
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.route_walked_summary_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(AppSurface.copy(alpha = 0.72f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "走过路线",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${walkedRoutes.size} 条已完成",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                WalkedSummaryMetric(
                    modifier = Modifier.weight(0.92f),
                    value = formatCompactDistance(totalDistanceMeters),
                    label = "总距离"
                )
                WalkedSummaryMetric(
                    modifier = Modifier.weight(0.92f),
                    value = formatHourDecimal(totalDurationMinutes),
                    label = "总时间"
                )
            }
        }
    }
}

@Composable
private fun WalkedSummaryMetric(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = AppTextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun WalkedRouteMapThumbnail(
    routeCode: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppSurfaceMuted)
    ) {
        Image(
            painter = painterResource(R.drawable.route_thumb_placeholder),
            contentDescription = "路线${routeCode}地图缩略图",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun RouteMetaItem(
    @DrawableRes iconRes: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteLibraryImageIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = AppTextMuted
        )
        Text(
            text = text,
            color = AppText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun WalkedRouteAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteLibraryImageIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = AppText
        )
        Text(
            text = label,
            color = AppText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MoreWalkedRoutesButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看更多走过路线",
                color = AppText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            RouteLibraryImageIcon(
                iconRes = R.drawable.icon_routes_chevron_right,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppText
            )
        }
    }
}

@Composable
private fun RouteHistoryGroupRow(
    group: RouteHistoryGroup,
    onOpenGroup: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    val canOpenRoutes = group.generationStatus == "SUCCESS" && group.routes.isNotEmpty()
    val failed = group.generationStatus == "FAILED"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpenRoutes, onClick = onOpenGroup),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.74f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(999.dp),
                color = if (failed) WarningAmber.copy(alpha = 0.12f) else RouteBlueSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RouteLibraryImageIcon(
                        iconRes = if (failed) R.drawable.icon_routes_warning else R.drawable.icon_routes_layers,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (failed) WarningAmber else RouteBlue
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
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
                    text = if (failed) {
                        "${formatCreatedAt(group.createdAt)} · 生成失败"
                    } else {
                        "${formatCreatedAt(group.createdAt)} · ${group.routes.size} 条路线"
                    },
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (failed) {
                        "路线生成失败，请稍后重试"
                    } else {
                        "路线 ${group.routes.size} 条 · 候选 ${group.routes.size * 3} 条"
                    },
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            UrbanBadge(
                text = formatHistoryStatus(group),
                style = historyStatusBadgeStyle(group)
            )
            RouteLibraryImageIcon(
                iconRes = R.drawable.icon_routes_chevron_right,
                contentDescription = "查看生成组",
                modifier = Modifier.size(20.dp),
                tint = AppTextMuted
            )
        }
    }
}

private fun isGeneratingHistory(group: RouteHistoryGroup): Boolean {
    return group.generationStatus == "PENDING" || group.generationStatus == "GENERATING"
}

private data class WalkedShareTarget(
    val requestId: String,
    val routeCode: String,
    val routeTitle: String
)

@Composable
private fun RouteHistoryRouteChip(
    route: RouteHistoryRouteSummary,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val routeAccent = routeChipAccentColor(route.routeCode)
    Surface(
        modifier = Modifier
            .width(132.dp)
            .height(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) routeAccent.copy(alpha = 0.10f) else AppSurface,
        border = BorderStroke(1.dp, routeAccent.copy(alpha = if (isActive) 0.52f else 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .background(routeAccent, RoundedCornerShape(999.dp))
                )
                Text(
                    text = route.routeCode,
                    color = routeAccent,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RouteChipMetricLine(
                iconRes = R.drawable.icon_routes_clock,
                text = formatDuration(route.totalDurationMinutes)
            )
            RouteChipMetricLine(
                iconRes = R.drawable.icon_routes_location,
                text = formatCompactDistance(route.totalDistanceMeters)
            )
            RouteChipMetricLine(
                iconRes = R.drawable.icon_routes_flag,
                text = "${routePlaceholderStopCount(route)} 个点"
            )
        }
    }
}

@Composable
private fun RouteChipMetricLine(
    @DrawableRes iconRes: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteLibraryImageIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = AppTextMuted
        )
        Text(
            text = text,
            color = AppText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours}小时${restMinutes}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}

private fun formatDistance(meters: Int): String {
    return formatCompactDistance(meters)
}

private fun formatCompactDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${String.format("%.1f", meters / 1000.0)} km"
    } else {
        "${meters} m"
    }
}

private fun formatHourDecimal(minutes: Int): String {
    return if (minutes >= 60) {
        "${String.format("%.1f", minutes / 60.0)} 小时"
    } else {
        "${minutes} 分钟"
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

private fun formatHistoryStatus(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "PENDING" -> "等待生成"
        "GENERATING" -> "生成中"
        "FAILED" -> "生成失败"
        "PARTIAL_SUCCESS" -> "部分完成"
        else -> formatExecutionStatus(group.executionStatus)
    }
}

private fun historyStatusBadgeStyle(group: RouteHistoryGroup): UrbanBadgeStyle {
    return when {
        group.generationStatus == "GENERATING" -> UrbanBadgeStyle.Area
        group.generationStatus == "FAILED" -> UrbanBadgeStyle.Warning
        group.executionStatus == "IN_PROGRESS" -> UrbanBadgeStyle.RouteA
        group.executionStatus == "COMPLETED" -> UrbanBadgeStyle.Reward
        else -> UrbanBadgeStyle.Area
    }
}

private fun historyStatusAccentColor(group: RouteHistoryGroup): Color {
    return when {
        group.generationStatus == "FAILED" -> WarningAmber
        group.executionStatus == "IN_PROGRESS" -> RouteTeal
        group.executionStatus == "COMPLETED" -> InfoCyan
        else -> AreaGreen
    }
}

private fun routeChipAccentColor(routeCode: String): Color {
    return when (routeCode.uppercase()) {
        "A" -> RouteTeal
        "B" -> InfoCyan
        "C" -> AreaGreen
        else -> WarningAmber
    }
}

private fun routePlaceholderStopCount(route: RouteHistoryRouteSummary): Int {
    return when (route.routeCode.uppercase()) {
        "A" -> 12
        "B" -> 10
        "C" -> 9
        else -> 8
    }
}

private fun RouteHistoryGroup.withOnlyActiveRoute(): RouteHistoryGroup? {
    val walkedRouteCode = this.activeRouteCode ?: return null
    val walkedRoute = this.routes.firstOrNull { route -> route.routeCode == walkedRouteCode } ?: return null
    return this.copy(routes = listOf(walkedRoute))
}

private fun formatHistorySubtitle(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "SUCCESS" -> "${formatCreatedAt(group.createdAt)} · ${group.routes.size} 条路线"
        "FAILED" -> "${formatCreatedAt(group.createdAt)} · 生成失败"
        else -> "${formatCreatedAt(group.createdAt)} · ${formatGenerationStage(group.generationStage)}"
    }
}

private fun formatHistoryProgressText(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "FAILED" -> "路线生成失败，请稍后重试"
        "PENDING" -> "正在等待路线生成"
        else -> formatGenerationStage(group.generationStage)
    }
}

private fun formatGenerationStage(stage: String?): String {
    return when (stage) {
        "queued" -> "正在准备路线生成"
        "validateRouteRequest" -> "正在检查路线条件"
        "resolveArea" -> "正在确定搜索范围"
        "loadInterestTags" -> "正在匹配兴趣偏好"
        "loadUserPreferenceProfile" -> "正在读取个人偏好"
        "loadPoiSemanticMappings" -> "正在整理地点类型"
        "loadRouteWeather" -> "正在检查天气影响"
        "loadPoiCandidates" -> "正在寻找可用地点"
        "enrichPoiDetails" -> "正在补充地点信息"
        "selectPoiPool" -> "正在筛选候选地点"
        "buildCandidateRoutes" -> "正在生成路线"
        "scoreAndSelectRoutes" -> "正在筛选路线"
        "calibrateSelectedRouteSegments" -> "正在校准路线"
        "filterCalibratedRoutes" -> "正在确认路线可用性"
        "saveRoutePreferenceTrainingSamples" -> "正在保存路线结果"
        "completed" -> "路线生成已结束"
        else -> "正在更新路线状态"
    }
}

private fun formatCreatedAt(createdAt: String): String {
    if (createdAt.isBlank()) {
        return "刚刚生成"
    }
    return runCatching {
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(Instant.parse(createdAt).atZone(ROUTE_HISTORY_ZONE))
    }.getOrElse {
        createdAt
            .substringBefore(".")
            .replace("T", " ")
            .removeSuffix("Z")
            .ifBlank { "刚刚生成" }
    }
}

private fun formatCreatedDate(createdAt: String): String {
    if (createdAt.isBlank()) {
        return "刚刚"
    }
    return runCatching {
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .format(Instant.parse(createdAt).atZone(ROUTE_HISTORY_ZONE))
    }.getOrElse {
        createdAt
            .substringBefore(" ")
            .substringBefore("T")
            .ifBlank { "刚刚" }
    }
}

private val ROUTE_HISTORY_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

private const val DEFAULT_SHARE_TEXT = "这条路线走下来很顺，适合直接照着走。"

private const val MAX_SHARE_TEXT_LENGTH = 240

private enum class RouteLibraryTab(
    val label: String,
    val sectionTitle: String,
    val emptyTitle: String,
    val emptyDescription: String
) {
    Generated(
        label = "生成结果",
        sectionTitle = "全部生成组",
        emptyTitle = "没有待查看的生成路线",
        emptyDescription = "生成后的路线会先放在这里，开始并完成后会进入走过路线。"
    ),
    Walked(
        label = "走过路线",
        sectionTitle = "已经走完的路线",
        emptyTitle = "还没有走完路线",
        emptyDescription = "完成最后一个打卡点后，路线会沉淀到这里。"
    )
}
