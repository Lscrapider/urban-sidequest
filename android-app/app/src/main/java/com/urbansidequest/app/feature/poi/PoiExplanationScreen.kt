package com.urbansidequest.app.feature.poi

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
fun PoiExplanationScreen(
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(
            subtitle = "路线 A · 第 2 站",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RouteMapPreview(
                label = "国家博物馆",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            UrbanSection {
                Text(
                    text = "国家博物馆",
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "展馆 · 距上一站步行约 12 分钟",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                MetricRow(
                    items = listOf(
                        "停留" to "70m",
                        "状态" to "需预约",
                        "费用" to "免费"
                    )
                )
            }

            UrbanSection {
                Text(
                    text = "为什么安排这里",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "它位于路线中段，适合把最长的室内停留安排在午前；离前门返程区不远，后续餐饮和地铁选择更多。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbanChip(text = "避开午后暴晒", selected = true)
                    UrbanChip(text = "衔接顺")
                    UrbanChip(text = "文化历史")
                }
            }

            WarningBanner(text = "主要风险是预约和排队。若无法入场，建议替换为广场东侧短停，不影响后续路线。")

            UrbanSection {
                Text(
                    text = "替换建议",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ReplacementItem(name = "广场东侧短停", reason = "不需要预约，补足空间感")
                ReplacementItem(name = "前门附近咖啡休息", reason = "适合体力下降时缩短路线")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = {},
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DeepTeal
                    ),
                    border = BorderStroke(1.dp, DeepTeal)
                ) {
                    Text(text = "替换此点", fontWeight = FontWeight.Bold)
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "保留这个点", fontWeight = FontWeight.Bold)
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
private fun ReplacementItem(name: String, reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            color = AppText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = reason,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
