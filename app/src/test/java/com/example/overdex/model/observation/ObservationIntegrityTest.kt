package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationIntegrityTest {

    private val resolver = DefaultObservationResolver()
    private val source = ObservationSource.OCR
    private val confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)

    @Test
    fun `evaluateIntegrity - COMPLETE when all required fields present`() {
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

        val integrity = session.evaluateIntegrity(resolver)
        assertEquals(IntegrityStatus.COMPLETE, integrity.status)
        assertTrue(integrity.missingFields.isEmpty())
        assertTrue(integrity.conflictingFields.isEmpty())
    }

    @Test
    fun `evaluateIntegrity - PARTIAL when some required fields missing`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.RegisterSpecimen
        )

        val integrity = session.evaluateIntegrity(resolver)
        assertEquals(IntegrityStatus.PARTIAL, integrity.status)
        assertEquals(setOf("FastMoveRow", "ChargedMoveRowA"), integrity.missingFields)
    }

    @Test
    fun `evaluateIntegrity - CONFLICTING when required field has multiple values`() {
        val history = mapOf(
            "SpeciesName" to listOf(
                PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f)),
                PokemonNameObservation("Raichu", source = source, observerId = "R1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f)) // Conflict within same recognizer
            ),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity(resolver)
        assertEquals(IntegrityStatus.CONFLICTING, integrity.status)
        assertTrue(integrity.conflictingFields.contains("SpeciesName"))
    }

    @Test
    fun `evaluateIntegrity - INSUFFICIENT when no resolved fields exist`() {
        val session = ObservationSession(
            history = emptyMap(),
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity(resolver)
        assertEquals(IntegrityStatus.INSUFFICIENT, integrity.status)
    }

    @Test
    fun `evaluateIntegrity - objective IdentifySpecimen ignores missing moves`() {
        val history = mapOf(
            "SpeciesName" to listOf(PokemonNameObservation("Pikachu", source = source, observerId = "R1", confidence = confidence)),
            "CombatPower" to listOf(CombatPowerObservation(500, source = source, observerId = "R1", confidence = confidence))
        )
        // IdentifySpecimen only requires Species and CP
        val session = ObservationSession(
            history = history,
            objective = ObservationObjective.IdentifySpecimen
        )

        val integrity = session.evaluateIntegrity(resolver)
        assertEquals(IntegrityStatus.COMPLETE, integrity.status)
        assertTrue(integrity.missingFields.isEmpty())
    }
}
