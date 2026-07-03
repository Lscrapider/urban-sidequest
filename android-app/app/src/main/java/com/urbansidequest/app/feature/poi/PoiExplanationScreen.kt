package com.urbansidequest.app.feature.poi

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
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanListContainer
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.components.WarningBanner
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted

@Composable
fun PoiExplanationScreen(
    onBack: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
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
                eyebrow = "站点 3 / 补给点",
                title = "街角咖啡馆"
            )
            UrbanTaskCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UrbanBadge(text = "路线 A", style = UrbanBadgeStyle.RouteA)
                    UrbanBadge(text = "停留 25 分钟")
                    UrbanBadge(text = "中段补给")
                }
                Text(
                    text = "它的价值不是评分，而是帮路线 A 保持体力和时间稳定。",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前位置距离下一站 900 米，咖啡馆位于转场中段，适合短暂停留、补水和确认后半段路线。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoiPhoto(label = "门面光线", modifier = Modifier.weight(1f))
                PoiPhoto(label = "靠窗座位", modifier = Modifier.weight(1f))
                PoiPhoto(label = "转场路口", modifier = Modifier.weight(1f))
            }
            WarningBanner(text = "如排队超过 10 分钟，替换为苏河便利店，保持路线 A 的时间结构。")
            UrbanListContainer {
                ExplanationRow(title = "为什么安排在这里", description = "前两站连续步行后需要补给，且这里离老仓库展厅的转场距离可控。")
                ExplanationRow(title = "时间窗口", description = "建议 16:35 到达，17:00 前离开，避免压缩展厅入场时间。")
                ExplanationRow(title = "替换策略", description = "替换点优先选择 500 米内便利店或茶饮，不改变后续站点顺序。")
            }
            UrbanPrimaryButton(text = "知道了", onClick = onBack)
            Spacer(modifier = Modifier.height(4.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onDiscoverClick = onOpenDiscover,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun PoiPhoto(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(12.dp),
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(label, color = AppText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExplanationRow(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, color = AppText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(description, color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
