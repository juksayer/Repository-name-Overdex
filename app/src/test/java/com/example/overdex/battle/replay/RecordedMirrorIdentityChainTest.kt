package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class RecordedMirrorIdentityChainTest {
    @Test fun `corrected opponent entry cannot reenter through a derived player form identity`() {
        val archive=pairedMirrorArchive("moondrop")
        val before=MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive,archive.copy(articles=archive.articles.reversed()))) {
            val model=pairedMirrorModel(input);val start=model.matchStartNanos!!
            for (seconds in listOf(64.5,65.0,66.0,68.0,69.0,72.0)) {
                val scene=model.sceneAt(start+(seconds*1e9).toLong())
                assertEquals("at $seconds", "Camerupt",scene.player?.speciesName)
                assertEquals("at $seconds", "Gourgeist",scene.opponent?.speciesName)
            }
        }
        assertEquals(before,MatchArchiveSerializer.serialize(archive))
    }
    @Test fun `missing or ambiguous entry-side proof does not discard a recorded player identity`() {
        val entry = ArchivedRealityArticle("entry", "control", 0, 0, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS",
            ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711),
            emptyList(), null, null, emptyList(), 2_000_000_000L)
        val resolved = entry.copy(articleId="resolved", sourceId="PLAYER_ACTIVE_IDENTITY_INFERENCE",
            predecessorIds=listOf("entry"), payload=ArchivedPlayerActiveIdentityResolved(
                "Gourgeist", "Gourgeist", 711, null, 2_000_000_000L, "TEST"))
        for (sources in listOf(listOf(resolved), listOf(entry, resolved))) {
            assertTrue(ReplayPlayerFormIdentity(sources).observations().any { it.articleId=="resolved" })
        }
    }
    @Test fun `real player Gourgeist entry on Pixel remains intact`() {
        val model=pairedMirrorModel(pairedMirrorArchive("pixel"));val start=model.matchStartNanos!!
        for (seconds in listOf(65L,71L,78L,95L))
            assertEquals("Gourgeist",model.sceneAt(start+seconds*1_000_000_000).player?.speciesName)
    }
}
