package com.example.overdex.battle.team

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The player's selected battle team, known before Pokémon GO begins the match. */
@Serializable
data class CurrentBattleTeamMember(
    val slot: Int,
    val speciesId: Int,
    val speciesName: String,
    val fastMoveName: String,
    val chargedMoveNames: List<String>,
    val ownedPokemonId: String? = null,
) {
    init {
        require(slot in 1..3)
        require(speciesName.isNotBlank())
        require(fastMoveName.isNotBlank())
        require(chargedMoveNames.size in 1..2)
        require(chargedMoveNames.none(String::isBlank))
    }
}

@Serializable
data class CurrentBattleTeam(val members: List<CurrentBattleTeamMember> = emptyList()) {
    init {
        require(members.size <= 3)
        require(members.map { it.slot }.distinct().size == members.size)
    }

    fun normalized(): CurrentBattleTeam = copy(
        members = members.sortedBy { it.slot }.mapIndexed { index, member ->
            member.copy(slot = index + 1, chargedMoveNames = member.chargedMoveNames.distinct().take(2))
        }
    )
}

@Serializable
data class SavedBattleTeam(val id: String, val name: String, val team: CurrentBattleTeam)

/** Active lineup plus reusable named snapshots; older active teams remain readable. */
class CurrentBattleTeamStore(context: Context, preferencesName: String = PREFERENCES_NAME) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _team = MutableStateFlow(load())
    val team = _team.asStateFlow()
    private val _savedTeams = MutableStateFlow(preferences.getString("saved_teams", null)?.let {
        json.decodeFromString<List<SavedBattleTeam>>(it)
    }.orEmpty())
    val savedTeams = _savedTeams.asStateFlow()

    @Synchronized fun saveNamed(name: String, team: CurrentBattleTeam, id: String? = null): SavedBattleTeam {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Enter a team name." }
        require(team.members.size == 3) { "A saved battle team needs three Pokémon and their moves." }
        require(_savedTeams.value.none { it.id != id && it.name.equals(cleanName, true) }) { "That team name is already in use." }
        val saved = SavedBattleTeam(id ?: java.util.UUID.randomUUID().toString(), cleanName, team.normalized())
        val updated = _savedTeams.value.filterNot { it.id == saved.id } + saved
        check(preferences.edit().putString("saved_teams", json.encodeToString(updated)).commit()) { "Unable to save the team." }
        _savedTeams.value = updated
        return saved
    }

    fun replace(team: CurrentBattleTeam) {
        val normalized = team.normalized()
        check(preferences.edit().putString(KEY_TEAM, json.encodeToString(normalized)).commit()) {
            "Could not persist the current battle team"
        }
        _team.value = normalized
    }

    fun clear() = replace(CurrentBattleTeam())

    private fun load(): CurrentBattleTeam {
        val encoded = preferences.getString(KEY_TEAM, null) ?: return CurrentBattleTeam()
        return runCatching { json.decodeFromString<CurrentBattleTeam>(encoded).normalized() }
            .getOrElse { CurrentBattleTeam() }
    }

    private companion object {
        const val PREFERENCES_NAME = "current_battle_team"
        const val KEY_TEAM = "team"
    }
}
