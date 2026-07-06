package com.urbansidequest.app.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal object ProfileAvatarImageEncoder {

    fun encodeJpeg(context: Context, avatarUri: Uri): ByteArray {
        val bitmap = context.contentResolver.openInputStream(avatarUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalStateException("头像图片解析失败")
        val maxDimension = max(bitmap.width, bitmap.height)
        val avatarBitmap = if (maxDimension > AVATAR_MAX_PIXEL_SIZE) {
            val scale = AVATAR_MAX_PIXEL_SIZE.toFloat() / maxDimension.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { outputStream ->
            avatarBitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, outputStream)
            outputStream.toByteArray()
        }
    }
}

internal const val AVATAR_JPEG_CONTENT_TYPE = "image/jpeg"

private const val AVATAR_MAX_PIXEL_SIZE = 512
private const val AVATAR_JPEG_QUALITY = 86
