package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Base interface for all evidence payloads recorded by the Observation Recorder.
 * Ensures type safety and clean serialization for diverse signal types.
 */
@Serializable
sealed interface RecordedPayload

/**
 * Payload implementation for Accessibility evidence.
 */
@Serializable
data class AccessibilityPayload(
    val eventType: String,
    val packageName: String?,
    val className: String?,
    val text: List<String>,
    val contentDescription: String?,
    val bounds: RectData?,
    val viewIdResourceName: String?,
    val nodeTree: AccessibilityProbeNode?,
    val rawEventData: Map<String, String> = emptyMap()
) : RecordedPayload

/**
 * Documents a visual capture attempt on a specific screen region.
 */
@Serializable
data class VisionCapturePayload(
    val regionId: String,
    val width: Int,
    val height: Int,
    val observationStage: String
) : RecordedPayload

/**
 * Documents a detected visual anchor.
 */
@Serializable
data class AnchorDetectedPayload(
    val anchorType: String,
    val bounds: RectData,
    val confidence: Float,
    val observationStage: String
) : RecordedPayload

/**
 * Documents a recognition attempt (success or failure).
 */
@Serializable
data class RecognitionAttemptPayload(
    val regionId: String,
    val recognizerName: String,
    val success: Boolean,
    val resultValue: String? = null,
    val confidence: Float = 0f,
    val observationStage: String
) : RecordedPayload

/**
 * Represents a competitor in a decision evaluation.
 */
@Serializable
data class DecisionCompetitor(
    val recognizer: String,
    val value: String?,
    val confidence: Float
)

/**
 * Documents a decision made by the engine regarding a specific field.
 */
@Serializable
data class DecisionEvaluatedPayload(
    val field: String,
    val winningValue: String?,
    val winningConfidence: Float,
    val competitors: List<DecisionCompetitor>,
    val observationStage: String,
    val contributingEvents: List<Long> = emptyList()
) : RecordedPayload

/**
 * Documents an integrity check on the session state.
 */
@Serializable
data class IntegrityCheckedPayload(
    val status: String,
    val resolvedFields: Set<String>,
    val missingFields: Set<String>,
    val conflictingFields: Set<String>
) : RecordedPayload

/**
 * Documents a change in the session's progress.
 */
@Serializable
data class ProgressUpdatedPayload(
    val percentComplete: Float,
    val isComplete: Boolean,
    val observationStage: String
) : RecordedPayload

/**
 * System-level event (Session started, stopped, etc.).
 */
@Serializable
data class SystemEventPayload(
    val eventName: String,
    val details: String? = null
) : RecordedPayload

@Serializable
data class AccessibilityProbeNode(
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val clickable: Boolean,
    val focusable: Boolean,
    val visible: Boolean,
    val enabled: Boolean,
    val bounds: RectData,
    val children: List<AccessibilityProbeNode> = emptyList()
)
