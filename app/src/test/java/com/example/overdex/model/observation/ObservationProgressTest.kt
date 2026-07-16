package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationProgressTest {

    @Test
    fun `evaluateProgress - empty session is 0 percent complete`() {
        val session = ObservationSession(
            recognitionResults = emptyMap(),
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
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
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
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1")),
            "CandyPanel" to listOf(RecognitionResult("Pikachu Candy", 1.0f, "R1")) // Optional
        )
        val session = ObservationSession(
            recognitionResults = results,
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
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1")),
            "FastMoveRow" to listOf(RecognitionResult("Thunder Shock", 1.0f, "R1")),
            "ChargedMoveRowA" to listOf(RecognitionResult("Discharge", 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.RegisterSpecimen
        )

        val progress = session.evaluateProgress()
        assertEquals(1.0f, progress.percentComplete)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `evaluateProgress - IdentifySpecimen has different requirements`() {
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        // IdentifySpecimen only requires Species and CP (2 fields)
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.IdentifySpecimen
        )

        val progress = session.evaluateProgress()
        assertEquals(1.0f, progress.percentComplete)
        assertTrue(progress.isComplete)
    }
}
