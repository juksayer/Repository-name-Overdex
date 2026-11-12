package com.example.overdex.ui.ODXFi

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBackPressTrackerTest {
    private val timeoutMillis = 500L

    @Test
    fun shortPressReturnsOnlyAfterRelease() {
        val tracker = RemoteBackPressTracker(timeoutMillis)

        assertEquals(
            RemoteBackAction.CONSUME,
            tracker.onDown(eventTimeMillis = 1_000L, downTimeMillis = 1_000L, repeatCount = 0, isLongPress = false),
        )
        assertEquals(
            RemoteBackAction.RETURN,
            tracker.onUp(eventTimeMillis = 1_200L, downTimeMillis = 1_000L),
        )
    }

    @Test
    fun heldReleaseRestoresNavigationWithoutReturning() {
        val tracker = RemoteBackPressTracker(timeoutMillis)
        tracker.onDown(eventTimeMillis = 2_000L, downTimeMillis = 2_000L, repeatCount = 0, isLongPress = false)

        assertEquals(
            RemoteBackAction.RESTORE_NAVIGATION,
            tracker.onUp(eventTimeMillis = 2_600L, downTimeMillis = 2_000L),
        )
    }

    @Test
    fun longPressRepeatRestoresNavigationOnlyOnce() {
        val tracker = RemoteBackPressTracker(timeoutMillis)
        tracker.onDown(eventTimeMillis = 3_000L, downTimeMillis = 3_000L, repeatCount = 0, isLongPress = false)

        assertEquals(
            RemoteBackAction.RESTORE_NAVIGATION,
            tracker.onDown(eventTimeMillis = 3_500L, downTimeMillis = 3_000L, repeatCount = 1, isLongPress = true),
        )
        assertEquals(
            RemoteBackAction.CONSUME,
            tracker.onDown(eventTimeMillis = 3_600L, downTimeMillis = 3_000L, repeatCount = 2, isLongPress = true),
        )
        assertEquals(
            RemoteBackAction.CONSUME,
            tracker.onUp(eventTimeMillis = 3_700L, downTimeMillis = 3_000L),
        )
    }

    @Test
    fun cancelledPressDoesNotAffectNextPress() {
        val tracker = RemoteBackPressTracker(timeoutMillis)
        tracker.onDown(eventTimeMillis = 4_000L, downTimeMillis = 4_000L, repeatCount = 0, isLongPress = false)
        tracker.cancel()

        tracker.onDown(eventTimeMillis = 5_000L, downTimeMillis = 5_000L, repeatCount = 0, isLongPress = false)
        assertEquals(
            RemoteBackAction.RETURN,
            tracker.onUp(eventTimeMillis = 5_100L, downTimeMillis = 5_000L),
        )
    }
}
