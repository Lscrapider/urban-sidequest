package com.urbansidequest.app.feature.profile

import androidx.compose.ui.graphics.Color
import com.urbansidequest.app.domain.model.ProfileStats
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import kotlin.math.floor

data class ExplorationPreferenceAnswers(
    val interestCodes: Set<String> = emptySet(),
    val distanceSensitivityCode: String? = null,
    val budgetSensitivityCode: String? = null,
    val transferSensitivityCode: String? = null,
    val hiddenGemCode: String? = null
)

internal data class AchievementSpec(
    val title: String,
    val description: String,
    val iconKey: AchievementIconKey,
    val rarity: AchievementRarity,
    val earned: Boolean
)

internal enum class AchievementIconKey {
    Route,
    Distance,
    Favorite,
    Like,
    Dislike,
    Streak,
    Preference,
    Level
}

internal enum class AchievementRarity {
    Common,
    Advanced,
    Rare,
    Milestone
}

internal data class AchievementBadgeColors(
    val cardSurface: Color,
    val markerSurface: Color,
    val border: Color
)

internal data class QuestionnaireOption(
    val code: String,
    val label: String
)

internal fun buildProfileStats(
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

internal fun buildAchievements(stats: ProfileStats): List<AchievementSpec> {
    return listOf(
        AchievementSpec("第一条路线", "完成任意 1 条路线", AchievementIconKey.Route, AchievementRarity.Common, stats.completedRoutes >= 1),
        AchievementSpec("路线熟手", "完成 3 条路线", AchievementIconKey.Route, AchievementRarity.Common, stats.completedRoutes >= 3),
        AchievementSpec("半日行者", "完成 5 条路线", AchievementIconKey.Route, AchievementRarity.Advanced, stats.completedRoutes >= 5),
        AchievementSpec("十路线记录", "完成 10 条路线", AchievementIconKey.Route, AchievementRarity.Advanced, stats.completedRoutes >= 10),
        AchievementSpec("二十路线记录", "完成 20 条路线", AchievementIconKey.Route, AchievementRarity.Rare, stats.completedRoutes >= 20),
        AchievementSpec("五十路线记录", "完成 50 条路线", AchievementIconKey.Route, AchievementRarity.Milestone, stats.completedRoutes >= 50),
        AchievementSpec("三公里起步", "累计出行 3 公里", AchievementIconKey.Distance, AchievementRarity.Common, stats.travelDistanceMeters >= 3_000L),
        AchievementSpec("十公里出行", "累计出行 10 公里", AchievementIconKey.Distance, AchievementRarity.Common, stats.travelDistanceMeters >= 10_000L),
        AchievementSpec("二十五公里", "累计出行 25 公里", AchievementIconKey.Distance, AchievementRarity.Advanced, stats.travelDistanceMeters >= 25_000L),
        AchievementSpec("五十公里", "累计出行 50 公里", AchievementIconKey.Distance, AchievementRarity.Rare, stats.travelDistanceMeters >= 50_000L),
        AchievementSpec("百公里城市账本", "累计出行 100 公里", AchievementIconKey.Distance, AchievementRarity.Milestone, stats.travelDistanceMeters >= 100_000L),
        AchievementSpec("收藏起步", "收藏 1 条路线", AchievementIconKey.Favorite, AchievementRarity.Common, stats.favoriteRoutes >= 1),
        AchievementSpec("收藏整理者", "收藏 3 条路线", AchievementIconKey.Favorite, AchievementRarity.Common, stats.favoriteRoutes >= 3),
        AchievementSpec("路线夹收藏家", "收藏 8 条路线", AchievementIconKey.Favorite, AchievementRarity.Advanced, stats.favoriteRoutes >= 8),
        AchievementSpec("复走候选库", "收藏 15 条路线", AchievementIconKey.Favorite, AchievementRarity.Rare, stats.favoriteRoutes >= 15),
        AchievementSpec("第一次认可", "喜欢 1 条路线", AchievementIconKey.Like, AchievementRarity.Common, stats.likedRoutes >= 1),
        AchievementSpec("路线认可", "喜欢 3 条路线", AchievementIconKey.Like, AchievementRarity.Common, stats.likedRoutes >= 3),
        AchievementSpec("稳定路线派", "喜欢 8 条路线", AchievementIconKey.Like, AchievementRarity.Advanced, stats.likedRoutes >= 8),
        AchievementSpec("路线校准员", "标记 1 条不喜欢路线", AchievementIconKey.Dislike, AchievementRarity.Common, stats.dislikedRoutes >= 1),
        AchievementSpec("偏好边界", "标记 3 条不喜欢路线", AchievementIconKey.Dislike, AchievementRarity.Advanced, stats.dislikedRoutes >= 3),
        AchievementSpec("连续探索", "连续打开我的页 3 天", AchievementIconKey.Streak, AchievementRarity.Common, stats.explorationStreakDays >= 3),
        AchievementSpec("一周探索", "连续打开我的页 7 天", AchievementIconKey.Streak, AchievementRarity.Advanced, stats.explorationStreakDays >= 7),
        AchievementSpec("双周探索", "连续打开我的页 14 天", AchievementIconKey.Streak, AchievementRarity.Rare, stats.explorationStreakDays >= 14),
        AchievementSpec("月度探索", "连续打开我的页 30 天", AchievementIconKey.Streak, AchievementRarity.Milestone, stats.explorationStreakDays >= 30),
        AchievementSpec("偏好起草", "完成部分探索偏好", AchievementIconKey.Preference, AchievementRarity.Common, stats.profileConfidence >= 0.4),
        AchievementSpec("兴趣清单", "记录兴趣类偏好", AchievementIconKey.Preference, AchievementRarity.Advanced, stats.profileConfidence >= 0.6),
        AchievementSpec("偏好完成", "完成探索偏好题组", AchievementIconKey.Preference, AchievementRarity.Rare, stats.profileConfidence >= 1.0),
        AchievementSpec("城市新人", "Lv.1 初到城市", AchievementIconKey.Level, AchievementRarity.Common, stats.completedRoutes >= 0),
        AchievementSpec("路线熟路", "达到 Lv.2 路线熟手", AchievementIconKey.Level, AchievementRarity.Common, stats.completedRoutes >= 1),
        AchievementSpec("城区行者", "达到 Lv.3 城区行者", AchievementIconKey.Level, AchievementRarity.Advanced, stats.completedRoutes >= 4 || stats.travelDistanceMeters >= 12_000L),
        AchievementSpec("深度探索", "达到 Lv.4 深度探索者", AchievementIconKey.Level, AchievementRarity.Rare, stats.completedRoutes >= 8 || stats.travelDistanceMeters >= 30_000L),
        AchievementSpec("城市罗盘", "达到 Lv.5 城市罗盘", AchievementIconKey.Level, AchievementRarity.Milestone, stats.completedRoutes >= 15 || stats.travelDistanceMeters >= 60_000L)
    )
}

internal fun ExplorationPreferenceAnswers?.profileConfidence(): Double {
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

internal fun profileSummary(answers: ExplorationPreferenceAnswers?, confidence: Double): String {
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

internal fun formatKilometers(meters: Long): String {
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

internal val InterestOptions = listOf(
    QuestionnaireOption("MUSEUM", "博物馆"),
    QuestionnaireOption("SCENIC", "风景地标"),
    QuestionnaireOption("LOCAL", "本地烟火"),
    QuestionnaireOption("FOOD", "餐饮补给"),
    QuestionnaireOption("NIGHT", "夜游"),
    QuestionnaireOption("PHOTO", "拍照出片")
)

internal val DistanceOptions = listOf(
    QuestionnaireOption("LOW", "很能接受"),
    QuestionnaireOption("MEDIUM", "一般"),
    QuestionnaireOption("HIGH", "比较敏感"),
    QuestionnaireOption("STRICT", "非常敏感")
)

internal val BudgetOptions = listOf(
    QuestionnaireOption("LOW", "不太在意"),
    QuestionnaireOption("MEDIUM", "正常控制"),
    QuestionnaireOption("HIGH", "偏低预算"),
    QuestionnaireOption("STRICT", "严格低预算")
)

internal val TransferOptions = listOf(
    QuestionnaireOption("LOW", "可以接受"),
    QuestionnaireOption("MEDIUM", "一般"),
    QuestionnaireOption("HIGH", "尽量少换乘"),
    QuestionnaireOption("STRICT", "不想换乘")
)

internal val HiddenGemOptions = listOf(
    QuestionnaireOption("NONE", "不喜欢"),
    QuestionnaireOption("LOW", "偶尔可以"),
    QuestionnaireOption("HIGH", "比较喜欢"),
    QuestionnaireOption("MAX", "很喜欢")
)
