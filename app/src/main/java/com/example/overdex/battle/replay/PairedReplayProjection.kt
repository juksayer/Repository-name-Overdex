package com.example.overdex.battle.replay

/** Applies supplemental observations only to a replay scene, never to either ledger or KO inference. */
internal class PairedReplayProjection(
    private val primaryMatchId: String,
    private val matchStartNanos: Long,
    private val paired: PairedReplayEvidence,
    private val chargedTypes: Map<Pair<String, String>, com.example.overdex.model.PokemonType> = emptyMap(),
) {
    fun apply(scene: ReplayScene, cursor: Long): ReplayScene {
        val extra = paired.events.filter { !it.ambiguous && it.observations.size == 1 }
            .map { it.observations.single() }.filter { it.archiveId != primaryMatchId }
        fun actor(side: String) = if (side == "PLAYER") scene.player else scene.opponent
        fun faint(side: String, combatant: ReplayCombatant?): ReplayCombatant? {
            combatant ?: return null
            if (combatant.isFainted) return combatant
            val observation = extra.filter { it.kind == PairedReplayObservation.Kind.FAINT && it.side == side &&
                it.species?.equals(combatant.speciesName, ignoreCase = true) == true }
                .filter { matchStartNanos + it.offsetNanos in combatant.identityEstablishedAtNanos..cursor }
                .minByOrNull { it.offsetNanos } ?: return combatant
            val at = matchStartNanos + observation.offsetNanos
            return combatant.copy(isFainted = true, faintedAtNanos = at, knockoutPresentationAtNanos = at,
                knockoutBasis = "PAIRED_RECORD", knockoutSourceArticleIds = listOf(observation.article.articleId),
                finishingChargeAssociation = null, finishingFastMoveAssociation = null, trainerFinishingMove = null)
        }
        val supplemental = extra.filter { it.kind == PairedReplayObservation.Kind.FAST_MOVE }.mapNotNull { observation ->
            val impact = matchStartNanos + observation.offsetNanos
            val start = impact - DURATION
            if (cursor !in start..impact) return@mapNotNull null
            val combatant = actor(observation.side) ?: return@mapNotNull null
            if (combatant.isFainted || observation.species?.equals(combatant.speciesName, true) != true ||
                impact < combatant.identityEstablishedAtNanos) return@mapNotNull null
            // A primary action nearby may be the same attack. Keep its source
            // evidence in the archive and avoid drawing a second projectile.
            if (scene.fastMoveActions.any { it.side == observation.side &&
                    kotlin.math.abs(it.impactAtNanos - impact) <= DUPLICATE_WINDOW }) return@mapNotNull null
            ReplayFastMoveAction(observation.side, null, null, start,
                ((cursor - start).toFloat() / DURATION).coerceIn(0f, 1f),
                evidenceKinds = setOf("PAIRED_RECORD"), durationNanos = DURATION, impactAtNanos = impact,
                sourceArticleIds = setOf(observation.article.articleId), observedAtNanos = setOf(impact))
        }
        val charged = extra.filter { it.kind == PairedReplayObservation.Kind.CHARGED_MOVE }.mapNotNull { observation ->
            val impact = matchStartNanos + observation.offsetNanos
            val start = impact - CHARGE_DURATION
            if (cursor !in start..impact) return@mapNotNull null
            val combatant = actor(observation.side) ?: return@mapNotNull null
            if (combatant.isFainted || observation.species?.equals(combatant.speciesName, true) != true ||
                impact < combatant.identityEstablishedAtNanos || scene.chargedMoveActions.any { it.side == observation.side }) return@mapNotNull null
            ReplayChargedMoveAction(observation.side, observation.moveName, start,
                ((cursor - start).toFloat() / CHARGE_DURATION).coerceIn(0f, 1f), evidenceKinds = setOf("PAIRED_RECORD"),
                type = chargedTypes[observation.species.orEmpty().uppercase() to observation.moveName.orEmpty().uppercase()])
        }
        val player = faint("PLAYER", scene.player)
        val opponent = faint("OPPONENT", scene.opponent)
        return scene.copy(player = player, opponent = opponent,
            playerHp = if (player?.knockoutBasis == "PAIRED_RECORD") ReplayHpState(0f) else scene.playerHp,
            opponentHp = if (opponent?.knockoutBasis == "PAIRED_RECORD") ReplayHpState(0f) else scene.opponentHp,
            fastMoveActions = scene.fastMoveActions + supplemental, chargedMoveActions = scene.chargedMoveActions + charged)
    }

    private companion object {
        const val DURATION = 450_000_000L
        const val CHARGE_DURATION = 700_000_000L
        const val DUPLICATE_WINDOW = 1_200_000_000L
    }
}
