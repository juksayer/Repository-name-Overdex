package com.example.overdex.battle.custody

import kotlinx.serialization.Serializable

/** Frozen chromatic support, not a segmented Pokemon or an observed departure. */
@Serializable
data class TerminalAppearanceSupport(
    val baselineAtNanos: Long,
    val hueBins: List<Int>,
    val baselineArea: Int,
    val baselineLargestComponent: Int,
    val area: Int,
    val brightenedFraction: Double,
)

/** Independent visibility and numeric appearance measurements from one image. */
@Serializable
data class TerminalAppearanceMeasurement(
    val status: String,
    val width: Int,
    val height: Int,
    // Near, far-color reference, background. Coordinates refer to the measured raster.
    val regions: List<List<Int>>,
    val backgroundChange: Double? = null,
    val backgroundTexture: Double? = null,
    val backgroundMean: Double? = null,
    val supports: List<TerminalAppearanceSupport> = emptyList(),
    val version: Int = 1,
    val previousSourceAtNanos: Long? = null,
)
