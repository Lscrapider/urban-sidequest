package com.urbansidequest.app.feature.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanListContainer
import com.urbansidequest.app.ui.components.UrbanMetricGrid
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTealDark
import com.urbansidequest.app.ui.theme.WarningSurface

@Composable
fun ProfileScreen(
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {}
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "我的资产",
                title = "林沐的城市资产",
                trailing = { ProfileAvatar() }
            )
            UrbanTaskCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UrbanBadge(text = "Lv.7 城市漫游者", style = UrbanBadgeStyle.RouteA)
                    UrbanBadge(text = "连续探索 6 天")
                }
                Text(
                    text = "轻量成就，专注个人路线",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "保留完成路线、私房地点和偏好入口，帮助下一次路线 A 更贴近你的节奏。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                UrbanMetricGrid(
                    items = listOf(
                        "18" to "完成任务",
                        "42.6" to "步行公里",
                        "9" to "收藏路线"
                    )
                )
            }
            SectionTitle("徽章")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AchievementCard(title = "河岸观察者", description = "完成苏州河主题路线", marker = "河", modifier = Modifier.weight(1f))
                AchievementCard(title = "街角发现者", description = "收藏 3 个私房地点", marker = "巷", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AchievementCard(title = "夜游记录员", description = "完成 2 次夜间任务", marker = "夜", modifier = Modifier.weight(1f))
                AchievementCard(title = "稳妥路线派", description = "连续反馈路线风险", marker = "稳", modifier = Modifier.weight(1f))
            }
            UrbanListContainer {
                AssetRow(title = "收藏路线", description = "苏州河半日走法、老城地标线", action = "进入")
                AssetRow(title = "私人地点", description = "咖啡补给、避雨点、夜景点", action = "管理")
                AssetRow(title = "路线偏好", description = "4 小时 · 步行 + 地铁 · 稳妥省心", action = "调整")
            }
            UrbanTaskCard {
                Text("长期画像仍在积累", color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("完成 3 条路线后，会出现更稳定的兴趣与体力建议。", color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Profile,
            onDiscoverClick = onOpenDiscover,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = {}
        )
    }
}

@Composable
private fun ProfileAvatar() {
    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = DeepTealDark) {
        Box(contentAlignment = Alignment.Center) {
            Text("林", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun AchievementCard(
    title: String,
    description: String,
    marker: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = WarningSurface, border = BorderStroke(1.dp, AppBorder)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(marker, color = DeepTealDark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(title, color = AppText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(description, color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AssetRow(title: String, description: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(description, color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        UrbanBadge(text = action)
    }
}
