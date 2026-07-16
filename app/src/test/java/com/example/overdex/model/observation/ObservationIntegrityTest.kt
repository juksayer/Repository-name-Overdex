package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationIntegrityTest {

    @Test
    fun `evaluateIntegrity - COMPLETE when all required fields present`() {
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

        val integrity = session.evaluateIntegrity()
        assertEquals(IntegrityStatus.COMPLETE, integrity.status)
        assertTrue(integrity.missingFields.isEmpty())
        assertTrue(integrity.conflictingFields.isEmpty())
    }

    @Test
    fun `evaluateIntegrity - PARTIAL when some required fields missing`() {
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.RegisterSpecimen
        )

        val integrity = session.evaluateIntegrity()
        assertEquals(IntegrityStatus.PARTIAL, integrity.status)
        assertEquals(setOf("FastMoveRow", "ChargedMoveRowA"), integrity.missingFields)
    }

    @Test
    fun `evaluateIntegrity - CONFLICTING when required field has multiple values`() {
        val results = mapOf(
            "SpeciesName" to listOf(
                RecognitionResult("Pikachu", 0.9f, "R1"),
                RecognitionResult("Raichu", 0.8f, "R1") // Conflict within same recognizer
            ),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity()
        assertEquals(IntegrityStatus.CONFLICTING, integrity.status)
        assertTrue(integrity.conflictingFields.contains("SpeciesName"))
    }

    @Test
    fun `evaluateIntegrity - INSUFFICIENT when no resolved fields exist`() {
        val session = ObservationSession(
            recognitionResults = emptyMap(),
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity()
        assertEquals(IntegrityStatus.INSUFFICIENT, integrity.status)
    }

    @Test
    fun `evaluateIntegrity - objective IdentifySpecimen ignores missing moves`() {
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        // IdentifySpecimen only requires Species and CP
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity()
        assertEquals(IntegrityStatus.COMPLETE, integrity.status)
        assertTrue(integrity.missingFields.isEmpty())
    }
}
