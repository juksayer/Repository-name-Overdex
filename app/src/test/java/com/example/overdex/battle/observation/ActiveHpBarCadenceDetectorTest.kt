package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveHpBarCadenceDetectorTest {
    @Test
    fun `repeated partial motion cannot manufacture extra cadence pulses`() {
        val detector = ActiveHpBarCadenceDetector()

        assertNull(detector.accept(article("rest-1", 0L, 100), hp(100), 0L))
        assertNull(detector.accept(article("away-1", 100_000_000L, 70), hp(70), 100_000_000L))
        assertNull(detector.accept(article("partial-a", 200_000_000L, 82), hp(82), 200_000_000L))
        assertNull(detector.accept(article("partial-b", 300_000_000L, 72), hp(72), 300_000_000L))
        assertNull(detector.accept(article("return-1", 400_000_000L, 100), hp(100), 400_000_000L))

        assertNull(detector.accept(article("away-2", 1_700_000_000L, 68), hp(68), 1_700_000_000L))
        val cadence = detector.accept(article("return-2", 1_900_000_000L, 100), hp(100), 1_900_000_000L)

        requireNotNull(cadence)
        assertEquals(1_500_000_000L, cadence.intervalNanos)
        assertEquals(listOf("away-2", "return-2"), cadence.evidenceArticleIds)
        assertTrue(cadence.verticalExcursionPixels >= 30f)
    }

    @Test
    fun `layout relocation is reanchored without a cadence claim`() {
        val detector = ActiveHpBarCadenceDetector()
        assertNull(detector.accept(article("rest", 0L, 100), hp(100), 0L))
        assertNull(detector.accept(article("moved", 100_000_000L, 40), hp(40), 100_000_000L))
        assertNull(detector.accept(article("still-moved", 3_700_000_000L, 40), hp(40), 3_700_000_000L))
        assertNull(detector.accept(article("new-rest", 3_800_000_000L, 40), hp(40), 3_800_000_000L))
    }

    @Test
    fun `small completed hp bar hops still establish cadence`() {
        val detector = ActiveHpBarCadenceDetector()
        assertNull(detector.accept(article("rest", 0L, 100), hp(100), 0L))
        assertNull(detector.accept(article("away-1", 100_000_000L, 94), hp(94), 100_000_000L))
        assertNull(detector.accept(article("return-1", 200_000_000L, 100), hp(100), 200_000_000L))
        assertNull(detector.accept(article("away-2", 1_100_000_000L, 94), hp(94), 1_100_000_000L))

        val cadence = detector.accept(article("return-2", 1_200_000_000L, 100), hp(100), 1_200_000_000L)

        requireNotNull(cadence)
        assertEquals(1_000_000_000L, cadence.intervalNanos)
        assertTrue(cadence.verticalExcursionPixels >= 6f)
    }

    private fun hp(top: Int) = ActiveHpBarMeasured(
        side = ActivePokemonSide.OPPONENT,
        barLeft = 10,
        barTop = top,
        barRight = 310,
        barBottom = top + 20,
        filledFraction = 0.8f
    )

    private fun article(id: String, nanos: Long, top: Int) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = nanos / 1_000_000L,
        recordedAt = nanos / 1_000_000L,
        sourceId = SourceId("OPPONENT_ACTIVE_HP_BAR_WITNESS"),
        payload = hp(top),
        confidence = 0.9f,
        monotonicTimeNanos = nanos
    )
}
