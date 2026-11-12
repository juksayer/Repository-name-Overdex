package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** All original Articles from the second 19:03 recording match, with no fabricated identities or KOs. */
class RecordedAggregateTeamLossReplayTest {
    @Test fun `unqualified player terminal pixels cannot invent the missing final Camerupt KO or zero HP`() {
        val original=recorded()
        val bytes=MatchArchiveSerializer.serialize(original)
        val charge=original.articles.last { it.payload is ArchivedChargedMoveEnergySpent }.monotonicTimeNanos!!
        // Test-only alignment to the second GO in the shared video; never new phone testimony.
        val offset=GO-211_063_000_000L
        val phase=charge-10_000_000_000L
        val until=charge+6_000_000_000L
        val samples=TerminalSequenceFixtures.playerFactorySamples("190345").map { s ->
            s.copy(sourceAtNanos=s.sourceAtNanos+offset,previousSourceAtNanos=s.previousSourceAtNanos?.plus(offset))
        }.filter { it.sourceAtNanos in phase until until }
        assertTrue(samples.count { it.status=="SAMPLED" }>10)
        assertTrue("Real bright effects were measured, but not the required ordered collapse",samples.any { it.flashes.isNotEmpty() })
        assertTrue(TerminalCollapseSequences.find(samples.mapIndexed { i,s -> TerminalEffectFrame("sample-$i",s) }).isEmpty())
        val batch=ArchivedRealityArticle("test-player-terminal-pixels",original.matchId,0,0,"TERMINAL_PARTICLE_WITNESS",
            ArchivedTerminalParticleObservations("PLAYER",phase,until,samples,true,samples.size),
            emptyList(),null,null,listOf("test-only-video-190345"),samples.last().sourceAtNanos)
        val enriched=original.copy(articles=original.articles+batch)
        assertTrue(ReplayTerminalChargedFaintProjection(enriched,enriched.articles).resolutionsByArticleId.isEmpty())
        val result=original.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val scene=model(enriched).sceneAt(result)
        assertEquals("Camerupt",scene.player!!.displayName)
        assertFalse(scene.player.isFainted)
        assertEquals(model(original).sceneAt(result).playerHp,scene.playerHp)
        assertNotEquals("An unavailable terminal HP reading must not become measured zero",0f,scene.playerHp?.filledFraction)
        val lastHp=original.articles.last { (it.payload as? ArchivedActiveHpBarMeasured)?.side=="PLAYER" }
        assertEquals((lastHp.payload as ArchivedActiveHpBarMeasured).filledFraction,
            model(enriched).sceneAt(lastHp.monotonicTimeNanos!!).playerHp!!.filledFraction!!,0f)
        assertEquals(bytes,MatchArchiveSerializer.serialize(original))
    }
    @Test fun `recorded three to one resolves two owners without changing archived events or source clocks`() {
        val archive = recorded()
        val original = MatchArchiveSerializer.serialize(archive)
        assertTrue(archive.articles.none { it.payload is ArchivedActivePokemonFaintedWitnessed })
        assertEquals(listOf(3, 1), archive.articles.mapNotNull {
            (it.payload as? ArchivedPlayerPokeBallCountMeasured)?.visibleCount
        })
        val count = archive.articles.single { it.articleId == AFTER }
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val projection = ReplayTeamLossProjection(input)
            assertEquals(2, projection.resolutionsByArticleId.size)
            val owners = projection.resolutionsByArticleId.values.map { it.resolved!!.speciesName }.toSet()
            assertEquals(setOf("Turtonator", "Gourgeist"), owners)
            assertEquals(setOf(AFTER), projection.resolutionsByArticleId.values.map { it.lossId }.toSet())
            val model = model(input)
            val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals(setOf("Faint · Turtonator", "Faint · Gourgeist"), faints.map { it.label }.toSet())
            assertEquals(2, faints.size)
            for (marker in faints) {
                assertEquals("PLAYER", marker.side)
                assertEquals("The group count supplies no separate earlier faint clock",
                    count.monotonicTimeNanos, marker.sourceAtNanos)
                assertTrue(marker.sourceArticleIds.containsAll(listOf(BEFORE, AFTER)))
                val actor = model.sceneAt(marker.atNanos).player!!
                assertEquals(marker.label.removePrefix("Faint · "), actor.displayName)
                assertTrue(actor.isFainted)
                assertEquals(ReplayTeamLossProjection.BASIS, actor.knockoutBasis)
            }
            assertTrue(model.timedArticles.containsAll(input.articles.filter { it.monotonicTimeNanos != null }))
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `aggregate owners never backdate count one or invent an intermediate count two`() {
        val archive = recorded()
        val after = archive.articles.single { it.articleId == AFTER }.monotonicTimeNanos!!
        val model = model(archive)
        val firstKo = model.eventMarkers.first { it.kind == ReplayEventMarkerKind.FAINT }.atNanos
        assertTrue(firstKo < after)
        for (at in listOf(firstKo - 1, firstKo, firstKo + SECOND, after - 1, after, after + SECOND).reversed()) {
            assertEquals(if (at < after) 3 else 1, model.sceneAt(at).playerResources.pokeBalls)
        }
        val countChanges = (0..150).map { model.sceneAt(GO + it * SECOND).playerResources.pokeBalls }.toSet()
        assertEquals(setOf(3, 1), countChanges)
        assertEquals(listOf(3, 1), ReplayTeamLossProjection(archive).articles.mapNotNull {
            (it.payload as? ArchivedPlayerPokeBallCountMeasured)?.visibleCount
        })
    }

    @Test fun `recorded zero remains Gourgeist HP and Camerupt final faint remains unresolved`() {
        val archive = recorded()
        val zero = archive.articles.single { it.articleId == "9d77191a-675b-4122-b600-bf7ed49bea28" }
        assertEquals(0f, (zero.payload as ArchivedActiveHpBarMeasured).filledFraction)
        val model = model(archive)
        val zeroScene = model.sceneAt(zero.monotonicTimeNanos!!)
        assertEquals("Gourgeist", zeroScene.player!!.displayName)
        assertEquals(0f, zeroScene.playerHp!!.filledFraction!!, 0f)
        for (at in listOf(120, 132, 144)) {
            val scene = model.sceneAt(GO + at * SECOND)
            assertEquals("Camerupt", scene.player!!.displayName)
            assertFalse(scene.player.isFainted)
            assertTrue(scene.playerHp!!.filledFraction!! > 0f)
            assertFalse("Living opponent switches must not acquire a KO", scene.opponent!!.isFainted)
            assertEquals(3, scene.opponentResources.pokeBalls)
        }
        val result = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        assertFalse("LOSS does not supply the missing final Camerupt faint", model.sceneAt(result).player!!.isFainted)
    }

    @Test fun `supported Camerupt and opponent attacks after both earlier KOs remain present`() {
        for (archive in recorded().let { listOf(it, it.copy(articles = it.articles.reversed())) }) {
            val model = model(archive)
            val expected = setOf("d7e7b3c6-a021-4927-9f84-cc83c599e9fb", "b8816e88-bba5-4201-a88c-2bd4276751fb")
            val observed = mutableSetOf<String>()
            for (millis in 117_000L..120_000L step 25L) {
                val scene = model.sceneAt(GO + millis * 1_000_000L)
                assertFalse(scene.player!!.isFainted)
                assertFalse(scene.opponent!!.isFainted)
                scene.fastMoveActions.forEach { observed += it.sourceArticleIds }
            }
            assertTrue("Later independent attacks must survive aggregate attribution: $observed",
                observed.containsAll(expected))
        }
    }

    private fun recorded(): MatchArchive {
        val path = System.getenv("OVERDEX_AGGREGATE_LOSS_REPLAY_ARCHIVE")
        val bytes = if (path != null) ZipFile(File(path)).use { zip ->
            zip.getInputStream(checkNotNull(zip.getEntry("timeline.json"))).use { it.readBytes() }
        } else GZIPInputStream(checkNotNull(javaClass.getResourceAsStream(
            "/replay/moondrop-aggregate-team-loss-20261111.json.gz"))).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("The complete original timeline must remain byte-for-byte intact",
            "ea9490baf42f33737ca2418b75910143522fc4db82ef0bcd4e1e79d51944f7ce", hash)
        return MatchArchiveSerializer.deserialize(bytes.toString(Charsets.UTF_8)).also {
            assertEquals(3492, it.articles.size)
            assertEquals("e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7", it.matchId)
        }
    }

    private fun model(archive: MatchArchive) = MatchReplayModel(archive,
        speciesIdsByName = mapOf("Turtonator" to 776, "Gourgeist" to 711, "Gourgeist (Average)" to 711,
            "Camerupt" to 323, "Stunfisk" to 618, "Pelipper" to 279))

    private companion object {
        const val SECOND = 1_000_000_000L
        const val GO = 10825626046183L
        const val BEFORE = "8850bcda-f463-492e-9ef7-9d27bc0fae8b"
        const val AFTER = "cd67d8f0-e95c-4185-8d76-fd46c23519a2"
    }
}
