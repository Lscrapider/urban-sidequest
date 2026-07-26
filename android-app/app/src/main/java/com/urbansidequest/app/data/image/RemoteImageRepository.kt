package com.urbansidequest.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.urbansidequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object RemoteImageRepository {

    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheSizeKilobytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount.toLong() / BYTES_PER_KILOBYTE)
                .coerceAtLeast(MINIMUM_CACHE_ENTRY_SIZE_KILOBYTES)
                .toInt()
        }
    }

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
        memoryCache.get(resolvedUrl)?.let { return@withContext it }
        runCatching {
            val connection = URL(resolvedUrl).openConnection().apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
            }
            connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull()?.also { bitmap ->
            memoryCache.put(resolvedUrl, bitmap)
        }
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

    private fun memoryCacheSizeKilobytes(): Int {
        return (Runtime.getRuntime().maxMemory() / MEMORY_CACHE_DIVISOR / BYTES_PER_KILOBYTE)
            .toInt()
            .coerceAtLeast(MINIMUM_CACHE_SIZE_KILOBYTES)
    }

    private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 6_000
    private const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
    private const val MEMORY_CACHE_DIVISOR = 8L
    private const val BYTES_PER_KILOBYTE = 1_024L
    private const val MINIMUM_CACHE_SIZE_KILOBYTES = 1
    private const val MINIMUM_CACHE_ENTRY_SIZE_KILOBYTES = 1L
}
