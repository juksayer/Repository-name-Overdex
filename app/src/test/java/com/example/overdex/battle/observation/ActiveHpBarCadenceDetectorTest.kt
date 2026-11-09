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

    @Test fun `camera slides do not complete fast move hops`() {
        val detector = ActiveHpBarCadenceDetector()
        val positions = listOf(10 to 100, 10 to 70, 10 to 100, 70 to 70, 10 to 100)
        positions.forEachIndexed { i, (x, y) ->
            val measured = hp(y).copy(barLeft = x, barRight = x + 300)
            assertNull(detector.accept(article("camera-$i", i * 300_000_000L, y).copy(payload = measured), measured, i * 300_000_000L))
        }
    }

    @Test fun `charged impact HP cannot complete hops even without a lateral camera slide`() {
        for (side in ActivePokemonSide.entries) {
            val detector = ActiveHpBarCadenceDetector()
            val positions = listOf(100, 70, 100, 70, 100)
            positions.forEachIndexed { i, top ->
                val at = i * 300_000_000L
                val measured = hp(top).copy(side = side)
                val article = article("charged-$i", at, top).copy(
                    sourceId = SourceId(ChargedImpactHpSource.id(side)), payload = measured)
                assertNull(detector.accept(article, measured, at))
            }
        }
    }

    @Test fun `charged impact resets previous completion and pending departure before ordinary reentry`() {
        val detector = ActiveHpBarCadenceDetector()
        assertNull(detector.accept(article("rest", 0L, 100), hp(100), 0L))
        assertNull(detector.accept(article("first-away", 100_000_000L, 70), hp(70), 100_000_000L))
        assertNull(detector.accept(article("first-return", 200_000_000L, 100), hp(100), 200_000_000L))
        assertNull(detector.accept(article("pending-away", 300_000_000L, 70), hp(70), 300_000_000L))

        val charged = article("charge-camera", 400_000_000L, 100).copy(
            sourceId = SourceId(ChargedImpactHpSource.id(ActivePokemonSide.OPPONENT)))
        assertNull(detector.accept(charged, hp(100), 400_000_000L))
        assertNull(detector.accept(article("ordinary-return", 800_000_000L, 100), hp(100), 800_000_000L))
        assertNull(detector.accept(article("new-away-1", 900_000_000L, 70), hp(70), 900_000_000L))
        assertNull(detector.accept(article("new-return-1", 1_000_000_000L, 100), hp(100), 1_000_000_000L))
        assertNull(detector.accept(article("new-away-2", 1_900_000_000L, 70), hp(70), 1_900_000_000L))
        val resumed = requireNotNull(detector.accept(
            article("new-return-2", 2_000_000_000L, 100), hp(100), 2_000_000_000L))
        assertEquals(1_000_000_000L, resumed.intervalNanos)
        assertEquals(listOf("new-away-2", "new-return-2"), resumed.evidenceArticleIds)
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
