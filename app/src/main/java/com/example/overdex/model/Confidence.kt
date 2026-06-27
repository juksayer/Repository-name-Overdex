package com.example.overdex.model

enum class ConfidenceLevel {
    INFERRED,    // Data predicted by logic (e.g. backline prediction, energy)
    OBSERVED     // Data directly seen (e.g. OCR confirmed species or move)
}

data class Confidence(
    val level: ConfidenceLevel,
    val score: Float = if (level == ConfidenceLevel.OBSERVED) 1.0f else 0.5f
)
