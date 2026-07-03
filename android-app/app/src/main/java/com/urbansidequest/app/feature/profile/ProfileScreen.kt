package com.urbansidequest.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.ui.components.UrbanBadge
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.components.UrbanBottomNavigationBar
import com.urbansidequest.app.ui.components.UrbanChip
import com.urbansidequest.app.ui.components.UrbanDestination
import com.urbansidequest.app.ui.components.UrbanListContainer
import com.urbansidequest.app.ui.components.UrbanMetricGrid
import com.urbansidequest.app.ui.components.UrbanPrimaryButton
import com.urbansidequest.app.ui.components.UrbanScreenTitle
import com.urbansidequest.app.ui.components.UrbanSecondaryButton
import com.urbansidequest.app.ui.components.UrbanTaskCard
import com.urbansidequest.app.ui.components.UrbanTopBar
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.DeepTealDark
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.WarningSurface
import kotlin.math.floor

data class ExplorationPreferenceAnswers(
    val interestCodes: Set<String> = emptySet(),
    val distanceSensitivityCode: String? = null,
    val budgetSensitivityCode: String? = null,
    val transferSensitivityCode: String? = null,
    val hiddenGemCode: String? = null
)

private data class ProfileStats(
    val completedRoutes: Int,
    val travelDistanceMeters: Long,
    val favoriteRoutes: Int,
    val likedRoutes: Int,
    val dislikedRoutes: Int,
    val explorationStreakDays: Int,
    val profileConfidence: Double
)

private data class ProfileLevel(
    val number: Int,
    val title: String,
    val nextHint: String
)

private data class AchievementSpec(
    val title: String,
    val description: String,
    val marker: String,
    val earned: Boolean
)

private data class QuestionnaireOption(
    val code: String,
    val label: String
)

