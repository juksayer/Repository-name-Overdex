package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.TeamLossCandidate
import com.example.overdex.battle.inference.TeamLossResolution
import org.junit.Assert.*
import org.junit.Test

class ReplayFormRefinementKnockoutTest {
    @Test fun `reconciled ownership preserves distinct configured slots with the same species number`() {
        val faint = event("projected", ArchivedActivePokemonFaintedWitnessed("PLAYER",
            "Raichu (Alolan)", 26, ReplayTeamLossProjection.BASIS), 10)
        val resolution = TeamLossResolution("two", 10_000 * MS, 3, 2,
            listOf(TeamLossCandidate("alolan", 0, "Raichu (Alolan)", 26)),
            TeamLossCandidate("alolan", 0, "Raichu (Alolan)", 26), listOf("alolan", "three", "two"))
        val track = ReplayKnockoutTrack(listOf(faint), mapOf("PLAYER" to listOf(
            ReplayIdentityObservation("PLAYER", "Raichu (Alolan)", 26, 0, "TEST",
                articleId = "alolan", playerTeamSlot = 1),
            ReplayIdentityObservation("PLAYER", "Raichu", 26, 8_000 * MS, "TEST",
                articleId = "ordinary", playerTeamSlot = 2),
        )), mapOf("projected" to resolution))
        assertEquals(8_000 * MS, track.knockouts.single().replacementAtNanos)
        assertEquals("Raichu (Alolan)", track.at("PLAYER", 7_950 * MS)!!.speciesName)
        assertNull(track.at("PLAYER", 8_000 * MS))
        assertNull(track.at("PLAYER", 10_000 * MS))
    }

    @Test fun `form refinement does not become a replacement before a reconciled faint`() {
        val input = archive(listOf(
            identity("bare", "Gourgeist", 711, 0), count("three", 3, 1),
            identity("form", "Gourgeist (Average)", 711, 2), count("two", 2, 10),
        ))
        val original = MatchArchiveSerializer.serialize(input)
        val model = MatchReplayModel(input)
        assertFalse(model.sceneAt(1_900 * MS).opponent!!.isFainted)
        assertEquals("Gourgeist (Average)", model.sceneAt(2_100 * MS).opponent!!.speciesName)
        assertFalse(model.sceneAt(9_900 * MS).opponent!!.isFainted)
        val fainted = model.sceneAt(10_100 * MS).opponent!!
        assertEquals("Gourgeist (Average)", fainted.speciesName)
        assertTrue(fainted.isFainted)
        assertEquals(10_000 * MS, fainted.faintedAtNanos)
        assertEquals(10_000 * MS, fainted.knockoutPresentationAtNanos)
        assertNull(fainted.knockoutFinishByNanos)
        assertEquals(ReplayTeamLossProjection.BASIS, fainted.knockoutBasis)
        val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        assertEquals("faint:OPPONENT:0", marker.id)
        assertEquals(10_000 * MS, marker.atNanos)
        assertEquals("Faint · Gourgeist", marker.label)
        assertEquals(original, MatchArchiveSerializer.serialize(input))
    }

    @Test fun `late count fits before the actual replacement and never before the form refinement`() {
        val model = MatchReplayModel(archive(listOf(
            identity("bare", "Gourgeist", 711, 0), count("three", 3, 1),
            identity("form", "Gourgeist (Average)", 711, 2),
            identity("replacement", "Carbink", 703, 8), count("two", 2, 10),
            event("alive", ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 200, 15, .5f), 11),
        )))
        assertFalse(model.sceneAt(1_900 * MS).opponent!!.isFainted)
        assertFalse(model.sceneAt(7_000 * MS).opponent!!.isFainted)
        val outgoing = model.sceneAt(7_950 * MS).opponent!!
        assertEquals("Gourgeist (Average)", outgoing.speciesName)
        assertTrue(outgoing.isFainted)
        assertEquals(8_000 * MS - ReplayKnockoutTrack.MIN_VISIBLE_KO_NANOS, outgoing.knockoutPresentationAtNanos)
        assertEquals(10_000 * MS, outgoing.faintedAtNanos)
        assertEquals("Carbink", model.sceneAt(8_000 * MS).opponent!!.speciesName)
        assertFalse(model.sceneAt(11_000 * MS).opponent!!.isFainted)
    }

    @Test fun `a real switch separates two visits despite their equivalent form names`() {
        val model = MatchReplayModel(archive(listOf(
            identity("bare", "Gourgeist", 711, 0),
            identity("form", "Gourgeist (Average)", 711, 2),
            identity("away", "Carbink", 703, 5), count("three", 3, 6),
            identity("return-form", "Gourgeist (Average)", 711, 7),
            identity("return-bare", "Gourgeist", 711, 8), count("still-three", 3, 9),
            count("two", 2, 10),
        )))
        assertFalse(model.sceneAt(4_950 * MS).opponent!!.isFainted)
        assertFalse(model.sceneAt(9_950 * MS).opponent!!.isFainted)
        assertTrue(model.sceneAt(10_100 * MS).opponent!!.isFainted)
        val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        assertEquals("faint:OPPONENT:${7_000 * MS}", marker.id)
        assertEquals(10_000 * MS, marker.atNanos)
    }

    private fun identity(id: String, name: String, dex: Int, seconds: Long) =
        event(id, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", name, dex), seconds)
    private fun count(id: String, remaining: Int, seconds: Long) =
        event(id, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", remaining, 3), seconds)
    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "forms", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), seconds * 1_000 * MS)
    private fun archive(articles: List<ArchivedRealityArticle>) = MatchArchive(matchId = "forms", articles = articles)
    private companion object { const val MS = 1_000_000L }
}
