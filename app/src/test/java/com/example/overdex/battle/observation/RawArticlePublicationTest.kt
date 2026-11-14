package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RawArticlePublicationTest {
    private fun article(sequence: Long) = RealityArticle(ArticleId("raw-$sequence"), 123, 456,
        SourceId("fixture"), RawTestimony("original"), sequenceNumber = sequence,
        evidenceReferences = listOf("crop"), monotonicTimeNanos = 789)

    @Test fun `direct receipt waits do not subscribe to replay history`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("direct-receipts", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(), processingDiagnostics = MatchProcessingDiagnostics())
        try {
            withTimeout(10000) {
                repeat(200) { n ->
                    val accepted = custody.submitTestimony(SourceId("FIXTURE"), RawTestimony("receipt-$n"),
                        123L + n, .9f, listOf("crop-$n"), 789L + n)
                    val raw = match.awaitRawArticlePublished(accepted.sequenceNumber)
                    assertSame(timeline.articleForSequence(accepted.sequenceNumber), raw)
                    assertEquals(accepted.payload, raw.payload)
                    assertEquals(accepted.evidenceReferences, raw.evidenceReferences)
                    assertEquals(accepted.monotonicTimeNanos, raw.monotonicTimeNanos)
                }
                match.awaitAcceptedEvidencePublished()
            }
            assertTrue(match.articleListenerDiagnostics!!.snapshot().keys.none { "collectWhile" in it })
            assertEquals(200, timeline.getArticles().size)
        } finally { match.release() }
    }

    @Test fun `publication before registration returns original beyond stream replay capacity`() = runBlocking {
        val timeline = InMemoryRealityTimeline()
        val receipt = RawArticlePublication(timeline::articleForSequence)
        val first = article(1)
        timeline.append(first); receipt.published(first)
        for (i in 2L..5000L) { val a = article(i); timeline.append(a); receipt.published(a) }
        assertSame(first, withTimeout(1000) { receipt.await(1) })
        assertEquals(0, receipt.pendingCount)
    }

    @Test fun `waiters receive only their exact receipt and cancellation removes registration`() = runBlocking {
        val timeline = InMemoryRealityTimeline()
        val receipt = RawArticlePublication(timeline::articleForSequence)
        val one = async(start = CoroutineStart.UNDISPATCHED) { receipt.await(1) }
        val another = async(start = CoroutineStart.UNDISPATCHED) { receipt.await(1) }
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { receipt.await(2) }
        cancelled.cancelAndJoin()
        assertEquals(2, receipt.pendingCount)
        val unrelated = article(3); timeline.append(unrelated); receipt.published(unrelated)
        assertFalse(one.isCompleted)
        val a = article(1); timeline.append(a); receipt.published(a)
        assertSame(a, one.await()); assertSame(a, another.await())
        assertEquals(0, receipt.pendingCount)
    }

    @Test fun `release cancels registered and capacity waiting callers`() = runBlocking {
        val receipt = RawArticlePublication { null }
        val jobs = (1L..300L).map { async(start = CoroutineStart.UNDISPATCHED) { receipt.await(it) } }
        assertEquals(256, receipt.pendingCount)
        receipt.close()
        jobs.forEach { try { withTimeout(1000) { it.await() }; fail("must cancel") } catch (_: CancellationException) {} }
        assertEquals(0, receipt.pendingCount)
    }

    @Test fun `concurrent publication and registration do not lose notifications`() = runBlocking {
        val timeline = InMemoryRealityTimeline()
        val receipt = RawArticlePublication(timeline::articleForSequence)
        val results = (1L..200L).map { n -> async(Dispatchers.Default) { receipt.await(n) } }
        val originals = (1L..200L).map(::article)
        withContext(Dispatchers.Default) { originals.forEach { timeline.append(it); receipt.published(it) } }
        withTimeout(5000) { results.forEachIndexed { i, job -> assertSame(originals[i], job.await()) } }
        assertEquals(0, receipt.pendingCount)
    }
}
