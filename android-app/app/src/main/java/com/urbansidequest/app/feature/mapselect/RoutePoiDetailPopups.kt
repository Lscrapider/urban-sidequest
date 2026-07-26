package com.urbansidequest.app.feature.mapselect

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.Polyline
import com.urbansidequest.app.R
import com.urbansidequest.app.data.image.RemoteImageRepository
import com.urbansidequest.app.domain.model.RouteSegment
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteSegmentPopup(
    routeColor: Int,
    payload: RouteSegmentPolylinePayload,
    onClose: () -> Unit
) {
    val color = routeColor.toComposeColor()
    val segment = payload.segment
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${payload.routeCode} 线第 ${segment.order} 段怎么去",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildRouteSegmentTitle(payload),
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭路线段说明",
                        tint = AppTextMuted
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrbanChip(text = formatTransportMode(segment.mode), selected = true)
                UrbanChip(text = "${segment.durationMinutes} 分钟")
                UrbanChip(text = formatDistance(segment.distanceMeters))
                if (payload.isEstimated) {
                    UrbanChip(text = "估算路线")
                }
            }

            if (payload.isEstimated) {
                Text(
                    text = "当前路段没有拿到真实路径规划，地图上以低透明虚线显示估算路线。",
                    color = WarningAmber,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = segment.summary,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segment.steps.take(MAX_VISIBLE_ROUTE_STEPS).forEach { step ->
                    Text(
                        text = "${step.order}. ${step.instruction}",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PoiDetailPopup(
    routeCode: String,
    routeColor: Int,
    stop: RouteStop,
    distanceMetersOverride: Int? = null,
    showStopDistanceFallback: Boolean = true,
    onLocate: () -> Unit,
    onClose: () -> Unit
) {
    val color = routeColor.toComposeColor()
    var selectedImageIndex by remember(stop.id, stop.imageUrls) { mutableStateOf<Int?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = color
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stop.order.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stop.name,
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$routeCode 线 · ${formatStopLabel(stop)}",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭地点详情",
                        tint = AppTextMuted
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stop.rating?.let { rating ->
                    UrbanChip(text = "评分 ${formatRating(rating)}")
                }
                stop.stayMinutes?.let { minutes ->
                    UrbanChip(text = "停留 ${minutes} 分钟", selected = true)
                }
                if (!stop.transportToNext.isNullOrBlank() && stop.durationToNextMinutes != null) {
                    UrbanChip(text = "下一段 ${formatTransportMode(stop.transportToNext)} ${stop.durationToNextMinutes} 分钟")
                }
                val displayDistance = distanceMetersOverride
                    ?: stop.distanceToNextMeters.takeIf { showStopDistanceFallback }
                displayDistance?.let { meters ->
                    UrbanChip(text = "距离 ${formatDistance(meters)}")
                }
            }

            if (stop.imageUrls.isNotEmpty()) {
                PoiImageCarousel(
                    poiName = stop.name,
                    imageUrls = stop.imageUrls,
                    onImageClick = { index -> selectedImageIndex = index }
                )
            }

            if (!stop.description.isNullOrBlank()) {
                Text(
                    text = stop.description,
                    color = AppText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!stop.riskNote.isNullOrBlank()) {
                Text(
                    text = stop.riskNote,
                    color = RouteTeal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                onClick = onLocate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "定位到这个地点", fontWeight = FontWeight.Bold)
            }
        }
    }

    selectedImageIndex?.let { imageIndex ->
        PoiImageDialog(
            imageUrls = stop.imageUrls,
            selectedIndex = imageIndex.coerceIn(stop.imageUrls.indices),
            onSelectIndex = { selectedImageIndex = it },
            onDismiss = { selectedImageIndex = null }
        )
    }
}

@Composable
internal fun PoiImageCarousel(
    poiName: String,
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        imageUrls.forEachIndexed { index, imageUrl ->
            RemotePoiImage(
                modifier = Modifier
                    .width(132.dp)
                    .height(86.dp)
                    .clickable { onImageClick(index) },
                imageUrl = imageUrl,
                contentDescription = "$poiName 图片 ${index + 1}"
            )
        }
    }
}

@Composable
internal fun RemotePoiImage(
    modifier: Modifier = Modifier,
    imageUrl: String,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = AppSurfaceMuted,
    placeholderTextColor: Color = AppTextMuted
) {
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoadFinished by remember(imageUrl) { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        isLoadFinished = false
        bitmap = RemoteImageRepository.loadBitmap(
            imageUrl = imageUrl,
            connectTimeoutMillis = IMAGE_CONNECT_TIMEOUT_MILLIS,
            readTimeoutMillis = IMAGE_READ_TIMEOUT_MILLIS
        )
        isLoadFinished = true
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(placeholderColor)
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale
            )
        } else {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = if (isLoadFinished) "图片加载失败" else "图片加载中",
                color = placeholderTextColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun PoiImageDialog(
    imageUrls: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            RemotePoiImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 560.dp)
                    .padding(horizontal = 20.dp),
                imageUrl = imageUrls[selectedIndex],
                contentDescription = "地点图片 ${selectedIndex + 1}",
                contentScale = ContentScale.Fit,
                placeholderColor = Color.Transparent,
                placeholderTextColor = Color.White
            )

            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                onClick = onDismiss
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭大图",
                    tint = Color.White
                )
            }

            if (selectedIndex > 0) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                    onClick = { onSelectIndex(selectedIndex - 1) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一张图片",
                        tint = Color.White
                    )
                }
            }

            if (selectedIndex < imageUrls.lastIndex) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    onClick = { onSelectIndex(selectedIndex + 1) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一张图片",
                        tint = Color.White
                    )
                }
            }

            if (imageUrls.size > 1) {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    text = "${selectedIndex + 1} / ${imageUrls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
