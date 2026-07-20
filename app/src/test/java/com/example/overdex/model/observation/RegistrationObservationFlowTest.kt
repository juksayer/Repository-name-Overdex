package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import com.example.overdex.model.RegistrationSession
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationObservationFlowTest {

    private val resolver = DefaultObservationResolver()

    @Test
    fun `registration - consumes observations and resolves best values`() {
        val speciesId = 25 // Pikachu

        val o1 = PokemonNameObservation("Pikachu", source = ObservationSource.OCR, observerId = "OCR1", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f))
        val o2 = CombatPowerObservation(500, source = ObservationSource.OCR, observerId = "OCR2", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.7f))
        val o3 = CombatPowerObservation(550, source = ObservationSource.OCR, observerId = "OCR3", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f))
        val o4 = FastMoveObservation("Pikachu", "Thunder Shock", source = ObservationSource.OCR, observerId = "OCR4", confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f))
        val o5 = ShadowStatusObservation(true, source = ObservationSource.OCR, observerId = "OCR5", confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f))

        val registrationSession = RegistrationSession(
            observations = listOf(o1, o2, o3, o4, o5)
        )

        val specimen = registrationSession.buildSpecimen(speciesId, resolver)

        assertEquals(550, specimen.cp) // 550 (0.8) wins over 500 (0.7)
        assertEquals("Thunder Shock", specimen.fastMove)
        assertEquals(true, specimen.isShadow)
    }

    @Test
    fun `registration - handles multiple charged moves`() {
        val speciesId = 25

        val m1 = ChargedMoveObservation("Pikachu", "Wild Charge", source = ObservationSource.OCR, observerId = "OCR", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f))
        val m2 = ChargedMoveObservation("Pikachu", "Thunderbolt", source = ObservationSource.OCR, observerId = "OCR", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f))
        val m3 = ChargedMoveObservation("Pikachu", "Discharge", source = ObservationSource.OCR, observerId = "OCR", confidence = Confidence(ConfidenceLevel.OBSERVED, 0.7f))

        val session = RegistrationSession(
            observations = listOf(m1, m2, m3)
        )

        val specimen = session.buildSpecimen(speciesId, resolver)

        // Should pick the two highest confidence moves
        assertEquals("Wild Charge", specimen.chargedMove1)
        assertEquals("Thunderbolt", specimen.chargedMove2)
    }
}
