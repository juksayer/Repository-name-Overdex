package com.example.overdex.model

/**
 * A collection of named [CaptureRegion]s that define a specific UI screen state.
 * 
 * Templates are used by the observation pipeline to know which parts of the
 * screen to crop and process for a given context (e.g., the Summary Screen).
 */
data class CaptureTemplate(
    val name: String,
    val regions: List<CaptureRegion>
)
