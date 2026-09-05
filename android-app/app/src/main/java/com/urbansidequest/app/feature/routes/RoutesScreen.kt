package com.urbansidequest.app.feature.routes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.AreaGreen
import com.urbansidequest.app.ui.theme.AreaGreenSurface
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.DeepTealDark
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import com.urbansidequest.app.ui.theme.WarningSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun RoutesScreen(
    historyGroups: List<RouteHistoryGroup> = emptyList(),
    routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    routeInteractionKey: (String, String) -> String = { candidateSetId, routeCode -> "$candidateSetId:$routeCode" },
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMoreHistory: Boolean = false,
    errorMessage: String? = null,
    onOpenHistoryGroup: (String) -> Unit = {},
    onOpenHistoryRoute: (String, String) -> Unit = { _, _ -> },
    onToggleRouteFavorite: (String, String, String) -> Unit = { _, _, _ -> },
    onShareWalkedRoute: (String, String, String) -> Unit = { _, _, _ -> },
    onRefreshHistory: () -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    routesViewModel: RoutesViewModel = viewModel()
) {
    val uiState by routesViewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab = uiState.selectedTab
    val generatedFilter = uiState.generatedFilter
    val shareTarget = uiState.shareTarget
    val shareText = uiState.shareText
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isMoreMenuExpanded by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(routesViewModel) {
        onDispose {
            routesViewModel.resetUiState()
        }
    }
    val activeGroup = historyGroups.firstOrNull {
        it.generationStatus == "SUCCESS" && it.executionStatus == "IN_PROGRESS" && it.activeRouteCode != null
    }
    val featuredGroup = activeGroup
        ?: historyGroups.firstOrNull { group ->
            group.routes.isNotEmpty() && group.executionStatus != "COMPLETED"
        }
        ?: historyGroups.firstOrNull { group -> group.routes.isNotEmpty() }
    val featuredRoute = featuredGroup?.let { group ->
        group.routes.firstOrNull { route -> route.routeCode == group.activeRouteCode }
            ?: group.routes.firstOrNull { route -> route.routeCode == "A" }
            ?: group.routes.firstOrNull()
    }
    val allWalkedGroups = historyGroups.mapNotNull { group ->
        if (group.generationStatus == "SUCCESS" && group.executionStatus == "COMPLETED") {
            group.withOnlyActiveRoute()
        } else {
            null
        }
    }
    val countedGeneratedGroups = historyGroups.filter { group ->
        group.requestId != activeGroup?.requestId
    }
    val generatedBaseGroups = countedGeneratedGroups.filter { group ->
        group.requestId != featuredGroup?.requestId
    }
    val normalizedSearchQuery = searchQuery.trim()
    val matchesSearchQuery: (RouteHistoryGroup) -> Boolean = { group ->
        normalizedSearchQuery.isBlank() ||
            group.areaLabel.contains(normalizedSearchQuery, ignoreCase = true) ||
            group.routes.any { route ->
                route.routeCode.contains(normalizedSearchQuery, ignoreCase = true) ||
                    route.title.contains(normalizedSearchQuery, ignoreCase = true)
            }
    }
    val generatedGroups = generatedBaseGroups
        .filter(generatedFilter::matches)
        .filter(matchesSearchQuery)
    val walkedGroups = allWalkedGroups.filter(matchesSearchQuery)
    val visibleGroups = when (selectedTab) {
        RouteLibraryTab.Generated -> generatedGroups
        RouteLibraryTab.Walked -> walkedGroups
    }
    val generatedRouteCount = countedGeneratedGroups.sumOf { group -> group.routes.size }
    val walkedRouteCount = allWalkedGroups.sumOf { group -> group.routes.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteLibraryHeader(
                        isSearchVisible = isSearchVisible,
                        isMoreMenuExpanded = isMoreMenuExpanded,
                        onToggleSearch = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) {
                                searchQuery = ""
                            }
                        },
                        onShowMoreMenu = { isMoreMenuExpanded = true },
                        onDismissMoreMenu = { isMoreMenuExpanded = false },
                        onRefreshHistory = {
                            isMoreMenuExpanded = false
                            onRefreshHistory()
                        },
                        onOpenMap = {
                            isMoreMenuExpanded = false
                            onOpenMap()
                        }
                    )
                    if (isSearchVisible) {
                        RouteLibrarySearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
                    }
                }
            }

            if (isLoading && historyGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "正在加载路线历史",
                        description = "正在同步你已生成的城市副本路线。"
                    )
                }
            } else if (errorMessage != null && historyGroups.isEmpty()) {
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
                if (isLoading) {
                    item {
                        Text(
                            text = "正在刷新路线历史…",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage,
                            color = WarningAmber,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (featuredGroup != null && featuredRoute != null) {
                    item(key = "featured_${featuredGroup.requestId}_${featuredRoute.routeCode}") {
                        FeaturedRoutePanel(
                            group = featuredGroup,
                            route = featuredRoute,
                            isActive = featuredGroup.requestId == activeGroup?.requestId,
                            onOpenRoute = {
                                onOpenHistoryRoute(featuredGroup.requestId, featuredRoute.routeCode)
                            }
                        )
                    }
                }
                item {
                    RouteLibrarySwitcher(
                        selectedTab = selectedTab,
                        generatedCount = generatedRouteCount,
                        walkedCount = walkedRouteCount,
                        onSelectTab = routesViewModel::selectTab
                    )
                }
                when (selectedTab) {
                    RouteLibraryTab.Walked -> {
                        if (visibleGroups.isEmpty()) {
                            item(key = "empty_${selectedTab.name}") {
                                EmptyState(
                                    title = selectedTab.emptyTitle,
                                    description = selectedTab.emptyDescription,
                                    illustrationResId = R.drawable.illustration_empty_routes
                                )
                            }
                        } else {
                            item {
                                WalkedRoutesSummaryCard(groups = walkedGroups)
                            }
                            item {
                                SectionHeader(
                                    title = selectedTab.sectionTitle,
                                    trailingText = "最近完成"
                                )
                            }
                            items(
                                items = visibleGroups,
                                key = { group -> "walked_${group.requestId}" },
                                contentType = { "walked_route" }
                            ) { group ->
                                val route = group.routes.firstOrNull()
                                if (route != null) {
                                    WalkedRouteRow(
                                        group = group,
                                        route = route,
                                        isFavorite = routeInteractions[
                                            routeInteractionKey(group.candidateSetId, route.routeCode)
                                        ]?.isFavorite == true,
                                        onOpenRoute = {
                                            onOpenHistoryRoute(group.requestId, route.routeCode)
                                        },
                                        onToggleFavorite = {
                                            onToggleRouteFavorite(
                                                group.requestId,
                                                group.candidateSetId,
                                                route.routeCode
                                            )
                                        },
                                        onShareRoute = {
                                            routesViewModel.openShareDialog(
                                                WalkedShareTarget(
                                                    group.requestId,
                                                    route.routeCode,
                                                    route.title
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    RouteLibraryTab.Generated -> {
                        item {
                            GeneratedFilterSectionHeader(
                                title = "最近生成",
                                selectedFilter = generatedFilter,
                                onSelectFilter = routesViewModel::selectGeneratedFilter
                            )
                        }
                        if (visibleGroups.isEmpty()) {
                            item(key = "empty_${selectedTab.name}_${generatedFilter.name}") {
                                EmptyState(
                                    title = if (generatedFilter == GeneratedRouteFilter.All) {
                                        selectedTab.emptyTitle
                                    } else {
                                        "“${generatedFilter.label}”中没有路线"
                                    },
                                    description = if (generatedFilter == GeneratedRouteFilter.All) {
                                        selectedTab.emptyDescription
                                    } else {
                                        "可以切换上方筛选，查看其他路线记录。"
                                    },
                                    illustrationResId = R.drawable.illustration_empty_routes
                                )
                            }
                        } else {
                            items(
                                items = visibleGroups,
                                key = { "${selectedTab.name}_${it.requestId}" },
                                contentType = { "generated_group" }
                            ) { group ->
                                RouteHistoryGroupRow(
                                    group = group,
                                    onOpenGroup = {
                                        if (group.generationStatus == "FAILED") {
                                            onOpenMap()
                                        } else {
                                            onOpenHistoryGroup(group.requestId)
                                        }
                                    },
                                    onOpenRoute = { routeCode ->
                                        onOpenHistoryRoute(group.requestId, routeCode)
                                    }
                                )
                            }
                        }
                    }
                }
                if (isLoading || isLoadingMore) {
                    item {
                        Text(
                            text = "正在加载更多路线…",
                            modifier = Modifier.fillMaxWidth(),
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (hasMoreHistory) {
                    item {
                        UrbanSecondaryButton(
                            text = "加载更多路线",
                            onClick = onLoadMoreHistory
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
            onShareTextChange = routesViewModel::changeShareText,
            onDismiss = routesViewModel::dismissShareDialog,
            onConfirm = {
                onShareWalkedRoute(target.requestId, target.routeCode, shareText)
                routesViewModel.dismissShareDialog()
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
    isLoadingMore: Boolean = false,
    hasMoreHistory: Boolean = false,
    errorMessage: String? = null,
    onOpenFavoriteRoute: (String, String) -> Unit = { _, _ -> },
    onRefreshHistory: () -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
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

            if (isLoading && historyGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "正在加载收藏路线",
                        description = "正在同步你收藏过的路线。"
                    )
                }
            } else if (errorMessage != null && historyGroups.isEmpty()) {
                item {
                    EmptyState(
                        title = "收藏路线加载失败",
                        description = errorMessage
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrbanPrimaryButton(text = "重新加载", onClick = onRefreshHistory)
                }
            } else {
                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage,
                            color = WarningAmber,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (favoriteGroups.isEmpty()) {
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
                }
                if (isLoading || isLoadingMore) {
                    item {
                        Text(
                            text = "正在加载更多路线…",
                            modifier = Modifier.fillMaxWidth(),
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (hasMoreHistory) {
                    item {
                        UrbanSecondaryButton(
                            text = "加载更多路线",
                            onClick = onLoadMoreHistory
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
        color = AppSurfaceMuted
    ) {
        Row(
            modifier = Modifier.padding(start = 3.dp, top = 3.dp, end = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RouteLibraryTab.values().forEach { tab ->
                val selected = tab == selectedTab
                val count = when (tab) {
                    RouteLibraryTab.Generated -> generatedCount
                    RouteLibraryTab.Walked -> walkedCount
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            color = if (selected) AppSurface else Color.Transparent,
                            shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
                        )
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable { onSelectTab(tab) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tab.label}  $count",
                            color = if (selected) DeepTeal else AppTextMuted.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (selected) RouteTeal else Color.Transparent,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteLibraryHeader(
    isSearchVisible: Boolean,
    isMoreMenuExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onShowMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRefreshHistory: () -> Unit,
    onOpenMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "路线",
            modifier = Modifier.weight(1f),
            color = AppText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (isSearchVisible) "关闭路线搜索" else "搜索路线",
                modifier = Modifier.size(24.dp),
                tint = if (isSearchVisible) RouteTeal else AppTextMuted
            )
        }
        Box {
            IconButton(onClick = onShowMoreMenu) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多路线操作",
                    modifier = Modifier.size(24.dp),
                    tint = AppTextMuted
                )
            }
            DropdownMenu(
                expanded = isMoreMenuExpanded,
                onDismissRequest = onDismissMoreMenu
            ) {
                DropdownMenuItem(
                    text = { Text("刷新路线") },
                    onClick = onRefreshHistory
                )
                DropdownMenuItem(
                    text = { Text("去地图生成路线") },
                    onClick = onOpenMap
                )
            }
        }
    }
}

@Composable
private fun RouteLibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = "搜索路线名称或区域",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AppTextMuted
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            focusedContainerColor = AppSurface,
            unfocusedContainerColor = AppSurface,
            focusedBorderColor = RouteTeal,
            unfocusedBorderColor = AppBorder,
            cursorColor = RouteTeal
        )
    )
}

@Composable
internal fun RouteLibraryImageIcon(
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
private fun FeaturedRoutePanel(
    group: RouteHistoryGroup,
    route: RouteHistoryRouteSummary,
    isActive: Boolean,
    onOpenRoute: () -> Unit
) {
    val statusText = when {
        isActive -> "正在进行"
        group.executionStatus == "COMPLETED" -> "最近完成"
        group.generationStatus == "PARTIAL_SUCCESS" -> "部分生成"
        else -> "最近生成"
    }
    val actionText = if (isActive) "继续路线" else "查看路线"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DeepTeal
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
        ) {
            RouteMapSnapshot(
                route = route,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                DeepTealDark.copy(alpha = 0.78f),
                                DeepTeal.copy(alpha = 0.42f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppSurface.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = AppSurface.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = route.title,
                            modifier = Modifier.weight(1f),
                            color = AppSurface.copy(alpha = 0.96f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${formatCompactDistance(route.totalDistanceMeters)} · " +
                            "${formatDuration(route.totalDurationMinutes)} · " +
                            formatStopCount(route.stopCount),
                        color = AppSurface.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onOpenRoute,
                        modifier = Modifier
                            .height(44.dp)
                            .sizeIn(minWidth = 120.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppSurface,
                            contentColor = DeepTeal
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailingText: String
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
                color = AppTextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GeneratedFilterSectionHeader(
    title: String,
    selectedFilter: GeneratedRouteFilter,
    onSelectFilter: (GeneratedRouteFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GeneratedRouteFilter.values().forEach { filter ->
                val selected = filter == selectedFilter
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .semantics {
                            role = Role.Button
                            this.selected = selected
                        }
                        .clickable { onSelectFilter(filter) },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            RouteTeal.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                RouteTeal.copy(alpha = 0.50f)
                            } else {
                                AppBorder.copy(alpha = 0.55f)
                            }
                        )
                    ) {
                        Text(
                            text = filter.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = if (selected) DeepTeal else AppTextMuted.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteHistoryGroupRow(
    group: RouteHistoryGroup,
    onOpenGroup: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    val primaryRoute = group.routes.firstOrNull { route -> route.routeCode == "A" }
        ?: group.routes.firstOrNull()
    val canOpenRoutes = group.routes.isNotEmpty()
    val failed = group.generationStatus == "FAILED"
    val canOpenGroup = canOpenRoutes || failed
    val showSnapshot = group.executionStatus == "COMPLETED" &&
        primaryRoute?.mapSnapshotUrl != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpenGroup, onClick = onOpenGroup)
            .padding(top = 2.dp)
    ) {
        Text(
            text = formatCreatedAt(group.createdAt),
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryRoute?.title ?: group.areaLabel,
                modifier = Modifier.weight(1f),
                color = AppText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RouteHistoryStatusBadge(group = group)
            if (canOpenGroup) {
                RouteLibraryImageIcon(
                    iconRes = R.drawable.icon_routes_chevron_right,
                    contentDescription = if (failed) "重新配置路线" else "查看生成组",
                    modifier = Modifier.size(20.dp),
                    tint = AppTextMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        if (primaryRoute != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        group.routes.forEach { route ->
                            GeneratedRouteLabel(
                                route = route,
                                isActive = route.routeCode == group.activeRouteCode,
                                onClick = { onOpenRoute(route.routeCode) }
                            )
                        }
                    }
                    Text(
                        text = "${formatCompactDistance(primaryRoute.totalDistanceMeters)} · " +
                        "${formatDuration(primaryRoute.totalDurationMinutes)} · " +
                            formatStopCount(primaryRoute.stopCount),
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showSnapshot) {
                    RouteMapSnapshot(
                        route = primaryRoute,
                        modifier = Modifier.size(width = 96.dp, height = 64.dp),
                        contentDescription = "${primaryRoute.title}路线快照"
                    )
                }
            }
        } else {
            Text(
                text = if (failed) "调整条件后重新生成" else formatHistoryProgressText(group),
                color = if (failed) WarningAmber else AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = AppBorder.copy(alpha = 0.58f))
    }
}

@Composable
private fun RouteHistoryStatusBadge(group: RouteHistoryGroup) {
    val isPartial = group.generationStatus == "PARTIAL_SUCCESS"
    val failed = group.generationStatus == "FAILED"
    val completed = group.executionStatus == "COMPLETED"
    val inProgress = group.executionStatus == "IN_PROGRESS"
    val containerColor = when {
        failed || isPartial -> WarningSurface
        completed -> AreaGreenSurface
        inProgress -> DeepTeal
        else -> AppSurfaceMuted
    }
    val contentColor = when {
        failed || isPartial -> WarningAmber
        completed -> AreaGreen
        inProgress -> AppSurface
        else -> AppTextMuted
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = formatHistoryStatus(group),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun GeneratedRouteLabel(
    route: RouteHistoryRouteSummary,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val routeAccent = routeChipAccentColor(route.routeCode)
    Box(
        modifier = Modifier
            .widthIn(max = 174.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = routeAccent.copy(alpha = if (isActive) 0.13f else 0.07f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.routeCode,
                    color = routeAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = route.title,
                    modifier = Modifier.weight(1f, fill = false),
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
