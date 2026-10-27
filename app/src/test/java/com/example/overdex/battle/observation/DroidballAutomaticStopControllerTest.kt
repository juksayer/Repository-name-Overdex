package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballAutomaticStopControllerTest {
    @Test fun awakeUnlockedAndAwakeLockscreenDoNotEndObservation() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        // Foreground navigation and an illuminated lockscreen are not sleep.
        repeat(3) { assertFalse(controller.onDeviceState(interactive = true, keyguardShowing = false)) }
        assertFalse(controller.onDeviceState(interactive = true, keyguardShowing = true))
        assertTrue(stops.isEmpty())
    }

    @Test fun screenOffWaitsForDelayedKeyguardInsteadOfStoppingWhileUnlocked() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        repeat(20) { assertTrue(controller.onDeviceState(interactive = false, keyguardShowing = false)) }
        assertTrue(stops.isEmpty())
        assertFalse(controller.onDeviceState(interactive = false, keyguardShowing = true))
        assertEquals(listOf(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED), stops)
    }

    @Test fun wakingBeforeKeyguardSettlesCancelsFurtherChecksWithoutStopping() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        assertTrue(controller.onDeviceState(interactive = false, keyguardShowing = false))
        // Also represents a stale SCREEN_OFF broadcast delivered after wakeup:
        // the controller receives freshly read interactive state, not cached flags.
        assertFalse(controller.onDeviceState(interactive = true, keyguardShowing = true))
        assertFalse(controller.onDeviceState(interactive = true, keyguardShowing = false))
        assertTrue(stops.isEmpty())
    }

    @Test fun alreadyAsleepAtStartupStopsWithoutWaitingForAnotherBroadcast() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        // Keyguard showing includes swipe-only lock; a credential is not required.
        assertFalse(controller.onDeviceState(interactive = false, keyguardShowing = true))
        assertEquals(listOf(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED), stops)
    }

    @Test fun lockAndProjectionCallbacksInEitherOrderStopOnce() {
        for (projectionFirst in listOf(false, true)) {
            val stops = mutableListOf<DroidballAutomaticStopReason>()
            val controller = DroidballAutomaticStopController(stops::add)
            if (projectionFirst) controller.onProjectionStopped(interactive = false, keyguardShowing = true)
            controller.onDeviceState(interactive = false, keyguardShowing = true)
            controller.onProjectionStopped(interactive = false, keyguardShowing = true)
            controller.onDeviceState(interactive = false, keyguardShowing = true)
            assertEquals(listOf(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED), stops)
        }
    }

    @Test fun projectionStoppingBeforeKeyguardIsConfirmedDoesNotClaimALockObservation() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        controller.onProjectionStopped(interactive = false, keyguardShowing = false)
        controller.onDeviceState(interactive = false, keyguardShowing = true)
        assertEquals(listOf(DroidballAutomaticStopReason.MEDIA_PROJECTION_STOPPED), stops)
    }

    @Test fun androidRevokingProjectionWhileAwakeStillEndsTheSession() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val controller = DroidballAutomaticStopController(stops::add)

        controller.onProjectionStopped(interactive = true, keyguardShowing = false)
        assertEquals(listOf(DroidballAutomaticStopReason.MEDIA_PROJECTION_STOPPED), stops)
    }

    @Test fun destroyedServiceIgnoresQueuedCallbacksAndCannotEndANewerSession() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        val oldController = DroidballAutomaticStopController(stops::add)
        oldController.close()
        val currentController = DroidballAutomaticStopController(stops::add)

        assertFalse(oldController.onDeviceState(interactive = false, keyguardShowing = true))
        oldController.onProjectionStopped(interactive = true, keyguardShowing = false)
        assertTrue(stops.isEmpty())
        currentController.onDeviceState(interactive = false, keyguardShowing = true)
        assertEquals(listOf(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED), stops)
    }

    @Test fun synchronousProjectionCallbackDuringStopCannotStopRecursively() {
        val stops = mutableListOf<DroidballAutomaticStopReason>()
        lateinit var controller: DroidballAutomaticStopController
        controller = DroidballAutomaticStopController { reason ->
            stops += reason
            controller.onProjectionStopped(interactive = false, keyguardShowing = true)
        }

        controller.onDeviceState(interactive = false, keyguardShowing = true)
        assertEquals(listOf(DroidballAutomaticStopReason.SCREEN_OFF_AND_LOCKED), stops)
    }
}
