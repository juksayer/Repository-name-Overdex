package com.example.overdex.battle.replay

/** Navigation only: the full recording remains available for backwards seeking. */
internal fun replayDefaultPosition(start: Long, end: Long, matchStart: Long?, clip: Boolean): Long =
    (if (clip) start else matchStart ?: start).coerceIn(start, end)
