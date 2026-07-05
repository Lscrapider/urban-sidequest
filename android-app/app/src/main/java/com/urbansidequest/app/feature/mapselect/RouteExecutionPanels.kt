package com.urbansidequest.app.feature.mapselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.GeneratedRoute
import com.urbansidequest.app.domain.model.RouteStop
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.RouteTeal

@Composable
internal fun RouteCheckInPrompt(
    route: GeneratedRoute,
    stop: RouteStop,
    completedCount: Int,
    totalCount: Int,
    distanceMeters: Int?,
    canCheckIn: Boolean,
    onCheckIn: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp), clip = false),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, if (canCheckIn) RouteTeal else AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "路线 ${route.routeCode} · 第 ${completedCount + 1}/${totalCount} 站",
                        color = RouteTeal,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stop.name,
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildCheckInDistanceText(distanceMeters, canCheckIn),
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (canCheckIn) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "稍后打卡",
                            tint = AppTextMuted
                        )
                    }
                }
            }
            if (canCheckIn) {
                UrbanPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "确认打卡",
                    onClick = onCheckIn
                )
            } else {
                Text(
                    text = "到达目标点 ${CHECK_IN_RADIUS_METERS} 米内后可以主动打卡。",
                    color = RouteTeal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun RouteCompletionPendingPanel(route: GeneratedRoute) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalScreenPadding, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, RouteTeal.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
