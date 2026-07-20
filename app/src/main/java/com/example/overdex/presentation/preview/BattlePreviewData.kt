package com.example.overdex.presentation.preview

import com.example.overdex.model.PokemonType
import com.example.overdex.presentation.*

/**
 * Provides demo PresentationState instances for the Battle Preview Mode.
 */
object BattlePreviewData {
    
    /**
     * Complete battle observation for a high-threat opponent.
     */
    fun mewtwoDemo(): PresentationState {
        return PresentationState(
            observation = ObservationPresentation(
                focus = ObservationFocus.COMPLETE
            ),
            team = TeamPresentation(
                opponent = OpponentTeamPresentation(
                    activeSpecies = "Mewtwo",
                    knownMoves = listOf(
                        SemanticMove("Psycho Cut", PokemonType.PSYCHIC, isFast = true, effectiveness = MoveEffectiveness.NEUTRAL),
                        SemanticMove("Psystrike", PokemonType.PSYCHIC, isFast = false, effectiveness = MoveEffectiveness.NEUTRAL),
                        SemanticMove("Shadow Ball", PokemonType.GHOST, isFast = false, effectiveness = MoveEffectiveness.NEUTRAL)
                    )
                )
            ),
            tactical = TacticalPresentation(
                primaryGuidance = TacticalAction.SHIELD_LIKELY_REQUIRED,
                shieldRequired = true
            )
        )
    }

    /**
     * Initial observation phase where species is known but moves are still being identified.
     */
    fun missingMovesDemo(): PresentationState {
        return PresentationState(
            observation = ObservationPresentation(
                focus = ObservationFocus.FAST_MOVE,
                activity = ObservationActivity.RECOGNIZING_FAST_MOVE,
                status = ObservationIndicator.SEARCHING
            ),
            team = TeamPresentation(
                opponent = OpponentTeamPresentation(
                    activeSpecies = "Dialga",
                    knownMoves = emptyList()
                )
            ),
            tactical = TacticalPresentation(
                primaryGuidance = TacticalAction.STAY_AND_FIGHT
            )
        )
    }

    /**
     * High-confidence observation of a Shadow Pokemon, requiring aggressive tactical shifts.
     */
    fun shadowDemo(): PresentationState {
        return PresentationState(
            observation = ObservationPresentation(
                focus = ObservationFocus.COMPLETE
            ),
            team = TeamPresentation(
                opponent = OpponentTeamPresentation(
                    activeSpecies = "Shadow Mewtwo",
                    knownMoves = listOf(
                        SemanticMove("Confusion", PokemonType.PSYCHIC, isFast = true, effectiveness = MoveEffectiveness.SUPER_EFFECTIVE),
                        SemanticMove("Frustration", PokemonType.NORMAL, isFast = false, effectiveness = MoveEffectiveness.NEUTRAL)
                    )
                )
            ),
            tactical = TacticalPresentation(
                primaryGuidance = TacticalAction.SWITCH_NOW,
                urgency = TacticalPriority.HIGH,
                threat = TacticalAdvantage.HIGH
            )
        )
    }

    /**
     * Observation of an opponent that has fainted, transitioning instrument to idle or next target.
     */
    fun faintedDemo(): PresentationState {
        return PresentationState(
            observation = ObservationPresentation(
                focus = ObservationFocus.IDLE,
                status = ObservationIndicator.OFF
            ),
            team = TeamPresentation(
                opponent = OpponentTeamPresentation(
                    activeSpecies = "Snorlax",
                    remainingPokemon = 2,
                    members = listOf(
                        EnemyMemberPresentation("Snorlax", null, isAlive = false, isActive = true, energyLevel = 0f)
                    )
                )
            ),
            tactical = TacticalPresentation(
                primaryGuidance = TacticalAction.FARM_ENERGY
            )
        )
    }
}
