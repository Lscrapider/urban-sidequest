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
        val repository = routeRepository ?: return
        val center = selectedCenter ?: return
        val state = mutableUiState.value
        if (state.isGenerating) {
            return
        }
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

val InterestTagOptions = listOf(
    CodeOption("美食", "FOOD"),
    CodeOption("咖啡休息", "COFFEE"),
    CodeOption("展馆", "MUSEUM"),
    CodeOption("景点", "SCENIC"),
    CodeOption("拍照", "PHOTO"),
    CodeOption("购物", "SHOPPING"),
    CodeOption("夜游", "NIGHT")
)

private fun RouteConfigUiState.buildRequest(
    center: GeoPoint,
    routeCityInfo: RouteCityInfo?
): RouteGenerateRequest {
    return RouteGenerateRequest(
        areaMode = "AUTO_RADIUS",
        areaLabel = "地图选区",
        center = center,
        areaPolygonGcj02 = emptyList(),
        routeCityName = routeCityInfo?.cityName,
        routeCityAdcode = routeCityInfo?.cityAdcode,
        departureTime = selectedDeparture.toBeijingLocalDateTimeString(),
        durationMinutes = selectedDuration.minutes,
        transportProfile = selectedTransport.code,
        routeGoal = selectedGoal.code,
        interestTags = selectedInterestTags.toList(),
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
    return LocalDateTime.of(LocalDate.now(RouteZone), time)
        .format(beijingLocalDateTimeFormatter)
}
