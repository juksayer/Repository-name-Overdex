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

/**
 * Defines the logical lifecycle of the Overdex instrument hardware.
 */
enum class InstrumentLifecycle {
    /** Hardware is powered off or inactive. */
    IDLE,
    /** System is initializing sensors and recognizers. */
    BOOTING,
    /** User is adjusting region offsets. */
    CALIBRATING,
    /** Full tactical display is active. */
    DEPLOYED,
    /** Background monitoring is active. */
    SERVICE_ACTIVE
}

/**
 * Describes the current activity and progress of the Observation Layer.
 * 
 * @property focus The specific UI element being targeted for observation.
 * @property activity The technical task being performed (e.g., OCR).
 * @property status Semantic indicator for Droidball/UI alerts.
 * @property progress Overall completion of the session objective (0.0 to 1.0).
 * @property captureId Identifier for the specific visual capture being processed.
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

/**
 * Specific target areas for visual focus during an observation attempt.
 */
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

/**
 * Technical tasks performed by the observation pipeline.
 */
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

/**
 * High-level status indicators for the observation engine.
 */
enum class ObservationIndicator {
    /** Observation engine is inactive. */
    OFF,
    /** Actively searching for patterns or text. */
    SEARCHING,
    /** Calibrating to visual anchors. */
    ALIGNING,
    /** Information confirmed with high confidence. */
    CONFIRMED,
    /** Synchronizing with external sources or GameMaster. */
    SYNCING,
    /** A technical fault has occurred. */
    ERROR
}

/**
 * Data requirements that must be met to satisfy an observation objective.
 */
enum class ObservationRequirement {
    SPECIES,
    COMBAT_POWER,
    FAST_MOVE,
    CHARGED_MOVE_A,
    CHARGED_MOVE_B
}

/**
 * Predictive metrics for the current observation session.
 */
data class ObservationEstimate(
    val remainingObservations: Int = 0
)

/**
 * Tactical recommendations derived from Intelligence Layer analysis.
 * 
 * @property primaryGuidance The suggested tactical action for the trainer.
 * @property urgency How critical the recommended action is.
 * @property threat Assessment of the opponent's advantage.
 * @property advantage Assessment of the player's advantage.
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
    /** Evidence based on elemental type advantages. */
    data class TypeAdvantage(val advantage: TacticalAdvantage) : TacticalEvidence()
    /** Evidence based on a move that was actually observed. */
    data class ObservedMove(val moveName: String, val isFast: Boolean) : TacticalEvidence()
    /** Evidence based on current energy estimation. */
    data class EnergyLead(val isLeading: Boolean) : TacticalEvidence()
}

/**
 * High-level tactical actions recommended to the player.
 */
enum class TacticalAction {
    UNKNOWN,
    STAY_AND_FIGHT,
    SWITCH_NOW,
    FARM_ENERGY,
    SHIELD_LIKELY_REQUIRED,
    REGISTER_SPECIMEN,
    SELECT_SPECIES_MANUALLY
}

/**
 * Priority levels for tactical guidance.
 */
enum class TacticalPriority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Semantic assessments of combat advantage or threat.
 */
enum class TacticalAdvantage {
    NEUTRAL,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Presentation of both teams in the current engagement.
 */
data class TeamPresentation(
    val opponent: OpponentTeamPresentation = OpponentTeamPresentation(),
    val player: PlayerTeamPresentation = PlayerTeamPresentation()
)

/**
 * Status and identification of the opponent's team members.
 */
data class OpponentTeamPresentation(
    val activeSpecies: String? = null,
    val activeSpeciesId: Int? = null,
    val knownMoves: List<SemanticMove> = emptyList(),
    val shieldsUsed: Int = 0,
    val remainingPokemon: Int? = null,
    val members: List<EnemyMemberPresentation> = emptyList()
)

/**
 * Status of a single Pokémon in the opponent's team.
 */
data class EnemyMemberPresentation(
    val species: String,
    val speciesId: Int?,
    val isAlive: Boolean,
    val isActive: Boolean,
    val energyLevel: Float // 0.0 to 1.0
)

/**
 * A Pokémon move and its effectiveness in the current matchup context.
 */
data class SemanticMove(
    val name: String,
    val type: PokemonType,
    val isFast: Boolean,
    val effectiveness: MoveEffectiveness
)

/**
 * Simplified effectiveness categories for UI display.
 */
enum class MoveEffectiveness {
    SUPER_EFFECTIVE,
    NEUTRAL,
    NOT_VERY_EFFECTIVE,
    IMMUNE,
    UNKNOWN
}

/**
 * Status of the player's active Pokémon and resources.
 */
data class PlayerTeamPresentation(
    val activeSpecies: String? = null,
    val shieldsUsed: Int = 0
)

/**
 * Status of the Battle Timeline during an engagement.
 */
data class TimelinePresentation(
    val status: TimelineStatus = TimelineStatus.IDLE,
    val eventCount: Int = 0,
    val events: List<SemanticTimelineEvent> = emptyList()
)

/**
 * A battle event reduced to the information required by presentation surfaces.
 */
data class SemanticTimelineEvent(
    val type: SemanticTimelineEventType,
    val actor: SemanticBattleActor,
    val pokemonId: Int? = null,
    val value: Int? = null,
    val message: String? = null,
    val timestamp: Long
)

/**
 * Battle-event categories exposed to the Presentation Layer.
 */
enum class SemanticTimelineEventType {
    BATTLE_STARTED,
    BATTLE_ENDED,
    POKEMON_IDENTIFIED,
    POKEMON_SWITCHED,
    POKEMON_FAINTED,
    CHARGED_MOVE_THROWN,
    SHIELD_USED,
    ENERGY_UPDATED
}

/**
 * Battle participants exposed to the Presentation Layer.
 */
enum class SemanticBattleActor {
    PLAYER,
    ENEMY,
    SYSTEM
}

/**
 * Connectivity and recording status of the timeline.
 */
enum class TimelineStatus {
    /** Timeline is inactive. */
    IDLE,
    /** Actively recording battle events. */
    RECORDING,
    /** Successfully synchronized with a partner or archive. */
    SYNCHRONIZED
}
