package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.DeviceMotionPulseMeasured
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeMoveQteVibrationInferenceTest {
    @Test fun `three plausible pulses support one player charge move qte`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(getReady(500_000_000L)))
        assertNull(inference.accept(pulse("nice", 1_000_000_000L)))
        assertNull(inference.accept(pulse("great", 2_000_000_000L)))
        val result = inference.accept(pulse("excellent", 3_000_000_000L))

        requireNotNull(result)
        assertEquals(3, result.payload.pulseCount)
        assertEquals(2_000_000_000L, result.payload.windowNanos)
        assertEquals(listOf(ArticleId("ready"), ArticleId("nice"), ArticleId("great"), ArticleId("excellent")), result.predecessorIds)
        assertEquals(1_000_000_000L, result.observedArticle.monotonicTimeNanos)
    }

    @Test fun `widely separated motion pulses do not become a qte`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(getReady(500_000_000L)))
        assertNull(inference.accept(pulse("one", 1_000_000_000L)))
        assertNull(inference.accept(pulse("two", 5_000_000_000L)))
        assertNull(inference.accept(pulse("three", 9_000_000_000L)))
    }

    @Test fun `ordinary device motion cannot become an unprompted qte`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(pulse("one", 1_000_000_000L)))
        assertNull(inference.accept(pulse("two", 2_000_000_000L)))
        assertNull(inference.accept(pulse("three", 3_000_000_000L)))
    }

    private fun getReady(atNanos: Long) = RealityArticle(
        id = ArticleId("ready"),
        perceivedAt = 1,
        recordedAt = 1,
        sourceId = SourceId("GET_READY_WITNESS"),
        payload = GetReadyWitnessed,
        monotonicTimeNanos = atNanos
    )

    private fun pulse(id: String, atNanos: Long) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = 1,
        recordedAt = 1,
        sourceId = SourceId("DEVICE_MOTION_PULSE_WITNESS"),
        payload = DeviceMotionPulseMeasured(80_000_000L, 1.2f, 0.7f, 8),
        monotonicTimeNanos = atNanos
    )
}
