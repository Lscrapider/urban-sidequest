package com.urbansidequest.app.feature.routeconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.R
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanMetricGrid
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanQuestLoadingCard
import com.urbansidequest.app.ui.components.UrbanSearchField
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.components.WarningBanner
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteConfigScreen(
    routeRepository: RouteRepository? = null,
    selectedCenter: GeoPoint? = null,
    onBack: () -> Unit = {},
    onSubmitRouteGeneration: (RouteGenerateRequest) -> Unit = {},
    routeConfigViewModel: RouteConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val submitScope = rememberCoroutineScope()
    val uiState by routeConfigViewModel.uiState.collectAsStateWithLifecycle()
    val validationMessage = uiState.validateForRouteRequest(
        selectedCenter = selectedCenter,
        routeRepositoryAvailable = routeRepository != null
    )
    val canGenerate = validationMessage == null && !uiState.isGenerating
    val feasibleMealWindows = uiState.feasibleMealWindowCodes()
    val feasibleMealWindowOptions = MealWindowOptions.filter { option -> option.code in feasibleMealWindows }

    LaunchedEffect(routeConfigViewModel) {
        routeConfigViewModel.events.collectLatest { event ->
            when (event) {
                is RouteConfigEvent.RouteGenerationSubmitted -> {
                    onSubmitRouteGeneration(event.request)
                    onBack()
                }
            }
        }
    }

    LaunchedEffect(selectedCenter) {
        routeConfigViewModel.reset()
    }

    LaunchedEffect(uiState.mustVisitSearchText, selectedCenter) {
        routeConfigViewModel.searchMustVisitSuggestions(
            context = context,
            selectedCenter = selectedCenter
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(
            subtitle = "路线条件",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "路线生成",
                title = "生成路线条件",
                trailing = {
                    UrbanBadge(
                        text = "自动范围",
                        style = UrbanBadgeStyle.Area
                    )
                }
            )

            UrbanTaskCard(highlighted = true) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RouteMapPreview(
                        label = if (selectedCenter == null) "待选择" else "地图选区",
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "当前区域",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = if (selectedCenter == null) "请先从地图选点" else "地图中心点已确认",
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "系统会按出发时间、可用时长和交通组合自动确定搜索范围。",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                UrbanMetricGrid(
                    items = listOf(
                        uiState.selectedDuration.label to "可用时长",
                        uiState.selectedTransport.label to "交通组合",
                        uiState.selectedBudget.label to "预算偏好"
                    )
                )
            }

            UrbanSection {
                SectionTitle(title = "路线窗口", subtitle = "用于判断饭点、营业风险和路线密度")
                FieldLabel(text = "出发时间")
                OptionFlow {
                    DepartureOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedDeparture,
                            onClick = { routeConfigViewModel.selectDeparture(option) }
                        )
                    }
                }
                FieldLabel(text = "可用时长")
                OptionFlow {
                    DurationOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedDuration,
                            onClick = { routeConfigViewModel.selectDuration(option) }
                        )
                    }
                }
                if (feasibleMealWindowOptions.isNotEmpty()) {
                    FieldLabel(text = "饭点安排")
                    OptionFlow {
                        feasibleMealWindowOptions.forEach { option ->
                            SelectableChip(
                                text = option.label,
                                selected = uiState.selectedMealWindows.contains(option.code),
                                onClick = { routeConfigViewModel.toggleMealWindow(option.code) }
                            )
                        }
                    }
                }
                Text(
                    text = "午餐窗口 11:30-13:30，晚餐窗口 17:30-20:00；超出当前路线窗口会被拦截。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            UrbanSection {
                SectionTitle(title = "路线策略", subtitle = "按出行方式、路线目标和预算控制生成结果")
                FieldLabel(text = "交通组合")
                OptionFlow {
                    TransportOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedTransport,
                            onClick = { routeConfigViewModel.selectTransport(option) }
                        )
                    }
                }
                FieldLabel(text = "路线目标")
                OptionFlow {
                    RouteGoalOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedGoal,
                            onClick = { routeConfigViewModel.selectGoal(option) }
                        )
                    }
                }
                FieldLabel(text = "预算偏好")
                OptionFlow {
                    BudgetOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedBudget,
                            onClick = { routeConfigViewModel.selectBudget(option) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "兴趣偏好", subtitle = "最多 5 个兴趣大类；餐饮偏好需要选择午餐或晚餐")
                FieldLabel(text = "路线兴趣")
                OptionFlow {
                    NonFoodInterestTagOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = uiState.selectedInterestTags.contains(option.code),
                            onClick = { routeConfigViewModel.toggleInterestTag(option.code) }
                        )
                    }
                }
                if (uiState.selectedMealWindows.isNotEmpty()) {
                    FieldLabel(text = "餐饮偏好")
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FoodInterestGroups.forEach { group ->
                            FoodInterestGroupPicker(
                                group = group,
                                selectedTags = uiState.selectedInterestTags,
                                onToggleTag = routeConfigViewModel::toggleInterestTag
                            )
                        }
                    }
                }
                if (uiState.hasFoodInterest() && uiState.selectedMealWindows.isEmpty()) {
                    WarningBanner(text = "选择餐饮偏好时，需要同时选择午餐或晚餐饭点。")
                }
            }

            UrbanSection {
                SectionTitle(title = "必去点", subtitle = "搜索地点加入本次路线")
                MustVisitPicker(
                    searchText = uiState.mustVisitSearchText,
                    suggestions = uiState.mustVisitSuggestions,
                    isSearching = uiState.isMustVisitSearching,
                    selectedPoints = uiState.mustVisitPoints,
                    onSearchTextChange = routeConfigViewModel::onMustVisitSearchTextChange,
                    onSelectSuggestion = { suggestion ->
                        routeConfigViewModel.addMustVisitSuggestion(suggestion)
                        focusManager.clearFocus()
                    },
                    onRemovePoint = routeConfigViewModel::removeMustVisitPoint
                )
            }

            if (validationMessage != null) {
                WarningBanner(text = validationMessage)
            }

            if (uiState.isGenerating) {
                UrbanQuestLoadingCard(
                    title = "正在生成路线 A",
                    subtitle = "根据当前区域、时间窗口和兴趣偏好组合路线",
                    statusText = "正在生成候选路线和地图节点",
                    badgeText = "生成中",
                    illustrationResId = R.drawable.illustration_route_generating
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrbanPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canGenerate,
                    text = "生成路线",
                    onClick = {
                        submitScope.launch {
                            routeConfigViewModel.submitRouteGeneration(
                                routeRepositoryAvailable = routeRepository != null,
                                selectedCenter = selectedCenter
                            )
                        }
                    }
                )
                Text(
                    text = validationMessage
                        ?: uiState.errorMessage
                        ?: "会按当前范围、出发窗口、饭点、策略、预算、兴趣和必去点生成路线。",
                    color = if (validationMessage == null && uiState.errorMessage == null) {
                        AppTextMuted
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    UrbanChip(
        text = text,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun FoodInterestGroupPicker(
    group: FoodInterestGroup,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = group.option.label,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            SelectableChip(
                text = "偏好${group.option.label}",
                selected = selectedTags.contains(group.option.code),
                onClick = { onToggleTag(group.option.code) }
            )
        }
        if (group.children.isNotEmpty()) {
            OptionFlow {
                group.children.forEach { option ->
                    SelectableChip(
                        text = option.label,
                        selected = selectedTags.contains(option.code),
                        onClick = { onToggleTag(option.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MustVisitPicker(
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    selectedPoints: List<RouteMustVisitPoint>,
    onSearchTextChange: (String) -> Unit,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit,
    onRemovePoint: (RouteMustVisitPoint) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MustVisitSearchField(
            searchText = searchText,
            onSearchTextChange = onSearchTextChange
        )
        if (searchText.isNotBlank()) {
            MustVisitSuggestionsPanel(
                searchText = searchText,
                suggestions = suggestions,
                isSearching = isSearching,
                onSelectSuggestion = onSelectSuggestion
            )
        }
        if (selectedPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "未添加必去点，路线会按兴趣偏好自动生成。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedPoints.forEach { point ->
                    MustVisitPointRow(
                        point = point,
                        onRemove = { onRemovePoint(point) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MustVisitSearchField(
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    UrbanSearchField(
        value = searchText,
        onValueChange = onSearchTextChange,
        placeholder = "搜索并加入必去点",
        trailingIcon = {
            if (searchText.isNotBlank()) {
                IconButton(onClick = { onSearchTextChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空必去点搜索",
                        tint = AppTextMuted
                    )
                }
            }
        }
    )
}

@Composable
private fun MustVisitSuggestionsPanel(
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            when {
                searchText.trim().length < 2 -> MustVisitHint(text = "输入至少 2 个字搜索地点")
                isSearching -> MustVisitHint(text = "正在搜索")
                suggestions.isEmpty() -> MustVisitHint(text = "没有找到可定位的地点")
                else -> suggestions.forEach { suggestion ->
                    MustVisitSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onSelectSuggestion(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MustVisitHint(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun MustVisitSuggestionRow(
    suggestion: PlaceSearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = DeepTeal
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (suggestion.description.isNotBlank()) {
                Text(
                    text = suggestion.description,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MustVisitPointRow(
    point: RouteMustVisitPoint,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = DeepTeal
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.name,
                    color = AppText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "必去 · 已加入路线约束",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除必去点",
                    tint = AppTextMuted
                )
            }
        }
    }
}
