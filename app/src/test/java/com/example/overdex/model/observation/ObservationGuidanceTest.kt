package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationGuidanceTest {

    @Test
    fun `nextObservation - OBJECTIVE_COMPLETE when all required fields resolved`() {
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

        val guidance = session.nextObservation()
        assertEquals(GuidanceStatus.OBJECTIVE_COMPLETE, guidance.status)
        assertTrue(guidance.targetFields.isEmpty())
    }

    @Test
    fun `nextObservation - CONTINUE_OBSERVING when required fields missing`() {
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.RegisterSpecimen
        )

        val guidance = session.nextObservation()
        assertEquals(GuidanceStatus.CONTINUE_OBSERVING, guidance.status)
        assertEquals(setOf("FastMoveRow", "ChargedMoveRowA"), guidance.targetFields)
    }

    @Test
    fun `nextObservation - CONFLICT_REQUIRES_CONFIRMATION when required fields conflict`() {
        val results = mapOf(
            "SpeciesName" to listOf(
                RecognitionResult("Pikachu", 1.0f, "R1"),
                RecognitionResult("Raichu", 0.9f, "R1")
            ),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation()
        assertEquals(GuidanceStatus.CONFLICT_REQUIRES_CONFIRMATION, guidance.status)
        assertTrue(guidance.targetFields.contains("SpeciesName"))
    }

    @Test
    fun `nextObservation - INSUFFICIENT_EVIDENCE when no required fields resolved`() {
        val session = ObservationSession(
            recognitionResults = emptyMap(),
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation()
        assertEquals(GuidanceStatus.INSUFFICIENT_EVIDENCE, guidance.status)
        assertEquals(setOf("SpeciesName", "CombatPower"), guidance.targetFields)
    }

    @Test
    fun `nextObservation - optional fields do not block OBJECTIVE_COMPLETE`() {
        val results = mapOf(
            "SpeciesName" to listOf(RecognitionResult("Pikachu", 1.0f, "R1")),
            "CombatPower" to listOf(RecognitionResult(500, 1.0f, "R1"))
        )
        // IdentifySpecimen only requires Species and CP
        val session = ObservationSession(
            recognitionResults = results,
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation()
        assertEquals(GuidanceStatus.OBJECTIVE_COMPLETE, guidance.status)
    }
}
