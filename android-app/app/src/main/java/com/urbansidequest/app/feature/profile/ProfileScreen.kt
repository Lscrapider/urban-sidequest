package com.urbansidequest.app.feature.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbansidequest.app.BuildConfig
import com.urbansidequest.app.R
import com.urbansidequest.app.domain.model.ProfileLevel
import com.urbansidequest.app.domain.model.ProfileStats
import com.urbansidequest.app.domain.model.RouteInteractionState
import com.urbansidequest.app.domain.model.RouteReaction
import com.urbansidequest.app.domain.model.resolveProfileLevel
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
import com.urbansidequest.app.ui.theme.AreaGreen
import com.urbansidequest.app.ui.theme.AreaGreenSurface
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.WarningAmber
import com.urbansidequest.app.ui.theme.WarningSurface
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import java.net.URL

data class ExplorationPreferenceAnswers(
    val interestCodes: Set<String> = emptySet(),
    val distanceSensitivityCode: String? = null,
    val budgetSensitivityCode: String? = null,
    val transferSensitivityCode: String? = null,
    val hiddenGemCode: String? = null
)

private data class AchievementSpec(
    val title: String,
    val description: String,
    val iconKey: AchievementIconKey,
    val rarity: AchievementRarity,
    val earned: Boolean
)

private enum class AchievementIconKey {
    Route,
    Distance,
    Favorite,
    Like,
    Dislike,
    Streak,
    Preference,
    Level
}

private enum class AchievementRarity {
    Common,
    Advanced,
    Rare,
    Milestone
}

private data class AchievementBadgeColors(
    val cardSurface: Color,
    val markerSurface: Color,
    val border: Color
)

private data class QuestionnaireOption(
    val code: String,
    val label: String
)

@Composable
fun ProfileScreen(
    nickname: String = "",
    avatarUrl: String = "",
    completedRouteCount: Int = 0,
    travelDistanceMeters: Long = 0L,
    routeInteractions: Map<String, RouteInteractionState> = emptyMap(),
    explorationStreakDays: Int = 0,
    preferenceAnswers: ExplorationPreferenceAnswers? = null,
    onAvatarSelected: (Uri) -> Unit = {},
    onOpenQuestionnaire: () -> Unit = {},
    onOpenFavoriteRoutes: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onLogout: () -> Unit = {}
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
    val achievements = remember(stats) { buildAchievements(stats) }
    val level = remember(stats) { resolveProfileLevel(stats) }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onAvatarSelected(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileTopActions()
            ProfileHeaderCard(
                nickname = nickname,
                avatarUrl = avatarUrl,
                level = level,
                stats = stats,
                onAvatarClick = { avatarPicker.launch("image/*") }
            )

            PreferenceSurveyCard(
                onClick = onOpenQuestionnaire
            )

            ProfileMenuCard(
                earnedAchievements = achievements.count { it.earned },
                totalAchievements = achievements.size,
                onOpenAssets = onOpenRoutes,
                onOpenRoutes = onOpenFavoriteRoutes
            )

            LogoutCard(onClick = onLogout)
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

@Composable
private fun ProfileTopActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileTopIcon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = "通知"
        )
        Spacer(modifier = Modifier.width(16.dp))
        ProfileTopIcon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "设置"
        )
    }
}

@Composable
private fun ProfileTopIcon(
    imageVector: ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(25.dp),
            tint = Color(0xFF121B23)
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    nickname: String,
    avatarUrl: String,
    level: ProfileLevel,
    stats: ProfileStats,
    onAvatarClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(
                avatarUrl = avatarUrl,
                fallbackText = avatarFallbackText(nickname),
                onClick = onAvatarClick
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "城市探索者",
                        color = Color(0xFF16212A),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            lineHeight = 29.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LevelBadge(level = level)
                }
                Text(
                    text = "持续探索这座城市",
                    color = Color(0xFF7D8B99),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
        ProfileStatsRow(
            items = listOf(
                ProfileMetric(stats.completedRoutes.toString(), "完成路线"),
                ProfileMetric(formatKilometers(stats.travelDistanceMeters), "出行距离(km)"),
                ProfileMetric(stats.favoriteRoutes.toString(), "收藏路线"),
                ProfileMetric(stats.explorationStreakDays.toString(), "连续天数")
            )
        )
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String,
    fallbackText: String,
    onClick: () -> Unit
) {
    val resolvedAvatarUrl = remember(avatarUrl) { resolveProfileAvatarUrl(avatarUrl) }
    var bitmap by remember(resolvedAvatarUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(resolvedAvatarUrl) {
        bitmap = null
        if (resolvedAvatarUrl.isBlank()) {
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(resolvedAvatarUrl).openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 10_000
                }
                connection.getInputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .size(92.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFE6EEF7)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = fallbackText,
                    color = Color(0xFFFFFFFF),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(level: ProfileLevel) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFE8F0FF)
    ) {
        Text(
            text = "Lv.${level.number}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color(0xFF226BFF),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            fontWeight = FontWeight.Bold
        )
    }
}

private fun avatarFallbackText(nickname: String): String {
    val trimmedName = nickname.trim()
    return trimmedName.firstOrNull()?.toString() ?: "城"
}

private fun resolveProfileAvatarUrl(avatarUrl: String): String {
    val trimmedUrl = avatarUrl.trim()
    if (trimmedUrl.isBlank()) {
        return ""
    }
    val baseUrl = BuildConfig.MINIO_IMAGE_BASE_URL.trimEnd('/')
    if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
        val existingPath = runCatching { URL(trimmedUrl).path }.getOrNull()
        if (!existingPath.isNullOrBlank() && existingPath.startsWith("/urban-sidequest-shares/")) {
            return "$baseUrl$existingPath"
        }
        return trimmedUrl
    }
    val imagePath = if (trimmedUrl.startsWith("/")) trimmedUrl else "/$trimmedUrl"
    return "$baseUrl$imagePath"
}

