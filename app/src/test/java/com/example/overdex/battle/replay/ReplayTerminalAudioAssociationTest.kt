package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayTerminalAudioAssociationTest {
    private val fragment = ReplayAudioFragment("sound", 10*S, "capture", "reference", 0, 0, 600_000_000, .999f)
    private val audio = ReplayAudioRecovery(listOf(ReplayRecoveredCharge("Seed Bomb", 10*S, 2*S, listOf(fragment))),
        listOf(ReplayRecoveredCry(148, 13*S, S, listOf(fragment.copy(articleId = "cry", captureAtNanos = 13*S)))))
    private val charge = ReplayFinishingChargeCandidate("PLAYER", "Seed Bomb", 10*S, 10*S, 12*S, setOf("sound"), true)
    private val rows = listOf(event("count", 8, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3)),
        event("shield", 8, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2)), event("result", 16, ArchivedMatchEnded("WIN")))
    private fun resolve(extra: List<ArchivedRealityArticle> = emptyList(), recovery: ReplayAudioRecovery = audio,
                        charges: List<ReplayFinishingChargeCandidate> = listOf(charge), fast: List<ReplayFastMoveCycle> = emptyList(),
                        mirror: Boolean = false, switched: Boolean = false,
                        acceptsHp: (ArchivedRealityArticle) -> Boolean = { true }) = ReplayTerminalAudioAssociation.resolve(rows + extra,
        recovery, charges, fast, { side, _ -> if (side == "PLAYER") ReplayCombatant("Gourgeist", if (mirror) 148 else 711, 0)
            else ReplayCombatant("Dragonair", 148, 0) }, { _, _ -> 0 }, { _, _ -> if (switched) 14*S else null }, acceptsHp)

    @Test fun `independent final chain produces annotated association without fabricated count or replacement`() {
        val link = resolve().single()
        assertNull(link.replacementAtNanos)
        assertEquals(ReplayTerminalAudioAssociation.BASIS, link.basis)
        assertEquals(setOf("sound", "cry", "count", "result", "shield"), link.evidenceArticleIds.toSet())
        assertEquals(13*S, link.presentationImpactAtNanos)
    }
    @Test fun `result alone cry alone shield and mirror ambiguity never prove a KO`() {
        assertTrue(resolve(recovery = audio.copy(charges = emptyList())).isEmpty())
        assertTrue(resolve(recovery = audio.copy(cries = emptyList())).isEmpty())
        assertTrue(resolve(mirror = true).isEmpty())
        assertTrue(resolve(switched = true).isEmpty())
        assertTrue(resolve(listOf(event("shield", 9, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 1, 2)))).isEmpty())
        assertTrue(resolve(listOf(event("count", 9, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3)))).isEmpty())
        assertTrue(resolve(listOf(event("result", 17, ArchivedMatchEnded("LOSS")))).isEmpty())
    }
    @Test fun `the captured cry fragment bounds the faint even when its clip starts before the attack`() {
        val cry = audio.cries.single()
        val fragment = cry.fragments.single().copy(captureAtNanos = 9*S, queryOffsetNanos = 4*S + S/4,
            referenceOffsetNanos = S/4)
        val link = resolve(recovery = audio.copy(cries = listOf(cry.copy(fragments = listOf(fragment))))).single()
        assertEquals(9*S, link.faintSourceAtNanos)
        assertEquals(13*S, link.landingAtNanos)
        assertEquals(ReplayTimeBound(13*S + S/4, fragment.articleId), link.departedBy)
    }
    @Test fun `unrendered unknown uses and pending impacts remain competing evidence`() {
        for (at in listOf(6L, 11L, 14L)) assertTrue(resolve(listOf(event("unknown", at,
            ArchivedFastMoveUseObserved("u", "UNKNOWN", "UNKNOWN", evidenceKinds = emptyList(), basis = "TEST")))).isEmpty())
        assertTrue(resolve(recovery = audio.copy(fastEffects = listOf(ReplayRecoveredFastEffect("Unknown-side fast", 12*S, listOf(fragment))))).isEmpty())
        val witness = ReplayFastMoveWitness("PLAYER", 4*S, null, null, null, 0, 20*S, setOf("AUDIO"), setOf("pending"), setOf(4*S))
        assertTrue(resolve(fast = listOf(ReplayFastMoveCycle(witness, 4*S, 13*S))).isEmpty())
        assertTrue(resolve(charges = listOf(charge, charge.copy(side = "UNKNOWN", moveName = null, sourceAtNanos = 12*S))).isEmpty())
    }
    @Test fun `post cry sliver and missing media keep ending unresolved`() {
        assertTrue(resolve(listOf(event("tiny", 14, ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 100, 10, .000001f, 1080, 2400)))).isEmpty())
        assertTrue(resolve(recovery = audio.copy(unavailableReferences = listOf("Struggle"))).isEmpty())
        assertTrue(resolve(recovery = audio.copy(unavailableArtifacts = listOf("sound"))).isEmpty())
        assertTrue(resolve(listOf(event("stop", 11, ArchivedObservationSessionStoppedByUser))).isEmpty())
    }
    @Test fun `unclassified positive HP withholds the link without asserting survival`() {
        val live = event("hp", 14, ArchivedActiveHpBarMeasured("OPPONENT", 400, 600, 600, 617,
            .00001f, 1080, 2400, "RED")).copy(confidence = .99f)
        for (sample in listOf(live.copy(confidence = null), live.copy(confidence = .2f))) {
            val policy = ReplayHpEvidence(rows + sample)
            assertEquals(1, resolve(listOf(sample), acceptsHp = { policy.confirmsLiveHp(it) }).size)
        }
        val unclassified = live.copy(payload = (live.payload as ArchivedActiveHpBarMeasured).copy(fillColor = null))
        val unknownPolicy = ReplayHpEvidence(rows + unclassified)
        assertFalse(unknownPolicy.confirmsLiveHp(unclassified))
        assertTrue(resolve(listOf(unclassified), acceptsHp = { unknownPolicy.confirmsLiveHp(it) }).isEmpty())
        val policy = ReplayHpEvidence(rows + live)
        assertTrue(resolve(listOf(live), acceptsHp = { policy.confirmsLiveHp(it) }).isEmpty())
        assertTrue(resolve(listOf(live.copy(confidence = .2f)), recovery = audio.copy(cries = emptyList()),
            acceptsHp = { false }).isEmpty())
    }

    @Test fun `sound reclassification explains only unaccepted claims with exact shared source`() {
        fun sound(accepted: Boolean) = event("sound-reader", 10, ArchivedFastMoveSoundMeasured(true, onsetOffsetNanos = 100_000_000, peakAmplitude = .5f, referenceMatchAccepted = accepted))
        assertTrue(resolve(listOf(sound(false))).isEmpty())
        assertEquals(1, resolve(listOf(sound(false).copy(evidenceReferences = listOf("sound")))).size)
        assertTrue(resolve(listOf(sound(false).copy(monotonicTimeNanos = 11*S, evidenceReferences = listOf("sound")))).isEmpty())
        assertTrue(resolve(listOf(sound(true).copy(evidenceReferences = listOf("sound")))).isEmpty())
    }
    @Test fun `raw inventory retains every supported attack lane and unknown claims`() {
        val items = listOf(event("fast", 1, ArchivedFastMoveUseObserved("f", "UNKNOWN", "UNKNOWN", evidenceKinds = emptyList(), basis = "test")),
            event("charge", 2, ArchivedChargedMoveEnergySpent("PLAYER", "Gourgeist", "Seed Bomb", 40, "test")),
            event("tick", 3, ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .4f, .3f, .1f)),
            event("pulse", 4, ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 10f, 2)),
            event("announcement", 5, ArchivedChargeMoveUsedAnnounced))
        assertEquals(items.map { it.articleId }, ReplayPossibleDamageClaims.collect(items.reversed()).map { it.articleId })
    }
    private fun event(id: String, at: Long, p: ArchivedTestimonyPayload) = ArchivedRealityArticle(id, "test", at*1000, at*1000,
        "TEST", p, emptyList(), null, null, emptyList(), at*S)
    private companion object { const val S = 1_000_000_000L }
}
