package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class RecordedZubatFinishingHitTest {
    @Test fun dragonClawLandsAndCreditsOnlyTheResolvedZubatFaint() {
        val path = System.getenv("OVERDEX_ZUBAT_FINISH_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val input = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(input.readBytes())
        assertEquals("1a896bc59d99d136c569f2127b568e22a6a7efb8f27e3aae238d19aa9d5761a3", hash)
        val archive = ZipFile(input).use { zip ->
            MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
        }
        val original = MatchArchiveSerializer.serialize(archive)
        fun replay(rows: List<ArchivedRealityArticle> = archive.articles) = MatchReplayModel(archive.copy(articles = rows),
            chargedMoveTypesByName = mapOf("Dragon Claw" to PokemonType.DRAGON))
        val model = replay()
        val link = model.finishingChargeAssociations.single { it.recipientSpeciesName == "Zubat" }
        assertEquals("Dragon Claw", link.moveName)
        assertEquals("Turtonator", link.attackerSpeciesName)
        assertNull(link.reception)
        val landing = model.sceneAt(link.landingAtNanos)
        assertEquals("Zubat", landing.opponent?.speciesName)
        assertTrue(landing.opponent!!.isFainted)
        assertFalse(model.sceneAt(link.landingAtNanos - 1).opponent!!.isFainted)
        val action = landing.chargedMoveActions.single { it.moveName == "Dragon Claw" }
        assertEquals(link, action.finishingAssociation)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        val markers = model.eventMarkers.filter { it.finishingAttribution == link }
        assertEquals(2, markers.size)
        assertEquals(setOf(ReplayEventMarkerKind.CHARGED_MOVE, ReplayEventMarkerKind.FAINT), markers.map { it.kind }.toSet())
        assertTrue(markers.single { it.kind == ReplayEventMarkerKind.FAINT }.atNanos <= action.startedAtNanos)
        assertEquals("Faint · Zubat · Dragon Claw by Turtonator",
            model.eventMarkers.single { it.finishingAttribution == link && it.kind == ReplayEventMarkerKind.FAINT }.label)
        model.sceneAt(model.endNanos)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
        val rows = archive.articles
        val delayed = replay(rows.reversed().mapIndexed { i, article -> article.copy(recordedAt = 200_000L + i) })
        assertEquals(link, delayed.finishingChargeAssociations.single { it.recipientSpeciesName == "Zubat" })
        val impact = rows.single { it.articleId == "e49ae627-4bbf-4e23-967f-19bee82a99ae" }
        val at = requireNotNull(impact.monotonicTimeNanos)
        fun extra(id: String, offset: Long, payload: ArchivedTestimonyPayload) = impact.copy(
            articleId = id, payload = payload, monotonicTimeNanos = at + offset, sourceId = "TEST",
            predecessorIds = emptyList(), evidenceReferences = emptyList())
        val sliver = extra("surviving-sliver", 100_000_000L,
            (impact.payload as ArchivedActiveHpBarMeasured).copy(filledFraction = .005f, fillColor = "RED"))
        val livingSwitch = rows.filterNot {
            val payload = it.payload
            when (payload) {
                is ArchivedOpponentBattleResourceCountMeasured -> payload.resource == "POKE_BALLS" && payload.visibleCount == 1
                is ArchivedActivePokemonFaintedWitnessed -> payload.speciesName == "Zubat"
                else -> false
            }
        } + sliver
        assertTrue(replay(livingSwitch).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT && "Zubat" in it.label })
        val competingFast = extra("competing-fast", -100_000_000L, ArchivedFastMoveUseObserved(
            "competing-use", "PLAYER", "OPPONENT", attackerSpeciesName = "Turtonator",
            evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "test"))
        val competingCharge = extra("competing-charge", 100_000_000L, ArchivedChargedMoveEnergySpent(
            "PLAYER", "Turtonator", "Brutal Swing", 35, "test"))
        val shieldConflict = extra("shield-spent", 150_000_000L,
            ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 1, 2))
        val wrongQte = rows.map { if (it == impact) it.copy(evidenceReferences = listOf("unrelated-qte")) else it }
        val controls = mapOf("living switch" to livingSwitch, "surviving sliver" to rows + sliver,
            "competing fast hit" to rows + competingFast, "competing charge" to rows + competingCharge,
            "shield conflict" to rows + shieldConflict, "unrelated QTE" to wrongQte)
        for ((scenario, control) in controls) {
            val negative = replay(control)
            assertTrue(scenario, negative.finishingAttributions.none { it.recipientSpeciesName == "Zubat" })
            assertTrue(scenario, negative.eventMarkers.any {
                it.kind == ReplayEventMarkerKind.CHARGED_MOVE && "Dragon Claw" in it.label
            })
            if (scenario == "competing fast hit" || scenario == "competing charge") {
                assertEquals(scenario, "Faint · Zubat",
                    negative.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT && "Zubat" in it.label }.label)
            }
        }
        assertTrue(link.corroboratingArticleIds.contains(impact.articleId))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(input.readBytes()))
    }
}
