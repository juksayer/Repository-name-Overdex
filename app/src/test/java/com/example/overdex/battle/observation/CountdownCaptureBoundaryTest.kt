package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownCaptureBoundaryTest {
    @Test fun `opening species and HP keep countdown enabled but combat retires it`() {
        assertFalse(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Abomasnow", 460).closesCountdownCapture())
        assertFalse(ActiveHpBarMeasured(ActivePokemonSide.PLAYER, 10, 20, 210, 35, 1f).closesCountdownCapture())
        assertTrue(FastMoveUseObserved("hit", ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT,
            "aboma", "Abomasnow", listOf("HP_DAMAGE_TICK")).closesCountdownCapture())
        assertTrue(GetReadyWitnessed.closesCountdownCapture())
        assertTrue(AttackIncoming.closesCountdownCapture())
        assertTrue(ChargeMoveUsedAnnounced.closesCountdownCapture())
    }
}
