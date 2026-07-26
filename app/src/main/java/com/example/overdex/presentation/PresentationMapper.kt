package com.example.overdex.presentation

import com.example.overdex.BattleMemory
import com.example.overdex.data.observation.ObservationStage
import com.example.overdex.data.observation.PipelineStatus
import com.example.overdex.model.*
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.model.observation.SessionPhase

/**
 * A pure transformation utility that maps domain models into the unified 
 * [PresentationState].
 */
object PresentationMapper {

    /**
     * Maps heterogeneous domain models into a single, unified [PresentationState].
     * 
     * @param instrumentState The high-level hardware/operating state.
     * @param pipelineStatus Status from the active observation pipeline.
     * @param battleMemory Snapshot of the current battle's remembered facts.
     * @param matchup Assessment of the active 1v1 combat matchup.
     * @param decision Tactical recommendations from the decision engine.
     */
    fun map(
        instrumentState: ObservationSessionState,
        pipelineStatus: PipelineStatus?,
        battleMemory: BattleMemory?,
        matchup: MatchupAnalysis?,
        decision: DecisionAnalysis?
    ): PresentationState {
        return PresentationState(
            instrument = mapInstrumentLifecycle(instrumentState),
            observation = mapObservation(pipelineStatus),
            tactical = mapTactical(decision, matchup),
            team = mapTeam(battleMemory, matchup),
            timeline = mapTimeline(battleMemory)
        )
    }

    private fun mapInstrumentLifecycle(
        state: ObservationSessionState
    ): InstrumentLifecycle {
        return when (state) {
            ObservationSessionState.IDLE -> InstrumentLifecycle.IDLE
            ObservationSessionState.OBSERVING -> InstrumentLifecycle.DEPLOYED
            ObservationSessionState.CALIBRATING -> InstrumentLifecycle.CALIBRATING
            ObservationSessionState.SERVICE_ACTIVE -> InstrumentLifecycle.SERVICE_ACTIVE
        }
    }

    private fun mapObservation(status: PipelineStatus?): ObservationPresentation {
        if (status == null) return ObservationPresentation()

        val progress = status.session?.evaluateProgress()
        
        return ObservationPresentation(
            focus = mapObservationFocus(status.currentStage),
            activity = mapObservationActivity(status.currentStage),
            status = mapObservationIndicator(status),
            progress = progress?.percentComplete ?: 0f,
            captureId = status.captureId,
            missingRequirements = progress?.missingFields?.mapNotNull { mapObservationRequirement(it) } ?: emptyList(),
            estimate = ObservationEstimate(
                remainingObservations = progress?.missingFields?.size ?: 0
            )
        )
    }

    private fun mapObservationFocus(stage: ObservationStage): ObservationFocus {
        return when (stage) {
            ObservationStage.LocatingAnchors -> ObservationFocus.ANCHORS
            ObservationStage.Species -> ObservationFocus.SPECIES
            ObservationStage.CombatPower -> ObservationFocus.COMBAT_POWER
            ObservationStage.ShadowStatus -> ObservationFocus.SHADOW
            ObservationStage.FastMove -> ObservationFocus.FAST_MOVE
            ObservationStage.ChargedMoveA -> ObservationFocus.CHARGED_MOVE_A
            ObservationStage.ChargedMoveB -> ObservationFocus.CHARGED_MOVE_B
            ObservationStage.Complete -> ObservationFocus.COMPLETE
        }
    }

    private fun mapObservationActivity(stage: ObservationStage): ObservationActivity {
        return when (stage) {
            ObservationStage.LocatingAnchors -> ObservationActivity.LOCATING_ANCHORS
            ObservationStage.Species -> ObservationActivity.IDENTIFYING_SPECIES
            ObservationStage.CombatPower -> ObservationActivity.MEASURING_COMBAT_POWER
            ObservationStage.ShadowStatus -> ObservationActivity.DETECTING_SHADOW_STATUS
            ObservationStage.FastMove -> ObservationActivity.RECOGNIZING_FAST_MOVE
            ObservationStage.ChargedMoveA -> ObservationActivity.RECOGNIZING_CHARGED_MOVE_A
            ObservationStage.ChargedMoveB -> ObservationActivity.RECOGNIZING_CHARGED_MOVE_B
            ObservationStage.Complete -> ObservationActivity.FINALIZING_SESSION
        }
    }

