package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.Circle
import com.urbansidequest.app.R
import com.urbansidequest.app.feature.routeconfig.RouteConfigUiState
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.WarningAmber

@Composable
internal fun MapShortcutRow(
    modifier: Modifier = Modifier,
    onOpenRoutes: () -> Unit,
    onOpenFavorites: () -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MapShortcutChip(
            text = "路线库",
            icon = Icons.AutoMirrored.Filled.ListAlt,
            onClick = onOpenRoutes
        )
        MapShortcutChip(
            text = "我的路线",
            icon = Icons.Filled.Route,
            onClick = onOpenRoutes
        )
        MapShortcutChip(
            text = "收藏点",
            icon = Icons.Filled.StarBorder,
            onClick = onOpenFavorites
        )
    }
}

@Composable
internal fun MapShortcutChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = AppSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AppText
            )
            Text(
                text = text,
                color = AppText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun MapRangeSheet(
    uiState: RouteConfigUiState,
    rangeSelectionMode: RangeSelectionMode,
    manualVertexCount: Int,
    previewAreaText: String,
    message: String?,
    isSubmitting: Boolean,
    onOpenConditions: () -> Unit,
    onSelectAutoRange: () -> Unit,
    onSelectManualRange: () -> Unit,
    onUndoManualPoint: () -> Unit,
    onResetManualRange: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            SheetGrabber(modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (rangeSelectionMode == RangeSelectionMode.Auto) {
                        "自动范围 · $previewAreaText"
                    } else {
                        "手动绘制范围 · 已添加 $manualVertexCount 个顶点"
                    },
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (rangeSelectionMode == RangeSelectionMode.Auto) {
                        "系统按时长和交通方式控制可行范围"
                    } else {
                        "点击地图依次添加顶点，路线会优先从选区内挑选地点"
                    },
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RangeModeSegmentedControl(
                selectedMode = rangeSelectionMode,
                onSelectAuto = onSelectAutoRange,
                onSelectManual = onSelectManualRange
            )

            if (rangeSelectionMode == RangeSelectionMode.Auto) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RouteConditionSummaryTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.AccessTime,
                        value = uiState.selectedDeparture.label,
                        label = "出发时间"
                    )
                    RouteConditionSummaryTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.AccessTime,
                        value = uiState.selectedDuration.label,
                        label = "可用时长"
                    )
                    RouteConditionSummaryTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Route,
                        value = uiState.selectedTransport.shortTransportLabel(),
                        label = "交通组合"
                    )
                    RouteConditionSummaryTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.StarBorder,
                        value = uiState.selectedGoal.label,
                        label = "路线目标"
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MapManualActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.Undo,
                        text = "撤销一点",
                        onClick = onUndoManualPoint
                    )
                    MapManualActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Refresh,
                        text = "重画",
                        onClick = onResetManualRange
                    )
                    MapManualActionButton(
                        modifier = Modifier.weight(1.24f),
                        icon = Icons.Filled.GpsFixed,
                        text = "使用自动范围",
                        onClick = onSelectAutoRange
                    )
                }
            }

            MapPrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = if (isSubmitting) "生成中" else "配置路线条件",
                onClick = onOpenConditions,
                enabled = !isSubmitting
            )

            if (rangeSelectionMode == RangeSelectionMode.Manual) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppTextMuted
                    )
                    Text(
                        text = "至少添加 $MIN_MANUAL_POLYGON_VERTEX_COUNT 个顶点后，可配置路线条件",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = WarningAmber,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun RangeModeSegmentedControl(
    selectedMode: RangeSelectionMode,
    onSelectAuto: () -> Unit,
    onSelectManual: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp),
        shape = CircleShape,
        color = AppSurface,
        border = BorderStroke(1.dp, Color(0xFFDCE4EF))
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            RangeModeSegment(
                modifier = Modifier.weight(1f),
                text = "自动范围",
                selected = selectedMode == RangeSelectionMode.Auto,
                onClick = onSelectAuto
            )
            RangeModeSegment(
                modifier = Modifier.weight(1f),
                text = "手绘范围",
                selected = selectedMode == RangeSelectionMode.Manual,
                onClick = onSelectManual
            )
        }
    }
}

@Composable
internal fun RangeModeSegment(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(39.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) ROUTE_A_COLOR.toComposeColor() else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) Color.White else AppText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun MapManualActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppSurface,
            contentColor = AppText
        ),
        border = BorderStroke(1.dp, Color(0xFFDCE4EF))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = AppText
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun MapPrimaryActionButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ROUTE_A_COLOR.toComposeColor(),
            contentColor = Color.White,
            disabledContainerColor = ROUTE_A_COLOR.toComposeColor().copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.76f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun MapSecondaryActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppSurface,
            contentColor = AppText
        ),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.78f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = AppText
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun AutoRangeBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, Color(0xFFDCE4EF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自动范围",
                color = AppText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun RouteConditionSummaryTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(9.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, Color(0xFFDCE4EF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = AppText
            )
            Text(
                text = value,
                color = ROUTE_A_COLOR.toComposeColor(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                color = AppTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
