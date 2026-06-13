package com.urbansidequest.app.feature.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.MetricRow
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.TimelineItem
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.components.WarningBanner
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteExecutionScreen(
    onBackToRoutes: () -> Unit = {},
    onOpenPoi: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(
            subtitle = "路线 A · 第 2 站 / 共 5 站",
            onBack = onBackToRoutes
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RouteMapPreview(
                label = "下一站 · 国家博物馆",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            UrbanSection {
                Text(
                    text = "下一步做什么",
                    color = AppText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "步行约 12 分钟到国家博物馆北门，建议先确认预约状态，再决定是否进入。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                MetricRow(
                    items = listOf(
                        "到达" to "12m",
                        "停留" to "70m",
                        "方式" to "步行"
                    )
                )
                WarningBanner(text = "如果现场排队超过 25 分钟，路线会建议跳过并改去广场东侧短停。")
            }

            UrbanSection {
                Text(
                    text = "当前任务",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        onClick = {},
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepTeal,
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "确认到达", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        onClick = onOpenPoi,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DeepTeal
                        ),
                        border = BorderStroke(1.dp, DeepTeal)
                    ) {
                        Text(text = "查看解释", fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = {},
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DeepTeal
                        ),
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Text(text = "拍照打卡")
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = {},
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DeepTeal
                        ),
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Text(text = "跳过 / 替换")
                    }
                }
            }

            UrbanSection {
                Text(
                    text = "后续节点",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TimelineItem(title = "国家博物馆", description = "当前下一站，优先确认预约")
                TimelineItem(title = "前门大街", description = "餐饮补给和返程选择更稳定")
                TimelineItem(title = "地铁站", description = "预计 15:10 结束路线", isLast = true)
            }

            UrbanSection {
                Text(
                    text = "这段路线反馈",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbanChip(text = "节奏刚好", selected = true)
                    UrbanChip(text = "有点累")
                    UrbanChip(text = "有惊喜")
                    UrbanChip(text = "想换安静点")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onMapClick = onOpenMap,
            onRoutesClick = onBackToRoutes,
            onProfileClick = onOpenProfile
        )
    }
}
