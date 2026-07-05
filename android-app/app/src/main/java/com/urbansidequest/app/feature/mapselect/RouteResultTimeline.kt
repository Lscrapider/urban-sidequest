package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText

@Composable
internal fun RouteDetailCollapsedStrip(
    route: GeneratedRoute,
    routeColor: Color,
    selectedStopId: String?,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteStop, RouteStop) -> Unit,
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .routeSheetDragGesture(
                onDrag = onDrag,
                onDragEnd = onDragEnd
            ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "路线 ${route.routeCode}",
                color = routeColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            RouteStopTimeline(
                modifier = Modifier.weight(1f),
                route = route,
                routeColor = routeColor,
                selectedStopId = selectedStopId ?: route.stops.minByOrNull(RouteStop::order)?.id,
                compact = true,
                onSelectStop = onSelectStop,
                onSelectSegment = onSelectSegment
            )
            IconButton(
                modifier = Modifier.size(36.dp),
                onClick = onExpand
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "展开路线详情",
                    modifier = Modifier.size(20.dp),
                    tint = AppText
                )
            }
        }
    }
}

@Composable
internal fun RouteStopTimeline(
    modifier: Modifier = Modifier,
    route: GeneratedRoute,
    routeColor: Color,
    selectedStopId: String?,
    compact: Boolean = false,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteStop, RouteStop) -> Unit
) {
    val stops = route.stops.sortedBy(RouteStop::order)
    if (stops.isEmpty()) {
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        stops.forEachIndexed { index, stop ->
            val selected = stop.id == selectedStopId
            val nodeSize = if (compact) 24.dp else 28.dp
            val lineColor = AppBorder.copy(alpha = 0.86f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.Button
                        contentDescription = "查看${stop.name}"
                        this.selected = selected
                    }
                    .clickable { onSelectStop(stop) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(nodeSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (index > 0) {
                        val previousStop = stops[index - 1]
                        RouteTimelineSegmentHitArea(
                            modifier = Modifier.align(Alignment.CenterStart),
                            compact = compact,
                            lineColor = lineColor,
                            contentDescription = "查看${previousStop.name}到${stop.name}怎么去",
                            onClick = { onSelectSegment(previousStop, stop) }
                        )
                    }
                    if (index < stops.lastIndex) {
                        val nextStop = stops[index + 1]
                        RouteTimelineSegmentHitArea(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            compact = compact,
                            lineColor = lineColor,
                            contentDescription = "查看${stop.name}到${nextStop.name}怎么去",
                            onClick = { onSelectSegment(stop, nextStop) }
                        )
                    }
                    Surface(
                        modifier = Modifier.size(nodeSize),
                        shape = CircleShape,
                        color = if (selected) routeColor else AppSurface,
                        border = BorderStroke(1.4.dp, if (selected) routeColor else AppBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stop.order.toString(),
                                color = if (selected) Color.White else AppText,
                                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = stop.name.take(4),
                    color = AppText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RouteTimelineSegmentHitArea(
    modifier: Modifier = Modifier,
    compact: Boolean,
    lineColor: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.5f)
            .height(if (compact) 22.dp else 26.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(lineColor)
        )
    }
}

internal fun Modifier.routeSheetDragGesture(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return pointerInput(onDrag, onDragEnd) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, drag ->
                change.consume()
                onDrag(drag)
            },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd
        )
    }
}
