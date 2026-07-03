package com.urbansidequest.app.feature.discover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.BuildConfig
import com.urbansidequest.app.domain.model.ProfileStats
import com.urbansidequest.app.domain.model.RouteShare
import com.urbansidequest.app.domain.model.resolveProfileLevel
import com.urbansidequest.app.ui.components.EmptyState
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun DiscoverScreen(
    nickname: String = "",
    completedRouteCount: Int = 0,
    travelDistanceMeters: Long = 0L,
    explorationStreakDays: Int = 0,
    routeShares: List<RouteShare> = emptyList(),
    isRouteSharesLoading: Boolean = false,
    routeSharesError: String? = null,
    onOpenShare: (RouteShare) -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val displayNickname = nickname.ifBlank { "城市探索者" }
    val level = remember(completedRouteCount, travelDistanceMeters) {
        resolveProfileLevel(
            ProfileStats(
                completedRoutes = completedRouteCount,
                travelDistanceMeters = travelDistanceMeters,
                favoriteRoutes = 0,
                likedRoutes = 0,
                dislikedRoutes = 0,
                explorationStreakDays = explorationStreakDays,
                profileConfidence = 0.0
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "今日入口",
                title = "今天从哪里开始？"
            )

            Surface(
                shape = CircleShape,
                color = AppSurfaceMuted,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lv.${level.number} ${level.title}",
                        color = AppText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "连续 ${explorationStreakDays} 天",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "$completedRouteCount 条路线",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            UrbanTaskCard {
                UrbanBadge(text = "先定范围", style = UrbanBadgeStyle.Area)
                Text(
                    text = "$displayNickname，先在地图圈出今天的出发范围",
                    color = AppText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "搜索酒店、街区或地标，确认中心点后，系统再生成一条可执行的路线 A。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "约 30 秒完成选区",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                UrbanPrimaryButton(text = "去地图选点", onClick = onOpenMap)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "路线分享",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    UrbanBadge(text = "真实地图", style = UrbanBadgeStyle.Reward)
                }
                when {
                    isRouteSharesLoading -> EmptyState(
                        title = "正在加载路线分享",
                        description = "正在同步大家走完后分享的城市路线。"
                    )
                    routeSharesError != null -> EmptyState(
                        title = "路线分享加载失败",
                        description = routeSharesError
                    )
                    routeShares.isEmpty() -> EmptyState(
                        title = "还没有分享路线",
                        description = "走完路线后可以从走过路线里分享，发现页会展示真实地图缩略图和分享文字。"
                    )
                    else -> RouteShareWaterfall(
                        shares = routeShares,
                        onOpenShare = onOpenShare
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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
private fun RouteShareWaterfall(
    shares: List<RouteShare>,
    onOpenShare: (RouteShare) -> Unit
) {
    val columns = remember(shares) {
        listOf(
            shares.filterIndexed { index, _ -> index % 2 == 0 },
            shares.filterIndexed { index, _ -> index % 2 == 1 }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        columns.forEach { columnShares ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                columnShares.forEach { share ->
                    RouteShareTile(
                        share = share,
                        onClick = { onOpenShare(share) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteShareTile(
    share: RouteShare,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RouteShareImage(
            imageUrl = share.imageUrl,
            contentDescription = "${share.routeTitle} 路线地图缩略图"
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = share.routeTitle,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Text(
                text = share.shareText,
                color = AppText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RouteShareImage(
    imageUrl: String,
    contentDescription: String
) {
    val resolvedImageUrl = remember(imageUrl) { resolveRouteShareImageUrl(imageUrl) }
    var bitmap by remember(resolvedImageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoadFinished by remember(resolvedImageUrl) { mutableStateOf(false) }
    val imageAspectRatio = remember(bitmap, resolvedImageUrl) {
        routeShareImageAspectRatio(bitmap, resolvedImageUrl)
    }
    LaunchedEffect(resolvedImageUrl) {
        bitmap = null
        isLoadFinished = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(resolvedImageUrl).openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 10_000
                }
                connection.getInputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }.getOrNull()
        }
        isLoadFinished = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(AppSurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
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

private fun resolveRouteShareImageUrl(imageUrl: String): String {
    val baseUrl = BuildConfig.MINIO_IMAGE_BASE_URL.trimEnd('/')
    if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
        val existingPath = runCatching { URL(imageUrl).path }.getOrNull()
        if (!existingPath.isNullOrBlank() && existingPath.startsWith("/urban-sidequest-shares/")) {
            return "$baseUrl$existingPath"
        }
        return imageUrl
    }
    val imagePath = if (imageUrl.startsWith("/")) imageUrl else "/$imageUrl"
    return "$baseUrl$imagePath"
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

private const val MIN_ROUTE_SHARE_IMAGE_ASPECT_RATIO = 0.86f
private const val MAX_ROUTE_SHARE_IMAGE_ASPECT_RATIO = 1.62f
