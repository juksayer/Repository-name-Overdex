package com.example.overdex.battle.observation

import android.content.Context
import android.os.Build

/** User-adjusted field HUD geometry for one phone model and display size. */
data class BattleHudLayout(
    val windowX: Int? = null,
    val windowY: Int? = null,
    val topHalfOffsetX: Float = 0f,
    val topHalfOffsetY: Float = 0f,
    val bottomHalfOffsetX: Float = 0f,
    val bottomHalfOffsetY: Float = 0f,
)

/**
 * Keeps live test-match placement app-private and stable across app restarts
 * and build updates that preserve application data.
 * Display geometry is part of the key so a layout tuned on one phone cannot
 * silently move the HUD off-screen on another device.
 */
class BattleHudLayoutStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(displayWidth: Int, displayHeight: Int): BattleHudLayout {
        val prefix = prefix(displayWidth, displayHeight)
        return BattleHudLayout(
            windowX = preferences.intOrNull("${prefix}_window_x"),
            windowY = preferences.intOrNull("${prefix}_window_y"),
            topHalfOffsetX = preferences.getFloat("${prefix}_top_x", 0f),
            topHalfOffsetY = preferences.getFloat("${prefix}_top_y", 0f),
            bottomHalfOffsetX = preferences.getFloat("${prefix}_bottom_x", 0f),
            bottomHalfOffsetY = preferences.getFloat("${prefix}_bottom_y", 0f),
        )
    }

    fun saveWindow(displayWidth: Int, displayHeight: Int, x: Int, y: Int) {
        val prefix = prefix(displayWidth, displayHeight)
        preferences.edit()
            .putInt("${prefix}_window_x", x)
            .putInt("${prefix}_window_y", y)
            .commit()
    }

    fun saveHalves(
        displayWidth: Int,
        displayHeight: Int,
        topHalfOffsetX: Float,
        topHalfOffsetY: Float,
        bottomHalfOffsetX: Float,
        bottomHalfOffsetY: Float,
    ) {
        val prefix = prefix(displayWidth, displayHeight)
        preferences.edit()
            .putFloat("${prefix}_top_x", topHalfOffsetX)
            .putFloat("${prefix}_top_y", topHalfOffsetY)
            .putFloat("${prefix}_bottom_x", bottomHalfOffsetX)
            .putFloat("${prefix}_bottom_y", bottomHalfOffsetY)
            .commit()
    }

    fun reset(displayWidth: Int, displayHeight: Int) {
        val prefix = prefix(displayWidth, displayHeight)
        preferences.edit()
            .remove("${prefix}_window_x")
            .remove("${prefix}_window_y")
            .remove("${prefix}_top_x")
            .remove("${prefix}_top_y")
            .remove("${prefix}_bottom_x")
            .remove("${prefix}_bottom_y")
            .commit()
    }

    private fun prefix(displayWidth: Int, displayHeight: Int): String =
        "${Build.MODEL}_${displayWidth.coerceAtLeast(1)}x${displayHeight.coerceAtLeast(1)}"

    private fun android.content.SharedPreferences.intOrNull(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private companion object {
        const val PREFERENCES = "battle_hud_layout"
    }
}
