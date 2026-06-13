package com.urbansidequest.app.feature.routeresult

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.MetricRow
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.TimelineItem
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.WarningBanner
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.RouteSecondary

@Composable
fun RouteResultScreen(
    onAdjustRoute: () -> Unit = {},
    onStartRoute: () -> Unit = {},
    onOpenPoi: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        RouteMapPreview(
            label = "路线 A · 天安门半日",
            showAlternative = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(328.dp)
                .statusBarsPadding()
        )

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "路线 A · 经典稳妥线",
                        color = AppText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "适合第一次到这片区域：先建立空间感，再安排博物馆和步行街。",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                MetricRow(
                    items = listOf(
                        "时长" to "4h 20m",
                        "步行" to "3.2km",
                        "预算" to "¥80-180"
                    )
                )

                WarningBanner(text = "国家博物馆可能需要提前预约，若无法入场会替换为广场东侧短停。")

                UrbanSection {
                    Text(
                        text = "为什么这样安排",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "这条线把室内停留放在中段，避开午后暴晒；末段回到前门一带，餐饮和地铁选择更稳。",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                UrbanSection {
                    Text(
                        text = "路线节点",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Column(modifier = Modifier.clickable(onClick = onOpenPoi)) {
                        TimelineItem(title = "当前位置", description = "从地图选区起点出发，步行进入广场周边")
                        TimelineItem(title = "国家博物馆", description = "核心停留 70 分钟，视预约情况调整")
                        TimelineItem(title = "前门大街", description = "收束到餐饮和返程更稳定的区域", isLast = true)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlternativeRouteCard(
                        title = "路线 B",
                        subtitle = "老城烟火线",
                        modifier = Modifier.weight(1f)
                    )
                    AlternativeRouteCard(
                        title = "路线 C",
                        subtitle = "低预算夜游",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = onAdjustRoute,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DeepTeal
                        ),
                        border = BorderStroke(1.dp, DeepTeal)
                    ) {
                        Text(text = "调整路线", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = onStartRoute,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepTeal,
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "开始路线", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun AlternativeRouteCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppSurfaceMuted,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = RouteSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(1.dp))
            Text(
                text = "作为备选，不抢路线 A 的主决策。",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
