package com.example.overdex.battle.team

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.OwnedPokemonDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class FieldTeamEditorTest {
    private val species = Pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), "Kanto",
        fastMoves = listOf(Move("Volt Switch", PokemonType.ELECTRIC, 12, 16, true)),
        chargedMoves = listOf("Thunder Punch", "Wild Charge", "Brick Break").map {
            Move(it, PokemonType.ELECTRIC, 50, 40, false)
        })
    private val knowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = species.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int) = species.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames() = setOf(species.name)
    }
    private fun saved() = CurrentBattleTeam((1..3).map {
        CurrentBattleTeamMember(it, 26, "Raichu", "Volt Switch", listOf("Thunder Punch"), "owned-$it")
    })

    private fun collectionTeam(draft: FieldTeamDraft) = CurrentBattleTeam(draft.team().members.mapIndexed { index, member ->
        member.copy(ownedPokemonId = draft.slots[index].newCard?.id ?: draft.slots[index].ownedPokemonId)
    })

    private fun readyImports(editor: FieldTeamEditor) {
        editor.openScan(List(3) { "Raichu" })
        repeat(3) { slot ->
            editor.importNewCard(slot)
            editor.chooseFast(slot, "Volt Switch")
            editor.toggleCharged(slot, "Thunder Punch")
        }
        editor.setTeamName("Practice")
    }

    @Test fun `scan requires moves and never assigns a same-species owned card`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }) { selected = it }
            editor.open(saved())
            editor.openScan(List(3) { "Raichu" })
            assertFalse(editor.draft.value!!.ready)
            editor.useTeam()
            assertNull(selected)
            repeat(3) { slot ->
                editor.chooseFast(slot, "Volt Switch")
                editor.toggleCharged(slot, "Thunder Punch")
            }
            assertTrue(editor.draft.value!!.ready)
            editor.useTeam()
            assertEquals(listOf(1, 2, 3), selected!!.members.map { it.slot })
            assertTrue(selected!!.members.all { it.ownedPokemonId == null })
            assertNull(editor.draft.value)
        } finally { scope.cancel() }
    }

    @Test fun `explicit saved team keeps card ids and cancelling an edit never saves`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }) { selected = it }
            editor.open(saved())
            editor.toggleCharged(0, "Wild Charge")
            editor.close()
            assertNull(selected)
            editor.open(saved())
            editor.useTeam()
            assertEquals(saved(), selected)
        } finally { scope.cancel() }
    }

    @Test fun `reopening a pending scan keeps partial moves without saving or reusing an owned card`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var saves = 0
            val editor = FieldTeamEditor(knowledge, scope, { true }) { saves++ }
            val roster = List(3) { "Raichu" }
            editor.openScan(roster)
            editor.chooseFast(1, "Volt Switch")
            editor.toggleCharged(1, "Wild Charge")
            editor.close()
            editor.resumeScan(roster)
            assertEquals("Volt Switch", editor.draft.value!!.slots[1].fastMove)
            assertEquals(listOf("Wild Charge"), editor.draft.value!!.slots[1].chargedMoves)
            assertNull(editor.draft.value!!.slots[1].ownedPokemonId)
            assertFalse(editor.draft.value!!.ready)
            assertEquals(0, saves)

            // A new explicit scan may be a different copy, even with identical names.
            editor.openScan(roster)
            assertTrue(editor.draft.value!!.slots.all { it.fastMove == null && it.chargedMoves.isEmpty() })

            editor.chooseFast(0, "Volt Switch")
            editor.toggleCharged(0, "Thunder Punch")
            editor.close()
            editor.discardScan() // The RESCAN path, even before a new roster is confirmed.
            editor.resumeScan(roster)
            assertTrue(editor.draft.value!!.slots.all { it.fastMove == null && it.chargedMoves.isEmpty() })
        } finally { scope.cancel() }
    }

    @Test fun `two charged moves maximum and no moves outside the species pool`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val editor = FieldTeamEditor(knowledge, scope, { true }) {}
            editor.open(saved())
            editor.toggleCharged(0, "Wild Charge")
            editor.toggleCharged(0, "Brick Break")
            editor.chooseFast(0, "Incinerate")
            assertEquals(listOf("Thunder Punch", "Wild Charge"), editor.draft.value!!.slots[0].chargedMoves)
            assertEquals("Volt Switch", editor.draft.value!!.slots[0].fastMove)
            editor.toggleCharged(0, "Thunder Punch")
            editor.toggleCharged(0, "Brick Break")
            assertEquals(listOf("Wild Charge", "Brick Break"), editor.draft.value!!.slots[0].chargedMoves)
        } finally { scope.cancel() }
    }

    @Test fun `GO closes unsaved draft and prevents changing the running match`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var allowed = true
            var saves = 0
            val editor = FieldTeamEditor(knowledge, scope, { allowed }) { saves++ }
            editor.open(saved())
            allowed = false
            editor.useTeam()
            editor.open(saved())
            assertNull(editor.draft.value)
            assertEquals(0, saves)
        } finally { scope.cancel() }
    }

    @Test fun `late catalogue response cannot reopen a closed editor`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val ready = CompletableDeferred<Unit>()
        try {
            val delayed = object : PokemonKnowledge by knowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? { ready.await(); return species }
            }
            val editor = FieldTeamEditor(delayed, scope, { true }) {}
            editor.openScan(List(3) { "Raichu" })
            assertTrue(editor.draft.value!!.loading)
            editor.close()
            ready.complete(Unit)
            assertNull(editor.draft.value)
        } finally { scope.cancel() }
    }

    @Test fun `failed new scan does not resurrect the previous complete loadout`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fail = false
            var saves = 0
            val failing = object : PokemonKnowledge by knowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? {
                    if (fail) error("Catalogue unavailable")
                    return species
                }
            }
            val editor = FieldTeamEditor(failing, scope, { true }) { saves++ }
            editor.open(saved())
            assertTrue(editor.draft.value!!.ready)
            fail = true
            editor.openScan(List(3) { "Raichu" })
            assertFalse(editor.draft.value!!.ready)
            assertEquals("Catalogue unavailable", editor.draft.value!!.error)
            editor.useTeam()
            assertEquals(0, saves)
        } finally { scope.cancel() }
    }

    @Test fun `scan candidates retain exact form and legacy choices without auto linking`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val alolan = species.copy(name = "Raichu (Alolan)")
            val regionalKnowledge = object : PokemonKnowledge by knowledge {
                override suspend fun getPokemonByName(name: String) = when (name) {
                    alolan.name -> alolan
                    species.name -> species
                    else -> null
                }
            }
            val cards = listOf(
                OwnedPokemon(id = "ordinary", speciesId = 26, speciesName = "Raichu"),
                OwnedPokemon(id = "alolan", speciesId = 26, speciesName = alolan.name),
                OwnedPokemon(id = "legacy", speciesId = 26),
                OwnedPokemon(id = "pikachu", speciesId = 25, speciesName = alolan.name),
            )
            val editor = FieldTeamEditor(regionalKnowledge, scope, { true }, loadOwnedCards = { cards }) {}
            editor.openScan(List(3) { alolan.name })
            assertTrue(editor.draft.value!!.isScannedTeam)
            editor.draft.value!!.slots.forEach { member ->
                assertEquals(listOf("alolan", "legacy"), member.ownedCandidates.map { it.id })
                assertNull(member.ownedPokemonId)
                assertNull(member.newCard)
            }
            editor.chooseOwnedCard(0, "ordinary")
            assertNull(editor.draft.value!!.slots[0].ownedPokemonId)
            editor.chooseOwnedCard(0, "legacy")
            assertEquals("legacy", editor.draft.value!!.slots[0].ownedPokemonId)
            editor.open(saved())
            assertFalse(editor.draft.value!!.isScannedTeam)
        } finally { scope.cancel() }
    }

    @Test fun `choosing an owned card copies only legal moves without changing its metadata`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val card = OwnedPokemon(id = "mine", speciesId = 26, speciesName = "Raichu", displayName = "Partner",
                cp = 1499, isShadow = true, fastMove = "Incinerate", chargedMove1 = "Thunder Punch", chargedMove2 = "Psychic")
            val editor = FieldTeamEditor(knowledge, scope, { true }, loadOwnedCards = { listOf(card) }) {}
            editor.openScan(List(3) { "Raichu" })
            editor.importNewCard(0)
            editor.chooseOwnedCard(0, card.id)
            val slot = editor.draft.value!!.slots[0]
            assertEquals(card.id, slot.ownedPokemonId)
            assertNull(slot.newCard)
            assertNull(slot.fastMove)
            assertEquals(listOf("Thunder Punch"), slot.chargedMoves)
            assertFalse(slot.ready)
            assertEquals(card, slot.ownedCandidates.single())
        } finally { scope.cancel() }
    }

    @Test fun `new card UUID and explicit metadata survive edits and scan panel reopen`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val editor = FieldTeamEditor(knowledge, scope, { true }, saveToCollection = { collectionTeam(it) }) {}
            readyImports(editor)
            val original = editor.draft.value!!.slots[0].newCard!!
            editor.importNewCard(0)
            editor.updateImport(0) { it.copy(id = "replacement", speciesId = 25, speciesName = "Pikachu",
                nickname = "Partner", cp = "1499", shadow = true, createdAt = -1) }
            editor.toggleCharged(0, "Wild Charge")
            val expected = editor.draft.value!!.slots[0].newCard!!
            assertEquals(original.id, expected.id)
            assertEquals(original.createdAt, expected.createdAt)
            assertEquals(26, expected.speciesId)
            assertEquals("Raichu", expected.speciesName)
            assertEquals("Volt Switch", expected.fastMove)
            assertEquals("Wild Charge", expected.chargedMove2)
            assertEquals("Partner", expected.nickname)
            assertTrue(expected.shadow)
            editor.close()
            editor.resumeScan(List(3) { "Raichu" })
            assertEquals(expected, editor.draft.value!!.slots[0].newCard)
            assertEquals("Practice", editor.draft.value!!.teamName)
            assertTrue(editor.draft.value!!.collectionReady)
        } finally { scope.cancel() }
    }

    @Test fun `collection readiness requires distinct specimens and valid new card fields`() {
        val new = OwnedPokemonDraft(speciesId = 26, speciesName = "Raichu")
        val slot = FieldTeamSlot(species, fastMove = "Volt Switch", chargedMoves = listOf("Thunder Punch"), newCard = new)
        val draft = FieldTeamDraft(List(3) { slot.copy(newCard = new.copy(id = "new-$it")) },
            collectionEnabled = true, teamName = "Practice")
        assertTrue(draft.collectionReady)
        assertFalse(draft.copy(slots = List(3) { slot }).collectionReady)
        assertFalse(draft.copy(slots = listOf(slot.copy(newCard = new.copy(cp = "bad"))) + draft.slots.drop(1)).collectionReady)
        assertFalse(draft.copy(slots = listOf(slot.copy(ownedPokemonId = "also-selected")) + draft.slots.drop(1)).collectionReady)
        assertFalse(draft.copy(slots = listOf(slot.copy(newCard = null, ownedPokemonId = null)) + draft.slots.drop(1)).collectionReady)
        assertFalse(draft.copy(collectionEnabled = false).collectionReady)
        assertFalse(draft.copy(teamName = "   ").collectionReady)
        assertFalse(draft.copy(teamName = "Great, Training").collectionReady)
        assertFalse(draft.copy(teamName = "Great\nTraining").collectionReady)
        assertFalse(draft.copy(teamName = "Great\rTraining").collectionReady)
    }

    @Test fun `collection save hands stable card identities to active team only after persistence`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var persisted: FieldTeamDraft? = null
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }, saveToCollection = {
                assertNull(selected)
                persisted = it
                collectionTeam(it)
            }, save = { selected = it })
            readyImports(editor)
            val ids = editor.draft.value!!.slots.map { it.newCard!!.id }
            editor.saveTeamToCollection()
            assertEquals("Practice", persisted!!.teamName)
            assertEquals(ids, selected!!.members.map { it.ownedPokemonId })
            assertNull(editor.draft.value)
        } finally { scope.cancel() }
    }

    @Test fun `failed collection save keeps same UUIDs for retry without falling back to ordinary use`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var attempts = 0
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }, saveToCollection = {
                if (++attempts == 1) error("Storage unavailable")
                collectionTeam(it)
            }, save = { selected = it })
            readyImports(editor)
            val ids = editor.draft.value!!.slots.map { it.newCard!!.id }
            editor.saveTeamToCollection()
            assertEquals("Storage unavailable", editor.draft.value!!.error)
            assertEquals(ids, editor.draft.value!!.slots.map { it.newCard!!.id })
            assertTrue(editor.draft.value!!.collectionReady)
            assertNull(selected)
            editor.saveTeamToCollection()
            assertEquals(2, attempts)
            assertEquals(ids, selected!!.members.map { it.ownedPokemonId })
        } finally { scope.cancel() }
    }

    @Test fun `closing panel does not cancel accepted card save or activate an obsolete selection`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            var persisted = false
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }, saveToCollection = {
                gate.await()
                persisted = true
                collectionTeam(it)
            }, save = { selected = it })
            readyImports(editor)
            val ids = editor.draft.value!!.slots.map { it.newCard!!.id }
            editor.saveTeamToCollection()
            assertTrue(editor.draft.value!!.loading)
            editor.close()
            editor.resumeScan(List(3) { "Raichu" })
            assertEquals(ids, editor.draft.value!!.slots.map { it.newCard!!.id })
            gate.complete(Unit)
            yield()
            assertTrue(persisted)
            assertNull(selected)
            assertEquals(ids, editor.draft.value!!.slots.map { it.newCard!!.id })
        } finally { scope.cancel() }
    }

    @Test fun `match starting during accepted save cannot change running team`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            var allowed = true
            var persisted = false
            var saves = 0
            val editor = FieldTeamEditor(knowledge, scope, { allowed }, saveToCollection = {
                gate.await()
                persisted = true
                collectionTeam(it)
            }) { saves++ }
            readyImports(editor)
            editor.saveTeamToCollection()
            allowed = false
            gate.complete(Unit)
            yield()
            assertTrue(persisted)
            assertEquals(0, saves)
            assertNull(editor.draft.value)
        } finally { scope.cancel() }
    }

    @Test fun `collection requires a team name but ordinary use does not require imported cards`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var writes = 0
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true }, saveToCollection = {
                writes++
                collectionTeam(it)
            }, save = { selected = it })
            readyImports(editor)
            editor.setTeamName("")
            editor.saveTeamToCollection()
            assertEquals("Enter a name for this team.", editor.draft.value!!.error)
            assertEquals(0, writes)
            editor.useTeam()
            assertNotNull(selected)
            assertTrue(selected!!.members.all { it.ownedPokemonId == null })
            assertEquals(0, writes)
        } finally { scope.cancel() }
    }

    @Test fun `owned collection read failure does not block continuing with explicit moves`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var selected: CurrentBattleTeam? = null
            val editor = FieldTeamEditor(knowledge, scope, { true },
                loadOwnedCards = { error("Cards unavailable") }, saveToCollection = { collectionTeam(it) },
                save = { selected = it })
            editor.openScan(List(3) { "Raichu" })
            assertEquals("Cards unavailable", editor.draft.value!!.error)
            assertFalse(editor.draft.value!!.collectionEnabled)
            repeat(3) { slot ->
                editor.chooseFast(slot, "Volt Switch")
                editor.toggleCharged(slot, "Thunder Punch")
            }
            assertTrue(editor.draft.value!!.ready)
            assertFalse(editor.draft.value!!.collectionReady)
            editor.useTeam()
            assertNotNull(selected)
            assertTrue(selected!!.members.all { it.ownedPokemonId == null })
        } finally { scope.cancel() }
    }

    @Test fun `catalogue fallback cannot replace a requested canonical form or configured dex id`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val fallback = object : PokemonKnowledge by knowledge {
                override suspend fun getPokemonByName(name: String) = species
            }
            val editor = FieldTeamEditor(fallback, scope, { true }, saveToCollection = { collectionTeam(it) }) {}
            editor.openScan(List(3) { "Raichu (Alolan)" })
            assertFalse(editor.draft.value!!.ready)
            assertTrue(editor.draft.value!!.slots.all { it.pokemon == null })
            assertTrue(editor.draft.value!!.error!!.contains("Raichu (Alolan)"))

            editor.open(saved().copy(members = saved().members.map { it.copy(speciesName = "Raichu (Alolan)") }))
            assertFalse(editor.draft.value!!.ready)
            assertTrue(editor.draft.value!!.slots.all { it.pokemon == null })

            editor.open(saved().copy(members = saved().members.map { it.copy(speciesId = 25) }))
            assertFalse(editor.draft.value!!.ready)
            assertTrue(editor.draft.value!!.slots.all { it.pokemon == null })

            editor.openScan(List(3) { "Raichu" })
            editor.chooseSpecies(0, "Raichu (Alolan)")
            assertEquals("Raichu", editor.draft.value!!.slots[0].pokemon?.name)
            assertTrue(editor.draft.value!!.error!!.contains("Raichu (Alolan)"))
            editor.importNewCard(0)
            assertEquals("Raichu", editor.draft.value!!.slots[0].newCard!!.speciesName)
        } finally { scope.cancel() }
    }
}
