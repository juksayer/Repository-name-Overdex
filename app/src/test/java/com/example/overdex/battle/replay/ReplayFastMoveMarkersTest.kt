package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayFastMoveMarkersTest {
    @Test fun `canonical use and contributing pulse produce one seek target with original evidence`() {
        val pulse = event("pulse", 2, ArchivedHpBarBorderPulse("OPPONENT", "PRESENT"))
        val use = event("use", 2, ArchivedFastMoveUseObserved("one", "PLAYER", "OPPONENT",
            evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "TEST")).copy(predecessorIds = listOf("pulse"))
        val archive = MatchArchive(matchId="markers", articles=listOf(
            event("start", 0, ArchivedMatchStarted),
            event("player", 0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
            event("opponent", 0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323)),
            pulse, use, event("end", 4, ArchivedMatchEnded("WIN")),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles=archive.articles.reversed()))) {
            val marker = MatchReplayModel(input).eventMarkers.single { it.kind==ReplayEventMarkerKind.FAST_MOVE }
            assertEquals(2_000_000_000L, marker.atNanos)
            assertEquals(marker.atNanos, marker.sourceAtNanos)
            assertEquals("PLAYER", marker.side)
            assertEquals("Fast move · name unknown", marker.label)
            assertEquals(setOf("pulse", "use"), marker.sourceArticleIds.toSet())
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `paired recording markers retain resolved attacks without reintroducing camera or HP duplicates`() {
        for ((device, count) in listOf("pixel" to 10, "moondrop" to 15)) {
            val archive=pairedMirrorArchive(device)
            val original=MatchArchiveSerializer.serialize(archive)
            val model=pairedMirrorModel(archive)
            val markers=model.eventMarkers.filter { it.kind==ReplayEventMarkerKind.FAST_MOVE }
            assertEquals(device, count, markers.size)
            assertEquals(markers, pairedMirrorModel(archive.copy(articles=archive.articles.reversed()))
                .eventMarkers.filter { it.kind==ReplayEventMarkerKind.FAST_MOVE })
            assertTrue(markers.none { "f666daf2-9a01-4901-8232-a0e34201337c" in it.sourceArticleIds })
            for (marker in markers) {
                // Seeking lands on the same resolved impact; no new action is inferred by the marker.
                assertTrue(model.sceneAt(marker.atNanos).fastMoveActions.any {
                    it.side==marker.side && it.impactAtNanos==marker.atNanos &&
                        it.sourceArticleIds==marker.sourceArticleIds.toSet()
                })
            }
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `reconstructed impact marker preserves its source time and navigation reaches coincident events`() {
        val source=ReplayFastMoveWitness("OPPONENT", 2_000_000_000L, "Incinerate", null,
            2_500_000_000L, 0, 10_000_000_000L, setOf("HP_BORDER_PULSE"), setOf("source"), setOf(2_000_000_000L))
        val fast=ReplayEventMarkers.fastMoves(listOf(ReplayFastMoveCycle(source, 500_000_000L, 3_000_000_000L))).single()
        assertEquals(3_000_000_000L, fast.atNanos)
        assertEquals(2_000_000_000L, fast.sourceAtNanos)
        assertEquals("Fast move · Incinerate", fast.label)
        val charge=ReplayEventMarker("charge", ReplayEventMarkerKind.CHARGED_MOVE, fast.atNanos, "PLAYER", "Charged move")
        assertEquals(1, ReplayEventMarkerNavigation.nextIndex(listOf(charge,fast), fast.atNanos, "charge"))
        assertEquals(0, ReplayEventMarkerNavigation.previousIndex(listOf(charge,fast), fast.atNanos, fast.id))
    }
    private fun event(id: String, seconds: Long, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "markers", seconds*1000, seconds*1000, "TEST", payload, emptyList(), null, null,
        emptyList(), seconds*1_000_000_000L)
}
