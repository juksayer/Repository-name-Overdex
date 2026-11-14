package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayEntryTransitionTest {
    private fun badge(id: String, side: String, species: String, seconds: Long) =
        ArchivedRealityArticle(id, "mirror", 0, 0, "${side}_SPECIES_OVERLAY_PIPELINE",
            ArchivedActivePokemonSpeciesWitnessed(side, species), emptyList(), null, null,
            emptyList(), seconds * 1_000_000_000L)
    private fun brackets() = listOf(
        badge("p0", "PLAYER", "Camerupt", 20), badge("o0", "OPPONENT", "Turtonator", 31),
        badge("o1", "OPPONENT", "Camerupt", 44), badge("p1", "PLAYER", "Camerupt", 45))
    private fun resolve(articles: List<ArchivedRealityArticle>) = archivedEntryTransition("Camerupt", 40_000_000_000L, articles)

    @Test fun `unique changing side is independent of custody order and keeps its witnesses`() {
        for (input in listOf(brackets(), brackets().reversed())) {
            val result = requireNotNull(resolve(input))
            assertEquals("OPPONENT", result.side)
            assertEquals(setOf("p0", "p1", "o0", "o1"), result.evidenceArticleIds.toSet())
        }
    }
    @Test fun `repeated counts of the same species are not an entry transition`() {
        assertNull(resolve(brackets().map { if (it.articleId == "o0") badge("o0", "OPPONENT", "Camerupt", 31) else it }))
    }
    @Test fun `simultaneous changes and missing other side evidence remain unresolved`() {
        assertNull(resolve(brackets().map { if (it.articleId == "p0") badge("p0", "PLAYER", "Gourgeist", 20) else it }))
        assertNull(resolve(brackets().filterNot { it.articleId == "p1" }))
    }
    @Test fun `first later badge must agree within the existing eight second window`() {
        assertNull(resolve(brackets() + badge("still-old", "OPPONENT", "Turtonator", 41)))
        assertNull(resolve(brackets().map { if (it.articleId == "o1") badge("late", "OPPONENT", "Camerupt", 49) else it }))
    }
    @Test fun `conflicting simultaneous reads or a restarted observation cannot repair identity`() {
        assertNull(resolve(brackets() + badge("conflict", "OPPONENT", "Gourgeist", 44)))
        assertNull(resolve(brackets() + badge("reset", "PLAYER", "Camerupt", 39).copy(payload = ArchivedObservationRestartedByUser)))
    }
}
