package com.example.overdex.data.observation

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import com.example.overdex.model.observation.*

/**
 * Mapper responsible for converting raw [RecognitionResult] artifacts into domain [Observation] facts.
 */
object RecognitionObservationMapper {

    /**
     * Maps a [RecognitionResult] to a specific [Observation] subclass based on the [regionId].
     */
    fun map(
        regionId: String,
        result: RecognitionResult<*>,
        source: ObservationSource = ObservationSource.OCR
    ): Observation? {
        val value = result.value ?: return null
        val confidence = Confidence(ConfidenceLevel.OBSERVED, result.confidence)
        val observerId = result.recognizer
        val timestamp = System.currentTimeMillis()

        return when (regionId) {
            "SpeciesName" -> PokemonNameObservation(
                species = value as String,
                timestamp = timestamp,
                source = source,
                observerId = observerId,
                confidence = confidence
            )
            "CombatPower" -> CombatPowerObservation(
                cp = value as Int,
                timestamp = timestamp,
                source = source,
                observerId = observerId,
                confidence = confidence
            )
            "CandyPanel" -> EvolutionFamilyObservation(
                familySpecies = value as String,
                timestamp = timestamp,
                source = source,
                observerId = observerId,
                confidence = confidence
            )
            "FastMoveRow", "SummaryFastMove" -> FastMoveObservation(
                species = "UNKNOWN", // Species context might be added later if needed
                moveName = value as String,
                timestamp = timestamp,
                source = source,
                observerId = observerId,
                confidence = confidence
            )
            "ChargedMoveRowA", "ChargedMoveRowB" -> ChargedMoveObservation(
                species = "UNKNOWN",
                moveName = value as String,
                timestamp = timestamp,
                source = source,
                observerId = observerId,
                confidence = confidence
            )
            // Note: ShadowBonusRecognizer might be called on move rows
            else -> {
                if (value is Boolean && (regionId.contains("Move") || regionId.contains("Fast") || regionId.contains("Charged"))) {
                    ShadowStatusObservation(
                        isShadow = value,
                        timestamp = timestamp,
                        source = source,
                        observerId = observerId,
                        confidence = confidence
                    )
                } else {
                    null
                }
            }
        }
    }
}
