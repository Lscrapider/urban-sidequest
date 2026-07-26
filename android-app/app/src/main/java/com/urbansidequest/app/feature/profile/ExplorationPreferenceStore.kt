package com.urbansidequest.app.feature.profile

import android.content.Context
import android.util.Base64

/** 按登录用户隔离保存探索问卷，避免切换账号时串用画像。 */
internal class ExplorationPreferenceStore(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(userId: String): ExplorationPreferenceAnswers? {
        val prefix = keyPrefix(userId)
        val hasSavedAnswers = sharedPreferences.getBoolean("${prefix}_$KEY_HAS_SAVED_ANSWERS", false)
        if (!hasSavedAnswers) {
            return null
        }
        return ExplorationPreferenceAnswers(
            interestCodes = sharedPreferences.getStringSet("${prefix}_$KEY_INTEREST_CODES", emptySet()).orEmpty(),
            distanceSensitivityCode = sharedPreferences.getString("${prefix}_$KEY_DISTANCE", null),
            budgetSensitivityCode = sharedPreferences.getString("${prefix}_$KEY_BUDGET", null),
            transferSensitivityCode = sharedPreferences.getString("${prefix}_$KEY_TRANSFER", null),
            hiddenGemCode = sharedPreferences.getString("${prefix}_$KEY_HIDDEN_GEM", null)
        )
    }

    fun save(userId: String, answers: ExplorationPreferenceAnswers) {
        val prefix = keyPrefix(userId)
        sharedPreferences.edit()
            .putBoolean("${prefix}_$KEY_HAS_SAVED_ANSWERS", true)
            .putStringSet("${prefix}_$KEY_INTEREST_CODES", answers.interestCodes)
            .putString("${prefix}_$KEY_DISTANCE", answers.distanceSensitivityCode)
            .putString("${prefix}_$KEY_BUDGET", answers.budgetSensitivityCode)
            .putString("${prefix}_$KEY_TRANSFER", answers.transferSensitivityCode)
            .putString("${prefix}_$KEY_HIDDEN_GEM", answers.hiddenGemCode)
            .apply()
    }

    private fun keyPrefix(userId: String): String {
        val encodedUserId = Base64.encodeToString(
            userId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$KEY_PREFIX$encodedUserId"
    }

    private companion object {
        private const val PREFERENCES_NAME = "exploration_preference_store"
        private const val KEY_PREFIX = "user_"
        private const val KEY_HAS_SAVED_ANSWERS = "has_saved_answers"
        private const val KEY_INTEREST_CODES = "interest_codes"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_BUDGET = "budget"
        private const val KEY_TRANSFER = "transfer"
        private const val KEY_HIDDEN_GEM = "hidden_gem"
    }
}
