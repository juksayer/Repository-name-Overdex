package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide.*
import com.example.overdex.battle.custody.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ActiveTypeSamplingGateTest {
    @Test fun `unresolved badges get three attempts without a periodic recovery probe`() {
        val gate = ActiveTypeSamplingGate()
        val captures = (0..300).map { it * 200_000_000L }.filter { at ->
            gate.accepts(PLAYER, at).also { if (it) gate.captured(PLAYER, at) }
        }
        assertEquals(listOf(0L, 200_000_000L, 400_000_000L), captures)
        assertFalse(gate.accepts(PLAYER, 600_000_000_000L))
    }
    @Test fun `species changes reopen only that side and repeated same-name testimony does not`() {
        val gate = ActiveTypeSamplingGate()
        gate.species(PLAYER, "Pikachu")
        for (side in listOf(PLAYER, OPPONENT)) for (at in listOf(0L, 200_000_000, 400_000_000)) gate.captured(side, at)
        gate.species(PLAYER, "pikachu")
        assertFalse(gate.accepts(PLAYER, 600_000_000))
        gate.species(OPPONENT, "Sealeo")
        assertTrue(gate.accepts(OPPONENT, 600_000_000))
        assertFalse(gate.accepts(PLAYER, 600_000_000))
        gate.trigger()
        assertTrue(gate.accepts(PLAYER, 600_000_000))
    }

    @Test fun `a resolved type closes only its own side until a lifecycle cue`() {
        val gate = ActiveTypeSamplingGate()
        gate.observe(MatchStarted, 1_000_000_000L)
        gate.captured(PLAYER, 1_000_000_000L)
        gate.observe(ActivePokemonTypesWitnessed(PLAYER, listOf(PokemonType.FIRE), .95f), 1_000_000_000L)

        assertFalse(gate.accepts(PLAYER, 60_000_000_000L))
        assertTrue(gate.accepts(OPPONENT, 1_200_000_000L))
        gate.observe(ActivePokemonFaintedWitnessed(PLAYER, "Turtonator"), 61_000_000_000L)
        assertTrue(gate.accepts(PLAYER, 61_000_000_000L))
    }

    @Test fun `charged move and QTE cues rearm resolved types without a timer`() {
        val gate = ActiveTypeSamplingGate()
        val cues = listOf(GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
            ChargeMoveQteVibrationPatternInferred(pulseCount = 1, windowNanos = 0))
        var at = 1_000_000_000L
        for (cue in cues) {
            for (side in listOf(PLAYER, OPPONENT)) {
                gate.resolved(side, at)
                assertFalse(gate.accepts(side, at))
            }
            at += 1_000_000_000L
            gate.observe(cue, at)
            for (side in listOf(PLAYER, OPPONENT)) assertTrue(cue.toString(), gate.accepts(side, at))
        }
    }

    @Test fun `duplicate cue and repeated species cannot reopen a resolved burst`() {
        val gate = ActiveTypeSamplingGate()
        gate.observe(ActivePokemonSpeciesWitnessed(PLAYER, "Turtonator"), 1_000_000_000L)
        gate.resolved(PLAYER, 1_100_000_000L)
        gate.observe(ActivePokemonSpeciesWitnessed(PLAYER, "turtonator"), 2_000_000_000L)
        assertFalse(gate.accepts(PLAYER, 2_000_000_000L))

        gate.observe(GetReadyWitnessed, 3_000_000_000L)
        gate.resolved(PLAYER, 3_100_000_000L)
        gate.observe(GetReadyWitnessed, 3_000_000_000L)
        assertFalse(gate.accepts(PLAYER, 4_000_000_000L))
    }

    @Test fun `late old type and species reads cannot close or reopen a replacement check`() {
        val gate = ActiveTypeSamplingGate()
        gate.observe(ActivePokemonSpeciesWitnessed(OPPONENT, "Golbat"), 1_000_000_000L)
        gate.resolved(OPPONENT, 1_100_000_000L)
        gate.observe(ActivePokemonSpeciesWitnessed(OPPONENT, "Stunfisk"), 2_000_000_000L)
        gate.observe(ActivePokemonTypesWitnessed(OPPONENT, listOf(PokemonType.POISON, PokemonType.FLYING), .95f), 1_500_000_000L)
        assertTrue(gate.accepts(OPPONENT, 2_000_000_000L))
        gate.resolved(OPPONENT, 2_100_000_000L)
        gate.observe(ActivePokemonSpeciesWitnessed(OPPONENT, "Golbat"), 1_500_000_000L)
        assertFalse(gate.accepts(OPPONENT, 3_000_000_000L))
    }

    @Test fun `stop restores an immediate first attempt for a fresh observation session`() {
        val gate = ActiveTypeSamplingGate()
        gate.resolved(PLAYER, 1_000_000_000L)
        gate.stop()
        assertTrue(gate.accepts(PLAYER, 2_000_000_000L))
    }

    @Test fun `queued reads remain eligible after capture budget then retire on resolution or a newer cue`() {
        val gate = ActiveTypeSamplingGate()
        gate.trigger(OPPONENT, 1_000_000_000L)
        for (at in listOf(1_000_000_000L, 1_200_000_000L, 1_400_000_000L)) gate.captured(OPPONENT, at)
        assertFalse(gate.accepts(OPPONENT, 2_000_000_000L))
        assertTrue(gate.needsReading(OPPONENT, 1_400_000_000L))
        gate.resolved(OPPONENT, 1_200_000_000L)
        assertFalse(gate.needsReading(OPPONENT, 1_400_000_000L))
        gate.trigger(OPPONENT, 3_000_000_000L)
        assertFalse(gate.needsReading(OPPONENT, 1_400_000_000L))
        assertTrue(gate.needsReading(OPPONENT, 3_000_000_000L))
    }
}
