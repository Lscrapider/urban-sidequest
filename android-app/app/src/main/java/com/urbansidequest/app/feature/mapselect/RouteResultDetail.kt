package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import com.urbansidequest.app.ui.theme.WarningSurface
import kotlin.math.roundToInt

@Composable
internal fun RouteDetailSheet(
    route: GeneratedRoute,
    routeIndex: Int,
    routeCount: Int,
    isRouteCompleted: Boolean,
    interaction: RouteInteractionState,
    sheetProgress: Float,
    hiddenProgress: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onLocateStop: (RouteStop) -> Unit,
    onLocateSegment: (RouteSegmentPolylinePayload) -> Unit,
    onStartRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReact: (RouteReaction) -> Unit
) {
    val routeColor = routeColor(routeIndex).toComposeColor()
    val detailHeight = 300.dp * sheetProgress.coerceIn(0f, 1f)
    var sheetHeightPx by remember { mutableStateOf(0f) }
    val hiddenOffsetPx = ((sheetHeightPx - ROUTE_SHEET_PEEK_HANDLE_HEIGHT_PX).coerceAtLeast(0f) *
        hiddenProgress.coerceIn(0f, 1f)).roundToInt()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> sheetHeightPx = size.height.toFloat() }
            .offset { IntOffset(x = 0, y = hiddenOffsetPx) }
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RouteSheetHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onDrag = onDrag,
                onDragEnd = onDragEnd
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = route.title,
                        color = AppText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (route.routeCode == "A") {
                            "今天最稳妥，少绕路，解释充分。"
                        } else {
                            "${routeCount} 条路线可切换，默认仍从路线 A 开始。"
                        },
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "路线 ${route.routeCode}",
                        color = routeColor.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    RouteInteractionActions(
                        interaction = interaction,
                        onToggleFavorite = onToggleFavorite,
                        onReact = onReact
                    )
                }
            }

            RouteMetricStrip(
                duration = formatDuration(route.totalDurationMinutes),
                distance = formatDistance(route.totalDistanceMeters),
                stopCount = route.stops.size,
                risk = formatRiskLevel(route.riskLevel),
                routeColor = routeColor
            )

            Text(
                text = route.summary,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )

            RouteStopTimeline(
                route = route,
                routeColor = routeColor,
                selectedStopId = route.stops.minByOrNull(RouteStop::order)?.id,
                onSelectStop = onLocateStop,
                onSelectSegment = { originStop, destinationStop ->
                    onLocateSegment(
                        buildRailSegmentPayload(
                            routeIndex = routeIndex,
                            route = route,
                            originStop = originStop,
                            destinationStop = destinationStop
                        )
                    )
                }
            )

            if (sheetProgress > 0.02f) {
                Column(
                    modifier = Modifier
                        .height(detailHeight)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    route.stops.sortedBy(RouteStop::order).forEach { stop ->
                        RouteStopDetailRow(
                            stop = stop,
                            routeColor = routeColor,
                            onLocate = { onLocateStop(stop) }
                        )
                    }
                    Text(
                        text = route.explanation,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            UrbanPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = if (isRouteCompleted) "路线 ${route.routeCode} 已完成" else "开始路线 ${route.routeCode}",
                onClick = onStartRoute,
                enabled = !isRouteCompleted
            )
        }
    }
}

@Composable
internal fun RouteMetricStrip(
    duration: String,
    distance: String,
    stopCount: Int,
    risk: String,
    routeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RouteMetricPill(
            modifier = Modifier.weight(1f),
            label = "时间",
            value = duration,
            accentColor = routeColor,
            surfaceColor = routeColor.copy(alpha = 0.10f)
        )
        RouteMetricPill(
            modifier = Modifier.weight(1f),
            label = "距离",
            value = distance,
            accentColor = InfoCyan,
            surfaceColor = InfoCyanSurface
        )
        RouteMetricPill(
            modifier = Modifier.weight(1f),
            label = "站点",
            value = "${stopCount}个",
            accentColor = Color(0xFF7C4DFF),
            surfaceColor = Color(0xFFF1EDFF)
        )
        RouteMetricPill(
            modifier = Modifier.weight(1f),
            label = "风险",
            value = risk,
            accentColor = WarningAmber,
            surfaceColor = WarningSurface
        )
    }
}

@Composable
internal fun RouteMetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accentColor: Color,
    surfaceColor: Color
) {
    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = AppText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun RouteInteractionActions(
    interaction: RouteInteractionState,
    onToggleFavorite: () -> Unit,
    onReact: (RouteReaction) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteActionButton(
            selected = interaction.isFavorite,
            contentDescription = if (interaction.isFavorite) "取消收藏路线" else "收藏路线",
            onClick = onToggleFavorite
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_route_action_favorite),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                colorFilter = ColorFilter.tint(if (interaction.isFavorite) RouteTeal else AppTextMuted)
            )
        }
        RouteActionButton(
            selected = interaction.reaction == RouteReaction.Liked,
            contentDescription = "喜欢这条路线",
            onClick = { onReact(RouteReaction.Liked) }
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_route_action_like),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                colorFilter = ColorFilter.tint(if (interaction.reaction == RouteReaction.Liked) RouteTeal else AppTextMuted)
            )
        }
        RouteActionButton(
            selected = interaction.reaction == RouteReaction.Disliked,
            contentDescription = "不喜欢这条路线",
            onClick = { onReact(RouteReaction.Disliked) }
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_route_action_dislike),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                colorFilter = ColorFilter.tint(if (interaction.reaction == RouteReaction.Disliked) WarningAmber else AppTextMuted)
            )
        }
    }
}

@Composable
internal fun RouteActionButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color = if (selected) RouteTeal.copy(alpha = 0.10f) else AppSurfaceMuted.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, if (selected) RouteTeal.copy(alpha = 0.32f) else AppBorder.copy(alpha = 0.58f))
        ) {
            Box(modifier = Modifier.size(15.dp), contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}

@Composable
internal fun RouteDetailPeekHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 8.dp)
            .height(38.dp)
            .shadow(6.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = AppSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .background(AppBorder, CircleShape)
            )
        }
    }
}

@Composable
internal fun RouteSheetHandle(
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = modifier
            .width(64.dp)
            .height(20.dp)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
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
internal fun RouteStopDetailRow(
    stop: RouteStop,
    routeColor: Color,
    onLocate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLocate)
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = routeColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stop.order.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stop.name,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatStopLabel(stop),
                color = AppTextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!stop.description.isNullOrBlank()) {
                Text(
                    text = stop.description,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!stop.reason.isNullOrBlank()) {
                Text(
                    text = stop.reason,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val nextText = buildString {
                if (stop.stayMinutes != null) {
                    append("停留 ${stop.stayMinutes} 分钟")
                }
                if (!stop.transportToNext.isNullOrBlank() && stop.durationToNextMinutes != null) {
                    if (isNotEmpty()) {
                        append(" · ")
                    }
                    append("${formatTransportMode(stop.transportToNext)} ${stop.durationToNextMinutes} 分钟")
                }
            }
            if (nextText.isNotBlank()) {
                Text(
                    text = nextText,
                    color = routeColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!stop.riskNote.isNullOrBlank()) {
                Text(
                    text = stop.riskNote,
                    color = RouteTeal,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        IconButton(onClick = onLocate) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "定位到${stop.name}",
                tint = routeColor
            )
        }
    }
}
