package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayQteSpriteFocusTest {
    @Test fun `focus eases in holds through impact then eases out without scrub state`() {
        val focus = ReplayChargeFocus(ns(5.0), ns(11.8))
        fun amount(t: Double) = ReplayChargePose.qteFocusAmount(focus, ns(t))
        assertEquals(0f, amount(4.9), 0f)
        assertEquals(0f, amount(5.0), 0f)
        assertEquals(.5f, amount(5.375), .00001f)
        for (t in listOf(5.75, 9.9, 10.0, 11.0, 11.8)) {
            assertEquals(1f, amount(t), 0f)
            assertEquals(1.2f, ReplayChargePose.qteSpriteScale(focus, ns(t), true), .00001f)
            assertEquals(.85f, ReplayChargePose.qteSpriteScale(focus, ns(t), false), .00001f)
            assertEquals(20f, ReplayChargePose.qteSpriteTranslationXPixels(focus, ns(t), true), 0f)
            assertEquals(-10f, ReplayChargePose.qteSpriteTranslationXPixels(focus, ns(t), false), 0f)
        }
        assertEquals(.5f, amount(12.175), .00001f)
        assertEquals(0f, amount(12.55), 0f)
        assertEquals(0f, ReplayChargePose.qteFocusAmount(null, ns(8.0)), 0f)
        assertEquals(.5f, amount(5.375), .00001f)
        assertEquals(1f, amount(11.8), 0f)
    }

    @Test fun `Get Ready and shield choice retain framing across release without extending attacks`() {
        for ((cue, side) in listOf(ArchivedGetReadyWitnessed to "PLAYER",
            ArchivedRawText("Use a Protect Shield?") to "OPPONENT")) {
            val archive = archive(at("cue", 5.0, cue, "ANNOUNCEMENT_WITNESS"),
                at("used", 10.0, spend(side)))
            val before = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            val full = model.sceneAt(ns(7.0))
            assertEquals(1f, ReplayChargePose.qteFocusAmount(full.chargeFocus, ns(7.0)), 0f)
            assertEquals(side, full.chargedMoveActions.single().side)
            for (t in listOf(9.9, 10.0, 11.8)) {
                val scene = model.sceneAt(ns(t))
                assertEquals(1f, ReplayChargePose.qteFocusAmount(scene.chargeFocus, ns(t)), 0f)
            }
            val settling = model.sceneAt(ns(12.175))
            assertTrue(settling.chargedMoveActions.isEmpty())
            assertEquals(.5f, ReplayChargePose.qteFocusAmount(settling.chargeFocus, ns(12.175)), .00001f)
            assertNull(model.sceneAt(ns(12.55)).chargeFocus)
            assertEquals(full, model.sceneAt(ns(7.0)))
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `open preparation expires without fabricating impact and replacement or result clears framing`() {
        val open = MatchReplayModel(archive(at("ready", 5.0, ArchivedGetReadyWitnessed)))
        assertTrue(open.sceneAt(ns(15.2)).chargedMoveActions.isEmpty())
        assertTrue(ReplayChargePose.qteFocusAmount(open.sceneAt(ns(15.2)).chargeFocus, ns(15.2)) > 0f)
        assertNull(open.sceneAt(ns(15.75)).chargeFocus)
        for (boundary in listOf(
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Dragonair", 148),
            ArchivedMatchEnded("WIN"), ArchivedObservationRestartedByUser)) {
            val model = MatchReplayModel(archive(at("ready", 5.0, ArchivedGetReadyWitnessed),
                at("used", 10.0, spend("PLAYER")), at("boundary", 11.9, boundary)))
            assertNotNull(model.sceneAt(ns(11.8)).chargeFocus)
            assertNull(model.sceneAt(ns(11.9)).chargeFocus)
        }
    }

    private fun spend(side: String) = ArchivedChargedMoveEnergySpent(side,
        if (side == "PLAYER") "Camerupt" else "Rapidash", "Earth Power", 55, "TEST")
    private fun archive(vararg events: ArchivedRealityArticle) = MatchArchive(matchId = "focus", articles = listOf(
        at("start", .1, ArchivedMatchStarted),
        at("player", .2, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
        at("opponent", .2, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Rapidash", 78)),
    ) + events)
    private fun at(id: String, t: Double, payload: ArchivedTestimonyPayload, source: String = "TEST") =
        ArchivedRealityArticle(id, "focus", (t * 1000).toLong(), 99_000, source, payload,
            emptyList(), null, null, emptyList(), ns(t))
    private fun ns(t: Double) = (t * 1_000_000_000L).toLong()
}
