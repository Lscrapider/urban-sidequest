package com.urbansidequest.app.feature.profile

import android.content.Context
import android.util.Base64
import java.time.LocalDate
import androidx.core.content.edit

/** 按登录用户保存连续探索天数，避免应用重启后丢失已获得的进度。 */
internal class ExplorationStreakStore(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(userId: String): ExplorationStreakProgress {
        val prefix = keyPrefix(userId)
        val streakDays = sharedPreferences.getInt("${prefix}_$KEY_STREAK_DAYS", 0)
        val lastVisitEpochDay = sharedPreferences.getLong(
            "${prefix}_$KEY_LAST_VISIT_EPOCH_DAY",
            NO_SAVED_EPOCH_DAY
        )
        return ExplorationStreakProgress(
            streakDays = streakDays.coerceAtLeast(0),
            lastVisitDate = lastVisitEpochDay
                .takeIf { it != NO_SAVED_EPOCH_DAY }
                ?.let(LocalDate::ofEpochDay)
        )
    }

    fun save(userId: String, streakDays: Int, lastVisitDate: LocalDate) {
        val prefix = keyPrefix(userId)
        sharedPreferences.edit {
            putInt("${prefix}_$KEY_STREAK_DAYS", streakDays.coerceAtLeast(0))
                .putLong("${prefix}_$KEY_LAST_VISIT_EPOCH_DAY", lastVisitDate.toEpochDay())
        }
    }

    private fun keyPrefix(userId: String): String {
        val encodedUserId = Base64.encodeToString(
            userId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$KEY_PREFIX$encodedUserId"
    }

    private companion object {
        private const val PREFERENCES_NAME = "exploration_streak_store"
        private const val KEY_PREFIX = "user_"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_VISIT_EPOCH_DAY = "last_visit_epoch_day"
        private const val NO_SAVED_EPOCH_DAY = Long.MIN_VALUE
    }
}

internal data class ExplorationStreakProgress(
    val streakDays: Int,
    val lastVisitDate: LocalDate?
)
