package com.urbansidequest.app.domain.model

data class DiscoverCityWeather(
    val cityName: String = DEFAULT_DISCOVER_CITY_NAME,
    val weatherText: String = DEFAULT_DISCOVER_WEATHER_TEXT
)

const val DEFAULT_DISCOVER_CITY_NAME = "北京"
const val DEFAULT_DISCOVER_WEATHER_TEXT = "晴转多云 28°C"
