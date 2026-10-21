package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/**
 * A presentation schedule, not a collection of witnessed glyphs. Repeated/missed
 * readings never stretch or skip a beat. The archive remains the authority for
 * what was actually observed; evidence IDs explain this reconstruction.
 */
data class ReplayCountdown(
    val goAtNanos: Long,
    val timingBasis: ReplayCountdownTiming,
    val evidenceArticleIds: List<String>,
    val observations: List<ReplayCountdownObservation>,
) {
    val startNanos: Long get() = goAtNanos - 3 * BEAT_NANOS
    val endNanos: Long get() = goAtNanos + BEAT_NANOS

    fun frameAt(atNanos: Long): ReplayCountdownFrame? {
        if (atNanos < startNanos || atNanos >= endNanos) return null
        val index = ((atNanos - startNanos) / BEAT_NANOS).toInt()
        val glyph = GLYPHS[index]
        return ReplayCountdownFrame(
            glyph = glyph,
            displayStartNanos = startNanos + index * BEAT_NANOS,
            timingBasis = timingBasis,
            observedArticleIds = observations.filter { it.glyph == glyph }.map { it.articleId },
            evidenceArticleIds = evidenceArticleIds,
        )
    }

    companion object {
        const val BEAT_NANOS = 1_000_000_000L
        private val GLYPHS = listOf("3", "2", "1", "GO")

        fun from(articles: List<ArchivedRealityArticle>): ReplayCountdown? {
            val timed = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
            val confirmation = timed.firstOrNull { it.payload is ArchivedMatchStarted }
                ?: timed.firstOrNull { (it.payload as? ArchivedCountdownGlyphWitnessed)?.glyph == "GO" }
                ?: timed.firstOrNull { confirmsCombat(it.payload) }
                ?: return null
            val confirmedAt = confirmation.monotonicTimeNanos!!
            // A restarted/ignored attempt must not supply a later battle's countdown.
            val attemptStart = timed.lastOrNull {
                it.monotonicTimeNanos!! <= confirmedAt &&
                    (it.payload is ArchivedObservationRestartedByUser || it.payload is ArchivedScreenIgnoredByUser)
            }?.monotonicTimeNanos ?: Long.MIN_VALUE
            val attempt = timed.filter { it.monotonicTimeNanos!! >= attemptStart }
            val recordedStart = attempt.firstOrNull { it.payload is ArchivedMatchStarted }
            val observedGo = attempt.firstOrNull {
                (it.payload as? ArchivedCountdownGlyphWitnessed)?.glyph == "GO"
            }
            val openingEnd = recordedStart?.monotonicTimeNanos ?: observedGo?.monotonicTimeNanos ?: confirmedAt
            val glyphArticles = attempt.filter {
                val glyph = (it.payload as? ArchivedCountdownGlyphWitnessed)?.glyph
                glyph in GLYPHS && it.monotonicTimeNanos!! <= openingEnd + BEAT_NANOS
            }
            // Prefer the numeral nearest GO, and its first reading. Seeing "3"
            // repeatedly is not evidence that the three-second phase restarted.
            val numeral = glyphArticles
                .filter { (it.payload as ArchivedCountdownGlyphWitnessed).glyph != "GO" }
                .distinctBy { (it.payload as ArchivedCountdownGlyphWitnessed).glyph }
                .minByOrNull { (it.payload as ArchivedCountdownGlyphWitnessed).glyph.toInt() }
            val timer = attempt.firstOrNull {
                it.payload is ArchivedSupportingMatchStart && it.monotonicTimeNanos!! <= confirmedAt
            }
            val firstBattlefieldAppearance = attempt.firstOrNull {
                it.monotonicTimeNanos!! <= confirmedAt &&
                    (it.payload is ArchivedActivePokemonSpeciesWitnessed ||
                        it.payload is ArchivedActivePokemonTypesWitnessed)
            }
            val anchor = recordedStart ?: observedGo ?: numeral ?: timer ?: firstBattlefieldAppearance
            val goAt = when {
                recordedStart != null -> recordedStart.monotonicTimeNanos!!
                observedGo != null -> observedGo.monotonicTimeNanos!!
                numeral != null -> numeral.monotonicTimeNanos!! +
                    (numeral.payload as ArchivedCountdownGlyphWitnessed).glyph.toLong() * BEAT_NANOS
                timer != null -> timer.monotonicTimeNanos!!
                // Later combat establishes that the countdown happened, but it
                // cannot date GO. Put a clearly labelled presentation lead-in at
                // the first recorded battlefield appearance, without retiming
                // any battle article or declaring an exact MatchStarted.
                else -> firstBattlefieldAppearance?.monotonicTimeNanos ?: confirmedAt
            }
            val basis = when {
                recordedStart != null -> ReplayCountdownTiming.RECORDED_MATCH_START
                observedGo != null -> ReplayCountdownTiming.OBSERVED_GO
                numeral != null -> ReplayCountdownTiming.ESTIMATED_FROM_NUMERAL
                timer != null -> ReplayCountdownTiming.ESTIMATED_FROM_TIMER
                else -> ReplayCountdownTiming.PRESENTATION_ONLY
            }
            return ReplayCountdown(
                goAtNanos = goAt,
                timingBasis = basis,
                evidenceArticleIds = (listOfNotNull(anchor?.articleId, confirmation.articleId) +
                    glyphArticles.map { it.articleId }).distinct(),
                observations = glyphArticles.map {
                    ReplayCountdownObservation(
                        (it.payload as ArchivedCountdownGlyphWitnessed).glyph,
                        it.monotonicTimeNanos!!,
                        it.articleId,
                    )
                },
            )
        }

        private fun confirmsCombat(payload: ArchivedTestimonyPayload): Boolean = when (payload) {
            ArchivedAttackIncoming, ArchivedGetReadyWitnessed, ArchivedChargeMoveUsedAnnounced -> true
            is ArchivedChargedMoveEnergySpent, is ArchivedFastMoveUseObserved, is ArchivedMatchEnded -> true
            is ArchivedActiveHpBarDamageTickMeasured -> payload.lostFraction > 0f
            else -> false
        }
    }
}

enum class ReplayCountdownTiming {
    RECORDED_MATCH_START, OBSERVED_GO, ESTIMATED_FROM_NUMERAL, ESTIMATED_FROM_TIMER, PRESENTATION_ONLY
}

data class ReplayCountdownObservation(val glyph: String, val atNanos: Long, val articleId: String)

data class ReplayCountdownFrame(
    val glyph: String,
    val displayStartNanos: Long,
    val timingBasis: ReplayCountdownTiming,
    val observedArticleIds: List<String>,
    val evidenceArticleIds: List<String>,
) {
    val lcdLabel: String get() = when (timingBasis) {
        ReplayCountdownTiming.PRESENTATION_ONLY -> "COUNTDOWN RECREATED · START TIME UNKNOWN"
        ReplayCountdownTiming.ESTIMATED_FROM_NUMERAL,
        ReplayCountdownTiming.ESTIMATED_FROM_TIMER -> "COUNTDOWN RECREATED · START ESTIMATED"
        else -> "COUNTDOWN RECREATED · EVEN CADENCE"
    }
}
