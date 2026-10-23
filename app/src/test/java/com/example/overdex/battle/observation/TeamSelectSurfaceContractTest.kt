package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSelectSurfaceContractTest {
    @Test
    fun `isolated league text cannot accept Team Select`() {
        val evidence = TeamSelectSurfaceEvidence(
            leagueText = signal("league", atSeconds = 1)
        )

        assertNull(TeamSelectSurfaceContract.evaluate(evidence))
    }

    @Test
    fun `league text and one colorful region do not confuse league choice with Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                leagueText = signal("league", atSeconds = 1),
                partyCards = mapOf(1 to signal("card-1", atSeconds = 2))
            )
        )

        assertNull(result)
    }

    @Test
    fun `league colors and two pale regions cannot accept the overworld as Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                leagueBadge = signal("badge", atSeconds = 1),
                partyCards = mapOf(
                    1 to signal("card-1", atSeconds = 1),
                    2 to signal("card-2", atSeconds = 2)
                )
            )
        )

        assertNull(result)
    }

    @Test
    fun `three apparent cards without page wording cannot accept Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                partyCards = mapOf(
                    1 to signal("card-1", atSeconds = 1),
                    2 to signal("card-2", atSeconds = 1),
                    3 to signal("card-3", atSeconds = 2)
                )
            )
        )

        assertNull(result)
    }

    @Test fun `use party wording and league badge promptly accept the real screen`() {
        val result = TeamSelectSurfaceContract.evaluate(TeamSelectSurfaceEvidence(
            useThisParty = signal("use-party-text", 2), leagueBadge = signal("badge", 1)))
        assertEquals("LEAGUE_BADGE_AND_USE_PARTY_CONTROL", result?.basis)
        assertEquals(listOf("badge", "use-party-text"), result?.evidenceArticleIds)
    }

    @Test fun `league and CP wording with two cards recover a missed button read`() {
        val result = TeamSelectSurfaceContract.evaluate(TeamSelectSurfaceEvidence(
            leagueText = signal("league", 1), restrictions = signal("max-cp", 1),
            partyCards = mapOf(1 to signal("card-1", 1), 2 to signal("card-2", 2))))
        assertEquals("LEAGUE_AND_RESTRICTION_TEXT_WITH_PARTY_CARDS", result?.basis)
    }

    @Test fun `old use party words cannot authorize new scenery`() {
        assertNull(TeamSelectSurfaceContract.evaluate(TeamSelectSurfaceEvidence(
            useThisParty = signal("old-button", 1), leagueBadge = signal("scene-colors", 4),
            partyCards = (1..3).associateWith { signal("scene-$it", 4) })))
    }

    @Test
    fun `signals outside the agreement window cannot combine`() {
        val evidence = TeamSelectSurfaceEvidence(
            leagueText = signal("old-league", atSeconds = 1),
            partyCards = mapOf(1 to signal("new-card", atSeconds = 4))
        )

        assertNull(TeamSelectSurfaceContract.evaluate(evidence))
    }

    @Test
    fun `party card detector requires background ink and sprite color`() {
        val colors = buildList {
            repeat(70) { add(argb(235, 237, 225)) }
            repeat(15) { add(argb(62, 82, 91)) }
            repeat(15) { add(argb(218, 92, 42)) }
        }

        assertNotNull(TeamSelectSurfaceSignalDetector.partyCardConfidence(colors))
        assertNull(TeamSelectSurfaceSignalDetector.partyCardConfidence(List(100) { argb(245, 245, 245) }))
    }

    @Test
    fun `purpose-specific OCR checks tolerate punctuation and common spacing`() {
        assertTrue(TeamSelectSurfaceSignalDetector.isRestrictionText("Max CP per Pokémon: 1,500"))
        assertTrue(TeamSelectSurfaceSignalDetector.isRestrictionText("No limit"))
        assertTrue(TeamSelectSurfaceSignalDetector.isUsePartyText("USE  THIS PARTY"))
        org.junit.Assert.assertFalse(TeamSelectSurfaceSignalDetector.isRestrictionText("POKEMON"))
        org.junit.Assert.assertFalse(TeamSelectSurfaceSignalDetector.isUsePartyText("PARTY"))
    }

    private fun signal(id: String, atSeconds: Long, confidence: Float = 0.99f) =
        TeamSelectSignal(id, atSeconds * 1_000_000_000L, confidence)

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}
