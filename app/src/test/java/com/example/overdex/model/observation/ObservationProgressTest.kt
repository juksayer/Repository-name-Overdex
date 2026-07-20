package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationProgressTest {

    private val source = ObservationSource.OCR
    private val confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)

    @Test
    fun `evaluateProgress - empty session is 0 percent complete`() {
        val session = ObservationSession(
            history = emptyMap(),
            objective = ObservationObjective.RegisterSpecimen
        )

        val progress = session.evaluateProgress()
        assertEquals(0.0f, progress.percentComplete)
        assertEquals(4, progress.totalRequiredFields)
        assertEquals(0, progress.completedRequiredFields)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `evaluateProgress - partial progress updates correctly`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.RegisterSpecimen
        )

        val progress = session.evaluateProgress()
        // RegisterSpecimen has 4 required fields. 2 are present.
        assertEquals(0.5f, progress.percentComplete)
        assertEquals(2, progress.completedRequiredFields)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `evaluateProgress - optional fields do not affect progress percentage`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence)),
            "CandyPanel" to listOf(EvolutionFamilyObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)) // Optional
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.RegisterSpecimen
        )

        val progress = session.evaluateProgress()
        // Still 2/4 required fields. Optional "CandyPanel" shouldn't count toward the % or completion.
        assertEquals(0.5f, progress.percentComplete)
        assertEquals(2, progress.completedRequiredFields)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `evaluateProgress - isComplete true when all required fields present`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence)),
            "FastMoveRow" to listOf(FastMoveObservation("Pikachu", "Thunder Shock", source = source, observerId = "R1", confidence = confidence)),
            "ChargedMoveRowA" to listOf(ChargedMoveObservation("Pikachu", "Discharge", source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.RegisterSpecimen
        )

        val progress = session.evaluateProgress()
        assertEquals(1.0f, progress.percentComplete)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `evaluateProgress - IdentifySpecimen has different requirements`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        // IdentifySpecimen only requires Species and CP (2 fields)
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.IdentifySpecimen
        )

        val progress = session.evaluateProgress()
        assertEquals(1.0f, progress.percentComplete)
        assertTrue(progress.isComplete)
    }
}
