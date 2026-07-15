package com.example.overdex.model

/**
 * Represents a predefined area of the observation display that future recognizers will inspect.
 *
 * An Observation Region is defined by its spatial boundaries and a unique name.
 * Coordinates are normalized (0.0 to 1.0) relative to the source display dimensions.
 *
 * These regions establish a common coordinate system for future recognition,
 * allowing recognizers to receive a specific area instead of searching an entire screenshot.
 */
data class ObservationRegion(
    val name: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

/**
 * Standard registry of fixed observation regions used by the framework.
 */
object ObservationRegions {
    /**
     * Placeholder definitions for standard observation regions.
     * Coordinates are initialized to zero and will be populated during calibration implementation.
     */
    val Species = ObservationRegion(name = "Species")
    val CombatPower = ObservationRegion(name = "Combat Power")
    val FastMove = ObservationRegion(name = "Fast Move")
    val ChargedMove1 = ObservationRegion(name = "Charged Move 1")
    val ChargedMove2 = ObservationRegion(name = "Charged Move 2")
    val ShadowBonus = ObservationRegion(name = "Shadow Bonus")

    /**
     * Returns a list of all defined placeholder regions.
     */
    val all = listOf(
        Species,
        CombatPower,
        FastMove,
        ChargedMove1,
        ChargedMove2,
        ShadowBonus
    )
}
