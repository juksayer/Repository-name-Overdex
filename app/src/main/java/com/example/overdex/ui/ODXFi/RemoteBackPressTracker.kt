package com.example.overdex.ui.ODXFi

/** Distinguishes an ordinary Back press from a hold that restores shell navigation focus. */
internal class RemoteBackPressTracker(
    private val longPressTimeoutMillis: Long,
) {
    private var navigationRestored = false

    fun onDown(
        eventTimeMillis: Long,
        downTimeMillis: Long,
        repeatCount: Int,
        isLongPress: Boolean,
    ): RemoteBackAction {
        if (repeatCount == 0) navigationRestored = false
        val heldLongEnough = eventTimeMillis - downTimeMillis >= longPressTimeoutMillis
        return if (!navigationRestored && (isLongPress || heldLongEnough)) {
            navigationRestored = true
            RemoteBackAction.RESTORE_NAVIGATION
        } else {
            RemoteBackAction.CONSUME
        }
    }

    fun onUp(eventTimeMillis: Long, downTimeMillis: Long): RemoteBackAction {
        val wasRestored = navigationRestored
        val heldLongEnough = eventTimeMillis - downTimeMillis >= longPressTimeoutMillis
        navigationRestored = false
        return when {
            wasRestored -> RemoteBackAction.CONSUME
            heldLongEnough -> RemoteBackAction.RESTORE_NAVIGATION
            else -> RemoteBackAction.RETURN
        }
    }

    fun cancel() {
        navigationRestored = false
    }
}

internal enum class RemoteBackAction {
    CONSUME,
    RETURN,
    RESTORE_NAVIGATION,
}
