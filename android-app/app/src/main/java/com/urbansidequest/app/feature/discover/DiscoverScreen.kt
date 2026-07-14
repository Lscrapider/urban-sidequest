package com.urbansidequest.app.feature.discover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.urbansidequest.app.R
import com.urbansidequest.app.data.discover.DiscoverRepository
import com.urbansidequest.app.data.image.RemoteImageRepository
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteShare
import com.urbansidequest.app.domain.model.DiscoverExploreAction
import com.urbansidequest.app.domain.model.DiscoverMapLaunchRequest
import com.urbansidequest.app.domain.model.DiscoverRegion
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

private data class DiscoverRouteMeta(
    val icon: ImageVector,
    val text: String
)

@Composable
fun DiscoverRoute(
    discoverRepository: DiscoverRepository,
    onOpenSharedRoute: (RouteGeneration, String) -> Unit,
    onAuthenticationExpired: () -> Unit,
    onOpenExploreMap: (DiscoverMapLaunchRequest) -> Unit,
    onOpenMap: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val discoverViewModel: DiscoverViewModel = viewModel(
        factory = DiscoverViewModelFactory(discoverRepository)
    )
    val uiState by discoverViewModel.uiState.collectAsStateWithLifecycle()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        discoverViewModel.onLocationPermissionResult(
            granted = granted,
            permanentlyDenied = !granted && context.isDiscoverLocationPermissionPermanentlyDenied()
        )
    }

    LaunchedEffect(uiState.isLocationPermissionRequestPending) {
        if (uiState.isLocationPermissionRequestPending) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(lifecycle, discoverViewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                discoverViewModel.onLocationSettingsReturned(context.hasDiscoverLocationPermission())
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        discoverViewModel.initialize(context.hasDiscoverLocationPermission())
    }

    LaunchedEffect(discoverViewModel) {
        discoverViewModel.events.collectLatest { event ->
            when (event) {
                DiscoverEvent.AuthenticationExpired -> onAuthenticationExpired()
                is DiscoverEvent.OpenSharedRoute -> onOpenSharedRoute(event.routeGeneration, event.routeCode)
                is DiscoverEvent.OpenExploreMap -> onOpenExploreMap(event.request)
            }
        }
    }

    if (uiState.showLocationPermissionPrompt) {
        DiscoverLocationPermissionDialog(
            isPermissionDenied = uiState.isLocationPermissionDenied,
            isPermissionPermanentlyDenied = uiState.isLocationPermissionPermanentlyDenied,
            onRequestPermission = discoverViewModel::requestLocationPermission,
            onOpenSettings = {
                discoverViewModel.openLocationSettings()
                context.openLocationSettings()
            },
            onChooseRegion = discoverViewModel::openRegionPicker,
            onDismiss = discoverViewModel::dismissLocationPermissionPrompt
        )
    }

    if (uiState.isCitySwitcherVisible) {
        DiscoverCitySwitcherSheet(
            onDismiss = discoverViewModel::dismissCitySwitcher,
            onUseCurrentLocation = {
                discoverViewModel.useCurrentLocation(context.hasDiscoverLocationPermission())
            },
            onChooseRegion = discoverViewModel::openRegionPicker
        )
    }

    if (uiState.isRegionPickerVisible) {
        DiscoverRegionPickerSheet(
            uiState = uiState,
            onDismiss = discoverViewModel::dismissRegionPicker,
            onOpenRegion = discoverViewModel::openRegion,
            onSelectRegion = discoverViewModel::selectRegion,
            onNavigateUp = discoverViewModel::navigateUpRegion,
            onSelectCurrentRegion = discoverViewModel::selectCurrentRegion
        )
    }

    DiscoverScreen(
        uiState = uiState,
        onOpenShare = discoverViewModel::openShare,
        onCitySelectorClick = discoverViewModel::openCitySwitcher,
        onExploreAction = { action ->
            discoverViewModel.onExploreAction(action, context.hasDiscoverLocationPermission())
        },
        onOpenMap = onOpenMap,
        onOpenRoutes = onOpenRoutes,
        onOpenProfile = onOpenProfile
    )
}

