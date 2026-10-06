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
    fun `league text and one party card accept Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                leagueText = signal("league", atSeconds = 1),
                partyCards = mapOf(1 to signal("card-1", atSeconds = 2))
            )
        )

        assertEquals("LEAGUE_TEXT_AND_PARTY_CARD", result?.basis)
        assertEquals(listOf("league", "card-1"), result?.evidenceArticleIds)
    }

    @Test
    fun `league badge and two party cards accept Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                leagueBadge = signal("badge", atSeconds = 1),
                partyCards = mapOf(
                    1 to signal("card-1", atSeconds = 1),
                    2 to signal("card-2", atSeconds = 2)
                )
            )
        )

        assertEquals("LEAGUE_BADGE_AND_TWO_PARTY_CARDS", result?.basis)
        assertEquals(listOf("badge", "card-1", "card-2"), result?.evidenceArticleIds)
    }

    @Test
    fun `three party cards tentatively accept Team Select`() {
        val result = TeamSelectSurfaceContract.evaluate(
            TeamSelectSurfaceEvidence(
                partyCards = mapOf(
                    1 to signal("card-1", atSeconds = 1),
                    2 to signal("card-2", atSeconds = 1),
                    3 to signal("card-3", atSeconds = 2)
                )
            )
        )

        assertEquals("THREE_PARTY_CARD_GEOMETRY", result?.basis)
        assertEquals(0.86f, result?.confidence)
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
    }

    private fun signal(id: String, atSeconds: Long, confidence: Float = 0.99f) =
        TeamSelectSignal(id, atSeconds * 1_000_000_000L, confidence)

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}