@Composable
fun ProfileScreen(
    nickname: String = "",
    completedRouteCount: Int = 0,
    travelDistanceMeters: Long = 0L,
    routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    explorationStreakDays: Int = 0,
    preferenceAnswers: ExplorationPreferenceAnswers? = null,
    onOpenQuestionnaire: () -> Unit = {},
    onOpenFavoriteRoutes: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {}
) {
    val stats = remember(completedRouteCount, travelDistanceMeters, routeInteractions, explorationStreakDays, preferenceAnswers) {
        buildProfileStats(
            completedRouteCount = completedRouteCount,
            travelDistanceMeters = travelDistanceMeters,
            routeInteractions = routeInteractions,
            explorationStreakDays = explorationStreakDays,
            preferenceAnswers = preferenceAnswers
        )
    }
    val level = remember(stats) { resolveProfileLevel(stats) }
    val achievements = remember(stats) { buildAchievements(stats) }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "我的资产",
                title = "${displayNickname}的城市资产"
            )
            UrbanTaskCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UrbanBadge(text = "Lv.${level.number} ${level.title}", style = UrbanBadgeStyle.RouteA)
                    UrbanBadge(text = "连续探索 ${stats.explorationStreakDays} 天")
                }
                Text(
                    text = "轻量成就，专注个人路线",
                    color = AppText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "用走过路线、收藏和反馈沉淀你的城市资产，下一次路线 A 会更贴近你的节奏。",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                UrbanMetricGrid(
                    items = listOf(
                        stats.completedRoutes.toString() to "完成路线",
                        formatKilometers(stats.travelDistanceMeters) to "出行公里",
                        stats.favoriteRoutes.toString() to "收藏路线"
                    )
                )
                LevelProgressCard(level = level)
            }

            AchievementCollectionCard(achievements = achievements)

            UrbanListContainer {
                AssetRow(
                    title = "收藏路线",
                    description = "${stats.favoriteRoutes} 条已收藏路线，路线库里可继续查看和复走",
                    action = "进入",
                    onClick = onOpenFavoriteRoutes
                )
                AssetRow(
                    title = "完成路线",
                    description = "${stats.completedRoutes} 次路线完成，累计 ${formatKilometers(stats.travelDistanceMeters)} 公里出行",
                    action = "查看",
                    onClick = onOpenRoutes
                )
                AssetRow(
                    title = "路线反馈",
                    description = "${stats.likedRoutes} 次喜欢，${stats.dislikedRoutes} 次不喜欢，用于校准路线判断",
                    action = "记录"
                )
            }

            UrbanTaskCard(highlighted = stats.profileConfidence < 1.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (stats.profileConfidence >= 1.0) "探索偏好已完成" else "探索偏好仍在积累",
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = profileSummary(preferenceAnswers, stats.profileConfidence),
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    UrbanBadge(text = "${(stats.profileConfidence * 100).toInt()}%", style = UrbanBadgeStyle.Reward)
                }
                UrbanPrimaryButton(
                    text = if (preferenceAnswers == null) "开始探索偏好" else "更新探索偏好",
                    onClick = onOpenQuestionnaire
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileQuestionnaireScreen(
    answers: ExplorationPreferenceAnswers? = null,
    onBack: () -> Unit = {},
    onSave: (ExplorationPreferenceAnswers) -> Unit = {}
) {
    var interestCodes by remember(answers) { mutableStateOf(answers?.interestCodes.orEmpty()) }
    var distanceCode by remember(answers) { mutableStateOf(answers?.distanceSensitivityCode) }
    var budgetCode by remember(answers) { mutableStateOf(answers?.budgetSensitivityCode) }
    var transferCode by remember(answers) { mutableStateOf(answers?.transferSensitivityCode) }
    var hiddenGemCode by remember(answers) { mutableStateOf(answers?.hiddenGemCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        UrbanTopBar(
            subtitle = "探索偏好",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UrbanScreenTitle(
                eyebrow = "探索偏好",
                title = "让路线更懂你的节奏"
            )
            QuestionnaireSection(
                title = "兴趣偏好",
                subtitle = "选择长期感兴趣的城市内容，不影响单次路线配置"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InterestOptions.forEach { option ->
                        UrbanChip(
                            text = option.label,
                            selected = option.code in interestCodes,
                            onClick = {
                                interestCodes = if (option.code in interestCodes) {
                                    interestCodes - option.code
                                } else {
                                    interestCodes + option.code
                                }
                            }
                        )
                    }
                }
            }
            SingleChoiceQuestion(
                title = "距离敏感度",
                subtitle = "你对走远路或绕远的接受度？",
                options = DistanceOptions,
                selectedCode = distanceCode,
                onSelect = { distanceCode = it }
            )
            SingleChoiceQuestion(
                title = "预算敏感度",
                subtitle = "你希望路线控制消费吗？",
                options = BudgetOptions,
                selectedCode = budgetCode,
                onSelect = { budgetCode = it }
            )
            SingleChoiceQuestion(
                title = "换乘敏感度",
                subtitle = "你能接受复杂交通或换乘吗？",
                options = TransferOptions,
                selectedCode = transferCode,
                onSelect = { transferCode = it }
            )
            SingleChoiceQuestion(
                title = "小众探索偏好",
                subtitle = "你喜欢小众、本地、非热门地点吗？",
                options = HiddenGemOptions,
                selectedCode = hiddenGemCode,
                onSelect = { hiddenGemCode = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrbanSecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = "稍后再填",
                    onClick = onBack
                )
                UrbanPrimaryButton(
                    modifier = Modifier.weight(1f),
                    text = "保存画像",
                    onClick = {
                        onSave(
                            ExplorationPreferenceAnswers(
                                interestCodes = interestCodes,
                                distanceSensitivityCode = distanceCode,
                                budgetSensitivityCode = budgetCode,
                                transferSensitivityCode = transferCode,
                                hiddenGemCode = hiddenGemCode
                            )
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LevelProgressCard(level: ProfileLevel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = InfoCyanSurface,
        border = BorderStroke(1.dp, InfoCyan.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "当前标志：Lv.${level.number} ${level.title}",
                color = AppText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = level.nextHint,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AchievementCollectionCard(achievements: List<AchievementSpec>) {
    val earnedCount = achievements.count { it.earned }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "徽章",
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已获得 $earnedCount/${achievements.size}，左右滑动查看更多",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                UrbanBadge(text = "更多", style = UrbanBadgeStyle.Reward)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                achievements.forEach { achievement ->
                    AchievementBadgeItem(spec = achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadgeItem(spec: AchievementSpec) {
    val markerSurface = if (spec.earned) WarningSurface else AppSurfaceMuted
    val markerColor = if (spec.earned) DeepTealDark else AppTextMuted
    val borderColor = if (spec.earned) DeepTeal.copy(alpha = 0.22f) else AppBorder.copy(alpha = 0.62f)
    Surface(
        modifier = Modifier.width(92.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (spec.earned) AppSurfaceMuted else AppSurfaceMuted.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = markerSurface,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = spec.marker,
                        color = markerColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = spec.title,
                color = AppText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (spec.earned) "已获得" else "未获得",
                color = AppTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AssetRow(
    title: String,
    description: String,
    action: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
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

@Composable
private fun QuestionnaireSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    UrbanTaskCard {
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
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChoiceQuestion(
    title: String,
    subtitle: String,
    options: List<QuestionnaireOption>,
    selectedCode: String?,
    onSelect: (String) -> Unit
) {
    QuestionnaireSection(title = title, subtitle = subtitle) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                UrbanChip(
                    text = option.label,
                    selected = option.code == selectedCode,
                    onClick = { onSelect(option.code) }
                )
            }
        }
    }
}

private fun buildProfileStats(
    completedRouteCount: Int,
    travelDistanceMeters: Long,
    routeInteractions: Map<String, RouteInteractionState>,
    explorationStreakDays: Int,
    preferenceAnswers: ExplorationPreferenceAnswers?
): ProfileStats {
    val interactions = routeInteractions.values
    return ProfileStats(
        completedRoutes = completedRouteCount,
        travelDistanceMeters = travelDistanceMeters,
        favoriteRoutes = interactions.count { it.isFavorite },
        likedRoutes = interactions.count { it.reaction == RouteReaction.Liked },
        dislikedRoutes = interactions.count { it.reaction == RouteReaction.Disliked },
        explorationStreakDays = explorationStreakDays,
        profileConfidence = preferenceAnswers.profileConfidence()
    )
}

private fun resolveProfileLevel(stats: ProfileStats): ProfileLevel {
    val kilometer = stats.travelDistanceMeters / 1000.0
    return when {
        stats.completedRoutes >= 15 || kilometer >= 60.0 -> ProfileLevel(
            number = 5,
            title = "城市罗盘",
            nextHint = "你已经形成稳定路线资产，后续重点看收藏和反馈质量。"
        )
        stats.completedRoutes >= 8 || kilometer >= 30.0 -> ProfileLevel(
            number = 4,
            title = "深度探索者",
            nextHint = "再完成 ${remaining(stats.completedRoutes, 15)} 条路线，升级到 Lv.5。"
        )
        stats.completedRoutes >= 4 || kilometer >= 12.0 -> ProfileLevel(
            number = 3,
            title = "城区行者",
            nextHint = "再完成 ${remaining(stats.completedRoutes, 8)} 条路线，升级到 Lv.4。"
        )
        stats.completedRoutes >= 1 -> ProfileLevel(
            number = 2,
            title = "路线熟手",
            nextHint = "再完成 ${remaining(stats.completedRoutes, 4)} 条路线，升级到 Lv.3。"
        )
        else -> ProfileLevel(
            number = 1,
            title = "初到城市",
            nextHint = "完成第一条路线后，升级到 Lv.2。"
        )
    }
}

private fun buildAchievements(stats: ProfileStats): List<AchievementSpec> {
    return listOf(
        AchievementSpec("第一条路线", "完成任意 1 条路线", "1", stats.completedRoutes >= 1),
        AchievementSpec("路线熟手", "完成 3 条路线", "3", stats.completedRoutes >= 3),
        AchievementSpec("半日行者", "完成 5 条路线", "5", stats.completedRoutes >= 5),
        AchievementSpec("十路线记录", "完成 10 条路线", "10", stats.completedRoutes >= 10),
        AchievementSpec("二十路线记录", "完成 20 条路线", "20", stats.completedRoutes >= 20),
        AchievementSpec("五十路线记录", "完成 50 条路线", "50", stats.completedRoutes >= 50),
        AchievementSpec("三公里起步", "累计出行 3 公里", "3k", stats.travelDistanceMeters >= 3_000L),
        AchievementSpec("十公里出行", "累计出行 10 公里", "10", stats.travelDistanceMeters >= 10_000L),
        AchievementSpec("二十五公里", "累计出行 25 公里", "25", stats.travelDistanceMeters >= 25_000L),
        AchievementSpec("五十公里", "累计出行 50 公里", "50", stats.travelDistanceMeters >= 50_000L),
        AchievementSpec("百公里城市账本", "累计出行 100 公里", "百", stats.travelDistanceMeters >= 100_000L),
        AchievementSpec("收藏起步", "收藏 1 条路线", "藏", stats.favoriteRoutes >= 1),
        AchievementSpec("收藏整理者", "收藏 3 条路线", "藏", stats.favoriteRoutes >= 3),
        AchievementSpec("路线夹收藏家", "收藏 8 条路线", "夹", stats.favoriteRoutes >= 8),
        AchievementSpec("复走候选库", "收藏 15 条路线", "库", stats.favoriteRoutes >= 15),
        AchievementSpec("第一次认可", "喜欢 1 条路线", "赞", stats.likedRoutes >= 1),
        AchievementSpec("路线认可", "喜欢 3 条路线", "赞", stats.likedRoutes >= 3),
        AchievementSpec("稳定路线派", "喜欢 8 条路线", "稳", stats.likedRoutes >= 8),
        AchievementSpec("路线校准员", "标记 1 条不喜欢路线", "调", stats.dislikedRoutes >= 1),
        AchievementSpec("偏好边界", "标记 3 条不喜欢路线", "界", stats.dislikedRoutes >= 3),
        AchievementSpec("连续探索", "连续打开我的页 3 天", "连", stats.explorationStreakDays >= 3),
        AchievementSpec("一周探索", "连续打开我的页 7 天", "7", stats.explorationStreakDays >= 7),
        AchievementSpec("双周探索", "连续打开我的页 14 天", "14", stats.explorationStreakDays >= 14),
        AchievementSpec("月度探索", "连续打开我的页 30 天", "月", stats.explorationStreakDays >= 30),
        AchievementSpec("偏好起草", "完成部分探索偏好", "偏", stats.profileConfidence >= 0.4),
        AchievementSpec("兴趣清单", "记录兴趣类偏好", "趣", stats.profileConfidence >= 0.6),
        AchievementSpec("偏好完成", "完成探索偏好题组", "好", stats.profileConfidence >= 1.0),
        AchievementSpec("城市新人", "Lv.1 初到城市", "新", stats.completedRoutes >= 0),
        AchievementSpec("路线熟路", "达到 Lv.2 路线熟手", "熟", stats.completedRoutes >= 1),
        AchievementSpec("城区行者", "达到 Lv.3 城区行者", "行", stats.completedRoutes >= 4 || stats.travelDistanceMeters >= 12_000L),
        AchievementSpec("深度探索", "达到 Lv.4 深度探索者", "深", stats.completedRoutes >= 8 || stats.travelDistanceMeters >= 30_000L),
        AchievementSpec("城市罗盘", "达到 Lv.5 城市罗盘", "罗", stats.completedRoutes >= 15 || stats.travelDistanceMeters >= 60_000L)
    )
}

private fun ExplorationPreferenceAnswers?.profileConfidence(): Double {
    if (this == null) {
        return 0.0
    }
    val hasInterests = this.interestCodes.isNotEmpty()
    val sensitivityCount = listOf(
        this.distanceSensitivityCode,
        this.budgetSensitivityCode,
        this.transferSensitivityCode,
        this.hiddenGemCode
    ).count { it != null }
    return when {
        hasInterests && sensitivityCount == 4 -> 1.0
        hasInterests -> 0.6
        sensitivityCount > 0 -> 0.4
        else -> 0.0
    }
}

private fun profileSummary(answers: ExplorationPreferenceAnswers?, confidence: Double): String {
    if (answers == null || confidence == 0.0) {
        return "完成 5 组题后，会得到兴趣、距离、预算、换乘和小众探索偏好。"
    }
    val interestLabels = answers.interestCodes
        .mapNotNull { code -> InterestOptions.firstOrNull { it.code == code }?.label }
        .take(3)
        .joinToString("、")
        .ifBlank { "兴趣待补充" }
    return "已记录 $interestLabels 等探索偏好，会作为后续路线判断的参考。"
}

private fun formatKilometers(meters: Long): String {
    if (meters <= 0) {
        return "0"
    }
    val kilometers = meters / 1000.0
    return if (kilometers >= 10) {
        floor(kilometers).toInt().toString()
    } else {
        String.format("%.1f", kilometers)
    }
}

private fun remaining(current: Int, target: Int): Int {
    return (target - current).coerceAtLeast(1)
}

private val InterestOptions = listOf(
    QuestionnaireOption("MUSEUM", "博物馆"),
    QuestionnaireOption("SCENIC", "风景地标"),
    QuestionnaireOption("LOCAL", "本地烟火"),
    QuestionnaireOption("FOOD", "餐饮补给"),
    QuestionnaireOption("NIGHT", "夜游"),
    QuestionnaireOption("PHOTO", "拍照出片")
)

private val DistanceOptions = listOf(
    QuestionnaireOption("LOW", "很能接受"),
    QuestionnaireOption("MEDIUM", "一般"),
    QuestionnaireOption("HIGH", "比较敏感"),
    QuestionnaireOption("STRICT", "非常敏感")
)

private val BudgetOptions = listOf(
    QuestionnaireOption("LOW", "不太在意"),
    QuestionnaireOption("MEDIUM", "正常控制"),
    QuestionnaireOption("HIGH", "偏低预算"),
    QuestionnaireOption("STRICT", "严格低预算")
)

private val TransferOptions = listOf(
    QuestionnaireOption("LOW", "可以接受"),
    QuestionnaireOption("MEDIUM", "一般"),
    QuestionnaireOption("HIGH", "尽量少换乘"),
    QuestionnaireOption("STRICT", "不想换乘")
)

private val HiddenGemOptions = listOf(
    QuestionnaireOption("NONE", "不喜欢"),
    QuestionnaireOption("LOW", "偶尔可以"),
    QuestionnaireOption("HIGH", "比较喜欢"),
    QuestionnaireOption("MAX", "很喜欢")
)
