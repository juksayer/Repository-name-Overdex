package com.example.overdex.battle.replay

import com.example.overdex.model.Move

/** Presentation of separately retained trainer statements. Does not modify archive testimony. */
internal class ReplayTrainerProjection(
    private val matchId: String,
    private val matchStartNanos: Long,
    corrections: List<ReplayTrainerCorrection>,
    private val moves: Map<String, List<Move>> = emptyMap(),
) {
    private val entries = corrections.filter { entry ->
        runCatching { entry.validate(); entry.matchId == matchId }.getOrDefault(false)
    }.distinctBy { it.id }
    private fun at(entry: ReplayTrainerCorrection): Long? =
        runCatching { Math.addExact(matchStartNanos, entry.matchOffsetNanos) }.getOrNull()
    private fun sameName(a: String, b: String) = a.filter(Char::isLetterOrDigit).equals(b.filter(Char::isLetterOrDigit), true)

    /** Only an explicit, internally consistent link conveys trainer-asserted KO credit. */
    fun creditedMove(faint: ReplayTrainerCorrection): ReplayTrainerCorrection? {
        if (faint.kind != ReplayCorrectionKind.FAINT) return null
        val move = entries.singleOrNull { it.id == faint.finishingMoveId } ?: return null
        return move.takeIf { it.kind != ReplayCorrectionKind.FAINT && it.side != faint.side &&
            it.matchOffsetNanos <= faint.matchOffsetNanos }
    }

    fun soundEvents(recorded: List<ReplayAttackSoundEvent>, sceneAt: (Long) -> ReplayScene): List<ReplayAttackSoundEvent> {
        val additional = entries.mapNotNull { entry ->
            if (entry.kind == ReplayCorrectionKind.FAINT) return@mapNotNull null
            val impact = at(entry) ?: return@mapNotNull null
            val start = (impact - VISUAL_NANOS).coerceAtLeast(matchStartNanos)
            val scene = sceneAt(impact)
            val actor = if (entry.side == ReplayCorrectionSide.PLAYER) scene.player else scene.opponent
            if (actor == null || !sameName(actor.speciesName, entry.speciesName) || actor.identityEstablishedAtNanos > impact)
                return@mapNotNull null
            val charged = entry.kind == ReplayCorrectionKind.CHARGED_MOVE
            if (!charged && scene.fastMoveActions.any { it.side == entry.side.name &&
                    kotlin.math.abs(it.impactAtNanos - impact) <= DUPLICATE_WINDOW &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            if (charged && scene.chargedMoveActions.any { it.side == entry.side.name &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            if (recorded.any { it.side == entry.side.name && it.charged == charged &&
                    kotlin.math.abs(it.atNanos - start) <= DUPLICATE_WINDOW && sameName(it.moveName, entry.moveName.orEmpty()) })
                return@mapNotNull null
            ReplayAttackSoundEvent(start, entry.moveName!!, charged, entry.side.name)
        }
        return (recorded + additional).sortedBy { it.atNanos }
    }

    fun apply(scene: ReplayScene, cursor: Long): ReplayScene {
        fun actor(side: ReplayCorrectionSide) = if (side == ReplayCorrectionSide.PLAYER) scene.player else scene.opponent
        fun applyFaint(side: ReplayCorrectionSide, combatant: ReplayCombatant?): ReplayCombatant? {
            combatant ?: return null
            val entry = entries.filter { it.kind == ReplayCorrectionKind.FAINT && it.side == side &&
                sameName(it.speciesName, combatant.speciesName) }
                .mapNotNull { entry -> at(entry)?.let { entry to it } }
                .filter { (_, time) -> time in combatant.identityEstablishedAtNanos..cursor }
                .minByOrNull { it.second } ?: return combatant
            val (faint, time) = entry
            val link = creditedMove(faint)
            return combatant.copy(isFainted = true, faintedAtNanos = time,
                knockoutPresentationAtNanos = time, knockoutBasis = "TRAINER_ENTRY",
                knockoutSourceArticleIds = listOf("trainer:${faint.id}") + listOfNotNull(link?.let { "trainer:${it.id}" }),
                trainerFinishingMove = link?.moveName)
        }
        val additions = entries.mapNotNull { entry ->
            if (entry.kind == ReplayCorrectionKind.FAINT) return@mapNotNull null
            val impact = at(entry) ?: return@mapNotNull null
            val start = (impact - VISUAL_NANOS).coerceAtLeast(matchStartNanos)
            if (cursor < start || cursor > impact + HOLD_NANOS) return@mapNotNull null
            val combatant = actor(entry.side) ?: return@mapNotNull null
            if (!sameName(combatant.speciesName, entry.speciesName) || combatant.identityEstablishedAtNanos > impact)
                return@mapNotNull null
            val move = moves.entries.firstOrNull { sameName(it.key, entry.speciesName) }?.value
                ?.firstOrNull { sameName(it.name, entry.moveName.orEmpty()) }
            val duration = (impact - start).coerceAtLeast(1)
            val progress = ((cursor - start).toFloat() / duration).coerceIn(0f, 1f)
            Triple(entry, move, progress)
        }
        val fast = additions.filter { it.first.kind == ReplayCorrectionKind.FAST_MOVE }.mapNotNull { (entry, move, progress) ->
            val impact = at(entry)!!
            if (scene.fastMoveActions.any { it.side == entry.side.name &&
                    kotlin.math.abs(it.impactAtNanos - impact) <= DUPLICATE_WINDOW &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            val start = (impact - VISUAL_NANOS).coerceAtLeast(matchStartNanos)
            ReplayFastMoveAction(entry.side.name, entry.moveName, move?.type, start, progress,
                evidenceKinds = setOf("TRAINER_ENTRY"), durationNanos = (impact - start).coerceAtLeast(1),
                impactAtNanos = impact, sourceArticleIds = setOf("trainer:${entry.id}"), observedAtNanos = setOf(impact))
        }
        val charged = additions.filter { it.first.kind == ReplayCorrectionKind.CHARGED_MOVE }.mapNotNull { (entry, move, progress) ->
            if (scene.chargedMoveActions.any { it.side == entry.side.name &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            ReplayChargedMoveAction(entry.side.name, entry.moveName, at(entry)!! - VISUAL_NANOS, progress,
                evidenceKinds = setOf("TRAINER_ENTRY"), type = move?.type)
        }
        val player = applyFaint(ReplayCorrectionSide.PLAYER, scene.player)
        val opponent = applyFaint(ReplayCorrectionSide.OPPONENT, scene.opponent)
        return scene.copy(player = player, opponent = opponent,
            playerHp = if (player?.knockoutBasis == "TRAINER_ENTRY") ReplayHpState(0f) else scene.playerHp,
            opponentHp = if (opponent?.knockoutBasis == "TRAINER_ENTRY") ReplayHpState(0f) else scene.opponentHp,
            fastMoveActions = scene.fastMoveActions + fast, chargedMoveActions = scene.chargedMoveActions + charged)
    }

    private companion object { const val VISUAL_NANOS = 700_000_000L; const val HOLD_NANOS = 100_000_000L; const val DUPLICATE_WINDOW = 450_000_000L }
}
