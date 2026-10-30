package com.example.overdex.battle.team

import com.example.overdex.battle.history.BattleMetricEvent
import com.example.overdex.battle.history.BattleMetricKind
import com.example.overdex.battle.history.BattleRecordQuery
import com.example.overdex.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CurrentBattleTeamMoveEditTest {
    private val greninja = Pokemon(658, "Greninja", listOf(PokemonType.WATER, PokemonType.DARK), "Kalos",
        fastMoves = listOf(Move("Water Shuriken", PokemonType.WATER, 6, 14, true, 3),
            Move("Bubble", PokemonType.WATER, 8, 11, true, 3)),
        chargedMoves = listOf(Move("Hydro Cannon", PokemonType.WATER, 80, 40, false),
            Move("Night Slash", PokemonType.DARK, 50, 35, false), Move("Surf", PokemonType.WATER, 75, 45, false)))
    private val member = CurrentBattleTeamMember(2, 658, "Greninja", "Water Shuriken",
        listOf("Hydro Cannon", "Surf"), "shadow-greninja")
    private val other = member.copy(slot = 1, ownedPokemonId = "other-greninja")
    private val team = CurrentBattleTeam(listOf(other, member))
    private val card = OwnedPokemon(id = "shadow-greninja", speciesId = 658, speciesName = "Greninja",
        displayName = "Battle buddy", cp = 1499, isShadow = true, isShiny = true, isFavorite = true,
        fastMove = "Water Shuriken", chargedMove1 = "Hydro Cannon", chargedMove2 = "Surf",
        createdAt = 15L, updatedAt = 20L, teams = listOf("Halloween", "Great"), size = PokemonSize.XL)

    private fun correction() = CurrentBattleTeamMoveEdit.prepare(team, member, greninja,
        "Water Shuriken", listOf("Night Slash", "Surf"))

    @Test fun `charged correction preserves specimen identity slot teammates and serialized team`() {
        val edited = correction().team
        assertEquals(other, edited.members.first())
        assertEquals(member.copy(chargedMoveNames = listOf("Night Slash", "Surf")), edited.members[1])
        assertEquals(edited, Json.decodeFromString<CurrentBattleTeam>(Json.encodeToString(edited)))
        assertEquals("shadow-greninja", edited.members[1].ownedPokemonId)
    }

    @Test fun `linked card changes moves and update time without changing its history identity or memberships`() {
        val edited = correction().updateOwnedCard(card, 100L)
        assertEquals(card.copy(chargedMove1 = "Night Slash", updatedAt = 100L), edited)
        assertEquals(card.id, edited.id)
        assertEquals(card.createdAt, edited.createdAt)
        assertEquals(card.teams, edited.teams)
    }

    @Test fun `past battle event remains attached to same specimen and keeps recorded move`() {
        val snapshot = OwnedPokemonBattleSnapshot.from(card)
        val past = BattleMetricEvent("old-article", BattleMetricKind.CHARGED_MOVE, 1000L,
            "Greninja", snapshot, "Hydro Cannon", "Fomantis", listOf("old-article"), "RECORDED_TIMELINE")
        val editedCard = correction().updateOwnedCard(card, 100L)
        assertTrue(BattleRecordQuery(ownedPokemonId = editedCard.id).accepts(past))
        assertFalse(BattleRecordQuery(ownedPokemonId = other.ownedPokemonId).accepts(past))
        assertEquals("Hydro Cannon", past.moveName)
        assertSame(snapshot, past.pokemon)
        assertEquals("Night Slash", editedCard.chargedMove1)
    }

    @Test fun `removing second charge clears it on same owned card`() {
        val edit = CurrentBattleTeamMoveEdit.prepare(team, member, greninja, "Bubble", listOf("Night Slash"))
        assertEquals(card.copy(fastMove = "Bubble", chargedMove1 = "Night Slash", chargedMove2 = null, updatedAt = 50L),
            edit.updateOwnedCard(card, 50L))
    }

    @Test fun `unlinked team member is not assigned a new owned card`() {
        val unlinked = member.copy(ownedPokemonId = null)
        val edited = CurrentBattleTeamMoveEdit.prepare(CurrentBattleTeam(listOf(unlinked)), unlinked,
            greninja, "water shuriken", listOf("night slash"))
        assertNull(edited.member.ownedPokemonId)
        assertEquals("Water Shuriken", edited.member.fastMoveName)
        assertEquals(listOf("Night Slash"), edited.member.chargedMoveNames)
    }

    @Test fun `stale member cannot overwrite a newer team selection`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrentBattleTeamMoveEdit.prepare(team.copy(members = listOf(other, member.copy(fastMoveName = "Bubble"))),
                member, greninja, "Water Shuriken", listOf("Night Slash"))
        }
    }

    @Test fun `regional reference cannot silently become ordinary form`() {
        val regional = member.copy(speciesId = 26, speciesName = "Raichu (Alolan)")
        assertThrows(IllegalArgumentException::class.java) {
            CurrentBattleTeamMoveEdit.prepare(CurrentBattleTeam(listOf(regional)), regional,
                greninja.copy(id = 26, name = "Raichu"), "Water Shuriken", listOf("Night Slash"))
        }
    }

    @Test fun `illegal move and duplicate charge choices are rejected`() {
        listOf(listOf("Flamethrower"), emptyList(), listOf("Night Slash", "night slash"),
            listOf("Night Slash", "Surf", "Hydro Cannon")).forEach { charged ->
            assertThrows(IllegalArgumentException::class.java) {
                CurrentBattleTeamMoveEdit.prepare(team, member, greninja, "Water Shuriken", charged)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrentBattleTeamMoveEdit.prepare(team, member, greninja, "Charm", listOf("Night Slash"))
        }
    }

    @Test fun `linked card species form and specimen mismatch cannot be written`() {
        listOf(card.copy(id = "other-greninja"), card.copy(speciesId = 26),
            card.copy(speciesName = "Greninja (Ash)")).forEach { wrong ->
            assertThrows(IllegalArgumentException::class.java) { correction().updateOwnedCard(wrong, 100L) }
        }
    }
}
