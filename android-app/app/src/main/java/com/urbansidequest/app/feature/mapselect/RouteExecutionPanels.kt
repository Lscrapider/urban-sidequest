package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber

private val ExecutionStopBadgeSize = 34.dp
private val ExecutionProgressNodeSize = 24.dp

@Composable
internal fun RouteExecutionPanel(
    route: GeneratedRoute,
    stop: RouteStop,
    completedStopIds: Set<String>,
    skippedStopIds: Set<String>,
    distanceMeters: Int?,
    durationMinutes: Int?,
    canCheckIn: Boolean,
    onShowDetail: () -> Unit,
    onCheckIn: () -> Unit,
    onUnableToArrive: () -> Unit,
    onFinishEarly: () -> Unit,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteStop, RouteStop) -> Unit
) {
    val routeColor = RouteTeal
    val stops = route.stops.sortedBy(RouteStop::order)
    val activeIndex = stops.indexOfFirst { it.id == stop.id }.coerceAtLeast(0)
    val displayOrdinal = activeIndex + 1
    val distanceText = distanceMeters?.let(::formatDistance)
    val etaText = durationMinutes?.let { minutes -> "预计 $minutes min" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RouteExecutionHandle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "下一站",
                    color = routeColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onFinishEarly)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    text = "提前结束",
                    color = routeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteStopNumberBadge(
                    number = displayOrdinal,
                    color = routeColor
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = stop.name,
                            color = AppText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            modifier = Modifier.clickable(onClick = onShowDetail),
                            shape = RoundedCornerShape(999.dp),
                            color = AppSurface,
                            border = BorderStroke(1.dp, AppBorder)
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                text = "详情 ›",
                                color = AppText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (distanceText != null || etaText != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            distanceText?.let {
                                Text(
                                    text = it,
                                    color = AppText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            etaText?.let {
                                Text(
                                    text = it,
                                    color = AppTextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (stop.imageUrls.isNotEmpty()) {
                    RemotePoiImage(
                        modifier = Modifier
                            .size(width = 96.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        imageUrl = stop.imageUrls.first(),
                        contentDescription = "${stop.name} 图片",
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    enabled = canCheckIn,
                    onClick = onCheckIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = routeColor,
                        contentColor = Color.White,
                        disabledContainerColor = AppSurfaceMuted,
                        disabledContentColor = AppTextMuted
                    )
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "到达打卡", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    onClick = onUnableToArrive,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText)
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Filled.Close,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "无法到达", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = AppBorder.copy(alpha = 0.55f))
            RouteExecutionProgressHeader(
                completedDisplay = displayOrdinal,
                totalCount = stops.size
            )
            RouteExecutionProgressTimeline(
                route = route,
                routeColor = routeColor,
                completedStopIds = completedStopIds,
                skippedStopIds = skippedStopIds,
                currentStopId = stop.id,
                onSelectStop = onSelectStop,
                onSelectSegment = onSelectSegment
            )
        }
    }
}

@Composable
internal fun RouteExecutionCompactPanel(
    route: GeneratedRoute,
    currentStop: RouteStop,
    selectedStop: RouteStop,
    completedStopIds: Set<String>,
    skippedStopIds: Set<String>,
    distanceMeters: Int?,
    durationMinutes: Int?,
    canCheckIn: Boolean,
    onConfirmCheckIn: () -> Unit,
    onFinishEarly: () -> Unit,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteStop, RouteStop) -> Unit
) {
    val stops = route.stops.sortedBy(RouteStop::order)
    val activeIndex = stops.indexOfFirst { it.id == selectedStop.id }.coerceAtLeast(0)
    val displayOrdinal = activeIndex + 1
    val isCurrentStop = selectedStop.id == currentStop.id
    val legText = listOfNotNull(
        distanceMeters?.let(::formatDistance),
        durationMinutes?.let { minutes -> "预计 $minutes min" }
    ).joinToString(" · ")
    val statusText = when {
        isCurrentStop && canCheckIn -> "你已进入 ${CHECK_IN_RADIUS_METERS} 米范围，确认后记录这一站。"
        isCurrentStop && legText.isNotBlank() -> "$legText，抵达后确认这一站。"
        isCurrentStop -> "抵达后确认这一站。"
        selectedStop.id in skippedStopIds -> "这一站已跳过，可继续查看路线进度。"
        selectedStop.id in completedStopIds -> "这一站已记录，可继续查看路线进度。"
        else -> "当前下一站是${currentStop.name}，可在地图上查看其他站点。"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "路线 ${route.routeCode} · 第 ${displayOrdinal}/${stops.size} 站",
                        color = RouteTeal,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedStop.name,
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    modifier = Modifier
                        .height(46.dp)
                        .padding(start = 12.dp),
                    enabled = isCurrentStop,
                    onClick = onConfirmCheckIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RouteTeal,
                        contentColor = Color.White,
                        disabledContainerColor = AppSurfaceMuted,
                        disabledContentColor = AppTextMuted
                    )
                ) {
                    Text(text = "确认打卡", fontWeight = FontWeight.Bold)
                }
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onFinishEarly)
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    text = "提前结束",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            RouteExecutionProgressTimeline(
                route = route,
                routeColor = RouteTeal,
                completedStopIds = completedStopIds,
                skippedStopIds = skippedStopIds,
                currentStopId = currentStop.id,
                compact = true,
                onSelectStop = onSelectStop,
                onSelectSegment = onSelectSegment
            )
        }
    }
}

