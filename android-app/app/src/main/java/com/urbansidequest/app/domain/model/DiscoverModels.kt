package com.urbansidequest.app.domain.model

data class DiscoverCityWeather(
    val cityName: String = DEFAULT_DISCOVER_CITY_NAME,
    val weatherText: String = DISCOVER_WEATHER_UNAVAILABLE_TEXT,
    val fetchedAtMillis: Long = 0L
)

data class DiscoverAnchor(
    val cityName: String,
    val cityAdcode: String?,
    val center: GeoPoint,
    val routeCityName: String? = null,
    val routeCityAdcode: String? = null,
    val source: DiscoverAnchorSource
) {
    val weatherCacheKey: String
        get() = cityAdcode.orEmpty().ifBlank { routeCityAdcode.orEmpty() }.ifBlank { cityName }
}

enum class DiscoverAnchorSource {
    DeviceLocation,
    ManualRegion
}

data class DiscoverRegion(
    val adcode: String,
    val parentAdcode: String?,
    val name: String,
    val level: String,
    val selectable: Boolean,
    val hasChildren: Boolean,
    val routeCityName: String,
    val routeCityAdcode: String,
    val center: GeoPoint
) {
    fun toAnchor(): DiscoverAnchor {
        return DiscoverAnchor(
            cityName = name,
            cityAdcode = adcode,
            center = center,
            routeCityName = routeCityName,
            routeCityAdcode = routeCityAdcode,
            source = DiscoverAnchorSource.ManualRegion
        )
    }
}

enum class DiscoverExploreAction {
    StartFromCurrent,
    ManualDraw,
    RandomExplore
}

enum class DiscoverMapRangeMode {
    Auto,
    Manual
}

data class DiscoverMapLaunchRequest(
    val anchor: DiscoverAnchor,
    val rangeMode: DiscoverMapRangeMode,
    val shouldApplyRandomPreset: Boolean,
    val launchId: Long = System.nanoTime()
)

const val DEFAULT_DISCOVER_CITY_NAME = "北京"
const val DISCOVER_WEATHER_UNAVAILABLE_TEXT = "天气暂不可用"
