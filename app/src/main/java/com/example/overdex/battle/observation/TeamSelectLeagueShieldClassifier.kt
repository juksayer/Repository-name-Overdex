package com.example.overdex.battle.observation

import android.graphics.Bitmap
import kotlin.math.max

enum class StandardBattleLeague(val timelineName: String) {
    GREAT("GREAT_LEAGUE"),
    ULTRA("ULTRA_LEAGUE"),
    MASTER("MASTER_LEAGUE")
}

data class LeagueShieldClassification(
    val league: StandardBattleLeague,
    val confidence: Float
) {
    val timelineName: String get() = league.timelineName
}

/** Counts the shield's diagonal stripes: Great=1, Ultra=2, Master=3. */
object TeamSelectLeagueShieldClassifier {
    fun classify(bitmap: Bitmap): LeagueShieldClassification? {
        if (bitmap.width < 16 || bitmap.height < 16) return null
        val scanX = (bitmap.width * 0.62f).toInt().coerceIn(0, bitmap.width - 1)
        val baselineStart = (bitmap.height * 0.07f).toInt()
        val baselineEnd = (bitmap.height * 0.15f).toInt().coerceAtLeast(baselineStart + 1)
        val baseline = medianColor((baselineStart until baselineEnd).map { bitmap.getPixel(scanX, it) })
        val scanStart = (bitmap.height * 0.16f).toInt()
        val scanEnd = (bitmap.height * 0.68f).toInt().coerceAtLeast(scanStart + 1)
        val stripeCount = countStripeRuns(
            (scanStart until scanEnd).map { bitmap.getPixel(scanX, it) },
            baseline,
            minimumRun = (bitmap.height * 0.045f).toInt().coerceAtLeast(3)
        )
        val league = when (stripeCount) {
            1 -> StandardBattleLeague.GREAT
            2 -> StandardBattleLeague.ULTRA
            3 -> StandardBattleLeague.MASTER
            else -> return null
        }
        return LeagueShieldClassification(league, 0.98f)
    }

    /** Color-family fallback retained for tests and future limited-cup support. */
    internal fun classifyArgb(colors: Iterable<Int>): LeagueShieldClassification? {
        var blue = 0
        var yellow = 0
        var magenta = 0
        var colorful = 0
        colors.forEach { argb ->
            val r = argb ushr 16 and 0xff
            val g = argb ushr 8 and 0xff
            val b = argb and 0xff
            val spread = max(r, max(g, b)) - minOf(r, g, b)
            if (spread < 35 || max(r, max(g, b)) < 75) return@forEach
            colorful++
            when {
                b > r + 28 && b > g + 12 -> blue++
                r > 145 && g > 110 && b + 45 < minOf(r, g) -> yellow++
                r > 90 && b > 90 && r > g + 30 && b > g + 30 -> magenta++
            }
        }
        if (colorful < 32) return null
        val blueShare = blue.toFloat() / colorful
        val yellowShare = yellow.toFloat() / colorful
        val magentaShare = magenta.toFloat() / colorful
        // Yellow and magenta stripes are distinctive even when the crop's
        // transparent corners expose more blue scenery than shield pixels.
        val league = when {
            magentaShare >= 0.12f -> StandardBattleLeague.MASTER
            yellowShare >= 0.12f -> StandardBattleLeague.ULTRA
            blueShare >= 0.12f -> StandardBattleLeague.GREAT
            else -> return null
        }
        val signatureShare = when (league) {
            StandardBattleLeague.GREAT -> blueShare
            StandardBattleLeague.ULTRA -> yellowShare
            StandardBattleLeague.MASTER -> magentaShare
        }
        return LeagueShieldClassification(league, (0.78f + signatureShare * 0.22f).coerceAtMost(0.98f))
    }

    internal fun countStripeRuns(colors: List<Int>, baseline: Int, minimumRun: Int): Int {
        if (colors.isEmpty()) return 0
        val changed = colors.map { colorDistance(it, baseline) >= 75f }.toMutableList()
        // Close one-pixel antialiasing gaps without joining distinct stripes.
        for (index in 1 until changed.lastIndex) {
            if (!changed[index] && changed[index - 1] && changed[index + 1]) changed[index] = true
        }
        var count = 0
        var runStart = -1
        for (index in 0..changed.size) {
            val active = index < changed.size && changed[index]
            if (active && runStart < 0) runStart = index
            if (!active && runStart >= 0) {
                if (index - runStart >= minimumRun) count++
                runStart = -1
            }
        }
        return count
    }

    private fun medianColor(colors: List<Int>): Int {
        fun channel(shift: Int): Int = colors.map { it ushr shift and 0xff }.sorted()[colors.size / 2]
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun colorDistance(left: Int, right: Int): Float {
        val dr = (left ushr 16 and 0xff) - (right ushr 16 and 0xff)
        val dg = (left ushr 8 and 0xff) - (right ushr 8 and 0xff)
        val db = (left and 0xff) - (right and 0xff)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }
}
