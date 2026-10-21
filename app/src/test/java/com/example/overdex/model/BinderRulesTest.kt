package com.example.overdex.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BinderRulesTest {
    private val reference = Pokemon(6, "Charizard", listOf(PokemonType.FIRE, PokemonType.FLYING), "Kanto",
        fastMoves = emptyList(), chargedMoves = emptyList())
    private val card = OwnedPokemon(id = "one-card", speciesId = 6, cp = 1500)

    @Test fun `league limits include the boundary and exclude unknown cp`() {
        val great = BinderRules(league = BinderLeague.GREAT)
        assertTrue(great.accepts(card, reference))
        assertFalse(great.accepts(card.copy(cp = 1501), reference))
        assertFalse(great.accepts(card.copy(cp = null), reference))
        val ultra = BinderRules(league = BinderLeague.ULTRA)
        assertTrue(ultra.accepts(card.copy(cp = 2500), reference))
        assertFalse(ultra.accepts(card.copy(cp = 2501), reference))
        assertTrue(BinderRules(league = BinderLeague.MASTER).accepts(card.copy(cp = 5000), reference))
    }

    @Test fun `custom cap cannot relax the league and contradictory range is rejected`() {
        val rules = BinderRules(league = BinderLeague.GREAT, maxCp = 2500)
        assertEquals(1500, rules.effectiveMaxCp)
        assertFalse(rules.accepts(card.copy(cp = 2000), reference))
        assertNotNull(rules.copy(minCp = 1600).validationError())
        assertNotNull(BinderRules(maxCp = 9).validationError())
        assertEquals(500, rules.copy(maxCp = 500).effectiveMaxCp)
    }

    @Test fun `dual types allow either selected type but exclusions always take priority`() {
        val rules = BinderRules(allowedTypes = setOf(PokemonType.FIRE))
        assertTrue(rules.accepts(card, reference))
        assertFalse(rules.copy(excludedTypes = setOf(PokemonType.FLYING)).accepts(card, reference))
        assertFalse(rules.copy(allowedTypes = setOf(PokemonType.WATER)).accepts(card, reference))
        assertFalse(rules.accepts(card, null))
    }

    @Test fun `species region and status restrictions compose`() {
        val rules = BinderRules(allowedSpecies = setOf(6), region = "kanto", shadow = BinderFlag.ONLY,
            shiny = BinderFlag.EXCLUDE)
        assertTrue(rules.accepts(card.copy(isShadow = true), reference))
        assertFalse(rules.accepts(card, reference))
        assertFalse(rules.accepts(card.copy(isShadow = true, isShiny = true), reference))
        assertFalse(rules.copy(excludedSpecies = setOf(6)).accepts(card.copy(isShadow = true), reference))
        assertFalse(rules.copy(region = "Johto").accepts(card.copy(isShadow = true), reference))
    }

    @Test fun `open league presets exclude mega and primal entries`() {
        val rules = BinderRules(league = BinderLeague.MASTER)
        assertFalse(rules.accepts(card, reference.copy(name = "Mega Charizard X")))
        assertFalse(rules.accepts(card, reference.copy(name = "Groudon (Primal)")))
        assertTrue(rules.accepts(card, reference.copy(name = "Meganium")))
    }

    @Test fun `hisui entries do not disappear behind an older paldea database label`() {
        val wyrdeer = reference.copy(id = 899, name = "Wyrdeer", region = "Paldea")
        val owned = card.copy(speciesId = 899)
        assertTrue(BinderRules(region = "Hisui").accepts(owned, wyrdeer))
        assertFalse(BinderRules(region = "Paldea").accepts(owned, wyrdeer))
    }

    @Test fun `saved custom rules survive serialization and never copy specimens`() {
        val binder = OwnedPokemonBinder("custom-1", "Great Fire", BinderRules(league = BinderLeague.GREAT,
            allowedTypes = setOf(PokemonType.FIRE), excludedSpecies = setOf(25)))
        val restored = Json.decodeFromString<OwnedPokemonBinder>(Json.encodeToString(binder))
        assertEquals(binder, restored)
        assertSame(card, listOf(card).filter { restored.contains(it, reference) }.single())
        assertFalse(restored.contains(card.copy(cp = 1600), reference))
    }
}
