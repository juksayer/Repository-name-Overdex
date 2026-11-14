package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.*
import org.junit.Assert.*
import org.junit.Test

class ReplayCorroboratedHpTimingTest {
    @Test fun `only independent HP proof for the same side and appearance advances the fall`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val owner = TeamLossCandidate("entry", 10, "Sealeo", 364)
            val loss = TeamLossResolution("drop", 30, 2, 1, listOf(owner), owner, listOf("before", "after"))
            val faint = ArchivedRealityArticle("faint", "match", 30, 40, "TEST",
                ArchivedActivePokemonFaintedWitnessed(side, "Sealeo", 364, ReplayTeamLossProjection.BASIS),
                emptyList(), .99f, null, emptyList(), 30)
            val proof = VerifiedHpFaintResolution("empty", 20, owner, listOf("entry", "red", "empty"))
            val appearances = mapOf(side to listOf(ReplayIdentityObservation(side, "Sealeo", 364, 10, "TEST")))
            fun track(hpSide: String, proofs: List<VerifiedHpFaintResolution>) = ReplayKnockoutTrack(
                listOf(faint), appearances, mapOf("faint" to loss),
                corroboratingHpLosses = mapOf(hpSide to proofs)).knockouts.single()
            val supported = track(side, listOf(proof))
            assertEquals(20, supported.presentationAtNanos)
            assertEquals(30, supported.verifiedPokeBallsAtNanos)
            assertEquals(1, supported.verifiedPokeBallsAfter)
            assertTrue(supported.sourceArticleIds.containsAll(listOf("before", "after", "red", "empty")))
            for (invalid in listOf(
                proof.copy(owner = owner.copy(appearanceAtNanos = 5)), // same species, different visit
                proof.copy(owner = owner.copy(speciesName = "Carbink", speciesId = 703)),
                proof.copy(owner = owner.copy(speciesId = 999)),
                proof.copy(atNanos = 9),
                proof.copy(atNanos = 35),
            )) assertEquals(30, track(side, listOf(invalid)).presentationAtNanos)
            assertEquals(30, track(if (side == "PLAYER") "OPPONENT" else "PLAYER", listOf(proof)).presentationAtNanos)
            assertEquals(30, track(side, listOf(proof, proof.copy(emptyArticleId = "conflicting", atNanos = 21))).presentationAtNanos)
        }
    }
}
