package com.urbansidequest.app.feature.routeconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urbansidequest.app.data.api.MustVisitPointRequest
import com.urbansidequest.app.data.api.RouteApiException
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.RouteCityInfo
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RouteConfigViewModel : ViewModel() {

    private val mutableUiState = MutableStateFlow(RouteConfigUiState())
    val uiState: StateFlow<RouteConfigUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<RouteConfigEvent>()
    val events: SharedFlow<RouteConfigEvent> = mutableEvents.asSharedFlow()

    fun reset() {
        mutableUiState.value = RouteConfigUiState()
    }

    fun selectDeparture(option: DepartureOption) {
        mutableUiState.update { it.copy(selectedDeparture = option) }
    }

    fun selectDuration(option: DurationOption) {
        mutableUiState.update { it.copy(selectedDuration = option) }
    }

    fun selectTransport(option: CodeOption) {
        mutableUiState.update { it.copy(selectedTransport = option) }
    }

    fun selectGoal(option: CodeOption) {
        mutableUiState.update { it.copy(selectedGoal = option) }
    }

    fun selectBudget(option: CodeOption) {
        mutableUiState.update { it.copy(selectedBudget = option) }
    }

    fun toggleMealWindow(code: String) {
        mutableUiState.update { state ->
            state.copy(
                selectedMealWindows = if (state.selectedMealWindows.contains(code)) {
                    state.selectedMealWindows - code
                } else {
                    state.selectedMealWindows + code
                }
            )
        }
    }

    fun toggleInterestTag(code: String) {
        mutableUiState.update { state ->
            state.copy(
                selectedInterestTags = if (state.selectedInterestTags.contains(code)) {
                    state.selectedInterestTags - code
                } else {
                    state.selectedInterestTags + code
                }
            )
        }
    }

    fun onMustVisitSearchTextChange(value: String) {
        mutableUiState.update {
            it.copy(
                mustVisitSearchText = value,
                mustVisitSuggestions = if (value.isBlank()) emptyList() else it.mustVisitSuggestions,
                isMustVisitSearching = value.trim().length >= MIN_SEARCH_KEYWORD_LENGTH
            )
        }
    }

    fun onMustVisitSearchStarted() {
        mutableUiState.update { it.copy(isMustVisitSearching = true) }
    }

    fun onMustVisitSuggestionsLoaded(keyword: String, suggestions: List<PlaceSearchSuggestion>) {
        mutableUiState.update { state ->
            if (state.mustVisitSearchText.trim() != keyword) {
                state
            } else {
                state.copy(mustVisitSuggestions = suggestions, isMustVisitSearching = false)
            }
        }
    }

    fun addMustVisitSuggestion(suggestion: PlaceSearchSuggestion) {
        val point = suggestion.toMustVisitPoint()
        mutableUiState.update { state ->
            val exists = state.mustVisitPoints.any { it.isSamePlace(point) }
            state.copy(
                mustVisitPoints = if (exists) state.mustVisitPoints else state.mustVisitPoints + point,
                mustVisitSearchText = "",
                mustVisitSuggestions = emptyList(),
                isMustVisitSearching = false
            )
        }
    }

    fun removeMustVisitPoint(point: MustVisitPointRequest) {
        mutableUiState.update { state ->
            state.copy(mustVisitPoints = state.mustVisitPoints.filterNot { it.isSamePlace(point) })
        }
    }

    fun generateRoute(
        routeRepository: RouteRepository?,
        selectedCenter: GeoPoint?,
        resolveRouteCityInfo: suspend (GeoPoint) -> RouteCityInfo?
    ) {
        val state = mutableUiState.value
        if (state.isGenerating) {
            return
        }
        val validationMessage = state.validateForRouteRequest(
            selectedCenter = selectedCenter,
            routeRepositoryAvailable = routeRepository != null
        )
        if (validationMessage != null) {
            mutableUiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        val repository = routeRepository ?: return
        val center = selectedCenter ?: return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isGenerating = true, errorMessage = null) }
            runCatching {
                repository.generateRoute(
                    state.buildRequest(
                        center = center,
                        routeCityInfo = resolveRouteCityInfo(center)
                    )
                )
            }.onSuccess { routeGeneration ->
                mutableEvents.emit(RouteConfigEvent.RouteGenerated(routeGeneration))
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(errorMessage = throwable.message ?: "路线生成失败，请稍后重试")
                }
                if (throwable is RouteApiException && throwable.isAuthenticationError) {
                    mutableEvents.emit(RouteConfigEvent.AuthExpired)
                }
            }
            mutableUiState.update { it.copy(isGenerating = false) }
        }
    }

    private companion object {
        private const val MIN_SEARCH_KEYWORD_LENGTH = 2
    }
}