    private fun mapObservationRequirement(fieldId: String): ObservationRequirement? {
        return when (fieldId) {
            "SpeciesName" -> ObservationRequirement.SPECIES
            "CombatPower" -> ObservationRequirement.COMBAT_POWER
            "FastMoveRow", "SummaryFastMove" -> ObservationRequirement.FAST_MOVE
            "ChargedMoveRowA" -> ObservationRequirement.CHARGED_MOVE_A
            "ChargedMoveRowB" -> ObservationRequirement.CHARGED_MOVE_B
            else -> null
        }
    }

    private fun mapObservationIndicator(status: PipelineStatus): ObservationIndicator {
        val phase = status.session?.state ?: SessionPhase.CREATED
        if (phase == SessionPhase.CREATED) return ObservationIndicator.OFF
        if (phase == SessionPhase.CANCELLED) return ObservationIndicator.ERROR

        val stage = status.currentStage
        if (stage == ObservationStage.LocatingAnchors) return ObservationIndicator.ALIGNING
        if (stage == ObservationStage.Complete) return ObservationIndicator.CONFIRMED

        val results = status.results[stage.label]
        val maxConfidence = results?.maxOfOrNull { it.confidence.score } ?: 0f

        return when {
            maxConfidence >= 0.8f -> ObservationIndicator.CONFIRMED
            maxConfidence > 0f -> ObservationIndicator.SEARCHING
            else -> ObservationIndicator.ALIGNING
        }
    }

    private fun mapTactical(
        decision: DecisionAnalysis?,
        matchup: MatchupAnalysis?
    ): TacticalPresentation {
        if (decision == null && matchup == null) return TacticalPresentation()

        return TacticalPresentation(
            primaryGuidance = mapTacticalAction(decision?.recommendedAction),
            urgency = mapTacticalPriority(decision?.actionPriority),
            threat = mapTacticalAdvantage(matchup?.enemyThreatLevel),
            advantage = mapTacticalAdvantage(matchup?.playerAdvantage),
            confidence = decision?.confidenceInDecision ?: 0f,
            shieldRequired = decision?.shieldRecommended ?: false,
            evidence = deriveTacticalEvidence(decision, matchup)
        )
    }

    private fun deriveTacticalEvidence(
        decision: DecisionAnalysis?,
        matchup: MatchupAnalysis?
    ): List<TacticalEvidence> {
        val evidence = mutableListOf<TacticalEvidence>()
        
        matchup?.let {
            evidence.add(TacticalEvidence.TypeAdvantage(mapTacticalAdvantage(it.playerAdvantage)))
            it.enemyFastMoveMatchup?.let { move ->
                evidence.add(TacticalEvidence.ObservedMove(move.moveName, isFast = true))
            }
        }
        
        decision?.let {
            if (it.farmOpportunityAvailable) {
                evidence.add(TacticalEvidence.EnergyLead(isLeading = true))
            }
        }
        
        return evidence
    }

    private fun mapTacticalAction(action: RecommendedAction?): TacticalAction {
        return when (action) {
            RecommendedAction.STAY_AND_FIGHT -> TacticalAction.STAY_AND_FIGHT
            RecommendedAction.SWITCH_NOW -> TacticalAction.SWITCH_NOW
            RecommendedAction.FARM_ENERGY -> TacticalAction.FARM_ENERGY
            RecommendedAction.SHIELD_LIKELY_REQUIRED -> TacticalAction.SHIELD_LIKELY_REQUIRED
            RecommendedAction.UNKNOWN -> TacticalAction.UNKNOWN
            null -> TacticalAction.UNKNOWN
        }
    }

    private fun mapTacticalPriority(priority: StrategicPriority?): TacticalPriority {
        return when (priority) {
            StrategicPriority.CRITICAL -> TacticalPriority.CRITICAL
            StrategicPriority.HIGH -> TacticalPriority.HIGH
            StrategicPriority.MEDIUM -> TacticalPriority.MEDIUM
            StrategicPriority.LOW -> TacticalPriority.LOW
            StrategicPriority.NONE -> TacticalPriority.NONE
            null -> TacticalPriority.NONE
        }
    }

    private fun mapTacticalAdvantage(advantage: AdvantageLevel?): TacticalAdvantage {
        return when (advantage) {
            AdvantageLevel.HIGH -> TacticalAdvantage.HIGH
            AdvantageLevel.MEDIUM -> TacticalAdvantage.MEDIUM
            AdvantageLevel.LOW -> TacticalAdvantage.LOW
            AdvantageLevel.NEUTRAL -> TacticalAdvantage.NEUTRAL
            null -> TacticalAdvantage.NEUTRAL
        }
    }

