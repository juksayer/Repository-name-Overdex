package com.example.overdex.ui.components

/** National-dex sections; Hisui remains accessible even in older imported region labels. */
enum class RegistrationRegion(val label: String, val dexRange: IntRange) {
    ALL("All", 1..Int.MAX_VALUE),
    KANTO("Kanto", 1..151),
    JOHTO("Johto", 152..251),
    HOENN("Hoenn", 252..386),
    SINNOH("Sinnoh", 387..493),
    UNOVA("Unova", 494..649),
    KALOS("Kalos", 650..721),
    ALOLA("Alola", 722..809),
    GALAR("Galar", 810..898),
    HISUI("Hisui", 899..905),
    PALDEA("Paldea", 906..1025),
}

object SpeciesBrowserNavigation {
    /** Measured from the beginning of the long press, with a bounded repeat rate. */
    fun repeatDelayMillis(heldMillis: Long): Long =
        (240.0 / (1.0 + heldMillis.coerceAtLeast(0) / 700.0)).toLong().coerceAtLeast(40L)

    fun repeatStep(heldMillis: Long): Int = (1 + heldMillis.coerceAtLeast(0) / 3000L).coerceAtMost(8L).toInt()

    /** Keep room in the travel direction, except where the list itself ends. */
    fun firstVisible(
        selected: Int, firstVisible: Int, visibleRows: Int, total: Int, direction: Int, fast: Boolean,
    ): Int {
        if (total == 0 || selected < 0) return 0
        val rows = visibleRows.coerceAtLeast(1)
        val ahead = (if (fast) 3 else 1).coerceAtMost(rows - 1)
        val before = if (direction < 0) ahead else 0
        val after = if (direction > 0) ahead else 0
        val first = when {
            selected < firstVisible + before -> selected - before
            selected > firstVisible + rows - 1 - after -> selected - rows + 1 + after
            else -> firstVisible
        }
        return first.coerceIn(0, (total - rows).coerceAtLeast(0))
    }
}
