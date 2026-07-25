package com.example.fishy.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short haptic for validation / blocking errors. */
object ErrorFeedback {
    fun vibrate(context: Context) {
        vibrateOneShot(context, durationMs = 50)
    }

    /** Stronger haptic for checklist reminders during an active shipment. */
    fun vibrateStrong(context: Context) {
        vibrateOneShot(context, durationMs = 350)
    }

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
