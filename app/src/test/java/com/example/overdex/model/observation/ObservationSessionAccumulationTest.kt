package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationSessionAccumulationTest {

    private val source = ObservationSource.OCR
    private val confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)
    private val resolver = DefaultObservationResolver()

    @Test
    fun `session accumulation - adding results to existing results`() {
        val o1 = PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)
        val session1 = ObservationSession(
            history = mapOf("SpeciesName" to listOf(o1))
        )

        val o2 = CombatPowerObservation(500, source = source, observerId = "R2", confidence = confidence)
        // Simulating GuidedObservationPipeline accumulation logic
        val allHistory = session1.history.toMutableMap()
        val currentHistory = allHistory["CombatPower"] ?: emptyList()
        allHistory["CombatPower"] = currentHistory + listOf(o2)

        val session2 = session1.copy(history = allHistory.toMap())

        assertEquals(2, session2.history.size)
        assertTrue(session2.history.containsKey("SpeciesName"))
        assertTrue(session2.history.containsKey("CombatPower"))
        assertEquals("Pikachu", (session2.history["SpeciesName"]?.first() as PokemonNameObservation).species)
        assertEquals(500, (session2.history["CombatPower"]?.first() as CombatPowerObservation).cp)
    }

    @Test
    fun `session accumulation - appending results to same region`() {
        val o1 = PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f))
        val session1 = ObservationSession(
            history = mapOf("SpeciesName" to listOf(o1))
        )

        val o2 = PokemonNameObservation("Raichu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f))
        val allHistory = session1.history.toMutableMap()
        val currentHistory = allHistory["SpeciesName"] ?: emptyList()
        allHistory["SpeciesName"] = currentHistory + listOf(o2)

        val session2 = session1.copy(history = allHistory.toMap())

        assertEquals(1, session2.history.size)
        val speciesHistory = session2.history["SpeciesName"]
        assertEquals(2, speciesHistory?.size)
        
        // Resolution should pick the higher confidence one
        val resolved = resolver.resolve(speciesHistory ?: emptyList())
        assertEquals("Raichu", (resolved as PokemonNameObservation).species)
    }
}
