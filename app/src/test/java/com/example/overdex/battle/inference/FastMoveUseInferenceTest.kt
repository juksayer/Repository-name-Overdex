package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.model.BattleResult
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastMoveUseInferenceTest {
    @Test fun `decision timer blocks impacts including late delivery after its clearance`() {
        val inference = startedInference()
        fun timer(seconds: Int?) = com.example.overdex.battle.custody.BattleDecisionTimerObserved(
            seconds, seconds?.toString().orEmpty(), "TEST")
        // The dial may arrive after a noisy HP pulse with a later capture time.
        inference.accept(article("early-noise", 1_100_000_000L, pulse(ActivePokemonSide.PLAYER)))
        inference.accept(article("dial", 1_000_000_000L, timer(4)))
        inference.accept(article("paused", 2_000_000_000L, pulse(ActivePokemonSide.PLAYER)))
        inference.accept(article("clear", 3_000_000_000L, timer(null)))
        inference.accept(article("late-noise", 2_500_000_000L, pulse(ActivePokemonSide.OPPONENT)))
        assertTrue(inference.flush().isEmpty())
        inference.accept(article("real", 3_500_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertEquals("fast-move-use:real", inference.flush().single().payload.useId)
    }

    @Test fun `decision timer cannot suppress the rest of a match if clearance is missed`() {
        val inference = startedInference()
        inference.accept(article("dial", 1_000_000_000L,
            com.example.overdex.battle.custody.BattleDecisionTimerObserved(2, "2", "TEST")))
        inference.accept(article("paused", 3_500_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertTrue(inference.flush().isEmpty())
        inference.accept(article("resumed", 4_500_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertEquals("fast-move-use:resumed", inference.flush().single().payload.useId)
    }

    @Test fun `lingering badge after faint cannot attribute replacement attacks to the corpse`() {
        val inference = startedInference()
        val side = ActivePokemonSide.PLAYER
        inference.accept(article("camerupt", 1_000_000_000L, ActivePokemonSpeciesWitnessed(side, "Camerupt", 323)))
        inference.accept(article("ko", 2_000_000_000L,
            com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed(side, "Camerupt", 323, "BALLS")))
        inference.accept(article("stale-badge", 2_400_000_000L, ActivePokemonSpeciesWitnessed(side, "Camerupt", 323)))
        inference.accept(article("hit", 3_000_000_000L, pulse(ActivePokemonSide.OPPONENT)))
        assertEquals(null, inference.flush().single().payload.attackerSpeciesName)
        inference.accept(article("replacement", 4_000_000_000L, ActivePokemonSpeciesWitnessed(side, "Galvantula", 596)))
        inference.accept(article("hit2", 5_000_000_000L, pulse(ActivePokemonSide.OPPONENT)))
        assertEquals("Galvantula", inference.flush().single().payload.attackerSpeciesName)
    }

    @Test
    fun `audio cue fires at first impact before fusion and is not repeated for corroboration`() {
        val cues = mutableListOf<RealityArticle>()
        val inference = FastMoveUseInference { cues += it }
        inference.accept(article("start", 0, MatchStarted))
        assertTrue(inference.accept(article("pulse", 1_000_000_000L, pulse(ActivePokemonSide.PLAYER))).isEmpty())
        assertEquals(listOf("pulse"), cues.map { it.id.value })
        inference.accept(article("tick", 1_200_000_000L,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, 1f, .9f, .1f)))
        assertEquals(1, cues.size)
        assertEquals(1, inference.flush().size)
    }

    @Test
    fun `late delivered pre-start measurements cannot become fast moves`() {
        val inference = FastMoveUseInference()
        inference.accept(article("start", 4_000_000_000L, MatchStarted))
        inference.accept(article("old-pulse", 3_500_000_000L, pulse(ActivePokemonSide.PLAYER)))
        inference.accept(article("old-fill", 3_600_000_000L,
            PlayerChargeMoveEnergyFillIncreased(listOf(0f, 0f), listOf(.1f, .1f), listOf(0, 1))))
        assertTrue(inference.flush().isEmpty())
    }

    @Test
    fun `late faint of a previous species does not erase current attack attribution`() {
        val inference = startedInference()
        inference.accept(article("vaporeon", 1_000_000_000L,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))
        inference.accept(article("sealeo-faint", 2_000_000_000L,
            com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364, "BALLS")))
        inference.accept(article("hit", 2_100_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertEquals("Vaporeon", inference.flush().single().payload.attackerSpeciesName)
    }

    @Test
    fun `corroborating recipient measurements become one side-attributed use`() {
        val inference = startedInference()
        inference.accept(article("species", 100, ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sealeo", 364
        )))

        assertEquals(0, inference.accept(article("pulse", 1_000_000_000, pulse(ActivePokemonSide.PLAYER))).size)
        assertEquals(0, inference.accept(article("visual", 1_120_000_000, visual(ActivePokemonSide.PLAYER))).size)
        assertEquals(0, inference.accept(article("cadence", 1_180_000_000, cadence(ActivePokemonSide.PLAYER))).size)
        val result = inference.accept(article("later", 2_700_000_000, RawTestimony("later"))).single()

        assertEquals(ActivePokemonSide.OPPONENT, result.payload.attackingSide)
        assertEquals(ActivePokemonSide.PLAYER, result.payload.damagedSide)
        assertEquals("Sealeo", result.payload.attackerSpeciesName)
        assertEquals("species", result.payload.appearanceId)
        assertEquals(
            listOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT", "HP_BORDER_CADENCE"),
            result.payload.evidenceKinds
        )
        assertEquals(listOf("pulse", "visual", "cadence"), result.predecessorIds.map { it.value })
    }

    @Test
    fun `consecutive one-turn moves remain separate uses`() {
        val inference = startedInference()
        inference.accept(article("first", 1_000_000_000, pulse(ActivePokemonSide.PLAYER)))

        inference.accept(article("second", 1_500_000_000, pulse(ActivePokemonSide.PLAYER)))
        val (first, second) = inference.flush()

        assertNotEquals(first.payload.useId, second.payload.useId)
        assertEquals("fast-move-use:first", first.payload.useId)
        assertEquals("fast-move-use:second", second.payload.useId)
    }

    @Test
    fun `absent aperture output is preserved but is not a fast move use`() {
        val inference = startedInference()

        assertTrue(inference.accept(article(
            "absent",
            1_000_000_000,
            HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.ABSENT)
        )).isEmpty())
        assertTrue(inference.flush().isEmpty())
    }

    @Test
    fun `one measured damage tick records one use by the opposite side`() {
        val inference = startedInference()

        inference.accept(article(
            "damage",
            1_000_000_000,
            ActiveHpBarDamageTickMeasured(
                damagedSide = ActivePokemonSide.OPPONENT,
                beforeFraction = 0.72f,
                afterFraction = 0.66f,
                lostFraction = 0.06f
            )
        ))
        val use = inference.accept(article("later", 2_700_000_000, RawTestimony("later"))).single()

        assertEquals(ActivePokemonSide.PLAYER, use.payload.attackingSide)
        assertEquals(ActivePokemonSide.OPPONENT, use.payload.damagedSide)
        assertEquals(listOf("HP_DAMAGE_TICK"), use.payload.evidenceKinds)
    }

    @Test
    fun `simultaneous evidence stays separated by attacker side`() {
        val inference = startedInference()
        inference.accept(article("opponent-hit", 1_000_000_000, pulse(ActivePokemonSide.PLAYER)))
        inference.accept(article("player-hit", 1_050_000_000, pulse(ActivePokemonSide.OPPONENT)))

        val uses = inference.flush().map { it.payload }.sortedBy { it.attackingSide.name }

        assertEquals(listOf(ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER), uses.map { it.attackingSide })
    }

    @Test
    fun `match end flushes the final observed use without waiting for another frame`() {
        val inference = startedInference()
        inference.accept(article("last-hit", 1_000_000_000, pulse(ActivePokemonSide.PLAYER)))

        val finalUse = inference.accept(
            article("end", 1_100_000_000, MatchEnded(BattleResult.WIN))
        ).single()

        assertEquals("fast-move-use:last-hit", finalUse.payload.useId)
    }

    @Test
    fun `species change closes the previous appearance before starting the next`() {
        val inference = startedInference()
        inference.accept(article("sneasel", 100, ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sneasel", 215
        )))
        inference.accept(article("old-hit", 1_000_000_000, pulse(ActivePokemonSide.PLAYER)))

        val oldUse = inference.accept(article("sealeo", 1_100_000_000, ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sealeo", 364
        ))).single()
        inference.accept(article("new-hit", 1_200_000_000, pulse(ActivePokemonSide.PLAYER)))
        val newUse = inference.flush().single()

        assertEquals("Sneasel", oldUse.payload.attackerSpeciesName)
        assertEquals("sneasel", oldUse.payload.appearanceId)
        assertEquals("Sealeo", newUse.payload.attackerSpeciesName)
        assertEquals("sealeo", newUse.payload.appearanceId)
    }

    @Test
    fun `charged move impact cannot masquerade as a fast move use`() {
        val inference = startedInference()
        inference.accept(article("charge-start", 1_000_000_000, AttackIncoming))
        inference.accept(article("charge-used", 5_000_000_000, ChargeMoveUsedAnnounced))

        assertTrue(inference.accept(article(
            "charged-impact", 5_200_000_000, pulse(ActivePokemonSide.PLAYER)
        )).isEmpty())
        assertTrue(inference.accept(article(
            "resumed-fast", 5_900_000_000, pulse(ActivePokemonSide.PLAYER)
        )).isEmpty())
        val result = inference.accept(article(
            "watermark", 7_600_000_001, RawTestimony("later")
        ))

        assertEquals(1, result.size)
        assertEquals("fast-move-use:resumed-fast", result.single().payload.useId)
    }

    @Test
    fun `idle motion shading and unclassified effects alone cannot establish a use`() {
        val inference = startedInference()
        inference.accept(article("motion", 1_000_000_000, ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.PLAYER, 500_000_000, 8f, 4
        )))
        inference.accept(article("fill", 1_100_000_000, PlayerChargeMoveEnergyFillIncreased(
            listOf(0f, 0f), listOf(0.9f, 0.2f), listOf(0, 1)
        )))
        inference.accept(article("effect", 1_200_000_000, visual(ActivePokemonSide.OPPONENT)))

        assertTrue(inference.flush().isEmpty())
    }

    @Test
    fun `supporting motion keeps its provenance but impact determines use time`() {
        val inference = startedInference()
        inference.accept(article("motion", 1_000_000_000, ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.PLAYER, 500_000_000, 8f, 4
        )))
        inference.accept(article("hit", 1_200_000_000, pulse(ActivePokemonSide.OPPONENT)))

        val use = inference.flush().single()
        assertEquals("hit", use.observedArticle.id.value)
        assertEquals(listOf("motion", "hit"), use.predecessorIds.map { it.value })
        assertEquals(ActivePokemonSide.PLAYER, use.payload.attackingSide)
    }

    @Test
    fun `countdown charge shading cannot become a pre-start fast move`() {
        val inference = FastMoveUseInference()

        inference.accept(article(
            "countdown-shade",
            900_000_000,
            com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased(
                beforeBySlot = listOf(0.1f, 0.1f),
                afterBySlot = listOf(0.2f, 0.2f),
                changedSlots = listOf(0, 1)
            )
        ))
        inference.accept(article("start", 1_000_000_000, MatchStarted))

        assertTrue(inference.accept(article("watermark", 1_500_000_001, RawTestimony("later"))).isEmpty())
        assertTrue(inference.flush().isEmpty())
    }

    @Test
    fun `recorded Sealeo HP steps remain nine separate opponent uses`() {
        val inference = startedInference()
        inference.accept(article("sealeo", 36_800_000_000L,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        val inputs = requireNotNull(javaClass.getResourceAsStream("/battle/oct12/sealeo-damage-steps.tsv"))
            .bufferedReader().useLines { lines -> lines.filterNot { it.startsWith("#") }.map { line ->
                val (id, nanos, before, after) = line.split('\t')
                article(id, nanos.toLong(), ActiveHpBarDamageTickMeasured(
                    ActivePokemonSide.PLAYER, before.toFloat(), after.toFloat(), before.toFloat() - after.toFloat()))
            }.toList() }
        val uses = inputs.flatMap(inference::accept) + inference.flush()
        assertEquals(9, uses.size)
        assertEquals(inputs.map { it.id }, uses.map { it.observedArticle.id })
        assertEquals(inputs.map { it.monotonicTimeNanos }, uses.map { it.observedArticle.monotonicTimeNanos })
        assertTrue(uses.all { it.payload.attackingSide == ActivePokemonSide.OPPONENT && it.payload.attackerSpeciesName == "Sealeo" })
    }

    @Test
    fun `delayed entry request cannot split one hit or erase its known attacker`() {
        val inference = startedInference()
        inference.accept(article("sneasel", 21_000_000_000L,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        inference.accept(article("tick", 22_914_000_000L,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, 1f, .95f, .05f)))
        inference.accept(article("request", 23_217_000_000L,
            com.example.overdex.battle.custody.SpeciesCheckMeasured(
                ActivePokemonSide.OPPONENT, 1, "OPENED", "ENTRY", 20_783_000_000L, 0, 1_500_000_000L)))
        inference.accept(article("pulse", 23_345_000_000L, pulse(ActivePokemonSide.PLAYER)))
        val use = inference.flush().single()
        assertEquals("Sneasel", use.payload.attackerSpeciesName)
        assertEquals(setOf("tick", "pulse"), use.predecessorIds.map { it.value }.toSet())
    }

    @Test
    fun `known Incinerate merges staggered flash and HP loss without generating uses from its duration`() {
        val inference = startedInference()
        inference.accept(article("camerupt", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        inference.observeIdentity(com.example.overdex.battle.custody.FastMoveIdentified(
            ActivePokemonSide.PLAYER, "Camerupt", "Incinerate", 2_500_000_000L, null, 0,
            "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"))
        assertTrue(inference.accept(article("idle", 50_000_000_000L, RawTestimony("idle"))).isEmpty())
        inference.accept(article("fill", 60_010_000_000L,
            PlayerChargeMoveEnergyFillIncreased(listOf(.1f, .1f), listOf(.2f, .2f), listOf(0, 1))))
        inference.accept(article("tick", 60_311_000_000L,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, .7f, .6f, .1f)))
        inference.accept(article("other-work", 61_100_000_000L, RawTestimony("later receipt")))
        inference.accept(article("pulse", 60_997_000_000L, pulse(ActivePokemonSide.OPPONENT)))
        val use = inference.flush().single()
        assertEquals(60_311_000_000L, use.observedArticle.monotonicTimeNanos)
        assertEquals(setOf("tick", "pulse", "fill"), use.predecessorIds.map { it.value }.toSet())
    }

    @Test
    fun `late delivery corroborates the original hit rather than generating another`() {
        val inference = startedInference()
        inference.accept(article("tick", 1_000_000_000L,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .7f, .6f, .1f)))
        assertTrue(inference.accept(article("advanced", 2_100_000_000L, RawTestimony("receipt"))).isEmpty())
        inference.accept(article("late-pulse", 1_200_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertEquals(1, inference.flush().size)
    }

    @Test
    fun `two pulse onsets 420 ms apart remain separate even without a name`() {
        val inference = startedInference()
        inference.accept(article("pulse1", 1_000_000_000L, pulse(ActivePokemonSide.PLAYER)))
        inference.accept(article("pulse2", 1_420_000_000L, pulse(ActivePokemonSide.PLAYER)))
        assertEquals(2, inference.flush().size)
    }

    private fun startedInference() = FastMoveUseInference().also {
        it.accept(article("start", 0, MatchStarted))
    }

    private fun pulse(damagedSide: ActivePokemonSide) = HpBarBorderPulse(
        barSide = damagedSide,
        status = ApertureStatus.PRESENT,
        peakColorDistance = 0.3f,
        sampleCount = 6
    )

    private fun cadence(damagedSide: ActivePokemonSide) = ActiveHpBarBorderCadenceMeasured(
        damagedBarSide = damagedSide,
        intervalNanos = 500_000_000,
        peakColorDistance = 0.3f,
        sampleCount = 6
    )

    private fun visual(damagedSide: ActivePokemonSide) = FastMoveRecipientVisualArtifactMeasured(
        damagedSide = damagedSide,
        changedPixelFraction = 0.2f,
        meanColorDistance = 0.3f,
        meanRed = 0.8f,
        meanGreen = 0.2f,
        meanBlue = 0.1f,
        centroidX = 0.5f,
        centroidY = 0.5f,
        changedSampleCount = 40
    )

    private fun article(id: String, atNanos: Long, payload: com.example.overdex.battle.custody.TestimonyPayload) =
        RealityArticle(
            id = ArticleId(id),
            perceivedAt = atNanos / 1_000_000,
            recordedAt = atNanos / 1_000_000,
            sourceId = SourceId("TEST"),
            payload = payload,
            matchId = MatchId("match"),
            monotonicTimeNanos = atNanos
        )
}
