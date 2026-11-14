package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import com.example.overdex.model.BattleResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class PreBattleArticleRouteTest {
    private fun crop(name: String) = CropCaptured(CropArtifactReference("fixture.png", "hash", 1),
        BattleCropProvenance(name, 1080, 2400, BattleCropBounds(1, 2, 10, 20)))
    private fun article(n: Long, payload: TestimonyPayload, at: Long = n) = RealityArticle(
        ArticleId("article-$n"), at / 1000000, 1000, SourceId("fixture"), payload,
        sequenceNumber = n, evidenceReferences = listOf("evidence-$n"), monotonicTimeNanos = at)

    @Test fun `all prebattle crops and transition controls retain exact identity and order`() = runBlocking {
        val route = PreBattleArticleRoute()
        val controls = listOf(TeamSelectPartyWitnessed, VsScreenWitnessed,
            CountdownGlyphWitnessed("GO", 1f, 1), MatchStarted, MatchEnded(BattleResult.WIN),
            TeamSelectScanStartedByUser, ObservationRestartedByUser,
            ObservationSessionStoppedByUser, ScreenIgnoredByUser)
        val expected = (PreBattleArticleRoute.cropNames.map(::crop) + controls)
            .mapIndexed { i, payload -> article(i.toLong(), payload) }
        expected.forEach(route::publish)
        val replayed = route.articles.take(expected.size).toList()
        expected.zip(replayed).forEach { (original, delivered) -> assertSame(original, delivered) }
    }

    @Test fun `battle content does not wake prebattle consumers`() = runBlocking {
        val route = PreBattleArticleRoute()
        var delivered = 0
        val listener = launch(start = CoroutineStart.UNDISPATCHED) { route.articles.collect { delivered++ } }
        repeat(1000) { n ->
            route.publish(article(n.toLong(), RawTestimony("SUPER EFFECTIVE!")))
            route.publish(article(n.toLong() + 1000, crop(BattleCropContracts.matchOutcome.cropName)))
        }
        yield()
        assertEquals(0, delivered)
        assertTrue(route.articles.replayCache.isEmpty())
        listener.cancelAndJoin()
    }

    @Test fun `late prebattle captures survive match start and retain capture time window decisions`() {
        val route = PreBattleArticleRoute()
        val party = article(1, TeamSelectPartyWitnessed, 10_000_000_000)
        val start = article(2, MatchStarted, 11_000_000_000)
        val late = article(3, crop(TeamSelectCropContracts.playerRosterName1.cropName), 10_500_000_000)
        val tooLate = article(4, crop(TeamSelectCropContracts.playerRosterName1.cropName), 11_500_000_000)
        listOf(party, start, late, tooLate).forEach(route::publish)
        assertEquals(listOf(party, start, late, tooLate), route.articles.replayCache)
        val window = TeamSelectRosterWindow()
        window.confirm(party.monotonicTimeNanos!!); window.close(start.monotonicTimeNanos!!)
        assertTrue(window.allows(late.monotonicTimeNanos!!))
        assertFalse(window.allows(tooLate.monotonicTimeNanos!!))
    }

    @Test fun `match keeps every article while fifteen readers avoid general broadcasts`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("prebattle-routing", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(), processingDiagnostics = MatchProcessingDiagnostics())
        val received = List(15) { mutableListOf<RealityArticle>() }
        val jobs = received.map { target -> launch(start = CoroutineStart.UNDISPATCHED) {
            match.preBattleArticles.collect { target.add(it) }
        } }
        try {
            repeat(100) { n -> custody.submitTestimony(SourceId("fixture"), RawTestimony("unrelated-$n"),
                n.toLong(), 1f, listOf("crop-$n"), n.toLong()) }
            val accepted = custody.submitTestimony(SourceId("fixture"),
                crop(TeamSelectCropContracts.useThisParty.cropName), 200, .9f, listOf("original"), 200)
            withTimeout(5000) { match.awaitAcceptedEvidencePublished() }
            yield()
            val raw = timeline.articleForSequence(accepted.sequenceNumber)!!
            received.forEach { assertEquals(1, it.size); assertSame(raw, it.single()) }
            assertEquals(101, timeline.getArticles().size)
            assertEquals(15, match.preBattleListenerDiagnostics!!.peakSubscriptions)
            assertEquals(15L, match.preBattleListenerDiagnostics.snapshot().values.sumOf { it.deliveries })
            assertTrue(match.articleListenerDiagnostics!!.peakSubscriptions <= 1)
            custody.submitTestimony(SourceId(BattleWitnessContracts.countdownGlyph.witnessId),
                CountdownGlyphWitnessed("GO", .99f, 1), 300, 1f, listOf("go-crop"), 300)
            withTimeout(5000) { match.awaitAcceptedEvidencePublished() }
            yield()
            val start = timeline.getArticles().single { it.payload == MatchStarted }
            received.forEach { target -> assertSame(start, target.single { it.payload == MatchStarted }) }
        } finally { jobs.forEach { it.cancelAndJoin() }; match.release() }
    }
}
