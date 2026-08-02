package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Base interface for all evidence payloads recorded by the [ObservationRecorder].
 * 
 * Payloads represent the specific data points captured by the observatory, from
 * raw OCR text to complex accessibility node trees.
 */
@Serializable
sealed interface RecordedPayload

/**
 * Evidence captured from the Android Accessibility framework.
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
 * Metadata regarding a visual capture attempt on a specific screen region.
 */
@Serializable
data class VisionCapturePayload(
    val regionId: String,
    val width: Int,
    val height: Int,
    val observationStage: String
) : RecordedPayload

/**
 * Metadata for a detected visual anchor used for spatial alignment.
 */
@Serializable
data class
AnchorDetectedPayload(
    val anchorType: String,
    val bounds: RectData,
    val confidence: Float,
    val observationStage: String
) : RecordedPayload

/**
 * Outcome of a specific recognition attempt (e.g., OCR or pattern matching).
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
 * Represents a specific evidence point or competitor considered during a decision evaluation.
 */
@Serializable
data class DecisionCompetitor(
    val recognizer: String,
    val value: String?,
    val confidence: Float
)

/**
 * Records a decision made by an observation engine regarding the value of a specific field.
 * 
 * @property field The name of the field being decided (e.g., "SpeciesName").
 * @property winningValue The value selected as the best belief.
 * @property winningConfidence The confidence score associated with the winning value.
 * @property competitors All candidate values considered during resolution.
 * @property observationStage The pipeline stage where the decision occurred.
 * @property contributingEvents A list of sequence numbers for events that influenced this decision.
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
 * Records the results of a Match integrity check.
 */
@Serializable
data class IntegrityCheckedPayload(
    val status: String,
    val resolvedFields: Set<String>,
    val missingFields: Set<String>,
    val conflictingFields: Set<String>
) : RecordedPayload

/**
 * Records a point-in-time snapshot of observation progress.
 */
@Serializable
data class ProgressUpdatedPayload(
    val percentComplete: Float,
    val isComplete: Boolean,
    val observationStage: String
) : RecordedPayload

/**
 * Records a system-level event within the observation lifecycle.
 * 
 * @property eventName Name of the system event (e.g., "MatchStarted").
 * @property details Additional contextual information.
 */
@Serializable
data class SystemEventPayload(
    val eventName: String,
    val details: String? = null
) : RecordedPayload

/**
 * A simplified representation of an accessibility node for debugging and recording.
 */
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
