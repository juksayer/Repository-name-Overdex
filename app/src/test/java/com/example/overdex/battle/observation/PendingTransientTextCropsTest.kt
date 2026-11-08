package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

/** Scheduling tests use visual rankings only; no strings stand in for native recognition. */
class PendingTransientTextCropsTest {
    @Test fun `eight visual episodes survive contention and overflow drops only the oldest`() {
        val queue = PendingTransientTextCrops()
        val frames = (1..9).map { article("frame-$it", it * 100L) }
        frames.forEachIndexed { index, frame -> queue.offer(frame, text(index + 1L)) }

        val first = requireNotNull(queue.select(900 * MS))
        assertSame(frames[1], first.article)
        assertEquals("TEXT_KEYFRAME", first.kind)
        assertEquals(1L, first.overflowTotal)
        assertEquals(7, first.pendingCount)
        assertEquals(frames.drop(1), listOf(first.article) + drain(queue, 900).map { it.article })
        assertFalse(queue.hasPending(900 * MS))
    }

    @Test fun `capture age expires old keyframes while retaining a fresh unranked fallback`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("old-one", 0), text(1))
        queue.offer(article("old-two", 100), text(2))
        val fresh = article("fresh-blank", 10_900)
        queue.offer(fresh, blank())

        assertTrue(queue.hasPending(11_000 * MS))
        val selected = requireNotNull(queue.select(11_000 * MS))
        assertSame(fresh, selected.article)
        assertEquals("LATEST_FALLBACK", selected.kind)
        assertEquals(2L, selected.expiredTotal)
        assertEquals(0, selected.pendingCount)
        assertNull(queue.select(11_000 * MS))
    }

    @Test fun `a frame exactly ten seconds old is eligible but an older one is not`() {
        val boundary = PendingTransientTextCrops()
        val source = article("at-age-boundary", 0)
        boundary.offer(source, text(1))
        assertSame(source, boundary.select(10_000 * MS)?.article)

        val expired = PendingTransientTextCrops()
        expired.offer(source, text(1))
        assertFalse(expired.hasPending(10_000 * MS + 1))
        assertNull(expired.select(10_000 * MS + 1))
    }

    @Test fun `latest fallback cannot revive an expired capture`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("old-unranked", 0), null)
        assertNull(queue.select(10_000 * MS + 1))
        assertFalse(queue.hasPending(10_000 * MS + 1))
    }

    @Test fun `continuous equal visual signatures coalesce without changing the retained source clock`() {
        val queue = PendingTransientTextCrops()
        val first = article("first-readable", 100, wall = 12_345)
        queue.offer(first, text(8, .90f))
        queue.offer(article("slightly-stronger", 300, wall = 54_321), text(8, .94f))
        queue.offer(article("weaker", 500), text(8, .80f))

        val selected = requireNotNull(queue.select(500 * MS))
        assertSame(first, selected.article)
        assertEquals(100 * MS, selected.article.monotonicTimeNanos)
        assertEquals(12_345L, selected.article.perceivedAt)
        assertEquals(2L, selected.coalescedTotal)
        assertEquals(0, selected.pendingCount)
        assertNull(queue.select(500 * MS))
    }

    @Test fun `a materially clearer frame replaces the episode using its own later source time`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("initial", 100, wall = 111), text(7, .90f))
        val clearer = article("clearer", 300, wall = 777)
        queue.offer(clearer, text(7, .96f))

        val selected = requireNotNull(queue.select(300 * MS))
        assertSame(clearer, selected.article)
        assertEquals(300 * MS, selected.article.monotonicTimeNanos)
        assertEquals(777L, selected.article.perceivedAt)
        assertEquals(1L, selected.coalescedTotal)
        assertNull(queue.select(300 * MS))
    }

    @Test fun `the same visible message after a blank is a new episode`() {
        val queue = PendingTransientTextCrops()
        val first = article("first", 0)
        val returned = article("returned", 200)
        queue.offer(first, text(3))
        queue.offer(article("intervening-blank", 100), blank())
        queue.offer(returned, text(3))

        assertEquals(listOf(first, returned), drain(queue, 200).map { it.article })
    }

    @Test fun `the same visible message after a different message is a new episode`() {
        val queue = PendingTransientTextCrops()
        val first = article("first", 0)
        val middle = article("different", 100)
        val returned = article("returned", 200)
        queue.offer(first, text(3))
        queue.offer(middle, text(4))
        queue.offer(returned, text(3))

        assertEquals(listOf(first, middle, returned), drain(queue, 200).map { it.article })
    }

    @Test fun `a capture gap beyond one and a half seconds starts a new visual episode`() {
        val queue = PendingTransientTextCrops()
        val first = article("first", 0)
        val returned = article("after-gap", 1_501)
        queue.offer(first, text(3))
        queue.offer(returned, text(3))

        assertEquals(listOf(first, returned), drain(queue, 1_501).map { it.article })
    }

    @Test fun `one and a half seconds exactly still belongs to the continuous episode`() {
        val queue = PendingTransientTextCrops()
        val first = article("first", 0)
        queue.offer(first, text(3))
        queue.offer(article("at-gap-boundary", 1_500), text(3))

        val selected = requireNotNull(queue.select(1_500 * MS))
        assertSame(first, selected.article)
        assertEquals(1L, selected.coalescedTotal)
        assertNull(queue.select(1_500 * MS))
    }

    @Test fun `an old coalesced episode cannot discard its still fresh latest frame at expiry`() {
        val queue = PendingTransientTextCrops()
        val frames = (0..11).map { article("continuous-$it", it * 1_000L) }
        frames.forEach { queue.offer(it, text(3)) }

        val selected = requireNotNull(queue.select(11_000 * MS))
        assertSame(frames.last(), selected.article)
        assertEquals("LATEST_FALLBACK", selected.kind)
        assertEquals(1L, selected.expiredTotal)
        assertNull(queue.select(11_000 * MS))
    }

    @Test fun `false negative or unavailable visual probe keeps the latest native OCR opportunity`() {
        for (visual in listOf(null, blank(), text(0), text(2).copy(hasText = false))) {
            val queue = PendingTransientTextCrops()
            queue.offer(article("earlier", 0), null)
            val latest = article("latest", 100)
            queue.offer(latest, visual)

            val selected = requireNotNull(queue.select(100 * MS))
            assertSame(latest, selected.article)
            assertEquals("LATEST_FALLBACK", selected.kind)
            assertEquals(1L, selected.replacedTotal)
            assertNull(queue.select(100 * MS))
        }
    }

    @Test fun `a text candidate is read before the latest unranked fallback without consuming that fallback`() {
        val queue = PendingTransientTextCrops()
        val readable = article("text", 0)
        val fallback = article("unranked", 100)
        queue.offer(readable, text(2))
        queue.offer(fallback, null)

        val first = requireNotNull(queue.select(100 * MS))
        assertSame(readable, first.article)
        assertEquals("TEXT_KEYFRAME", first.kind)
        assertEquals(1, first.pendingCount)
        val second = requireNotNull(queue.select(100 * MS))
        assertSame(fallback, second.article)
        assertEquals("LATEST_FALLBACK", second.kind)
        assertNull(queue.select(100 * MS))
    }

    @Test fun `consumed visual content suppresses repeated turns only while continuously visible`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("first", 0), text(1))
        assertNotNull(queue.select(0))
        queue.offer(article("same", 100), text(1))
        queue.offer(article("same-again", 200), text(1))
        assertFalse(queue.hasPending(200 * MS))

        queue.offer(article("blank", 300), blank())
        val returned = article("returned", 400)
        queue.offer(returned, text(1))
        assertSame(returned, queue.select(400 * MS)?.article)
    }

    @Test fun `failed native attempt permits a subsequent frame of the same visual message`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("attempted", 0), text(1))
        val failed = requireNotNull(queue.select(0))
        queue.failed(failed)
        val retry = article("retry", 100)
        queue.offer(retry, text(1))

        assertSame(retry, queue.select(100 * MS)?.article)
    }

    @Test fun `a newer same episode frame captured during a failed read can survive the later blank`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("attempted", 0), text(1))
        val attempted = requireNotNull(queue.select(0))
        val clearer = article("clearer", 100, wall = 78_901)
        queue.offer(clearer, text(1, .99f))
        val blank = article("gone", 200)
        queue.offer(blank, blank())
        queue.failed(attempted)

        val retry = requireNotNull(queue.select(200 * MS))
        assertSame(clearer, retry.article)
        assertEquals(100 * MS, retry.article.monotonicTimeNanos)
        assertEquals(78_901L, retry.article.perceivedAt)
        queue.succeeded(retry)
        assertSame(blank, queue.select(200 * MS)?.article)
    }

    @Test fun `successful native read discards its held duplicate without erasing a later different episode`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("attempted", 0), text(1))
        val attempted = requireNotNull(queue.select(0))
        queue.offer(article("same-during-read", 100), text(1))
        val different = article("different", 200)
        queue.offer(different, text(2))
        queue.succeeded(attempted)

        assertSame(different, queue.select(200 * MS)?.article)
        assertFalse(queue.hasPending(200 * MS))
    }

    @Test fun `failure of an older episode does not reopen a different consumed episode`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("older", 0), text(1))
        val older = requireNotNull(queue.select(0))
        queue.offer(article("newer", 100), text(2))
        assertNotNull(queue.select(100 * MS))
        queue.failed(older)
        queue.offer(article("same-newer", 200), text(2))

        assertFalse(queue.hasPending(200 * MS))
    }

    @Test fun `a current episode evicted for an in flight retry is not mistaken for completed OCR`() {
        val queue = PendingTransientTextCrops(capacity = 2)
        queue.offer(article("attempted", 0), text(1))
        val attempted = requireNotNull(queue.select(0))
        queue.offer(article("retry", 100), text(1))
        queue.offer(article("middle", 200), text(2))
        queue.offer(article("evicted", 300), text(3))
        queue.failed(attempted)
        val continued = article("continued", 400)
        queue.offer(continued, text(3))
        assertTrue(drain(queue, 400).any { it.article.id == continued.id })
    }

    @Test fun `clear drops pending references and a newer capture can start a fresh episode`() {
        val queue = PendingTransientTextCrops()
        queue.offer(article("queued", 0), text(1))
        queue.offer(article("fallback", 100), blank())
        queue.clear()
        assertFalse(queue.hasPending(100 * MS))
        assertNull(queue.select(100 * MS))

        val fresh = article("fresh", 200)
        queue.offer(fresh, text(1))
        assertSame(fresh, queue.select(200 * MS)?.article)
    }

    @Test fun `missing equal and out of order source times cannot replace a pending capture`() {
        val queue = PendingTransientTextCrops()
        val first = article("first", 100)
        queue.offer(first, text(1))
        queue.offer(article("equal", 100), text(2))
        queue.offer(article("older", 99), text(3))
        queue.offer(article("missing-clock", 200).copy(monotonicTimeNanos = null), text(4))

        val selected = requireNotNull(queue.select(200 * MS))
        assertSame(first, selected.article)
        assertEquals(0L, selected.replacedTotal)
        assertEquals(0L, selected.coalescedTotal)
        assertNull(queue.select(200 * MS))
    }

    private fun drain(queue: PendingTransientTextCrops, nowMillis: Long): List<PendingTransientTextCrops.Selection> =
        buildList { while (true) add(queue.select(nowMillis * MS) ?: break) }

    private fun text(signature: Long, score: Float = .90f) = TransientTextProbe.Reading(
        score, true, signature, TransientTextProbe.PixelBounds(5, 5, 150, 30))

    private fun blank() = TransientTextProbe.Reading(0f, false, 0, null)

    private fun article(label: String, atMillis: Long, wall: Long = atMillis + 1_000) = RealityArticle(
        ArticleId(label), wall, wall + 25, SourceId("ANNOUNCEMENT_CROP_CAPTURE"),
        CropCaptured(CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
            BattleCropProvenance(BattleCropContracts.announcement.cropName, 672, 1493,
                BattleCropBounds(50, 326, 622, 375))),
        monotonicTimeNanos = atMillis * MS,
    )

    private companion object { const val MS = 1_000_000L }
}
