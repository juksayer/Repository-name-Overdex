package com.example.overdex

import android.content.Context
import android.content.SharedPreferences

class ResearcherManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "researcher_prefs"
        private const val KEY_UNLOCKED = "researcher_mode_unlocked"
    }

    fun isUnlocked(): Boolean {
        return prefs.getBoolean(KEY_UNLOCKED, false)
    }

    fun setUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_UNLOCKED, unlocked).apply()
    }
}
