package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayContradictedHpTest {
    private val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
        "/replay/moondrop-mismagius-hp-20261111.json")).readText())

    @Test fun `recorded obscured Mismagius fill cannot pin the remaining fight below actual HP`() {
        // Exact original Articles from f66d7125. Video GO+37.64s shows the
        // roughly 77 percent bar under a bright purple effect, not 40 percent.
        val original = MatchArchiveSerializer.serialize(archive)
        val falseReading = archive.articles.single { it.articleId == FALSE_HP }
        val at = falseReading.monotonicTimeNanos!!
        assertFalse(ReplayHpEvidence(archive.articles).accepts(falseReading))
        val replay = MatchReplayModel(archive)
        val subsequent = archive.articles.single { it.articleId == "8e277339-7693-4971-aa40-0959756d397e" }
        assertEquals(.7765958f, replay.sceneAt(subsequent.monotonicTimeNanos!! + 1).opponentHp!!.filledFraction!!, .001f)
        val later = archive.articles.first { article ->
            val bar = article.payload as? ArchivedActiveHpBarMeasured
            bar?.side == "OPPONENT" && article.monotonicTimeNanos!! > at + 10_000_000_000 &&
                bar.filledFraction in .54f.. .56f
        }
        assertEquals((later.payload as ArchivedActiveHpBarMeasured).filledFraction,
            replay.sceneAt(later.monotonicTimeNanos!! + 1).opponentHp!!.filledFraction!!, .001f)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(replay.sceneAt(at).opponentHp,
            MatchReplayModel(archive.copy(articles = archive.articles.reversed())).sceneAt(at).opponentHp)
    }

    @Test fun `contradicted fill does not discard an independent pulse in that frame`() {
        val records = evidence(.4f)
        val pulse = event("pulse", ArchivedHpBarBorderPulse("OPPONENT", "PRESENT", .8f), 2.0)
        val policy = ReplayHpEvidence(records + pulse)
        assertFalse(policy.accepts(records[1]))
        assertTrue(policy.accepts(pulse))
    }

    @Test fun `a lone terminal zero and a sustained low plateau are retained`() {
        val zero = evidence(0f)
        assertTrue(ReplayHpEvidence(zero).accepts(zero[1]))
        val sustained = evidence(.4f).toMutableList().apply { add(2, hp("second-low", 2.1, .4f)) }
        assertTrue(ReplayHpEvidence(sustained).accepts(sustained[1]))
    }

    @Test fun `one later high reading or a long gap cannot reject a low reading`() {
        val one = evidence(.4f).take(3)
        assertTrue(ReplayHpEvidence(one).accepts(one[1]))
        val late = evidence(.4f).take(2) + listOf(hp("a", 4.0, .77f), hp("b", 4.2, .77f), hp("c", 4.4, .77f))
        assertTrue(ReplayHpEvidence(late).accepts(late[1]))
    }

    @Test fun `switch faint or charge boundary prevents borrowing subsequent HP`() {
        for (boundary in listOf<ArchivedTestimonyPayload>(
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Roselia", 315),
            ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Mismagius", 429, "TEST_COUNT"),
            ArchivedChargeMoveUsedAnnounced,
        )) {
            val records = evidence(.4f) + event("boundary", boundary, 2.15)
            assertTrue(ReplayHpEvidence(records).accepts(records[1]))
        }
    }

    @Test fun `unresolved count loss and same species replacement still separate appearances`() {
        val records = evidence(.4f) + listOf(
            event("baseline", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 1.5),
            event("loss", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3), 2.15),
        )
        assertTrue(ReplayHpEvidence(records).accepts(records[1]))
        for (reason in listOf("SWITCH", "ENTRY")) {
            val switch = evidence(.4f) + event("same-species-entry",
                ArchivedSpeciesCheckMeasured("OPPONENT", 1, "OPENED", reason, 2_150_000_000L, 0, 1_000_000_000L), 2.15)
            assertTrue(ReplayHpEvidence(switch).accepts(switch[1]))
        }
    }

    @Test fun `co-timed transition preserves ambiguous HP but initial identity alone does not`() {
        val records = evidence(.4f)
        val firstIdentityAtReading = records.map {
            if (it.articleId == "identity") event("identity", it.payload, 2.0) else it
        }
        assertFalse(ReplayHpEvidence(firstIdentityAtReading).accepts(records[1]))
        for (boundary in listOf<ArchivedTestimonyPayload>(
            ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Mismagius", 429, "TEST_COUNT"),
            ArchivedChargeMoveUsedAnnounced,
            ArchivedSpeciesCheckMeasured("OPPONENT", 1, "OPENED", "SWITCH", 2_000_000_000L, 0, 1_000_000_000L),
        )) {
            assertTrue(ReplayHpEvidence(records + event("boundary", boundary, 2.0)).accepts(records[1]))
        }
        val countBoundary = records + listOf(
            event("baseline", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 1.5),
            event("loss", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3), 2.0),
        )
        assertTrue(ReplayHpEvidence(countBoundary).accepts(records[1]))
    }

    @Test fun `co-timed terminal zero and its damage survive a contradicted positive fill in either order`() {
        val original = evidence(.4f)
        val zero = hp("zero", 2.0, 0f)
        val damage = event("zero-damage", ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .77f, 0f, .77f), 2.0)
        for (records in listOf(original + zero + damage, (original + zero + damage).reversed())) {
            val policy = ReplayHpEvidence(records)
            assertFalse(policy.accepts(original[1]))
            assertTrue(policy.accepts(zero))
            assertTrue(policy.accepts(damage))
        }
    }

    @Test fun `same captured frame cannot serve as a later confirming image`() {
        val records = evidence(.4f).take(4) + hp("co-timed-high", 2.0, .77f)
        assertTrue(ReplayHpEvidence(records).accepts(records[1]))
        assertTrue(ReplayHpEvidence(records.reversed()).accepts(records[1]))
    }

    @Test fun `conflicting readers of a later frame cannot provide stable confirmation`() {
        val records = evidence(.4f) + hp("conflicting-later-frame", 2.2, .3f)
        assertTrue(ReplayHpEvidence(records).accepts(records[1]))
        assertTrue(ReplayHpEvidence(records.reversed()).accepts(records[1]))
    }

    private fun evidence(low: Float) = listOf(
        event("identity", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Mismagius", 429), 1.0),
        hp("low", 2.0, low), hp("a", 2.2, .77f), hp("b", 2.35, .77f), hp("c", 2.5, .77f),
    )
    private fun hp(id: String, at: Double, fraction: Float) = event(id,
        ArchivedActiveHpBarMeasured("OPPONENT", 392, 637, 584, 655, fraction), at)
    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Double) = ArchivedRealityArticle(
        id, "contradicted", (at * 1000).toLong(), (at * 1000).toLong(), "TEST", payload,
        emptyList(), null, null, emptyList(), (at * 1e9).toLong())

    companion object { private const val FALSE_HP = "47be66de-e034-4547-b7c4-f4a49fba64ac" }
}
