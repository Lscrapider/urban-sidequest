package com.urbansidequest.app.feature.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.TimelineItem
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanMetricGrid
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanProgressPanel
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanSecondaryButton
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted

@Composable
fun RouteExecutionScreen(
    onBackToRoutes: () -> Unit = {},
    onOpenPoi: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "进行中 / 第 3 站",
                title = "下一站：街角咖啡馆"
            )
            UrbanProgressPanel(
                title = "苏州河半日走法",
                description = "距离 900 米，预计 14 分钟到达；当前段以步行为主。",
                progress = 0.52f,
                badgeText = "进行中"
            )
            UrbanTaskCard {
                Text(
                    text = "当前操作",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UrbanPrimaryButton(
                        modifier = Modifier.weight(1f),
                        text = "到达确认",
                        onClick = onOpenPoi
                    )
                    UrbanSecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "拍照记录",
                        onClick = onOpenPoi
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UrbanSecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "替换此点",
                        onClick = onOpenPoi
                    )
                    UrbanSecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = "跳过此点",
                        onClick = onBackToRoutes
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "当前段",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    UrbanMetricGrid(
                        items = listOf(
                            "14m" to "到达",
                            "900m" to "本段",
                            "2/5" to "检查点",
                            "18:20" to "收尾"
                        )
                    )
                    Text(
                        text = "沿北苏州路向西，过河南北路路口后右转进入支路。巷口标识不明显，注意地图节点。",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("检查点时间线", color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        UrbanBadge(text = "第 3 站", style = UrbanBadgeStyle.RouteA)
                    }
                    TimelineItem(title = "外白渡桥", description = "已完成")
                    TimelineItem(title = "苏州河步道", description = "已完成")
                    TimelineItem(title = "街角咖啡馆", description = "16:35 · 下一站")
                    TimelineItem(title = "老仓库展厅", description = "17:05 · 停止入场提醒")
                    TimelineItem(title = "霓虹巷口", description = "18:20 · 待到达", isLast = true)
                }
            }
            Box(modifier = Modifier.height(4.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onDiscoverClick = onOpenDiscover,
            onMapClick = onOpenMap,
            onRoutesClick = onBackToRoutes,
            onProfileClick = onOpenProfile
        )
    }
}
