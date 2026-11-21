package com.example.overdex.battle.replay

import com.example.overdex.model.Move
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor

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
    private fun sameName(a: String, b: String): Boolean {
        if (a.filter(Char::isLetterOrDigit).equals(b.filter(Char::isLetterOrDigit), true)) return true
        // A source without a form label does not assert a particular form or size.
        // Two explicitly different forms must still remain distinct.
        return ('(' !in a || '(' !in b) &&
            TeamRosterSpeciesAttributor.speciesKey(a) == TeamRosterSpeciesAttributor.speciesKey(b)
    }

    private fun reviewed(link: ReplayFinishingFastAssociation): Boolean = entries.any { faint ->
        if (faint.kind != ReplayCorrectionKind.FAINT || faint.side.name != link.recipientSide ||
            !sameName(faint.speciesName, link.recipientSpeciesName)) return@any false
        val move = creditedMove(faint) ?: return@any false
        val impact = at(move) ?: return@any false
        move.side.name == link.attackingSide && sameName(move.speciesName, link.attackerSpeciesName) &&
            sameName(move.moveName.orEmpty(), link.moveName.orEmpty()) && kotlin.math.abs(impact - link.impactAtNanos) <= REVIEWED_KO_WINDOW
    }

    private fun reviewedColor(side: String, name: String?, impact: Long, scene: ReplayScene): Boolean = entries.any { faint ->
        val move = creditedMove(faint) ?: return@any false
        val time = at(move) ?: return@any false
        val actor = if (move.side == ReplayCorrectionSide.PLAYER) scene.player else scene.opponent
        val recipient = if (faint.side == ReplayCorrectionSide.PLAYER) scene.player else scene.opponent
        move.side.name == side && sameName(move.moveName.orEmpty(), name.orEmpty()) &&
            actor?.let { sameName(it.speciesName, move.speciesName) } == true &&
            recipient?.let { sameName(it.speciesName, faint.speciesName) } == true &&
            kotlin.math.abs(time - impact) <= DUPLICATE_WINDOW
    }

    /** Only an explicit, internally consistent link conveys trainer-asserted KO credit. */
    fun creditedMove(faint: ReplayTrainerCorrection): ReplayTrainerCorrection? {
        if (faint.kind != ReplayCorrectionKind.FAINT) return null
        val move = entries.singleOrNull { it.id == faint.finishingMoveId } ?: return null
        return move.takeIf { it.kind.isMove && it.side != faint.side &&
            it.matchOffsetNanos <= faint.matchOffsetNanos }
    }

    fun markers(): List<ReplayEventMarker> = entries.mapNotNull { entry ->
        val time = at(entry) ?: return@mapNotNull null
        val kind = when (entry.kind) {
            ReplayCorrectionKind.FAST_MOVE -> ReplayEventMarkerKind.FAST_MOVE
            ReplayCorrectionKind.CHARGED_MOVE -> ReplayEventMarkerKind.CHARGED_MOVE
            ReplayCorrectionKind.FAINT -> ReplayEventMarkerKind.FAINT
            ReplayCorrectionKind.CONCESSION -> ReplayEventMarkerKind.CONCESSION
        }
        val credited = creditedMove(entry)
        val label = if (entry.kind == ReplayCorrectionKind.CONCESSION) {
            "${entry.side.concessionLabel} · Trainer entry by ${entry.authorName}"
        } else {
            "Trainer entry · ${entry.speciesName} · ${entry.moveName ?: "faint"} · ${entry.authorName}" +
                (credited?.let { " · KO by ${it.moveName}" } ?: "")
        }
        ReplayEventMarker("trainer:${entry.id}", kind, time, entry.side.name, label,
            faintTiming = if (entry.kind == ReplayCorrectionKind.FAINT) ReplayFaintTiming.TrainerMark(time) else null,
            causeSide = credited?.side?.name)
    }

    fun soundEvents(recorded: List<ReplayAttackSoundEvent>, sceneAt: (Long) -> ReplayScene): List<ReplayAttackSoundEvent> {
        val retained = recorded.filterNot { event ->
            event.finishingAssociation?.let(::reviewed) == true ||
                ("MIXED_AUDIO_COMPLETE_BAR_COLOR_CHANGE" in event.evidenceKinds && event.impactAtNanos?.let {
                    reviewedColor(event.side, event.moveName, it, sceneAt(it))
                } == true)
        }
        val additional = entries.mapNotNull { entry ->
            if (!entry.kind.isMove) return@mapNotNull null
            val impact = at(entry) ?: return@mapNotNull null
            val start = (impact - VISUAL_NANOS).coerceAtLeast(matchStartNanos)
            val scene = sceneAt(impact)
            val actor = if (entry.side == ReplayCorrectionSide.PLAYER) scene.player else scene.opponent
            if (actor == null || !sameName(actor.speciesName, entry.speciesName) || actor.identityEstablishedAtNanos > impact)
                return@mapNotNull null
            val charged = entry.kind == ReplayCorrectionKind.CHARGED_MOVE
            if (!charged && scene.fastMoveActions.any { it.finishingAssociation?.let(::reviewed) != true &&
                    !("MIXED_AUDIO_COMPLETE_BAR_COLOR_CHANGE" in it.evidenceKinds && reviewedColor(it.side, it.moveName, it.impactAtNanos, scene)) && it.side == entry.side.name &&
                    kotlin.math.abs(it.impactAtNanos - impact) <= DUPLICATE_WINDOW &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            if (charged && scene.chargedMoveActions.any { it.side == entry.side.name &&
                    (it.moveName == null || sameName(it.moveName, entry.moveName.orEmpty())) }) return@mapNotNull null
            if (retained.any { it.side == entry.side.name && it.charged == charged &&
                    kotlin.math.abs(it.atNanos - start) <= DUPLICATE_WINDOW && sameName(it.moveName, entry.moveName.orEmpty()) })
                return@mapNotNull null
            ReplayAttackSoundEvent(start, entry.moveName!!, charged, entry.side.name)
        }
        return (retained + additional).sortedBy { it.atNanos }
    }

    fun apply(scene: ReplayScene, cursor: Long): ReplayScene {
        val retainedFast = scene.fastMoveActions.filterNot { action ->
            action.finishingAssociation?.let(::reviewed) == true ||
                ("MIXED_AUDIO_COMPLETE_BAR_COLOR_CHANGE" in action.evidenceKinds &&
                    reviewedColor(action.side, action.moveName, action.impactAtNanos, scene))
        }
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
            if (!entry.kind.isMove) return@mapNotNull null
            val impact = at(entry) ?: return@mapNotNull null
            val start = (impact - VISUAL_NANOS).coerceAtLeast(matchStartNanos)
            if (cursor < start || cursor > impact + HOLD_NANOS) return@mapNotNull null
            val combatant = actor(entry.side) ?: return@mapNotNull null
            if (!sameName(combatant.speciesName, entry.speciesName) || combatant.identityEstablishedAtNanos > impact)
                return@mapNotNull null
            val isFast = entry.kind == ReplayCorrectionKind.FAST_MOVE
            val scoped = moves.entries.firstOrNull { sameName(it.key, entry.speciesName) }?.value
                ?.firstOrNull { it.isFast == isFast && sameName(it.name, entry.moveName.orEmpty()) }
            // The trainer may supply a valid move absent from the species' current
            // catalogue. A unique type from the match's other references can still
            // color the icon; it does not establish legality or create an attack.
            val global = moves.values.flatten().filter { it.isFast == isFast && sameName(it.name, entry.moveName.orEmpty()) }
            val move = scoped ?: global.takeIf { it.map(Move::type).distinct().size == 1 }?.firstOrNull()
            val duration = (impact - start).coerceAtLeast(1)
            val progress = ((cursor - start).toFloat() / duration).coerceIn(0f, 1f)
            Triple(entry, move, progress)
        }
        val fast = additions.filter { it.first.kind == ReplayCorrectionKind.FAST_MOVE }.mapNotNull { (entry, move, progress) ->
            val impact = at(entry)!!
            if (retainedFast.any { it.side == entry.side.name &&
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
        val concession = entries.asSequence().filter { it.kind == ReplayCorrectionKind.CONCESSION }
            .filter { at(it)?.let { time -> time <= cursor } == true }
            .map { it.side }.distinct().singleOrNull()
        return scene.copy(player = player, opponent = opponent,
            playerHp = if (player?.knockoutBasis == "TRAINER_ENTRY") ReplayHpState(0f) else scene.playerHp,
            opponentHp = if (opponent?.knockoutBasis == "TRAINER_ENTRY") ReplayHpState(0f) else scene.opponentHp,
            fastMoveActions = retainedFast + fast, chargedMoveActions = scene.chargedMoveActions + charged,
            trainerConcession = concession)
    }

    private companion object { const val VISUAL_NANOS = 700_000_000L; const val HOLD_NANOS = 100_000_000L; const val DUPLICATE_WINDOW = 450_000_000L; const val REVIEWED_KO_WINDOW = 1_500_000_000L }
}
