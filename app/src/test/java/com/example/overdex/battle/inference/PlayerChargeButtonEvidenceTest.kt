package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlayerChargeButtonEvidenceTest {
    private val mon = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
        fastMoves = emptyList(), chargedMoves = listOf(
            Move("Earth Power", PokemonType.GROUND, 90, 50, false),
            Move("Earthquake", PokemonType.GROUND, 110, 65, false),
            Move("Rock Tomb", PokemonType.ROCK, 70, 60, false)))
    private fun inference() = ChargedMoveAnnouncementInference(object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = mon.takeIf { name == it.name }
        override suspend fun getPokemonById(id: Int) = mon.takeIf { id == it.id }
        override suspend fun getAllSpeciesNames() = setOf(mon.name)
    })
    private fun article(id: String, t: Double, payload: TestimonyPayload, source: String = "TEST") = RealityArticle(
        ArticleId(id), (t * 1000).toLong(), 99_000, SourceId(source), payload,
        monotonicTimeNanos = (t * 1e9).toLong())
    private fun species(side: ActivePokemonSide = ActivePokemonSide.PLAYER, t: Double = 1.0, name: String = "Camerupt") =
        article("$side-$t", t, ActivePokemonSpeciesWitnessed(side, name, 323))
    private fun label(slot: Int = 2, text: String = "EARTH POWER", t: Double = 9.5) =
        article("label-$slot-$t", t, PlayerChargeMoveLabelRead(slot, text))
    private fun touch(slot: Int = 2, t: Double = 10.0) = article("touch-$slot-$t", t,
        PlayerChargeButtonTouchWitnessed(slot, 748f, 2187f, 1080, 2400, "P: 1/1 X: 748 Y: 2187"))
    private fun announcement(t: Double = 19.0, text: String = "Camerupt used Ear") =
        article("announcement-$t", t, RawTestimony(text), "ANNOUNCEMENT_WITNESS")
    private suspend fun ChargedMoveAnnouncementInference.feed(vararg articles: RealityArticle): ChargedMoveAnnouncementDerivation? {
        var result: ChargedMoveAnnouncementDerivation? = null
        articles.forEach { result = accept(it) { null } }
        return result
    }

    @Test fun `button mashing and labels never execute a move without its announcement`() = runBlocking {
        val inference = inference()
        assertNull(inference.feed(species(), label()))
        repeat(30) { assertNull(inference.feed(touch(t = 10.0 + it / 10.0))) }
        assertNull(inference.feed(article("ready", 13.1, GetReadyWitnessed)))
    }

    @Test fun `clipped Ear uses actual selected label even when both Earth moves are legal`() = runBlocking {
        val inference = inference()
        inference.feed(species(), label(1, "EARTHQUAKE"), label(), touch())
        val result = inference.feed(announcement())!!
        assertEquals("Earth Power", result.payload.moveName)
        assertEquals(50, result.payload.energyCost)
        assertEquals(19_000_000_000L, result.observedArticle.monotonicTimeNanos)
        assertTrue(result.predecessorIds.containsAll(listOf(label().id, touch().id, announcement().id)))
        assertTrue(result.payload.basis.contains("VISIBLE_TOUCH"))
        assertNull(inference.feed(announcement(19.3)))
    }

    @Test fun `touching other button resolves Earthquake without using popularity`() = runBlocking {
        assertEquals("Earthquake", inference().feed(species(), label(1, "EARTHQUAKE"), label(), touch(1), announcement())?.payload?.moveName)
    }

    @Test fun `a damaged matchup line does not invalidate the complete move name`() = runBlocking {
        val result = inference().feed(species(), label(text = "EARTH POWER\nCIDER EFFECTIVE"), touch(), announcement())
        assertEquals("Earth Power", result?.payload?.moveName)
        assertNull(inference().feed(species(), label(text = "EARTH POWER\nEARTHQUAKE"), touch(), announcement()))
    }

    @Test fun `delayed label publication resolves earlier announcement at its source time`() = runBlocking {
        val inference = inference()
        assertNull(inference.feed(species(), touch(), announcement()))
        assertEquals(announcement().id, inference.feed(label())?.observedArticle?.id)
    }

    @Test fun `stale absent impossible or unrelated button labels cannot complete Ear`() = runBlocking {
        for (label in listOf(label(t = 3.0), label(text = ""), label(text = "EARTH"), label(text = "THUNDERBOLT"), label(text = "ROCK TOMB"))) {
            assertNull(inference().feed(species(), label, touch(), announcement()))
        }
        assertNull(inference().feed(species(), label(), label(text = "", t = 9.9), touch(), announcement()))
        assertNull(inference().feed(species(), label(), touch(), announcement(27.0)))
    }

    @Test fun `switch and faint invalidate earlier selection even if player returns to same species`() = runBlocking {
        val switched = inference()
        assertNull(switched.feed(species(), label(), touch(), species(t = 12.0, name = "Gourgeist"), species(t = 14.0), announcement()))
        val faint = article("faint", 12.0, ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323, "TEST"))
        assertNull(inference().feed(species(), label(), touch(), faint, announcement()))
    }

    @Test fun `mirror needs player charge cue and opponent charge cue prevents guessing`() = runBlocking {
        val ready = article("ready", 10.3, GetReadyWitnessed)
        val common = arrayOf(species(), species(ActivePokemonSide.OPPONENT), label(), touch())
        assertNull(inference().feed(*common, announcement()))
        assertEquals(ActivePokemonSide.PLAYER, inference().feed(*common, ready, announcement())?.payload?.side)
        assertNull(inference().feed(*common, ready, article("incoming", 15.0, AttackIncoming), announcement()))
    }

    @Test fun `player contact never resolves an opponent clipped announcement`() = runBlocking {
        assertNull(inference().feed(species(ActivePokemonSide.OPPONENT), label(), touch(), announcement()))
    }

    @Test fun `same old selection cannot spend again later`() = runBlocking {
        val inference = inference()
        assertNotNull(inference.feed(species(), label(), touch(), announcement()))
        assertNull(inference.feed(announcement(24.0)))
    }
}
