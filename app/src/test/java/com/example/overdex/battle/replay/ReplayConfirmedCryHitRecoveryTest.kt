package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayConfirmedCryHitRecoveryTest {
    private val s = 1_000_000_000L
    private fun row(id: String, at: Long, payload: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, "test", 0, 0, "TEST", payload, emptyList(), .99f, null, emptyList(), at)
    private val motion = row("motion", s, ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 2*s+s/2, 7f, 6))
    private val cry = row("cry", 2*s, ArchivedBattleCryLocalized(364, "cryref", .9f, 600_000_000L, "test"))
    private val repeat = row("repeat", 2*s, ArchivedRepeatCryDepartureCandidate(364, "OPPONENT", listOf("sealeo"), listOf("entry", "cry")))
    private val fragment = ReplayAudioFragment("audio", s, "hash", "ref", 0, 0, 400_000_000L, .6f)
    private val sound = ReplayMixedAudioCandidate("Incinerate", s, listOf(fragment, fragment.copy(referenceOffsetNanos = 400_000_000L)))
    private val ko = ReplayKnockout("OPPONENT", "Sealeo", 0, 5*s, 4*s, 4*s, listOf("ball-before", "ball-after"), 1)
    private fun actor(side: String, at: Long) = if (side == "PLAYER") ReplayCombatant("Gourgeist", 711, 0) else ReplayCombatant("Sealeo", 364, 0)
    private fun run(rows: List<ArchivedRealityArticle> = listOf(motion, cry, repeat), kos: List<ReplayKnockout> = listOf(ko),
                    other: Boolean? = false, same: (Long, Long) -> Boolean = { _, _ -> true }) =
        ReplayConfirmedCryHitRecovery.recover(rows, listOf(sound), kos, { "Incinerate" to 2*s+s/2 }, { _, _ -> other },
            ::actor, { _, _ -> 0 }, same)
    @Test fun `faint is independently resolved and sound plus motion can recover one move`() {
        val hit = run().single()
        assertEquals(2*s, hit.atNanos)
        assertEquals(setOf("audio", "motion", "cry", "repeat", "ball-before", "ball-after"), hit.articleIds)
        assertEquals(hit, run(listOf(repeat, motion, cry)).single())
    }
    @Test fun `living switch nonlethal and missing cues cannot create the hit`() {
        assertTrue(run(kos = emptyList()).isEmpty())
        assertTrue(run(rows = listOf(motion, cry)).isEmpty())
        assertTrue(run(rows = listOf(cry, repeat)).isEmpty())
        assertTrue(run(other = true).isEmpty())
        assertTrue(run(other = null).isEmpty())
        assertTrue(run(same = { _, _ -> false }).isEmpty())
        assertTrue(run(kos = listOf(ko.copy(verifiedPokeBallsAfter = null))).isEmpty())
    }
    @Test fun `a competing player charge prevents borrowing the previous sound`() {
        assertTrue(run(rows = listOf(motion, cry, repeat, row("charge", s+s/2, ArchivedChargeMoveUsedAnnounced))).isEmpty())
    }
}
