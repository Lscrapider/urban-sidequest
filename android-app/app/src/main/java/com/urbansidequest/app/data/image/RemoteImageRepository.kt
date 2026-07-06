package com.urbansidequest.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.urbansidequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object RemoteImageRepository {

    suspend fun loadBitmap(
        imageUrl: String,
        connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
        minioRewritePrefix: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveImageUrl(
            imageUrl = imageUrl,
            minioRewritePrefix = minioRewritePrefix
        )
        if (resolvedUrl.isBlank()) {
            return@withContext null
        }
        runCatching {
            val connection = URL(resolvedUrl).openConnection().apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
            }
            connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull()
    }

    fun resolveImageUrl(
        imageUrl: String,
        minioRewritePrefix: String? = null
    ): String {
        val trimmedUrl = imageUrl.trim()
        if (trimmedUrl.isBlank()) {
            return ""
        }
        val baseUrl = BuildConfig.MINIO_IMAGE_BASE_URL.trimEnd('/')
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            val existingPath = runCatching { URL(trimmedUrl).path }.getOrNull()
            if (
                minioRewritePrefix != null &&
                !existingPath.isNullOrBlank() &&
                existingPath.startsWith(minioRewritePrefix)
            ) {
                return "$baseUrl$existingPath"
            }
            return trimmedUrl
        }
        val imagePath = if (trimmedUrl.startsWith("/")) trimmedUrl else "/$trimmedUrl"
        return "$baseUrl$imagePath"
    }

    private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 6_000
    private const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
}
