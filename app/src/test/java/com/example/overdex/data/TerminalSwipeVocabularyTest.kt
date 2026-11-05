package com.example.overdex.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSwipeVocabularyTest {
    @Test fun reusesCurrentCatalogueAndItsCanonicalMoveNamesWithoutReloading() = runBlocking {
        var reads = 0
        val loader = GameMasterLoader {
            reads++
            """{"pokemon":[{"dex":26,"speciesId":"raichu_alolan","speciesName":"Raichu (Alolan)",
                "fastMoves":["VOLT_SWITCH"],"chargedMoves":["THUNDER_PUNCH","MISSING"]}],
                "moves":[{"moveId":"VOLT_SWITCH","name":"Volt Switch","type":"electric","power":1,
                "energy":0,"energyGain":1},{"moveId":"THUNDER_PUNCH","name":"Thunder Punch",
                "type":"electric","power":1,"energy":40,"energyGain":0},
                {"moveId":"RETURN","name":"Return","type":"normal","power":1,"energy":70,"energyGain":0}]}"""
        }
        // Another consumer can initialize the shared loader before the keyboard asks for words.
        assertEquals(1, loader.allPokemon().size)
        val vocabulary = TerminalSwipeVocabulary(loader, Dispatchers.Unconfined)
        val first = vocabulary.load()
        assertTrue(first.containsAll(listOf("Raichu", "Raichu (Alolan)", "Volt Switch", "Thunder Punch", "Return")))
        assertEquals(first, vocabulary.load())
        assertEquals(1, reads)
    }
}
