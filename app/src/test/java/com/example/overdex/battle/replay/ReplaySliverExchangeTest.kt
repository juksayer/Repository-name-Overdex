package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.Pcm16Audio
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ReplaySliverExchangeTest {
    private fun row(id: String, at: Long, p: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, "match", 0, 0, "HP", p, emptyList(), .99f, null, null, at)

    @Test fun onlyAdjacentCompleteProfilesWithTheSameGeometryLocateTheDamage() {
        val red = row("red", 10L, ArchivedActiveHpBarMeasured("OPPONENT", 380, 600, 570, 618,
            .03f, 672, 1493, "RED"))
        val initial = ArchivedCompleteHpBarObserved("OPPONENT", 380, 600, 570, 618, 672, 1493,
            184, 5, 0, "LIVE_COLOR", null, null, .94f, .1f)
        val live = row("live", 10L, initial)
        val changed = initial.copy(liveColumns = 0, damageColumns = 5, status = "UNRESOLVED_COLOR")
        val impact = row("impact", 60L, changed)
        val sources = listOf(red, live, impact)
        assertEquals(impact, ReplaySliverExchange.changes(sources.reversed()).single().damage)
        val bar = red.payload as ArchivedActiveHpBarMeasured
        val unlabelled = red.copy(payload = bar.copy(fillColor = null))
        assertEquals(unlabelled, ReplaySliverExchange.changes(listOf(unlabelled, live, impact)).single().liveMeasurement)
        for (mismatch in listOf(unlabelled.copy(monotonicTimeNanos = 9),
            unlabelled.copy(payload = bar.copy(fillColor = null, filledFraction = .12f)),
            unlabelled.copy(payload = bar.copy(fillColor = null, barTop = bar.barTop + 1)))) {
            assertTrue(ReplaySliverExchange.changes(listOf(mismatch, live, impact)).isEmpty())
        }
        val survivor = row("survivor", 60L, changed.copy(status = "LIVE_COLOR", liveColumns = 1))
        assertTrue(ReplaySliverExchange.changes(sources + survivor).isEmpty())
        assertTrue(ReplaySliverExchange.changes((sources + survivor).reversed()).isEmpty())
        assertTrue(ReplaySliverExchange.changes(listOf(red, live, survivor)).isEmpty())
        for (p in listOf(changed.copy(barLeft = -1), changed.copy(barRight = 672),
                changed.copy(barTop = 675, barBottom = 693), changed.copy(damageColumns = 1))) {
            assertTrue(ReplaySliverExchange.changes(listOf(red, live, impact.copy(payload = p))).isEmpty())
        }
        assertTrue(ReplaySliverExchange.changes(listOf(red, live, impact.copy(confidence = .2f))).isEmpty())
        assertTrue(ReplaySliverExchange.changes(listOf(red, live, impact.copy(monotonicTimeNanos = 2_000_000_000))).isEmpty())
    }

    @Test fun twoDisjointPerformancesInOneClipKeepBothOriginalOnsets() {
        val random = Random(763)
        val wave = ShortArray(16_000) { random.nextInt(-24_000, 24_000).toShort() }
        val clip = wave.copyOfRange(11_200, 16_000) + wave.copyOfRange(0, 4_800)
        val sample = Pcm16Audio(8_000, 1, clip)
        val ref = ReplayAudioReference("test effect", false, null, "ref", Pcm16Audio(8_000, 1, wave))
        val hits = ReplayMixedAudioRecovery.measure(sample, "source", 2_000_000_000L, "clip", listOf(ref),
            shortFragments = true).sortedBy { it.estimatedOnsetNanos }
        assertEquals(listOf(600_000_000L, 2_600_000_000L), hits.map { it.estimatedOnsetNanos })
        assertTrue(hits.all { use -> use.fragments.all { it.articleId == "source" && it.audioSha256 == "clip" && it.referenceSha256 == "ref" } })
        val firstEnd = hits.first().fragments.maxOf { it.queryOffsetNanos + it.durationNanos }
        assertTrue(firstEnd <= hits.last().fragments.minOf { it.queryOffsetNanos })
    }
}
