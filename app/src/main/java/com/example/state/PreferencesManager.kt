package com.example.state

import android.content.Context

/**
 * Lightweight preferences manager for persisting user setup state across app launches.
 */
object PreferencesManager {
    private const val PREFS_NAME = "social_timer_prefs"
    private const val KEY_OVERLAY_SETUP_COMPLETED = "overlay_setup_completed"

    fun isOverlaySetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OVERLAY_SETUP_COMPLETED, false)
    }

    fun setOverlaySetupCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OVERLAY_SETUP_COMPLETED, completed).apply()
    }
}