@Composable
internal fun RouteCompletionPendingPanel(route: GeneratedRoute) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, RouteTeal.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "路线 ${route.routeCode} 已打完",
                color = AppText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "正在保存这次路线完成状态。",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RouteExecutionHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .background(AppBorder, CircleShape)
        )
    }
}

@Composable
private fun RouteStopNumberBadge(
    number: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(ExecutionStopBadgeSize),
        shape = CircleShape,
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RouteExecutionProgressHeader(
    completedDisplay: Int,
    totalCount: Int
) {
    val percent = if (totalCount > 0) {
        (completedDisplay * 100 / totalCount).coerceIn(0, 100)
    } else {
        0
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "路线进度",
            color = AppText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$completedDisplay / $totalCount 站 · $percent%",
            color = AppTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RouteExecutionProgressTimeline(
    route: GeneratedRoute,
    routeColor: Color,
    completedStopIds: Set<String>,
    skippedStopIds: Set<String>,
    currentStopId: String,
    compact: Boolean = false,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteStop, RouteStop) -> Unit
) {
    val stops = route.stops.sortedBy(RouteStop::order)
    if (stops.isEmpty()) {
        return
    }
    val currentIndex = stops.indexOfFirst { it.id == currentStopId }.coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        stops.forEachIndexed { index, stop ->
            val active = index == currentIndex
            val skipped = stop.id in skippedStopIds
            val completed = stop.id in completedStopIds || (index < currentIndex && !skipped)
            val nodeSize = ExecutionProgressNodeSize
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.Button
                        contentDescription = "查看${stop.name}"
                    }
                    .clickable { onSelectStop(stop) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(nodeSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (index > 0) {
                        val previousStop = stops[index - 1]
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.5f)
                                .height(nodeSize)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "查看${previousStop.name}到${stop.name}怎么去"
                                }
                                .clickable { onSelectSegment(previousStop, stop) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (completed) routeColor else AppBorder)
                            )
                        }
                    }
                    if (index < stops.lastIndex) {
                        val nextStop = stops[index + 1]
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(0.5f)
                                .height(nodeSize)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "查看${stop.name}到${nextStop.name}怎么去"
                                }
                                .clickable { onSelectSegment(stop, nextStop) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (index < currentIndex) routeColor else AppBorder)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(nodeSize),
                        shape = CircleShape,
                        color = when {
                            active -> routeColor
                            skipped -> WarningAmber.copy(alpha = 0.18f)
                            completed -> routeColor.copy(alpha = 0.18f)
                            else -> AppSurface
                        },
                        border = BorderStroke(
                            width = if (active) 2.dp else 1.2.dp,
                            color = when {
                                active || completed -> routeColor
                                skipped -> WarningAmber
                                else -> AppBorder
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                    .background(Color.White, CircleShape)
                                )
                            } else if (skipped) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(WarningAmber, CircleShape)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stop.name.take(if (compact) 4 else 5),
                    color = if (active) AppText else AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
