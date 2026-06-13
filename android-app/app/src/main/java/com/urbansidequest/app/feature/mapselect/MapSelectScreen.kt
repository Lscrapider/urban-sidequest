package com.urbansidequest.app.feature.mapselect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.DeepTealDark
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val DefaultMapCenter = LatLng(39.908722, 116.397499)
private val HorizontalScreenPadding = 16.dp

@Composable
fun MapSelectScreen() {
    val context = LocalContext.current
    var isSelectionExpanded by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<AMap?>(null) }
    var currentLocation by remember { mutableStateOf(DefaultMapCenter) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<MapSearchSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun moveToLocation(location: LatLng, zoom: Float = 16f) {
        currentLocation = location
        mapController?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }

    fun requestCurrentLocation() {
        startSingleAmapLocation(
            context = context,
            onLocated = ::moveToLocation
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestCurrentLocation()
        }
    }

    fun locateWithPermission() {
        if (context.hasLocationPermission()) {
            requestCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        locateWithPermission()
    }

    LaunchedEffect(searchText, currentLocation) {
        val keyword = searchText.trim()
        if (!isSearchActive || keyword.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        searchAmapInputTips(
            context = context,
            keyword = keyword,
            location = currentLocation,
            onResult = { resultKeyword, suggestions ->
                if (resultKeyword == searchText.trim()) {
                    searchSuggestions = suggestions
                    isSearching = false
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppSurfaceMuted)
    ) {
        AMapCanvas(
            modifier = Modifier.fillMaxSize(),
            currentLocation = currentLocation,
            onMapReady = { mapController = it }
        )

        MapTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            isSearchActive = isSearchActive,
            searchText = searchText,
            suggestions = searchSuggestions,
            isSearching = isSearching,
            onSearchFocus = { isSearchActive = true },
            onSearchTextChange = { searchText = it },
            onCancelSearch = {
                isSearchActive = false
                searchText = ""
                searchSuggestions = emptyList()
                isSearching = false
                focusManager.clearFocus()
            },
            onSelectSuggestion = { suggestion ->
                moveToLocation(suggestion.location)
                searchText = suggestion.name
                searchSuggestions = emptyList()
                isSearchActive = false
                focusManager.clearFocus()
            }
        )

        MapLocationButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = HorizontalScreenPadding,
                    bottom = if (isSelectionExpanded) 318.dp else 234.dp
                ),
            onClick = ::locateWithPermission
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (isSelectionExpanded) {
                MapSelectionSheet(
                    onNext = {},
                    onManualSelect = {}
                )
            } else {
                MapHomeActionSheet(
                    onGenerateRoute = { isSelectionExpanded = true }
                )
            }
            BottomNavigationBar()
        }
    }
}

@Composable
private fun AMapCanvas(
    modifier: Modifier = Modifier,
    currentLocation: LatLng,
    onMapReady: (AMap) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var currentLocationMarker by remember { mutableStateOf<Marker?>(null) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                val aMap = map
                aMap.uiSettings.isZoomControlsEnabled = false
                aMap.uiSettings.isCompassEnabled = false
                aMap.uiSettings.isScaleControlsEnabled = true
                currentLocationMarker = aMap.addMarker(
                    MarkerOptions()
                        .position(currentLocation)
                        .anchor(0.5f, 0.5f)
                        .icon(createCurrentLocationIcon(context))
                        .zIndex(10f)
                )
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14f))
                onMapReady(aMap)
            }
        },
        update = {
            currentLocationMarker?.position = currentLocation
        }
    )
}

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

private fun startSingleAmapLocation(
    context: Context,
    onLocated: (LatLng) -> Unit
) {
    val locationClient = AMapLocationClient(context.applicationContext)
    val locationOption = AMapLocationClientOption().apply {
        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        isOnceLocation = true
        isOnceLocationLatest = true
        isNeedAddress = false
        httpTimeOut = LOCATION_TIMEOUT_MILLIS
    }

    locationClient.setLocationOption(locationOption)
    locationClient.setLocationListener { location ->
        if (location != null && location.errorCode == 0) {
            onLocated(LatLng(location.latitude, location.longitude))
        }
        locationClient.stopLocation()
        locationClient.onDestroy()
    }
    locationClient.startLocation()
}

private fun searchAmapInputTips(
    context: Context,
    keyword: String,
    location: LatLng,
    onResult: (String, List<MapSearchSuggestion>) -> Unit
) {
    val query = InputtipsQuery(keyword, "").apply {
        setLocation(LatLonPoint(location.latitude, location.longitude))
        setCityLimit(false)
    }
    val inputTips = Inputtips(context.applicationContext, query)
    inputTips.setInputtipsListener { tips, resultCode ->
        val suggestions = if (resultCode == AMAP_SUCCESS_CODE) {
            tips.orEmpty()
                .mapNotNull(Tip::toMapSearchSuggestion)
                .take(MAX_SEARCH_SUGGESTIONS)
        } else {
            emptyList()
        }
        onResult(keyword, suggestions)
    }
    inputTips.requestInputtipsAsyn()
}

