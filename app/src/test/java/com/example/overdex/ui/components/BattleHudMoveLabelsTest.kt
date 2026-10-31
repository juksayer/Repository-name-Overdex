package com.example.overdex.ui.components

import com.example.overdex.model.GameMaster
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class BattleHudMoveLabelsTest {
    @Test fun `short familiar moves remain complete while long phrases retain meaningful words`() {
        val expected = linkedMapOf(
            "Night Slash" to "Night Slash",
            "Hydro Cannon" to "Hydro Cannon",
            "Razor Leaf" to "Razor Leaf",
            "Thunder Punch" to "T. Punch",
            "Water Shuriken" to "W. Shuriken",
            "Weather Ball (Ice)" to "W. Ball (Ice)",
            "Power-Up Punch" to "P-Up Punch",
            "Double Iron Bash" to "D. Iron Bash",
        )
        assertEquals(expected, BattleHudMoveLabels.forPool(expected.keys))
    }

    @Test fun `typed suffixes survive and keep variants distinct`() {
        val names = listOf("Hidden Power (Electric)", "Hidden Power (Ice)", "Weather Ball (Fire)",
            "Weather Ball (Ice)", "Techno Blast (Burn)", "Techno Blast (Chill)")
        val labels = BattleHudMoveLabels.forPool(names)
        assertEquals("H. Power (Electric)", labels.getValue(names.first()))
        names.forEach { name ->
            assertEquals(name.substringAfterLast(" ("), labels.getValue(name).substringAfterLast(" ("))
        }
        assertEquals(names.size, labels.values.toSet().size)
    }

    @Test fun `same abbreviated words fall back to full distinguishable canonical names`() {
        val names = listOf("Thunder Punch", "Thundering Punch", "Night Slash")
        assertEquals(names.associateWith { it }, BattleHudMoveLabels.forPool(names))
    }

    @Test fun `collision with a preexisting short label also restores the full long name`() {
        assertEquals(mapOf("Thunder Punch" to "Thunder Punch", "t. punch" to "t. punch"),
            BattleHudMoveLabels.forPool(listOf("Thunder Punch", "t. punch")))
    }

    @Test fun `canonical names and unknown long single words stay untouched`() {
        val names = listOf("Electroencephalogram", "Thunder Punch", "Thunder Punch", "THUNDER PUNCH")
        val original = names.toList()
        val labels = BattleHudMoveLabels.forPool(names)
        assertEquals(original, names)
        assertEquals("Electroencephalogram", labels.getValue("Electroencephalogram"))
        assertEquals("T. Punch", labels.getValue("Thunder Punch"))
        assertEquals("T. PUNCH", labels.getValue("THUNDER PUNCH"))
        assertEquals(names.distinct().toSet(), labels.keys)
    }

    @Test fun `every checked in species form pool remains unambiguous without changing names`() {
        val game = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
            File("src/main/assets/gamemaster.json").readText())
        val moves = game.moves.associateBy { it.moveId }
        var checked = 0
        game.pokemon.forEach { pokemon ->
            val names = (pokemon.fastMoves + pokemon.chargedMoves).mapNotNull { moves[it]?.name }.distinct()
            val labels = BattleHudMoveLabels.forPool(names)
            assertEquals(pokemon.speciesName, names.toSet(), labels.keys)
            val distinctNames = names.distinctBy { it.lowercase(Locale.ROOT) }
            assertEquals("Ambiguous HUD labels for ${pokemon.speciesName}: $labels", distinctNames.size,
                distinctNames.map { labels.getValue(it).lowercase(Locale.ROOT) }.distinct().size)
            names.filter { it.length <= 12 }.forEach { assertEquals(it, labels.getValue(it)) }
            names.filter { it.endsWith(")") }.forEach { name ->
                assertTrue("Qualifier lost for $name", labels.getValue(name).endsWith(name.substringAfterLast(" (")))
            }
            checked++
        }
        assertTrue("The complete checked-in form catalogue must be exercised", checked > 1000)
    }
}
