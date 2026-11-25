package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleWitnessContracts
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class CountdownMatchStartInferenceTest {
    @Test fun everyTrustedGlyphOpensAtCapture() {
        for (value in listOf("3", "2", "1", "GO")) {
            val cue = glyph(value, 4_000)
            val start = CountdownMatchStartInference().accept(cue)!!
            assertEquals(MatchStarted, start.payload)
            assertEquals(cue.monotonicTimeNanos, start.monotonicTimeNanos)
            assertEquals(cue.perceivedAt, start.perceivedAt)
            assertEquals(listOf(cue.id), start.predecessorIds)
        }
    }

    @Test fun combatAloneOpensAtCapture() {
        for (cue in listOf(damage(4_000), article("ball", 7_000, PlayerPokeBallCountMeasured(2, 3)),
            article("charge", 9_000, ChargeMoveUsedAnnounced))) {
            assertEquals(cue.monotonicTimeNanos, CountdownMatchStartInference().accept(cue)?.monotonicTimeNanos)
        }
    }

    @Test fun preBattleAndUnavailableInputsDoNotOpen() {
        for (cue in listOf(glyph("4", 1000), glyph("GO", 1000).copy(sourceId = SourceId("UNRELATED_OCR")),
            article("vs", 1000, VsScreenWitnessed), article("ready", 1000, WitnessOperating(true)),
            article("party", 1000, TeamSelectPartyWitnessed),
            article("absent", 1000, HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.ABSENT)))) {
            assertNull(CountdownMatchStartInference().accept(cue))
        }
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
