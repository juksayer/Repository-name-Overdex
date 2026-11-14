package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.observation.*
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream

/** Exact archive member: no video-derived or synthetic testimony is added. */
class RecordedSealeoFinishingHitTest {
    private fun archive(): MatchArchive = MatchArchiveSerializer.deserialize(GZIPInputStream(
        requireNotNull(javaClass.getResourceAsStream("/battle/2026-11-13-sealeo-finish/original-timeline.json.gz"))
    ).bufferedReader().use { it.readText() })

    @Test fun `recorded terminal HP alone reproduces the missing damage tick`() {
        val archive = archive()
        val rows = archive.articles.filter { a ->
            val hp = a.payload as? ArchivedActiveHpBarMeasured
            hp?.side == "OPPONENT" && a.monotonicTimeNanos!! in (EMPTY_AT - 2_000_000_000L)..EMPTY_AT
        }.sortedBy { it.monotonicTimeNanos }
        val detector = ActiveHpBarDamageTickDetector()
        val ticks = rows.mapNotNull { a ->
            val hp = a.payload as ArchivedActiveHpBarMeasured
            detector.accept(ActiveHpBarMeasurement(hp.barLeft, hp.barTop, hp.barRight, hp.barBottom,
                hp.filledFraction, requireNotNull(a.confidence)), a.monotonicTimeNanos!!,
                hp.fillColor?.let(HpBarFillColor::valueOf))
        }
        assertEquals("EMPTY", (rows.last().payload as ArchivedActiveHpBarMeasured).fillColor)
        assertEquals(990_493_588L, EMPTY_AT - rows[rows.lastIndex - 1].monotonicTimeNanos!!)
        assertTrue("One archived empty reading after this gap does not settle a damage tick", ticks.isEmpty())
        assertTrue(archive.articles.any { a -> (a.payload as? ArchivedActivePokemonFaintedWitnessed)?.let {
            it.speciesName == "Sealeo" && it.basis == "VERIFIED_RED_TO_EMPTY_HP" && a.monotonicTimeNanos == EMPTY_AT
        } == true })
    }

    @Test fun `recorded finishing exchange retains its source aligned sight and sound despite publication delay`() {
        val original = archive()
        val bytesBefore = MatchArchiveSerializer.serialize(original)
        val visual = original.articles.single { it.articleId == VISUAL_ID }
        val sound = original.articles.single { it.articleId == SOUND_ID }
        val support = FastMoveUseEvidence.recipientAudioSupport(
            ProcessingArchiveInput.restore(sound) as FastMoveSoundMeasured,
            sound.monotonicTimeNanos!!, ActivePokemonSide.OPPONENT, visual.monotonicTimeNanos!!,
            mapOf(ActivePokemonSide.PLAYER to "Camerupt", ActivePokemonSide.OPPONENT to "Sealeo"))
        assertNotNull("The original references independently support the same exchange", support)
        assertEquals("Incinerate", support!!.moveName)
        assertTrue(kotlin.math.abs(sound.recordedAt - visual.recordedAt) > 1_200)
        for (input in listOf(original, original.copy(articles = original.articles.reversed()))) {
            val model = MatchReplayModel(input)
            val actions = (-30..30).flatMap { offset ->
                model.sceneAt(visual.monotonicTimeNanos!! + offset * 100_000_000L).fastMoveActions
            }.filter { VISUAL_ID in it.sourceArticleIds && SOUND_ID in it.sourceArticleIds }
                .distinctBy { it.sourceArticleIds }
            assertEquals("A captured finishing exchange must not disappear because recognition completed late", 1, actions.size)
            assertEquals("PLAYER", actions.single().side)
            assertTrue(FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT in actions.single().evidenceKinds)
            assertEquals(0f, model.sceneAt(EMPTY_AT + 100_000_000L).opponentHp!!.filledFraction!!, .0001f)
            assertTrue("scene=${model.sceneAt(EMPTY_AT + 100_000_000L).opponent}; markers=${model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }}",
                model.sceneAt(EMPTY_AT + 100_000_000L).opponent!!.isFainted)
        }
        assertEquals(bytesBefore, MatchArchiveSerializer.serialize(original))
    }

    companion object {
        private const val VISUAL_ID = "3b4ac826-2c5b-4eb0-9b41-72d39d373919"
        private const val SOUND_ID = "ea61e2ca-6379-43e7-b5a5-483921ae1ef6"
        private const val EMPTY_AT = 487965141807414L
    }
}
