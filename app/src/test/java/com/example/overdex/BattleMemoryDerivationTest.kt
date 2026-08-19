package com.example.overdex

import com.example.overdex.model.*
import org.junit.Assert.*
import org.junit.Test

class BattleMemoryDerivationTest {

    @Test
    fun `POKEMON_IDENTIFIED adds new enemy to enemyTeam`() {
        val memory = BattleMemory()
        val event = BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu",
            confidence = Confidence(ConfidenceLevel.OBSERVED)
        )

        memory.recordEvent(event)

        assertEquals(1, memory.enemyTeam.size)
        assertEquals("Pikachu", memory.enemyTeam[0].species)
        assertTrue("First identified pokemon should be active", memory.enemyTeam[0].isActive)
    }

    @Test
    fun `Subsequent POKEMON_IDENTIFIED updates confidence but does not duplicate entry`() {
        val memory = BattleMemory()
        val event1 = BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu",
            confidence = Confidence(ConfidenceLevel.INFERRED)
        )
        val event2 = BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu",
            confidence = Confidence(ConfidenceLevel.OBSERVED)
        )

        memory.recordEvent(event1)
        val initialConfidence = memory.enemyTeam[0].speciesConfidence
        
        memory.recordEvent(event2)

        assertEquals(1, memory.enemyTeam.size)
        assertNotEquals(initialConfidence, memory.enemyTeam[0].speciesConfidence)
        assertEquals(ConfidenceLevel.OBSERVED, memory.enemyTeam[0].speciesConfidence.level)
    }

    @Test
    fun `Second distinct POKEMON_IDENTIFIED is added but not set as active`() {
        val memory = BattleMemory()
        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu"
        ))

        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Raichu"
        ))

        assertEquals(2, memory.enemyTeam.size)
        assertTrue("Pikachu should still be active", memory.enemyTeam.find { it.species == "Pikachu" }?.isActive == true)
        assertFalse("Raichu should not be active", memory.enemyTeam.find { it.species == "Raichu" }?.isActive == true)
    }

    @Test
    fun `POKEMON_IDENTIFIED for PLAYER actor is ignored by enemyTeam`() {
        val memory = BattleMemory()
        val event = BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.PLAYER,
            message = "Charmander"
        )

        memory.recordEvent(event)

        assertEquals(0, memory.enemyTeam.size)
    }
}
