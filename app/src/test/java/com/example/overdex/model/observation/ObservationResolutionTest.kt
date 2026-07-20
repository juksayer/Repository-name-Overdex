package com.example.overdex.model.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationResolutionTest {

    private val resolver = DefaultObservationResolver()

    @Test
    fun `resolve - higher confidence wins`() {
        val lowConf = PokemonNameObservation(
            species = "Pikachu",
            source = ObservationSource.OCR,
            observerId = "R1",
            confidence = Confidence(ConfidenceLevel.OBSERVED, 0.5f)
        )
        val highConf = PokemonNameObservation(
            species = "Raichu",
            source = ObservationSource.OCR,
            observerId = "R1",
            confidence = Confidence(ConfidenceLevel.OBSERVED, 0.9f)
        )

        val resolved = resolver.resolve(listOf(lowConf, highConf)) as? PokemonNameObservation
        assertEquals("Raichu", resolved?.species)
    }

    @Test
    fun `resolve - equal confidence preserves existing`() {
        val first = PokemonNameObservation(
            species = "Pikachu",
            source = ObservationSource.OCR,
            observerId = "R1",
            confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f)
        )
        val second = PokemonNameObservation(
            species = "Raichu",
            source = ObservationSource.OCR,
            observerId = "R1",
            confidence = Confidence(ConfidenceLevel.OBSERVED, 0.8f)
        )

        val resolved = resolver.resolve(listOf(first, second)) as? PokemonNameObservation
        assertEquals("Pikachu", resolved?.species)
    }

    @Test
    fun `resolve - new information wins`() {
        val newInfo = PokemonNameObservation(
            species = "Pikachu",
            source = ObservationSource.OCR,
            observerId = "R1",
            confidence = Confidence(ConfidenceLevel.OBSERVED, 0.1f)
        )

        val resolved = resolver.resolve(listOf(newInfo)) as? PokemonNameObservation
        assertEquals("Pikachu", resolved?.species)
    }
}
