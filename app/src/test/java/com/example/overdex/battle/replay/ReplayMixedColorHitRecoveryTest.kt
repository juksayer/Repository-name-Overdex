package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayMixedColorHitRecoveryTest {
    private val s = 1_000_000_000L
    private fun row(id: String, at: Long, payload: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, "test", 0, 0, "TEST", payload, emptyList(), .99f, null, emptyList(), at)
    private val visual = row("visual", 2*s, ArchivedFastMoveRecipientVisualArtifactMeasured("OPPONENT", .4f, .3f, .8f, .4f, .1f, .5f, .5f, 25))
    private fun profile(id: String, at: Long, live: Int, damage: Int, status: String) = row(id, at,
        ArchivedCompleteHpBarObserved("OPPONENT", 100, 500, 300, 520, 672, 1493, 196, live, damage,
            status, null, null, 250f, 150f))
    private val before = profile("before", 2*s + s/4, 11, 0, "LIVE_COLOR")
    private val after = profile("after", 2*s + s/2, 0, 11, "UNRESOLVED_COLOR")
    private val fragment = ReplayAudioFragment("audio", s, "audiohash", "ref", 0, 0, 400_000_000L, .6f)
    private val audio = ReplayMixedAudioCandidate("Incinerate", s, listOf(fragment, fragment.copy(referenceOffsetNanos = 400_000_000L)))
    private fun run(articles: List<ArchivedRealityArticle> = listOf(visual, before, after),
        sounds: List<ReplayMixedAudioCandidate> = listOf(audio), player: String? = "Incinerate",
        opponent: Boolean? = false, same: (Long, Long) -> Boolean = { _, _ -> true }) =
        ReplayMixedColorHitRecovery.recover(articles, sounds, { player }, { _, _ -> opponent }, same)

    @Test fun `audio visual and complete color change recover hit while preserving sources`() {
        val result = run().single()
        assertEquals(2*s + s/2, result.atNanos)
        assertEquals(setOf("audio", "visual", "before", "after"), result.articleIds)
        assertEquals(result, run(listOf(after, visual, before)).single())
        assertEquals(11, (after.payload as ArchivedCompleteHpBarObserved).damageColumns)
    }
    @Test fun `a nonlethal color change is still a hit and does not become a faint`() {
        val nonlethal = profile("after", 2*s + s/2, 3, 8, "UNRESOLVED_COLOR")
        assertEquals(1, run(listOf(visual, before, nonlethal)).size)
        assertEquals(3, (nonlethal.payload as ArchivedCompleteHpBarObserved).liveColumns)
    }
    @Test fun `living switch missing evidence and ambiguous ownership never make a hit`() {
        assertTrue(run(same = { _, _ -> false }).isEmpty())
        assertTrue(run(articles = listOf(visual, before)).isEmpty())
        assertTrue(run(articles = listOf(before, after)).isEmpty())
        assertTrue(run(sounds = emptyList()).isEmpty())
        assertTrue(run(player = null).isEmpty())
        assertTrue(run(opponent = true).isEmpty())
        assertTrue(run(opponent = null).isEmpty())
        assertTrue(run(articles = listOf(visual, before, profile("after", 2*s+s/2, 0, 1, "UNRESOLVED_COLOR"))).isEmpty())
        val later = audio.copy(estimatedOnsetNanos = s + s/2, fragments = audio.fragments.map { it.copy(articleId = "second") })
        assertTrue(run(sounds = listOf(audio, later)).isEmpty())
    }
}
