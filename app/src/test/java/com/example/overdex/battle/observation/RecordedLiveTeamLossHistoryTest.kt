package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.PlayerPokeBallCountMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.inference.LiveTeamLossHistory
import com.example.overdex.battle.inference.TeamLossResolution
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Live adapters exercised with original publication order, including the delayed ball count.
 * The shared fixture retains source article IDs and clocks; its adjacent provenance file records
 * archive SHA-256 3d9acb465204b5d981a2df21bea56373fdf1de104c28abd44c30292ab24b55d4.
 */
class RecordedLiveTeamLossHistoryTest {
    private val presentation = DroidballOverlayPresentation

    @Before fun resetBefore() = presentation.reset()
    @After fun resetAfter() = presentation.reset()

    @Test
    fun `recorded publication updates the HUD from current history instead of old faint conclusions`() {
        val articles = fixture()
        val histories = histories()
        val trackers = trackers()
        val emitted = mutableListOf<Emission>()
        var currentOpponentAt = Long.MIN_VALUE

        articles.forEach { article ->
            val identity = article.payload as? ActivePokemonSpeciesWitnessed
            if (identity?.side == ActivePokemonSide.OPPONENT &&
                requireNotNull(article.monotonicTimeNanos) >= currentOpponentAt) {
                currentOpponentAt = requireNotNull(article.monotonicTimeNanos)
                presentation.recordOpponentSpecies(
                    identity.speciesName, identity.speciesId, emptyList(), emptyList(),
                    observedAtNanos = currentOpponentAt,
                )
            }
            for (side in ActivePokemonSide.entries) {
                val history = histories.getValue(side)
                if (history.accept(article) && side == ActivePokemonSide.OPPONENT) {
                    presentation.reconcileOpponentFaints(
                        history.resolutions.mapNotNull { it.resolved?.speciesName }.toSet(),
                        requireNotNull(article.monotonicTimeNanos),
                    )
                }
                emitted += trackers.getValue(side).acceptAll(article).map { Emission(side, article, it) }
            }
            (article.payload as? OpponentBattleResourceCountMeasured)?.let {
                presentation.recordOpponentResources(it, requireNotNull(article.monotonicTimeNanos))
            }
        }

        assertFinalLosses(histories.mapValues { it.value.resolutions })
        assertEquals(setOf("Carbink", "Gourgeist", "Quagsire"),
            presentation.opponentSpecies.value.map { it.speciesName }.toSet())
        assertEquals(setOf("Gourgeist"), presentation.opponentSpecies.value
            .filter { it.isFainted }.map { it.speciesName }.toSet())
        assertFalse(presentation.opponentSpecies.value.single { it.speciesName == "Carbink" }.isFainted)
        assertEquals("Quagsire", presentation.activeOpponentMovePossibilities.value?.speciesName)
        assertEquals(3, emitted.size)
        assertEquals(setOf(ActivePokemonSide.PLAYER to "Turtonator",
            ActivePokemonSide.PLAYER to "Camerupt", ActivePokemonSide.OPPONENT to "Gourgeist"),
            emitted.map { it.side to it.decision.speciesName }.toSet())

        // A returning name is not enough: this recording resolves old losses when positive HP arrives.
        assertTrue(emitted.any { it.trigger.payload is ActiveHpBarMeasured &&
            requireNotNull(it.trigger.monotonicTimeNanos) >
            requireNotNull(it.decision.sourceArticle.monotonicTimeNanos) })
        assertCountClocksAndUniqueDecisions(articles, emitted)

        // Repeat deliveries cannot submit another faint for the same count source.
        articles.forEach { article ->
            trackers.values.forEach { assertTrue(it.acceptAll(article).isEmpty()) }
        }
    }

    @Test
    fun `shuffled delivery converges on the same live history and tracker resolutions`() {
        val original = fixture()
        val expected = project(original)
        assertFinalLosses(expected)
        val deliveries = listOf(original.reversed()) +
            listOf(7, 41, 20261111).map { original.shuffled(Random(it)) }
        deliveries.forEach { articles ->
            val histories = histories()
            val trackers = trackers()
            val emitted = mutableListOf<Emission>()
            articles.forEach { article ->
                ActivePokemonSide.entries.forEach { side ->
                    histories.getValue(side).accept(article)
                    emitted += trackers.getValue(side).acceptAll(article).map { Emission(side, article, it) }
                }
            }
            assertEquals(expected, histories.mapValues { it.value.resolutions })
            assertEquals(expected, trackers.mapValues { it.value.resolutions })
            assertCountClocksAndUniqueDecisions(original, emitted)
            // Earlier emitted conclusions can be superseded. Only the complete final snapshot is
            // presentation authority; delivery-order invariance does not imply prophetic testimony.
        }
    }

