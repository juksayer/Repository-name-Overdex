package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test

class MatchMemoryOwnershipTest {

    @Test
    fun `each match instance has its own battle memory`() {
        val matchA = Match(
            matchId = "MATCH_A",
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline()
        )
        
        val matchB = Match(
            matchId = "MATCH_B",
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline()
        )

        assertNotNull("Match A should have a BattleMemory", matchA.battleMemory)
        assertNotNull("Match B should have a BattleMemory", matchB.battleMemory)
        assertNotSame("Match A and Match B should have different BattleMemory instances", matchA.battleMemory, matchB.battleMemory)
    }
}
