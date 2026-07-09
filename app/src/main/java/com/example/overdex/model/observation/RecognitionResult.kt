package com.example.overdex.model.observation

/**
 * A container for a value extracted from visual evidence by a specialized recognizer.
 */
data class RecognitionResult<T>(
    val value: T?,
    val confidence: Float,
    val recognizer: String
)
