package com.example.overdex.battle.audio

/**
 * Brief volatile catch-up for a species first recognized after its entrance cry.
 * Ten original-PCM windows, spaced at least one second apart; never a whole-match recorder.
 * Windows are independently contiguous (CryAudioWindow owns capture-gap resets).
 */
internal class CryRecentWindows {
    private val windows = ArrayDeque<CryAudioWindowSnapshot>()
    @Synchronized fun remember(window: CryAudioWindowSnapshot) {
        val last = windows.lastOrNull()
        if (last != null && (last.generation != window.generation || last.captureSource != window.captureSource ||
                window.endNanos < last.endNanos)) windows.clear()
        while (windows.firstOrNull()?.let { window.endNanos - it.endNanos > MAX_AGE_NANOS } == true) windows.removeFirst()
        if (windows.lastOrNull()?.let { window.endNanos - it.endNanos < 1_000_000_000L } == true) return
        windows.addLast(window)
        while (windows.size > MAX_WINDOWS) windows.removeFirst()
    }
    @Synchronized fun preceding(current: CryAudioWindowSnapshot): List<CryAudioWindowSnapshot> = windows.filter {
        it.generation == current.generation && it.captureSource == current.captureSource &&
            it.endNanos < current.endNanos && current.endNanos - it.endNanos <= MAX_AGE_NANOS
    }
    companion object {
        const val MAX_WINDOWS = 10
        const val MAX_AGE_NANOS = 12_000_000_000L
    }
}
