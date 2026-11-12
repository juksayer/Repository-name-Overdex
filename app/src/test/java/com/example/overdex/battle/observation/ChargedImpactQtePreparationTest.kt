package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class ChargedImpactQtePreparationTest {
    private val opponent = ActivePokemonSide.OPPONENT
    private val reference = ActiveHpBarMeasurement(395, 645, 588, 662, .4f, .95f)
    private fun at(seconds: Double) = (seconds * 1_000_000_000L).toLong()
    private fun seeded() = ChargedImpactHpWatchPolicy().apply {
        request(672, 1493, at(52.0))
        seed(opponent, reference, at(52.321))
    }

    @Test fun `accepted grade stream only admits a whole known grade`() {
        for (text in listOf("NICE!", "GREAT", "excellent!", " NICE! ")) {
            assertTrue(isAcceptedPlayerQteProgressCue(article(text)))
        }
        for (source in listOf("QTE_PROGRESS_TEXT_WITNESS_OCR", "ANNOUNCEMENT_WITNESS", "QTE_PROGRESS_TEXT_WITNESS_LATENCY")) {
            assertFalse(isAcceptedPlayerQteProgressCue(article("NICE!", source)))
        }
        for (text in listOf("", "NIICE!", "NICE ATTACK", "not GREAT", "GREAT!!")) {
            assertFalse(isAcceptedPlayerQteProgressCue(article(text)))
        }
        assertFalse(isAcceptedPlayerQteProgressCue(article("NICE!").copy(payload = MatchStarted)))
    }

    @Test fun `recorded NICE arrives before Get Ready and preserves recipient through centered empty interval`() {
        val watch = seeded()
        watch.prepareFromPlayerQte(at(56.805), "nice") // delivered at 59.058
        val first = requireNotNull(watch.request(672, 1493, at(59.058)))
        assertEquals(opponent, first.side)
        assertEquals(reference, first.reference)
        assertEquals(listOf("nice"), first.cueArticleIds)
        // Older Get Ready is delivered at 60.771; it must not reset the newer cue.
        watch.prepare(opponent, at(53.268), "get-ready")
        val empty = requireNotNull(watch.request(672, 1493, at(61.763)))
        assertEquals(first.token, empty.token)
        assertEquals(listOf("nice"), empty.cueArticleIds)
        // Actual centered EMPTY pixels are checked separately by the recorded
        // ChargedImpactHpBarMeasurer fixture at video 89.151–89.279 seconds.
    }

    @Test fun `chronological grade supports preparation without replacing its anchor or deadline`() {
        val watch = seeded()
        watch.prepare(opponent, at(53.268), "get-ready")
        val before = requireNotNull(watch.request(672, 1493, at(55.5)))
        val camera = ActiveHpBarMeasurement(239, 720, 432, 737, .4f, .95f)
        assertTrue(watch.accept(before, TrackedTerminalHpBarSample(camera, HpBarFillColor.YELLOW), at(55.5)))
        watch.prepareFromPlayerQte(at(55.6), "nice")
        val after = requireNotNull(watch.request(672, 1493, at(55.7)))
        assertEquals(before.token, after.token)
        assertEquals(camera, after.anchor)
        assertEquals(listOf("get-ready", "nice"), after.cueArticleIds)
        watch.prepareFromPlayerQte(at(56.0), "great")
        assertFalse(watch.canCapture(at(58.5))) // Original acquisition's three-second bound.
        assertFalse(watch.canCapture(at(65.269))) // Original phase's bound too.
    }

    @Test fun `repeated grades never extend the fallback phase or reopen its consumed zero`() {
        val watch = seeded()
        watch.prepareFromPlayerQte(at(56.805), "nice")
        watch.prepareFromPlayerQte(at(58.0), "great")
        watch.prepareFromPlayerQte(at(60.0), "excellent")
        watch.prepareFromPlayerQte(at(68.7), "lingering-excellent")
        assertTrue(watch.canCapture(at(68.805)))
        assertFalse(watch.canCapture(at(68.805) + 1))

        val consumed = seeded()
        consumed.prepareFromPlayerQte(at(56.805), "nice")
        val request = requireNotNull(consumed.request(672, 1493, at(61.763)))
        assertEquals(listOf("nice"), request.cueArticleIds)
        val empty = ActiveHpBarMeasurement(239, 720, 432, 737, 0f, .95f)
        assertTrue(consumed.accept(request, TrackedTerminalHpBarSample(empty, HpBarFillColor.EMPTY), at(61.763)))
        consumed.prepareFromPlayerQte(at(62.0), "another-grade")
        assertFalse(consumed.canCapture(at(62.1)))
    }

    @Test fun `unusable QTE reference never erases an earlier viable Get Ready`() {
        val watch = seeded()
        watch.prepareFromPlayerQte(at(58.0), "stale-reference") // Last normal geometry is older than five seconds.
        watch.prepare(opponent, at(53.268), "late-get-ready")
        val request = requireNotNull(watch.request(672, 1493, at(60.771)))
        assertEquals(listOf("late-get-ready"), request.cueArticleIds)
        val absent = ChargedImpactHpWatchPolicy()
        absent.prepareFromPlayerQte(at(56.805), "nice")
        assertNull(absent.request(672, 1493, at(59.058)))
    }

    @Test fun `old and expired grades cannot cancel a newer opponent charge cue`() {
        val watch = seeded()
        watch.prepare(opponent, at(54.0), "current-ready")
        watch.prepareFromPlayerQte(at(53.5), "older-grade")
        val request = requireNotNull(watch.request(672, 1493, at(57.0)))
        assertEquals(listOf("current-ready"), request.cueArticleIds)
        val expired = seeded()
        expired.request(672, 1493, at(70.0))
        expired.prepareFromPlayerQte(at(56.805), "late-grade")
        assertNull(expired.request(672, 1493, at(70.1)))
    }

    @Test fun `appearance stop restart and geometry boundaries invalidate QTE ownership`() {
        for (resetBoth in listOf(false, true)) {
            val watch = seeded()
            watch.prepareFromPlayerQte(at(56.805), "nice")
            val old = requireNotNull(watch.request(672, 1493, at(59.058)))
            if (resetBoth) watch.resetAll(at(59.1)) else watch.reset(opponent, at(59.1))
            assertFalse(watch.current(old))
            watch.prepareFromPlayerQte(at(58.0), "old-grade")
            assertNull(watch.request(672, 1493, at(59.2)))
            watch.prepareFromPlayerQte(at(59.3), "new-grade-without-reference")
            assertNull(watch.request(672, 1493, at(61.4)))
        }
        val resized = seeded()
        resized.prepareFromPlayerQte(at(56.805), "nice")
        assertNull(resized.request(816, 1808, at(59.058)))
        resized.prepareFromPlayerQte(at(59.1), "great")
        assertNull(resized.request(816, 1808, at(61.2)))
    }

    private fun article(text: String, source: String = "QTE_PROGRESS_TEXT_WITNESS") = RealityArticle(
        ArticleId("grade"), 0, 0, SourceId(source), RawTestimony(text), monotonicTimeNanos = at(56.805))
}
