package com.urbansidequest.app.feature.routes

import androidx.compose.ui.graphics.Color
import com.urbansidequest.app.domain.model.RouteHistoryGroup
import com.urbansidequest.app.ui.components.UrbanBadgeStyle
import com.urbansidequest.app.ui.theme.AreaGreen
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun isGeneratingHistory(group: RouteHistoryGroup): Boolean {
    return group.generationStatus == "PENDING" || group.generationStatus == "GENERATING"
}

internal fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours}小时${restMinutes}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}

internal fun formatDistance(meters: Int): String {
    return formatCompactDistance(meters)
}

internal fun formatCompactDistance(meters: Int): String {
    return if (meters >= 1000) {
        "${String.format("%.1f", meters / 1000.0)} km"
    } else {
        "${meters} m"
    }
}

internal fun formatHourDecimal(minutes: Int): String {
    return if (minutes >= 60) {
        "${String.format("%.1f", minutes / 60.0)} 小时"
    } else {
        "${minutes} 分钟"
    }
}

internal fun formatRiskLevel(riskLevel: String): String {
    return when (riskLevel) {
        "LOW" -> "风险低"
        "MEDIUM" -> "需留意"
        "HIGH" -> "风险高"
        else -> "风险待确认"
    }
}

internal fun formatExecutionStatus(status: String): String {
    return when (status) {
        "IN_PROGRESS" -> "进行中"
        "COMPLETED" -> "已完成"
        "ABANDONED" -> "已中止"
        else -> "已生成"
    }
}

internal fun formatHistoryStatus(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "PENDING" -> "等待生成"
        "GENERATING" -> "生成中"
        "FAILED" -> "生成失败"
        "PARTIAL_SUCCESS" -> "部分完成"
        else -> formatExecutionStatus(group.executionStatus)
    }
}

internal fun historyStatusBadgeStyle(group: RouteHistoryGroup): UrbanBadgeStyle {
    return when {
        group.generationStatus == "GENERATING" -> UrbanBadgeStyle.Area
        group.generationStatus == "FAILED" -> UrbanBadgeStyle.Warning
        group.executionStatus == "IN_PROGRESS" -> UrbanBadgeStyle.RouteA
        group.executionStatus == "COMPLETED" -> UrbanBadgeStyle.Reward
        else -> UrbanBadgeStyle.Area
    }
}

internal fun historyStatusAccentColor(group: RouteHistoryGroup): Color {
    return when {
        group.generationStatus == "FAILED" -> WarningAmber
        group.executionStatus == "IN_PROGRESS" -> RouteTeal
        group.executionStatus == "COMPLETED" -> InfoCyan
        else -> AreaGreen
    }
}

internal fun routeChipAccentColor(routeCode: String): Color {
    return when (routeCode.uppercase()) {
        "A" -> RouteTeal
        "B" -> InfoCyan
        "C" -> AreaGreen
        else -> WarningAmber
    }
}

internal fun RouteHistoryGroup.withOnlyActiveRoute(): RouteHistoryGroup? {
    val walkedRouteCode = this.activeRouteCode ?: return null
    val walkedRoute = this.routes.firstOrNull { route -> route.routeCode == walkedRouteCode } ?: return null
    return this.copy(routes = listOf(walkedRoute))
}

internal fun formatStopCount(stopCount: Int): String {
    return if (stopCount > 0) {
        "$stopCount 个点"
    } else {
        "— 个点"
    }
}

internal fun formatHistorySubtitle(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "SUCCESS" -> "${formatCreatedAt(group.createdAt)} · ${group.routes.size} 条路线"
        "FAILED" -> "${formatCreatedAt(group.createdAt)} · 生成失败"
        else -> "${formatCreatedAt(group.createdAt)} · ${formatGenerationStage(group.generationStage)}"
    }
}

internal fun formatHistoryProgressText(group: RouteHistoryGroup): String {
    return when (group.generationStatus) {
        "FAILED" -> "路线生成失败，请稍后重试"
        "PENDING" -> "正在等待路线生成"
        else -> formatGenerationStage(group.generationStage)
    }
}

internal fun formatGenerationStage(stage: String?): String {
    return when (stage) {
        "queued" -> "正在准备路线生成"
        "validateRouteRequest" -> "正在检查路线条件"
        "resolveArea" -> "正在确定搜索范围"
        "loadInterestTags" -> "正在匹配兴趣偏好"
        "loadUserPreferenceProfile" -> "正在读取个人偏好"
        "loadPoiSemanticMappings" -> "正在整理地点类型"
        "loadRouteWeather" -> "正在检查天气影响"
        "loadPoiCandidates" -> "正在寻找可用地点"
        "enrichPoiDetails" -> "正在补充地点信息"
        "selectPoiPool" -> "正在筛选候选地点"
        "buildCandidateRoutes" -> "正在生成路线"
        "scoreAndSelectRoutes" -> "正在筛选路线"
        "calibrateSelectedRouteSegments" -> "正在校准路线"
        "filterCalibratedRoutes" -> "正在确认路线可用性"
        "saveRoutePreferenceTrainingSamples" -> "正在保存路线结果"
        "completed" -> "路线生成已结束"
        else -> "正在更新路线状态"
    }
}

internal fun formatCreatedAt(createdAt: String): String {
    if (createdAt.isBlank()) {
        return "刚刚生成"
    }
    return runCatching {
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(Instant.parse(createdAt).atZone(ROUTE_HISTORY_ZONE))
    }.getOrElse {
        createdAt
            .substringBefore(".")
            .replace("T", " ")
            .removeSuffix("Z")
            .ifBlank { "刚刚生成" }
    }
}

internal fun formatCreatedDate(createdAt: String): String {
    if (createdAt.isBlank()) {
        return "刚刚"
    }
    return runCatching {
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .format(Instant.parse(createdAt).atZone(ROUTE_HISTORY_ZONE))
    }.getOrElse {
        createdAt
            .substringBefore(" ")
            .substringBefore("T")
            .ifBlank { "刚刚" }
    }
}

internal const val DEFAULT_SHARE_TEXT = "这条路线走下来很顺，适合直接照着走。"

internal const val MAX_SHARE_TEXT_LENGTH = 240

private val ROUTE_HISTORY_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

internal enum class RouteLibraryTab(
    val label: String,
    val sectionTitle: String,
    val emptyTitle: String,
    val emptyDescription: String
) {
    Generated(
        label = "生成结果",
        sectionTitle = "全部生成组",
        emptyTitle = "没有待查看的生成路线",
        emptyDescription = "生成后的路线会先放在这里，开始并完成后会进入走过路线。"
    ),
    Walked(
        label = "走过路线",
        sectionTitle = "已经走完的路线",
        emptyTitle = "还没有走完路线",
        emptyDescription = "完成最后一个打卡点后，路线会沉淀到这里。"
    )
}

internal enum class GeneratedRouteFilter(val label: String) {
    All("全部"),
    Ready("未开始"),
    Completed("已完成"),
    Generating("生成中"),
    Failed("失败");

    fun matches(group: RouteHistoryGroup): Boolean {
        return when (this) {
            All -> true
            Ready -> group.generationStatus == "SUCCESS" && group.executionStatus == "GENERATED"
            Completed -> group.generationStatus == "SUCCESS" && group.executionStatus == "COMPLETED"
            Generating -> isGeneratingHistory(group)
            Failed -> group.generationStatus == "FAILED"
        }
    }
}
