package com.example.overdex.battle.observation

/** Cheap, positive-only visual wake-up for the centered gold GREAT glyphs.
 * The dedicated QTE OCR remains the source of grade testimony.
 */
internal object PlayerQteGradeAttention {
    fun bounds(width: Int, height: Int) = BattleCropBounds(
        width * 28 / 100, height * 24 / 100, width * 72 / 100, height * 34 / 100)

    fun matches(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Boolean {
        val area = bounds(width, height)
        val step = maxOf(1, width / 340)
        var left = area.right
        var right = area.left
        var top = area.bottom
        var bottom = area.top
        for (y in area.top until area.bottom step step) {
            for (x in area.left until area.right step step) {
                if (!gold(pixelAt(x, y))) continue
                left = minOf(left, x)
                right = maxOf(right, x + step)
                top = minOf(top, y)
                bottom = maxOf(bottom, y + step)
            }
        }
        val span = right - left
        val rows = bottom - top
        if (span !in (width * 32 / 100)..(width * 40 / 100) ||
            rows !in (width * 6 / 100)..(width * 10 / 100) ||
            kotlin.math.abs((left + right) - width) > width * .05f) return false

        var differences = 0
        for ((row, pattern) in great.withIndex()) {
            val y = top + (2 * row + 1) * rows / (2 * great.size)
            for (column in pattern.indices) {
                val x = left + (2 * column + 1) * span / (2 * pattern.length)
                if (gold(pixelAt(x, y)) != (pattern[column] == '#')) differences++
            }
        }
        return differences <= 40
    }

    private fun gold(pixel: Int): Boolean {
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        return r >= 180 && g >= 125 && g <= r + 15 && g - b >= 77 && r - b >= 95
    }

    // Positive attention template sampled from the recorded GREAT glyphs.
    private val great = listOf(
        "..###..####...###...##...#####.#",
        ".#.....#..##.#......##.....#...#",
        "#......#...#.#......#.#....#...#",
        "#...##.####..####..#..#....#...#",
        "#....#.#..#..#.....####....#....",
        ".#...#.#..##.##....#...#...#....",
        "..###..#...#.####.#....#...#...#",
    )
}
