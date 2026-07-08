package com.example.overdex.model

/**
 * A collection of named [CaptureRegion]s that define a specific UI screen state.
 */
data class CaptureTemplate(
    val name: String,
    val regions: List<CaptureRegion>
)
