package com.urbansidequest.app.feature.routeconfig

import android.content.Context
import androidx.lifecycle.ViewModel
import com.amap.api.maps.model.LatLng
import com.urbansidequest.app.data.api.MustVisitPointRequest
import com.urbansidequest.app.data.api.RouteGenerateRequest
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.data.map.RouteCityInfo
import com.urbansidequest.app.data.map.searchAmapInputTips
import com.urbansidequest.app.domain.model.GeoPoint
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
import kotlinx.coroutines.delay
import kotlin.random.Random

class RouteConfigViewModel : ViewModel() {

    private val mutableUiState = MutableStateFlow(RouteConfigUiState())
    val uiState: StateFlow<RouteConfigUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<RouteConfigEvent>()
    val events: SharedFlow<RouteConfigEvent> = mutableEvents.asSharedFlow()

    fun reset() {
        mutableUiState.value = RouteConfigUiState()
    }

    fun selectDeparture(option: DepartureOption) {
        mutableUiState.update { state ->
            state.copy(selectedDeparture = option).normalizedForMealAvailability()
        }
    }

    fun selectDuration(option: DurationOption) {
        mutableUiState.update { state ->
            state.copy(selectedDuration = option).normalizedForMealAvailability()
        }
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

    /** 随机惊喜只复用当前已支持的路线参数，避免引入另一套策略默认值。 */
    fun applyDiscoverRandomPreset() {
        val random = Random.Default
        val randomInterestTags = NonFoodInterestTagOptions
            .shuffled(random)
            .take(DISCOVER_RANDOM_INTEREST_TAG_COUNT)
            .map(InterestTagOption::code)
            .toSet()
        mutableUiState.update {
            it.copy(
                selectedDeparture = DepartureOptions.random(random),
                selectedDuration = DurationOptions.random(random),
                selectedTransport = TransportOptions.random(random),
                selectedGoal = RouteGoalOptions.random(random),
                selectedBudget = BudgetOptions.random(random),
                selectedMealWindows = emptySet(),
                selectedInterestTags = randomInterestTags
            ).normalizedForMealAvailability()
        }
    }

    fun toggleMealWindow(code: String) {
        mutableUiState.update { state ->
            if (code !in state.feasibleMealWindowCodes()) {
                state
            } else {
                val selectedMealWindows = if (state.selectedMealWindows.contains(code)) {
                    state.selectedMealWindows - code
                } else {
                    state.selectedMealWindows + code
                }
                val nextState = state.copy(selectedMealWindows = selectedMealWindows)
                if (selectedMealWindows.isEmpty()) {
                    nextState.withoutFoodInterestTags()
                } else {
                    nextState
                }
            }
        }
    }

    fun toggleInterestTag(code: String) {
        mutableUiState.update { state ->
            state.copy(
                selectedInterestTags = state.selectedInterestTags.toggledInterestTag(code)
            )
        }
    }

    fun onMustVisitSearchTextChange(value: String) {
        val keyword = value.trim()
        mutableUiState.update {
            it.copy(
                mustVisitSearchText = value,
                mustVisitSuggestions = if (keyword.length < MIN_SEARCH_KEYWORD_LENGTH) emptyList() else it.mustVisitSuggestions,
                isMustVisitSearching = keyword.length >= MIN_SEARCH_KEYWORD_LENGTH
            )
        }
    }

    suspend fun searchMustVisitSuggestions(context: Context, selectedCenter: GeoPoint?) {
        val keyword = mutableUiState.value.mustVisitSearchText.trim()
        if (keyword.length < MIN_SEARCH_KEYWORD_LENGTH) {
            mutableUiState.update { it.copy(isMustVisitSearching = false, mustVisitSuggestions = emptyList()) }
            return
        }
        mutableUiState.update { it.copy(isMustVisitSearching = true) }
        delay(SEARCH_DEBOUNCE_MILLIS)
        searchAmapInputTips(
            context = context.applicationContext,
            keyword = keyword,
            location = selectedCenter?.toLatLng() ?: DEFAULT_SEARCH_CENTER,
            onResult = { resultKeyword, suggestions ->
                this@RouteConfigViewModel.onMustVisitSuggestionsLoaded(resultKeyword, suggestions)
            }
        )
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

    fun removeMustVisitPoint(point: RouteMustVisitPoint) {
        mutableUiState.update { state ->
            state.copy(mustVisitPoints = state.mustVisitPoints.filterNot { it.isSamePlace(point) })
        }
    }

    suspend fun submitRouteGeneration(
        routeRepositoryAvailable: Boolean,
        selectedCenter: GeoPoint?,
        isManualRange: Boolean = false,
        manualRangeVertices: List<GeoPoint> = emptyList(),
        routeCityInfo: RouteCityInfo? = null
    ) {
        val state = mutableUiState.value
        val validationMessage = state.validateForRouteRequest(
            selectedCenter = selectedCenter,
            routeRepositoryAvailable = routeRepositoryAvailable
        )
        if (validationMessage != null) {
            mutableUiState.update { it.copy(errorMessage = validationMessage) }
            return
        }
        if (isManualRange && manualRangeVertices.size < MIN_MANUAL_POLYGON_VERTEX_COUNT) {
            mutableUiState.update { it.copy(errorMessage = "请至少绘制 ${MIN_MANUAL_POLYGON_VERTEX_COUNT} 个顶点") }
            return
        }
        val center = selectedCenter ?: return
        mutableUiState.update { it.copy(errorMessage = null) }
        mutableEvents.emit(
            RouteConfigEvent.RouteGenerationSubmitted(
                state.buildRequest(
                    center = center,
                    routeCityInfo = routeCityInfo,
                    isManualRange = isManualRange,
                    manualRangeVertices = manualRangeVertices
                )
            )
        )
    }

    private companion object {
        private const val MIN_SEARCH_KEYWORD_LENGTH = 2
        private const val SEARCH_DEBOUNCE_MILLIS = 250L
        private val DEFAULT_SEARCH_CENTER = LatLng(39.908722, 116.397499)
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
    val mustVisitPoints: List<RouteMustVisitPoint> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

sealed interface RouteConfigEvent {
    data class RouteGenerationSubmitted(val request: RouteGenerateRequest) : RouteConfigEvent
}

data class DepartureOption(val label: String, val time: LocalTime)

data class DurationOption(val label: String, val minutes: Int)

data class CodeOption(val label: String, val code: String)

data class InterestTagOption(
    val label: String,
    val code: String,
    val parentCode: String? = null,
    val maxSiblingSelected: Int? = null
)

data class FoodInterestGroup(
    val option: InterestTagOption,
    val children: List<InterestTagOption>
)

data class RouteMustVisitPoint(
    val name: String,
    val amapPoiId: String?,
    val location: GeoPoint
)

val RouteZone: ZoneId = ZoneId.of("Asia/Shanghai")

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

val NonFoodInterestTagOptions = listOf(
    InterestTagOption("景点", "SCENIC"),
    InterestTagOption("文化", "CULTURE"),
    InterestTagOption("博物馆/展馆", "MUSEUM", parentCode = "CULTURE"),
    InterestTagOption("咖啡/茶饮/甜品", "COFFEE"),
    InterestTagOption("购物", "SHOPPING"),
    InterestTagOption("本地生活", "LOCAL"),
    InterestTagOption("夜游", "NIGHT"),
    InterestTagOption("拍照", "PHOTO"),
    InterestTagOption("娱乐", "ENTERTAINMENT"),
    InterestTagOption("活动/演出", "EVENT")
)

val FoodInterestGroups = listOf(
    FoodInterestGroup(
        option = InterestTagOption("中餐", "FOOD_CHINESE", parentCode = FOOD_ROOT_TAG, maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
        children = listOf(
            InterestTagOption("川菜", "FOOD_SICHUAN", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("粤菜", "FOOD_CANTONESE", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("鲁菜", "FOOD_SHANDONG", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("苏菜/淮扬", "FOOD_JIANGSU", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("浙菜", "FOOD_ZHEJIANG", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("湘菜", "FOOD_HUNAN", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("东北菜", "FOOD_DONG_BEI", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("老字号", "FOOD_OLD_BRAND", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("火锅", "FOOD_HOT_POT", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("地方风味/小吃", "FOOD_LOCAL_FLAVOR", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("清真菜", "FOOD_HALAL", parentCode = "FOOD_CHINESE", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT)
        )
    ),
    FoodInterestGroup(
        option = InterestTagOption("外国餐厅", "FOOD_FOREIGN", parentCode = FOOD_ROOT_TAG, maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
        children = listOf(
            InterestTagOption("西餐", "FOOD_WESTERN", parentCode = "FOOD_FOREIGN", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("美式", "FOOD_AMERICAN", parentCode = "FOOD_FOREIGN", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("印度菜", "FOOD_INDIAN", parentCode = "FOOD_FOREIGN", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
            InterestTagOption("墨西哥菜", "FOOD_MEXICAN", parentCode = "FOOD_FOREIGN", maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT)
        )
    ),
    FoodInterestGroup(
        option = InterestTagOption("快餐", "FOOD_FAST_FOOD", parentCode = FOOD_ROOT_TAG, maxSiblingSelected = MAX_FOOD_INTEREST_TAG_COUNT),
        children = emptyList()
    )
)

val InterestTagOptions = NonFoodInterestTagOptions + FoodInterestGroups.flatMap { group ->
    listOf(group.option) + group.children
}

private val InterestTagOptionByCode: Map<String, InterestTagOption> = InterestTagOptions.associateBy { it.code }

private val FoodChildCodesByParent: Map<String, Set<String>> = FoodInterestGroups.associate { group ->
    group.option.code to group.children.map { it.code }.toSet()
}

private val FoodParentByTagCode: Map<String, String> = FoodInterestGroups
    .flatMap { group -> group.children.map { child -> child.code to group.option.code } }
    .toMap()

private val FoodInterestTagCodes: Set<String> = FoodInterestGroups
    .flatMap { group -> listOf(group.option.code) + group.children.map { it.code } }
    .toSet()

private val TopLevelInterestBucketByCode: Map<String, String> = InterestTagOptions.associate { option ->
    option.code to when {
        option.code in FoodInterestTagCodes -> FOOD_ROOT_TAG
        else -> option.code
    }
}

private val FoodAncestorCodesByTagCode: Map<String, Set<String>> = InterestTagOptions.associate { option ->
    val ancestors = buildSet {
        var parent = option.parentCode
        while (parent != null) {
            add(parent)
            parent = InterestTagOptionByCode[parent]?.parentCode
        }
    }
    option.code to ancestors
}

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
    if (selectedDuration.minutes !in MIN_ROUTE_DURATION_MINUTES..MAX_ROUTE_DURATION_MINUTES) {
        return "路线时长需在 ${MIN_ROUTE_DURATION_MINUTES} 到 ${MAX_ROUTE_DURATION_MINUTES} 分钟之间"
    }
    if (selectedTransport.code.isBlank()) {
        return "请选择交通组合"
    }
    if (selectedGoal.code == DEPRECATED_LOW_BUDGET_ROUTE_GOAL) {
        return "低预算已经改为预算偏好，请在预算里选择"
    }
    if (selectedBudget.code.isBlank()) {
        return "请选择预算偏好"
    }

    selectedMealWindows.validationError(
        allowedCodes = MealWindowOptions.map { it.code }.toSet(),
        duplicateMessage = "饭点不能重复选择",
        blankMessage = "饭点不能为空",
        unknownMessage = "包含暂不支持的饭点"
    )?.let { return it }
    val feasibleMealWindows = feasibleMealWindowCodes()
    val infeasibleMealWindows = selectedMealWindows - feasibleMealWindows
    if (infeasibleMealWindows.isNotEmpty()) {
        return "当前路线窗口无法安排${infeasibleMealWindows.toMealWindowLabels()}，请调整出发时间或时长"
    }

    selectedInterestTags.validationError(
        allowedCodes = InterestTagOptions.map { it.code }.toSet(),
        duplicateMessage = "兴趣偏好不能重复选择",
        blankMessage = "兴趣偏好不能为空",
        unknownMessage = "包含暂不支持的兴趣偏好"
    )?.let { return it }
    if (selectedInterestTags.interestBucketCount() > MAX_GLOBAL_INTEREST_BUCKET_COUNT) {
        return "兴趣大类最多选择 ${MAX_GLOBAL_INTEREST_BUCKET_COUNT} 个"
    }
    val selectedFoodTags = selectedInterestTags.intersect(FoodInterestTagCodes)
    if (selectedFoodTags.size > MAX_FOOD_INTEREST_TAG_COUNT) {
        return "餐饮偏好最多选择 ${MAX_FOOD_INTEREST_TAG_COUNT} 个"
    }
    selectedInterestTags.foodParentChildConflictMessage()?.let { return it }
    selectedInterestTags.maxSiblingConflictMessage()?.let { return it }
    if (selectedFoodTags.isNotEmpty() && selectedMealWindows.isEmpty()) {
        return "选择餐饮偏好时，请至少选择午餐或晚餐饭点"
    }

    mustVisitPoints.forEach { point ->
        if (point.name.isBlank()) {
            return "必去点名称不能为空"
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
    return selectedInterestTags.any { it in FoodInterestTagCodes }
}

private fun RouteConfigUiState.normalizedForMealAvailability(): RouteConfigUiState {
    val feasibleMealWindows = feasibleMealWindowCodes()
    val availableMealWindows = selectedMealWindows.intersect(feasibleMealWindows)
    val nextState = copy(selectedMealWindows = availableMealWindows)
    return if (availableMealWindows.isEmpty()) {
        nextState.withoutFoodInterestTags()
    } else {
        nextState
    }
}

private fun RouteConfigUiState.buildRequest(
    center: GeoPoint,
    routeCityInfo: RouteCityInfo?,
    isManualRange: Boolean,
    manualRangeVertices: List<GeoPoint>
): RouteGenerateRequest {
    val polygon = if (isManualRange) manualRangeVertices.toClosedPolygon() else emptyList()
    return RouteGenerateRequest(
        areaMode = if (isManualRange) AREA_MODE_MANUAL_POLYGON else AREA_MODE_AUTO_RADIUS,
        areaLabel = if (isManualRange) "手动绘制区域" else "地图选区",
        center = center,
        radiusMeters = null,
        areaPolygonGcj02 = polygon,
        adminAdcodes = emptyList(),
        routeCityName = routeCityInfo?.cityName,
        routeCityAdcode = routeCityInfo?.cityAdcode,
        departureTime = selectedDeparture.toBeijingLocalDateTimeString(),
        durationMinutes = selectedDuration.minutes,
        transportProfile = selectedTransport.code,
        routeGoal = selectedGoal.code,
        budgetLevel = selectedBudget.code,
        interestTags = selectedInterestTags.orderedByInterestOptions(),
        mealWindows = selectedMealWindows.orderedBy(MealWindowOptions),
        mustVisitPoints = mustVisitPoints.map(RouteMustVisitPoint::toMustVisitPointRequest)
    )
}

private fun List<GeoPoint>.toClosedPolygon(): List<GeoPoint> {
    if (isEmpty()) {
        return emptyList()
    }
    return if (first() == last()) this else this + first()
}

private fun PlaceSearchSuggestion.toMustVisitPoint(): RouteMustVisitPoint {
    return RouteMustVisitPoint(
        name = name,
        amapPoiId = amapPoiId,
        location = GeoPoint(
            longitudeGcj02 = location.longitude,
            latitudeGcj02 = location.latitude
        )
    )
}

private fun RouteMustVisitPoint.toMustVisitPointRequest(): MustVisitPointRequest {
    return MustVisitPointRequest(
        name = name,
        amapPoiId = amapPoiId,
        location = location,
        priority = MUST_VISIT_PRIORITY
    )
}

fun RouteMustVisitPoint.isSamePlace(other: RouteMustVisitPoint): Boolean {
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
    allowedCodes: Set<String>,
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
    if (values.any { it !in allowedCodes }) {
        return unknownMessage
    }
    return null
}

private fun Set<String>.toggledInterestTag(code: String): Set<String> {
    if (contains(code)) {
        return this - code
    }
    val option = InterestTagOptionByCode[code] ?: return this
    var next = this + code
    FoodChildCodesByParent[option.code]?.let { childCodes ->
        next -= childCodes
    }
    FoodParentByTagCode[option.code]?.let { parentCode ->
        next -= parentCode
    }
    return next
}

private fun RouteConfigUiState.withoutFoodInterestTags(): RouteConfigUiState {
    return copy(selectedInterestTags = selectedInterestTags - FoodInterestTagCodes)
}

private fun Set<String>.orderedBy(options: List<CodeOption>): List<String> {
    return options.filter { contains(it.code) }.map { it.code }
}

private fun Set<String>.orderedByInterestOptions(): List<String> {
    return InterestTagOptions.filter { contains(it.code) }.map { it.code }
}

private fun Set<String>.interestBucketCount(): Int {
    return map { code -> TopLevelInterestBucketByCode[code] ?: code }
        .distinct()
        .size
}

private fun Set<String>.foodParentChildConflictMessage(): String? {
    val selectedFoodTags = intersect(FoodInterestTagCodes)
    val hasParentChildConflict = selectedFoodTags.any { tagCode ->
        FoodAncestorCodesByTagCode[tagCode].orEmpty().any { ancestorCode -> ancestorCode in selectedFoodTags }
    }
    return if (hasParentChildConflict) {
        "餐饮偏好同一分支不能同时选择大类和细分口味"
    } else {
        null
    }
}

private fun Set<String>.maxSiblingConflictMessage(): String? {
    val selectedOptionsByParent = mapNotNull { InterestTagOptionByCode[it] }
        .filter { it.parentCode != null }
        .groupBy { it.parentCode }
    val hasConflict = selectedOptionsByParent.values.any { siblingOptions ->
        val limit = siblingOptions.mapNotNull { it.maxSiblingSelected }.firstOrNull() ?: 0
        limit > 0 && siblingOptions.size > limit
    }
    return if (hasConflict) {
        "同一餐饮分支最多选择 ${MAX_FOOD_INTEREST_TAG_COUNT} 个"
    } else {
        null
    }
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

private const val FOOD_ROOT_TAG = "FOOD"
private const val AREA_MODE_AUTO_RADIUS = "AUTO_RADIUS"
private const val AREA_MODE_MANUAL_POLYGON = "MANUAL_POLYGON"
private const val MIN_MANUAL_POLYGON_VERTEX_COUNT = 3
private const val DISCOVER_RANDOM_INTEREST_TAG_COUNT = 2
private const val DEPRECATED_LOW_BUDGET_ROUTE_GOAL = "LOW_BUDGET"
private const val MIN_ROUTE_DURATION_MINUTES = 60
private const val MAX_ROUTE_DURATION_MINUTES = 720
private const val MAX_GLOBAL_INTEREST_BUCKET_COUNT = 5
private const val MAX_FOOD_INTEREST_TAG_COUNT = 3
private const val MUST_VISIT_PRIORITY = "MUST"
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
