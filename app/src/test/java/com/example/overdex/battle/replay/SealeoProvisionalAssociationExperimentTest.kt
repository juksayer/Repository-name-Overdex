package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test

/** Offline candidate ranking experiment. Never writes derived evidence to an archive or live state. */
class SealeoProvisionalAssociationExperimentTest {
    private fun archive(): MatchArchive = MatchArchiveSerializer.deserialize(GZIPInputStream(
        requireNotNull(javaClass.getResourceAsStream("/battle/2026-11-13-sealeo-finish/original-timeline.json.gz"))
    ).bufferedReader().use { it.readText() })

    private data class Candidate(val attackIds: Set<String>, val faintId: String,
                                 val redAt: Long, val emptyAt: Long, val limitations: Set<String>)

    private fun evaluate(a: MatchArchive): Candidate? {
        val visual = a.articles.singleOrNull { it.articleId == VISUAL } ?: return null
        val at = visual.monotonicTimeNanos ?: return null
        val model = MatchReplayModel(a)
        val attack = model.sceneAt(at).fastMoveActions.singleOrNull {
            it.side == "PLAYER" && it.moveName == "Incinerate" && VISUAL in it.sourceArticleIds &&
                SOUND in it.sourceArticleIds && FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT in it.evidenceKinds
        } ?: return null
        val faint = a.articles.singleOrNull { row ->
            (row.payload as? ArchivedActivePokemonFaintedWitnessed)?.let {
                it.side == "OPPONENT" && it.speciesName == "Sealeo" && it.basis == "VERIFIED_RED_TO_EMPTY_HP"
            } == true
        } ?: return null
        val end = faint.monotonicTimeNanos ?: return null
        if (end <= at || end - at > 3_000_000_000L) return null
        val red = a.articles.filter { row ->
            (row.payload as? ArchivedActiveHpBarMeasured)?.let {
                it.side == "OPPONENT" && it.fillColor == "RED" && it.filledFraction > 0f &&
                    row.monotonicTimeNanos?.let { t -> t in at until end } == true
            } == true
        }.maxByOrNull { it.monotonicTimeNanos!! } ?: return null
        val empty = a.articles.singleOrNull { row ->
            (row.payload as? ArchivedActiveHpBarMeasured)?.let {
                it.side == "OPPONENT" && it.fillColor == "EMPTY" && it.filledFraction == 0f &&
                    row.monotonicTimeNanos == end
            } == true
        } ?: return null
        // Every conflicting source claim remains a veto, including unscheduled moves.
        if (a.articles.any { row ->
            val t = row.monotonicTimeNanos ?: return@any false
            if (t !in (at + 1)..end) return@any false
            when (val p = row.payload) {
                is ArchivedFastMoveUseObserved -> p.attackingSide != "OPPONENT" || p.damagedSide != "PLAYER"
                is ArchivedActiveHpBarDamageTickMeasured -> p.damagedSide == "OPPONENT"
                is ArchivedFastMoveRecipientVisualArtifactMeasured -> p.damagedSide == "OPPONENT"
                is ArchivedChargedMoveEnergySpent, is ArchivedChargeMoveUsedAnnounced,
                is ArchivedGetReadyWitnessed, is ArchivedAttackIncoming,
                is ArchivedObservationRestartedByUser, is ArchivedObservationSessionStoppedByUser,
                is ArchivedScreenIgnoredByUser -> true
                is ArchivedActivePokemonSpeciesWitnessed -> p.side == "OPPONENT" && !p.speciesName.equals("Sealeo", true)
                else -> false
            }
        }) return null
        // Only an independently reidentified Sealeo followed by positive HP can
        // contradict its earlier faint. Unowned replacement HP is not Sealeo's.
        val laterSealeo = a.articles.filter { row ->
            (row.payload as? ArchivedActivePokemonSpeciesWitnessed)?.let {
                it.side == "OPPONENT" && it.speciesName.equals("Sealeo", true) &&
                    row.monotonicTimeNanos?.let { t -> t > end && t < end + 2_000_000_000L } == true
            } == true
        }.minByOrNull { it.monotonicTimeNanos!! }
        if (laterSealeo != null && a.articles.any { row ->
            val hp = row.payload as? ArchivedActiveHpBarMeasured ?: return@any false
            hp.side == "OPPONENT" && hp.filledFraction > 0f && row.monotonicTimeNanos?.let {
                it > laterSealeo.monotonicTimeNanos!! && it < end + 2_000_000_000L
            } == true
        }) return null
        val caveats = mutableSetOf("PROVISIONAL_ONLY", "UNKNOWN_EXACT_DAMAGE_TIME", "MISSING_FRAME_COVERAGE")
        if (a.articles.any { row -> row.monotonicTimeNanos?.let { it in (at + 1)..end } == true &&
                (row.payload as? ArchivedFastMoveSoundMeasured)?.let { it.audible && it.attackingSide == null } == true })
            caveats += "UNSIDED_AUDIO"
        return Candidate(attack.sourceArticleIds, faint.articleId, red.monotonicTimeNanos!!,
            empty.monotonicTimeNanos!!, caveats)
    }

