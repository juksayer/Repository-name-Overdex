package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class FastMoveListeningGateTest {
    @Test fun `VS opens listening through waiting and countdown but respects source time reset and result`() {
        val gate = FastMoveListeningGate()
        gate.observe(VsScreenWitnessed, 100, "vs")
        assertFalse(gate.allows(99))
        assertTrue(gate.accepts(100, 120_000_000_000, gate.generation))
        gate.observe(CountdownGlyphWitnessed("3", .99f, 1), 120_000_000_000, "three")
        assertEquals(listOf("vs"), gate.triggerIds())
        gate.observe(MatchEnded(BattleResult.WIN), 130_000_000_000)
        gate.observe(VsScreenWitnessed, 140_000_000_000, "after-result")
        assertFalse(gate.allows(140_000_000_000))
        gate.observe(ObservationRestartedByUser, 150_000_000_000)
        gate.observe(VsScreenWitnessed, 100, "delayed-old-vs")
        assertFalse(gate.allows(150_000_000_001))
        gate.observe(VsScreenWitnessed, 160_000_000_000, "new-vs")
        assertTrue(gate.allows(160_000_000_000))
        assertEquals(listOf("new-vs"), gate.triggerIds())
    }

    @Test fun `October 28 missed countdown opens audio on first persistent loss after both active species`() {
        val gate = FastMoveListeningGate()
        // Original source ordering from Moondrop bba80cf7. The first charge cue
        // arrived at +404.029 seconds, almost 99 seconds after this real damage.
        gate.observe(ObservationRestartedByUser, 64_733_000_000, "restart")
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Aegislash"),
            300_220_000_000, "player-species")
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Swampert"),
            301_385_000_000, "opponent-species")
        assertFalse(gate.allows(305_260_999_999))
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT,
            .98941797f, .96825397f, .021164f), 305_261_000_000, "first-damage")
        assertFalse(gate.allows(305_260_999_999))
        assertTrue(gate.accepts(305_261_000_000, 306_461_000_000, gate.generation))
        assertEquals(listOf("player-species", "opponent-species", "first-damage"), gate.triggerIds())
        gate.observe(GetReadyWitnessed, 404_029_000_000, "later-qte")
        assertEquals(listOf("player-species", "opponent-species", "first-damage"), gate.triggerIds())
    }

    @Test fun `names full bars malformed losses and flashes cannot open audio`() {
        val gate = FastMoveListeningGate()
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Aegislash"), 100)
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), 110)
        assertFalse("One named side is insufficient battle context", gate.allows(110))
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Swampert"), 120)
        assertFalse("A later name cannot validate earlier damage", gate.allows(120))
        listOf(
            ActiveHpBarMeasured(ActivePokemonSide.OPPONENT, 10, 10, 110, 20, 1f),
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .5f),
            HpBarBorderPulse(ActivePokemonSide.OPPONENT, ApertureStatus.PRESENT, 100f, 10),
            PlayerChargeMoveEnergyFillIncreased(listOf(.2f, .2f), listOf(.3f, .2f), listOf(0)),
        ).forEach { payload ->
            gate.observe(payload, 130)
            assertFalse("$payload", gate.allows(130))
        }
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, .9f, .8f, .1f), 140)
        assertTrue(gate.allows(140))
    }

    @Test fun `delayed species establishes combat only when its source time precedes the loss`() {
        val gate = FastMoveListeningGate()
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Aegislash"), 100, "player")
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), 200, "damage")
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), 120, "older-loss-delivered-later")
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Swampert"), 300, "later-name")
        assertFalse(gate.allows(300))
        gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Swampert"), 150, "delayed-name")
        assertTrue(gate.allows(200))
        assertFalse(gate.allows(199))
        assertEquals(listOf("player", "delayed-name", "damage"), gate.triggerIds())
    }

    @Test fun `old combat cannot reopen a restart and damage cannot reopen result stop or fifth faint`() {
        val gate = FastMoveListeningGate()
        fun combat(at: Long) {
            gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Aegislash"), at)
            gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Swampert"), at)
            gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), at + 1)
        }
        combat(100)
        val oldGeneration = gate.generation
        gate.observe(ObservationRestartedByUser, 300)
        combat(100)
        gate.observe(CountdownGlyphWitnessed("GO", .99f, 1), 200)
        gate.observe(ObservationRestartedByUser, 250)
        assertFalse(gate.accepts(101, 200, oldGeneration))
        assertFalse(gate.allows(301))
        // Current damage also needs current-attempt species, not stale names.
        gate.observe(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f), 350)
        assertFalse(gate.allows(350))
        combat(400)
        assertTrue(gate.allows(401))
        gate.observe(MatchEnded(BattleResult.WIN), 500)
        combat(600)
        assertFalse(gate.allows(601))
        gate.observe(ObservationRestartedByUser, 700)
        combat(800)
        gate.observe(ObservationSessionStoppedByUser, 900)
        combat(1000)
        assertFalse(gate.allows(1001))
        gate.observe(ObservationRestartedByUser, 1100)
        combat(1200)
        repeat(5) { index ->
            val side = if (index % 2 == 0) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER
            gate.observe(ActivePokemonSpeciesWitnessed(side, "Pokemon$index"), 1300L + index * 10)
            gate.observe(ActivePokemonFaintedWitnessed(side, "Pokemon$index"), 1301L + index * 10)
        }
        combat(1500)
        assertFalse(gate.allows(1501))
    }

    @Test fun `late charged phase opens listening at its witnessed time without a countdown`() {
        for (cue in listOf(AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced)) {
            val gate = FastMoveListeningGate()
            gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Dragonair"), 50, "species")
            assertFalse("A species alone can be visible before battle", gate.allows(99))
            gate.observe(cue, 100, "late-phase")
            assertFalse(gate.allows(99))
            assertTrue(gate.accepts(100, 200, gate.generation))
            assertEquals(listOf("late-phase"), gate.triggerIds())
            gate.observe(MatchEnded(BattleResult.WIN), 300)
            gate.observe(cue, 400, "late-after-result")
            assertFalse(gate.allows(400))
            gate.observe(ObservationRestartedByUser, 500)
            assertFalse(gate.allows(501))
            gate.observe(cue, 600)
            assertTrue(gate.allows(600))
            gate.observe(ObservationSessionStoppedByUser, 700)
            gate.observe(cue, 800)
            assertFalse(gate.allows(800))
        }
    }

    @Test fun `any countdown glyph opens audio without requiring GO a species or a visual hit`() {
        for (glyph in listOf("3", "2", "1", "GO")) {
            val gate = FastMoveListeningGate()
            assertFalse(gate.allows(100))
            gate.observe(CountdownGlyphWitnessed(glyph, .99f, 1), 100, "glyph")
            assertFalse(gate.allows(99))
            assertTrue(gate.allows(100))
            gate.observe(GetReadyWitnessed, 200)
            gate.observe(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt"), 300)
            assertTrue(gate.allows(400))
            assertEquals(listOf("glyph"), gate.triggerIds())
        }
    }

    @Test fun `fifth distinct faint closes audio and duplicate faint evidence does not count twice`() {
        val gate = FastMoveListeningGate()
        gate.observe(CountdownGlyphWitnessed("2", .99f, 1), 100)
        val sides = listOf(ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER,
            ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT)
        sides.forEachIndexed { index, side ->
            val name = "Pokemon$index"
            val at = 200L + index * 100
            gate.observe(ActivePokemonSpeciesWitnessed(side, name), at - 1)
            gate.observe(ActivePokemonFaintedWitnessed(side, name), at)
            gate.observe(ActivePokemonFaintedWitnessed(side, name), at + 1)
            gate.observe(ActivePokemonFaintedWitnessed(side), at + 2)
            assertEquals(index < 4, gate.allows(at + 3))
        }
        assertTrue(gate.accepts(400, 500, gate.generation))
        assertFalse(gate.accepts(550, 650, gate.generation))
        gate.observe(CountdownGlyphWitnessed("GO", .99f, 2), 700)
        assertFalse(gate.allows(800))
    }

    @Test fun `result can finish before five faints and manual restart invalidates queued work`() {
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, 100)
        gate.observe(MatchEnded(BattleResult.WIN), 400)
        assertTrue(gate.accepts(100, 300, gate.generation))
        assertFalse(gate.allows(500))
        val oldGeneration = gate.generation
        gate.observe(ObservationRestartedByUser, 600)
        assertFalse(gate.accepts(100, 300, oldGeneration))
        gate.observe(CountdownGlyphWitnessed("1", .99f, 1), 700)
        assertTrue(gate.allows(800))
        gate.observe(ScreenIgnoredByUser, 900)
        assertFalse(gate.allows(1000))
        gate.observe(MatchStarted, 1100)
        assertFalse(gate.allows(1200))
    }
}
