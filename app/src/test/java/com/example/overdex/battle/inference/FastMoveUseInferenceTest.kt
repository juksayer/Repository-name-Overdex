package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
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
    @Test
    fun `corroborating recipient measurements become one side-attributed use`() {
        val inference = startedInference()
        inference.accept(article("species", 100, ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sealeo", 364
        )))

        assertEquals(0, inference.accept(article("pulse", 1_000_000_000, pulse(ActivePokemonSide.PLAYER))).size)
        assertEquals(0, inference.accept(article("visual", 1_120_000_000, visual(ActivePokemonSide.PLAYER))).size)
        assertEquals(0, inference.accept(article("cadence", 1_180_000_000, cadence(ActivePokemonSide.PLAYER))).size)
        val result = inference.accept(article("later", 1_700_000_000, RawTestimony("later"))).single()

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

        val first = inference.accept(article("second", 1_500_000_000, pulse(ActivePokemonSide.PLAYER))).single()
        val second = inference.flush().single()

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
        val use = inference.accept(article("later", 1_500_000_000, RawTestimony("later"))).single()

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
            "watermark", 6_400_000_001, RawTestimony("later")
        ))

        assertEquals(1, result.size)
        assertEquals("fast-move-use:resumed-fast", result.single().payload.useId)
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
