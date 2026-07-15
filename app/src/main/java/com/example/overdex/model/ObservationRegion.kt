package com.example.overdex.model

/**
 * Represents a named area of the Observation Display that represents a meaningful game element.
 *
 * Observation Region is the permanent architectural term used by Overdex.
 * Future recognizers will receive an Observation Region to target their inspection.
 *
 * Note: The fact that an Observation Region is currently rectangular is an implementation detail.
 * Coordinates are normalized (0.0 to 1.0) relative to the source display dimensions.
 */
data class ObservationRegion(
    val name: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

/**
 * Registry of standard Observation Regions used by the framework.
 *
 * These regions establish a common coordinate system for future recognition.
 * Coordinates are initialized to zero and will be populated during calibration implementation.
 */
object ObservationRegions {
    val Species = ObservationRegion("Species")
    val CombatPower = ObservationRegion("Combat Power")
    val FastMove = ObservationRegion("Fast Move")
    val ChargedMove1 = ObservationRegion("Charged Move 1")
    val ChargedMove2 = ObservationRegion("Charged Move 2")
    val ShadowBonus = ObservationRegion("Shadow Bonus")

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
