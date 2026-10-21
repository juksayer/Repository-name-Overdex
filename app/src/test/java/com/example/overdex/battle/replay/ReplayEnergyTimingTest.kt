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
        fun at(s: Double) = track.stateAt("OPPONENT", "Sealeo", (s * 1e9).toLong()).recordedEnergy
        assertEquals(0, at(9.9))
        assertEquals(8, at(10.0))
        assertEquals(8, at(10.9)) // The next hit was regularized to 11.0, not its early witness.
        assertEquals(16, at(11.0))
        assertEquals(16, at(18.0)) // Excluded hit does not secretly generate energy.
        assertEquals(8, at(10.5)) // Backward scrub rebuilds the same balance.
        assertEquals(before, records)
    }
}
