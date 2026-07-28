package com.example.fishy.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.fishy.FishyApp

/** Short haptic for validation / blocking errors. */
object ErrorFeedback {
    fun vibrate(context: Context) {
        if (!isUserEnabled()) return
        vibrateOneShot(context, durationMs = 50)
    }

    /**
     * Stronger haptic (checklist reminders, wipe-data).
     * @param ignoreUserSetting when true (wipe dialogs), always vibrates.
     */
    fun vibrateStrong(context: Context, ignoreUserSetting: Boolean = false) {
        if (!ignoreUserSetting && !isUserEnabled()) return
        vibrateOneShot(context, durationMs = 350)
    }

    private fun isUserEnabled(): Boolean =
        runCatching { FishyApp.instance.settingsRepository.vibrationEnabledCached }
            .getOrDefault(true)

    private fun vibrateOneShot(context: Context, durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}
