package com.example.overdex.battle.replay

/** Start of the playback window, in the original recording clock. Clips use their own bounds. */
internal fun replayDefaultPosition(start: Long, end: Long, matchStart: Long?, clip: Boolean): Long =
    (if (clip) start else matchStart ?: start).coerceIn(start, end)
