package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballNotificationStateTest {
    @Test fun stoppedCaptureOffersStartAndReturnWithoutKeepingAServiceAlive() {
        assertEquals(listOf(DroidballNotificationControl.START, DroidballNotificationControl.OVERDEX),
            droidballNotificationControls(sessionActive = false, registrationMode = false, matchId = null))
    }

    @Test fun closedEyesDuringAnOpenSessionStillOfferStopAndNextMatch() {
        assertEquals(DroidballNotificationState.IDLE, state(visible = true))
        assertEquals(listOf(DroidballNotificationControl.STOP, DroidballNotificationControl.NEXT_MATCH,
            DroidballNotificationControl.OVERDEX),
            droidballNotificationControls(sessionActive = true, registrationMode = false, matchId = "match-1"))
    }

    @Test fun registrationAndStartingWithoutAMatchDoNotOfferNextMatch() {
        val expected = listOf(DroidballNotificationControl.STOP, DroidballNotificationControl.OVERDEX)
        assertEquals(expected, droidballNotificationControls(true, true, "old-match"))
        assertEquals(expected, droidballNotificationControls(true, false, null))
    }

    @Test fun oldNotificationCannotStopANewerSessionOrAdvanceANewerMatch() {
        assertEquals(true, droidballNotificationTargetsCurrentSession("session-1", "session-1", "match-1", "match-1"))
        assertEquals(false, droidballNotificationTargetsCurrentSession("session-1", "session-2", "match-1", "match-1"))
        assertEquals(false, droidballNotificationTargetsCurrentSession("session-1", "session-1", "match-1", "match-2"))
        assertEquals(false, droidballNotificationTargetsCurrentSession(null, "session-1", null, null))
        assertEquals(true, droidballNotificationTargetsCurrentSession("session-1", "session-1", null, null))
    }

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
