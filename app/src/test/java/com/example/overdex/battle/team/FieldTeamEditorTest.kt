package com.example.overdex.battle.team

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
}
