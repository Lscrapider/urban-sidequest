package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.Circle
import com.urbansidequest.app.R
import com.urbansidequest.app.data.map.PlaceSearchSuggestion
import com.urbansidequest.app.domain.model.GeoPoint
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.feature.routeconfig.DepartureOptions
import com.urbansidequest.app.feature.routeconfig.DurationOptions
import com.urbansidequest.app.feature.routeconfig.FoodInterestGroups
import com.urbansidequest.app.feature.routeconfig.MealWindowOptions
import com.urbansidequest.app.feature.routeconfig.NonFoodInterestTagOptions
import com.urbansidequest.app.feature.routeconfig.RouteConfigUiState
import com.urbansidequest.app.feature.routeconfig.RouteConfigViewModel
import com.urbansidequest.app.feature.routeconfig.RouteGoalOptions
import com.urbansidequest.app.feature.routeconfig.RouteMustVisitPoint
import com.urbansidequest.app.feature.routeconfig.TransportOptions
import com.urbansidequest.app.feature.routeconfig.feasibleMealWindowCodes
import com.urbansidequest.app.ui.components.UrbanMotion
import com.urbansidequest.app.ui.components.UrbanSearchField
import com.urbansidequest.app.ui.components.urbanMotionDuration
import com.urbansidequest.app.ui.components.urbanMotionEnabled
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteGenerationConditionSheet(
    uiState: RouteConfigUiState,
    selectedCenter: GeoPoint,
    previewAreaText: String,
    message: String?,
    isSubmitting: Boolean,
    routeConfigViewModel: RouteConfigViewModel,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        LazyColumn(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SheetGrabber(modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "生成路线条件",
                            color = AppText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "北京 · 天安门附近 · $previewAreaText",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AutoRangeBadge()
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭条件",
                                tint = AppText
                            )
                        }
                    }
                }
            }
            item {
                RouteConditionSection(title = "路线窗口") {
                    ConditionLabel(text = "出发时间")
                    ConditionOptionFlow {
                        DepartureOptions.forEach { option ->
                            ConditionChip(
                                text = option.label,
                                selected = option == uiState.selectedDeparture,
                                onClick = { routeConfigViewModel.selectDeparture(option) }
                            )
                        }
                    }
                    ConditionLabel(text = "可用时长")
                    ConditionOptionFlow {
                        DurationOptions.forEach { option ->
                            ConditionChip(
                                text = option.label,
                                selected = option == uiState.selectedDuration,
                                onClick = { routeConfigViewModel.selectDuration(option) }
                            )
                        }
                    }
                }
            }
            item {
                RouteConditionSection(title = "交通与目标") {
                    ConditionLabel(text = "交通组合")
                    ConditionOptionFlow {
                        TransportOptions.take(4).forEach { option ->
                            ConditionChip(
                                text = option.label,
                                icon = transportIcon(option.code),
                                selected = option == uiState.selectedTransport,
                                onClick = { routeConfigViewModel.selectTransport(option) }
                            )
                        }
                    }
                    ConditionLabel(text = "路线目标")
                    ConditionOptionFlow {
                        RouteGoalOptions.forEach { option ->
                            ConditionChip(
                                text = option.label,
                                icon = routeGoalIcon(option.code),
                                selected = option == uiState.selectedGoal,
                                onClick = { routeConfigViewModel.selectGoal(option) }
                            )
                        }
                    }
                }
            }
            item {
                RouteConditionSection(title = "兴趣偏好") {
                    ConditionOptionFlow {
                        NonFoodInterestTagOptions.take(8).forEach { option ->
                            ConditionChip(
                                text = option.label,
                                icon = interestIcon(option.code),
                                selected = option.code in uiState.selectedInterestTags,
                                onClick = { routeConfigViewModel.toggleInterestTag(option.code) }
                            )
                        }
                    }
                }
            }
            item {
                RouteConditionSection(title = "饭点与餐饮") {
                    ConditionLabel(text = "饭点安排")
                    ConditionOptionFlow {
                        val feasibleMeals = uiState.feasibleMealWindowCodes()
                        MealWindowOptions.forEach { option ->
                            ConditionChip(
                                text = option.label,
                                icon = Icons.Filled.LocalDining,
                                selected = option.code in uiState.selectedMealWindows,
                                enabled = option.code in feasibleMeals,
                                onClick = { routeConfigViewModel.toggleMealWindow(option.code) }
                            )
                        }
                    }
                    ConditionLabel(text = "餐饮偏好")
                    ConditionOptionFlow {
                        FoodInterestGroups.flatMap { group -> listOf(group.option) + group.children.take(4) }
                            .take(8)
                            .forEach { option ->
                                ConditionChip(
                                    text = option.label,
                                    icon = foodInterestIcon(option.code),
                                    selected = option.code in uiState.selectedInterestTags,
                                    onClick = { routeConfigViewModel.toggleInterestTag(option.code) }
                                )
                            }
                    }
                }
            }
            item {
                RouteConditionSection(title = "必去点") {
                    UrbanSearchField(
                        value = uiState.mustVisitSearchText,
                        onValueChange = routeConfigViewModel::onMustVisitSearchTextChange,
                        placeholder = "搜索并加入必去点",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = AppTextMuted
                            )
                        }
                    )
                    if (uiState.mustVisitSuggestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.mustVisitSuggestions.take(3).forEach { suggestion ->
                                MustVisitSuggestionRow(
                                    suggestion = suggestion,
                                    onClick = { routeConfigViewModel.addMustVisitSuggestion(suggestion) }
                                )
                            }
                        }
                    }
                    uiState.mustVisitPoints.forEach { point ->
                        MustVisitPointRow(
                            point = point,
                            onRemove = { routeConfigViewModel.removeMustVisitPoint(point) }
                        )
                    }
                }
            }
            item {
                val displayMessage = message ?: uiState.errorMessage
                if (!displayMessage.isNullOrBlank()) {
                    Text(
                        text = displayMessage,
                        color = WarningAmber,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                MapPrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isSubmitting) "生成中" else "应用并生成路线",
                    onClick = onSubmit,
                    enabled = !isSubmitting
                )
            }
        }
    }
}

