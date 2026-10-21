package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleWitnessContracts
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class CountdownMatchStartInferenceTest {
    @Test
    fun `October 12 countdown three plus first damage confirms estimated start without fabricating GO`() {
        val inference = CountdownMatchStartInference()
        val three = glyph("3", 21_709)
        assertNull(inference.accept(three))
        val hit = damage(26_836)
        val start = inference.accept(hit)!!
        assertEquals(MatchStarted, start.payload)
        assertEquals(24_709_000_000L, start.monotonicTimeNanos)
        assertEquals(24_709L, start.perceivedAt)
        assertEquals(listOf(three.id, hit.id), start.predecessorIds)
        assertEquals("COUNTDOWN_AND_COMBAT_START_INFERENCE", start.sourceId.id)
        assertNull(inference.accept(damage(27_000)))

        val moves = FastMoveUseInference()
        moves.accept(start)
        moves.accept(hit)
        assertEquals(ActivePokemonSide.OPPONENT, moves.flush().single().payload.attackingSide)
    }

    @Test
    fun `each accepted number can predict GO but needs combat afterwards`() {
        for (number in 1..3) {
            val inference = CountdownMatchStartInference()
            assertNull(inference.accept(glyph(number.toString(), (10 - number) * 1_000L)))
            assertEquals(10_000_000_000L, inference.accept(damage(10_500))?.monotonicTimeNanos)
        }
    }

    @Test
    fun `names countdown shading and absent pulses do not confirm combat`() {
        val inference = CountdownMatchStartInference()
        inference.accept(glyph("1", 1_000))
        assertNull(inference.accept(article("name", 2_500, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215))))
        assertNull(inference.accept(article("fill", 2_500, PlayerChargeMoveEnergyFillIncreased(listOf(0f, 0f), listOf(.1f, .1f), listOf(0, 1)))))
        assertNull(inference.accept(article("absent", 2_500, HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.ABSENT))))
        assertNotNull(inference.accept(article("pulse", 2_600, HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, 40f, 3))))
    }

    @Test
    fun `combat before expected GO or long after an abandoned countdown cannot confirm start`() {
        val inference = CountdownMatchStartInference()
        inference.accept(glyph("3", 1_000))
        assertNull(inference.accept(damage(3_500)))
        assertNull(inference.accept(damage(35_000)))
    }

    @Test
    fun `late countdown delivery uses captured time with already recorded combat`() {
        val inference = CountdownMatchStartInference()
        assertNull(inference.accept(damage(4_500)))
        assertEquals(4_000_000_000L, inference.accept(glyph("3", 1_000))?.monotonicTimeNanos)
    }

    @Test
    fun `ignored or restarted attempts discard countdown predictions`() {
        for (reset in listOf(ScreenIgnoredByUser, ObservationRestartedByUser)) {
            val inference = CountdownMatchStartInference()
            inference.accept(glyph("1", 1_000))
            inference.accept(article("reset", 1_500, reset))
            assertNull(inference.accept(damage(2_500)))
        }
    }

    @Test
    fun `untrusted glyph source cannot establish a prediction`() {
        val inference = CountdownMatchStartInference()
        inference.accept(glyph("3", 1_000).copy(sourceId = SourceId("UNRELATED_OCR")))
        assertNull(inference.accept(damage(4_500)))
    }

    private fun glyph(value: String, millis: Long) = article("glyph-$value", millis, CountdownGlyphWitnessed(value, .986f, 1))
        .copy(sourceId = SourceId(BattleWitnessContracts.countdownGlyph.witnessId))

    private fun damage(millis: Long) = article("damage-$millis", millis,
        ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .9894f, .9418f, .0476f))

    private fun article(id: String, millis: Long, payload: TestimonyPayload) = RealityArticle(
        id = ArticleId(id), perceivedAt = millis, recordedAt = millis + 500,
        sourceId = SourceId("HP_WITNESS"), payload = payload,
        monotonicTimeNanos = millis * 1_000_000L, matchId = MatchId("42fff18b-0d3f-4cb5-bc96-c2541b84e473")
    )
}