private fun Tip.toMapSearchSuggestion(): MapSearchSuggestion? {
    val point = point ?: return null
    val name = name.orEmpty().ifBlank { return null }
    val districtText = district.orEmpty()
    val addressText = address.orEmpty()
    val description = listOf(districtText, addressText)
        .filter { it.isNotBlank() && it != "[]" }
        .distinct()
        .joinToString(" · ")

    return MapSearchSuggestion(
        name = name,
        description = description,
        location = LatLng(point.latitude, point.longitude)
    )
}

private fun createCurrentLocationIcon(context: Context): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (46 * density).roundToInt()
    val outerRadius = size / 2f
    val whiteRadius = 13 * density
    val innerRadius = 8 * density
    val strokeWidth = 2 * density
    val center = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(41, 13, 77, 77)
    canvas.drawCircle(center, center, outerRadius, paint)

    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, whiteRadius, paint)

    paint.color = AndroidColor.rgb(13, 77, 77)
    canvas.drawCircle(center, center, innerRadius, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, whiteRadius, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private const val LOCATION_TIMEOUT_MILLIS = 8_000L
private const val AMAP_SUCCESS_CODE = 1000
private const val MAX_SEARCH_SUGGESTIONS = 8

private data class MapSearchSuggestion(
    val name: String,
    val description: String,
    val location: LatLng
)

@Composable
private fun MapTopBar(
    modifier: Modifier = Modifier,
    isSearchActive: Boolean,
    searchText: String,
    suggestions: List<MapSearchSuggestion>,
    isSearching: Boolean,
    onSearchFocus: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onSelectSuggestion: (MapSearchSuggestion) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp), clip = false),
            shape = RoundedCornerShape(8.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onSearchFocus)
                    .padding(horizontal = if (isSearchActive) 4.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchActive) {
                    IconButton(onClick = onCancelSearch) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出搜索",
                            tint = AppTextMuted
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = AppTextMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                BasicTextField(
                    modifier = Modifier.weight(1f),
                    value = searchText,
                    onValueChange = {
                        onSearchFocus()
                        onSearchTextChange(it)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                    decorationBox = { innerTextField ->
                        if (searchText.isBlank()) {
                            Text(
                                text = "搜索起点、区域或必去点",
                                color = AppTextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        innerTextField()
                    }
                )

                if (isSearchActive && searchText.isNotBlank()) {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "清空搜索",
                            tint = AppTextMuted
                        )
                    }
                } else if (!isSearchActive) {
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .width(1.dp)
                            .background(AppBorder)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "路线条件",
                        tint = DeepTeal
                    )
                }
            }
        }

        if (isSearchActive) {
            SearchSuggestionsPanel(
                searchText = searchText,
                suggestions = suggestions,
                isSearching = isSearching,
                onSelectSuggestion = onSelectSuggestion
            )
        }
    }
}

@Composable
private fun SearchSuggestionsPanel(
    searchText: String,
    suggestions: List<MapSearchSuggestion>,
    isSearching: Boolean,
    onSelectSuggestion: (MapSearchSuggestion) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(8.dp), clip = false),
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            when {
                searchText.trim().length < 2 -> SearchPanelHint(text = "输入至少 2 个字搜索地点")
                isSearching -> SearchPanelHint(text = "正在搜索")
                suggestions.isEmpty() -> SearchPanelHint(text = "没有找到可定位的地点")
                else -> suggestions.forEach { suggestion ->
                    SearchSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onSelectSuggestion(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanelHint(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SearchSuggestionRow(
    suggestion: MapSearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
private fun MapLocationButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppSurface,
            contentColor = DeepTeal
        ),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = Icons.Filled.GpsFixed,
            contentDescription = "回到当前位置",
            tint = DeepTeal
        )
    }
}

@Composable
private fun MapHomeActionSheet(onGenerateRoute: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onGenerateRoute,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepTeal,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "生成副本",
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "选择区域后生成今天的城市副本",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MapSelectionSheet(
    onNext: () -> Unit,
    onManualSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "天安门附近",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前范围适合 3-5 小时步行 + 地铁路线",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapChip(text = "半日路线")
                MapChip(text = "地铁可达")
                MapChip(text = "低绕路")
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onNext,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepTeal,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "下一步配置路线",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                onClick = onManualSelect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DeepTeal
                ),
                border = BorderStroke(1.dp, DeepTeal)
            ) {
                Text(
                    text = "手动框选区域",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MapChip(text: String) {
    Surface(
        shape = CircleShape,
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = text,
            color = AppTextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BottomNavigationBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavigationItem(
                text = "地图",
                icon = Icons.Filled.Map,
                selected = true
            )
            BottomNavigationItem(
                text = "路线",
                icon = Icons.Filled.Directions,
                selected = false
            )
            BottomNavigationItem(
                text = "我的",
                icon = Icons.Filled.Person,
                selected = false
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    text: String,
    icon: ImageVector,
    selected: Boolean
) {
    val contentColor = if (selected) Color.White else AppTextMuted
    val backgroundColor = if (selected) DeepTealDark else Color.Transparent

    Column(
        modifier = Modifier
            .clickable { }
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = Color.Transparent,
                shape = CircleShape
            )
            .background(backgroundColor, CircleShape)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = text,
            tint = contentColor
        )
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
