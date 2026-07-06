package com.urbansidequest.app.feature.profile

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.data.image.RemoteImageRepository

@Composable
internal fun ProfileAvatar(
    avatarUrl: String,
    fallbackText: String,
    onClick: () -> Unit
) {
    val resolvedAvatarUrl = remember(avatarUrl) {
        RemoteImageRepository.resolveImageUrl(
            imageUrl = avatarUrl,
            minioRewritePrefix = PROFILE_AVATAR_MINIO_REWRITE_PREFIX
        )
    }
    var bitmap by remember(resolvedAvatarUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(resolvedAvatarUrl) {
        bitmap = null
        if (resolvedAvatarUrl.isBlank()) {
            return@LaunchedEffect
        }
        bitmap = RemoteImageRepository.loadBitmap(
            imageUrl = avatarUrl,
            minioRewritePrefix = PROFILE_AVATAR_MINIO_REWRITE_PREFIX
        )
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

internal fun avatarFallbackText(nickname: String): String {
    val trimmedName = nickname.trim()
    return trimmedName.firstOrNull()?.toString() ?: "城"
}

private const val PROFILE_AVATAR_MINIO_REWRITE_PREFIX = "/urban-sidequest-shares/"
