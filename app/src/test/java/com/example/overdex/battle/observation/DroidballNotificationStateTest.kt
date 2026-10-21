package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballNotificationStateTest {
    private fun state(
        runtime: DroidballRuntimeState = DroidballRuntimeState.ACTIVE,
        visible: Boolean = false,
        mode: DroidballOverlayMode = DroidballOverlayMode.BATTLE_HUD,
        registration: Boolean = false,
    ) = droidballNotificationState(runtime, visible, mode, registration)

    @Test fun eyesCloseBeforeCaptureStartsOrAfterItStops() {
        assertEquals(DroidballNotificationState.IDLE, state(runtime = DroidballRuntimeState.STARTING))
        assertEquals(DroidballNotificationState.IDLE, state(runtime = DroidballRuntimeState.STOPPED))
    }

    @Test fun eyesCloseWhileWaitingAndAfterResult() {
        assertEquals(DroidballNotificationState.IDLE, state(mode = DroidballOverlayMode.PRE_BATTLE))
        assertEquals(DroidballNotificationState.IDLE, state(mode = DroidballOverlayMode.RESULT))
    }

    @Test fun eyesOpenWhileScanningCalibratingAndWatchingBattle() {
        listOf(DroidballOverlayMode.SEEKING_TEAM_SELECT, DroidballOverlayMode.TEAM_SELECT,
            DroidballOverlayMode.CALIBRATING, DroidballOverlayMode.BATTLE_HUD,
            DroidballOverlayMode.BATTLE_LIVE).forEach { mode ->
            assertEquals(mode.name, DroidballNotificationState.OBSERVING, state(mode = mode))
        }
    }

    @Test fun returningToOverdexClosesEyesEvenIfBattleHudModeRemains() {
        assertEquals(DroidballNotificationState.IDLE, state(visible = true))
        assertEquals(DroidballNotificationState.OBSERVING, state(visible = false))
    }

    @Test fun registrationDoesNotDependOnLastBattleModeButRespectsPause() {
        assertEquals(DroidballNotificationState.OBSERVING,
            state(mode = DroidballOverlayMode.RESULT, registration = true))
        assertEquals(DroidballNotificationState.IDLE, state(visible = true, registration = true))
        assertEquals(DroidballNotificationState.IDLE,
            state(runtime = DroidballRuntimeState.STARTING, registration = true))
    }
}
