package com.example.overdex.model.observation

/**
 * A container for a value extracted from visual evidence by a specialized recognizer.
 * 
 * @property value The recognized data, or null if recognition failed.
 * @property confidence Certainty of the recognition result from 0.0 to 1.0.
 * @property recognizer The identifier of the component that produced this result.
 */
data class RecognitionResult<T>(
    val value: T?,
    val confidence: Float,
    val recognizer: String
)
