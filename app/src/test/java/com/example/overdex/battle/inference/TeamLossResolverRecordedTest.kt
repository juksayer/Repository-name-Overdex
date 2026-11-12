package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.archive.ArchivedPlayerPokeBallCountMeasured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Original November 11 12:44 Moondrop testimony, including its incorrect old faint conclusions.
 * Source archive SHA-256: 3d9acb465204b5d981a2df21bea56373fdf1de104c28abd44c30292ab24b55d4.
 * The adjacent provenance file documents the exact-article, media-free fixture selection.
 */
class TeamLossResolverRecordedTest {
    @Test
    fun `recorded team losses resolve to Turtonator Camerupt and opponent Gourgeist`() {
        val archive = fixture()
        val before = MatchArchiveSerializer.serialize(archive)
        val player = TeamLossResolver.resolve(observations(archive, "PLAYER"))
        val opponent = TeamLossResolver.resolve(observations(archive, "OPPONENT"))

        assertEquals(listOf("Turtonator", "Camerupt"), player.map { it.resolved?.speciesName })
        assertEquals(listOf("Gourgeist"), opponent.map { it.resolved?.speciesName })
        assertEquals(listOf(3 to 2, 2 to 1), player.map { it.beforeCount to it.afterCount })
        assertEquals(listOf(3 to 2), opponent.map { it.beforeCount to it.afterCount })
        assertEquals(listOf(PLAYER_FIRST_LOSS_ID, PLAYER_SECOND_LOSS_ID), player.map { it.lossId })
        assertEquals(OPPONENT_LOSS_ID, opponent.single().lossId)

        (player + opponent).forEach { loss ->
            val source = archive.articles.single { it.articleId == loss.lossId }
            assertEquals(source.monotonicTimeNanos, loss.sourceAtNanos)
            assertTrue("The count source remains auditable", loss.lossId in loss.evidenceReferences)
            assertTrue("The resolved appearance remains auditable",
                loss.resolved!!.appearanceArticleId in loss.evidenceReferences)
        }
        assertFalse("No count source supports the final player Gourgeist faint",
            player.any { it.resolved?.speciesName == "Gourgeist" })
        assertFalse("Carbink returned alive and later switched out alive",
            opponent.any { it.resolved?.speciesName == "Carbink" })
        assertEquals("Projection must not rewrite the recorded conclusions", before,
            MatchArchiveSerializer.serialize(archive))
    }

    @Test
    fun `recorded final loss snapshots do not depend on publication order`() {
        val archive = fixture()
        for (side in listOf("PLAYER", "OPPONENT")) {
            val recordedDelivery = observations(archive, side)
            val expected = TeamLossResolver.resolve(recordedDelivery)
            val deliveries = listOf(recordedDelivery.reversed()) +
                listOf(7, 41, 20261111).map { recordedDelivery.shuffled(Random(it)) }
            deliveries.forEach { delivered ->
                assertEquals("Source-time projection changed for $side", expected,
                    TeamLossResolver.resolve(delivered))
            }
        }
    }

    @Test
    fun `old wrong faint conclusions stay in the fixture but cannot become resolver evidence`() {
        val archive = fixture()
        val oldFaints = archive.articles.filter { it.payload is ArchivedActivePokemonFaintedWitnessed }
        assertEquals(listOf("Gourgeist", "Camerupt", "Carbink"), oldFaints.map {
            (it.payload as ArchivedActivePokemonFaintedWitnessed).speciesName
        })
        val originalIds = archive.articles.map { it.articleId }.toSet()
        val oldFaintIds = oldFaints.map { it.articleId }.toSet()
        for (side in listOf("PLAYER", "OPPONENT")) {
            val resolved = TeamLossResolver.resolve(observations(archive, side))
            assertTrue(resolved.flatMap { it.evidenceReferences }.all { it in originalIds })
            assertTrue(resolved.flatMap { it.evidenceReferences }.none { it in oldFaintIds })
        }
    }

