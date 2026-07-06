package com.urbansidequest.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.ErrorRed
import com.urbansidequest.app.ui.theme.ErrorSurface
import com.urbansidequest.app.ui.theme.InfoCyan
import com.urbansidequest.app.ui.theme.InfoCyanSurface
import com.urbansidequest.app.ui.theme.RouteTeal
import kotlinx.coroutines.delay

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
