package com.urbansidequest.app.data.route

import android.content.Context
import android.util.Base64

/** 进行中路线的站点进度；服务端尚未提供逐站进度接口时用于恢复本机任务。 */
internal class RouteExecutionProgressStore(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(key: RouteExecutionProgressKey): RouteExecutionProgress {
        return RouteExecutionProgress(
            completedStopIds = sharedPreferences.getStringSet(preferenceKey(key, KEY_COMPLETED_STOP_IDS), emptySet())
                .orEmpty()
                .toSet(),
            skippedStopIds = sharedPreferences.getStringSet(preferenceKey(key, KEY_SKIPPED_STOP_IDS), emptySet())
                .orEmpty()
                .toSet()
        )
    }

    fun save(key: RouteExecutionProgressKey, progress: RouteExecutionProgress) {
        sharedPreferences.edit()
            .putStringSet(preferenceKey(key, KEY_COMPLETED_STOP_IDS), progress.completedStopIds)
            .putStringSet(preferenceKey(key, KEY_SKIPPED_STOP_IDS), progress.skippedStopIds)
            .apply()
    }

    fun clear(key: RouteExecutionProgressKey) {
        sharedPreferences.edit()
            .remove(preferenceKey(key, KEY_COMPLETED_STOP_IDS))
            .remove(preferenceKey(key, KEY_SKIPPED_STOP_IDS))
            .apply()
    }

    private fun preferenceKey(key: RouteExecutionProgressKey, field: String): String {
        val identity = "${key.requestId}:${key.routeCode}"
        val encodedIdentity = Base64.encodeToString(
            identity.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$PROGRESS_KEY_PREFIX${encodedIdentity}_$field"
    }

    private companion object {
        private const val PREFERENCES_NAME = "route_execution_progress"
        private const val PROGRESS_KEY_PREFIX = "progress_"
        private const val KEY_COMPLETED_STOP_IDS = "completed_stop_ids"
        private const val KEY_SKIPPED_STOP_IDS = "skipped_stop_ids"
    }
}

internal data class RouteExecutionProgressKey(
    val requestId: String,
    val routeCode: String
)

internal data class RouteExecutionProgress(
    val completedStopIds: Set<String> = emptySet(),
    val skippedStopIds: Set<String> = emptySet()
)
