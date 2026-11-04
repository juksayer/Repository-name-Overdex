package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class BattleShieldDecisionTextEvidenceTest {
    @Test fun `late combat without GO admits a full shield label with independent proof`() {
        val reader = BattleShieldDecisionTextEvidence()
        combat(reader)
        val cue = reader.accept(label("shield", 1, "Not  now", 1400))!!
        assertEquals(1_400_000_000L, cue.at)
        assertEquals(101_400L, cue.wall)
        assertEquals(setOf("player", "opponent", "loss", "shield"), cue.ids.toSet())
    }

    @Test fun `late combat admits separate apertures and preserves the first source instant`() {
        val reader = BattleShieldDecisionTextEvidence()
        combat(reader)
        assertNull(reader.accept(label("not", 1, "NOT", 1400)))
        val cue = reader.accept(label("now", 2, "NOW", 1800))!!
        assertEquals(1_400_000_000L, cue.at)
        assertEquals(101_400L, cue.wall)
        assertEquals(setOf("player", "opponent", "loss", "not", "now"), cue.ids.toSet())
        assertNull(reader.accept(label("again", 1, "NOT NOW", 1900)))
    }

    @Test fun `names prebattle glyphs full bars and flashes cannot admit shield text`() {
        val reader = BattleShieldDecisionTextEvidence()
        reader.accept(article("vs", VsScreenWitnessed, 100))
        for ((index, glyph) in listOf("3", "2", "1", "GO").withIndex()) {
            reader.accept(article("glyph-$index", CountdownGlyphWitnessed(glyph, .99f, 1), 200L + index))
        }
        species(reader)
        reader.accept(article("full", ActiveHpBarMeasured(ActivePokemonSide.OPPONENT,
            10, 10, 200, 20, 1f, 1080, 2400), 1200))
        reader.accept(article("flash", HpBarBorderPulse(ActivePokemonSide.OPPONENT,
            ApertureStatus.PRESENT, .9f, 5), 1250))
        assertNull(reader.accept(label("unrelated-popup", 1, "NOT NOW", 1400)))
        reader.accept(article("inconsistent-loss", ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT,
            .9f, .8f, .2f), 1500))
        assertNull(reader.accept(label("still-no-combat", 1, "NOT NOW", 1600)))
    }

    @Test fun `delayed species delivery can corroborate only a later source-time loss`() {
        val reader = BattleShieldDecisionTextEvidence()
        reader.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat"), 1000))
        reader.accept(loss("too-early", 1100))
        reader.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Combusken"), 1200))
        assertNull(reader.accept(label("unproven", 1, "NOT NOW", 1300)))
        reader.accept(loss("valid-loss", 1400))
        val cue = reader.accept(label("shield", 1, "NOT NOW", 1500))!!
        assertEquals(setOf("player", "opponent", "valid-loss", "shield"), cue.ids.toSet())

        val delayed = BattleShieldDecisionTextEvidence()
        delayed.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat"), 1000))
        delayed.accept(loss("loss", 1300))
        // Delivery is late, but this identity was visible before that HP decrease.
        delayed.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Combusken"), 1100)
            .copy(recordedAt = 200_000L))
        assertNotNull(delayed.accept(label("shield", 1, "NOT NOW", 1400)))
    }

    @Test fun `a delayed label before admission cannot combine with a later valid fragment`() {
        val reader = BattleShieldDecisionTextEvidence()
        combat(reader)
        assertNull(reader.accept(label("old-not", 1, "NOT", 1100).copy(recordedAt = 200_000L)))
        assertNull(reader.accept(label("new-now", 2, "NOW", 1400)))
        val cue = reader.accept(label("new-not", 1, "NOT", 1500))!!
        assertEquals(1_400_000_000L, cue.at)
        assertFalse("Source-time admission must not cite the earlier fragment", "old-not" in cue.ids)
    }

    @Test fun `independent charged phases admit only subsequent source-time labels`() {
        for (phase in listOf(AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced, MatchStarted)) {
            val reader = BattleShieldDecisionTextEvidence()
            reader.accept(article("phase", phase, 1000))
            assertNull(reader.accept(label("old", 1, "NOT NOW", 999)))
            val cue = reader.accept(label("shield", 1, "NOT NOW", 1100))!!
            assertEquals(setOf("phase", "shield"), cue.ids.toSet())
        }
    }

    @Test fun `result stop ignore and restart clear fragments and prevent reopening from stale work`() {
        val boundaries = listOf<TestimonyPayload>(MatchEnded(BattleResult.WIN),
            ObservationSessionStoppedByUser, ScreenIgnoredByUser, ObservationRestartedByUser)
        for (boundary in boundaries) {
            val reader = BattleShieldDecisionTextEvidence()
            reader.accept(article("start", MatchStarted, 1000))
            assertNull(reader.accept(label("old-not", 1, "NOT", 1100)))
            reader.accept(article("boundary", boundary, 1200))
            assertNull(reader.accept(label("after-now", 2, "NOW", 1300)))
            assertNull(reader.accept(label("after-full", 1, "NOT NOW", 1400)))
            reader.accept(article("restart", ObservationRestartedByUser, 1500))
            reader.accept(article("stale-start", MatchStarted, 1000).copy(recordedAt = 200_000L))
            assertNull(reader.accept(label("stale-full", 1, "NOT NOW", 1100).copy(recordedAt = 200_000L)))
            assertNull(reader.accept(label("unopened", 1, "NOT NOW", 1600)))
            reader.accept(article("new-start", MatchStarted, 1700))
            assertNull(reader.accept(label("new-now", 2, "NOW", 1800)))
            val cue = reader.accept(label("new-not", 1, "NOT", 1900))!!
            assertEquals(setOf("new-start", "new-now", "new-not"), cue.ids.toSet())
        }
    }

    @Test fun `charge completion clears an unfinished pair and rearms the next shield episode`() {
        val reader = BattleShieldDecisionTextEvidence()
        reader.accept(article("start", MatchStarted, 1000))
        assertNull(reader.accept(label("not-before-charge", 1, "NOT", 1100)))
        reader.accept(article("charge", ChargeMoveUsedAnnounced, 1200))
        assertNull(reader.accept(label("now-after-charge", 2, "NOW", 1300)))
        assertNotNull(reader.accept(label("not-after-charge", 1, "NOT", 1400)))
        reader.accept(article("next-charge", ChargeMoveUsedAnnounced, 1500))
        assertNotNull(reader.accept(label("next-full", 1, "NOT NOW", 1600)))
    }

    @Test fun `cached fragments effectiveness and empty controls do not become shield cues`() {
        val reader = BattleShieldDecisionTextEvidence()
        combat(reader)
        assertNull(reader.accept(label("effect", 1, "NOT VERY EFFECTIVE", 1400)))
        assertNull(reader.accept(label("now", 2, "NOW", 1500)))
        assertNull(reader.accept(label("cached-not", 1, "NOT", 1600, "UNCHANGED_LETTER_PIXELS")))
        assertNull(reader.accept(label("move", 1, "NIGHT SLASH", 1700)))
        assertNull(reader.accept(label("blank", 2, "", 1800)))
        assertNull(reader.accept(label("no-clock", 1, "NOT NOW", 1900).copy(monotonicTimeNanos = null)))
    }

    private fun species(reader: BattleShieldDecisionTextEvidence) {
        reader.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat"), 1000))
        reader.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Combusken"), 1100))
    }

    private fun combat(reader: BattleShieldDecisionTextEvidence) {
        species(reader)
        reader.accept(loss("loss", 1200))
    }

    private fun loss(id: String, atMillis: Long) = article(id,
        ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, .9f, .8f, .1f), atMillis)

    private fun label(id: String, slot: Int, text: String, atMillis: Long, basis: String = "OCR") =
        article(id, PlayerChargeMoveLabelRead(slot, text, basis), atMillis)

    private fun article(id: String, payload: TestimonyPayload, atMillis: Long) = RealityArticle(
        ArticleId(id), 100_000L + atMillis, 105_000L + atMillis, SourceId("test"), payload,
        monotonicTimeNanos = atMillis * 1_000_000L)
}
