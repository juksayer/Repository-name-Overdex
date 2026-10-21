package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*

/** Only an established Match can contribute result music. A short result grace period
 * lets audio corroborate text that arrived first, even after the live HUD has closed.
 */
internal class ResultMusicListeningGate {
    private var startedAt: Long? = null
    private var endedAt: Long? = null
    private var ignored = false
    private var stopped = false

    @Synchronized fun observe(payload: TestimonyPayload, at: Long?) {
        when (payload) {
            is MatchStarted -> if (at != null && startedAt == null) { startedAt = at; ignored = false }
            is MatchEnded -> if (endedAt == null) endedAt = at
            is ScreenIgnoredByUser -> ignored = true
            is ObservationRestartedByUser -> if (startedAt == null) ignored = false
            is TeamSelectScanStartedByUser -> if (startedAt == null) ignored = false
            is ObservationSessionStoppedByUser -> stopped = true
        }
    }

    @Synchronized fun allows(at: Long, battleSurfaceEnabled: Boolean): Boolean {
        val start = startedAt ?: return false
        if (stopped || ignored || at < start) return false
        val end = endedAt
        return if (end != null) at - end in 0..20_000_000_000L else battleSurfaceEnabled
    }
}
