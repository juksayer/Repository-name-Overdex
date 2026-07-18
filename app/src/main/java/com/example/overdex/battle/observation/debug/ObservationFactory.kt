package com.example.overdex.battle.observation.debug

import com.example.overdex.battle.observation.Observation
import com.example.overdex.battle.timeline.confidence.*
import com.example.overdex.battle.timeline.evidence.*
import com.example.overdex.battle.timeline.observer.*

/**
 * Convenience helpers for creating observations during development and testing.
 * Exists only to reduce boilerplate while exercising the observation pipeline.
 */
object ObservationFactory {

    fun createDebugObservation(
        sourceId: String = "DEBUG_SOURCE",
        sourceType: ObservationSource = ObservationSource.SYSTEM
    ): Observation {
        return Observation(
            timestamp = System.currentTimeMillis(),
            observerId = ObserverId(sourceId, sourceType),
            evidence = emptyList(),
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.CONFIRMED)
        )
    }

    fun createVisualObservation(
        sourceId: String,
        frameUri: String,
        timestamp: Long = System.currentTimeMillis()
    ): Observation {
        return Observation(
            timestamp = timestamp,
            observerId = ObserverId(sourceId, ObservationSource.SCREEN_CAPTURE),
            evidence = listOf(VisualEvidence(sourceId, frameUri)),
            confidence = ConfidenceScore(0.9f, ConfidenceLevel.OBSERVED)
        )
    }

    fun createAudioObservation(
        sourceId: String,
        audioUri: String,
        timestamp: Long = System.currentTimeMillis()
    ): Observation {
        return Observation(
            timestamp = timestamp,
            observerId = ObserverId(sourceId, ObservationSource.AUDIO_CAPTURE),
            evidence = listOf(AudioEvidence(sourceId, audioUri)),
            confidence = ConfidenceScore(0.8f, ConfidenceLevel.OBSERVED)
        )
    }

    fun createStateObservation(
        sourceId: String,
        key: String,
        value: String,
        timestamp: Long = System.currentTimeMillis()
    ): Observation {
        return Observation(
            timestamp = timestamp,
            observerId = ObserverId(sourceId, ObservationSource.SYSTEM),
            evidence = listOf(StateEvidence(sourceId, key, value)),
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.CONFIRMED)
        )
    }
}
