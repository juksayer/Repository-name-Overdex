package com.example.overdex.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Represents a named, immutable area of the Pokémon GO UI target for inspection.
 *
 * Observation Regions are the permanent architectural descriptors used by Overdex
 * to target recognizers (OCR, ML, etc.) to specific screen coordinates.
 * Coordinates are normalized (0.0 to 1.0) to ensure resolution independence.
 * 
 * @property name The identifier for the region (e.g., "Species").
 * @property x The normalized horizontal start position.
 * @property y The normalized vertical start position.
 * @property width The normalized width of the region.
 * @property height The normalized height of the region.
 */
data class ObservationRegion(
    val name: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

/**
 * Represents the current runtime state of an Observation Region.
 *
 * This holds mutable information like temporary adjustments or calibration offsets.
 * It is decoupled from the permanent definition.
 */
class ObservationRegionState(
    val region: ObservationRegion,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f
) {
    var offsetX by mutableStateOf(initialOffsetX)
    var offsetY by mutableStateOf(initialOffsetY)

    val currentX get() = region.x + offsetX
    val currentY get() = region.y + offsetY
    val name get() = region.name
    val width get() = region.width
    val height get() = region.height
}

/**
 * Registry of standard Observation Regions used by the framework.
 */
object ObservationRegions {
    val Species = ObservationRegion("Species")
    val CombatPower = ObservationRegion("Combat Power")
    val FastMove = ObservationRegion("Fast Move")
    val ChargedMove1 = ObservationRegion("Charged Move 1")
    val ChargedMove2 = ObservationRegion("Charged Move 2")
    val ShadowBonus = ObservationRegion("Shadow Bonus")

    /**
     * Runtime state registry.
     * Maps regions to their current mutable state.
     */
    val stateRegistry = mapOf(
        Species.name to ObservationRegionState(Species),
        CombatPower.name to ObservationRegionState(CombatPower),
        FastMove.name to ObservationRegionState(FastMove),
        ChargedMove1.name to ObservationRegionState(ChargedMove1),
        ChargedMove2.name to ObservationRegionState(ChargedMove2),
        ShadowBonus.name to ObservationRegionState(ShadowBonus)
    )

    /**
     * Returns the current state for a named region.
     */
    fun getState(name: String) = stateRegistry[name]

    /**
     * Returns all current Observation Region states.
     */
    val allStates get() = stateRegistry.values.toList()
}
