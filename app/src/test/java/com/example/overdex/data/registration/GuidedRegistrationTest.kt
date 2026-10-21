package com.example.overdex.data.registration

import com.example.overdex.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GuidedRegistrationTest {
    private val persistenceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val shock = Move("Thunder Shock", PokemonType.ELECTRIC, 3, 9, true, 2)
    private val spark = Move("Spark", PokemonType.ELECTRIC, 4, 7, true, 2)
    private val discharge = Move("Discharge", PokemonType.ELECTRIC, 65, 45, false)
    private val surf = Move("Surf", PokemonType.WATER, 65, 40, false)
    private val pikachu = Pokemon(25, "Pikachu", listOf(PokemonType.ELECTRIC), "Kanto",
        nextEvolutions = listOf(Evolution("026", "Raichu")), fastMoves = listOf(shock), chargedMoves = listOf(discharge, surf))
    private val raichu = Pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), "Kanto",
        prevEvolutions = listOf(Evolution("025", "Pikachu")), fastMoves = listOf(shock, spark), chargedMoves = listOf(discharge, surf))
    private val catalogue = RegistrationCatalogue(listOf(pikachu, raichu))
    private val reader = DetailPageReader(catalogue)
    private fun line(text: String, y: Float, x: Float = .3f, right: Float = .7f) = DetailTextLine(text, x, y, right, y + .015f)
    private fun top(name: String = "Pikachu", cp: Int = 500) = listOf(
        line("CP$cp", .1f), line(name, .4f), line("85 / 85 HP", .46f),
        line("ELECTRIC", .52f), line("WEIGHT", .54f, .05f, .2f), line("HEIGHT", .54f, .8f, .95f),
        line("Pikachu Candy", .61f), line("POWER UP", .75f))
    private fun moves(fast: String = "Thunder Shock", second: Boolean = false) = listOf(
        line("TRAINER BATTLES", .35f), line("$fast 3", .42f), line("Discharge 65", .55f),
        line(if (second) "Surf 65" else "NEW ATTACK", .68f))
    private fun observe(draft: RegistrationDraft, lines: List<DetailTextLine>, time: Long) =
        draft.accept(reader.read(lines), time, 1080, 2400)
    private fun twice(draft: RegistrationDraft, lines: List<DetailTextLine>, time: Long) =
        observe(observe(draft, lines, time), lines, time + 700)

    @Test fun `scrolling retains identity and cp when only moves remain visible`() {
        val named = twice(RegistrationDraft(), top(), 1000)
        val complete = twice(named, moves(), 3000)
        val resolved = complete.resolve(catalogue)
        assertTrue(resolved.ready)
        val card = requireNotNull(complete.card(resolved))
        assertEquals(25, card.speciesId)
        assertEquals(500, card.cp)
        assertEquals("Thunder Shock", card.fastMove)
        assertEquals("Discharge", card.chargedMove1)
        assertNull(card.chargedMove2)
        assertEquals(complete.cardId, card.id)
        assertTrue(complete.evidence.all { it.sourceWidth == 1080 && it.sourceHeight == 2400 })
    }

    @Test fun `candy does not replace nickname with base evolution`() {
        val draft = twice(twice(RegistrationDraft(), top("Sparky"), 1000), moves(), 3000)
        val unresolved = draft.resolve(catalogue)
        assertNull(unresolved.species)
        assertEquals(setOf(25, 26), unresolved.candidates.map { it.id }.toSet())
        assertFalse(unresolved.ready)
        val confirmed = draft.copy(confirmedSpeciesId = 26)
        val card = requireNotNull(confirmed.card(confirmed.resolve(catalogue)))
        assertEquals(26, card.speciesId)
        assertEquals("Sparky", card.displayName)
    }

    @Test fun `family plus evolution availability distinguishes nickname`() {
        val draft = twice(twice(RegistrationDraft(), top("Sparky") + line("EVOLVE", .82f), 1000), moves(), 3000)
        assertEquals(25, draft.resolve(catalogue).species?.id)
        assertEquals("Sparky", draft.card(draft.resolve(catalogue))?.displayName)
    }

    @Test fun `family plus unique legal move distinguishes evolved nickname`() {
        val draft = twice(twice(RegistrationDraft(), top("Sparky"), 1000), moves("Spark"), 3000)
        assertEquals(26, draft.resolve(catalogue).species?.id)
    }

    @Test fun `evolved pokemon nicknamed for base species is not forced to base species`() {
        val draft = twice(twice(RegistrationDraft(), top("Pikachu"), 1000), moves("Spark"), 3000)
        val resolved = draft.resolve(catalogue)
        assertEquals(26, resolved.species?.id)
        assertEquals("Pikachu", draft.card(resolved)?.displayName)
    }

    @Test fun `two OCR lines in same frame cannot confirm anything`() {
        val draft = observe(observe(RegistrationDraft(), top(), 1000), top(), 1000)
        assertTrue(draft.values("CP").isEmpty())
        assertNull(draft.resolve(catalogue).species)
    }

    @Test fun `reading the same screenshot later does not create independent evidence`() {
        val read = reader.read(top())
        val once = RegistrationDraft().accept(read, 1000, 1080, 2400, sourceId = "photo-a")
        val again = once.accept(read, 2000, 1080, 2400, sourceId = "photo-a")
        assertEquals(once.evidence, again.evidence)
        assertTrue(again.values("CP").isEmpty())
        val independent = again.accept(read, 3000, 1080, 2400, sourceId = "photo-b")
        assertEquals(listOf("500"), independent.values("CP"))
    }

    @Test fun `reviewed screenshot can confirm fields without pretending two frames existed`() {
        val read = reader.read(top())
        val once = RegistrationDraft().accept(read, 1000, 1080, 2400, sourceId = "photo-a")
        val reviewed = once.accept(read, 2000, 1080, 2400, "photo-a", confirmedByUser = true)
        assertEquals(listOf("500"), reviewed.values("CP"))
        assertEquals(setOf("photo-a"), reviewed.evidence.map { it.sourceId }.toSet())
        assertTrue(reviewed.evidence.any { it.confirmedByUser })
        val duplicate = reviewed.accept(read, 3000, 1080, 2400, "photo-a", confirmedByUser = true)
        assertEquals(reviewed.evidence, duplicate.evidence)
        assertEquals(reviewed, persistenceJson.decodeFromString<RegistrationDraft>(persistenceJson.encodeToString(reviewed)))
    }

    @Test fun `moves alone cannot register pokemon or fabricate cp`() {
        val draft = twice(RegistrationDraft(), moves(second = true), 1000)
        assertFalse(draft.resolve(catalogue).ready)
        assertNull(draft.card(draft.resolve(catalogue)))
    }

    @Test fun `complete move list reads two charged moves without unlock button`() {
        val draft = twice(twice(RegistrationDraft(), top(), 1000), moves(second = true), 3000)
        val card = requireNotNull(draft.card(draft.resolve(catalogue)))
        assertEquals("Surf", card.chargedMove2)
    }

    @Test fun `one move seen does not claim entire move list was read`() {
        val partial = moves().filterNot { it.text == "NEW ATTACK" }
        val draft = twice(twice(RegistrationDraft(), top(), 1000), partial, 3000)
        assertFalse(draft.resolve(catalogue).ready)
        assertTrue("bottom of the move list" in draft.resolve(catalogue).missing)
    }

    @Test fun `another pokemon cannot donate its moves to current card`() {
        val draft = twice(RegistrationDraft(), top(), 1000)
        val changed = twice(draft, top("Raichu", 1200) + moves("Spark"), 3000)
        assertTrue(changed.boundaryConflict)
        assertTrue(changed.values("FAST").isEmpty())
        assertFalse(changed.resolve(catalogue).ready)
    }

    @Test fun `saved card freezes and draft persistence preserves identity`() {
        val complete = twice(twice(RegistrationDraft(), top(), 1000), moves(), 3000).copy(saved = true)
        val restored = persistenceJson.decodeFromString<RegistrationDraft>(persistenceJson.encodeToString(complete))
        assertEquals(complete, restored)
        assertEquals(complete, twice(restored, top("Raichu", 1200), 5000))
        assertNotEquals(complete.cardId, RegistrationDraft().cardId)
    }

    @Test fun `battle and collection names are not detail-page registration evidence`() {
        assertFalse(reader.read(listOf(line("Pikachu", .1f), line("CP500", .1f), line("Go, Sneasel!", .4f))).isDetailPage)
        assertFalse(reader.read(listOf(line("Pikachu", .2f), line("Raichu", .4f), line("CP1200", .3f))).isDetailPage)
    }

    @Test fun `powerup cp is not mistaken for original cp`() {
        val read = reader.read(top() + line("CP560", .8f))
        assertEquals(listOf("500"), read.fields.filter { it.field == "CP" }.map { it.value })
    }

    @Test fun `split candy label supports nickname without claiming candy species`() {
        val input = top("Sparky").filterNot { it.text.contains("Candy") } +
            line("Pikachu", .59f) + line("CANDY", .61f)
        val read = reader.read(input)
        assertEquals("Pikachu", read.fields.single { it.field == "CANDY" }.value)
        assertEquals("Sparky", read.fields.single { it.field == "TITLE" }.value)
    }

    @Test fun `shadow needs positive text and repeated frames`() {
        val draft = twice(twice(RegistrationDraft(), top() + line("PURIFY", .7f), 1000), moves(), 3000)
        assertTrue(requireNotNull(draft.card(draft.resolve(catalogue))).isShadow)
    }

    @Test fun `XL height badge is saved independently from candy XL`() {
        val sized = top() + line("XL", .50f, .80f, .9f) + line("Pikachu Candy XL", .67f)
        val complete = twice(twice(RegistrationDraft(), sized, 1000), moves(), 3000)
        assertEquals(PokemonSize.XL, complete.card(complete.resolve(catalogue))?.size)
        val candyOnly = twice(twice(RegistrationDraft(), top() + line("XL", .7f), 1000), moves(), 3000)
        assertEquals(PokemonSize.UNKNOWN, candyOnly.card(candyOnly.resolve(catalogue))?.size)
    }

    @Test fun `ranked cry alone never silently selects a species and survives reload`() {
        val evidence = RegistrationCryEvidence(1000, "recording-sha", 1200,
            listOf(RegistrationCryCandidate(26, "Raichu", .95f, "reference-sha")))
        val draft = twice(RegistrationDraft(), top("Sparky"), 1000).copy(cries = listOf(evidence))
        assertNull(draft.resolve(catalogue).species)
        val confirmed = draft.copy(confirmedSpeciesId = 26, cries = listOf(evidence.copy(confirmedSpeciesId = 26)))
        val restored = persistenceJson.decodeFromString<RegistrationDraft>(persistenceJson.encodeToString(confirmed))
        assertEquals(26, restored.resolve(catalogue).species?.id)
        assertEquals(evidence.audioSha256, restored.cries.single().audioSha256)
        assertEquals(26, restored.cries.single().confirmedSpeciesId)
    }

    @Test fun `move icon read as O is separated only in the icon column`() {
        val read = reader.read(listOf(line("TRAINER BATTLES", .3f), line("OThunder Shock", .4f, .08f), line("ODischarge 65", .55f, .08f)))
        assertEquals("Thunder Shock", read.fields.single { it.field == "FAST" }.value)
        assertEquals("Discharge", read.fields.single { it.field == "CHARGED" }.value)
        assertTrue(reader.read(listOf(line("TRAINER BATTLES", .3f), line("OThunder Shock", .4f, .4f)))
            .fields.none { it.field == "FAST" })
    }

    @Test fun `a pokemon nicknamed for a move is still a nickname`() {
        assertTrue(reader.read(top("Thunder Shock")).fields.none { it.field == "FAST" })
    }

    @Test fun `current game master supplies families absent from old pokedex`() {
        val file = java.io.File("src/main/assets/gamemaster.json")
        val reference = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(file.readText())
        val current = buildRegistrationCatalogue(reference.pokemon.distinctBy { it.dex }.map {
            Pokemon(it.dex, it.speciesName.substringBefore(" ("), emptyList(), "", fastMoves = emptyList(), chargedMoves = emptyList())
        }, reference.pokemon, { id -> reference.moves.find { it.moveId == id } })
        assertTrue(current.family("Charcadet").containsAll(setOf("CHARCADET", "ARMAROUGE", "CERULEDGE")))
        assertTrue(current.family("Pikachu").containsAll(setOf("PICHU", "PIKACHU", "RAICHU")))
        assertEquals(setOf(PokemonType.FIRE, PokemonType.GHOST), current.byName.getValue("CERULEDGE").types.toSet())
    }
}
