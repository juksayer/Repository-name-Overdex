package com.example.overdex.battle.observation

/**
 * Tracks only the obligation created by requesting a foreground-service start.
 * Capture runtime remains owned by DroidballService. Android must receive
 * startForeground before a queued Stop is allowed to destroy that service.
 */
internal class DroidballForegroundStartupGate {
    enum class StopAction { NONE, DEFER, STOP_NOW }

    private var serviceRequested = false
    private var promotionPending = false
    private var stopRequested = false

    /** A duplicate request cannot discard a Stop already queued for this startup. */
    @Synchronized fun beginStart(): Boolean {
        if (serviceRequested) return false
        serviceRequested = true
        promotionPending = true
        stopRequested = false
        return true
    }

    @Synchronized fun requestStop(): StopAction {
        if (!serviceRequested) return StopAction.NONE
        if (!promotionPending) return StopAction.STOP_NOW
        stopRequested = true
        return StopAction.DEFER
    }

    /**
     * Call only after startForeground succeeds. True permits capture setup;
     * false means stopSelf now, without starting projection, audio or windows.
     */
    @Synchronized fun foregroundPromoted(): Boolean {
        if (!promotionPending) return false
        promotionPending = false
        return !stopRequested
    }

    /** A failed Android start or completed destruction releases the obligation. */
    @Synchronized fun markStopped() {
        serviceRequested = false
        promotionPending = false
        stopRequested = false
    }
}