private data class ProfileMetric(
    val value: String,
    val label: String
)

@Composable
private fun ProfileStatsRow(items: List<ProfileMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            ProfileMetricItem(
                modifier = Modifier.weight(1f),
                value = item.value,
                label = item.label
            )
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .width(1.dp)
                        .background(Color(0xFFE0E6EC))
                )
            }
        }
    }
}

@Composable
private fun ProfileMetricItem(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            color = Color(0xFF111820),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 23.sp,
                lineHeight = 28.sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = Color(0xFF5F6B77),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreferenceSurveyCard(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEDF5FF),
        border = BorderStroke(1.dp, Color(0xFFC9DAFF))
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "探索偏好问卷",
                    color = Color(0xFF1D5ED8),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "让路线更懂你",
                    color = Color(0xFF54677B),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Image(
                painter = painterResource(id = R.drawable.illustration_preference_survey),
                contentDescription = null,
                modifier = Modifier
                    .width(92.dp)
                    .height(66.dp),
                contentScale = ContentScale.Fit
            )
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = Color(0xFF1264F4)
            ) {
                Text(
                    text = "去填写",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuCard(
    earnedAchievements: Int,
    totalAchievements: Int,
    onOpenAssets: () -> Unit,
    onOpenRoutes: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8EE))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileMenuRow(
                icon = Icons.Outlined.EmojiEvents,
                title = "成就墙",
                trailingText = "$earnedAchievements / $totalAchievements"
            )
            ProfileDivider()
            ProfileMenuRow(
                icon = Icons.Outlined.BusinessCenter,
                title = "城市资产",
                subtitle = "地图选区 · 打卡点 · 笔记",
                onClick = onOpenAssets
            )
            ProfileDivider()
            ProfileMenuRow(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = "我的路线",
                subtitle = "已保存和收藏的路线",
                onClick = onOpenRoutes
            )
            ProfileDivider()
            ProfileMenuRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "我的反馈",
                subtitle = "帮助我们做得更好"
            )
            ProfileDivider()
            ProfileMenuRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "帮助与反馈"
            )
            ProfileDivider()
            ProfileMenuRow(
                icon = Icons.Outlined.Info,
                title = "关于城市副本"
            )
        }
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 1.dp,
        color = Color(0xFFE9EEF3)
    )
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 55.dp else 65.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(start = 18.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = Color(0xFF172635)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF182633),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color(0xFF8390A0),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = Color(0xFF536174),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF738092)
        )
    }
}

@Composable
private fun LogoutCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8EE))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color(0xFFF02722)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "退出登录",
                color = Color(0xFFF02722),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                fontWeight = FontWeight.Bold
            )
        }
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
    val displayAchievements = remember(achievements) {
        achievements
            .mapIndexed { index, achievement -> index to achievement }
            .sortedWith(
                compareByDescending<Pair<Int, AchievementSpec>> { it.second.earned }
                    .thenBy { it.first }
            )
            .map { it.second }
    }
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
                displayAchievements.forEach { achievement ->
                    AchievementBadgeItem(spec = achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadgeItem(spec: AchievementSpec) {
    val colors = achievementBadgeColors(spec)
    Surface(
        modifier = Modifier.width(92.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = colors.markerSurface,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = achievementIconRes(spec.iconKey)),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .alpha(if (spec.earned) 1f else 0.42f),
                        tint = Color.Unspecified
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

private fun achievementBadgeColors(spec: AchievementSpec): AchievementBadgeColors {
    if (!spec.earned) {
        return AchievementBadgeColors(
            cardSurface = AppSurfaceMuted.copy(alpha = 0.62f),
            markerSurface = AppSurfaceMuted,
            border = AppBorder.copy(alpha = 0.62f)
        )
    }
    return when (spec.rarity) {
        AchievementRarity.Common -> AchievementBadgeColors(
            cardSurface = AreaGreenSurface.copy(alpha = 0.70f),
            markerSurface = AreaGreenSurface,
            border = AreaGreen.copy(alpha = 0.30f)
        )
        AchievementRarity.Advanced -> AchievementBadgeColors(
            cardSurface = InfoCyanSurface.copy(alpha = 0.76f),
            markerSurface = InfoCyanSurface,
            border = InfoCyan.copy(alpha = 0.32f)
        )
        AchievementRarity.Rare -> AchievementBadgeColors(
            cardSurface = WarningSurface.copy(alpha = 0.78f),
            markerSurface = WarningSurface,
            border = WarningAmber.copy(alpha = 0.42f)
        )
        AchievementRarity.Milestone -> AchievementBadgeColors(
            cardSurface = DeepTeal.copy(alpha = 0.10f),
            markerSurface = AppSurface,
            border = DeepTeal.copy(alpha = 0.30f)
        )
    }
}

private fun achievementIconRes(iconKey: AchievementIconKey): Int {
    return when (iconKey) {
        AchievementIconKey.Route -> R.drawable.ic_achievement_route
        AchievementIconKey.Distance -> R.drawable.ic_achievement_distance
        AchievementIconKey.Favorite -> R.drawable.ic_achievement_favorite
        AchievementIconKey.Like -> R.drawable.ic_achievement_like
        AchievementIconKey.Dislike -> R.drawable.ic_achievement_dislike
        AchievementIconKey.Streak -> R.drawable.ic_achievement_streak
        AchievementIconKey.Preference -> R.drawable.ic_achievement_preference
        AchievementIconKey.Level -> R.drawable.ic_achievement_level
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

private fun buildAchievements(stats: ProfileStats): List<AchievementSpec> {
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
