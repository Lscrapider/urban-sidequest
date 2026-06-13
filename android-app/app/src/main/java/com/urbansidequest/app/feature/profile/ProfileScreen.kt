package com.urbansidequest.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanSection
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

@Composable
fun ProfileScreen(
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(subtitle = "个人信息")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = DeepTeal,
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
                            text = "城",
                            color = AppSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "城市探索者",
                            color = AppText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "默认从当前位置出发 · 偏好步行 + 地铁",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssetMetric(title = "已完成", value = "8", modifier = Modifier.weight(1f))
                AssetMetric(title = "收藏路线", value = "12", modifier = Modifier.weight(1f))
                AssetMetric(title = "私人地点", value = "19", modifier = Modifier.weight(1f))
            }

            UrbanSection {
                Text(
                    text = "我的城市地图",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ProfileRow(title = "北京", subtitle = "3 条路线 · 7 个私人地点")
                ProfileRow(title = "上海", subtitle = "2 条路线 · 4 个私人地点")
                ProfileRow(title = "苏州", subtitle = "1 条路线 · 3 个私人地点")
            }

            UrbanSection {
                Text(
                    text = "最近路线",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ProfileRow(title = "路线 A · 天安门半日", subtitle = "进行中 · 剩余约 1h 40m")
                ProfileRow(title = "苏州河半日", subtitle = "已完成 · 4h 20m")
            }

            UrbanSection {
                Text(
                    text = "偏好设置",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ProfileRow(title = "默认交通方式", subtitle = "步行 + 地铁")
                ProfileRow(title = "路线目标", subtitle = "稳妥省心优先")
                ProfileRow(title = "兴趣偏好", subtitle = "城市历史、河岸散步、咖啡休息")
            }

            UrbanSection {
                Text(
                    text = "反馈与数据",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ProfileRow(title = "路线反馈", subtitle = "帮助修正节奏、排队和替换策略")
                ProfileRow(title = "问题上报", subtitle = "位置、营业时间或路线不可执行")
                ProfileRow(title = "数据来源说明", subtitle = "地图数据、自建反馈和公开基础信息")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Profile,
            onMapClick = onOpenMap,
            onRoutesClick = onOpenRoutes,
            onProfileClick = {}
        )
    }
}

@Composable
private fun AssetMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = value,
                color = DeepTeal,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = AppTextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = AppText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = AppTextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
