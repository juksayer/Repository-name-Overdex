package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.BattleCryCandidateMeasurement
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisiveBattleCryRuleTest {
    @Test fun `clean separated cry is accepted as species evidence`() {
        val result = DecisiveBattleCryRule.match(candidates(0.94f, 0.88f))

        assertEquals(395, result?.speciesId)
        assertEquals(0.06f, result?.distinctSpeciesMargin ?: 0f, 0.001f)
    }

    @Test fun `close ranked cries remain candidates rather than identity`() {
        assertNull(DecisiveBattleCryRule.match(candidates(0.94f, 0.93f)))
    }

    @Test fun `fast move audio can never become species identity`() {
        assertNull(DecisiveBattleCryRule.match(candidates(0.98f, 0.80f, "FAST_MOVE_IMPACT")))
    }

    @Test fun `result music and charged effects cannot become species even with a separated ranking`() {
        listOf("MATCH_RESULT_MUSIC", "CHARGE_MOVE_QTE_ENTERED", "CHARGE_MOVE_QTE_COMPLETED",
            "STAT_CHANGE_SOUND", "CHARGED_MOVE_EFFECT", "UNRECOGNIZED_CUE").forEach { cue ->
            assertNull(cue, DecisiveBattleCryRule.match(candidates(0.99f, 0.70f, cue)))
        }
    }

    @Test fun `countdown shaped cue late in combat cannot become species identity`() {
        assertNull(DecisiveBattleCryRule.match(
            candidates(0.94f, 0.88f, "COUNTDOWN_GO"),
            cueAtNanos = 35_000_000_000L,
            matchStartedAtNanos = 5_000_000_000L,
        ))
    }

    @Test fun `opening countdown cry may finish shortly after GO`() {
        val result = DecisiveBattleCryRule.match(
            candidates(0.94f, 0.88f, "COUNTDOWN_GO"),
            cueAtNanos = 6_000_000_000L,
            matchStartedAtNanos = 5_000_000_000L,
        )

        assertEquals(395, result?.speciesId)
    }

    private fun candidates(best: Float, second: Float, cue: String = "SPECIES_ENTRY") =
        BattleCryCandidatesMeasured(
            cue,
            listOf(
                BattleCryCandidateMeasurement(395, "best", best),
                BattleCryCandidateMeasurement(473, "second", second),
            ),
        )
}
