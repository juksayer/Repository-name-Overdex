package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide.*
import com.example.overdex.battle.custody.FastMoveSoundCandidate
import com.example.overdex.battle.reality.ArticleId
import org.junit.Assert.*
import org.junit.Test

class FastMoveAudioPreferenceTrackerTest {
    private val context = listOf(
        AudioPreferenceCombatant(PLAYER, "Dragonair", 0, listOf("Dragon Breath", "Iron Tail")),
        AudioPreferenceCombatant(OPPONENT, "Forretress", 0, listOf("Bug Bite")),
    )
    private fun candidate(name: String, score: Float, changing: Float) =
        FastMoveSoundCandidate(name, "ref-$name", score, changing, 0, 0, 416_000_000)
    private val weak = listOf(candidate("Dragon Breath", .89f, .74f),
        candidate("Iron Tail", .83f, .68f), candidate("Bug Bite", .82f, .67f))
    private fun observe(t: FastMoveAudioPreferenceTracker, at: Long, scores: List<FastMoveSoundCandidate> = weak,
        fighters: List<AudioPreferenceCombatant> = context) = t.observe(ArticleId("clip-$at"), at, scores, fighters)

    @Test fun `one accepted global match favors its unique side even when saved shortlist omits low ranks`() {
        val t = FastMoveAudioPreferenceTracker()
        val strong = listOf(candidate("Dragon Breath", .98f, .94f), candidate("Bug Bite", .86f, .70f))
        t.observe(ArticleId("strong"), 1_000_000_000, strong, context, referenceMatchAccepted = true,
            contextEvidence = listOf(ArticleId("player-species"), ArticleId("opponent-species")))
        assertEquals("Dragon Breath", t.preference(PLAYER, context, 1_000_000_000)?.moveName)
        assertEquals(listOf(ArticleId("strong"), ArticleId("player-species"), ArticleId("opponent-species")),
            t.preference(PLAYER, context, 1_000_000_000)?.evidenceIds)
        assertNull(t.preference(OPPONENT, context, 1_000_000_000))
        assertNull(t.preference(PLAYER, context, 7_000_000_001))
    }

    @Test fun `strict flag cannot bypass score checks shared ownership or unknown combatant pool`() {
        val strong = listOf(candidate("Dragon Breath", .98f, .94f), candidate("Bug Bite", .86f, .70f))
        val cases = listOf(
            weak to context,
            strong to context.map { it.copy(legalMoveNames = listOf("Dragon Breath")) },
            strong to context.map { if (it.side == OPPONENT) it.copy(legalMoveNames = emptyList()) else it },
            strong to context.take(1),
            strong.map { it.copy(similarity = .5f) } to context,
        )
        for ((scores, fighters) in cases) {
            val t = FastMoveAudioPreferenceTracker()
            t.observe(ArticleId("strict"), 1_000_000_000, scores, fighters, referenceMatchAccepted = true)
            assertNull(t.preference(PLAYER, fighters, 1_000_000_000))
        }
        val unaccepted = FastMoveAudioPreferenceTracker()
        unaccepted.observe(ArticleId("unaccepted"), 1_000_000_000, strong, context)
        assertNull(unaccepted.preference(PLAYER, context, 1_000_000_000))
    }

    @Test fun `two disjoint comparisons provide an expiring preference without converting scores to probability`() {
        val t = FastMoveAudioPreferenceTracker()
        observe(t, 1_000_000_000)
        observe(t, 1_200_000_000) // Same PCM overlap cannot count twice.
        assertNull(t.preference(PLAYER, context, 1_200_000_000))
        observe(t, 2_500_000_000)
        val p = t.preference(PLAYER, context, 2_500_000_000)!!
        assertEquals("Dragon Breath", p.moveName)
        assertEquals(listOf(ArticleId("clip-1000000000"), ArticleId("clip-2500000000")), p.evidenceIds)
        assertNull(t.preference(OPPONENT, context, 2_500_000_000))
        assertNull(t.preference(PLAYER, context, 8_500_000_001))
    }

    @Test fun `ties low scores missing references shared moves and illegal winners do not create a preference`() {
        val cases = listOf(
            weak.map { if (it.moveName == "Iron Tail") it.copy(similarity = .885f) else it } to context,
            weak.map { it.copy(similarity = .6f) } to context,
            weak.map { it.copy(changingSimilarity = .5f) } to context,
            weak.filterNot { it.moveName == "Iron Tail" } to context,
            weak to context.map { it.copy(legalMoveNames = context.first().legalMoveNames) },
            (listOf(candidate("Ember", .95f, .9f)) + weak) to context,
        )
        for ((scores, fighters) in cases) {
            val t = FastMoveAudioPreferenceTracker()
            observe(t, 1_000_000_000, scores, fighters); observe(t, 2_500_000_000, scores, fighters)
            assertNull(t.preference(PLAYER, fighters, 2_500_000_000))
        }
    }

    @Test fun `conflicting comparison clears preference and needs repeated evidence for its replacement`() {
        val t = FastMoveAudioPreferenceTracker()
        observe(t, 1_000_000_000); observe(t, 2_500_000_000)
        val iron = weak.map { if (it.moveName == "Iron Tail") it.copy(similarity = .92f, changingSimilarity = .8f) else it }
        observe(t, 4_000_000_000, iron)
        assertNull(t.preference(PLAYER, context, 4_000_000_000))
        observe(t, 5_500_000_000, iron)
        assertEquals("Iron Tail", t.preference(PLAYER, context, 5_500_000_000)?.moveName)
    }

    @Test fun `switching either side or resetting invalidates the old acoustic ownership`() {
        val t = FastMoveAudioPreferenceTracker()
        observe(t, 1_000_000_000); observe(t, 2_500_000_000)
        for (side in listOf(PLAYER, OPPONENT)) {
            val switched = context.map { if (it.side == side) it.copy(appearanceStartedAtNanos = 3_000_000_000) else it }
            assertNull(t.preference(PLAYER, switched, 3_000_000_000))
        }
        t.clear()
        assertNull(t.preference(PLAYER, context, 3_000_000_000))
    }
}
