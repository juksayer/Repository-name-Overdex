package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayEnergyTimingTest {
    private fun event(id: String, seconds: Double, payload: ArchivedTestimonyPayload, predecessors: List<String> = emptyList()) = ArchivedRealityArticle(
        id, "match", (seconds * 1000).toLong(), (seconds * 1000).toLong(), "TEST", payload,
        predecessors, 1f, null, emptyList(), (seconds * 1e9).toLong())
    private fun use(id: String, at: Double) = event(id, at, ArchivedFastMoveUseObserved(id, "OPPONENT", "PLAYER", "appearance", "Sealeo", listOf("HP_DAMAGE_TICK"), "TEST"))
    private fun energy(id: String, at: Double, use: String) = event(id, at, ArchivedFastMoveEnergyDerived("OPPONENT", "Powder Snow", 1, 8, 8, "OBSERVED_USE"), listOf(use))
    private fun witness(id: String, at: Double) = ReplayFastMoveWitness("OPPONENT", (at * 1e9).toLong(), "Powder Snow", PokemonType.ICE,
        1_000_000_000, 0, 20_000_000_000, setOf("HP_DAMAGE_TICK"), setOf(id), setOf((at * 1e9).toLong()))

    @Test fun `merged and excluded attacks cannot add extra energy while replay is idle`() {
        val records = listOf(use("one", 10.0), energy("energy1", 10.0, "one"),
            use("jitter", 10.2), energy("energy2", 10.2, "jitter"),
            use("two", 10.9), energy("energy3", 10.9, "two"),
            use("excluded", 15.0), energy("energy4", 15.0, "excluded"))
        val before = records.toList()
        val cycles = ReplayFastMoveSchedule.build(listOf(witness("one", 10.0), witness("jitter", 10.2), witness("two", 10.9)))
        assertEquals(2, cycles.size)
        val track = ReplayEnergyTrack(records, { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        fun at(s: Double) = track.stateAt("OPPONENT", "Sealeo", (s * 1e9).toLong())?.recordedEnergy
        assertEquals(0, at(9.9))
        assertEquals(8, at(10.0))
        assertEquals(8, at(10.9)) // The next hit was regularized to 11.0, not its early witness.
        assertEquals(16, at(11.0))
        assertEquals(16, at(18.0)) // Excluded hit does not secretly generate energy.
        assertEquals(8, at(10.5)) // Backward scrub rebuilds the same balance.
        assertEquals(before, records)
    }

    @Test fun `first witnessed hit cannot credit energy before one full reference cycle`() {
        val records = listOf(use("early", .1), energy("gain", .1, "early"))
        val cycles = ReplayFastMoveSchedule.build(listOf(witness("early", .1)))
        val track = ReplayEnergyTrack(records, { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        assertEquals(0, track.stateAt("OPPONENT", "Sealeo", 999_000_000)?.recordedEnergy)
        assertEquals(8, track.stateAt("OPPONENT", "Sealeo", 1_000_000_000)?.recordedEnergy)
    }

    @Test fun `later matching derivation replaces wrong move gain without double credit`() {
        val wrong = event("wrong", 3.0, ArchivedFastMoveEnergyDerived("OPPONENT", "Other move", 1, 20, 20, "OLD_GUESS"), listOf("hit"))
        val corrected = energy("corrected", 4.0, "hit")
        val records = listOf(use("hit", 3.0), wrong, corrected)
        val cycles = ReplayFastMoveSchedule.build(listOf(witness("hit", 3.0)))
        val track = ReplayEnergyTrack(records, { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        assertEquals(8, track.stateAt("OPPONENT", "Sealeo", 3_000_000_000)?.recordedEnergy)
        assertEquals(8, track.stateAt("OPPONENT", "Sealeo", 5_000_000_000)?.generated)
        val unresolved = ReplayEnergyTrack(records.dropLast(1), { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        assertNull(unresolved.stateAt("OPPONENT", "Sealeo", 3_000_000_000))
        assertEquals(20, (wrong.payload as ArchivedFastMoveEnergyDerived).energyPerUse)
    }

    @Test fun `unknown duration cannot show exact energy and faint restores zero`() {
        val records = listOf(use("hit", 3.0), energy("gain", 3.0, "hit"),
            event("faint", 5.0, ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS")))
        val cycles = ReplayFastMoveSchedule.build(listOf(witness("hit", 3.0).copy(durationNanos = null)))
        val track = ReplayEnergyTrack(records, { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        assertNull(track.stateAt("OPPONENT", "Sealeo", 4_000_000_000))
        assertEquals(0, track.stateAt("OPPONENT", "Sealeo", 5_000_000_000)?.recordedEnergy)
        assertNull(track.stateAt("OPPONENT", "Sealeo", 4_000_000_000))
    }

    @Test fun `legacy aggregates remain testimony but cannot supply an unbounded balance`() {
        val record = event("aggregate", 1.0, ArchivedFastMoveEnergyDerived("OPPONENT", "Powder Snow", 20, 8, 160, "LEGACY"))
        val track = ReplayEnergyTrack(listOf(record), { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, emptyList())
        assertEquals(0, track.stateAt("OPPONENT", "Sealeo", 999_000_000)?.recordedEnergy)
        assertNull(track.stateAt("OPPONENT", "Sealeo", 1_000_000_000))
        assertEquals(160, (record.payload as ArchivedFastMoveEnergyDerived).totalEnergyGenerated)
    }

    @Test fun `charge and decision time cannot provide extra generation capacity`() {
        val records = listOf(use("before", 1.0), energy("before-gain", 1.0, "before"),
            use("excluded", 5.0), energy("excluded-gain", 5.0, "excluded"),
            use("after", 10.1), energy("after-gain", 10.1, "after"))
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("before", 1.0).copy(windowEndNanos = 2_000_000_000),
            witness("after", 10.1).copy(windowStartNanos = 10_000_000_000)))
        val track = ReplayEnergyTrack(records, { _, _ -> ReplayCombatant("Sealeo", 364, 0) }, cycles)
        assertEquals(8, track.stateAt("OPPONENT", "Sealeo", 10_999_000_000)?.recordedEnergy)
        assertEquals(16, track.stateAt("OPPONENT", "Sealeo", 11_000_000_000)?.recordedEnergy)
    }

}
