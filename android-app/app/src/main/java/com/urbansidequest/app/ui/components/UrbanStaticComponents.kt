package com.urbansidequest.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.theme.AppBackground
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppText
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal
import com.urbansidequest.app.ui.theme.RouteSecondary
import com.urbansidequest.app.ui.theme.WarningAmber
import com.urbansidequest.app.ui.theme.WarningSurface

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
            shape = RoundedCornerShape(12.dp),
            color = if (selected) DeepTeal.copy(alpha = 0.08f) else AppSurface,
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
                    color = if (selected) DeepTeal else AppText,
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
    enabled: Boolean = true
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        onClick = onClick,
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
            shape = RoundedCornerShape(8.dp),
            color = AppSurface,
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
