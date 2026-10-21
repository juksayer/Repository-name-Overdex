package com.example.overdex.data.registration

import com.example.overdex.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RegistrationFieldReaderTest {
    private val species = Pokemon(25, "Pikachu", listOf(PokemonType.ELECTRIC), "Kanto",
        fastMoves = listOf(Move("Thunder Shock", PokemonType.ELECTRIC, 3, 9, true, 2)),
        chargedMoves = listOf(Move("Surf", PokemonType.WATER, 65, 40, false), Move("Discharge", PokemonType.ELECTRIC, 65, 45, false)))
    private val catalogue = RegistrationCatalogue(listOf(species))
    private val reader = RegistrationFieldReader(catalogue)
    private val persistenceJson = Json { encodeDefaults = true }
    private fun line(text: String, y: Float = .8f) = DetailTextLine(text, .1f, y, .8f, y + .02f)
    private fun read(id: String, text: String, y: Float = .8f) = reader.read(id, listOf(line(text, y)))

    @Test fun `independent scrolled fields complete one draft without simultaneous page anchors`() {
        var draft = RegistrationDraft()
        listOf("SpeciesName" to "Pikachu", "CombatPower" to "CP 500", "CandyPanel" to "Pikachu Candy",
            "FastMoveRow" to "Thunder Shock 3", "ChargedMoveRowA" to "Surf 65", "MovesEnd" to "NEW ATTACK")
            .forEachIndexed { i, (id, text) ->
                draft = draft.acceptField(id, read(id, text, if (i % 2 == 0) .25f else .8f), i.toLong(), 1080, 2400, "frame-$i")
            }
        val resolution = draft.resolve(catalogue)
        assertTrue(resolution.ready)
        assertFalse(draft.saved)
        assertEquals("Thunder Shock", resolution.fastMove)
        assertEquals(listOf("Surf"), resolution.chargedMoves)
        assertEquals(draft.cardId, draft.card(resolution)?.id)
        assertEquals(6, draft.evidence.map { it.sourceId }.distinct().size)
    }
    @Test fun `tapped box limits meaning and rejects multiple move readings`() {
        assertTrue(read("FastMoveRow", "Surf 65").fields.isEmpty())
        assertTrue(read("ChargedMoveRowA", "Thunder Shock 3").fields.isEmpty())
        assertEquals(listOf("TITLE"), read("SpeciesName", "Thunder Shock").fields.map { it.field })
        assertTrue(reader.read("ChargedMoveRowA", listOf(line("Surf"), line("Discharge", .5f))).fields.isEmpty())
        assertTrue(read("CombatPower", "500 Stardust").fields.isEmpty())
    }
    @Test fun `correcting a charged move preserves the other slot and the original reading`() {
        var draft = RegistrationDraft().acceptField("ChargedMoveRowA", read("ChargedMoveRowA", "Surf"), 1, 1080, 2400, "a")
        draft = draft.acceptField("ChargedMoveRowB", read("ChargedMoveRowB", "Discharge"), 2, 1080, 2400, "b")
        draft = draft.acceptField("ChargedMoveRowA", read("ChargedMoveRowA", "Discharge"), 3, 1080, 2400, "c")
        assertEquals(listOf("Discharge"), draft.values("CHARGED"))
        assertEquals(3, draft.evidence.size)
        assertTrue(draft.evidence.first().superseded)
        assertEquals(draft, persistenceJson.decodeFromString<RegistrationDraft>(persistenceJson.encodeToString(draft)))
    }
    @Test fun `confirmed cp correction does not trigger a different specimen boundary`() {
        var draft = RegistrationDraft().acceptField("CombatPower", read("CombatPower", "CP 500"), 1, 1080, 2400, "a")
        draft = draft.acceptField("CombatPower", read("CombatPower", "CP 580"), 2, 1080, 2400, "b")
        assertEquals(listOf("580"), draft.values("CP"))
        assertFalse(draft.boundaryConflict)
    }
    @Test fun `changed identity fields require a choice but repeated readings and move edits do not`() {
        var draft = RegistrationDraft()
        listOf("CombatPower" to "CP 500", "SpeciesName" to "Pikachu", "CandyPanel" to "Pikachu Candy")
            .forEachIndexed { i, (id, text) -> draft = draft.acceptField(id, read(id, text), i.toLong(), 1080, 2400, "$i") }
        assertEquals(listOf("CP"), draft.identityChanges(read("CombatPower", "CP 580")).map { it.field })
        assertEquals(listOf("TITLE"), draft.identityChanges(read("SpeciesName", "Sparky")).map { it.field })
        assertTrue(draft.identityChanges(read("SpeciesName", "pikachu")).isEmpty())
        assertTrue(draft.identityChanges(read("CombatPower", "CP 500")).isEmpty())
        assertTrue(draft.identityChanges(read("ChargedMoveRowA", "Surf")).isEmpty())
    }
    @Test fun `a stale species name from another candy family cannot silently become a nickname`() {
        val other = species.copy(id = 150, name = "Mewtwo")
        val twoFamilies = RegistrationCatalogue(listOf(species, other))
        val twoReader = RegistrationFieldReader(twoFamilies)
        var draft = RegistrationDraft()
        listOf("SpeciesName" to "Pikachu", "CombatPower" to "CP 500", "CandyPanel" to "Mewtwo Candy",
            "FastMoveRow" to "Thunder Shock", "ChargedMoveRowA" to "Surf", "MovesEnd" to "NEW ATTACK")
            .forEachIndexed { i, (id, text) ->
                draft = draft.acceptField(id, twoReader.read(id, listOf(line(text))), i.toLong(), 1080, 2400, "$i")
            }
        val resolution = draft.resolve(twoFamilies)
        assertEquals(150, resolution.species?.id)
        assertFalse(resolution.ready)
        assertNull(draft.card(resolution))
        assertTrue(requireNotNull(resolution.conflict).contains("different Pokémon families"))
        // The user may genuinely have named this Mewtwo "Pikachu"; explicit species choice resolves it.
        val confirmed = draft.copy(confirmedSpeciesId = 150)
        assertTrue(confirmed.resolve(twoFamilies).ready)
        assertEquals("Pikachu", confirmed.card(confirmed.resolve(twoFamilies))?.displayName)
    }
    @Test fun `many cp corrections retain the first confirmed name`() {
        var draft = RegistrationDraft().acceptField("SpeciesName", read("SpeciesName", "Pikachu"), 0, 1080, 2400, "name")
        repeat(130) { i -> draft = draft.acceptField("CombatPower", read("CombatPower", "CP ${500+i}"), i.toLong(), 1080, 2400, "cp-$i") }
        assertEquals(listOf("Pikachu"), draft.values("TITLE"))
        assertEquals(listOf("629"), draft.values("CP"))
        assertTrue(draft.evidence.size <= 120)
    }
    @Test fun `split candy labels and shadow bonus do not become arbitrary species or moves`() {
        val candy = reader.read("CandyPanel", listOf(line("Pikachu", .3f), line("CANDY", .33f)))
        assertEquals("Pikachu", candy.fields.single().value)
        assertEquals("Thunder Shock", read("FastMoveRow", "OThunder Shock 3 Shadow Bonus +1").fields.single().value)
        assertEquals("shadow", read("ShadowStatus", "SHADOW BONUS").fields.single().value)
        assertTrue(read("SizeLabel", "Candy XL").fields.isEmpty())
        assertTrue(read("ShinyIndicator", "Pikachu").fields.isEmpty())
    }
    @Test fun `one layout contains one fast move box and both charged move slots`() {
        val ids = RegistrationFields.regions.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(1, ids.count { it == "FastMoveRow" })
        assertFalse("SummaryFastMove" in ids)
        assertTrue(ids.containsAll(listOf("ChargedMoveRowA", "ChargedMoveRowB", "HpRow", "MoveHeading", "MovesEnd")))
    }
}
