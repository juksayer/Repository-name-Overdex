package com.example.overdex.battle.observation

import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballJourneyPhase

/** Current-session phase delivery used by the live composition root, never Replay. */
internal fun presentDroidballSessionPhase(
    session: DroidballSession,
    currentSession: DroidballSession?,
    phase: DroidballSessionPhase,
    captureActive: Boolean,
    shellVisible: Boolean,
): Boolean {
    if (currentSession !== session || phase != session.phase.value) return false
    if (phase == DroidballSessionPhase.BATTLE_ACTIVE && !session.hasCompletedMatch && captureActive) {
        // Session.beginBattle consumes this replaying flow before publishing
        // BATTLE_ACTIVE. Layout hints/countdown alone never authorize opening.
        val start = session.match.matchStarted.replayCache.lastOrNull()
        if (start != null && DroidballOverlayPresentation.showStartedMatch(session.match.matchId)) {
            val journey = DroidballJourney.state.value
            // Leaving the shell without its explicit GO launch leaves the body
            // in transit. Accepted start can complete that handoff even when
            // badge localization missed. Older OCR cannot undo a newer return.
            if (!shellVisible && journey.phase == DroidballJourneyPhase.TRANSIT &&
                (start.monotonicTimeNanos ?: Long.MIN_VALUE) >= journey.startedAtNanos) {
                DroidballJourney.enterField()
            }
        }
    }
    DroidballOverlayPresentation.showSessionPhase(phase)
    return true
}
