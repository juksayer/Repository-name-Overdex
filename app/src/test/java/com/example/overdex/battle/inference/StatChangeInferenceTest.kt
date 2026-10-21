package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class StatChangeInferenceTest {
    private val effects = listOf(
        StatMoveEffect("Power-Up Punch", "ATTACK", 1, true),
        StatMoveEffect("Skull Bash", "DEFENSE", 1, true),
        StatMoveEffect("Rock Tomb", "ATTACK", -1, false),
        StatMoveEffect("Close Combat", "DEFENSE", -2, true),
        StatMoveEffect("Ancient Power", "ATTACK", 1, true),
    )
    @Test fun `sound and named move attribute all four changes to the recipient`() {
        for ((move, cue, side, stages) in listOf(
            Case("Power-Up Punch", "ATTACK_UP", ActivePokemonSide.PLAYER, 1),
            Case("Skull Bash", "DEFENSE_UP", ActivePokemonSide.PLAYER, 1),
            Case("Rock Tomb", "ATTACK_DOWN", ActivePokemonSide.OPPONENT, 1),
            Case("Close Combat", "DEFENSE_DOWN", ActivePokemonSide.PLAYER, 2),
        )) {
            val inference = StatChangeInference(effects)
            assertTrue(inference.accept(move(move)).isEmpty())
            val derived = inference.accept(sound(cue)).single()
            assertEquals(side, derived.payload.affectedSide)
            assertEquals(stages, derived.payload.stages)
            assertEquals(listOf("move"), derived.moves.map { it.id.value })
            assertEquals("sound", derived.payload.soundArticleId)
        }
    }
    @Test fun `opponent debuff lands on player and late OCR preserves audio time`() {
        val inference = StatChangeInference(effects)
        assertTrue(inference.accept(sound("ATTACK_DOWN")).isEmpty())
        val derived = inference.accept(move("Rock Tomb", ActivePokemonSide.OPPONENT).copy(recordedAt = 99_000)).single()
        assertEquals(ActivePokemonSide.PLAYER, derived.payload.affectedSide)
        assertEquals(11_000_000_000L, derived.sound.monotonicTimeNanos)
    }
    @Test fun `chance or guaranteed effect without a heard sound is not an observation`() {
        val inference = StatChangeInference(effects)
        assertTrue(inference.accept(move("Ancient Power")).isEmpty())
        assertTrue(inference.accept(move("Rock Tomb")).isEmpty())
    }
    @Test fun `incompatible sound or old move cannot establish a side`() {
        val inference = StatChangeInference(effects)
        inference.accept(move("Rock Tomb"))
        assertTrue(inference.accept(sound("DEFENSE_UP")).isEmpty())
        assertTrue(inference.accept(sound("ATTACK_DOWN").copy(monotonicTimeNanos = 30_000_000_000L)).isEmpty())
    }
    @Test fun `close competing moves retract earlier assignment instead of guessing a side`() {
        val inference = StatChangeInference(effects)
        inference.accept(move("Rock Tomb"))
        assertEquals(ActivePokemonSide.OPPONENT, inference.accept(sound("ATTACK_DOWN")).single().payload.affectedSide)
        val correction = inference.accept(move("Rock Tomb", ActivePokemonSide.OPPONENT)
            .copy(id = ArticleId("other-move"), monotonicTimeNanos = 10_200_000_000L)).single()
        assertNull(correction.payload.affectedSide)
        assertEquals("sound", correction.payload.soundArticleId)
        assertEquals(setOf("move", "other-move"), correction.moves.map { it.id.value }.toSet())
    }
    @Test fun `repeated article delivery does not duplicate the effect`() {
        val inference = StatChangeInference(effects)
        inference.accept(move("Rock Tomb"))
        val cue = sound("ATTACK_DOWN")
        assertEquals(1, inference.accept(cue).size)
        assertTrue(inference.accept(cue).isEmpty())
    }

    @Test fun `sound can occur several seconds before or after the announcement in either delivery order`() {
        for (gap in listOf(-6L, -4L, 4L, 6L)) for (soundFirst in listOf(false, true)) {
            val inference = StatChangeInference(effects)
            val announcement = move("Rock Tomb").copy(recordedAt = 99_000)
            val heard = sound("ATTACK_DOWN").copy(monotonicTimeNanos = (10 + gap) * 1_000_000_000)
            val (first, second) = if (soundFirst) heard to announcement else announcement to heard
            assertTrue(inference.accept(first).isEmpty())
            val change = inference.accept(second).single()
            assertEquals(ActivePokemonSide.OPPONENT, change.payload.affectedSide)
            assertEquals(heard.monotonicTimeNanos, change.sound.monotonicTimeNanos)
        }
    }

    @Test fun `variable timing never chooses the nearest of two compatible moves`() {
        val inference = StatChangeInference(effects)
        inference.accept(move("Rock Tomb"))
        assertEquals(ActivePokemonSide.OPPONENT, inference.accept(sound("ATTACK_DOWN")).single().payload.affectedSide)
        val correction = inference.accept(move("Rock Tomb", ActivePokemonSide.OPPONENT)
            .copy(id = ArticleId("later-move"), monotonicTimeNanos = 16_000_000_000L)).single()
        assertNull(correction.payload.affectedSide)
    }

    @Test fun `repeated move article is not a competing use and out of window audio remains unassigned`() {
        val inference = StatChangeInference(effects)
        inference.accept(move("Rock Tomb"))
        inference.accept(move("Rock Tomb"))
        assertEquals(ActivePokemonSide.OPPONENT, inference.accept(sound("ATTACK_DOWN")).single().payload.affectedSide)
        for (gap in listOf(-7L, 7L)) assertTrue(inference.accept(sound("ATTACK_DOWN")
            .copy(id = ArticleId("sound-$gap"), monotonicTimeNanos = (10 + gap) * 1_000_000_000)).isEmpty())
    }

    private data class Case(val move: String, val cue: String, val side: ActivePokemonSide, val stages: Int)
    private fun sound(cue: String) = article("sound", StatChangeSoundMeasured(cue, "sha", .98f, .75f, 900_000_000), 11)
    private fun move(name: String, side: ActivePokemonSide = ActivePokemonSide.PLAYER) = article("move",
        ChargedMoveEnergySpent(side, "Test Pokemon", name, 50, "ANNOUNCEMENT"), 10)
    private fun article(id: String, payload: TestimonyPayload, seconds: Long) = RealityArticle(
        ArticleId(id), seconds * 1000, seconds * 1000, SourceId("TEST"), payload,
        monotonicTimeNanos = seconds * 1_000_000_000)
}
