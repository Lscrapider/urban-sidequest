package com.urbansidequest.app.feature.routeconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.components.RouteMapPreview
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteConfigScreen(
    onBack: () -> Unit = {},
    onGenerateRoute: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(
            subtitle = "天安门附近 · 今天",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RouteMapPreview(
                        label = "起点 · 当前位置",
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "当前区域",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "天安门附近",
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "适合 3-5 小时步行 + 地铁路线，起点默认使用当前位置。",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            UrbanSection {
                SectionTitle(title = "基础条件", subtitle = "默认值已经适合第一次生成")
                ConfigRow(label = "出发时间", value = "今天 10:30")
                ConfigRow(label = "可用时长", value = "半日 · 约 4 小时")
                ConfigRow(label = "起终点方式", value = "从当前位置出发，地铁返回")
            }

            UrbanSection {
                SectionTitle(title = "交通组合", subtitle = "路线会优先减少绕路和换乘压力")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbanChip(text = "步行 + 地铁", selected = true)
                    UrbanChip(text = "少走路")
                    UrbanChip(text = "公交优先")
                    UrbanChip(text = "打车可接受")
                }
            }

            UrbanSection {
                SectionTitle(title = "路线目标", subtitle = "主路线 A 会以这个目标作为排序依据")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbanChip(text = "稳妥省心", selected = true)
                    UrbanChip(text = "经典必看")
                    UrbanChip(text = "地道烟火")
                    UrbanChip(text = "低预算")
                }
            }

            UrbanSection {
                SectionTitle(title = "兴趣偏好", subtitle = "可多选，不填也能生成")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbanChip(text = "城市历史", selected = true)
                    UrbanChip(text = "河岸散步", selected = true)
                    UrbanChip(text = "咖啡休息")
                    UrbanChip(text = "展馆")
                    UrbanChip(text = "本地小吃")
                }
            }

            UrbanSection {
                SectionTitle(title = "必去点", subtitle = "先用轻量规则区分强约束和弱约束")
                MustGoItem(name = "国家博物馆", mode = "必须保证")
                MustGoItem(name = "前门大街", mode = "尽量安排")
                AddPlacePlaceholder()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = onGenerateRoute,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "生成路线 A", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "同时生成两条备选路线，用于对照时间、距离和风险。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MustGoItem(name: String, mode: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = AppText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = mode,
            color = DeepTeal,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AddPlacePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "搜索并加入必去点",
            color = AppTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
