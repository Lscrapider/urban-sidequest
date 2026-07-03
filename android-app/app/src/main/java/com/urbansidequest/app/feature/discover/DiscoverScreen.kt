package com.urbansidequest.app.feature.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanListContainer
import com.urbansidequest.app.ui.components.UrbanMetricGrid
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted

@Composable
fun DiscoverScreen(
    nickname: String = "",
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val displayNickname = nickname.ifBlank { "城市探索者" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
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
                    Text("Lv.7 城市漫游者", color = AppText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("连续 6 天", color = AppTextMuted, style = MaterialTheme.typography.labelSmall)
                    Text("18 条路线", color = AppTextMuted, style = MaterialTheme.typography.labelSmall)
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickEntryCard(
                    modifier = Modifier.weight(1f),
                    title = "继续上次副本",
                    description = "苏州河半日走法 · 第 3 站",
                    style = UrbanBadgeStyle.Warning,
                    onClick = onOpenRoutes
                )
                QuickEntryCard(
                    modifier = Modifier.weight(1f),
                    title = "查看已生成路线",
                    description = "路线 A、备选路线和风险提醒",
                    style = UrbanBadgeStyle.Reward,
                    onClick = onOpenRoutes
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "可继续的城市副本",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    UrbanBadge(text = "最近更新", style = UrbanBadgeStyle.Reward)
                }
                UrbanListContainer {
                    CopyRow(
                        title = "苏州河半日走法",
                        meta = "路线 A 已生成 · 当前第 3 站 · 还剩 2 个检查点",
                        action = "继续",
                        onClick = onOpenRoutes
                    )
                    CopyRow(
                        title = "老城地标短线",
                        meta = "昨天完成 · 收藏 1 个私房地点 · 可复走",
                        action = "回看",
                        onClick = onOpenRoutes
                    )
                    CopyRow(
                        title = "霓虹巷口夜游",
                        meta = "已保存 · 适合傍晚 18:00 后重新生成",
                        action = "查看",
                        onClick = onOpenRoutes
                    )
                }
            }

            UrbanTaskCard {
                Text(
                    text = "本周资产",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                UrbanMetricGrid(
                    items = listOf(
                        "18" to "完成任务",
                        "42.6" to "步行公里",
                        "9" to "收藏路线",
                        "6" to "连续天数"
                    )
                )
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
private fun QuickEntryCard(
    title: String,
    description: String,
    style: UrbanBadgeStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    UrbanTaskCard(
        modifier = modifier.clickable(onClick = onClick),
        highlighted = style != UrbanBadgeStyle.Default
    ) {
        UrbanBadge(text = if (style == UrbanBadgeStyle.Warning) "继续" else "回看", style = style)
        Text(text = title, color = AppText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(text = description, color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CopyRow(
    title: String,
    meta: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = title, color = AppText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = meta, color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        UrbanBadge(text = action, style = UrbanBadgeStyle.Default)
    }
}
