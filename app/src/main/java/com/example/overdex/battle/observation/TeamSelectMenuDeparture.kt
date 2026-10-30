package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.AnchorRegion
import kotlin.math.roundToInt

/** Presentation-only geography check; it never accepts Team Select or starts a Match. */
internal object TeamSelectMenuSurfaceProbe {
    fun visible(bitmap: Bitmap, calibration: TeamSelectCalibration, overlay: AnchorRegion?): Boolean? {
        val cards = listOf(calibration.rosterSlot1, calibration.rosterSlot2, calibration.rosterSlot3)
        // Our menu, a moved editor, or invalid calibration is unavailable input,
        // rather than proof that GO's party screen disappeared.
        if (!unoccluded(calibration, overlay)) return null
        val paleCards = cards.count { cardBackgroundPresent(sample(bitmap, it)) }
        return paleCards >= 2 && partyButtonPresent(sample(bitmap, calibration.useThisParty))
    }

    internal fun unoccluded(calibration: TeamSelectCalibration, overlay: AnchorRegion?): Boolean =
        listOf(calibration.rosterSlot1, calibration.rosterSlot2, calibration.rosterSlot3, calibration.useThisParty)
            .none { !usable(it) || (overlay != null && overlaps(it, overlay)) }

    internal fun cardBackgroundPresent(colors: List<Int>): Boolean = colors.isNotEmpty() &&
        colors.count { color ->
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            minOf(red, green, blue) >= 205 && maxOf(red, green, blue) - minOf(red, green, blue) <= 42
        }.toFloat() / colors.size >= 0.36f

    internal fun partyButtonPresent(colors: List<Int>): Boolean = colors.isNotEmpty() &&
        colors.count { color ->
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            green >= 145 && green > red + 18 && green > blue + 8 && blue >= 80
        }.toFloat() / colors.size >= 0.20f

    private fun usable(region: AnchorRegion): Boolean =
        listOf(region.x, region.y, region.width, region.height).all { it.isFinite() } &&
            region.x >= 0f && region.y >= 0f && region.width > 0f && region.height > 0f &&
            region.x + region.width <= 1.00001f && region.y + region.height <= 1.00001f

    private fun overlaps(a: AnchorRegion, b: AnchorRegion): Boolean =
        a.x < b.x + b.width && b.x < a.x + a.width &&
            a.y < b.y + b.height && b.y < a.y + a.height

    private fun sample(bitmap: Bitmap, region: AnchorRegion): List<Int> {
        val left = (region.x * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (region.y * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = ((region.x + region.width) * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((region.y + region.height) * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        return buildList {
            for (y in top until bottom step ((bottom - top) / 12).coerceAtLeast(1)) {
                for (x in left until right step ((right - left) / 12).coerceAtLeast(1)) add(bitmap.getPixel(x, y))
            }
        }
    }
}

internal data class TeamSelectMenuScope(val matchId: String, val observationAttemptStartedAtNanos: Long)

/** Collapse only after a visible party anchor is followed by sustained fresh-frame loss. */
internal class TeamSelectMenuDepartureMonitor {
    private var scope: TeamSelectMenuScope? = null
    private var lastSampleAt: Long? = null
    private var sawParty = false
    private var firstMissingAt: Long? = null
    private var dismissed = false

    fun shouldSample(currentScope: TeamSelectMenuScope, atNanos: Long): Boolean {
        if (scope != currentScope) {
            scope = currentScope
            lastSampleAt = null
            sawParty = false
            firstMissingAt = null
            dismissed = false
        }
        if (dismissed || atNanos < currentScope.observationAttemptStartedAtNanos) return false
        if (lastSampleAt?.let { atNanos - it < SAMPLE_INTERVAL_NANOS } == true) return false
        lastSampleAt = atNanos
        return true
    }

    fun observe(atNanos: Long, partyVisible: Boolean?): Boolean {
        when (partyVisible) {
            true -> { sawParty = true; firstMissingAt = null }
            null -> firstMissingAt = null
            false -> if (sawParty && !dismissed) {
                val missingAt = firstMissingAt
                if (missingAt == null) firstMissingAt = atNanos
                else if (atNanos - missingAt >= SAMPLE_INTERVAL_NANOS) {
                    dismissed = true
                    return true
                }
            }
        }
        return false
    }

    private companion object { const val SAMPLE_INTERVAL_NANOS = 100_000_000L }
}