    @Test
    fun `two departed candidates remain unresolved until a returning candidate has positive HP`() {
        val prefix = listOf(
            identity("a", 1, "Turtonator", 776), count("three", 2, 3), alive("a-hp", 3),
            identity("b", 5, "Gourgeist", 711), alive("b-hp", 6),
            identity("c", 8, "Camerupt", 323), alive("c-hp", 9),
            count("two", 10, 2), alive("c-after-count", 11),
        )
        val pending = TeamLossResolver.resolve(prefix).single()
        assertNull(pending.resolved)
        assertEquals(setOf("Turtonator", "Gourgeist"), pending.candidates.map { it.speciesName }.toSet())

        val returningName = prefix + identity("b-return", 12, "Gourgeist", 711)
        assertNull("A recurring name alone does not prove the Pokémon is alive",
            TeamLossResolver.resolve(returningName).single().resolved)
        val supportedReturn = returningName + alive("b-return-hp", 13)
        val resolved = TeamLossResolver.resolve(supportedReturn).single()
        assertEquals("Turtonator", resolved.resolved?.speciesName)
        assertEquals(pending.lossId, resolved.lossId)
        assertEquals(pending.sourceAtNanos, resolved.sourceAtNanos)
        assertTrue("The independent positive measurement must be cited",
            "b-return-hp" in resolved.evidenceReferences)
    }

    @Test
    fun `a delayed same-count observation does not create another faint`() {
        val observations = listOf(
            identity("a", 1, "Turtonator", 776), count("three", 2, 3), alive("a-hp", 3),
            identity("b", 5, "Gourgeist", 711), count("two", 6, 2), alive("b-hp", 7),
            count("still-two", 8, 2),
        )
        val resolved = TeamLossResolver.resolve(observations)
        assertEquals(1, resolved.size)
        assertEquals("two", resolved.single().lossId)
        assertEquals("Turtonator", resolved.single().resolved?.speciesName)
        assertEquals(resolved, TeamLossResolver.resolve(observations.reversed()))
    }

    private fun fixture(): MatchArchive = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource(
            "/battle/recorded-evidence/2026-11-11-faint-attribution.json")).readText()
    )

    /** Feed raw independent observations only; the archived wrong faint articles stay untouched. */
    private fun observations(archive: MatchArchive, side: String): List<TeamLossObservation> =
        archive.articles.mapNotNull { article ->
            val at = article.monotonicTimeNanos ?: return@mapNotNull null
            when (val payload = article.payload) {
                is ArchivedActivePokemonSpeciesWitnessed -> if (payload.side == side) {
                    TeamLossObservation.Identity(article.articleId, at, payload.speciesName, payload.speciesId)
                } else null
                is ArchivedPlayerPokeBallCountMeasured -> if (side == "PLAYER") {
                    TeamLossObservation.Count(article.articleId, at, payload.visibleCount)
                } else null
                is ArchivedOpponentBattleResourceCountMeasured ->
                    if (side == "OPPONENT" && payload.resource == "POKE_BALLS") {
                        TeamLossObservation.Count(article.articleId, at, payload.visibleCount)
                    } else null
                is ArchivedActiveHpBarMeasured -> if (payload.side == side && payload.filledFraction > 0f) {
                    TeamLossObservation.Alive(article.articleId, at)
                } else null
                else -> null
            }
        }

    private fun identity(id: String, at: Long, name: String, speciesId: Int) =
        TeamLossObservation.Identity(id, at * 1_000_000_000L, name, speciesId)
    private fun count(id: String, at: Long, count: Int) =
        TeamLossObservation.Count(id, at * 1_000_000_000L, count)
    private fun alive(id: String, at: Long) = TeamLossObservation.Alive(id, at * 1_000_000_000L)

    companion object {
        private const val PLAYER_FIRST_LOSS_ID = "43c80a33-b81c-4bba-a707-47a165291ee3"
        private const val PLAYER_SECOND_LOSS_ID = "ad9ae3fe-e509-4296-a439-5e3c6ffb7214"
        private const val OPPONENT_LOSS_ID = "b48941cd-b624-4363-93b4-f80e9d31959f"
    }
}