@Composable
internal fun RouteConditionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
internal fun ConditionLabel(text: String) {
    Text(
        text = text,
        color = AppTextMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConditionOptionFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

internal fun transportIcon(code: String): ImageVector =
    when (code) {
        "WALK_ONLY" -> Icons.Filled.Route
        "WALK_SUBWAY", "WALK_BUS", "WALK_TRANSIT" -> Icons.Filled.Place
        else -> Icons.Filled.Route
    }

internal fun routeGoalIcon(code: String): ImageVector =
    when (code) {
        "CLASSIC" -> Icons.Filled.Star
        "LOCAL" -> Icons.Filled.Groups
        "NIGHT" -> Icons.Filled.Nightlight
        "PHOTO" -> Icons.Filled.PhotoCamera
        else -> Icons.Filled.AutoAwesome
    }

internal fun interestIcon(code: String): ImageVector =
    when (code) {
        "SCENIC" -> Icons.Filled.Star
        "CULTURE" -> Icons.Filled.AutoAwesome
        "MUSEUM" -> Icons.Filled.AccountBalance
        "COFFEE" -> Icons.Filled.LocalCafe
        "SHOPPING" -> Icons.Filled.ShoppingBag
        "LOCAL" -> Icons.Filled.Groups
        "NIGHT" -> Icons.Filled.Nightlight
        "PHOTO" -> Icons.Filled.PhotoCamera
        "ENTERTAINMENT" -> Icons.Filled.AutoAwesome
        "EVENT" -> Icons.Filled.Event
        else -> Icons.Filled.Star
    }

internal fun foodInterestIcon(code: String): ImageVector =
    when {
        code.contains("COFFEE") -> Icons.Filled.LocalCafe
        code.contains("FOREIGN") || code.contains("WESTERN") -> Icons.Filled.LocalDining
        else -> Icons.Filled.LocalDining
    }

@Composable
internal fun ConditionChip(
    text: String,
    icon: ImageVector? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val actionBlue = ROUTE_A_COLOR.toComposeColor()
    Surface(
        modifier = Modifier
            .height(34.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.42f)),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) actionBlue else AppSurface,
        border = BorderStroke(1.dp, if (selected) actionBlue else Color(0xFFE0E7F0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (icon == null) 13.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) Color.White else actionBlue
                )
            }
            Text(
                text = text,
                color = if (selected) Color.White else AppText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun MustVisitSuggestionRow(
    suggestion: PlaceSearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(AppSurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = ROUTE_A_COLOR.toComposeColor()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = suggestion.name,
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "加入",
            color = ROUTE_A_COLOR.toComposeColor(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun MustVisitPointRow(
    point: RouteMustVisitPoint,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(AppSurfaceMuted.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmarks,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = ROUTE_A_COLOR.toComposeColor()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = "${point.name} · 必去",
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = onRemove
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除${point.name}",
                modifier = Modifier.size(16.dp),
                tint = AppTextMuted
            )
        }
    }
}

@Composable
internal fun SheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .background(AppBorder, CircleShape)
        )
    }
}

@Composable
internal fun MapSelectionLockPulse(modifier: Modifier = Modifier) {
    var pulseStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pulseStarted = true
    }
    val motionEnabled = urbanMotionEnabled()
    val pulseProgress by animateFloatAsState(
        targetValue = if (pulseStarted && motionEnabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = urbanMotionDuration(UrbanMotion.MapLockScanMillis),
            easing = FastOutSlowInEasing
        ),
        label = "map_selection_lock_pulse"
    )
    Canvas(
        modifier = modifier.size(72.dp)
    ) {
        val centerRadius = 5.dp.toPx()
        val ringRadius = if (motionEnabled) {
            14.dp.toPx() + 18.dp.toPx() * pulseProgress
        } else {
            18.dp.toPx()
        }
        val ringAlpha = if (motionEnabled) {
            (1f - pulseProgress).coerceIn(0f, 1f) * 0.44f
        } else {
            0.32f
        }
        drawCircle(
            color = RouteTeal.copy(alpha = ringAlpha),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = AppSurface.copy(alpha = 0.90f),
            radius = centerRadius + 3.dp.toPx(),
            center = center
        )
        drawCircle(
            color = RouteTeal,
            radius = centerRadius,
            center = center
        )
    }
}

@Composable
internal fun MapChip(text: String) {
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
