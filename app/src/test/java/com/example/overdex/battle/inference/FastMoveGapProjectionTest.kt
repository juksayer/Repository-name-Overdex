package com.example.overdex.battle.inference

import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Test

class FastMoveGapProjectionTest {
    @Test fun `preserves a range when a visual gap crosses an unknown cycle phase`() {
        val move = Move("Bubble", PokemonType.WATER, damage = 7, energy = 11, isFast = true, turns = 3)
        val projection = FastMoveGapProjector.project(move, 3_100_000_000L)
        assertEquals(2, projection.minimumCompletedUses)
        assertEquals(3, projection.maximumCompletedUses)
        assertEquals(22, projection.minimumEnergyGain)
        assertEquals(33, projection.maximumEnergyGain)
    }
}
