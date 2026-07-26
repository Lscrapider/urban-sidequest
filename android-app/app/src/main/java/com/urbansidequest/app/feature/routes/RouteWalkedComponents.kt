package com.urbansidequest.app.feature.routes

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.R
import com.urbansidequest.app.data.image.RemoteImageRepository
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.domain.model.RouteHistoryRouteSummary
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted

@Composable
internal fun WalkedRouteRow(
    group: RouteHistoryGroup,
    route: RouteHistoryRouteSummary,
    isFavorite: Boolean,
    onOpenRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareRoute: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenRoute),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.82f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WalkedRouteMapThumbnail(
                modifier = Modifier.size(width = 118.dp, height = 96.dp),
                route = route
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = route.title,
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatCreatedDate(group.createdAt)} 完成",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    UrbanBadge(text = "已完成", style = UrbanBadgeStyle.Area)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RouteMetaItem(iconRes = R.drawable.icon_routes_location, text = formatCompactDistance(route.totalDistanceMeters))
                    RouteMetaItem(iconRes = R.drawable.icon_routes_clock, text = formatDuration(route.totalDurationMinutes))
                    RouteMetaItem(iconRes = R.drawable.icon_routes_flag, text = formatStopCount(route.stopCount))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        WalkedRouteAction(
                            iconRes = R.drawable.icon_routes_star,
                            label = if (isFavorite) "已收藏" else "收藏",
                            onClick = onToggleFavorite
                        )
                        WalkedRouteAction(
                            iconRes = R.drawable.icon_routes_share,
                            label = "分享",
                            onClick = onShareRoute
                        )
                    }
                    RouteLibraryImageIcon(
                        iconRes = R.drawable.icon_routes_chevron_right,
                        contentDescription = "查看路线详情",
                        modifier = Modifier.size(22.dp),
                        tint = AppText
                    )
                }
            }
        }
    }
}

@Composable
internal fun WalkedRoutesSummaryCard(groups: List<RouteHistoryGroup>) {
    val walkedRoutes = groups.mapNotNull { group -> group.routes.firstOrNull() }
    val totalDistanceMeters = walkedRoutes.sumOf { route -> route.totalDistanceMeters }
    val totalDurationMinutes = walkedRoutes.sumOf { route -> route.totalDurationMinutes }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.70f))
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.route_walked_summary_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(AppSurface.copy(alpha = 0.72f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "走过路线",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${walkedRoutes.size} 条已完成",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                WalkedSummaryMetric(
                    modifier = Modifier.weight(0.92f),
                    value = formatCompactDistance(totalDistanceMeters),
                    label = "总距离"
                )
                WalkedSummaryMetric(
                    modifier = Modifier.weight(0.92f),
                    value = formatHourDecimal(totalDurationMinutes),
                    label = "总时间"
                )
            }
        }
    }
}

@Composable
private fun WalkedSummaryMetric(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = AppTextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun WalkedRouteMapThumbnail(
    route: RouteHistoryRouteSummary,
    modifier: Modifier = Modifier
) {
    val snapshotUrl = route.mapSnapshotUrl
    val resolvedSnapshotUrl = remember(snapshotUrl) {
        snapshotUrl?.let { url ->
            RemoteImageRepository.resolveImageUrl(
                imageUrl = url,
                minioRewritePrefix = ROUTE_SHARE_IMAGE_MINIO_PREFIX
            )
        }
    }
    var bitmap by remember(resolvedSnapshotUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoadFinished by remember(resolvedSnapshotUrl) { mutableStateOf(false) }
    LaunchedEffect(resolvedSnapshotUrl) {
        bitmap = null
        isLoadFinished = false
        if (snapshotUrl != null) {
            bitmap = RemoteImageRepository.loadBitmap(
                imageUrl = snapshotUrl,
                minioRewritePrefix = ROUTE_SHARE_IMAGE_MINIO_PREFIX
            )
        }
        isLoadFinished = true
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppSurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "路线${route.routeCode}地图缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.route_thumb_placeholder),
                contentDescription = "路线${route.routeCode}地图缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (resolvedSnapshotUrl != null && !isLoadFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppSurface.copy(alpha = 0.36f))
                )
            }
        }
    }
}

private const val ROUTE_SHARE_IMAGE_MINIO_PREFIX = "/urban-sidequest-shares/"

@Composable
private fun RouteMetaItem(
    @DrawableRes iconRes: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteLibraryImageIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = AppTextMuted
        )
        Text(
            text = text,
            color = AppText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun WalkedRouteAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteLibraryImageIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = AppText
        )
        Text(
            text = label,
            color = AppText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
internal fun MoreWalkedRoutesButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看更多走过路线",
                color = AppText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            RouteLibraryImageIcon(
                iconRes = R.drawable.icon_routes_chevron_right,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppText
            )
        }
    }
}
