package com.urbansidequest.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.AreaGreen
import com.urbansidequest.app.ui.theme.AreaGreenSurface
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.DeepTealDark
import com.urbansidequest.app.ui.theme.ErrorRed
import com.urbansidequest.app.ui.theme.ErrorSurface
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.RouteSecondary
import com.urbansidequest.app.ui.theme.RouteTeal
import com.urbansidequest.app.ui.theme.WarningAmber
import com.urbansidequest.app.ui.theme.WarningSurface
import kotlinx.coroutines.delay

@Composable
fun UrbanTopBar(
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AppText
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 4.dp else 0.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = AppText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = AppTextMuted,
                        style = if (title == null) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun UrbanScreenTitle(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = AppText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        trailing?.invoke()
    }
}

enum class UrbanBadgeStyle {
    Default,
    RouteA,
    Area,
    Warning,
    Reward
}

@Composable
fun UrbanBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: UrbanBadgeStyle = UrbanBadgeStyle.Default
) {
    val (container, content, border) = when (style) {
        UrbanBadgeStyle.RouteA -> Triple(DeepTeal, AppSurface, DeepTeal)
        UrbanBadgeStyle.Area -> Triple(AreaGreenSurface, AreaGreen, AreaGreen.copy(alpha = 0.30f))
        UrbanBadgeStyle.Warning -> Triple(WarningSurface, AppText, WarningAmber.copy(alpha = 0.55f))
        UrbanBadgeStyle.Reward -> Triple(InfoCyanSurface, InfoCyan, InfoCyan.copy(alpha = 0.30f))
        UrbanBadgeStyle.Default -> Triple(AppSurfaceMuted, AppTextMuted, Color.Transparent)
    }
    Surface(
        modifier = modifier.heightIn(min = 24.dp),
        shape = CircleShape,
        color = container,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UrbanTaskCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) DeepTeal.copy(alpha = 0.05f) else AppSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) DeepTeal.copy(alpha = 0.24f) else AppBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun UrbanQuestLoadingCard(
    title: String,
    subtitle: String,
    statusText: String,
    badgeText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = DeepTeal,
    badgeStyle: UrbanBadgeStyle = UrbanBadgeStyle.Area,
    illustrationResId: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val motionEnabled = urbanMotionEnabled()
    val borderAlpha: Float
    val dotAlpha: Float
    val flowProgress: Float
    if (motionEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "quest_loading_card")
        val animatedBorderAlpha by infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.46f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = UrbanMotion.QuestBorderPulseMillis,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "quest_loading_border"
        )
        val animatedDotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.42f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = UrbanMotion.QuestBorderPulseMillis,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "quest_loading_dot"
        )
        val animatedFlowProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = UrbanMotion.QuestFlowMillis,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "quest_loading_flow"
        )
        borderAlpha = animatedBorderAlpha
        dotAlpha = animatedDotAlpha
        flowProgress = animatedFlowProgress
    } else {
        borderAlpha = 0.30f
        dotAlpha = 0.72f
        flowProgress = 0f
    }

    val interactionModifier = if (onClick == null) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    }

    Surface(
        modifier = interactionModifier,
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = borderAlpha))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = title,
                        color = AppText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                UrbanBadge(text = badgeText, style = badgeStyle)
            }
            if (illustrationResId != null) {
                Image(
                    painter = painterResource(id = illustrationResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accentColor.copy(alpha = dotAlpha), CircleShape)
                )
                Text(
                    text = statusText,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            UrbanQuestFlowLine(
                accentColor = accentColor,
                flowProgress = flowProgress,
                motionEnabled = motionEnabled
            )
        }
    }
}

@Composable
private fun UrbanQuestFlowLine(
    accentColor: Color,
    flowProgress: Float,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = accentColor.copy(alpha = 0.16f),
            size = size,
            cornerRadius = radius
        )
        val segmentWidth = if (motionEnabled) size.width * 0.32f else size.width
        val segmentStart = if (motionEnabled) {
            (size.width + segmentWidth) * flowProgress - segmentWidth
        } else {
            0f
        }
        drawRoundRect(
            color = accentColor.copy(alpha = if (motionEnabled) 0.66f else 0.40f),
            topLeft = Offset(segmentStart, 0f),
            size = Size(segmentWidth, size.height),
            cornerRadius = radius
        )
    }
}

