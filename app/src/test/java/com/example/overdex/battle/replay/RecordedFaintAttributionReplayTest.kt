package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Unmodified source Articles from the match against urbadatpvplol; provenance is beside the fixture. */
class RecordedFaintAttributionReplayTest {
    @Test fun `recorded saved Average label displays as Gourgeist without inventing specimen size`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        val configuration = archive.articles.mapNotNull { it.payload as? ArchivedPlayerTeamSlotConfigured }
            .single { it.speciesName == "Gourgeist (Average)" }
        assertNull(configuration.ownedPokemon)
        val combatant = model(archive).sceneAt(BASE + 178_000_000_000L).player!!
        assertEquals("Gourgeist", combatant.displayName)
        assertEquals("Gourgeist (Average)", combatant.speciesName)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `reconciled faints belong to Turtonator Camerupt and opponent Gourgeist`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val model = model(input)
            val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals(setOf("PLAYER" to "Faint · Turtonator", "PLAYER" to "Faint · Camerupt",
                "OPPONENT" to "Faint · Gourgeist"), faints.map { it.side to it.label }.toSet())
            assertEquals(3, faints.size)
            faints.forEach { marker ->
                val scene = model.sceneAt(marker.atNanos)
                val combatant = if (marker.side == "PLAYER") scene.player else scene.opponent
                assertEquals(marker.label.removePrefix("Faint · "), combatant?.speciesName)
                assertTrue("The resolved actor must receive the faint pose", combatant?.isFainted == true)
                assertEquals(ReplayTeamLossProjection.BASIS, combatant?.knockoutBasis)
            }
            assertEquals(BASE + 160_282_808_240L,
                faints.single { it.side == "OPPONENT" }.sourceAtNanos)
            assertTrue(faints.single { it.side == "OPPONENT" }.sourceArticleIds.contains(
                "b48941cd-b624-4363-93b4-f80e9d31959f"))
            assertTrue(model.timedArticles.any { it.articleId == "d561e4b0-9265-4bcb-b91d-ef854555f154" })
            assertFalse(model.sceneAt(BASE + 178_000_000_000L).opponent!!.isFainted)
            assertTrue(model.sceneAt(BASE + 178_000_000_000L).opponentHp!!.filledFraction!! > 0f)
            assertFalse("A loss alone cannot manufacture the final Gourgeist KO",
                model.sceneAt(BASE + 199_000_000_000L).player!!.isFainted)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `Carbink supported attacks remain visible after the old false faint`() {
        val model = model(recorded())
        val observed = mutableSetOf<String>()
        for (elapsed in 165_000L..180_000L step 25L) {
            val scene = model.sceneAt(BASE + elapsed * 1_000_000L)
            scene.fastMoveActions.filter { it.side == "OPPONENT" }.forEach { observed += it.sourceArticleIds }
        }
        assertTrue(observed.containsAll(listOf(
            "e4afb7c2-51ea-4e98-a723-0a8c0adf8db9",
            "9e8cdc67-1cb4-4be3-a40c-92b2f6ed4ae5",
            "caf14909-ace0-4d93-b34d-cccd0b5b78fd",
        )))
    }

    @Test fun `unassigned final Mud Shot sounds without a recipient impact do not manufacture attacks or faint`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        val replay = model(archive)
        for (millis in 191_000L..197_000L step 50L) {
            assertTrue(replay.sceneAt(BASE + millis * 1_000_000L).fastMoveActions.isEmpty())
        }
        assertFalse(replay.sceneAt(BASE + 199_000_000_000L).player!!.isFainted)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `recorded paired candidates cannot duplicate HP hits or use confounded audio as side proof`() {
        val original = recorded()
        val supplement = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/recorded-evidence/2026-11-11-recipient-audio-pairs.json")).readText())
        val archive = original.copy(articles = (original.articles + supplement.articles).distinctBy { it.articleId })
        val serialized = MatchArchiveSerializer.serialize(archive)
        val pairedVisualIds = listOf("5976cdf5-2ee8-4c5e-ba0e-707f423821c8", "b2e334b1-d92e-41e0-98e5-11ca7b82c74f")
        for (id in pairedVisualIds) {
            assertTrue("The hermetic fixture must contain original recipient visual $id", archive.articles.any {
                it.articleId == id && (it.payload as? ArchivedFastMoveRecipientVisualArtifactMeasured)?.damagedSide == "PLAYER"
            })
        }
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val replay = model(input)
            val observed = linkedMapOf<Set<String>, ReplayFastMoveAction>()
            for (millis in 68_000L..91_000L step 25L) {
                replay.sceneAt(BASE + millis * 1_000_000L).fastMoveActions.filter {
                    it.side == "OPPONENT"
                }.forEach { observed[it.sourceArticleIds] = it }
            }
            // Both are real recipient transients, but their audio windows also
            // fit the other side's Incinerate more strongly. Neither may claim
            // an independently side-resolved sound. Existing measured HP steps
            // already represent these hits and must remain single actions.
            assertTrue(observed.values.none {
                "RECIPIENT_VISUAL_ARTIFACT_WITH_MATCHED_AUDIO" in it.evidenceKinds
            })
            for (visual in pairedVisualIds) {
                val hit = observed.values.single { visual in it.sourceArticleIds }
                assertTrue(hit.evidenceKinds.contains("REPLAY_CORROBORATED_HP_STEP"))
            }
        }
        assertEquals(serialized, MatchArchiveSerializer.serialize(archive))
    }

    private fun recorded(): MatchArchive {
        // Optional local audit runs these same assertions against the complete,
        // unchanged phone archive without checking private media into the repo.
        val complete = System.getenv("OVERDEX_FAINT_REPLAY_ARCHIVE")
        val text = if (complete != null) ZipFile(File(complete)).use { zip ->
            zip.getInputStream(checkNotNull(zip.getEntry("timeline.json"))).bufferedReader().use { it.readText() }
        } else checkNotNull(javaClass.getResource(
            "/battle/recorded-evidence/2026-11-11-faint-attribution.json")).readText()
        return MatchArchiveSerializer.deserialize(text).also {
            if (complete != null) {
                assertEquals("Full original archive must be used for this local audit", 2642, it.articles.size)
                println("Full source archive Replay verification: ${it.articles.size} unchanged Articles")
            }
        }
    }

    private fun model(archive: MatchArchive) = MatchReplayModel(archive,
        speciesIdsByName = mapOf("Turtonator" to 776, "Camerupt" to 323, "Gourgeist" to 711,
            "Gourgeist (Average)" to 711, "Carbink" to 703, "Quagsire" to 195),
        fastMovesBySpeciesName = mapOf("Carbink" to listOf(Move("Rock Throw", PokemonType.ROCK, 8, 5, true, 2))))

    private companion object { const val BASE = 35820771263827L }
}
