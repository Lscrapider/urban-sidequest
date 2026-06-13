package com.urbansidequest.app.feature.routes

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
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.RouteSecondary

@Composable
fun RoutesScreen(
    onContinueRoute: () -> Unit = {},
    onOpenRouteResult: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(subtitle = "继续当前路线，或查看已生成方案")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "当前路线",
                color = AppText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            CurrentRouteCard(onContinueRoute = onContinueRoute)

            Text(
                text = "已生成路线",
                color = AppText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            GeneratedRouteCard(
                title = "路线 A · 经典稳妥线",
                state = "进行中",
                tag = "稳妥省心",
                metrics = "4h20m · 3.2km · ¥80-180",
                primary = true,
                onOpenRouteResult = onOpenRouteResult
            )
            GeneratedRouteCard(
                title = "路线 B · 老城烟火线",
                state = "未开始",
                tag = "地道烟火",
                metrics = "3h30m · 2.5km · ¥120-200",
                onOpenRouteResult = onOpenRouteResult
            )
            GeneratedRouteCard(
                title = "路线 C · 低预算夜游",
                state = "未开始",
                tag = "低预算",
                metrics = "2h45m · 4.0km · ¥40-90",
                onOpenRouteResult = onOpenRouteResult
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        UrbanBottomNavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            selectedDestination = UrbanDestination.Routes,
            onMapClick = onOpenMap,
            onRoutesClick = {},
            onProfileClick = onOpenProfile
        )
    }
}

@Composable
private fun CurrentRouteCard(onContinueRoute: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteMapPreview(
                label = "路线 A · 进行中",
                modifier = Modifier.height(156.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "下一站：国家博物馆",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "2/5",
                        color = DeepTeal,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                MetricRow(
                    items = listOf(
                        "剩余" to "1h 40m",
                        "下一段" to "步行 12m",
                        "节奏" to "正常"
                    )
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = onContinueRoute,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "继续路线", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GeneratedRouteCard(
    title: String,
    state: String,
    tag: String,
    metrics: String,
    primary: Boolean = false,
    onOpenRouteResult: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, if (primary) DeepTeal else AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = AppText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = metrics,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (primary) DeepTeal.copy(alpha = 0.10f) else AppSurfaceMuted,
                    border = BorderStroke(1.dp, if (primary) DeepTeal else AppBorder)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        text = state,
                        color = if (primary) DeepTeal else RouteSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tag,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onOpenRouteResult,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DeepTeal
                    ),
                    border = BorderStroke(1.dp, DeepTeal)
                ) {
                    Text(text = "查看详情", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