@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onOpenShare: (RouteShare) -> Unit,
    onCitySelectorClick: () -> Unit,
    onExploreAction: (DiscoverExploreAction) -> Unit,
    onOpenMap: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val cityWeather = uiState.cityWeather
    val cityName = when {
        uiState.selectedAnchor != null -> uiState.selectedAnchor.cityName
        uiState.isAnchorLoading -> "正在定位"
        uiState.isLocationPermissionDenied -> "无权限"
        else -> "暂未定位"
    }
    val weatherText = when {
        uiState.selectedAnchor != null -> cityWeather.weatherText
        uiState.isAnchorLoading -> "正在定位城市"
        uiState.isLocationPermissionDenied -> "选择地区后显示天气"
        else -> "请开启定位或选择地区"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DiscoverTopSection(
                    cityName = cityName,
                    weatherText = weatherText,
                    showCityHero = uiState.selectedAnchor != null,
                    isAnchorLoading = uiState.isAnchorLoading,
                    onCitySelectorClick = onCitySelectorClick,
                    onStartFromCurrent = { onExploreAction(DiscoverExploreAction.StartFromCurrent) },
                    onChooseArea = { onExploreAction(DiscoverExploreAction.ManualDraw) },
                    onRandomExplore = { onExploreAction(DiscoverExploreAction.RandomExplore) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                RouteGridHeader(onOpenAll = onOpenRoutes)
            }

            when {
                uiState.isRouteSharesLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "正在加载路线",
                        description = "正在同步大家走完后分享的城市路线。",
                        illustrationResId = R.drawable.illustration_empty_routes
                    )
                }
                uiState.routeSharesError != null || uiState.openShareError != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "路线加载失败",
                        description = uiState.routeSharesError ?: uiState.openShareError.orEmpty(),
                        illustrationResId = R.drawable.illustration_empty_routes
                    )
                }
                uiState.routeShares.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "还没有路线",
                        description = "走完路线后可以从走过路线里分享，发现页会展示真实地图缩略图和路线信息。",
                        illustrationResId = R.drawable.illustration_empty_routes
                    )
                }
                else -> items(
                    items = uiState.routeShares,
                    key = { it.shareId }
                ) { share ->
                    DiscoverRouteGridCard(
                        share = share,
                        onClick = { onOpenShare(share) }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Discover,
            onDiscoverClick = {},
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun DiscoverLocationPermissionDialog(
    isPermissionDenied: Boolean,
    isPermissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onChooseRegion: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPermissionDenied) "未获得定位权限" else "允许定位以自动选择城市",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("开启定位后会自动切换到当前位置。若暂不授权，也可以手动选择地区继续探索。")
        },
        confirmButton = {
            TextButton(onClick = if (isPermissionPermanentlyDenied) onOpenSettings else onRequestPermission) {
                Text(if (isPermissionPermanentlyDenied) "去设置" else "开启定位")
            }
        },
        dismissButton = {
            TextButton(onClick = onChooseRegion) {
                Text("选择地区")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscoverCitySwitcherSheet(
    onDismiss: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onChooseRegion: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "切换城市",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppText
            )
            Text(
                text = "默认使用当前位置，你也可以手动选择地区。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTextMuted
            )
            Button(
                onClick = onUseCurrentLocation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("使用当前位置")
            }
            TextButton(
                onClick = onChooseRegion,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("手动选择地区")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscoverRegionPickerSheet(
    uiState: DiscoverUiState,
    onDismiss: () -> Unit,
    onOpenRegion: (DiscoverRegion) -> Unit,
    onSelectRegion: (DiscoverRegion) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectCurrentRegion: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择地区",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppText
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
            val currentRegion = uiState.regionPath.lastOrNull()
            if (currentRegion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateUp) {
                        Text("返回上一级")
                    }
                    if (currentRegion.selectable) {
                        Button(
                            onClick = onSelectCurrentRegion,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1264F4))
                        ) {
                            Text("使用${currentRegion.name}")
                        }
                    } else {
                        Text(
                            text = "继续选择城市或城区",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Text(
                    text = uiState.regionPath.joinToString(" · ") { it.name },
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "逐级进入省、市、区，也可在任一级直接使用该地区中心。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when {
                uiState.isRegionsLoading -> Text(
                    text = "正在加载地区…",
                    color = AppTextMuted,
                    modifier = Modifier.padding(vertical = 28.dp)
                )
                uiState.regionError != null -> Text(
                    text = uiState.regionError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 28.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.height(360.dp)
                ) {
                    items(uiState.regions, key = { it.adcode }) { region ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = region.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenRegion(region) },
                                    color = AppText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (region.selectable) {
                                        TextButton(onClick = { onSelectRegion(region) }) {
                                            Text("使用")
                                        }
                                    }
                                    if (region.hasChildren) {
                                        TextButton(onClick = { onOpenRegion(region) }) {
                                            Text("进入")
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverTopSection(
    cityName: String,
    weatherText: String,
    showCityHero: Boolean,
    isAnchorLoading: Boolean,
    onCitySelectorClick: () -> Unit,
    onStartFromCurrent: () -> Unit,
    onChooseArea: () -> Unit,
    onRandomExplore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DiscoverHeader(
            cityName = cityName,
            isAnchorLoading = isAnchorLoading,
            onClick = onCitySelectorClick
        )
        DiscoverTabs()
        CityHeroCard(
            cityName = cityName,
            weatherText = weatherText,
            showCityHero = showCityHero
        )
        CityActionRow(
            onStartFromCurrent = onStartFromCurrent,
            onChooseArea = onChooseArea,
            onRandomExplore = onRandomExplore
        )
    }
}

@Composable
private fun DiscoverHeader(
    cityName: String,
    isAnchorLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "发现路线",
            color = AppText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp
            ),
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (isAnchorLoading) {
                        "正在定位城市"
                    } else {
                        "切换城市，当前$cityName"
                    }
                }
                .clickable(enabled = !isAnchorLoading, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, Color(0xFFD9E1E3))
            ) {
                Row(
                    modifier = Modifier.padding(start = 13.dp, top = 7.dp, end = 9.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "城市 · $cityName",
                        color = AppText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (isAnchorLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AppText,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AppText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverTabs() {
    val tabs = listOf("推荐", "最新", "完成路线", "城市精选")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        tabs.forEachIndexed { index, tab ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tab,
                    color = if (index == 0) Color(0xFF1767F2) else AppText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.46f)
                        .background(
                            if (index == 0) Color(0xFF1767F2) else Color.Transparent,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun CityHeroCard(
    cityName: String,
    weatherText: String,
    showCityHero: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
            if (showCityHero) {
                Image(
                    painter = painterResource(id = cityHeroResId(cityName)),
                    contentDescription = "$cityName 城市探索背景",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    AppSurface.copy(alpha = 0.88f),
                                    AppSurface.copy(alpha = 0.44f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppSurfaceMuted)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = if (showCityHero) "当前城市" else "定位状态",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = cityName,
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 27.sp,
                        lineHeight = 33.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCityHero) {
                        Text(text = "🌤", fontSize = 17.sp)
                    }
                    Text(
                        text = if (showCityHero) "$weatherText · ${formatDiscoverDate()}" else weatherText,
                        color = AppText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

@Composable
private fun CityActionRow(
    modifier: Modifier = Modifier,
    onStartFromCurrent: () -> Unit,
    onChooseArea: () -> Unit,
    onRandomExplore: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CityActionCell(
            text = "从当前位置开始",
            subtitle = "开启一段城市探索",
            icon = Icons.Outlined.Explore,
            primary = true,
            onClick = onStartFromCurrent
        )
        CityActionCell(
            text = "自由绘制区域",
            subtitle = "手动绘制",
            icon = Icons.Outlined.PinDrop,
            onClick = onChooseArea
        )
        CityActionCell(
            text = "随机探索",
            subtitle = "给你一个惊喜",
            icon = Icons.Outlined.Shuffle,
            onClick = onRandomExplore
        )
    }
}

@Composable
private fun RowScope.CityActionCell(
    text: String,
    subtitle: String,
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(if (primary) 1.36f else 1f)
            .height(62.dp)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (primary) Color(0xFF1264F4) else AppSurface,
        border = BorderStroke(1.dp, if (primary) Color(0xFF1264F4) else Color(0xFFE1E7E9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = if (primary) 10.dp else 7.dp),
            horizontalArrangement = Arrangement.spacedBy(if (primary) 7.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (primary) 32.dp else 25.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (primary) AppSurface else Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (primary) 18.dp else 24.dp),
                        tint = if (primary) Color(0xFF1264F4) else AppText
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = text,
                    color = if (primary) AppSurface else AppText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = if (primary) 12.sp else 11.sp,
                        lineHeight = if (primary) 15.sp else 14.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = if (primary) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (primary) AppSurface.copy(alpha = 0.92f) else AppTextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (primary) 10.sp else 9.sp,
                        lineHeight = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RouteGridHeader(onOpenAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "探索路线",
            color = AppText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .semantics { role = Role.Button }
                .clickable(onClick = onOpenAll)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "全部",
                color = Color(0xFF1767F2),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF1767F2)
            )
        }
    }
}

@Composable
private fun DiscoverRouteGridCard(
    share: RouteShare,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, Color(0xFFDDE7EA))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RouteShareImage(
                imageUrl = share.imageUrl,
                contentDescription = "${share.routeTitle} 路线地图缩略图",
                fixedAspectRatio = 1.72f,
                contentScale = ContentScale.Crop
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = share.routeTitle,
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = routeShareAreaText(share),
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DiscoverRouteMetaRow(share = share)
            }
        }
    }
}

@Composable
private fun DiscoverRouteMetaRow(share: RouteShare) {
    val metaItems = listOfNotNull(
        formatShareDistance(share.totalDistanceMeters)?.let {
            DiscoverRouteMeta(icon = Icons.Outlined.Explore, text = it)
        },
        formatShareDuration(share.totalDurationMinutes)?.let {
            DiscoverRouteMeta(icon = Icons.Outlined.AccessTime, text = it)
        },
        share.stopCount?.takeIf { it > 0 }?.let {
            DiscoverRouteMeta(icon = Icons.Outlined.Flag, text = "$it 个点")
        }
    )
    if (metaItems.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metaItems.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = AppTextMuted
                )
                Text(
                    text = item.text,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RouteShareImage(
    imageUrl: String,
    contentDescription: String,
    fixedAspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val resolvedImageUrl = remember(imageUrl) {
        RemoteImageRepository.resolveImageUrl(
            imageUrl = imageUrl,
            minioRewritePrefix = ROUTE_SHARE_IMAGE_MINIO_PREFIX
        )
    }
    var bitmap by remember(resolvedImageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoadFinished by remember(resolvedImageUrl) { mutableStateOf(false) }
    val imageAspectRatio = remember(bitmap, resolvedImageUrl) {
        routeShareImageAspectRatio(bitmap, resolvedImageUrl)
    }
    LaunchedEffect(resolvedImageUrl) {
        bitmap = null
        isLoadFinished = false
        bitmap = RemoteImageRepository.loadBitmap(
            imageUrl = imageUrl,
            minioRewritePrefix = ROUTE_SHARE_IMAGE_MINIO_PREFIX
        )
        isLoadFinished = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(fixedAspectRatio ?: imageAspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(AppSurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Text(
                text = if (isLoadFinished) "地图缩略图暂不可用" else "正在加载地图缩略图",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun routeShareAreaText(share: RouteShare): String {
    return share.cityName
}

private fun formatShareDistance(distanceMeters: Int?): String? {
    if (distanceMeters == null || distanceMeters <= 0) {
        return null
    }
    return if (distanceMeters >= 1000) {
        "${String.format("%.1f", distanceMeters / 1000.0)} km"
    } else {
        "$distanceMeters m"
    }
}

private fun formatShareDuration(durationMinutes: Int?): String? {
    if (durationMinutes == null || durationMinutes <= 0) {
        return null
    }
    return if (durationMinutes >= 60) {
        "${String.format("%.1f", durationMinutes / 60.0)} 小时"
    } else {
        "$durationMinutes 分钟"
    }
}

private fun routeShareImageAspectRatio(bitmap: Bitmap?, imageUrl: String): Float {
    val rawAspectRatio = if (bitmap != null && bitmap.height > 0) {
        bitmap.width.toFloat() / bitmap.height.toFloat()
    } else {
        fallbackRouteShareImageAspectRatio(imageUrl)
    }
    return rawAspectRatio.coerceIn(
        MIN_ROUTE_SHARE_IMAGE_ASPECT_RATIO,
        MAX_ROUTE_SHARE_IMAGE_ASPECT_RATIO
    )
}

private fun fallbackRouteShareImageAspectRatio(imageUrl: String): Float {
    return when (imageUrl.hashCode().ushr(1) % 4) {
        0 -> 1.06f
        1 -> 1.22f
        2 -> 1.38f
        else -> 1.55f
    }
}

private fun cityHeroResId(cityName: String): Int {
    return when {
        cityName.contains("上海") -> R.drawable.city_hero_shanghai
        else -> R.drawable.city_hero_beijing
    }
}

private fun Context.hasDiscoverLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.isDiscoverLocationPermissionPermanentlyDenied(): Boolean {
    val activity = findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) && !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.openLocationSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    )
}

private fun formatDiscoverDate(): String {
    val today = LocalDate.now()
    return "${today.monthValue}月${today.dayOfMonth}日"
}

private const val MIN_ROUTE_SHARE_IMAGE_ASPECT_RATIO = 0.86f
private const val MAX_ROUTE_SHARE_IMAGE_ASPECT_RATIO = 1.62f
private const val ROUTE_SHARE_IMAGE_MINIO_PREFIX = "/urban-sidequest-shares/"
