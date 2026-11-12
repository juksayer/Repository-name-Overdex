package com.example.overdex.battle.observation

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.assertEquals

class SpeciesSearchBudgetTest {
    @Test fun `recorded contaminated announcement search`() {
        val game = Json.parseToJsonElement(File("src/main/assets/gamemaster.json").readText())
        val imported = Json.parseToJsonElement(File("src/main/assets/pokemon_go_data.json").readText())
        val names = (game.jsonObject.getValue("pokemon").jsonArray.flatMap {
            val name = it.jsonObject.getValue("speciesName").jsonPrimitive.content
            listOf(name, name.substringBefore(" ("))
        } + imported.jsonObject.getValue("pokemon").jsonArray.map {
            it.jsonObject.getValue("name").jsonPrimitive.content
        }).toSet()
        // Compare the complete catalogue's accepted fuzzy costs, including ties.
        // This is the slow pre-change algorithm retained only as a test oracle.
        val reading = SpeciesOcrTypography.normalizeLabel("b0 N1ght Slast Gourgeist")
        for (name in names) {
            val candidate = SpeciesOcrTypography.normalizeLabel(name)
            val budget = SpeciesOcrTypography.allowedCost(candidate.length)
            val expected = legacyWindowDistance(reading, candidate).coerceAtMost(budget + 1)
            assertEquals(name, expected, SpeciesTextResolver.closestWindowDistance(reading, candidate, budget))
        }
        val start = System.nanoTime()
        val result = SpeciesTextResolver.resolve("b0 N1ght Slast Gourgeist", names)
        assertEquals("Gourgeist", result)
        println("RECORDED_SEARCH names=${names.size} result=$result elapsedMs=${(System.nanoTime()-start)/1_000_000}")
    }
    @Test fun `bounded distance preserves every accepted edit cost`() {
        val words = mutableListOf("")
        var level = listOf("")
        repeat(3) {
            level = level.flatMap { word -> listOf('A', '4', 'O', '0', 'X').map { word + it } }
            words.addAll(level)
        }
        for (left in words) for (right in words) for (budget in listOf(0, 2, 4)) {
            assertEquals("$left / $right budget=$budget",
                SpeciesOcrTypography.weightedDistance(left, right).coerceAtMost(budget + 1),
                SpeciesOcrTypography.boundedWeightedDistance(left, right, budget))
        }
    }

    @Test fun `window search preserves accepted costs with noise clipping and glyph substitutions`() {
        val readings = listOf("B0N1GHTSLASTGOURGEIST", "GOURGEI5T", "XXSNEASELXX", "MEWTO", "", "CP1499SKARMORY", "PORYGO", "PIK@CHU")
        val candidates = listOf("GOURGEIST", "SNEASEL", "MEW", "MEWTWO", "SKARMORY", "PORYGON", "PORYGON2", "PIKACHU")
        for (reading in readings) for (candidate in candidates) {
            val budget = SpeciesOcrTypography.allowedCost(candidate.length)
            val expected = legacyWindowDistance(reading, candidate)
            assertEquals("$reading / $candidate", expected.coerceAtMost(budget + 1),
                SpeciesTextResolver.closestWindowDistance(reading, candidate, budget))
        }
    }

    private fun legacyWindowDistance(reading: String, candidate: String): Int =
        if (reading.length < candidate.length) SpeciesOcrTypography.weightedDistance(reading, candidate)
        else (candidate.length - 2..candidate.length + 2).filter { it > 0 && it <= reading.length }
            .minOf { width -> (0..reading.length - width).minOf { start ->
                SpeciesOcrTypography.weightedDistance(reading.substring(start, start + width), candidate)
            } }

}