data class RouteConfigUiState(
    val selectedDeparture: DepartureOption = DepartureOptions.first(),
    val selectedDuration: DurationOption = DurationOptions[1],
    val selectedTransport: CodeOption = TransportOptions[1],
    val selectedGoal: CodeOption = RouteGoalOptions.first(),
    val selectedBudget: CodeOption = BudgetOptions[1],
    val selectedMealWindows: Set<String> = emptySet(),
    val selectedInterestTags: Set<String> = setOf("MUSEUM", "SCENIC"),
    val mustVisitSearchText: String = "",
    val mustVisitSuggestions: List<PlaceSearchSuggestion> = emptyList(),
    val isMustVisitSearching: Boolean = false,
    val mustVisitPoints: List<MustVisitPointRequest> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

sealed interface RouteConfigEvent {
    data class RouteGenerated(val routeGeneration: RouteGeneration?) : RouteConfigEvent
    data object AuthExpired : RouteConfigEvent
}

data class DepartureOption(val label: String, val time: LocalTime)

data class DurationOption(val label: String, val minutes: Int)

data class CodeOption(val label: String, val code: String)

val RouteZone: ZoneId = ZoneId.of("Asia/Shanghai")

const val ROUTE_AUTO_RADIUS_METERS = 3_000
const val ROUTE_MIN_RADIUS_METERS = 500
const val ROUTE_MAX_RADIUS_METERS = 15_000

val DepartureOptions = listOf(
    DepartureOption("上午 10:00", LocalTime.of(10, 0)),
    DepartureOption("中午 12:00", LocalTime.of(12, 0)),
    DepartureOption("下午 14:00", LocalTime.of(14, 0)),
    DepartureOption("傍晚 18:00", LocalTime.of(18, 0))
)

val DurationOptions = listOf(
    DurationOption("2 小时", 120),
    DurationOption("4 小时", 240),
    DurationOption("8 小时", 480)
)

val TransportOptions = listOf(
    CodeOption("只步行", "WALK_ONLY"),
    CodeOption("步行 + 地铁", "WALK_SUBWAY"),
    CodeOption("步行 + 公交", "WALK_BUS"),
    CodeOption("混合交通", "WALK_TRANSIT"),
    CodeOption("骑车 + 地铁", "BIKE_SUBWAY"),
    CodeOption("步行 + 打车", "WALK_TAXI")
)

val RouteGoalOptions = listOf(
    CodeOption("稳妥省心", "STEADY"),
    CodeOption("经典必看", "CLASSIC"),
    CodeOption("地道烟火", "LOCAL"),
    CodeOption("安静少打扰", "QUIET"),
    CodeOption("夜游", "NIGHT"),
    CodeOption("拍照出片", "PHOTO")
)

val BudgetOptions = listOf(
    CodeOption("省预算", "LOW"),
    CodeOption("标准", "NORMAL"),
    CodeOption("更灵活", "FLEXIBLE")
)

val MealWindowOptions = listOf(
    CodeOption("午餐", "LUNCH"),
    CodeOption("晚餐", "DINNER")
)

val InterestTagOptions = listOf(
    CodeOption("美食", "FOOD"),
    CodeOption("咖啡休息", "COFFEE"),
    CodeOption("展馆", "MUSEUM"),
    CodeOption("景点", "SCENIC"),
    CodeOption("拍照", "PHOTO"),
    CodeOption("购物", "SHOPPING"),
    CodeOption("夜游", "NIGHT")
)

fun RouteConfigUiState.validateForRouteRequest(
    selectedCenter: GeoPoint?,
    routeRepositoryAvailable: Boolean
): String? {
    if (!routeRepositoryAvailable) {
        return "路线服务暂不可用，请稍后重试"
    }
    if (selectedCenter == null) {
        return "自动范围需要中心点，请先从地图页确认区域"
    }
    selectedCenter.coordinateValidationError()?.let { return it }
    if (ROUTE_AUTO_RADIUS_METERS !in ROUTE_MIN_RADIUS_METERS..ROUTE_MAX_RADIUS_METERS) {
        return "自动范围半径需在 ${ROUTE_MIN_RADIUS_METERS} 到 ${ROUTE_MAX_RADIUS_METERS} 米之间"
    }
    if (selectedDuration.minutes !in MIN_ROUTE_DURATION_MINUTES..MAX_ROUTE_DURATION_MINUTES) {
        return "路线时长需在 ${MIN_ROUTE_DURATION_MINUTES} 到 ${MAX_ROUTE_DURATION_MINUTES} 分钟之间"
    }
    if (selectedTransport.code.isBlank()) {
        return "请选择交通组合"
    }
    if (selectedGoal.code == DEPRECATED_LOW_BUDGET_ROUTE_GOAL) {
        return "LOW_BUDGET 已退出路线目标，请使用预算偏好"
    }
    if (selectedBudget.code.isBlank()) {
        return "请选择预算偏好"
    }

    selectedMealWindows.validationError(
        allowedOptions = MealWindowOptions,
        duplicateMessage = "mealWindows 不能重复",
        blankMessage = "mealWindows 不能包含空饭点",
        unknownMessage = "mealWindows 包含不支持的饭点"
    )?.let { return it }
    val feasibleMealWindows = feasibleMealWindowCodes()
    val infeasibleMealWindows = selectedMealWindows - feasibleMealWindows
    if (infeasibleMealWindows.isNotEmpty()) {
        return "当前路线窗口无法安排${infeasibleMealWindows.toMealWindowLabels()}，请调整出发时间或时长"
    }

    selectedInterestTags.validationError(
        allowedOptions = InterestTagOptions,
        duplicateMessage = "interestTags 不能重复",
        blankMessage = "interestTags 不能包含空标签",
        unknownMessage = "interestTags 包含不支持的标签"
    )?.let { return it }
    if (selectedInterestTags.interestBucketCount() > MAX_GLOBAL_INTEREST_BUCKET_COUNT) {
        return "兴趣大类最多选择 ${MAX_GLOBAL_INTEREST_BUCKET_COUNT} 个"
    }
    val selectedFoodTags = selectedInterestTags.intersect(FoodInterestTags)
    if (selectedFoodTags.size > MAX_FOOD_INTEREST_TAG_COUNT) {
        return "餐饮偏好最多选择 ${MAX_FOOD_INTEREST_TAG_COUNT} 个"
    }
    if (selectedFoodTags.contains(FOOD_ROOT_TAG) && selectedFoodTags.size > 1) {
        return "美食大类和具体餐饮偏好不能同时选择"
    }
    if (selectedFoodTags.isNotEmpty() && selectedMealWindows.isEmpty()) {
        return "选择餐饮或咖啡偏好时，请至少选择午餐或晚餐饭点"
    }

    mustVisitPoints.forEach { point ->
        if (point.name.isBlank()) {
            return "必去点名称不能为空"
        }
        if (point.priority.isBlank()) {
            return "必去点优先级不能为空"
        }
        point.location.coordinateValidationError()?.let {
            return "必去点 ${point.name} 坐标异常"
        }
    }
    return null
}

fun RouteConfigUiState.feasibleMealWindowCodes(): Set<String> {
    val routeStart = selectedDeparture.toDepartureDateTime()
    val routeEnd = routeStart.plusMinutes(selectedDuration.minutes.toLong())
    return MealWindowDefinitions
        .filter { definition ->
            hasOverlapWithRouteWindow(
                routeStart = routeStart,
                routeEnd = routeEnd,
                mealStartTime = definition.start,
                mealEndTime = definition.end
            )
        }
        .map { it.code }
        .toSet()
}

fun RouteConfigUiState.hasFoodInterest(): Boolean {
    return selectedInterestTags.any { it in FoodInterestTags }
}

private fun RouteConfigUiState.buildRequest(
    center: GeoPoint,
    routeCityInfo: RouteCityInfo?
): RouteGenerateRequest {
    return RouteGenerateRequest(
        areaMode = "AUTO_RADIUS",
        areaLabel = "地图选区",
        center = center,
        radiusMeters = ROUTE_AUTO_RADIUS_METERS,
        areaPolygonGcj02 = emptyList(),
        adminAdcodes = emptyList(),
        routeCityName = routeCityInfo?.cityName,
        routeCityAdcode = routeCityInfo?.cityAdcode,
        departureTime = selectedDeparture.toBeijingLocalDateTimeString(),
        durationMinutes = selectedDuration.minutes,
        transportProfile = selectedTransport.code,
        routeGoal = selectedGoal.code,
        budgetLevel = selectedBudget.code,
        interestTags = selectedInterestTags.orderedBy(InterestTagOptions),
        mealWindows = selectedMealWindows.orderedBy(MealWindowOptions),
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

fun MustVisitPointRequest.isSamePlace(other: MustVisitPointRequest): Boolean {
    if (amapPoiId != null && other.amapPoiId != null) {
        return amapPoiId == other.amapPoiId
    }
    return name == other.name &&
        location.longitudeGcj02 == other.location.longitudeGcj02 &&
        location.latitudeGcj02 == other.location.latitudeGcj02
}

private val beijingLocalDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

private fun DepartureOption.toBeijingLocalDateTimeString(): String {
    return toDepartureDateTime().format(beijingLocalDateTimeFormatter)
}

private fun DepartureOption.toDepartureDateTime(): LocalDateTime {
    return LocalDateTime.of(LocalDate.now(RouteZone), time)
}

private fun GeoPoint.coordinateValidationError(): String? {
    if (longitudeGcj02 !in MIN_LONGITUDE..MAX_LONGITUDE || latitudeGcj02 !in MIN_LATITUDE..MAX_LATITUDE) {
        return "地图中心点坐标异常，请返回地图重新选择"
    }
    return null
}

private fun Set<String>.validationError(
    allowedOptions: List<CodeOption>,
    duplicateMessage: String,
    blankMessage: String,
    unknownMessage: String
): String? {
    val values = map { it.trim() }
    if (values.any { it.isBlank() }) {
        return blankMessage
    }
    if (values.distinct().size != values.size) {
        return duplicateMessage
    }
    val allowedCodes = allowedOptions.map { it.code }.toSet()
    if (values.any { it !in allowedCodes }) {
        return unknownMessage
    }
    return null
}

private fun Set<String>.orderedBy(options: List<CodeOption>): List<String> {
    return options.filter { contains(it.code) }.map { it.code }
}

private fun Set<String>.interestBucketCount(): Int {
    return map { code -> if (code in FoodInterestTags) FOOD_ROOT_TAG else code }
        .distinct()
        .size
}

private fun Set<String>.toMealWindowLabels(): String {
    return orderedBy(MealWindowOptions)
        .joinToString("、") { code -> MealWindowOptions.first { it.code == code }.label }
}

private fun hasOverlapWithRouteWindow(
    routeStart: LocalDateTime,
    routeEnd: LocalDateTime,
    mealStartTime: LocalTime,
    mealEndTime: LocalTime
): Boolean {
    var date = routeStart.toLocalDate()
    val lastDate = routeEnd.toLocalDate()
    while (!date.isAfter(lastDate)) {
        val mealStart = LocalDateTime.of(date, mealStartTime)
        val mealEnd = LocalDateTime.of(date, mealEndTime)
        if (routeStart.isBefore(mealEnd) && routeEnd.isAfter(mealStart)) {
            return true
        }
        date = date.plusDays(1)
    }
    return false
}

private data class MealWindowDefinition(
    val code: String,
    val start: LocalTime,
    val end: LocalTime
)

private val MealWindowDefinitions = listOf(
    MealWindowDefinition("LUNCH", LocalTime.of(11, 30), LocalTime.of(13, 30)),
    MealWindowDefinition("DINNER", LocalTime.of(17, 30), LocalTime.of(20, 0))
)

private val FoodInterestTags = setOf("FOOD", "COFFEE")

private const val FOOD_ROOT_TAG = "FOOD"
private const val DEPRECATED_LOW_BUDGET_ROUTE_GOAL = "LOW_BUDGET"
private const val MIN_ROUTE_DURATION_MINUTES = 60
private const val MAX_ROUTE_DURATION_MINUTES = 720
private const val MAX_GLOBAL_INTEREST_BUCKET_COUNT = 5
private const val MAX_FOOD_INTEREST_TAG_COUNT = 3
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
