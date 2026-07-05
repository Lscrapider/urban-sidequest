package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.Circle
import com.urbansidequest.app.R
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.ui.components.UrbanSearchField
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

@Composable
internal fun MapTopBar(
    modifier: Modifier = Modifier,
    isSearchActive: Boolean,
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    onSearchFocus: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(8.dp), clip = false)
        ) {
            UrbanSearchField(
                value = searchText,
                onValueChange = {
                    onSearchFocus()
                    onSearchTextChange(it)
                },
                placeholder = "搜索起点、区域或必去点",
                containerColor = AppSurface.copy(alpha = 0.86f),
                borderColor = AppBorder.copy(alpha = 0.58f),
                onFocus = onSearchFocus,
                leadingIcon = if (isSearchActive) {
                    {
                    IconButton(onClick = onCancelSearch) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出搜索",
                            tint = AppTextMuted
                        )
                    }
                    }
                } else {
                    null
                },
                trailingIcon = {
                    if (isSearchActive && searchText.isNotBlank()) {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空搜索",
                                tint = AppTextMuted
                            )
                        }
                    } else if (!isSearchActive) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "语音搜索",
                            tint = AppText
                        )
                    }
                }
            )
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
internal fun SearchSuggestionsPanel(
    searchText: String,
    suggestions: List<PlaceSearchSuggestion>,
    isSearching: Boolean,
    onSelectSuggestion: (PlaceSearchSuggestion) -> Unit
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
internal fun SearchPanelHint(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
internal fun SearchSuggestionRow(
    suggestion: PlaceSearchSuggestion,
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
internal fun MapLocationButton(
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
internal fun MapExecutionControlStack(
    modifier: Modifier = Modifier,
    onCurrentLocation: () -> Unit,
    onLayers: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MapRoundIconButton(
            contentDescription = "回到当前位置",
            onClick = onCurrentLocation
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = null,
                tint = AppText
            )
        }
        MapRoundIconButton(
            contentDescription = "切换地图图层",
            onClick = onLayers
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = Icons.Filled.Layers,
                contentDescription = null,
                tint = AppText
            )
        }
        MapRoundIconButton(
            contentDescription = "更多地图操作",
            onClick = onMore
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = AppText
            )
        }
    }
}

@Composable
private fun MapRoundIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .shadow(5.dp, CircleShape, clip = false)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = AppSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.72f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
internal fun RouteSwitcher(
    modifier: Modifier = Modifier,
    routes: List<GeneratedRoute>,
    selectedRouteIndex: Int?,
    visibleRouteIndexes: Set<Int>,
    onSelectRoute: (Int) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RouteSwitcherShape,
        color = AppSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.46f))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            routes.forEachIndexed { index, route ->
                val visible = index in visibleRouteIndexes
                val selected = index == selectedRouteIndex
                val color = routeColor(index).toComposeColor()
                Surface(
                    modifier = Modifier
                        .width(RouteSwitcherSegmentWidth)
                        .height(RouteSwitcherSegmentHeight)
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable { onSelectRoute(index) },
                    shape = RouteSwitcherSegmentShape,
                    color = when {
                        selected && visible -> color.copy(alpha = 0.18f)
                        visible -> color.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    },
                    border = BorderStroke(
                        1.dp,
                        if (visible) color.copy(alpha = 0.86f) else Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = route.routeCode,
                            color = if (visible) color else AppTextMuted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
