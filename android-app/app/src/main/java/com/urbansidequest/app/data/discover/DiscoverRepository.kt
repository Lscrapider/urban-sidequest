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
import com.urbansidequest.app.data.region.RegionRepository
import com.urbansidequest.app.data.route.RouteRepository
import com.urbansidequest.app.domain.model.DEFAULT_DISCOVER_CITY_NAME
import com.urbansidequest.app.domain.model.DISCOVER_WEATHER_UNAVAILABLE_TEXT
import com.urbansidequest.app.domain.model.DiscoverAnchor
import com.urbansidequest.app.domain.model.DiscoverAnchorSource
import com.urbansidequest.app.domain.model.DiscoverCityWeather
import com.urbansidequest.app.domain.model.DiscoverRegion
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteShare
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DiscoverRepository(
    context: Context,
    private val routeRepository: RouteRepository,
    private val regionRepository: RegionRepository
) {

    private val appContext = context.applicationContext
    private val localStore = DiscoverLocalStore(appContext)
    private val weatherRequestMutex = Mutex()

    suspend fun fetchRouteShares(): List<RouteShare> {
        return routeRepository.fetchRouteShares()
    }

    suspend fun fetchSharedRoute(shareId: String): RouteGeneration {
        return routeRepository.fetchSharedRoute(shareId)
    }

    suspend fun fetchRegions(parentAdcode: String?): List<DiscoverRegion> {
        return regionRepository.fetchRegions(parentAdcode)
    }

    fun loadSavedManualAnchor(): DiscoverAnchor? {
        return localStore.readManualAnchor()
    }

    fun saveManualAnchor(anchor: DiscoverAnchor) {
        localStore.saveManualAnchor(anchor)
    }

    fun clearSavedManualAnchor() {
        localStore.clearManualAnchor()
    }

    suspend fun locateDeviceAnchor(): DiscoverAnchor {
        return locateDeviceLocation().getOrThrow()
    }

    /** 天气缓存以地区编码为键，仅在成功查询到有效天气时刷新。 */
    suspend fun loadCityWeather(anchor: DiscoverAnchor): DiscoverCityWeather {
        return weatherRequestMutex.withLock {
            val nowMillis = System.currentTimeMillis()
            localStore.readFreshWeather(anchor.weatherCacheKey, nowMillis)?.let { cached ->
                return@withLock cached.copy(cityName = anchor.cityName)
            }
            val staleWeather = localStore.readStaleWeather(anchor.weatherCacheKey)
            val freshWeather = withTimeoutOrNull(DISCOVER_WEATHER_QUERY_TIMEOUT_MILLIS) {
                queryWeather(
                    cityQuery = anchor.cityAdcode.orEmpty()
                        .ifBlank { anchor.routeCityAdcode.orEmpty() }
                        .ifBlank { anchor.cityName },
                    cityName = anchor.cityName
                ).getOrNull()
            }
            when {
                freshWeather != null -> {
                    val cachedWeather = freshWeather.copy(
                        cityName = anchor.cityName,
                        fetchedAtMillis = nowMillis
                    )
                    localStore.saveWeather(anchor.weatherCacheKey, cachedWeather)
                    cachedWeather
                }

                staleWeather != null -> staleWeather.copy(cityName = anchor.cityName)
                else -> DiscoverCityWeather(
                    cityName = anchor.cityName,
                    weatherText = DISCOVER_WEATHER_UNAVAILABLE_TEXT
                )
            }
        }
    }

    private suspend fun locateDeviceLocation(): Result<DiscoverAnchor> = suspendCancellableCoroutine { continuation ->
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
                        DiscoverAnchor(
                            cityName = normalizeDiscoverCityName(location.city, location.province),
                            cityAdcode = location.adCode.orEmpty().ifBlank { null },
                            center = GeoPoint(
                                longitudeGcj02 = location.longitude,
                                latitudeGcj02 = location.latitude
                            ),
                            source = DiscoverAnchorSource.DeviceLocation
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
        cityName: String
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
                        val weatherText = weather.toDiscoverWeatherText()
                        if (continuation.isActive) {
                            continuation.resume(
                                weatherText?.let {
                                    Result.success(
                                        DiscoverCityWeather(
                                            cityName = cityName,
                                            weatherText = it
                                        )
                                    )
                                } ?: Result.failure(IllegalStateException("天气数据不可用"))
                            )
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

    private companion object {
        private const val AMAP_SUCCESS_CODE = 1000
        private const val DISCOVER_LOCATION_TIMEOUT_MILLIS = 5_000L
        private const val DISCOVER_WEATHER_QUERY_TIMEOUT_MILLIS = 8_000L
    }
}

private fun LocalWeatherLive?.toDiscoverWeatherText(): String? {
    val weather = this?.weather.orEmpty()
    val temperature = this?.temperature.orEmpty()
    if (weather.isNotBlank() && temperature.isNotBlank()) {
        return "$weather ${temperature}°C"
    }
    return when {
        weather.contains("雨") || weather.contains("雪") || weather.contains("沙") || weather.contains("霾") -> {
            "天气不太适合长时间步行"
        }
        weather.isBlank() -> null
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
