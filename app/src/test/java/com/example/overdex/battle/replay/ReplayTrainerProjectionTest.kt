package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType

class ReplayTrainerProjectionTest {
    private val start = 10_000_000_000L
    private fun scene(opponent: String = "Vaporeon") = ReplayScene(
        player = ReplayCombatant("Camerupt", 323, start), opponent = ReplayCombatant(opponent, 134, start),
        observedSpeciesNames = emptyList(), candidateSpeciesId = null, candidateSimilarity = null, outcome = null,
        playerGeneratedEnergy = 0, opponentGeneratedEnergy = 0, playerSpentEnergy = 0, opponentSpentEnergy = 0,
        fastMoveActions = emptyList(), chargedMoveActions = emptyList(), countdownGlyph = null,
        qteMilestone = null, qteMilestoneSide = null, latestEvidenceLabel = "source")
    private fun entry(kind: ReplayCorrectionKind, side: ReplayCorrectionSide, species: String, offset: Long,
                      move: String? = null, link: String? = null) = ReplayTrainerCorrection(
        UUID.randomUUID().toString(), "match", offset, kind, side, species, move, authorName = "Trainer", createdAtMillis = 1,
        finishingMoveId = link)

    @Test fun linkedMoveLandsAndCorrectRecipientFaintsWithAttribution() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 2_000_000_000, "Incinerate")
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", 2_000_000_000, link = hit.id)
        val projection = ReplayTrainerProjection("match", start, listOf(hit, faint))
        val inFlight = projection.apply(scene(), start + 1_650_000_000)
        assertEquals(1, inFlight.fastMoveActions.size)
        assertFalse(inFlight.opponent!!.isFainted)
        val landing = projection.apply(scene(), start + 2_000_000_000)
        assertEquals(1f, landing.fastMoveActions.single().progress)
        assertTrue(landing.opponent!!.isFainted)
        assertEquals(0f, landing.opponentHp!!.filledFraction!!)
        assertEquals("Incinerate", landing.opponent!!.trainerFinishingMove)
        val faded = projection.apply(scene(), start + 2_500_000_000)
        assertNotNull(ReplayCombatantEndPose.at(faded.opponent, start + 2_500_000_000, player = false))
        assertFalse(landing.player!!.isFainted)
        assertFalse(projection.apply(scene(), start + 1_000_000_000).opponent!!.isFainted)
        assertFalse(projection.apply(scene("Sealeo"), start + 2_000_000_000).opponent!!.isFainted)
        val sound = projection.soundEvents(emptyList()) { scene() }.single()
        assertEquals("Incinerate", sound.moveName)
        assertEquals(start + 1_300_000_000, sound.atNanos)
        assertEquals(listOf(sound), projection.soundEvents(listOf(sound)) { scene() })
        assertTrue(projection.soundEvents(emptyList()) { scene().copy(player = ReplayCombatant("Gourgeist", 711, start)) }.isEmpty())
    }

    @Test fun unknownAndInvalidLinksNeverGetKoCredit() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 3_000_000_000, "Incinerate")
        val earlier = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", 2_000_000_000, link = hit.id)
        val sameSide = earlier.copy(side = ReplayCorrectionSide.PLAYER, speciesName = "Camerupt", matchOffsetNanos = 4_000_000_000)
        val projection = ReplayTrainerProjection("match", start, listOf(hit, earlier, sameSide))
        assertNull(projection.creditedMove(earlier))
        assertNull(projection.creditedMove(sameSide))
        assertNull(projection.apply(scene(), start + 2_000_000_000).opponent!!.trainerFinishingMove)
    }

    @Test fun trainerConcessionMarksTheResultWithoutCreatingAnAttackOrFaint() {
        val offset = 2_000_000_000L
        val at = start + offset
        val source = scene().copy(outcome = "WIN", opponentHp = ReplayHpState(0.23f))
        val win = entry(ReplayCorrectionKind.CONCESSION, ReplayCorrectionSide.OPPONENT, "", offset)
        val invalidCredit = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.PLAYER, "Camerupt", offset, link = win.id)
        val projection = ReplayTrainerProjection("match", start, listOf(win))
        val marker = projection.markers().single()
        assertEquals(ReplayEventMarkerKind.CONCESSION, marker.kind)
        assertEquals("OPPONENT", marker.initiatingSide)
        assertEquals("Win by concession · Trainer entry by Trainer", marker.label)
        assertEquals(at, marker.atNanos)
        assertEquals(source, projection.apply(source, at - 1))
        assertEquals(source.copy(trainerConcession = ReplayCorrectionSide.OPPONENT), projection.apply(source, at))
        assertTrue(projection.soundEvents(emptyList()) { source }.isEmpty())
        assertNull(projection.creditedMove(invalidCredit))

        val loss = entry(ReplayCorrectionKind.CONCESSION, ReplayCorrectionSide.PLAYER, "", offset)
        val lossProjection = ReplayTrainerProjection("match", start, listOf(loss))
        assertEquals("Loss by concession · Trainer entry by Trainer", lossProjection.markers().single().label)
        assertEquals(ReplayCorrectionSide.PLAYER, lossProjection.apply(scene(), at).trainerConcession)
        val disputed = ReplayTrainerProjection("match", start, listOf(win, loss))
        assertEquals(2, disputed.markers().size)
        assertEquals(source, disputed.apply(source, at))
    }

    @Test fun trainerMoveDoesNotAddFaintOrDuplicateAnExistingAttack() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 2_000_000_000, "Incinerate")
        val projection = ReplayTrainerProjection("match", start, listOf(hit))
        val impact = start + hit.matchOffsetNanos
        val recorded = ReplayFastMoveAction("PLAYER", "Incinerate", null, impact - 500_000_000, 1f, impactAtNanos = impact)
        val result = projection.apply(scene().copy(fastMoveActions = listOf(recorded)), impact)
        assertEquals(listOf(recorded), result.fastMoveActions)
        assertFalse(result.opponent!!.isFainted)
        val laterAppearance = scene().copy(opponent = ReplayCombatant("Vaporeon", 134, impact + 1))
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", 2_000_000_000, link = hit.id)
        assertFalse(ReplayTrainerProjection("match", start, listOf(hit, faint)).apply(laterAppearance, impact + 5).opponent!!.isFainted)
    }

    @Test fun bareSpeciesCanMatchAFormWithoutAssumingItAndDifferentFormsStayDistinct() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Gourgeist", 2_000_000_000, "Incinerate")
        val bare = ReplayTrainerProjection("match", start, listOf(hit))
        val formScene = scene().copy(player = ReplayCombatant("Gourgeist (Average)", 711, start))
        assertEquals("Incinerate", bare.apply(formScene, start + 2_000_000_000).fastMoveActions.single().moveName)
        val explicit = ReplayTrainerProjection("match", start, listOf(hit.copy(speciesName = "Gourgeist (Super)")))
        assertTrue(explicit.apply(formScene, start + 2_000_000_000).fastMoveActions.isEmpty())
    }

    @Test fun trainerMoveUsesUnambiguousReferenceTypeEvenWhenAbsentFromSpeciesCatalogue() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Gourgeist", 2_000_000_000, "Incinerate")
        val fire = Move("Incinerate", PokemonType.FIRE, 1, 1, true)
        val formScene = scene().copy(player = ReplayCombatant("Gourgeist (Average)", 711, start))
        val projection = ReplayTrainerProjection("match", start, listOf(hit), mapOf("Camerupt" to listOf(fire)))
        assertEquals(PokemonType.FIRE, projection.apply(formScene, start + 2_000_000_000).fastMoveActions.single().type)
        val conflicting = ReplayTrainerProjection("match", start, listOf(hit),
            mapOf("Camerupt" to listOf(fire), "Other" to listOf(fire.copy(type = PokemonType.WATER))))
        assertNull(conflicting.apply(formScene, start + 2_000_000_000).fastMoveActions.single().type)
    }

    @Test fun reviewedKoDoesNotDuplicateAnAutomaticallyRecoveredAttackOrSound() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 2_000_000_000, "Incinerate")
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", 2_500_000_000, link = hit.id)
        val link = ReplayFinishingFastAssociation("PLAYER", "Camerupt", "OPPONENT", "Vaporeon", start,
            "Incinerate", start + 2_700_000_000, start + 2_700_000_000, start + 5_000_000_000,
            listOf("hit"), listOf("audio"), listOf("count"))
        val automatic = ReplayFastMoveAction("PLAYER", "Incinerate", PokemonType.FIRE, start, .7f,
            impactAtNanos = link.impactAtNanos, finishingAssociation = link)
        val p = ReplayTrainerProjection("match", start, listOf(hit, faint))
        val at = start + hit.matchOffsetNanos
        val result = p.apply(scene().copy(fastMoveActions = listOf(automatic)), at)
        assertEquals(1, result.fastMoveActions.size)
        assertEquals(setOf("TRAINER_ENTRY"), result.fastMoveActions.single().evidenceKinds)
        val sound = ReplayAttackSoundEvent(start, "Incinerate", false, "PLAYER", link)
        assertEquals(1, p.soundEvents(listOf(sound)) { scene().copy(fastMoveActions = listOf(automatic)) }.size)
        assertTrue(p.soundEvents(listOf(sound)) { scene().copy(fastMoveActions = listOf(automatic)) }.single().finishingAssociation == null)
    }

    @Test fun reviewedKoDoesNotDuplicateTheColorRecoveredAttackOrSound() {
        val hit = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 2_000_000_000, "Incinerate")
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", 3_000_000_000, link = hit.id)
        val impact = start + 2_080_000_000
        val kinds = setOf("MIXED_AUDIO_COMPLETE_BAR_COLOR_CHANGE")
        val automatic = ReplayFastMoveAction("PLAYER", "Incinerate", PokemonType.FIRE, start, .9f,
            evidenceKinds = kinds, impactAtNanos = impact)
        val p = ReplayTrainerProjection("match", start, listOf(hit, faint))
        val source = scene().copy(fastMoveActions = listOf(automatic))
        val result = p.apply(source, start + hit.matchOffsetNanos)
        assertEquals(1, result.fastMoveActions.size)
        assertEquals(setOf("TRAINER_ENTRY"), result.fastMoveActions.single().evidenceKinds)
        val sound = ReplayAttackSoundEvent(start, "Incinerate", false, "PLAYER", evidenceKinds = kinds, impactAtNanos = impact)
        assertEquals(1, p.soundEvents(listOf(sound)) { source }.size)
        assertTrue(p.soundEvents(listOf(sound)) { source }.single().evidenceKinds.isEmpty())
    }

    @Test fun chargedMoveUsesTrainerTimeAndDoesNotInventFaint() {
        val hit = entry(ReplayCorrectionKind.CHARGED_MOVE, ReplayCorrectionSide.PLAYER, "Camerupt", 2_000_000_000, "Rock Tomb")
        val projection = ReplayTrainerProjection("match", start, listOf(hit))
        val during = projection.apply(scene(), start + 1_650_000_000)
        assertEquals("Rock Tomb", during.chargedMoveActions.single().moveName)
        assertEquals(setOf("TRAINER_ENTRY"), during.chargedMoveActions.single().evidenceKinds)
        assertFalse(during.opponent!!.isFainted)
        assertTrue(projection.apply(scene(), start + 1_000_000_000).chargedMoveActions.isEmpty())
    }
}
