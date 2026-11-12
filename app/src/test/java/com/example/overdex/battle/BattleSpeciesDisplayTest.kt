package com.example.overdex.battle

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleSpeciesDisplayTest {
    @Test fun `catalogue size suffixes do not assert an observed specimen size`() {
        for (species in listOf("Gourgeist", "Pumpkaboo")) {
            for (size in listOf("Small", "Average", "Large", "Super")) {
                assertEquals(species, BattleSpeciesDisplay.name("$species ($size)"))
            }
            assertEquals(species, BattleSpeciesDisplay.name(species))
        }
    }

    @Test fun `regional forms and unrelated qualifiers retain their identity`() {
        for (name in listOf("Raichu (Alolan)", "Raichu", "Meowth (Galarian)",
            "Giratina (Origin)", "Gourgeist (Unknown)", "Other (Average)")) {
            assertEquals(name, BattleSpeciesDisplay.name(name))
        }
    }
}
