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
            val impact = actions.single().impactAtNanos
            val landing = model.sceneAt(impact).fastMoveActions.single {
                VISUAL_ID in it.sourceArticleIds && SOUND_ID in it.sourceArticleIds
            }
            assertEquals("The supported projectile reaches its recipient", 1f, landing.progress, 0f)
            assertEquals("Incinerate", landing.moveName)
            assertTrue("The visual/audio cue does not itself timestamp settled damage",
                landing.damageObservationTimesNanos.isEmpty())
            val laterRed = original.articles.filter { a ->
                val hp = a.payload as? ArchivedActiveHpBarMeasured
                hp?.side == "OPPONENT" && hp.fillColor == "RED" && hp.filledFraction > 0f &&
                    a.monotonicTimeNanos?.let { it > impact && it < EMPTY_AT } == true
            }
            assertTrue("A later recorded living sliver makes the reconstructed impact time insufficient for KO credit", laterRed.isNotEmpty())
            assertTrue("The original test must retain the intervening red testimony", laterRed.all { it in model.timedArticles })
            assertTrue(FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT in actions.single().evidenceKinds)
            assertEquals(0f, model.sceneAt(EMPTY_AT + 100_000_000L).opponentHp!!.filledFraction!!, .0001f)
            assertTrue("scene=${model.sceneAt(EMPTY_AT + 100_000_000L).opponent}; markers=${model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }}",
                model.sceneAt(EMPTY_AT + 100_000_000L).opponent!!.isFainted)
        }
        assertEquals(bytesBefore, MatchArchiveSerializer.serialize(original))
    }

    @Test fun `recorded bar motion and terminal hp stay separate from KO cause`() {
        val original = archive()
        val before = MatchArchiveSerializer.serialize(original)
        val visualAt = original.articles.single { it.articleId == VISUAL_ID }.monotonicTimeNanos!!
        val rows = original.articles.filter { it.monotonicTimeNanos?.let { t ->
            t in (visualAt - 500_000_000L)..EMPTY_AT
        } == true }
        val playerBars = rows.mapNotNull { a -> (a.payload as? ArchivedActiveHpBarMeasured)
            ?.takeIf { it.side == "PLAYER" } }
        assertTrue("Player bar moves during the exchange", playerBars.maxOf { it.barTop } - playerBars.minOf { it.barTop } >= 30)
        val red = rows.filter { a -> (a.payload as? ArchivedActiveHpBarMeasured)?.let {
            it.side == "OPPONENT" && it.fillColor == "RED" && it.filledFraction > 0f
        } == true }
        assertTrue(red.any { it.monotonicTimeNanos!! > visualAt })
        assertTrue(rows.any { a -> (a.payload as? ArchivedActiveHpBarMeasured)?.let {
            it.side == "OPPONENT" && it.fillColor == "EMPTY" && a.monotonicTimeNanos == EMPTY_AT
        } == true })
        assertTrue("The source archive has no separately published motion cadence in this interval",
            rows.none { it.payload is ArchivedActiveHpBarMotionCadenceMeasured })
        assertTrue("Opponent attacks remain separate competing source observations", rows.any { a ->
            (a.payload as? ArchivedFastMoveUseObserved)?.attackingSide == "OPPONENT"
        })
        assertEquals(before, MatchArchiveSerializer.serialize(original))
    }

    @Test fun `original later image narrows visible orange while clipped bar cannot establish zero`() {
        val original = archive()
        val before = MatchArchiveSerializer.serialize(original)
        val red = original.articles.single { it.articleId.startsWith("68a22539-") }
        val crop = original.articles.single { it.articleId.startsWith("5ccb340b-") }
        val payload = crop.payload as ArchivedCropCaptured
        val lastBar = red.payload as ArchivedActiveHpBarMeasured
        assertTrue(red.monotonicTimeNanos!! < crop.monotonicTimeNanos!!)
        assertTrue(crop.monotonicTimeNanos!! < EMPTY_AT)
        assertTrue("The original orange image clips the right end of the bar", payload.cropRight!! < lastBar.barRight)
        assertEquals("812af88f91f27bd8915266184c47c7c1b2eb0570782ff93d2243130452dee532", payload.sha256)
        val bytes = requireNotNull(javaClass.getResourceAsStream(
            "/battle/2026-11-13-sealeo-finish/partial-orange-original.png")).readBytes()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(payload.sha256, digest)
        val image = javax.imageio.ImageIO.read(bytes.inputStream())
        val x = lastBar.barLeft - payload.cropLeft!! + 4
        val y = lastBar.barTop - payload.cropTop!! + 8
        assertTrue("Retained original crop contains orange-colored pixels near the left cap",
            (x-4..x+5).any { px -> (y-5..y+5).any { py ->
                val rgb = image.getRGB(px, py); val r = rgb shr 16 and 255
                val g = rgb shr 8 and 255; val b = rgb and 255
                r > 160 && g > 65 && r > g * 1.1 && g > b * 1.2
            } })
        assertTrue("A partial crop does not invent a new HP or faint article",
            original.articles.none { it.evidenceReferences?.contains(crop.articleId) == true &&
                it.payload is ArchivedActivePokemonFaintedWitnessed })
        assertEquals(before, MatchArchiveSerializer.serialize(original))
    }

    companion object {
        private const val VISUAL_ID = "3b4ac826-2c5b-4eb0-9b41-72d39d373919"
        private const val SOUND_ID = "ea61e2ca-6379-43e7-b5a5-483921ae1ef6"
        private const val EMPTY_AT = 487965141807414L
    }
}