    @Test
    fun `archived wrong faint articles neither change live history nor feed another faint`() {
        val articles = fixture()
        val histories = histories()
        val trackers = trackers()
        articles.filterNot { it.payload is ActivePokemonFaintedWitnessed }.forEach { article ->
            histories.values.forEach { it.accept(article) }
            trackers.values.forEach { it.acceptAll(article) }
        }
        val before = histories.mapValues { it.value.resolutions }
        val oldFaints = articles.filter { it.payload is ActivePokemonFaintedWitnessed }
        assertEquals(listOf("Gourgeist", "Camerupt", "Carbink"),
            oldFaints.map { (it.payload as ActivePokemonFaintedWitnessed).speciesName })
        oldFaints.forEach { article ->
            histories.values.forEach { assertFalse(it.accept(article)) }
            trackers.values.forEach { assertTrue(it.acceptAll(article).isEmpty()) }
        }
        assertEquals(before, histories.mapValues { it.value.resolutions })
        assertFinalLosses(before)
    }

    private fun assertCountClocksAndUniqueDecisions(articles: List<RealityArticle>, emitted: List<Emission>) {
        val sources = articles.associateBy { it.id.value }
        // Reordered delivery can first expose an aggregate interval, or revise
        // its named owner. Each loss/owner pair is delivered once; the rebuilt
        // complete history above remains authority for the final faint set.
        assertEquals("One named publication per count source and owner", emitted.size,
            emitted.map { Triple(it.side, it.decision.sourceArticle.id.value,
                com.example.overdex.battle.inference.TeamLossResolver.speciesKey(it.decision.speciesName)) }
                .distinct().size)
        emitted.forEach { emission ->
            val decision = emission.decision
            val source = sources.getValue(decision.sourceArticle.id.value)
            assertTrue(source.payload is PlayerPokeBallCountMeasured ||
                source.payload is OpponentBattleResourceCountMeasured)
            assertEquals(source, decision.sourceArticle)
            assertEquals("Later identity resolution must retain the count capture clock",
                source.monotonicTimeNanos, decision.sourceArticle.monotonicTimeNanos)
            assertEquals(source.perceivedAt, decision.sourceArticle.perceivedAt)
            assertTrue(source.id.value in decision.evidenceReferences)
            assertTrue(decision.evidenceReferences.all { it in sources })
        }
    }

    private fun assertFinalLosses(resolutions: Map<ActivePokemonSide, List<TeamLossResolution>>) {
        assertEquals(listOf("Turtonator", "Camerupt"), resolutions.getValue(ActivePokemonSide.PLAYER)
            .map { it.resolved?.speciesName })
        assertEquals(listOf("Gourgeist"), resolutions.getValue(ActivePokemonSide.OPPONENT)
            .map { it.resolved?.speciesName })
    }

    private fun histories() = ActivePokemonSide.entries.associateWith { LiveTeamLossHistory(it) }
    private fun trackers() = ActivePokemonSide.entries.associateWith { TeamFaintTracker(it) }

    private fun project(articles: List<RealityArticle>): Map<ActivePokemonSide, List<TeamLossResolution>> {
        val histories = histories()
        articles.forEach { article -> histories.values.forEach { it.accept(article) } }
        return histories.mapValues { it.value.resolutions }
    }

    private data class Emission(
        val side: ActivePokemonSide,
        val trigger: RealityArticle,
        val decision: TeamFaintDecision,
    )

    private fun fixture(): List<RealityArticle> = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource(
            "/battle/recorded-evidence/2026-11-11-faint-attribution.json")).readText()
    ).articles.mapNotNull(::liveArticle)

    /** Rehydrate only the live adapter's inputs, preserving every source identity and custody clock. */
    private fun liveArticle(article: ArchivedRealityArticle): RealityArticle? {
        val payload = when (val p = article.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
            is ArchivedActiveHpBarMeasured -> ActiveHpBarMeasured(
                ActivePokemonSide.valueOf(p.side), p.barLeft, p.barTop, p.barRight, p.barBottom,
                p.filledFraction, p.sourceWidth, p.sourceHeight, p.fillColor)
            is ArchivedPlayerPokeBallCountMeasured -> PlayerPokeBallCountMeasured(p.visibleCount, p.maximumCount)
            is ArchivedOpponentBattleResourceCountMeasured -> OpponentBattleResourceCountMeasured(
                OpponentBattleResource.valueOf(p.resource), p.visibleCount, p.maximumCount)
            is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(
                ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId, p.basis)
            else -> return null
        }
        return RealityArticle(
            id = ArticleId(article.articleId), perceivedAt = article.perceivedAt, recordedAt = article.recordedAt,
            sourceId = SourceId(article.sourceId), payload = payload,
            predecessorIds = article.predecessorIds.map(::ArticleId), confidence = article.confidence,
            sequenceNumber = article.sequenceNumber, evidenceReferences = article.evidenceReferences,
            matchId = MatchId(article.matchId), monotonicTimeNanos = article.monotonicTimeNanos,
        )
    }
}
