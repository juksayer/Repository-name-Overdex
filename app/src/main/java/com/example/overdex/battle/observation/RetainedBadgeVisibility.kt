package com.example.overdex.battle.observation

/** The untouched player badge is the return signal; our opponent copy cannot see itself. */
internal class RetainedBadgeVisibility {
    data class Decision(val cacheOriginal: Boolean = false, val showRetained: Boolean = false)
    private var hasOriginal = false
    private var missingSince: Long? = null
    private var showing = false

    fun reset() { hasOriginal = false; missingSince = null; showing = false }

    fun update(at: Long, playerPresent: Boolean, opponentPresent: Boolean,
               sourceCovered: Boolean): Decision {
        if (showing) {
            if (playerPresent) { showing = false; missingSince = null }
            return Decision(showRetained = showing)
        }
        // A hidden window can still be present in an already queued capture.
        if (sourceCovered) return Decision()
        if (playerPresent && opponentPresent) {
            hasOriginal = true
            missingSince = null
            return Decision(cacheOriginal = true)
        }
        if (playerPresent || opponentPresent || !hasOriginal) {
            missingSince = null
            return Decision()
        }
        val since = missingSince ?: at.also { missingSince = it }
        showing = at - since >= 100_000_000L
        return Decision(showRetained = showing)
    }
}
