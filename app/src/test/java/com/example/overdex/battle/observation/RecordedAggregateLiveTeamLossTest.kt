package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchive
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
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live history and publication adapters fed the second 19:03 match's complete original source evidence. */
class RecordedAggregateLiveTeamLossTest {
    @Test
    fun `recorded publication resolves both missing owners with their original shared count clock`() {
        val archive = fixture()
        val before = MatchArchiveSerializer.serialize(archive)
        val articles = archive.articles.mapNotNull(::liveArticle)
        val projection = project(articles)

        assertFinalLosses(projection.histories.mapValues { it.value.resolutions })
        assertEquals(projection.histories.mapValues { it.value.resolutions },
            projection.trackers.mapValues { it.value.resolutions })
        assertEquals(listOf(3, 1), articles.mapNotNull {
            (it.payload as? PlayerPokeBallCountMeasured)?.visibleCount
        })
        assertEquals("The archive contains no named faint conclusions to recycle", 0,
            articles.count { it.payload is ActivePokemonFaintedWitnessed })
        assertEquals(2, projection.emitted.size)
        assertEquals(setOf("Turtonator", "Gourgeist"), projection.emitted.map { it.decision.speciesName }.toSet())
        assertTrue(projection.emitted.all { it.side == ActivePokemonSide.PLAYER })
        assertCountClocksAndUniqueOwners(articles, projection.emitted)
        assertEquals("Source history must not rewrite original Articles", before,
            MatchArchiveSerializer.serialize(archive))
    }

    @Test
    fun `delayed and shuffled original Articles converge on the same live aggregate resolution`() {
        val original = fixture().articles.mapNotNull(::liveArticle)
        val expected = project(original).histories.mapValues { it.value.resolutions }
        assertFinalLosses(expected)
        val deliveries = listOf(original.reversed()) +
            listOf(7, 41, 20261111).map { original.shuffled(Random(it)) }
        deliveries.forEach { delivered ->
            val projection = project(delivered)
            assertEquals(expected, projection.histories.mapValues { it.value.resolutions })
            assertEquals(expected, projection.trackers.mapValues { it.value.resolutions })
            assertTrue("Unchanged opponent ball counts cannot generate a faint",
                projection.emitted.none { it.side == ActivePokemonSide.OPPONENT })
            assertCountClocksAndUniqueOwners(original, projection.emitted)
            // Partial delivered histories may be superseded. The final source-time snapshot is
            // authoritative; reordering does not grant an early publication future knowledge.
        }
    }

    @Test
    fun `repeated deliveries cannot publish either aggregate owner a second time`() {
        val original = fixture().articles.mapNotNull(::liveArticle)
        val projection = project(original.flatMap { listOf(it, it, it) })
        assertFinalLosses(projection.histories.mapValues { it.value.resolutions })
        assertEquals(2, projection.emitted.size)
        assertCountClocksAndUniqueOwners(original, projection.emitted)
        val before = projection.histories.mapValues { it.value.resolutions }
        original.reversed().forEach { article ->
            projection.histories.values.forEach { assertFalse(it.accept(article)) }
            projection.trackers.values.forEach { assertTrue(it.acceptAll(article).isEmpty()) }
        }
        assertEquals(before, projection.histories.mapValues { it.value.resolutions })
        assertEquals(before, projection.trackers.mapValues { it.value.resolutions })
    }

    private fun assertFinalLosses(resolutions: Map<ActivePokemonSide, List<TeamLossResolution>>) {
        val player = resolutions.getValue(ActivePokemonSide.PLAYER).single()
        assertEquals(3 to 1, player.beforeCount to player.afterCount)
        assertEquals(AFTER, player.lossId)
        assertEquals(AFTER_NANOS, player.sourceAtNanos)
        assertNull("This one aggregate interval has no singular owner", player.resolved)
        assertEquals(setOf("Turtonator", "Gourgeist"), player.resolvedCandidates.map { it.speciesName }.toSet())
        assertEquals(2, player.resolvedCandidates.size)
        assertTrue(player.evidenceReferences.containsAll(listOf(BEFORE, AFTER)))
        assertFalse("The final Camerupt faint lacks independent count evidence in this archive",
            player.resolvedCandidates.any { it.speciesName == "Camerupt" })
        assertTrue("Opponent Stunfisk and Pelipper switch while conscious",
            resolutions.getValue(ActivePokemonSide.OPPONENT).isEmpty())
    }

    private fun assertCountClocksAndUniqueOwners(articles: List<RealityArticle>, emitted: List<Emission>) {
        val sources = articles.associateBy { it.id.value }
        val count = sources.getValue(AFTER)
        assertEquals(AFTER_NANOS, count.monotonicTimeNanos)
        assertEquals("Each owner can be published once for the same count source", emitted.size,
            emitted.map { Triple(it.side, it.decision.sourceArticle.id.value,
                TeamLossResolver.speciesKey(it.decision.speciesName)) }.distinct().size)
        emitted.forEach { emission ->
            val decision = emission.decision
            assertEquals("Aggregate owners share the unaltered count Article", count, decision.sourceArticle)
            assertEquals(count.perceivedAt, decision.sourceArticle.perceivedAt)
            assertEquals(count.recordedAt, decision.sourceArticle.recordedAt)
            assertEquals(count.monotonicTimeNanos, decision.sourceArticle.monotonicTimeNanos)
            assertEquals("PLAYER_POKE_BALL_COUNT_DECREASE_AGGREGATE", decision.basis)
            assertFalse("A nearby cry cannot date every member of an aggregate", decision.cryCorroborated)
            assertTrue(decision.evidenceReferences.containsAll(listOf(BEFORE, AFTER)))
            assertTrue(decision.evidenceReferences.all { it in sources })
        }
    }

    private data class Projection(
        val histories: Map<ActivePokemonSide, LiveTeamLossHistory>,
        val trackers: Map<ActivePokemonSide, TeamFaintTracker>,
        val emitted: List<Emission>,
    )

    private data class Emission(val side: ActivePokemonSide, val decision: TeamFaintDecision)

    private fun project(articles: List<RealityArticle>): Projection {
        val histories = ActivePokemonSide.entries.associateWith { LiveTeamLossHistory(it) }
        val trackers = ActivePokemonSide.entries.associateWith { TeamFaintTracker(it) }
        val emitted = mutableListOf<Emission>()
        articles.forEach { article ->
            ActivePokemonSide.entries.forEach { side ->
                histories.getValue(side).accept(article)
                emitted += trackers.getValue(side).acceptAll(article).map { Emission(side, it) }
            }
        }
        return Projection(histories, trackers, emitted)
    }

    private fun fixture(): MatchArchive {
        val bytes = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream(
            "/replay/moondrop-aggregate-team-loss-20261111.json.gz"))).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("Complete original timeline, without supplied identities or synthetic faints",
            "ea9490baf42f33737ca2418b75910143522fc4db82ef0bcd4e1e79d51944f7ce", hash)
        return MatchArchiveSerializer.deserialize(bytes.toString(Charsets.UTF_8)).also {
            assertEquals(3492, it.articles.size)
            assertEquals("e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7", it.matchId)
        }
    }

    /** Preserve the live reader inputs and custody clocks exactly; unrelated Articles are not fabricated inputs. */
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

    private companion object {
        const val BEFORE = "8850bcda-f463-492e-9ef7-9d27bc0fae8b"
        const val AFTER = "cd67d8f0-e95c-4185-8d76-fd46c23519a2"
        const val AFTER_NANOS = 10940538100421L
    }
}
