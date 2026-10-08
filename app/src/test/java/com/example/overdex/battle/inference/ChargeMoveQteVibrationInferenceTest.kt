package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.DeviceMotionPulseMeasured
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
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

    @Test fun `charge announcement retrospectively groups three short pulses`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(pulse("nice", 1_000_000_000L, 116_000_000L)))
        assertNull(inference.accept(pulse("great", 2_194_000_000L, 209_000_000L)))
        assertNull(inference.accept(pulse("excellent", 4_731_000_000L, 243_000_000L)))
        val result = inference.accept(chargeUsed(5_000_000_000L))

        requireNotNull(result)
        assertEquals(3, result.payload.pulseCount)
        assertEquals(3_731_000_000L, result.payload.windowNanos)
        assertEquals(
            listOf(ArticleId("nice"), ArticleId("great"), ArticleId("excellent"), ArticleId("charge-used")),
            result.predecessorIds,
        )
    }

    @Test fun `max duration phone motion is excluded from a charge pattern`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(pulse("one", 1_000_000_000L, 350_000_000L)))
        assertNull(inference.accept(pulse("two", 2_000_000_000L, 350_000_000L)))
        assertNull(inference.accept(pulse("three", 3_000_000_000L, 350_000_000L)))
        assertNull(inference.accept(chargeUsed(4_000_000_000L)))
    }

    @Test fun `get ready plus closing cue preserves a partial Great result`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(getReady(500_000_000L)))
        assertNull(inference.accept(pulse("nice", 1_000_000_000L)))
        assertNull(inference.accept(pulse("great", 2_000_000_000L)))
        val result = inference.accept(chargeUsed(3_000_000_000L))

        requireNotNull(result)
        assertEquals(2, result.payload.pulseCount)
        assertEquals(1_000_000_000L, result.payload.windowNanos)
    }

    @Test fun `late get ready OCR can group pulses already followed by charge announcement`() {
        val inference = ChargeMoveQteVibrationInference()

        assertNull(inference.accept(pulse("nice", 1_000_000_000L)))
        assertNull(inference.accept(pulse("great", 2_000_000_000L)))
        assertNull(inference.accept(chargeUsed(3_000_000_000L)))
        val result = inference.accept(getReady(500_000_000L))

        requireNotNull(result)
        assertEquals(2, result.payload.pulseCount)
        assertEquals(1_000_000_000L, result.observedArticle.monotonicTimeNanos)
    }

    private fun getReady(atNanos: Long) = RealityArticle(
        id = ArticleId("ready"),
        perceivedAt = 1,
        recordedAt = 1,
        sourceId = SourceId("GET_READY_WITNESS"),
        payload = GetReadyWitnessed,
        monotonicTimeNanos = atNanos
    )

    private fun pulse(id: String, atNanos: Long, durationNanos: Long = 80_000_000L) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = 1,
        recordedAt = 1,
        sourceId = SourceId("DEVICE_MOTION_PULSE_WITNESS"),
        payload = DeviceMotionPulseMeasured(durationNanos, 1.2f, 0.7f, 8),
        monotonicTimeNanos = atNanos
    )

    private fun chargeUsed(atNanos: Long) = RealityArticle(
        id = ArticleId("charge-used"),
        perceivedAt = 1,
        recordedAt = 1,
        sourceId = SourceId("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS"),
        payload = ChargeMoveUsedAnnounced,
        monotonicTimeNanos = atNanos,
    )
}
