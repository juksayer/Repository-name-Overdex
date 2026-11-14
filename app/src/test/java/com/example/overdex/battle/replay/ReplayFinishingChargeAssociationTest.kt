package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayFinishingChargeAssociationTest {
    @Test fun `recorded Rock Tomb lands with resolved Camerupt faint and preserves both clocks and all articles`() {
        val archive = pairedMirrorArchive("moondrop")
        val before = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val model = pairedMirrorModel(input)
            val link = model.finishingChargeAssociations.single { it.moveName == "Rock Tomb" }
            assertEquals("PLAYER", link.attackingSide)
            assertEquals("OPPONENT", link.recipientSide)
            assertEquals("Camerupt", link.attackerSpeciesName)
            assertEquals("Camerupt", link.recipientSpeciesName)
            assertTrue(link.moveArticleIds.contains("d29c64c0-5986-4434-b83a-aa63064dd836"))
            assertTrue(link.faintArticleIds.contains("13aa76f7-2d80-4ac4-bbd3-aecf6fa41573"))
            assertTrue(link.shieldArticleIds.containsAll(listOf("d76de4da-c655-4d96-9a39-97c8187863b9", "08ebc8a3-fe14-4c6d-9475-3f9ae10705f0")))
            assertEquals(archive.articles.single { it.articleId == "13aa76f7-2d80-4ac4-bbd3-aecf6fa41573" }.monotonicTimeNanos, link.faintSourceAtNanos)
            val impact = model.sceneAt(link.presentationImpactAtNanos)
            assertTrue(impact.opponent!!.isFainted)
            assertEquals(link, impact.opponent.finishingChargeAssociation)
            assertEquals(link.faintSourceAtNanos, impact.opponent.faintedAtNanos)
            assertEquals(0f, impact.opponentHp!!.filledFraction)
            assertEquals(1f, impact.chargedMoveActions.single().progress, 0f)
            assertEquals(link, impact.chargedMoveActions.single().finishingAssociation)
            assertFalse(model.sceneAt(link.presentationImpactAtNanos - 1).opponent!!.isFainted)
            assertEquals("Gourgeist", model.sceneAt(requireNotNull(link.replacementAtNanos)).opponent!!.speciesName)
            assertFalse(model.sceneAt(requireNotNull(link.replacementAtNanos)).opponent!!.isFainted)
            assertEquals(impact, model.sceneAt(link.presentationImpactAtNanos)) // reverse scrub
            val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT && it.atNanos == link.presentationImpactAtNanos }
            assertEquals(link.faintSourceAtNanos, marker.sourceAtNanos)
            assertEquals(link.evidenceArticleIds, marker.sourceArticleIds)
            val chargeMarker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE &&
                it.sourceAtNanos == link.moveSourceAtNanos }
            assertTrue(chargeMarker.label.endsWith(" · KO"))
            assertEquals(link.evidenceArticleIds, chargeMarker.sourceArticleIds)
            assertEquals(15, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.FAST_MOVE })
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `Pixel mirror ambiguity and final missing faint are not filled from the other phone`() {
        for (device in listOf("pixel", "moondrop")) {
            val archive = pairedMirrorArchive(device)
            val before = MatchArchiveSerializer.serialize(archive)
            val model = pairedMirrorModel(archive)
            assertTrue(model.finishingChargeAssociations.none { it.recipientSpeciesName.startsWith("Gourgeist") })
            if (device == "pixel") {
                assertTrue(model.finishingChargeAssociations.none { it.moveName == "Rock Tomb" })
                assertEquals(10, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.FAST_MOVE })
            }
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `single resolved loss and unchanged shield bracket support association without an empty bar`() {
        val link = resolve().single()
        assertEquals(25*S, link.faintSourceAtNanos)
        assertEquals(14*S, link.presentationImpactAtNanos)
        assertEquals(listOf("charge"), link.moveArticleIds)
        assertEquals(listOf("count-after", "count-before", "replacement"), link.faintArticleIds)
        assertEquals(listOf("shields-before", "shields-after"), link.shieldArticleIds)
    }

    @Test fun `shielded unknown and contradictory shield outcomes do not get KO credit`() {
        assertTrue(resolve(rows = listOf(shield("shields-before", 11, 2))).isEmpty())
        assertTrue(resolve(rows = listOf(shield("shields-before", 11, 2), shield("shields-after", 18, 1))).isEmpty())
        assertTrue(resolve(rows = rows + shield("conflict", 11, 1)).isEmpty())
        assertTrue(resolve(rows = rows + shield("drop", 15, 1)).isEmpty())
    }

    @Test fun `surviving tiny HP post impact blocks link without changing that measurement`() {
        val tiny = event("tiny", 15, ArchivedActiveHpBarMeasured("OPPONENT", 10, 100, 203, 117, .0001f, 672, 1493))
        assertTrue(resolve(rows = rows + tiny).isEmpty())
        assertEquals(.0001f, (tiny.payload as ArchivedActiveHpBarMeasured).filledFraction, 0f)
    }

    @Test fun `pending or later unnamed fast attack remains separate and blocks charged KO credit`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val witness = ReplayFastMoveWitness(side, 15*S, null, null, null, 0, 30*S,
                setOf("HP_DAMAGE_TICK"), setOf("real-hit"), setOf(15*S))
            val pending = ReplayFastMoveCycle(witness, 10*S, 15*S)
            assertTrue(resolve(fast = listOf(pending)).isEmpty())
            // A reconstructed schedule cannot erase the competing original impact time.
            assertTrue(resolve(fast = listOf(pending.copy(impactAtNanos = 4*S))).isEmpty())
        }
    }

    @Test fun `recorded competing use cannot disappear just because decoration scheduling excluded it`() {
        val rawUse = event("pending-use", 13, ArchivedFastMoveUseObserved("pending", "PLAYER", "OPPONENT",
            evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "TEST"))
        assertTrue(resolve(rows = rows + rawUse).isEmpty())
    }

    @Test fun `switch alone aggregate loss unknown performer and wrong appearance cannot produce link`() {
        assertTrue(resolve(kos = emptyList()).isEmpty())
        assertTrue(resolve(kos = listOf(ko.copy(verifiedPokeBallsAfter = null))).isEmpty())
        assertTrue(resolve(charges = listOf(charge.copy(side = "UNKNOWN"))).isEmpty())
        assertTrue(resolve(charges = listOf(charge.copy(completed = false))).isEmpty())
        assertTrue(resolve(kos = listOf(ko.copy(speciesName = "Turtonator"))).isEmpty())
        assertTrue(resolve(kos = listOf(ko.copy(appearanceAtNanos = 18*S))).isEmpty())
        assertTrue(resolve(kos = listOf(ko.copy(replacementAtNanos = 30*S))).isEmpty())
        assertTrue(resolve(charges = listOf(charge.copy(preparationAtNanos = -1))).isEmpty())
        assertTrue(resolve(actorSwitch = true).isEmpty())
    }

    @Test fun `competing charge restart result and observation stop prevent association`() {
        assertTrue(resolve(charges = listOf(charge, charge.copy(side = "UNKNOWN", moveName = null, sourceAtNanos = 15*S))).isEmpty())
        for (p in listOf(ArchivedGetReadyWitnessed, ArchivedAttackIncoming, ArchivedObservationRestartedByUser,
            ArchivedObservationSessionStoppedByUser, ArchivedScreenIgnoredByUser, ArchivedMatchEnded("WIN"))) {
            assertTrue(p.toString(), resolve(rows = rows + event("boundary", 15, p)).isEmpty())
        }
    }

    private val charge = ReplayFinishingChargeCandidate("PLAYER", "Rock Tomb", 5*S, 12*S, 14*S, setOf("charge"), true)
    private val ko = ReplayKnockout("OPPONENT", "Camerupt", 0, 25*S, 17*S, 18*S,
        listOf("count-before", "count-after", "replacement"), 1, ReplayTeamLossProjection.BASIS)
    private val rows get() = listOf(shield("shields-before", 11, 2), shield("shields-after", 18, 2))
    private fun resolve(rows: List<ArchivedRealityArticle> = this.rows,
        charges: List<ReplayFinishingChargeCandidate> = listOf(charge), kos: List<ReplayKnockout> = listOf(ko),
        fast: List<ReplayFastMoveCycle> = emptyList(), actorSwitch: Boolean = false) = ReplayFinishingChargeAssociator.resolve(
        rows, charges, kos, fast,
        { _, _ -> ReplayCombatant("Camerupt", 323, 0) },
        { side, at -> if (at < 0) -S else if (side == "OPPONENT" && at >= 18*S) 18*S else 0 },
        { side, _ -> if (side == "OPPONENT") 18*S else if (actorSwitch) 16*S else null }, { true })
    private fun shield(id: String, at: Long, count: Int) = event(id, at, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", count, 2))
    private fun event(id: String, at: Long, p: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "test", at*1000, at*1000, "TEST", p, emptyList(), null, null, emptyList(), at*S)
    private companion object { const val S = 1_000_000_000L }
}
