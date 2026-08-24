package com.example.overdex

import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.BattleResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleMemoryOutcomeTest {

    @Test
    fun `explicit WIN overrides a heuristic UNKNOWN`() {
        val memory = BattleMemory()
        
        // Timeline has WIN
        memory.recordEvent(BattleEvent(
            type = BattleEventType.BATTLE_ENDED,
            result = BattleResult.WIN
        ))

        val log = memory.toBattleLog()
        assertEquals(BattleResult.WIN, log.result)
    }

    @Test
    fun `explicit LOSS overrides a heuristic WIN`() {
        val memory = BattleMemory()
        
        // Heuristic would say WIN (enemy fainted)
        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu"
        ))
        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_FAINTED,
            actor = BattleActor.ENEMY
        ))
        
        // But Timeline has explicit LOSS (e.g. from GOOD EFFORT Witness)
        memory.recordEvent(BattleEvent(
            type = BattleEventType.BATTLE_ENDED,
            result = BattleResult.LOSS
        ))

        val log = memory.toBattleLog()
        assertEquals("Explicit LOSS must override heuristic WIN", BattleResult.LOSS, log.result)
    }

    @Test
    fun `explicit DRAW overrides the heuristic`() {
        val memory = BattleMemory()
        
        memory.recordEvent(BattleEvent(
            type = BattleEventType.BATTLE_ENDED,
            result = BattleResult.DRAW
        ))

        val log = memory.toBattleLog()
        assertEquals(BattleResult.DRAW, log.result)
    }

    @Test
    fun `no explicit outcome preserves the existing heuristic behavior`() {
        val memory = BattleMemory()
        
        // No BATTLE_ENDED event, but enemy fainted
        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_IDENTIFIED,
            actor = BattleActor.ENEMY,
            message = "Pikachu"
        ))
        memory.recordEvent(BattleEvent(
            type = BattleEventType.POKEMON_FAINTED,
            actor = BattleActor.ENEMY
        ))

        val log = memory.toBattleLog()
        assertEquals("Fallback to heuristic WIN when timeline is empty", BattleResult.WIN, log.result)
    }

    @Test
    fun `no explicit outcome and no heuristic evidence remains UNKNOWN`() {
        val memory = BattleMemory()
        
        // Battle started but no faints or end screen
        memory.recordEvent(BattleEvent(type = BattleEventType.BATTLE_STARTED))

        val log = memory.toBattleLog()
        assertEquals(BattleResult.UNKNOWN, log.result)
    }

    @Test
    fun `latest BATTLE_ENDED event is preferred if multiple exist`() {
        val memory = BattleMemory()
        
        // First observation saw LOSS (maybe OCR noise)
        memory.recordEvent(BattleEvent(
            type = BattleEventType.BATTLE_ENDED,
            result = BattleResult.LOSS
        ))
        
        // Second observation confirmed WIN
        memory.recordEvent(BattleEvent(
            type = BattleEventType.BATTLE_ENDED,
            result = BattleResult.WIN
        ))

        val log = memory.toBattleLog()
        assertEquals("Latest outcome in timeline must be preferred", BattleResult.WIN, log.result)
    }
}