    @Test fun `recorded source suggests Incinerate while missing data stays explicit`() {
        val original = archive(); val before = MatchArchiveSerializer.serialize(original)
        for (input in listOf(original, original.copy(articles = original.articles.reversed()))) {
            val candidate = requireNotNull(evaluate(input))
            assertTrue(candidate.attackIds.containsAll(setOf(VISUAL, SOUND)))
            assertEquals(487964151313826L, candidate.redAt)
            assertEquals(EMPTY, candidate.emptyAt)
            assertTrue(candidate.limitations.containsAll(setOf("PROVISIONAL_ONLY", "MISSING_FRAME_COVERAGE", "UNKNOWN_EXACT_DAMAGE_TIME", "UNSIDED_AUDIO")))
        }
        assertEquals(before, MatchArchiveSerializer.serialize(original))
    }

    @Test fun `competing player hit and missing independently verified faint prevent link`() {
        val original = archive(); val visual = original.articles.single { it.articleId == VISUAL }
        val conflict = visual.copy(articleId = "competing-hit", monotonicTimeNanos = EMPTY - 600_000_000L,
            payload = ArchivedFastMoveUseObserved("competing", "PLAYER", "OPPONENT", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST"))
        assertNull(evaluate(original.copy(articles = original.articles + conflict)))
        assertNull(evaluate(original.copy(articles = original.articles.filterNot {
            (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.let { f -> f.speciesName == "Sealeo" && f.basis == "VERIFIED_RED_TO_EMPTY_HP" } == true
        })))
    }

    @Test fun `missing key observations never become proof and long gaps stay provisional`() {
        val original = archive()
        for (id in listOf(VISUAL, SOUND)) {
            assertNull(evaluate(original.copy(articles = original.articles.filterNot { it.articleId == id })))
        }
        val noEmpty = original.copy(articles = original.articles.filterNot { row ->
            (row.payload as? ArchivedActiveHpBarMeasured)?.let { it.side == "OPPONENT" &&
                it.fillColor == "EMPTY" && row.monotonicTimeNanos == EMPTY } == true
        })
        assertNull(evaluate(noEmpty))
        assertTrue(requireNotNull(evaluate(original)).limitations.contains("MISSING_FRAME_COVERAGE"))
        val visual = original.articles.single { it.articleId == VISUAL }
        val stop = visual.copy(articleId = "capture-stop", monotonicTimeNanos = EMPTY - 100_000_000L,
            payload = ArchivedObservationSessionStoppedByUser)
        assertNull(evaluate(original.copy(articles = original.articles + stop)))
    }

    @Test fun `a separately observed recipient damage tick blocks nearest-event shortcut`() {
        val original = archive(); val visual = original.articles.single { it.articleId == VISUAL }
        val damage = visual.copy(articleId = "competing-damage", monotonicTimeNanos = EMPTY - 300_000_000L,
            payload = ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .03f, .01f, .02f))
        assertNull(evaluate(original.copy(articles = original.articles + damage)))
    }

    @Test fun `living switch and post-faint positive hp contradict attribution`() {
        val original = archive(); val visual = original.articles.single { it.articleId == VISUAL }
        val switch = visual.copy(articleId = "replacement", monotonicTimeNanos = EMPTY - 200_000_000L,
            payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134))
        assertNull(evaluate(original.copy(articles = original.articles + switch)))
        val red = original.articles.single { it.articleId.startsWith("68a22539-") }
        val identity = visual.copy(articleId = "later-sealeo", monotonicTimeNanos = EMPTY + 50_000_000L,
            payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364))
        val live = red.copy(articleId = "later-live", monotonicTimeNanos = EMPTY + 100_000_000L)
        // An unowned HP sample may belong to the replacement; don't claim it is Sealeo's.
        assertNotNull(evaluate(original.copy(articles = original.articles + live)))
        assertNull(evaluate(original.copy(articles = original.articles + listOf(identity, live))))
    }
    private companion object {
        const val VISUAL = "3b4ac826-2c5b-4eb0-9b41-72d39d373919"
        const val SOUND = "ea61e2ca-6379-43e7-b5a5-483921ae1ef6"
        const val EMPTY = 487965141807414L
    }
}
