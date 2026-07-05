package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted

@Composable
internal fun RoutePoiRail(
    modifier: Modifier = Modifier,
    route: GeneratedRoute,
    routeIndex: Int,
    currentStopId: String?,
    completedStopIds: Set<String>,
    routeColor: Color,
    onSelectStop: (RouteStop) -> Unit,
    onSelectSegment: (RouteSegmentPolylinePayload) -> Unit
) {
    val stops = route.stops.sortedBy(RouteStop::order)
    Surface(
        modifier = modifier.width(RoutePoiRailWidth),
        shape = RoundedCornerShape(999.dp),
        color = AppSurface.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.34f))
    ) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text(
                    text = route.routeCode,
                    color = routeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            itemsIndexed(
                items = stops,
                key = { _, stop -> stop.id }
            ) { index, stop ->
                RoutePoiRailItem(
                    stop = stop,
                    isCurrent = stop.id == currentStopId,
                    isCompleted = stop.id in completedStopIds,
                    routeColor = routeColor,
                    onClick = { onSelectStop(stop) }
                )
                val nextStop = stops.getOrNull(index + 1)
                if (nextStop != null) {
                    RoutePoiRailConnector(
                        routeColor = routeColor,
                        onClick = {
                            onSelectSegment(
                                buildRailSegmentPayload(
                                    routeIndex = routeIndex,
                                    route = route,
                                    originStop = stop,
                                    destinationStop = nextStop
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoutePoiRailItem(
    stop: RouteStop,
    isCurrent: Boolean,
    isCompleted: Boolean,
    routeColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> routeColor
        isCompleted -> routeColor.copy(alpha = 0.20f)
        else -> AppSurfaceMuted.copy(alpha = 0.82f)
    }
    Box(
        modifier = Modifier
            .size(RoutePoiRailTouchSize)
            .semantics {
                role = Role.Button
                contentDescription = "查看${stop.name}"
                selected = isCurrent
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(if (isCurrent) RoutePoiRailDotSize + 2.dp else RoutePoiRailDotSize),
            shape = CircleShape,
            color = backgroundColor,
            border = BorderStroke(1.dp, if (isCurrent || isCompleted) routeColor else AppBorder.copy(alpha = 0.64f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCompleted && !isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(routeColor, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoutePoiRailConnector(
    routeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(RoutePoiRailTouchSize)
            .height(RoutePoiRailConnectorHeight)
            .semantics {
                role = Role.Button
                contentDescription = "查看这一段怎么去"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(RoutePoiRailConnectorHeight)
                .background(routeColor.copy(alpha = 0.46f), CircleShape)
        )
    }
}
