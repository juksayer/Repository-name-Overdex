package com.example.overdex.battle.observation

/** A restart can reuse the same Match; it still needs a fresh native badge. */
internal data class RetainedBadgeScope(val matchId: String, val attemptStartedAtNanos: Long)

/** The untouched player badge is the return signal; our opponent copy cannot see itself. */
internal class RetainedBadgeVisibility {
    data class Decision(val cacheOriginal: Boolean = false, val showRetained: Boolean = false,
                        val discardOriginal: Boolean = false)
    private var scope: RetainedBadgeScope? = null
    private var hasOriginal = false
    private var missingSince: Long? = null
    private var showing = false

    fun reset() { scope = null; hasOriginal = false; missingSince = null; showing = false }

    fun update(at: Long, playerPresent: Boolean, opponentPresent: Boolean,
               sourceCovered: Boolean, observationScope: RetainedBadgeScope?, battleSurfaceEstablished: Boolean,
               sourceOverlapsHud: Boolean = false): Decision {
        // Color resemblance, manual HUD opening, and a previous match's badge
        // cannot establish a battle. Never retain scenery while navigating GO.
        val discard = scope != observationScope || !battleSurfaceEstablished || observationScope == null
        if (discard) {
            reset()
            scope = observationScope
        }
        if (!battleSurfaceEstablished || observationScope == null) return Decision(discardOriginal = true)
        return updateVisibleBadge(at, playerPresent, opponentPresent, sourceCovered, sourceOverlapsHud)
            .copy(discardOriginal = discard)
    }

    private fun updateVisibleBadge(at: Long, playerPresent: Boolean, opponentPresent: Boolean,
                                   sourceCovered: Boolean, sourceOverlapsHud: Boolean): Decision {
        if (showing) {
            if (playerPresent) { showing = false; missingSince = null }
            return Decision(showRetained = showing)
        }
        // A hidden window can still be present in an already queued capture.
        if (sourceCovered) return Decision()
        if (playerPresent && opponentPresent) {
            missingSince = null
            // Masking our HUD during detection does not remove it from the
            // bitmap. The entire cached rectangle must be unobstructed.
            if (sourceOverlapsHud) return Decision()
            hasOriginal = true
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
