package com.example.overdex.battle.observation

/** A confirmed party frame is not permission to classify later VS or battlefield scenery.
 * Compare capture times, so slow OCR may still publish valid earlier evidence.
 */
internal class TeamSelectRosterWindow {
    private var confirmedAt: Long? = null
    private var closedAt: Long? = null
    fun confirm(at: Long) { confirmedAt = at }
    fun close(at: Long) { closedAt = minOf(closedAt ?: at, at) }
    fun allows(at: Long): Boolean {
        val confirmed = confirmedAt ?: return false
        return at >= confirmed - TeamSelectSurfaceContract.AGREEMENT_WINDOW_NANOS &&
            at <= confirmed + TeamSelectSurfaceContract.AGREEMENT_WINDOW_NANOS &&
            (closedAt == null || at < closedAt!!)
    }
}
