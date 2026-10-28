package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.BattleDecisionTimerObserved
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic controls for delayed phases; recorded Charm fixtures live separately. */
class FastMovePlateauPairingTest {
    @Test fun `an unresolved move joins a late onset only when HP is observed at its post loss plateau`() {
        val loss = loss("loss", 1_000)
        val flash = pulse("flash", 1_804)
        val samples = plateau()
        val uses = evaluate(listOf(loss) + samples + flash)

        assertEquals(1, uses.size)
        val use = uses.single()
        assertEquals(ActivePokemonSide.OPPONENT, use.payload.attackingSide)
        assertEquals(loss.monotonicTimeNanos, use.observedArticle.monotonicTimeNanos)
        assertTrue(use.predecessorIds.containsAll((listOf(loss, flash) + samples).map { it.id }))
    }

    @Test fun `known Charm can join a plateau backed onset beyond its ordinary fusion window`() {
        val samples = listOf(hp("hp-0", 1_000), hp("hp-1", 1_500),
            hp("hp-2", 2_000), hp("hp-3", 2_139))
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 2_139), knownCharm = true)

        assertEquals(1, uses.size)
        assertHasTogether(uses.single(), "loss", "flash")
    }

    @Test fun `late onset without measured plateau remains independent`() {
        val uses = evaluate(listOf(loss("loss", 1_000), pulse("flash", 1_804)))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `a long gap in plateau samples cannot certify a delayed phase`() {
        val samples = listOf(hp("hp-0", 1_000), hp("hp-1", 1_100), hp("hp-2", 1_804))
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `a different fill between endpoints cannot certify a plateau`() {
        val samples = listOf(hp("hp-0", 1_000), hp("hp-1", 1_400, fraction = .92f), hp("hp-2", 1_804))
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `moved endcaps cannot certify a plateau even if the fraction is unchanged`() {
        val samples = listOf(hp("hp-0", 1_000), hp("hp-1", 1_400, left = 104, right = 304), hp("hp-2", 1_804))
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `a change of capture dimensions prevents a plateau certificate`() {
        val samples = listOf(hp("hp-0", 1_000), hp("hp-1", 1_400, sourceWidth = 720), hp("hp-2", 1_804))
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `other side HP cannot corroborate the damaged side plateau`() {
        val samples = plateau().map { sample ->
            sample.copy(payload = (sample.payload as ActiveHpBarMeasured).copy(side = ActivePokemonSide.OPPONENT))
        }
        val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `three neighboring half second attacks retain their own loss and onset`() {
        val input = (0..2).flatMap { index ->
            val at = 1_000L + index * 500L
            listOf(loss("loss-$index", at, before = 1f - index * .1f, after = .9f - index * .1f),
                hp("hp-$index", at, fraction = .9f - index * .1f), pulse("flash-$index", at + 100))
        }
        val uses = evaluate(input)
        assertEquals(3, uses.size)
        (0..2).forEach { index ->
            assertEquals(1, uses.count { hasTogether(it, "loss-$index", "flash-$index") })
        }
    }

    @Test fun `an intervening loss owns its nearby onset and an older loss cannot consume it`() {
        val input = listOf(loss("loss-0", 1_000), hp("hp-0", 1_000), hp("hp-1", 1_200),
            loss("loss-1", 1_400, before = .9f, after = .8f), hp("hp-2", 1_400, fraction = .8f),
            hp("hp-3", 1_800, fraction = .8f), pulse("flash", 1_804))
        val uses = evaluate(input)
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss-0", "flash")
        assertEquals(1, uses.count { hasTogether(it, "loss-1", "flash") })
    }

    @Test fun `one certified loss cannot absorb two separate pulse onsets`() {
        val input = listOf(loss("loss", 1_000), pulse("early-flash", 1_100)) + plateau() + pulse("later-flash", 1_804)
        val uses = evaluate(input)
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "early-flash", "later-flash")
    }

    @Test fun `late HP sample delivery can reconcile previously separate pending phases`() {
        val input = listOf(pulse("flash", 1_804, receivedAt = 2_000),
            loss("loss", 1_000, receivedAt = 2_100)) + plateau().mapIndexed { index, sample ->
            sample.copy(recordedAt = 2_150L + index * 50L)
        }
        val uses = evaluate(input)
        assertEquals(1, uses.size)
        assertHasTogether(uses.single(), "loss", "flash")
        assertEquals(1_000_000_000L, uses.single().observedArticle.monotonicTimeNanos)
    }

    @Test fun `late contradictory HP within pending delivery grace revokes an extended join`() {
        val input = listOf(loss("loss", 1_000)) + plateau() + pulse("flash", 1_804) +
            hp("contradictory-hp", 1_200, fraction = .92f).copy(recordedAt = 1_900)
        val uses = evaluate(input)
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `late receiver transition within pending delivery grace revokes an extended join`() {
        val input = listOf(loss("loss", 1_000)) + plateau() + pulse("flash", 1_804) +
            article("replacement", 1_400, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER,
                "Swampert", 260), receivedAt = 1_900)
        val uses = evaluate(input)
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `certified preceding loss wins over a nearer future loss delivered before the flash`() {
        val input = listOf(loss("prior-loss", 1_000, receivedAt = 1_100)) + plateau().map {
            it.copy(recordedAt = 1_950L)
        } + listOf(loss("future-loss", 2_518, before = .9f, after = .8f, receivedAt = 2_100),
            pulse("flash", 1_804, receivedAt = 2_200))
        val uses = evaluate(input, knownCharm = true)
        assertEquals(2, uses.size)
        assertEquals(1, uses.count { hasTogether(it, "prior-loss", "flash") })
        assertNeverJoined(uses, "future-loss", "flash")
    }

    @Test fun `receiver species transition breaks the delayed phase join`() {
        val boundary = article("replacement", 1_400,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Swampert", 260))
        val uses = evaluate(listOf(loss("loss", 1_000)) + plateau() + boundary + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `attacker species transition breaks the delayed phase join`() {
        val boundary = article("replacement", 1_400,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Mawile", 303))
        val uses = evaluate(listOf(loss("loss", 1_000)) + plateau() + boundary + pulse("flash", 1_804))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `receiver faint breaks the delayed phase join without requiring replacement OCR`() {
        val boundary = article("faint", 1_400,
            ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Melmetal", 809, "BALLS"))
        val uses = evaluate(listOf(loss("loss", 1_000)) + plateau() + boundary + pulse("flash", 1_804))
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `QTE keeps its in phase onset out of an earlier fast move`() {
        val input = listOf(loss("loss", 1_000)) + plateau() +
            listOf(article("qte", 1_400, GetReadyWitnessed), pulse("flash", 1_804))
        val uses = evaluate(input)
        assertEquals(1, uses.size)
        assertTrue(uses.single().predecessorIds.none { it.value == "flash" })
    }

    @Test fun `a cleared decision interval still breaks a delayed phase join`() {
        val input = listOf(loss("loss", 1_000)) + plateau() + listOf(
            article("decision", 1_300, BattleDecisionTimerObserved(0, "0", "TEST")),
            article("decision-clear", 1_400, BattleDecisionTimerObserved(null, "", "TEST")),
            pulse("flash", 1_804))
        val uses = evaluate(input)
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "flash")
    }

    @Test fun `ordinary pulse before damage still merges without plateau measurements`() {
        val uses = evaluate(listOf(pulse("flash", 1_000), loss("loss", 1_150)))
        assertEquals(1, uses.size)
        assertHasTogether(uses.single(), "loss", "flash")
    }

    @Test fun `uncertified older loss does not permanently split a nearby pulse before damage pair`() {
        val uses = evaluate(listOf(loss("older-loss", 1_000), pulse("flash", 1_700),
            loss("current-loss", 1_800, before = .9f, after = .8f)))
        assertEquals(2, uses.size)
        assertEquals(1, uses.count { hasTogether(it, "current-loss", "flash") })
        assertNeverJoined(uses, "older-loss", "flash")
    }

    @Test fun `narrow or thick false bar rectangles cannot certify a plateau`() {
        for ((width, height) in listOf(3 to 20, 134 to 26)) {
            val samples = listOf(1_000L, 1_400L, 1_804L).mapIndexed { i, at ->
                article("bad-shape-$i", at, ActiveHpBarMeasured(ActivePokemonSide.PLAYER,
                    100, 400, 100 + width, 400 + height, .9f, 1080, 2400))
            }
            val uses = evaluate(listOf(loss("loss", 1_000)) + samples + pulse("flash", 1_804))
            assertEquals(2, uses.size)
            assertNeverJoined(uses, "loss", "flash")
        }
    }

    @Test fun `a phase arriving beyond publication grace cannot rewrite an already emitted use`() {
        val uses = evaluate(listOf(loss("loss", 1_000)) + plateau() + listOf(
            article("later-watermark", 2_700, RawTestimony("later capture")),
            pulse("late-flash", 1_804, receivedAt = 2_800)))
        assertEquals(2, uses.size)
        assertNeverJoined(uses, "loss", "late-flash")
        assertEquals(listOf(1_000_000_000L, 1_804_000_000L), uses.map { it.observedArticle.monotonicTimeNanos })
    }

    private fun evaluate(input: List<RealityArticle>, knownCharm: Boolean = false): List<FastMoveUseDerivation> {
        val inference = FastMoveUseInference()
        inference.accept(article("start", 0, MatchStarted))
        inference.accept(article("attacker", 1,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Gardevoir", 282)))
        inference.accept(article("receiver", 2,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Melmetal", 809)))
        if (knownCharm) inference.observeIdentity(FastMoveIdentified(ActivePokemonSide.OPPONENT,
            "Gardevoir", "Charm", 1_500_000_000L, null, 0, "AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE"))
        return input.flatMap(inference::accept) + inference.flush()
    }

    private fun plateau() = listOf(hp("hp-0", 1_000), hp("hp-1", 1_400), hp("hp-2", 1_804))

    private fun hp(id: String, at: Long, fraction: Float = .9f, left: Int = 100, right: Int = 300,
        sourceWidth: Int = 1080) = article(id, at, ActiveHpBarMeasured(ActivePokemonSide.PLAYER,
        left, 400, right, 420, fraction, sourceWidth, 2400))

    private fun loss(id: String, at: Long, before: Float = 1f, after: Float = .9f, receivedAt: Long = at) =
        article(id, at, ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, before, after, before - after), receivedAt)

    private fun pulse(id: String, at: Long, receivedAt: Long = at) = article(id, at,
        HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, .3f, 6), receivedAt)

    private fun hasTogether(use: FastMoveUseDerivation, first: String, second: String): Boolean =
        use.predecessorIds.map { it.value }.containsAll(listOf(first, second))

    private fun assertHasTogether(use: FastMoveUseDerivation, first: String, second: String) =
        assertTrue("Expected $first and $second in the same use", hasTogether(use, first, second))

    private fun assertNeverJoined(uses: List<FastMoveUseDerivation>, first: String, second: String) =
        assertTrue("Must not join $first with $second", uses.none { hasTogether(it, first, second) })

    private fun article(id: String, atMillis: Long, payload: TestimonyPayload, receivedAt: Long = atMillis) =
        RealityArticle(id = ArticleId(id), perceivedAt = atMillis, recordedAt = receivedAt,
            sourceId = SourceId("TEST"), payload = payload, matchId = MatchId("synthetic-plateau-pairing"),
            monotonicTimeNanos = atMillis * 1_000_000L)
}
