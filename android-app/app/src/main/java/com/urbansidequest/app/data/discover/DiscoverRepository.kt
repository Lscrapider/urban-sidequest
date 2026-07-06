package com.urbansidequest.app.data.discover

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLive
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.DEFAULT_DISCOVER_CITY_NAME
import com.urbansidequest.app.domain.model.DEFAULT_DISCOVER_WEATHER_TEXT
import com.urbansidequest.app.domain.model.DiscoverCityWeather
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteShare
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class DiscoverRepository(
    context: Context,
    private val routeRepository: RouteRepository
) {

    private val appContext = context.applicationContext

    suspend fun fetchRouteShares(): List<RouteShare> {
        return routeRepository.fetchRouteShares()
    }

    suspend fun fetchSharedRoute(shareId: String): RouteGeneration {
        return routeRepository.fetchSharedRoute(shareId)
    }

    suspend fun loadCityWeather(): DiscoverCityWeather {
        val locationCity = locateCity().getOrDefault(
            DiscoverLocatedCity(cityName = DEFAULT_DISCOVER_CITY_NAME, adCode = null)
        )
        return queryWeather(
            cityQuery = locationCity.adCode.orEmpty().ifBlank { locationCity.cityName },
            fallbackCityName = locationCity.cityName
        ).getOrDefault(DiscoverCityWeather(cityName = locationCity.cityName))
    }

    private suspend fun locateCity(): Result<DiscoverLocatedCity> = suspendCancellableCoroutine { continuation ->
        runCatching {
            val locationClient = AMapLocationClient(appContext)
            val locationOption = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = true
                isNeedAddress = true
                httpTimeOut = DISCOVER_LOCATION_TIMEOUT_MILLIS
            }
            locationClient.setLocationOption(locationOption)
            locationClient.setLocationListener { location ->
                val result = if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                    Result.success(
                        DiscoverLocatedCity(
                            cityName = normalizeDiscoverCityName(location.city, location.province),
                            adCode = location.adCode.orEmpty().ifBlank { null }
                        )
                    )
                } else {
                    Result.failure(IllegalStateException("定位失败"))
                }
                locationClient.stopLocation()
                locationClient.onDestroy()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                locationClient.stopLocation()
                locationClient.onDestroy()
            }
            locationClient.startLocation()
        }.onFailure { throwable ->
            if (continuation.isActive) {
                continuation.resume(Result.failure(throwable))
            }
        }
    }

    private suspend fun queryWeather(
        cityQuery: String,
        fallbackCityName: String
    ): Result<DiscoverCityWeather> = suspendCancellableCoroutine { continuation ->
        runCatching {
            val weatherSearch = WeatherSearch(appContext)
            weatherSearch.setQuery(
                WeatherSearchQuery(
                    cityQuery,
                    WeatherSearchQuery.WEATHER_TYPE_LIVE
                )
            )
            weatherSearch.setOnWeatherSearchListener(
                object : WeatherSearch.OnWeatherSearchListener {
                    override fun onWeatherLiveSearched(
                        result: LocalWeatherLiveResult?,
                        resultCode: Int
                    ) {
                        val weather = if (resultCode == AMAP_SUCCESS_CODE) {
                            result?.liveResult
                        } else {
                            null
                        }
                        val cityWeather = DiscoverCityWeather(
                            cityName = normalizeDiscoverCityName(
                                weather?.city,
                                fallbackCityName
                            ),
                            weatherText = weather.toDiscoverWeatherText()
                        )
                        if (continuation.isActive) {
                            continuation.resume(Result.success(cityWeather))
                        }
                    }

                    override fun onWeatherForecastSearched(
                        result: LocalWeatherForecastResult?,
                        resultCode: Int
                    ) = Unit
                }
            )
            weatherSearch.searchWeatherAsyn()
        }.onFailure { throwable ->
            if (continuation.isActive) {
                continuation.resume(Result.failure(throwable))
            }
        }
    }

    private data class DiscoverLocatedCity(
        val cityName: String,
        val adCode: String?
    )

    private companion object {
        private const val AMAP_SUCCESS_CODE = 1000
        private const val DISCOVER_LOCATION_TIMEOUT_MILLIS = 5_000L
    }
}

private fun LocalWeatherLive?.toDiscoverWeatherText(): String {
    val weather = this?.weather.orEmpty()
    val temperature = this?.temperature.orEmpty()
    if (weather.isNotBlank() && temperature.isNotBlank()) {
        return "$weather ${temperature}°C"
    }
    return when {
        weather.contains("雨") || weather.contains("雪") || weather.contains("沙") || weather.contains("霾") -> {
            "天气不太适合长时间步行"
        }
        weather.isBlank() -> DEFAULT_DISCOVER_WEATHER_TEXT
        else -> weather
    }
}

private fun normalizeDiscoverCityName(
    city: String?,
    fallback: String?
): String {
    val rawName = city.orEmpty().ifBlank { fallback.orEmpty() }.ifBlank { DEFAULT_DISCOVER_CITY_NAME }
    return rawName
        .removeSuffix("市")
        .removeSuffix("省")
        .removeSuffix("自治区")
        .removeSuffix("特别行政区")
}
