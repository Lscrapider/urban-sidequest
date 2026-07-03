package com.urbansidequest.app.ui.components

import android.animation.ValueAnimator

object UrbanMotion {
    const val ClickMillis = 120
    const val SelectionMillis = 180
    const val CardStateMillis = 220
    const val SheetMillis = 250
    const val MapLockScanMillis = 600
    const val QuestBorderPulseMillis = 1600
    const val QuestFlowMillis = 1400
    const val PressedScale = 0.98f
}

fun urbanMotionDuration(defaultMillis: Int): Int {
    return if (ValueAnimator.areAnimatorsEnabled()) defaultMillis else 0
}

fun urbanMotionEnabled(): Boolean {
    return ValueAnimator.areAnimatorsEnabled()
}
