package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** Cheap display gating only. This neither reads resource counts nor identifies species. */
internal object TeamBadgePresenceDetector {
    fun present(width: Int, height: Int, region: AnchorRegion, pixel: (Int, Int) -> Int): Boolean {
        if (width < 1 || height < 1 || region.width <= 0 || region.height <= 0) return false
        var pale = 0; var nameInk = 0; var lowerResources = 0
        for (row in 0 until 12) for (col in 0 until 40) {
            val x = ((region.x + region.width * (col + .5f) / 40) * width).toInt().coerceIn(0, width - 1)
            val y = ((region.y + region.height * (row + .5f) / 12) * height).toInt().coerceIn(0, height - 1)
            val color = pixel(x, y)
            val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
            if (r >= 190 && g >= 205 && b >= 205) pale++
            // Native names/CP are above the resource row. Scattered scene colors
            // or the top edge of a card cannot stand in for that arrangement.
            if (row in 2..5 && r < 150 && g in 50..190 && b in 50..190 && g > r + 10) nameInk++
            if (row in 6..10 && (r > 150 && r > g * 1.35 && r > b * 1.2 ||
                r > 120 && b > 120 && r > g * 1.1 && b > g * 1.1)) lowerResources++
        }
        return pale >= 190 && nameInk >= 3 && lowerResources >= 2
    }
}
