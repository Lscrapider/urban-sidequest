package com.urbansidequest.app.data.discover

import android.content.Context
import android.util.Base64
import com.urbansidequest.app.domain.model.DiscoverAnchor
import com.urbansidequest.app.domain.model.DiscoverAnchorSource
import com.urbansidequest.app.domain.model.DiscoverCityWeather
import com.urbansidequest.app.domain.model.GeoPoint

/** 发现页的本地缓存与手选地区，不和登录态共用偏好文件。 */
class DiscoverLocalStore(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readFreshWeather(cacheKey: String, nowMillis: Long): DiscoverCityWeather? {
        val fetchedAtMillis = sharedPreferences.getLong(weatherKey(cacheKey, KEY_WEATHER_FETCHED_AT), 0L)
        if (fetchedAtMillis <= 0L || nowMillis - fetchedAtMillis >= DISCOVER_WEATHER_CACHE_TTL_MILLIS) {
            return null
        }
        val cityName = sharedPreferences.getString(weatherKey(cacheKey, KEY_WEATHER_CITY_NAME), null)
        val weatherText = sharedPreferences.getString(weatherKey(cacheKey, KEY_WEATHER_TEXT), null)
        if (cityName.isNullOrBlank() || weatherText.isNullOrBlank()) {
            return null
        }
        return DiscoverCityWeather(
            cityName = cityName,
            weatherText = weatherText,
            fetchedAtMillis = fetchedAtMillis
        )
    }

    fun readStaleWeather(cacheKey: String): DiscoverCityWeather? {
        val fetchedAtMillis = sharedPreferences.getLong(weatherKey(cacheKey, KEY_WEATHER_FETCHED_AT), 0L)
        val cityName = sharedPreferences.getString(weatherKey(cacheKey, KEY_WEATHER_CITY_NAME), null)
        val weatherText = sharedPreferences.getString(weatherKey(cacheKey, KEY_WEATHER_TEXT), null)
        if (fetchedAtMillis <= 0L || cityName.isNullOrBlank() || weatherText.isNullOrBlank()) {
            return null
        }
        return DiscoverCityWeather(
            cityName = cityName,
            weatherText = weatherText,
            fetchedAtMillis = fetchedAtMillis
        )
    }

    fun saveWeather(cacheKey: String, weather: DiscoverCityWeather) {
        sharedPreferences.edit()
            .putString(weatherKey(cacheKey, KEY_WEATHER_CITY_NAME), weather.cityName)
            .putString(weatherKey(cacheKey, KEY_WEATHER_TEXT), weather.weatherText)
            .putLong(weatherKey(cacheKey, KEY_WEATHER_FETCHED_AT), weather.fetchedAtMillis)
            .apply()
    }

    fun saveManualAnchor(anchor: DiscoverAnchor) {
        sharedPreferences.edit()
            .putString(KEY_MANUAL_CITY_NAME, anchor.cityName)
            .putString(KEY_MANUAL_CITY_ADCODE, anchor.cityAdcode)
            .putString(KEY_MANUAL_ROUTE_CITY_NAME, anchor.routeCityName)
            .putString(KEY_MANUAL_ROUTE_CITY_ADCODE, anchor.routeCityAdcode)
            .putLong(KEY_MANUAL_LONGITUDE, anchor.center.longitudeGcj02.toBits())
            .putLong(KEY_MANUAL_LATITUDE, anchor.center.latitudeGcj02.toBits())
            .apply()
    }

    fun clearManualAnchor() {
        sharedPreferences.edit()
            .remove(KEY_MANUAL_CITY_NAME)
            .remove(KEY_MANUAL_CITY_ADCODE)
            .remove(KEY_MANUAL_ROUTE_CITY_NAME)
            .remove(KEY_MANUAL_ROUTE_CITY_ADCODE)
            .remove(KEY_MANUAL_LONGITUDE)
            .remove(KEY_MANUAL_LATITUDE)
            .apply()
    }

    fun readManualAnchor(): DiscoverAnchor? {
        val cityName = sharedPreferences.getString(KEY_MANUAL_CITY_NAME, null) ?: return null
        val longitudeBits = sharedPreferences.getLong(KEY_MANUAL_LONGITUDE, Long.MIN_VALUE)
        val latitudeBits = sharedPreferences.getLong(KEY_MANUAL_LATITUDE, Long.MIN_VALUE)
        if (longitudeBits == Long.MIN_VALUE || latitudeBits == Long.MIN_VALUE) {
            return null
        }
        return DiscoverAnchor(
            cityName = cityName,
            cityAdcode = sharedPreferences.getString(KEY_MANUAL_CITY_ADCODE, null),
            center = GeoPoint(
                longitudeGcj02 = Double.fromBits(longitudeBits),
                latitudeGcj02 = Double.fromBits(latitudeBits)
            ),
            routeCityName = sharedPreferences.getString(KEY_MANUAL_ROUTE_CITY_NAME, null),
            routeCityAdcode = sharedPreferences.getString(KEY_MANUAL_ROUTE_CITY_ADCODE, null),
            source = DiscoverAnchorSource.ManualRegion
        )
    }

    private fun weatherKey(cacheKey: String, field: String): String {
        val encodedCacheKey = Base64.encodeToString(
            cacheKey.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$WEATHER_KEY_PREFIX${encodedCacheKey}_$field"
    }

    private companion object {
        private const val PREFERENCES_NAME = "discover_local_store"
        private const val WEATHER_KEY_PREFIX = "weather_"
        private const val KEY_WEATHER_CITY_NAME = "city_name"
        private const val KEY_WEATHER_TEXT = "weather_text"
        private const val KEY_WEATHER_FETCHED_AT = "fetched_at"
        private const val KEY_MANUAL_CITY_NAME = "manual_city_name"
        private const val KEY_MANUAL_CITY_ADCODE = "manual_city_adcode"
        private const val KEY_MANUAL_ROUTE_CITY_NAME = "manual_route_city_name"
        private const val KEY_MANUAL_ROUTE_CITY_ADCODE = "manual_route_city_adcode"
        private const val KEY_MANUAL_LONGITUDE = "manual_longitude"
        private const val KEY_MANUAL_LATITUDE = "manual_latitude"
    }
}

const val DISCOVER_WEATHER_CACHE_TTL_MILLIS = 2 * 60 * 60 * 1000L
