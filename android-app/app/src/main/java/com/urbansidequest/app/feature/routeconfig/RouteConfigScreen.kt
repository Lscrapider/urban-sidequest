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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.MustVisitPointRequest
import com.urbansidequest.app.data.api.RouteApiException
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.RouteCityInfo
import com.urbansidequest.app.data.map.resolveRouteCityInfo
import com.urbansidequest.app.data.map.searchAmapInputTips
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteConfigScreen(
    routeRepository: RouteRepository? = null,
    selectedCenter: GeoPoint? = null,
    onBack: () -> Unit = {},
    onAuthExpired: () -> Unit = {},
    onGenerateRoute: (RouteGeneration?) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var selectedDeparture by remember { mutableStateOf(DepartureOptions.first()) }
    var selectedDuration by remember { mutableStateOf(DurationOptions[1]) }
    var selectedTransport by remember { mutableStateOf(TransportOptions[1]) }
    var selectedGoal by remember { mutableStateOf(RouteGoalOptions.first()) }
    var selectedInterestTags by remember { mutableStateOf(setOf("MUSEUM", "SCENIC")) }
    var mustVisitSearchText by remember { mutableStateOf("") }
    var mustVisitSuggestions by remember { mutableStateOf<List<PlaceSearchSuggestion>>(emptyList()) }
    var isMustVisitSearching by remember { mutableStateOf(false) }
    var mustVisitPoints by remember { mutableStateOf<List<MustVisitPointRequest>>(emptyList()) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canGenerate = selectedCenter != null && routeRepository != null && !isGenerating

    LaunchedEffect(mustVisitSearchText, selectedCenter) {
        val keyword = mustVisitSearchText.trim()
        if (keyword.length < 2) {
            mustVisitSuggestions = emptyList()
            isMustVisitSearching = false
            return@LaunchedEffect
        }
        isMustVisitSearching = true
        delay(250)
        searchAmapInputTips(
            context = context,
            keyword = keyword,
            location = selectedCenter?.toLatLng() ?: DefaultSearchCenter,
            onResult = { resultKeyword, suggestions ->
                if (resultKeyword == mustVisitSearchText.trim()) {
                    mustVisitSuggestions = suggestions
                    isMustVisitSearching = false
                }
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
                            selected = option == selectedDeparture,
                            onClick = { selectedDeparture = option }
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
                            selected = option == selectedDuration,
                            onClick = { selectedDuration = option }
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
                            selected = option == selectedTransport,
                            onClick = { selectedTransport = option }
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
                            selected = option == selectedGoal,
                            onClick = { selectedGoal = option }
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
                            selected = selectedInterestTags.contains(option.code),
                            onClick = {
                                selectedInterestTags = if (selectedInterestTags.contains(option.code)) {
                                    selectedInterestTags - option.code
                                } else {
                                    selectedInterestTags + option.code
                                }
                            }
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "必去点", subtitle = "搜索地点加入路线硬约束")
                MustVisitPicker(
                    searchText = mustVisitSearchText,
                    suggestions = mustVisitSuggestions,
                    isSearching = isMustVisitSearching,
                    selectedPoints = mustVisitPoints,
                    onSearchTextChange = { mustVisitSearchText = it },
                    onSelectSuggestion = { suggestion ->
                        val point = suggestion.toMustVisitPoint()
                        val exists = mustVisitPoints.any { it.isSamePlace(point) }
                        if (!exists) {
                            mustVisitPoints = mustVisitPoints + point
                        }
                        mustVisitSearchText = ""
                        mustVisitSuggestions = emptyList()
                        isMustVisitSearching = false
                        focusManager.clearFocus()
                    },
                    onRemovePoint = { point ->
                        mustVisitPoints = mustVisitPoints.filterNot { it.isSamePlace(point) }
                    }
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
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canGenerate,
                    onClick = {
                        val center = selectedCenter ?: return@Button
                        val repository = routeRepository ?: return@Button
                        coroutineScope.launch {
                            isGenerating = true
                            errorMessage = null
                            runCatching {
                                val routeCityInfo = resolveRouteCityInfo(
                                    context = context,
                                    location = LatLng(center.latitudeGcj02, center.longitudeGcj02)
                                )
                                repository.generateRoute(
                                    buildRequest(
                                        center = center,
                                        routeCityInfo = routeCityInfo,
                                        departureOption = selectedDeparture,
                                        durationOption = selectedDuration,
                                        transportOption = selectedTransport,
                                        goalOption = selectedGoal,
                                        interestTags = selectedInterestTags.toList(),
                                        mustVisitPoints = mustVisitPoints
                                    )
                                )
                            }.onSuccess { routeGeneration ->
                                onGenerateRoute(routeGeneration)
                            }.onFailure { throwable ->
                                errorMessage = throwable.message ?: "路线生成失败，请稍后重试"
                                if (throwable is RouteApiException && throwable.isAuthenticationError) {
                                    onAuthExpired()
                                }
                            }
                            isGenerating = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isGenerating) "正在生成..." else "生成路线",
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = errorMessage ?: if (selectedCenter == null) {
                        "请先从地图页确认区域。"
                    } else {
                        "路线会由后端生成，前端只提交结构化条件。"
                    },
                    color = if (errorMessage == null) AppTextMuted else MaterialTheme.colorScheme.error,
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
        modifier = Modifier.clickable(onClick = onClick)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppTextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                modifier = Modifier.weight(1f),
                value = searchText,
                onValueChange = onSearchTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                decorationBox = { innerTextField ->
                    if (searchText.isBlank()) {
                        Text(
                            text = "搜索并加入必去点",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    innerTextField()
                }
            )
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
    }
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

private fun buildRequest(
    center: GeoPoint,
    routeCityInfo: RouteCityInfo?,
    departureOption: DepartureOption,
    durationOption: DurationOption,
    transportOption: CodeOption,
    goalOption: CodeOption,
    interestTags: List<String>,
    mustVisitPoints: List<MustVisitPointRequest>
): RouteGenerateRequest {
    return RouteGenerateRequest(
        areaMode = "AUTO_RADIUS",
        areaLabel = "地图选区",
        center = center,
        areaPolygonGcj02 = emptyList(),
        routeCityName = routeCityInfo?.cityName,
        routeCityAdcode = routeCityInfo?.cityAdcode,
        departureTime = departureOption.toInstantString(),
        durationMinutes = durationOption.minutes,
        transportProfile = transportOption.code,
        routeGoal = goalOption.code,
        interestTags = interestTags,
        mustVisitPoints = mustVisitPoints
    )
}

private fun PlaceSearchSuggestion.toMustVisitPoint(): MustVisitPointRequest {
    return MustVisitPointRequest(
        name = name,
        amapPoiId = amapPoiId,
        location = GeoPoint(
            longitudeGcj02 = location.longitude,
            latitudeGcj02 = location.latitude
        ),
        priority = "MUST"
    )
}

private fun MustVisitPointRequest.isSamePlace(other: MustVisitPointRequest): Boolean {
    if (amapPoiId != null && other.amapPoiId != null) {
        return amapPoiId == other.amapPoiId
    }
    return name == other.name &&
        location.longitudeGcj02 == other.location.longitudeGcj02 &&
        location.latitudeGcj02 == other.location.latitudeGcj02
}

private fun GeoPoint.toLatLng(): LatLng {
    return LatLng(latitudeGcj02, longitudeGcj02)
}

private fun DepartureOption.toInstantString(): String {
    return LocalDateTime.of(LocalDate.now(RouteZone), time)
        .atZone(RouteZone)
        .toInstant()
        .toString()
}

private data class DepartureOption(val label: String, val time: LocalTime)

private data class DurationOption(val label: String, val minutes: Int)

private data class CodeOption(val label: String, val code: String)

private val RouteZone = ZoneId.of("Asia/Shanghai")
private val DefaultSearchCenter = LatLng(39.908722, 116.397499)

private val DepartureOptions = listOf(
    DepartureOption("上午 10:00", LocalTime.of(10, 0)),
    DepartureOption("中午 12:00", LocalTime.of(12, 0)),
    DepartureOption("下午 14:00", LocalTime.of(14, 0)),
    DepartureOption("傍晚 18:00", LocalTime.of(18, 0))
)

private val DurationOptions = listOf(
    DurationOption("2 小时", 120),
    DurationOption("4 小时", 240),
    DurationOption("8 小时", 480)
)

private val TransportOptions = listOf(
    CodeOption("只步行", "WALK_ONLY"),
    CodeOption("步行 + 地铁", "WALK_SUBWAY"),
    CodeOption("骑车 + 地铁", "BIKE_SUBWAY"),
    CodeOption("步行 + 打车", "WALK_TAXI")
)

private val RouteGoalOptions = listOf(
    CodeOption("稳妥省心", "STEADY"),
    CodeOption("经典必看", "CLASSIC"),
    CodeOption("地道烟火", "LOCAL"),
    CodeOption("低预算", "LOW_BUDGET"),
    CodeOption("夜游", "NIGHT"),
    CodeOption("拍照出片", "PHOTO")
)

private val InterestTagOptions = listOf(
    CodeOption("美食", "FOOD"),
    CodeOption("咖啡休息", "COFFEE"),
    CodeOption("展馆", "MUSEUM"),
    CodeOption("景点", "SCENIC"),
    CodeOption("拍照", "PHOTO"),
    CodeOption("购物", "SHOPPING"),
    CodeOption("夜游", "NIGHT")
)
