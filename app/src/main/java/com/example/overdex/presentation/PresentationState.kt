package com.example.overdex.presentation

import com.example.overdex.model.PokemonType

/**
 * The top-level immutable model representing the entire instrument's 
 * current presentation state.
 *
 * This model is strictly semantic and UI-independent. It describes *what* is known,
 * not *how* it is rendered. It is designed to be serializable for replays.
 */
data class PresentationState(
    val instrument: InstrumentLifecycle = InstrumentLifecycle.IDLE,
    val observation: ObservationPresentation = ObservationPresentation(),
    val tactical: TacticalPresentation = TacticalPresentation(),
    val team: TeamPresentation = TeamPresentation(),
    val timeline: TimelinePresentation = TimelinePresentation()
)

enum class InstrumentLifecycle {
    IDLE,
    BOOTING,
    CALIBRATING,
    DEPLOYED,
    SERVICE_ACTIVE
}

/**
 * Describes the current activity and progress of the Observation Layer.
 */
data class ObservationPresentation(
    val focus: ObservationFocus = ObservationFocus.IDLE,
    val activity: ObservationActivity = ObservationActivity.IDLE,
    val status: ObservationIndicator = ObservationIndicator.OFF,
    val progress: Float = 0f,
    val captureId: String = "00000",
    val missingRequirements: List<ObservationRequirement> = emptyList(),
    val estimate: ObservationEstimate = ObservationEstimate()
)

enum class ObservationFocus {
    IDLE,
    ANCHORS,
    SPECIES,
    COMBAT_POWER,
    SHADOW,
    FAST_MOVE,
    CHARGED_MOVE_A,
    CHARGED_MOVE_B,
    COMPLETE
}

enum class ObservationActivity {
    IDLE,
    LOCATING_ANCHORS,
    IDENTIFYING_SPECIES,
    MEASURING_COMBAT_POWER,
    DETECTING_SHADOW_STATUS,
    RECOGNIZING_FAST_MOVE,
    RECOGNIZING_CHARGED_MOVE_A,
    RECOGNIZING_CHARGED_MOVE_B,
    FINALIZING_SESSION
}

enum class ObservationIndicator {
    OFF,
    SEARCHING,
    ALIGNING,
    CONFIRMED,
    SYNCING,
    ERROR
}

enum class ObservationRequirement {
    SPECIES,
    COMBAT_POWER,
    FAST_MOVE,
    CHARGED_MOVE_A,
    CHARGED_MOVE_B
}

data class ObservationEstimate(
    val remainingObservations: Int = 0
)

/**
 * Tactical recommendations derived from Intelligence Layer analysis.
 */
data class TacticalPresentation(
    val primaryGuidance: TacticalAction = TacticalAction.UNKNOWN,
    val urgency: TacticalPriority = TacticalPriority.NONE,
    val threat: TacticalAdvantage = TacticalAdvantage.NEUTRAL,
    val advantage: TacticalAdvantage = TacticalAdvantage.NEUTRAL,
    val confidence: Float = 0f,
    val shieldRequired: Boolean = false,
    val evidence: List<TacticalEvidence> = emptyList()
)

/**
 * Semantic evidence supporting a tactical recommendation.
 */
sealed class TacticalEvidence {
    data class TypeAdvantage(val advantage: TacticalAdvantage) : TacticalEvidence()
    data class ObservedMove(val moveName: String, val isFast: Boolean) : TacticalEvidence()
    data class EnergyLead(val isLeading: Boolean) : TacticalEvidence()
}

enum class TacticalAction {
    UNKNOWN,
    STAY_AND_FIGHT,
    SWITCH_NOW,
    FARM_ENERGY,
    SHIELD_LIKELY_REQUIRED,
    REGISTER_SPECIMEN,
    SELECT_SPECIES_MANUALLY
}

enum class TacticalPriority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class TacticalAdvantage {
    NEUTRAL,
    LOW,
    MEDIUM,
    HIGH
}

data class TeamPresentation(
    val opponent: OpponentTeamPresentation = OpponentTeamPresentation(),
    val player: PlayerTeamPresentation = PlayerTeamPresentation()
)

data class OpponentTeamPresentation(
    val activeSpecies: String? = null,
    val activeSpeciesId: Int? = null,
    val knownMoves: List<SemanticMove> = emptyList(),
    val shieldsUsed: Int = 0,
    val remainingPokemon: Int? = null,
    val members: List<EnemyMemberPresentation> = emptyList()
)

data class EnemyMemberPresentation(
    val species: String,
    val speciesId: Int?,
    val isAlive: Boolean,
    val isActive: Boolean,
    val energyLevel: Float // 0.0 to 1.0
)

data class SemanticMove(
    val name: String,
    val type: PokemonType,
    val isFast: Boolean,
    val effectiveness: MoveEffectiveness
)

enum class MoveEffectiveness {
    SUPER_EFFECTIVE,
    NEUTRAL,
    NOT_VERY_EFFECTIVE,
    IMMUNE,
    UNKNOWN
}

data class PlayerTeamPresentation(
    val activeSpecies: String? = null,
    val shieldsUsed: Int = 0
)

data class TimelinePresentation(
    val status: TimelineStatus = TimelineStatus.IDLE,
    val eventCount: Int = 0
)

enum class TimelineStatus {
    IDLE,
    RECORDING,
    SYNCHRONIZED
}
