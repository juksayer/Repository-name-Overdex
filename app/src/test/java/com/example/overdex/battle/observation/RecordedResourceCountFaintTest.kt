package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Video-derived recovery fixture; this never changes the original compact archive. */
class RecordedResourceCountFaintTest {
    private val directory = File("src/test/resources/battle/balls-20261111")

    @Test fun `latest Moondrop crops distinguish three two and one survivors and reject missing badge`() {
        val samples = Json.parseToJsonElement(File(directory, "manifest.json").readText())
            .jsonObject.getValue("samples").jsonArray
        samples.forEach { value ->
            val sample = value.jsonObject
            val name = sample.getValue("file").jsonPrimitive.content
            assertEquals(name, sample.getValue("expectedCount").jsonPrimitive.intOrNull, count(name))
        }
    }

    @Test fun `real independent frame pairs recover Tinkaton and Mismagius losses under delayed recognition`() {
        val confirmation = BattleResourceCountConfirmation()
        val opponent = TeamFaintTracker(ActivePokemonSide.OPPONENT)
        val player = TeamFaintTracker(ActivePokemonSide.PLAYER)
        val losses = mutableListOf<TeamFaintDecision>()
        fun accept(article: RealityArticle) {
            losses += opponent.acceptAll(article)
            assertTrue("No player faint in this recording", player.acceptAll(article).isEmpty())
        }
        fun species(side: ActivePokemonSide, name: String, id: Int, at: Double) =
            accept(article("$name-$at", at, ActivePokemonSpeciesWitnessed(side, name, id)))
        fun alive(at: Double) = accept(article("alive-$at", at,
            ActiveHpBarMeasured(ActivePokemonSide.OPPONENT, 0, 0, 100, 10, .7f)))
        fun pair(first: String, firstAt: Double, second: String, secondAt: Double, deliveredAt: Double) {
            val a = article(first, firstAt, MatchStarted, deliveredAt)
            val b = article(second, secondAt, MatchStarted, deliveredAt + .1)
            assertNull(confirmation.observe(a, count(first)))
            val result = confirmation.observe(b, count(second))!!
            assertEquals(a.monotonicTimeNanos, result.observed.monotonicTimeNanos)
            assertEquals(listOf(a.id, b.id), result.evidence.map { it.id })
            accept(result.observed.copy(id = ArticleId("count-$first"),
                payload = OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, result.count, 3),
                evidenceReferences = result.evidence.map { it.id.value }))
        }

        // Relative to first visible GO at video 27.3885667. Independent video
        // identity/positive-HP labels support ownership, not inferred damage.
        species(ActivePokemonSide.PLAYER, "Turtonator", 776, 0.0)
        species(ActivePokemonSide.OPPONENT, "Roselia", 315, 0.0)
        accept(article("player-3", .5, PlayerPokeBallCountMeasured(3)))
        pair("balls-27.922200000.png", .5336333, "balls-28.104466667.png", .7159, 2.0)
        species(ActivePokemonSide.OPPONENT, "Tinkaton", 959, 5.62)
        species(ActivePokemonSide.PLAYER, "Camerupt", 323, 15.619)

        // Replacement identity is delivered ahead of the earlier count crop.
        species(ActivePokemonSide.OPPONENT, "Mismagius", 429, 34.899)
        alive(37.642)
        pair("balls-60.504888889.png", 33.1163222, "balls-60.704577778.png", 33.3160111, 41.0)
        species(ActivePokemonSide.OPPONENT, "Roselia", 315, 63.118)
        pair("balls-91.016655556.png", 63.6280889, "balls-91.222800000.png", 63.8342333, 68.0)
        alive(64.0)
        accept(article("concession-win", 70.622, MatchEnded(BattleResult.WIN)))

        assertEquals(setOf("Tinkaton", "Mismagius"), losses.map { it.speciesName }.toSet())
        assertEquals(2, losses.size)
        assertTrue(losses.none { it.speciesName == "Roselia" })
        assertEquals(listOf(33.1163222, 63.6280889).map { (it * SECOND).toLong() },
            losses.map { it.sourceArticle.monotonicTimeNanos }.sortedBy { it })
    }

    private fun count(name: String): Int? = ImageIO.read(File(directory, name)).let {
        OpponentBattleResourceCounter.pokeBallCount(it.width, it.height, it::getRGB)
    }

    private fun article(id: String, seconds: Double, payload: TestimonyPayload, delivered: Double = seconds) =
        RealityArticle(ArticleId(id), (seconds * 1_000).toLong(), (delivered * 1_000).toLong(),
            SourceId("VIDEO_RECOVERY_TEST"), payload, monotonicTimeNanos = (seconds * SECOND).toLong())

    private companion object { const val SECOND = 1_000_000_000L }
}
