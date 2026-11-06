package com.example.overdex.tournament

import kotlinx.serialization.Serializable

@Serializable enum class TournamentPhase { LOBBY, ACTIVE, COMPLETED, ENDED_EARLY }
@Serializable enum class TournamentLeague { GREAT, ULTRA, MASTER }
@Serializable enum class SeriesPhase { WAITING, PLAYING, COMPLETE, BYE }
@Serializable enum class BattleResultReport { NONE, WIN, LOSS, DRAW, DISPUTE }
@Serializable enum class TournamentResultBasis { AGREED, HOST_RESOLVED }

@Serializable data class TournamentSettings(
    val title: String = "Friendly tournament",
    val league: TournamentLeague = TournamentLeague.GREAT,
    val rules: String = "",
    val winsPerSeries: Int = 1,
    val winsInFinal: Int = 1,
) {
    fun finalTarget(players: Int) = maxOf(winsInFinal, when (players) { 2 -> 3; 3 -> 2; else -> 1 })
    fun minimumBattles(players: Int) = if (players < 2) 0 else (players - 2) * winsPerSeries + finalTarget(players)
}

@Serializable data class TournamentPlayer(val accountId: String, val displayName: String, val trainerCode: String)
@Serializable data class TournamentBattle(
    val key: String,
    val winnerId: String? = null,
    val draw: Boolean = false,
    val basis: TournamentResultBasis,
    val reason: String = "",
    val completedAt: Long,
)
@Serializable data class TournamentSeries(
    val id: String,
    val round: Int,
    val position: Int,
    val playerA: String? = null,
    val playerB: String? = null,
    val sourceA: String? = null,
    val sourceB: String? = null,
    val winsRequired: Int,
    val phase: SeriesPhase = SeriesPhase.WAITING,
    val winnerId: String? = null,
    val battles: List<TournamentBattle> = emptyList(),
) {
    val battleKey get() = "${id}b${battles.size + 1}"
    val playerIds get() = listOfNotNull(playerA, playerB)
    fun wins(playerId: String?) = battles.count { !it.draw && it.winnerId == playerId }
}
@Serializable data class TournamentEvent(
    val id: String,
    val ownerId: String,
    val settings: TournamentSettings,
    val players: List<TournamentPlayer>,
    val phase: TournamentPhase = TournamentPhase.LOBBY,
    val participantIds: List<String> = emptyList(),
    val series: List<TournamentSeries> = emptyList(),
    val revision: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val endReason: String = "",
    val settingsRevision: Long = 0,
) {
    val memberIds get() = players.map { it.accountId }
    val activeSeries get() = if (phase == TournamentPhase.ACTIVE) series.filter { it.phase == SeriesPhase.PLAYING } else emptyList()
    val activeBattles get() = activeSeries.associate { it.battleKey to it.playerIds }
    val completedBattleCount get() = series.sumOf { it.battles.size }
    val championId get() = series.maxByOrNull { it.round }?.winnerId.takeIf { phase == TournamentPhase.COMPLETED }
    fun name(id: String?) = players.firstOrNull { it.accountId == id }?.displayName ?: "Awaiting opponent"
    companion object { const val MAX_PLAYERS = 16 }
}

@Serializable data class TournamentAttendance(val accepted: Boolean = false, val checkedIn: Boolean = false,
                                            val declined: Boolean = false, val settingsRevision: Long = 0)
@Serializable data class BattlePreparation(
    val playerId: String,
    val battleKey: String,
    val ready: Boolean = false,
    val result: BattleResultReport = BattleResultReport.NONE,
    val note: String = "",
) { val documentId get() = "${battleKey}_$playerId" }

data class TournamentLobby(
    val event: TournamentEvent,
    val attendance: Map<String, TournamentAttendance> = emptyMap(),
    val preparations: Map<String, BattlePreparation> = emptyMap(),
    val fromCache: Boolean = false,
) {
    fun preparation(series: TournamentSeries, playerId: String): BattlePreparation? =
        preparations["${series.battleKey}_$playerId"]
}

sealed interface TournamentHostCommand {
    data class Settings(val value: TournamentSettings) : TournamentHostCommand
    data class Invite(val player: TournamentPlayer) : TournamentHostCommand
    data object Start : TournamentHostCommand
    data class Finalize(val seriesId: String, val battleKey: String) : TournamentHostCommand
    /** This attests to a played battle, never a bye or a forfeit before play. */
    data class Resolve(val seriesId: String, val battleKey: String, val winnerId: String?, val draw: Boolean, val reason: String) : TournamentHostCommand
    data class EndEarly(val reason: String) : TournamentHostCommand
}

data class TournamentState(
    val accountId: String? = null,
    val events: List<TournamentEvent> = emptyList(),
    val selectedId: String? = null,
    val lobby: TournamentLobby? = null,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)
