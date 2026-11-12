package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class PostChargeRecipientWatchPolicyTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val bar = ActiveHpBarMeasurement(35, 700, 228, 717, .08f, .95f)
    private val pair = mapOf(player to bar, opponent to bar.copy(left = 387, right = 580))
    private fun at(ms: Long) = ms * 1_000_000L
    private fun observe(p: PostChargeRecipientWatchPolicy, ms: Long,
        bars: Map<ActivePokemonSide, ActiveHpBarMeasurement> = pair) = p.observe(672, 1493, at(ms), bars)
    private fun returned() = PostChargeRecipientWatchPolicy().apply {
        prepare(at(1_000)); impact(at(5_000))
        assertFalse(observe(this, 6_000))
        assertTrue(observe(this, 6_100))
    }

    @Test fun `missing HP retains only pixel anchors for fixed three second tail`() {
        val policy = returned()
        assertFalse(observe(policy, 6_150, emptyMap()))
        assertEquals(2, policy.requests(at(6_200)).size)
        // Fresh normal measurements update geometry, never slide the deadline.
        observe(policy, 8_900)
        assertEquals(2, policy.requests(at(9_099)).size)
        assertTrue(policy.requests(at(9_100)).isEmpty())
    }

    @Test fun `ordinary precharge bars and a single return frame cannot open a window`() {
        val p = PostChargeRecipientWatchPolicy()
        p.prepare(at(1_000))
        observe(p, 1_100); observe(p, 1_200)
        assertTrue(p.requests(at(1_250)).isEmpty())
        p.impact(at(5_000))
        observe(p, 5_300); observe(p, 5_450)
        assertTrue(p.requests(at(5_500)).isEmpty())
        observe(p, 6_000)
        assertTrue(p.requests(at(6_050)).isEmpty())
    }

    @Test fun `measured camera then paired normal return works when used OCR is absent`() {
        val p = PostChargeRecipientWatchPolicy()
        p.prepare(at(1_000)); p.cameraMeasured(at(6_000))
        assertFalse(observe(p, 6_100))
        assertTrue(observe(p, 6_200))
        assertEquals(2, p.requests(at(6_250)).size)
    }

    @Test fun `centered missing and widely separated return frames are not confirmation`() {
        val p = PostChargeRecipientWatchPolicy()
        p.impact(at(5_000))
        observe(p, 6_000, pair + (player to bar.copy(left = 240, right = 433)))
        observe(p, 6_100, pair - opponent)
        observe(p, 6_200)
        observe(p, 6_800)
        assertTrue(p.requests(at(6_850)).isEmpty())
    }

    @Test fun `switch ignore dimensions and new charge invalidate in flight requests`() {
        for (reset in listOf<(PostChargeRecipientWatchPolicy) -> Unit>(
            { it.reset(at(6_200)) }, { it.prepare(at(6_200)) },
            { it.observe(1080, 2400, at(6_200), emptyMap()) },
        )) {
            val p = returned()
            val request = p.requests(at(6_150)).first()
            reset(p)
            assertFalse(p.current(request, at(6_250)))
            assertTrue(p.requests(at(6_250)).isEmpty())
        }
    }

    @Test fun `stale frames and repeated cue do not extend the postcharge tail`() {
        val p = returned()
        p.impact(at(5_500)); p.prepare(at(900))
        observe(p, 6_000)
        assertTrue(p.requests(at(6_000)).isEmpty())
        assertTrue(p.requests(at(9_100)).isEmpty())
        p.impact(at(10_000))
        observe(p, 11_000); observe(p, 11_100)
        assertTrue(p.requests(at(11_200)).isEmpty())
    }

    @Test fun `historical reset cannot erase a later camera assignment`() {
        val p = returned()
        p.reset(at(900))
        assertEquals(2, p.requests(at(6200)).size)
    }

    @Test fun `independent recipient samples complete a transient without inventing HP`() = runBlocking {
        val hub = LiveActiveHpBarFrameHub()
        val hp = mutableListOf<LiveActiveHpBarSample>()
        val observations = mutableListOf<FastMoveRecipientVisualArtifactMeasurement>()
        val detector = FastMoveRecipientVisualArtifactDetector()
        val hpJob = launch(start = CoroutineStart.UNDISPATCHED) { hub.samples.collect { hp += it } }
        val visualJob = launch(start = CoroutineStart.UNDISPATCHED) { hub.visualSamples.collect {
            it.visual?.let { pixels -> detector.accept(pixels, it.capturedAtMonotonicTimeNanos)?.let(observations::add) }
        } }
        val neutral = FastMoveRecipientVisualSample(IntArray(24 * 24) { 0xff505050.toInt() })
        val peak = FastMoveRecipientVisualSample(neutral.pixels.copyOf().also { pixels ->
            for (i in 0 until 80) pixels[i] = 0xffef3420.toInt()
        })
        hub.publish(LiveActiveHpBarSample(bar, null, neutral, 1000, at(1000)))
        hub.publishVisual(LiveRecipientVisualSample(peak, 1100, at(1100)))
        hub.publishVisual(LiveRecipientVisualSample(neutral, 1200, at(1200)))
        hub.publishVisual(LiveRecipientVisualSample(peak, 1100, at(1100)))
        yield()
        assertEquals(1, hp.size)
        assertEquals(at(1000), hub.latestSample()!!.capturedAtMonotonicTimeNanos)
        assertEquals(.08f, hub.latestSample()!!.measurement.filledFraction)
        assertEquals(at(1100), observations.single().observedAtMonotonicTimeNanos)
        hpJob.cancelAndJoin(); visualJob.cancelAndJoin()
    }
}
