package com.urbansidequest.app.feature.mapselect

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.Circle
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteGeneration
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import kotlin.math.roundToInt

@Composable
internal fun RouteGeneratedOverviewSheet(
    routes: List<GeneratedRoute>,
    selectedRouteIndex: Int,
    routeGeneration: RouteGeneration?,
    routeInteractions: Map<String, RouteInteractionState>,
    routeInteractionKey: (String, String) -> String,
    onSelectRoute: (Int) -> Unit,
    onApplyRoute: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onToggleFavorite: (String, String, String) -> Unit,
    onReact: (String, String, String, RouteReaction) -> Unit
) {
    var dragAmountY by remember { mutableStateOf(0f) }
    val displayRoutes = routes
    val selectedRoute = routes.getOrNull(selectedRouteIndex) ?: displayRoutes.firstOrNull()
    val sheetOffsetY = dragAmountY.coerceIn(0f, 132f).roundToInt()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, sheetOffsetY) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        dragAmountY = 0f
                    },
                    onDragCancel = {
                        dragAmountY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragAmountY = (dragAmountY + dragAmount).coerceIn(0f, 132f)
                    }
                )
            }
            .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetGrabber(
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "生成候选路线",
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                selectedRoute?.let { route ->
                    RouteGeneratedMetricRow(route = route)
                }
                Text(
                    text = selectedRoute?.summary ?: "默认推荐路线 A，可切换对照",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (displayRoutes.size > 3) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayRoutes.forEachIndexed { index, route ->
                        val interaction = routeGeneration?.candidateSetId
                            ?.let { candidateSetId -> routeInteractions[routeInteractionKey(candidateSetId, route.routeCode)] }
                            ?: RouteInteractionState()
                        GeneratedRouteCandidateCard(
                            modifier = Modifier.width(146.dp),
                            route = route,
                            routeIndex = index,
                            selected = index == selectedRouteIndex,
                            interaction = interaction,
                            onSelect = { onSelectRoute(index) },
                            onToggleFavorite = {
                                routeGeneration?.let { generation ->
                                    onToggleFavorite(generation.requestId, generation.candidateSetId, route.routeCode)
                                }
                            },
                            onReact = { reaction ->
                                routeGeneration?.let { generation ->
                                    onReact(generation.requestId, generation.candidateSetId, route.routeCode, reaction)
                                }
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayRoutes.forEachIndexed { index, route ->
                        val interaction = routeGeneration?.candidateSetId
                            ?.let { candidateSetId -> routeInteractions[routeInteractionKey(candidateSetId, route.routeCode)] }
                            ?: RouteInteractionState()
                        GeneratedRouteCandidateCard(
                            modifier = Modifier.weight(1f),
                            route = route,
                            routeIndex = index,
                            selected = index == selectedRouteIndex,
                            interaction = interaction,
                            onSelect = { onSelectRoute(index) },
                            onToggleFavorite = {
                                routeGeneration?.let { generation ->
                                    onToggleFavorite(generation.requestId, generation.candidateSetId, route.routeCode)
                                }
                            },
                            onReact = { reaction ->
                                routeGeneration?.let { generation ->
                                    onReact(generation.requestId, generation.candidateSetId, route.routeCode, reaction)
                                }
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MapPrimaryActionButton(
                    modifier = Modifier.weight(1f),
                    text = "应用路线 ${selectedRoute?.routeCode ?: "A"}",
                    onClick = { onApplyRoute(selectedRouteIndex) }
                )
                MapSecondaryActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    icon = Icons.Filled.Refresh,
                    text = "重新生成",
                    onClick = onRegenerate
                )
            }
        }
    }
}

@Composable
internal fun RouteGeneratedMetricRow(route: GeneratedRoute) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteGeneratedMetric(
            icon = Icons.Outlined.AccessTime,
            text = "约 ${formatCompactDuration(route.totalDurationMinutes)}"
        )
        RouteGeneratedMetric(
            icon = Icons.Outlined.Route,
            text = formatCompactDistance(route.totalDistanceMeters)
        )
        RouteGeneratedMetric(
            icon = Icons.Outlined.Flag,
            text = "${route.stops.size} 站点"
        )
    }
}

@Composable
internal fun RouteGeneratedMetric(
    icon: ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = AppTextMuted
        )
        Text(
            text = text,
            color = AppText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun GeneratedRouteCandidateCard(
    modifier: Modifier = Modifier,
    route: GeneratedRoute,
    routeIndex: Int,
    selected: Boolean,
    interaction: RouteInteractionState,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReact: (RouteReaction) -> Unit
) {
    val accent = routeColor(routeIndex).toComposeColor()
    Surface(
        modifier = modifier
            .height(132.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        color = AppSurface,
        border = BorderStroke(1.4.dp, if (selected) accent else AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = accent.copy(alpha = if (selected) 0.16f else 0.10f),
                        border = BorderStroke(1.dp, accent.copy(alpha = if (selected) 1f else 0.72f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = route.routeCode,
                                color = accent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = route.title,
                    color = AppText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatCompactDuration(route.totalDurationMinutes)}  ${formatCompactDistance(route.totalDistanceMeters)}  ${route.stops.size} 个点",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = Color(0xFFE5EAF1), thickness = 1.dp)
                GeneratedRouteCardActions(
                    interaction = interaction,
                    onToggleFavorite = onToggleFavorite,
                    onReact = onReact
                )
            }
        }
    }
}

@Composable
internal fun GeneratedRouteCardActions(
    interaction: RouteInteractionState,
    onToggleFavorite: () -> Unit,
    onReact: (RouteReaction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GeneratedRouteCardAction(
            iconRes = R.drawable.icon_route_action_favorite,
            text = "收藏",
            selected = interaction.isFavorite,
            onClick = onToggleFavorite
        )
        GeneratedRouteCardAction(
            iconRes = R.drawable.icon_route_action_like,
            text = "点赞",
            selected = interaction.reaction == RouteReaction.Liked,
            onClick = { onReact(RouteReaction.Liked) }
        )
        GeneratedRouteCardAction(
            iconRes = R.drawable.icon_route_action_dislike,
            text = "不喜欢",
            selected = interaction.reaction == RouteReaction.Disliked,
            onClick = { onReact(RouteReaction.Disliked) }
        )
    }
}

@Composable
internal fun GeneratedRouteCardAction(
    @DrawableRes iconRes: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) ROUTE_A_COLOR.toComposeColor() else AppText
    Column(
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = text,
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(tint)
        )
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
