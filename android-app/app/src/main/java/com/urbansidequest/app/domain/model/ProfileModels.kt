package com.urbansidequest.app.domain.model

data class ProfileStats(
    val completedRoutes: Int,
    val travelDistanceMeters: Long,
    val favoriteRoutes: Int,
    val likedRoutes: Int,
    val dislikedRoutes: Int,
    val explorationStreakDays: Int,
    val profileConfidence: Double
)

data class ProfileLevel(
    val number: Int,
    val title: String,
    val nextHint: String
)

fun resolveProfileLevel(stats: ProfileStats): ProfileLevel {
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

private fun remaining(current: Int, target: Int): Int {
    return (target - current).coerceAtLeast(0)
}
