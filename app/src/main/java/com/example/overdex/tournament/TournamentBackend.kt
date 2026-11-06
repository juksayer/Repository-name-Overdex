package com.example.overdex.tournament

import kotlinx.coroutines.flow.Flow

interface TournamentBackend {
    fun events(): Flow<List<TournamentEvent>>
    fun lobby(eventId: String): Flow<TournamentLobby>
    suspend fun create(event: TournamentEvent)
    suspend fun host(eventId: String, command: TournamentHostCommand)
    suspend fun attendance(eventId: String, value: TournamentAttendance)
    suspend fun prepare(eventId: String, value: BattlePreparation)
}
