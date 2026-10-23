package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import org.junit.Assert.*
import org.junit.Test

class PlayerActiveIdentityInferenceTest {
    private val engine = PlayerActiveIdentityInference()
    private fun article(id: String, p: TestimonyPayload, seconds: Long, source: String = "test") =
        RealityArticle(ArticleId(id), seconds * 1000, 99999, SourceId(source), p,
            monotonicTimeNanos = seconds * 1_000_000_000L)
    private fun team(scan: Boolean = true) {
        engine.accept(article("config-a", PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Trailblaze", "Wild Charge")), 0))
        engine.accept(article("config-k", PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Brick Break", "Thunder")), 0))
        engine.accept(article("config-o", PlayerTeamSlotConfigured(3, "Obstagoon", 862, "Counter", listOf("Night Slash")), 0))
        if (scan) {
            engine.accept(article("scan-a", PlayerTeamRosterSlotWitnessed(1, "Raichu (Alolan)", 26, "fALOLA", .2f), 1))
            engine.accept(article("scan-k", PlayerTeamRosterSlotWitnessed(2, "Raichu", 26, "normal", .2f), 1))
        }
    }
    private fun species(id: String, name: String, seconds: Long, entry: Boolean = false) =
        article(id, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, name, if (name == "Obstagoon") 862 else 26),
            seconds, if (entry) "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" else "PLAYER_SPECIES_OVERLAY_PIPELINE")

    @Test fun `late ball decrease resolves same name incoming slot without fainting it`() {
        team()
        engine.accept(species("lead", "Raichu", 3))
        engine.accept(species("incoming", "Raichu", 20, entry = true))
        val faint = article("ko", ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER,
            "Raichu", 26, "PLAYER_POKE_BALL_COUNT_DECREASE"), 21)
        val resolved = engine.accept(faint).single().payload
        assertEquals(2, resolved.teamSlot)
        assertEquals("Raichu", resolved.speciesName)
        assertEquals(20_000_000_000L, resolved.appearanceStartedAtNanos)
        assertEquals(2, engine.at(22_000_000_000L)?.payload?.teamSlot)
        assertEquals("Raichu (Alolan)", (engine.projectFaint(faint).payload as ActivePokemonFaintedWitnessed).speciesName)
    }

    @Test fun `observed lead keeps form and slot across repeated bare badges`() {
        team()
        val raw = species("first", "Raichu", 3)
        val result = engine.accept(raw).single()
        assertEquals("Raichu (Alolan)", result.payload.speciesName)
        assertEquals(1, result.payload.teamSlot)
        assertTrue(result.evidence.containsAll(listOf(ArticleId("scan-a"), ArticleId("first"))))
        assertEquals("Raichu", (raw.payload as ActivePokemonSpeciesWitnessed).speciesName)
        assertTrue(engine.accept(species("repeated", "Raichu", 4)).isEmpty())
        assertEquals(3_000_000_000L, engine.at(4_000_000_000L)?.payload?.appearanceStartedAtNanos)
    }

    @Test fun `configured lead alone does not select between same name forms`() {
        team(scan = false)
        assertNull(engine.accept(species("first", "Raichu", 3)).single().payload.teamSlot)
    }

    @Test fun `ordinary form can be scanned in first slot without stealing Alolan moves`() {
        team()
        engine.accept(article("scan-reordered", PlayerTeamRosterSlotWitnessed(1, "Raichu", 26, "normal", .2f), 2))
        engine.accept(article("scan-reordered-a", PlayerTeamRosterSlotWitnessed(2, "Raichu (Alolan)", 26, "fALOLA", .2f), 2))
        val result = engine.accept(species("first", "Raichu", 3)).single()
        assertEquals("Raichu", result.payload.speciesName)
        assertEquals(1, result.payload.teamSlot)
    }

    @Test fun `switch away and return uses independent button evidence and original source interval`() {
        team()
        engine.accept(species("lead", "Raichu", 3))
        engine.accept(species("switch", "Obstagoon", 10))
        val ambiguous = engine.accept(species("return", "Raichu", 20, entry = true)).single()
        assertNull(ambiguous.payload.teamSlot)
        engine.accept(article("label1", PlayerChargeMoveLabelRead(1, "BRICK BREAK"), 21))
        val ordinary = engine.accept(article("label2", PlayerChargeMoveLabelRead(1, "BRICK BREAK"), 22)).single()
        assertEquals(2, ordinary.payload.teamSlot)
        assertEquals(20_000_000_000L, ordinary.payload.appearanceStartedAtNanos)
        engine.accept(species("next", "Obstagoon", 30))
        assertTrue(engine.accept(species("late-old-badge", "Raichu", 24)).isEmpty())
        // A late label is scoped to the already completed appearance.
        engine.accept(article("late-label", PlayerChargeMoveLabelRead(2, "THUNDER"), 23))
        assertEquals("Obstagoon", engine.at(31_000_000_000L)?.payload?.speciesName)
        assertEquals(2, engine.at(25_000_000_000L)?.payload?.teamSlot)
        engine.accept(species("return-a", "Raichu", 40, entry = true))
        engine.accept(article("a-label1", PlayerChargeMoveLabelRead(1, "TRAILBLAZE"), 41))
        val alolan = engine.accept(article("a-label2", PlayerChargeMoveLabelRead(1, "TRAILBLAZE"), 42)).single()
        assertEquals(1, alolan.payload.teamSlot)
    }

    @Test fun `faint closes only the identified form and lingering badge does not revive the other`() {
        team()
        engine.accept(species("lead", "Raichu", 3))
        val faint = article("faint", ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26), 10)
        engine.accept(faint)
        assertEquals("Raichu (Alolan)", (engine.projectFaint(faint).payload as ActivePokemonFaintedWitnessed).speciesName)
        assertNull(engine.at(11_000_000_000L))
        assertTrue(engine.accept(species("lingering", "Raichu", 11)).isEmpty())
        val ordinary = engine.accept(species("replacement", "Raichu", 13, entry = true)).single()
        assertEquals(2, ordinary.payload.teamSlot)
        assertEquals("Raichu", ordinary.payload.speciesName)
    }

    @Test fun `same name entry starts unknown appearance without assuming a switch to the other form`() {
        team()
        engine.accept(species("lead", "Raichu", 3, entry = true))
        val result = engine.accept(species("next-raichu", "Raichu", 20, entry = true)).single()
        assertNull(result.payload.teamSlot)
        assertEquals(20_000_000_000L, result.payload.appearanceStartedAtNanos)
    }

    @Test fun `duplicate and cached labels cannot establish a form and opponent never uses player roster`() {
        team(scan = false)
        engine.accept(species("lead", "Raichu", 3))
        val label = article("label", PlayerChargeMoveLabelRead(1, "TRAILBLAZE"), 4)
        assertTrue(engine.accept(label).isEmpty())
        assertTrue(engine.accept(label).isEmpty())
        assertTrue(engine.accept(article("cached", PlayerChargeMoveLabelRead(1, "TRAILBLAZE", "CACHED"), 5)).isEmpty())
        assertNull(engine.at(6_000_000_000L)?.payload?.teamSlot)
        assertTrue(engine.accept(article("mirror", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Raichu", 26), 6)).isEmpty())
    }
}
