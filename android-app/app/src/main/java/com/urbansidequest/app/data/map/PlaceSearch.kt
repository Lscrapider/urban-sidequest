package com.urbansidequest.app.data.map

import android.content.Context
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaceSearchSuggestion(
    val name: String,
    val description: String,
    val location: LatLng,
    val amapPoiId: String?
)

data class RouteCityInfo(
    val cityName: String?,
    val cityAdcode: String?
)

fun searchAmapInputTips(
    context: Context,
    keyword: String,
    location: LatLng,
    onResult: (String, List<PlaceSearchSuggestion>) -> Unit
) {
    val query = InputtipsQuery(keyword, "").apply {
        setLocation(LatLonPoint(location.latitude, location.longitude))
        setCityLimit(false)
    }
    val inputTips = Inputtips(context.applicationContext, query)
    inputTips.setInputtipsListener { tips, resultCode ->
        val suggestions = if (resultCode == AMAP_SUCCESS_CODE) {
            tips.orEmpty()
                .mapNotNull(Tip::toPlaceSearchSuggestion)
                .take(MAX_SEARCH_SUGGESTIONS)
        } else {
            emptyList()
        }
        onResult(keyword, suggestions)
    }
    inputTips.requestInputtipsAsyn()
}

suspend fun resolveRouteCityInfo(
    context: Context,
    location: LatLng
): RouteCityInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val geocodeSearch = GeocodeSearch(context.applicationContext)
        val query = RegeocodeQuery(
            LatLonPoint(location.latitude, location.longitude),
            REGEOCODE_RADIUS_METERS,
            GeocodeSearch.AMAP
        )
        val address = geocodeSearch.getFromLocation(query)
        RouteCityInfo(
            cityName = address.city.orEmpty().ifBlank { address.province.orEmpty().ifBlank { null } },
            cityAdcode = address.adCode.orEmpty().ifBlank { null }
        )
    }.recoverCatching { throwable ->
        if (throwable is AMapException) {
            null
        } else {
            throw throwable
        }
    }.getOrNull()
}

private fun Tip.toPlaceSearchSuggestion(): PlaceSearchSuggestion? {
    val point = point ?: return null
    val name = name.orEmpty().ifBlank { return null }
    val districtText = district.orEmpty()
    val addressText = address.orEmpty()
    val description = listOf(districtText, addressText)
        .filter { it.isNotBlank() && it != "[]" }
        .distinct()
        .joinToString(" · ")

    return PlaceSearchSuggestion(
        name = name,
        description = description,
        location = LatLng(point.latitude, point.longitude),
        amapPoiId = poiID.orEmpty().ifBlank { null }
    )
}

private const val AMAP_SUCCESS_CODE = 1000
private const val MAX_SEARCH_SUGGESTIONS = 8
private const val REGEOCODE_RADIUS_METERS = 1000f
