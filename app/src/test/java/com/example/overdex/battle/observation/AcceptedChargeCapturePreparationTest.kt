package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptedChargeCapturePreparationTest {
    @Test
    fun `ready handoff retains source crop and both clocks without publishing testimony`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        val crop = crop("ready", 10)
        offer(handoff, crop, "Get Ready!", recognizedAt = 12 * SECOND)
        assertEquals(listOf(AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.GET_READY,
            "ready", 10 * SECOND, 12 * SECOND, crop.perceivedAt)), cues)
        assertTrue("The source remains the untouched CropCaptured Article", crop.payload is CropCaptured)
    }

    @Test
    fun `both trusted text lanes share phrase visibility and duplicates cannot rearm it`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        val first = crop("ready", 10)
        offer(handoff, first, "Get Ready")
        offer(handoff, first, "Get Ready")
        offer(handoff, crop("same-qte", 11, QTE), "Get Ready", QTE)
        offer(handoff, crop("blank-qte", 12, QTE), "", QTE)
        offer(handoff, crop("still-ready", 13), "Get Ready")
        assertEquals(listOf("ready"), cues.map { it.cropArticleId })
        offer(handoff, crop("blank-main", 14), "")
        offer(handoff, crop("new-qte", 15, QTE), "Get Ready", QTE)
        assertEquals(listOf("ready", "new-qte"), cues.map { it.cropArticleId })
    }

    @Test
    fun `incoming vocabulary uses canonical episode suppression and rearming`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        offer(handoff, crop("incoming", 10), "Attack incoming!")
        offer(handoff, crop("same-warning", 11, QTE), "Use a Protect Shield?", QTE)
        offer(handoff, crop("main-clear", 12), "Opponent used Earth Power!")
        offer(handoff, crop("qte-clear", 13, QTE), "NICE", QTE)
        offer(handoff, crop("next-warning", 14), "No Protect Shields remaining!")
        assertEquals(listOf("incoming", "next-warning"), cues.filter {
            it.kind == AcceptedChargeCaptureCueKind.ATTACK_INCOMING
        }.map { it.cropArticleId })
    }

    @Test
    fun `QTE grades can prepare only through the paired QTE crop and reader`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        offer(handoff, crop("main-grade", 10), "NICE")
        offer(handoff, crop("qte-nice", 11, QTE), "NICE!", QTE)
        offer(handoff, crop("qte-nice", 11, QTE), "NICE!", QTE)
        offer(handoff, crop("qte-great", 12, QTE), "GREAT", QTE)
        offer(handoff, crop("old-grade", 10, QTE), "EXCELLENT", QTE, recognizedAt = 13 * SECOND)
        offer(handoff, crop("qte-excellent", 13, QTE), "EXCELLENT", QTE)
        assertEquals(listOf("qte-nice", "qte-great", "qte-excellent"), cues.map { it.cropArticleId })
        assertTrue(cues.all { it.kind == AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS })
    }

    @Test
    fun `unknown words and unrepaired QTE text cannot supply charge evidence`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        listOf("", "Hello", "NICE!!", "EXCELLENT attack", "NICE GREAT", "N1CE", "Shield", "Super Effective!")
            .forEachIndexed { index, text -> offer(handoff, crop("unknown-$index", 10L + index, QTE), text, QTE) }
        assertTrue(cues.isEmpty())
    }

    @Test
    fun `wrong source match contract or uncited source cannot prepare the reader`() {
        val invalid = listOf(
            Triple("UNTRUSTED_READER", BattleCropContracts.announcement, crop("wrong-reader", 10)),
            Triple(MAIN, BattleCropContracts.qteProgressText, crop("wrong-pair", 10, QTE)),
            Triple(QTE, BattleCropContracts.announcement, crop("wrong-pair2", 10)),
            Triple(MAIN, BattleCropContracts.announcement.copy(region = BattleRegionId.COUNTDOWN), crop("changed-contract", 10)),
            Triple(MAIN, BattleCropContracts.announcement, crop("wrong-provenance", 10, QTE)),
            Triple(MAIN, BattleCropContracts.announcement, crop("wrong-match", 10).copy(matchId = MatchId("other"))),
            Triple(MAIN, BattleCropContracts.announcement, crop("absent-match", 10).copy(matchId = null)),
            Triple(MAIN, BattleCropContracts.announcement, crop("", 10)),
            Triple(MAIN, BattleCropContracts.announcement, crop("uncited-source", 10).copy(sourceId = SourceId(""))),
            Triple(MAIN, BattleCropContracts.announcement, crop("not-crop", 10).copy(payload = RawTestimony("Get Ready"))),
            Triple(MAIN, BattleCropContracts.announcement, crop("missing-clock", 10).copy(monotonicTimeNanos = null)),
        )
        invalid.forEach { (source, contract, sourceCrop) ->
            val handoff = AcceptedChargeCapturePreparation(MATCH)
            val cues = mutableListOf<AcceptedChargeCaptureCue>()
            handoff.attach(cues::add)
            handoff.offer(source, contract, sourceCrop, "Get Ready", 10 * SECOND)
            assertTrue("Rejected input: ${sourceCrop.id.value} / $source", cues.isEmpty())
            offer(handoff, crop("valid", 10), "Get Ready")
            assertEquals("Invalid input must not consume the phrase episode", 1, cues.size)
        }
    }

    @Test
    fun `future stale and missing monotonic clocks cannot arm or consume an episode`() {
        for ((captured, recognized) in listOf(10 * SECOND to (10 * SECOND - 1),
            10 * SECOND to (22 * SECOND + 1), -1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            val handoff = AcceptedChargeCapturePreparation(MATCH)
            val cues = mutableListOf<AcceptedChargeCaptureCue>()
            handoff.attach(cues::add)
            offer(handoff, crop("invalid-time", 10).copy(monotonicTimeNanos = captured), "Get Ready", recognizedAt = recognized)
            assertTrue(cues.isEmpty())
            offer(handoff, crop("valid", 10), "Get Ready")
            assertEquals(1, cues.size)
        }
        for (age in listOf(0L, 12 * SECOND)) {
            val handoff = AcceptedChargeCapturePreparation(MATCH)
            val cues = mutableListOf<AcceptedChargeCaptureCue>()
            handoff.attach(cues::add)
            offer(handoff, crop("boundary", 10), "Get Ready", recognizedAt = 10 * SECOND + age)
            assertEquals("Accepted age boundary $age", 1, cues.size)
        }
    }

    @Test
    fun `offers without an attached reader are neither buffered nor consumed`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        val first = crop("ready", 10)
        offer(handoff, first, "Get Ready")
        val detach = handoff.attach(cues::add)
        assertTrue("Attach does not replay missed hints", cues.isEmpty())
        offer(handoff, first, "Get Ready")
        assertEquals(1, cues.size)
        detach()
        val incoming = crop("incoming", 11)
        offer(handoff, incoming, "Attack incoming")
        handoff.attach(cues::add)
        assertEquals(1, cues.size)
        offer(handoff, incoming, "Attack incoming")
        assertEquals(listOf("ready", "incoming"), cues.map { it.cropArticleId })
    }

    @Test
    fun `detaching an old reader cannot detach a replacement listener`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val old = mutableListOf<AcceptedChargeCaptureCue>()
        val current = mutableListOf<AcceptedChargeCaptureCue>()
        val detachOld = handoff.attach(old::add)
        val detachCurrent = handoff.attach(current::add)
        detachOld()
        offer(handoff, crop("ready", 10), "Get Ready")
        assertTrue(old.isEmpty())
        assertEquals(1, current.size)
        detachCurrent()
        offer(handoff, crop("incoming", 11), "Attack incoming")
        assertEquals(1, current.size)
    }

    @Test
    fun `close is terminal and cannot replay or reopen old scheduling`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        handoff.close()
        offer(handoff, crop("ready", 10), "Get Ready")
        handoff.attach(cues::add)
        offer(handoff, crop("incoming", 11), "Attack incoming")
        handoff.close()
        assertTrue(cues.isEmpty())
    }

    @Test
    fun `listener is called outside handoff lock so lifecycle changes cannot deadlock`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val changed = CountDownLatch(1)
        var worker: Thread? = null
        handoff.attach {
            worker = Thread {
                handoff.attach {}
                changed.countDown()
            }.also { it.start() }
            assertTrue("Listener execution must not hold the handoff lock", changed.await(2, TimeUnit.SECONDS))
        }
        try {
            offer(handoff, crop("ready", 10), "Get Ready")
        } finally {
            worker?.join(2_000)
        }
    }

    @Test
    fun `independent consumers receive the same validated cue and share duplicate suppression`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val hp = mutableListOf<AcceptedChargeCaptureCue>()
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(hp::add)
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        val source = crop("shared-qte", 10, QTE)
        offer(handoff, source, "GREAT", QTE, 12 * SECOND)
        offer(handoff, source, "GREAT", QTE, 13 * SECOND)

        val expected = AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS,
            "shared-qte", 10 * SECOND, 12 * SECOND, source.perceivedAt)
        assertEquals(listOf(expected), hp)
        assertEquals(hp, terminal)
        assertSame("One accepted cue is fanned out without changing provenance", hp.single(), terminal.single())
    }

    @Test
    fun `replacing either consumer preserves the other and old detach cannot remove replacements`() {
        AcceptedChargeCaptureConsumer.entries.forEach { replaced ->
            val handoff = AcceptedChargeCapturePreparation(MATCH)
            val old = mutableListOf<AcceptedChargeCaptureCue>()
            val replacement = mutableListOf<AcceptedChargeCaptureCue>()
            val other = mutableListOf<AcceptedChargeCaptureCue>()
            val remaining = AcceptedChargeCaptureConsumer.entries.single { it != replaced }
            val detachOld = handoff.attach(replaced, old::add)
            handoff.attach(remaining, other::add)
            val detachReplacement = handoff.attach(replaced, replacement::add)
            detachOld()
            offer(handoff, crop("ready", 10), "Get Ready")
            assertTrue(old.isEmpty())
            assertEquals(listOf("ready"), replacement.map { it.cropArticleId })
            assertEquals(replacement, other)

            detachReplacement()
            detachOld()
            offer(handoff, crop("incoming", 11), "Attack incoming")
            assertEquals(listOf("ready"), replacement.map { it.cropArticleId })
            assertEquals(listOf("ready", "incoming"), other.map { it.cropArticleId })
        }
    }

    @Test
    fun `default attach replaces only the explicitly attached HP slot`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val originalHp = mutableListOf<AcceptedChargeCaptureCue>()
        val currentHp = mutableListOf<AcceptedChargeCaptureCue>()
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        val detachOldHp = handoff.attach(AcceptedChargeCaptureConsumer.HP, originalHp::add)
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        handoff.attach(currentHp::add)
        detachOldHp()
        offer(handoff, crop("ready", 10), "Get Ready")
        assertTrue(originalHp.isEmpty())
        assertEquals(1, currentHp.size)
        assertEquals(currentHp, terminal)
    }

    @Test
    fun `late consumer receives no replay or duplicate from an episode accepted by the other`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        val hp = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        offer(handoff, crop("ready", 10), "Get Ready")
        handoff.attach(hp::add)
        assertTrue(hp.isEmpty())
        offer(handoff, crop("same-episode", 11), "Get Ready")
        assertTrue(hp.isEmpty())
        assertEquals(1, terminal.size)
        offer(handoff, crop("cleared", 12), "")
        offer(handoff, crop("next-ready", 13), "Get Ready")
        assertEquals(listOf("next-ready"), hp.map { it.cropArticleId })
        assertEquals(listOf("ready", "next-ready"), terminal.map { it.cropArticleId })
    }

    @Test
    fun `replacement while another callback is running suppresses old pending callback without replay`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val old = mutableListOf<AcceptedChargeCaptureCue>()
        val replacement = mutableListOf<AcceptedChargeCaptureCue>()
        val detachOld = handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, old::add)
        duringBlockedHpDelivery(handoff) {
            handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, replacement::add)
            detachOld()
        }
        assertTrue(old.isEmpty())
        assertTrue("A new consumer is not retroactively included in a delivery", replacement.isEmpty())
        offer(handoff, crop("incoming", 11), "Attack incoming")
        assertEquals(listOf("incoming"), replacement.map { it.cropArticleId })
    }

    @Test
    fun `detach while another callback is running suppresses only the detached pending callback`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        val detach = handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        duringBlockedHpDelivery(handoff, detach)
        assertTrue(terminal.isEmpty())
        val hp = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(hp::add)
        offer(handoff, crop("incoming", 11), "Attack incoming")
        assertEquals(1, hp.size)
        assertTrue(terminal.isEmpty())
    }

    @Test
    fun `close during delivery cancels all pending consumers and cannot be reopened`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        duringBlockedHpDelivery(handoff, handoff::close)
        assertTrue(terminal.isEmpty())
        val hp = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(hp::add)
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        offer(handoff, crop("incoming", 11), "Attack incoming")
        assertTrue(hp.isEmpty())
        assertTrue(terminal.isEmpty())
    }

    @Test
    fun `throwing callback does not skip another consumer and its failure remains visible`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val failure = IllegalStateException("HP callback failed")
        val terminal = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach { throw failure }
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL, terminal::add)
        val thrown = runCatching { offer(handoff, crop("ready", 10), "Get Ready") }.exceptionOrNull()
        assertSame(failure, thrown)
        assertEquals(listOf("ready"), terminal.map { it.cropArticleId })
        offer(handoff, crop("duplicate", 11), "Get Ready")
        assertEquals("A failed callback does not create a retry queue", 1, terminal.size)
    }

    @Test
    fun `both callback failures are reported after both active consumers are called`() {
        val handoff = AcceptedChargeCapturePreparation(MATCH)
        val first = IllegalStateException("HP failed")
        val second = IllegalArgumentException("Terminal failed")
        val calls = mutableListOf<AcceptedChargeCaptureConsumer>()
        handoff.attach { calls += AcceptedChargeCaptureConsumer.HP; throw first }
        handoff.attach(AcceptedChargeCaptureConsumer.TERMINAL_VISUAL) {
            calls += AcceptedChargeCaptureConsumer.TERMINAL_VISUAL
            throw second
        }
        val thrown = runCatching { offer(handoff, crop("ready", 10), "Get Ready") }.exceptionOrNull()
        assertEquals(AcceptedChargeCaptureConsumer.entries.toList(), calls)
        assertSame(first, thrown)
        assertEquals(listOf(second), first.suppressed.toList())
    }

    private fun duringBlockedHpDelivery(handoff: AcceptedChargeCapturePreparation, change: () -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        handoff.attach {
            entered.countDown()
            assertTrue("Lifecycle change must not wait on the callback", release.await(2, TimeUnit.SECONDS))
        }
        val worker = Thread {
            try {
                offer(handoff, crop("ready", 10), "Get Ready")
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.also { it.start() }
        try {
            assertTrue("Offer must reach the first callback", entered.await(2, TimeUnit.SECONDS))
            change()
        } finally {
            release.countDown()
            worker.join(2_000)
        }
        assertTrue("Offer must finish after releasing its callback", !worker.isAlive)
        failure.get()?.let { throw AssertionError("Delivery worker failed", it) }
    }

    private fun offer(handoff: AcceptedChargeCapturePreparation, crop: RealityArticle, text: String,
        source: String = MAIN, recognizedAt: Long = crop.monotonicTimeNanos ?: 0L) {
        handoff.offer(source, if (source == QTE) BattleCropContracts.qteProgressText else BattleCropContracts.announcement,
            crop, text, recognizedAt)
    }

    private fun crop(id: String, seconds: Long, lane: String = MAIN): RealityArticle {
        val contract = if (lane == QTE) BattleCropContracts.qteProgressText else BattleCropContracts.announcement
        return RealityArticle(ArticleId(id), 1_000_000L + seconds * 1000, 1_000_025L + seconds * 1000,
            SourceId("${lane}_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/$id.png", "a".repeat(64), 1),
                BattleCropProvenance(contract.cropName, 1080, 2400, BattleCropBounds(100, 500, 900, 650))),
            matchId = MatchId(MATCH), monotonicTimeNanos = seconds * SECOND)
    }

    private companion object {
        const val MATCH = "owned-match"
        const val MAIN = "ANNOUNCEMENT_WITNESS"
        const val QTE = "QTE_PROGRESS_TEXT_WITNESS"
        const val SECOND = 1_000_000_000L
    }
}