    private fun mapTeam(battleMemory: BattleMemory?, matchup: MatchupAnalysis?): TeamPresentation {
        if (battleMemory == null) return TeamPresentation()

        val activeEnemy = battleMemory.enemyTeam.find { it.isActive }
        
        return TeamPresentation(
            opponent = OpponentTeamPresentation(
                activeSpecies = activeEnemy?.species,
                knownMoves = deriveSemanticMoves(matchup),
                shieldsUsed = battleMemory.enemyShieldsUsed,
                remainingPokemon = battleMemory.enemyRemainingPokemon,
                members = battleMemory.enemyTeam.map { 
                    EnemyMemberPresentation(
                        species = it.species,
                        speciesId = null, 
                        isAlive = it.alive,
                        isActive = it.isActive,
                        energyLevel = (it.estimatedEnergy.coerceIn(0, 100)) / 100f
                    )
                }
            ),
            player = PlayerTeamPresentation(
                activeSpecies = battleMemory.playerActivePokemon,
                shieldsUsed = battleMemory.playerShieldsUsed
            )
        )
    }

    private fun deriveSemanticMoves(matchup: MatchupAnalysis?): List<SemanticMove> {
        if (matchup == null) return emptyList()
        
        val moves = mutableListOf<SemanticMove>()
        
        matchup.enemyFastMoveMatchup?.let {
            moves.add(SemanticMove(it.moveName, it.type, isFast = true, mapEffectiveness(it.effectiveness)))
        }
        
        matchup.enemyChargedMoveMatchups.forEach {
            moves.add(SemanticMove(it.moveName, it.type, isFast = false, mapEffectiveness(it.effectiveness)))
        }
        
        return moves
    }

    private fun mapEffectiveness(effectiveness: Effectiveness): MoveEffectiveness {
        return when (effectiveness) {
            Effectiveness.SUPER_EFFECTIVE -> MoveEffectiveness.SUPER_EFFECTIVE
            Effectiveness.NEUTRAL -> MoveEffectiveness.NEUTRAL
            Effectiveness.NOT_VERY_EFFECTIVE -> MoveEffectiveness.NOT_VERY_EFFECTIVE
            Effectiveness.IMMUNE -> MoveEffectiveness.IMMUNE
            Effectiveness.UNKNOWN -> MoveEffectiveness.UNKNOWN
        }
    }

    private fun mapTimeline(battleMemory: BattleMemory?): TimelinePresentation {
        if (battleMemory == null) return TimelinePresentation()

        val events = battleMemory.timeline.events.map { event ->
            SemanticTimelineEvent(
                type = when (event.type) {
                    BattleEventType.BATTLE_STARTED -> SemanticTimelineEventType.BATTLE_STARTED
                    BattleEventType.BATTLE_ENDED -> SemanticTimelineEventType.BATTLE_ENDED
                    BattleEventType.POKEMON_IDENTIFIED -> SemanticTimelineEventType.POKEMON_IDENTIFIED
                    BattleEventType.POKEMON_SWITCHED -> SemanticTimelineEventType.POKEMON_SWITCHED
                    BattleEventType.POKEMON_FAINTED -> SemanticTimelineEventType.POKEMON_FAINTED
                    BattleEventType.CHARGED_MOVE_THROWN -> SemanticTimelineEventType.CHARGED_MOVE_THROWN
                    BattleEventType.SHIELD_USED -> SemanticTimelineEventType.SHIELD_USED
                    BattleEventType.ENERGY_UPDATED -> SemanticTimelineEventType.ENERGY_UPDATED
                },
                actor = when (event.actor) {
                    BattleActor.PLAYER -> SemanticBattleActor.PLAYER
                    BattleActor.ENEMY -> SemanticBattleActor.ENEMY
                    BattleActor.SYSTEM -> SemanticBattleActor.SYSTEM
                },
                pokemonId = event.pokemonId,
                message = event.message,
                value = event.value,
                timestamp = event.timestamp
            )
        }

        return TimelinePresentation(
            status = if (battleMemory.startTime > 0) {
                TimelineStatus.RECORDING
            } else {
                TimelineStatus.IDLE
            },
            eventCount = events.size,
            events = events
        )
    }
}
