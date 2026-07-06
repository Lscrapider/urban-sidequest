package com.urbansidequest.app.feature.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.urbansidequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
internal fun ProfileAvatar(
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

internal fun avatarFallbackText(nickname: String): String {
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
