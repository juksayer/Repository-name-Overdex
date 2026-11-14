package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayHpBaselineContinuityTest {
    private val anchor = ArchivedActiveHpBarMeasured("PLAYER",42,746,234,764,.85f,672,1419)
    @Test fun `entry camera bar cannot establish a settled field damage baseline`() {
        assertFalse(replayHpBaselineContinues(anchor.copy(barTop=892,barBottom=910),anchor))
        for (invalid in listOf(anchor.copy(barLeft=240,barRight=432),anchor.copy(sourceHeight=1493),
            anchor.copy(side="OPPONENT"),anchor.copy(barRight=180))) {
            assertFalse(replayHpBaselineContinues(anchor,invalid))
        }
    }
    @Test fun `normal bobbing end cap jitter and tiny or zero HP do not reset a valid baseline`() {
        for (fill in listOf(.85f,.84f,.005f,0f)) {
            assertTrue(replayHpBaselineContinues(anchor,anchor.copy(barTop=659,barBottom=677,filledFraction=fill)))
            assertTrue(replayHpBaselineContinues(anchor,anchor.copy(barLeft=43,barRight=235,filledFraction=fill)))
        }
    }
    @Test fun `stable camera preserves two HP steps and their separate tiny pulse neighbors including final hit`() {
        val input = listOf(
            event("go", 0.0, ArchivedMatchStarted),
            event("player", .01, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
            event("opponent", .01, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323)),
            hp("baseline", .5, .95f), hp("loss-one", 1.0, .94f), hp("confirm-one", 1.2, .94f),
            event("tiny-two", 1.5, ArchivedHpBarBorderPulse("PLAYER", "PRESENT")),
            hp("loss-three", 2.0, .93f), hp("confirm-three", 2.2, .93f),
            event("final-four", 2.5, ArchivedHpBarBorderPulse("PLAYER", "PRESENT")),
            event("faint", 2.5, ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "EXPLICIT_TEST")),
            event("end", 3.0, ArchivedMatchEnded("LOSS")),
        )
        val archive = MatchArchive(matchId = "control", articles = input)
        val original = MatchArchiveSerializer.serialize(archive)
        for (records in listOf(input, input.reversed())) {
            val cycles = cycles(MatchReplayModel(archive.copy(articles = records)))
            assertEquals(listOf(1.0, 1.5, 2.0, 2.5), cycles.map { it.impactAtNanos / 1e9 })
            assertTrue("tiny-two" in cycles[1].witness.articleIds)
            assertTrue("final-four" in cycles.last().witness.articleIds)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }
    @Test fun `relocation keeps independent hit and subsequent settled loss while tiny surviving HP remains visible`() {
        val input = listOf(
            event("go", 0.0, ArchivedMatchStarted),
            event("player", .01, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
            event("opponent", .01, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323)),
            event("old-camera", .5, anchor.copy(barTop=892, barBottom=910, filledFraction=.95f)),
            hp("settled", 1.0, .8f), hp("confirmation", 1.2, .8f),
            event("independent", 1.2, ArchivedHpBarBorderPulse("PLAYER", "PRESENT")),
            hp("tiny-remains", 2.0, .005f), hp("confirmed-tiny", 2.2, .005f),
            event("end", 3.0, ArchivedMatchEnded("UNKNOWN")),
        )
        val model = MatchReplayModel(MatchArchive(matchId="control", articles=input))
        assertEquals(listOf(1.2, 2.0), cycles(model).map { it.impactAtNanos / 1e9 })
        assertEquals(.005f, model.sceneAt(2_300_000_000L).playerHp!!.filledFraction!!, .00001f)
        assertFalse(model.sceneAt(2_300_000_000L).player!!.isFainted)
    }
    private fun hp(id: String, seconds: Double, fill: Float) = event(id, seconds, anchor.copy(filledFraction=fill))
    private fun event(id: String, seconds: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "control", (seconds*1000).toLong(), 3000, "TEST", payload,
        emptyList(), .99f, null, emptyList(), (seconds*1e9).toLong())
    @Suppress("UNCHECKED_CAST")
    private fun cycles(model: MatchReplayModel): List<ReplayFastMoveCycle> =
        model.javaClass.getDeclaredField("fastMoveCycles").apply { isAccessible=true }.get(model) as List<ReplayFastMoveCycle>
    @Test fun `recorded duplicate is an HP inference across entry camera relocation not two independent hits`() {
        val archive=pairedMirrorArchive("pixel")
        val before=MatchArchiveSerializer.serialize(archive)
        val model=pairedMirrorModel(archive)
        val start=model.matchStartNanos!!
        val field=model.javaClass.getDeclaredField("fastMoveCycles").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val cycles=field.get(model) as List<ReplayFastMoveCycle>
        val segment=cycles.filter { it.witness.side=="OPPONENT" && it.impactAtNanos in
            (start+42_000_000_000)..(start+50_000_000_000) }
        assertEquals(3,segment.size)
        assertEquals(listOf(44_317_454_197L,45_987_806_759L,48_344_304_182L),segment.map { it.impactAtNanos-start })
        assertTrue(segment.first().witness.articleIds.contains("67aa33ee-b63e-4aa6-a97c-3beebc06ab7a"))
        assertEquals(2,segment.count { it.witness.damageTicks.isNotEmpty() })
        assertEquals(before,MatchArchiveSerializer.serialize(archive))
    }
}
