package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationGuidanceTest {

    private val resolver = DefaultObservationResolver()
    private val source = ObservationSource.OCR
    private val confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)

    @Test
    fun `nextObservation - OBJECTIVE_COMPLETE when all required fields resolved`() {
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

        val guidance = session.nextObservation(resolver)
        assertEquals(GuidanceStatus.OBJECTIVE_COMPLETE, guidance.status)
        assertTrue(guidance.targetFields.isEmpty())
    }

    @Test
    fun `nextObservation - CONTINUE_OBSERVING when required fields missing`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.RegisterSpecimen
        )

        val guidance = session.nextObservation(resolver)
        assertEquals(GuidanceStatus.CONTINUE_OBSERVING, guidance.status)
        assertEquals(setOf("FastMoveRow", "ChargedMoveRowA"), guidance.targetFields)
    }

    @Test
    fun `nextObservation - CONFLICT_REQUIRES_CONFIRMATION when required fields conflict`() {
        val history = mapOf(
            "SpeciesName" to listOf(
                PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)),
                PokemonNameObservation("Raichu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f))
            ),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation(resolver)
        assertEquals(GuidanceStatus.CONFLICT_REQUIRES_CONFIRMATION, guidance.status)
        assertTrue(guidance.targetFields.contains("SpeciesName"))
    }

    @Test
    fun `nextObservation - INSUFFICIENT_EVIDENCE when no required fields resolved`() {
        val session = ObservationSession(
            history = emptyMap(),
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation(resolver)
        assertEquals(GuidanceStatus.INSUFFICIENT_EVIDENCE, guidance.status)
        assertEquals(setOf("SpeciesName", "CombatPower"), guidance.targetFields)
    }

    @Test
    fun `nextObservation - optional fields do not block OBJECTIVE_COMPLETE`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        // IdentifySpecimen only requires Species and CP
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.IdentifySpecimen
        )

        val guidance = session.nextObservation(resolver)
        assertEquals(GuidanceStatus.OBJECTIVE_COMPLETE, guidance.status)
    }
}
