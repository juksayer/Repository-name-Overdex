package com.example.overdex.battle.observation

/** Lifecycle observations, not battle outcomes or an explicit user Stop action. */
enum class DroidballAutomaticStopReason(val description: String) {
    SCREEN_OFF_AND_LOCKED("Device non-interactive with lock screen showing"),
    MEDIA_PROJECTION_STOPPED("Android stopped screen capture"),
}

/**
 * One controller per service instance, called on the main thread. Android can deliver
 * SCREEN_OFF before Keyguard starts showing, so the caller must recheck while this
 * returns true. Fresh platform state, not the broadcast name, decides whether to stop.
 */
internal class DroidballAutomaticStopController(
    private val stopSession: (DroidballAutomaticStopReason) -> Unit,
) {
    private var closed = false

    /** True requests another check while asleep but not yet showing a lock screen. */
    fun onDeviceState(interactive: Boolean, keyguardShowing: Boolean): Boolean {
        if (closed) return false
        if (!interactive && keyguardShowing) {
            requestStop(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED)
            return false
        }
        return !interactive
    }

    /** A revoked projection must also end observation even when the phone is awake. */
    fun onProjectionStopped(interactive: Boolean, keyguardShowing: Boolean) {
        requestStop(
            if (!interactive && keyguardShowing) DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED
            else DroidballAutomaticStopReason.MEDIA_PROJECTION_STOPPED
        )
    }

    /** Ignore callbacks queued before normal teardown or after this session ended. */
    fun close() {
        closed = true
    }

    private fun requestStop(reason: DroidballAutomaticStopReason) {
        if (closed) return
        closed = true
        stopSession(reason)
    }
}
