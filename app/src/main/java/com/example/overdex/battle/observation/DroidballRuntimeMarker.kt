package com.example.overdex.battle.observation

import android.content.Context

/**
 * A process-death breadcrumb, not a second runtime state machine.
 *
 * A normal service shutdown clears it. If Android kills the process while
 * capture is active, the next process consumes the marker and can explain why
 * START is being offered instead of STOP.
 */
internal object DroidballRuntimeMarker {
    private const val PREFERENCES = "droidball_runtime"
    private const val CAPTURE_WAS_ACTIVE = "capture_was_active"

    fun markActive(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CAPTURE_WAS_ACTIVE, true)
            .apply()
    }

    fun markStopped(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CAPTURE_WAS_ACTIVE, false)
            .apply()
    }

    fun consumeInterruptedCapture(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val wasActive = preferences.getBoolean(CAPTURE_WAS_ACTIVE, false)
        if (wasActive) {
            preferences.edit().putBoolean(CAPTURE_WAS_ACTIVE, false).apply()
        }
        return wasActive
    }
}
