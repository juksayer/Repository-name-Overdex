package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamSelectLeagueShieldClassifierTest {
    @Test fun `blue shield identifies Great League`() {
        assertEquals(StandardBattleLeague.GREAT, TeamSelectLeagueShieldClassifier.classifyArgb(colors(0x244fcf, 0xd44235))!!.league)
    }

    @Test fun `yellow striped shield identifies Ultra League`() {
        assertEquals(StandardBattleLeague.ULTRA, TeamSelectLeagueShieldClassifier.classifyArgb(colors(0xf0c72c, 0x303b40))!!.league)
    }

    @Test fun `magenta striped shield identifies Master League`() {
        assertEquals(StandardBattleLeague.MASTER, TeamSelectLeagueShieldClassifier.classifyArgb(colors(0xd83ebd, 0x60268f))!!.league)
    }

    @Test fun `unrecognized low saturation shield remains unresolved`() {
        assertNull(TeamSelectLeagueShieldClassifier.classifyArgb(List(200) { 0x808080 }))
    }

    @Test fun `counts separated stripe runs without knowing their colors`() {
        val baseline = 0x304050
        val accent = 0xe03090
        val scan = List(12) { baseline } + List(10) { accent } +
            List(12) { baseline } + List(10) { accent } +
            List(12) { baseline } + List(10) { accent }

        assertEquals(3, TeamSelectLeagueShieldClassifier.countStripeRuns(scan, baseline, minimumRun = 6))
    }

    private fun colors(primary: Int, secondary: Int): List<Int> =
        List(200) { index -> if (index < 150) primary else secondary }
}