enum class UrbanQuestNoticeTone {
    Info,
    Success,
    Error
}

@Composable
fun UrbanQuestNoticeOverlay(
    visible: Boolean,
    title: String,
    message: String,
    tone: UrbanQuestNoticeTone,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    autoDismissMillis: Long? = null,
    onDismiss: () -> Unit = {}
) {
    val motionDuration = urbanMotionDuration(UrbanMotion.CardStateMillis)
    var overlayVisible by remember(title, message, tone) { mutableStateOf(false) }
    LaunchedEffect(visible, title, message, tone) {
        overlayVisible = visible
    }
    LaunchedEffect(visible, title, message, tone, autoDismissMillis) {
        if (visible && autoDismissMillis != null) {
            delay(autoDismissMillis)
            overlayVisible = false
            delay(motionDuration.toLong())
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible && overlayVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = motionDuration)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = motionDuration, easing = FastOutSlowInEasing),
                    initialOffsetY = { height -> -height / 3 }
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = motionDuration)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = motionDuration, easing = FastOutSlowInEasing),
                    targetOffsetY = { height -> -height / 3 }
                )
        ) {
            val spec = tone.toQuestNoticeSpec()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .semantics {
                        liveRegion = if (tone == UrbanQuestNoticeTone.Error) {
                            LiveRegionMode.Assertive
                        } else {
                            LiveRegionMode.Polite
                        }
                    },
                shape = RoundedCornerShape(18.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, spec.accent.copy(alpha = 0.28f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = spec.accentSurface,
                        border = BorderStroke(1.dp, spec.accent.copy(alpha = 0.24f))
                    ) {
                        UrbanQuestStatusMark(
                            tone = tone,
                            color = spec.accent,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            color = AppText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = message,
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (actionText != null && onAction != null) {
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    role = Role.Button
                                }
                                .clickable(onClick = onAction),
                            shape = CircleShape,
                            color = spec.accent.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, spec.accent.copy(alpha = 0.24f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = actionText,
                                    color = spec.accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UrbanQuestStatusMark(
    tone: UrbanQuestNoticeTone,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.12f
        when (tone) {
            UrbanQuestNoticeTone.Info -> {
                drawCircle(color = color, radius = size.minDimension * 0.26f, center = center)
            }
            UrbanQuestNoticeTone.Success -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.52f),
                    end = Offset(size.width * 0.42f, size.height * 0.74f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.42f, size.height * 0.74f),
                    end = Offset(size.width * 0.82f, size.height * 0.26f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            UrbanQuestNoticeTone.Error -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.28f, size.height * 0.28f),
                    end = Offset(size.width * 0.72f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.72f, size.height * 0.28f),
                    end = Offset(size.width * 0.28f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(
            color = color,
            radius = size.minDimension * 0.44f,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
}

private data class UrbanQuestNoticeToneSpec(
    val accent: Color,
    val accentSurface: Color
)

private fun UrbanQuestNoticeTone.toQuestNoticeSpec(): UrbanQuestNoticeToneSpec {
    return when (this) {
        UrbanQuestNoticeTone.Info -> UrbanQuestNoticeToneSpec(
            accent = InfoCyan,
            accentSurface = InfoCyanSurface
        )
        UrbanQuestNoticeTone.Success -> UrbanQuestNoticeToneSpec(
            accent = RouteTeal,
            accentSurface = DeepTeal.copy(alpha = 0.08f)
        )
        UrbanQuestNoticeTone.Error -> UrbanQuestNoticeToneSpec(
            accent = ErrorRed,
            accentSurface = ErrorSurface
        )
    }
}

@Composable
fun UrbanMetricGrid(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items.forEach { (value, label) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = AppSurfaceMuted
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = value,
                        color = AppText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = label,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun UrbanListContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(content = content)
    }
}

@Composable
fun UrbanSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun UrbanChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button
) {
    val interactionModifier = if (onClick == null) {
        modifier
    } else {
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.role = role
                this.selected = selected
            }
            .clickable(onClick = onClick)
    }
    Box(
        modifier = interactionModifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.height(36.dp),
            shape = CircleShape,
            color = if (selected) DeepTeal else AppSurface,
            border = BorderStroke(1.dp, if (selected) DeepTeal else AppBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (selected) AppSurface else AppText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun UrbanPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressedScale && isPressed) UrbanMotion.PressedScale else 1f,
        animationSpec = tween(
            durationMillis = urbanMotionDuration(UrbanMotion.ClickMillis),
            easing = FastOutSlowInEasing
        ),
        label = "primary_button_pressed_scale"
    )
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = DeepTeal,
            contentColor = AppSurface,
            disabledContainerColor = AppBorder,
            disabledContentColor = AppTextMuted
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UrbanSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, DeepTeal),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DeepTeal,
            disabledContentColor = AppTextMuted
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UrbanSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppSurface,
    borderColor: Color = AppBorder,
    focusedBorderColor: Color = DeepTeal,
    onFocus: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onFocus == null) Modifier else Modifier.onFocusChanged { state ->
                if (state.isFocused) {
                    onFocus()
                }
            }),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
        leadingIcon = leadingIcon ?: {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppTextMuted
            )
        },
        trailingIcon = trailingIcon,
        placeholder = {
            Text(
                text = placeholder,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = borderColor,
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            cursorColor = DeepTeal
        )
    )
}

@Composable
fun MetricRow(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = AppSurfaceMuted,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.first,
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = item.second,
                        color = AppText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = WarningSurface,
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "!",
                color = WarningAmber,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = text,
                color = AppText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    illustrationResId: Int? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (illustrationResId != null) {
                Image(
                    painter = painterResource(id = illustrationResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = title,
                color = AppText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun RouteMapPreview(
    label: String,
    modifier: Modifier = Modifier,
    showAlternative: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val roadColor = AppBorder.copy(alpha = 0.55f)
            val minorRoadColor = AppBorder.copy(alpha = 0.28f)
            val stepY = size.height / 5f
            val stepX = size.width / 5f

            repeat(6) { index ->
                val y = index * stepY
                drawLine(
                    color = if (index % 2 == 0) roadColor else minorRoadColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y + stepY * 0.3f),
                    strokeWidth = 2.dp.toPx()
                )
            }
            repeat(6) { index ->
                val x = index * stepX
                drawLine(
                    color = minorRoadColor,
                    start = Offset(x, 0f),
                    end = Offset(x + stepX * 0.28f, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            val routePath = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.54f,
                    size.width * 0.43f,
                    size.height * 0.60f,
                    size.width * 0.53f,
                    size.height * 0.42f
                )
                cubicTo(
                    size.width * 0.65f,
                    size.height * 0.18f,
                    size.width * 0.78f,
                    size.height * 0.34f,
                    size.width * 0.84f,
                    size.height * 0.20f
                )
            }
            if (showAlternative) {
                val alternativePath = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.72f)
                    quadraticTo(
                        size.width * 0.42f,
                        size.height * 0.78f,
                        size.width * 0.58f,
                        size.height * 0.56f
                    )
                    quadraticTo(
                        size.width * 0.72f,
                        size.height * 0.38f,
                        size.width * 0.84f,
                        size.height * 0.20f
                    )
                }
                drawPath(
                    path = alternativePath,
                    color = RouteSecondary.copy(alpha = 0.72f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            drawPath(
                path = routePath,
                color = DeepTeal,
                style = Stroke(
                    width = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            listOf(
                Offset(size.width * 0.18f, size.height * 0.72f),
                Offset(size.width * 0.53f, size.height * 0.42f),
                Offset(size.width * 0.84f, size.height * 0.20f)
            ).forEachIndexed { index, point ->
                drawCircle(color = Color.White, radius = 12.dp.toPx(), center = point)
                drawCircle(
                    color = DeepTeal,
                    radius = 12.dp.toPx(),
                    center = point,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(color = DeepTeal, radius = if (index == 0) 4.dp.toPx() else 3.dp.toPx(), center = point)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = CircleShape,
            color = AppSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = label,
                color = DeepTeal,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TimelineItem(
    title: String,
    description: String,
    isLast: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = AppSurface,
                border = BorderStroke(2.dp, DeepTeal)
            ) {}
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AppBorder)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun UrbanProgressPanel(
    title: String,
    description: String,
    progress: Float,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DeepTealDark
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = AppSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        color = AppSurface.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                UrbanBadge(text = badgeText, style = UrbanBadgeStyle.Warning)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(AppSurface.copy(alpha = 0.20f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(WarningAmber, CircleShape)
                )
            }
        }
    }
}
