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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.MustVisitPointRequest
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.resolveRouteCityInfo
import com.urbansidequest.app.data.map.searchAmapInputTips
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanSearchField
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteConfigScreen(
    routeRepository: RouteRepository? = null,
    selectedCenter: GeoPoint? = null,
    onBack: () -> Unit = {},
    onAuthExpired: () -> Unit = {},
    onGenerateRoute: (RouteGeneration?) -> Unit = {},
    routeConfigViewModel: RouteConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by routeConfigViewModel.uiState.collectAsStateWithLifecycle()
    val canGenerate = selectedCenter != null && routeRepository != null && !uiState.isGenerating

    LaunchedEffect(routeConfigViewModel) {
        routeConfigViewModel.events.collectLatest { event ->
            when (event) {
                RouteConfigEvent.AuthExpired -> onAuthExpired()
                is RouteConfigEvent.RouteGenerated -> onGenerateRoute(event.routeGeneration)
            }
        }
    }

    LaunchedEffect(selectedCenter) {
        routeConfigViewModel.reset()
    }

    LaunchedEffect(uiState.mustVisitSearchText, selectedCenter) {
        val keyword = uiState.mustVisitSearchText.trim()
        if (keyword.length < 2) {
            return@LaunchedEffect
        }
        routeConfigViewModel.onMustVisitSearchStarted()
        delay(250)
        searchAmapInputTips(
            context = context,
            keyword = keyword,
            location = selectedCenter?.toLatLng() ?: DefaultSearchCenter,
            onResult = { resultKeyword, suggestions ->
                routeConfigViewModel.onMustVisitSuggestionsLoaded(resultKeyword, suggestions)
            }
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RouteMapPreview(
                        label = if (selectedCenter == null) "待选择区域" else "已选择区域",
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
                            text = if (selectedCenter == null) "待选择" else "地图选区",
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "路线生成会把区域、时长、交通组合和兴趣偏好一起提交后端。",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "出发时间", subtitle = "用于判断饭点和营业风险")
                OptionFlow {
                    DepartureOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedDeparture,
                            onClick = { routeConfigViewModel.selectDeparture(option) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "可用时长", subtitle = "后端会据此选择默认范围和路线密度")
                OptionFlow {
                    DurationOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedDuration,
                            onClick = { routeConfigViewModel.selectDuration(option) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "交通组合", subtitle = "选项和后端 TransportProfile 保持一致")
                OptionFlow {
                    TransportOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedTransport,
                            onClick = { routeConfigViewModel.selectTransport(option) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "路线目标", subtitle = "选项和后端 RouteGoal 保持一致")
                OptionFlow {
                    RouteGoalOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = option == uiState.selectedGoal,
                            onClick = { routeConfigViewModel.selectGoal(option) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "兴趣偏好", subtitle = "可多选，标签和后端 interest_tag_catalog 对齐")
                OptionFlow {
                    InterestTagOptions.forEach { option ->
                        SelectableChip(
                            text = option.label,
                            selected = uiState.selectedInterestTags.contains(option.code),
                            onClick = { routeConfigViewModel.toggleInterestTag(option.code) }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "必去点", subtitle = "搜索地点加入路线硬约束")
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
                    text = if (uiState.isGenerating) "正在生成..." else "生成路线",
                    onClick = {
                        routeConfigViewModel.generateRoute(
                            routeRepository = routeRepository,
                            selectedCenter = selectedCenter,
                            resolveRouteCityInfo = { center ->
                                resolveRouteCityInfo(
                                    context = context,
                                    location = LatLng(center.latitudeGcj02, center.longitudeGcj02)
                                )
                            }
                        )
                    }
                )
                Text(
                    text = uiState.errorMessage ?: if (selectedCenter == null) {
                        "请先从地图页确认区域。"
                    } else {
                        "路线会由后端生成，前端只提交结构化条件。"
                    },
                    color = if (uiState.errorMessage == null) AppTextMuted else MaterialTheme.colorScheme.error,
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
private fun MustVisitPicker(
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    selectedPoints: List<MustVisitPointRequest>,
    onSearchTextChange: (String) -> Unit,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit,
    onRemovePoint: (MustVisitPointRequest) -> Unit
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
    point: MustVisitPointRequest,
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

private fun GeoPoint.toLatLng(): LatLng {
    return LatLng(latitudeGcj02, longitudeGcj02)
}

private val DefaultSearchCenter = LatLng(39.908722, 116.397499)
