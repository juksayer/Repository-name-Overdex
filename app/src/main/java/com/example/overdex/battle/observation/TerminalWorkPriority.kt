package com.example.overdex.battle.observation

/** Short capture scheduling only: this never establishes a hit, faint or result. */
internal class TerminalWorkPriority(private val now: () -> Long = System::nanoTime) {
    private var until = Long.MIN_VALUE
    private var cooldownUntil = Long.MIN_VALUE

    @Synchronized fun request(sourceAt: Long): Boolean {
        val current = now()
        if (current - sourceAt !in 0..MAX_CUE_AGE || current < until || current < cooldownUntil) return false
        until = sourceAt + WINDOW
        cooldownUntil = until + COOLDOWN
        return true
    }

    @Synchronized fun isActive(): Boolean = now() < until

    companion object {
        const val WINDOW = 3_000_000_000L
        const val COOLDOWN = 1_000_000_000L
        const val MAX_CUE_AGE = 500_000_000L
    }
}
