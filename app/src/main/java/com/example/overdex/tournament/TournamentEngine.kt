package com.example.overdex.tournament

import kotlin.random.Random

data class TournamentAgreement(val winnerId: String?, val draw: Boolean)

/** Competition decisions are separate from the immutable recording of a Pokémon GO battle. */
object TournamentEngine {
    fun validateSettings(settings: TournamentSettings) {
        require(settings.title.isNotBlank() && settings.title.length <= 80) { "Use a tournament name of 1–80 characters." }
        require(settings.rules.length <= 2000) { "Keep tournament rules within 2,000 characters." }
        require(settings.winsPerSeries in 1..3 && settings.winsInFinal in 1..3) { "A series needs one, two, or three wins." }
    }

    fun validatePlayer(player: TournamentPlayer) {
        require(player.accountId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "This trainer account is not valid." }
        require(player.displayName.isNotBlank() && player.displayName.length <= 40) { "This trainer needs a display name." }
        require(player.trainerCode.matches(Regex("[0-9]{12}"))) { "This trainer needs a twelve-digit trainer code." }
    }

    fun validateEvent(event: TournamentEvent) {
        validateSettings(event.settings)
        require(event.id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "The tournament ID is not valid." }
        require(event.players.size in 1..TournamentEvent.MAX_PLAYERS) { "A tournament supports up to 16 invited trainers." }
        event.players.forEach(::validatePlayer)
        require(event.memberIds.distinct().size == event.players.size && event.ownerId in event.memberIds) { "The host and invitations must have distinct accounts." }
        require(event.revision >= 0 && event.settingsRevision >= 0 && event.createdAt >= 0 && event.updatedAt >= event.createdAt)
        if (event.phase == TournamentPhase.ACTIVE || event.phase == TournamentPhase.COMPLETED) {
            require(event.participantIds.size >= 2 && event.participantIds.distinct().size == event.participantIds.size)
            require(event.memberIds.containsAll(event.participantIds))
            require(event.settings.minimumBattles(event.participantIds.size) >= 3)
        }
    }

    fun agreement(lobby: TournamentLobby, series: TournamentSeries): TournamentAgreement? {
        if (series.phase != SeriesPhase.PLAYING) return null
        val a = series.playerA ?: return null
        val b = series.playerB ?: return null
        val first = lobby.preparation(series, a)?.takeIf { it.ready } ?: return null
        val second = lobby.preparation(series, b)?.takeIf { it.ready } ?: return null
        return when {
            first.result == BattleResultReport.WIN && second.result == BattleResultReport.LOSS -> TournamentAgreement(a, false)
            first.result == BattleResultReport.LOSS && second.result == BattleResultReport.WIN -> TournamentAgreement(b, false)
            first.result == BattleResultReport.DRAW && second.result == BattleResultReport.DRAW -> TournamentAgreement(null, true)
            else -> null
        }
    }

    fun apply(lobby: TournamentLobby, actorId: String, command: TournamentHostCommand, now: Long): TournamentEvent {
        val event = lobby.event
        validateEvent(event)
        require(actorId == event.ownerId) { "Only the tournament host can do that." }
        require(!lobby.fromCache) { "Reconnect before changing tournament progress." }
        require(now >= event.createdAt)
        val next = when (command) {
            is TournamentHostCommand.Settings -> {
                requireLobby(event)
                validateSettings(command.value)
                event.copy(settings = command.value.copy(title = command.value.title.trim(), rules = command.value.rules.trim()),
                    settingsRevision = event.settingsRevision + 1)
            }
            is TournamentHostCommand.Invite -> {
                requireLobby(event)
                validatePlayer(command.player)
                require(command.player.accountId !in event.memberIds) { "That trainer is already invited." }
                require(event.players.size < TournamentEvent.MAX_PLAYERS) { "This first version supports 16 invited trainers." }
                event.copy(players = event.players + command.player)
            }
            TournamentHostCommand.Start -> {
                requireLobby(event)
                val entrants = event.players.filter { player ->
                    lobby.attendance[player.accountId]?.let { it.accepted && !it.declined && it.checkedIn &&
                        it.settingsRevision == event.settingsRevision } == true
                }.map { it.accountId }
                require(entrants.size >= 2) { "At least two trainers must accept and check in." }
                val seeded = entrants.shuffled(Random(event.id.hashCode()))
                event.copy(phase = TournamentPhase.ACTIVE, participantIds = seeded,
                    series = settle(makeBracket(seeded, event.settings)))
            }
            is TournamentHostCommand.Finalize -> {
                val series = currentSeries(event, command.seriesId, command.battleKey)
                val result = requireNotNull(agreement(lobby, series)) { "Both players must be ready and agree on this battle's result." }
                record(event, series, result, TournamentResultBasis.AGREED, "", now)
            }
            is TournamentHostCommand.Resolve -> {
                val series = currentSeries(event, command.seriesId, command.battleKey)
                require(command.reason.trim().length in 5..240) { "Explain the host's correction in 5–240 characters." }
                require(if (command.draw) command.winnerId == null else command.winnerId in series.playerIds) { "Choose a player in this pairing, or a draw." }
                record(event, series, TournamentAgreement(command.winnerId, command.draw),
                    TournamentResultBasis.HOST_RESOLVED, command.reason.trim(), now)
            }
            is TournamentHostCommand.EndEarly -> {
                require(event.phase == TournamentPhase.LOBBY || event.phase == TournamentPhase.ACTIVE) { "This tournament has already ended." }
                require(command.reason.trim().length in 5..240) { "Give a reason of 5–240 characters for ending early." }
                event.copy(phase = TournamentPhase.ENDED_EARLY, endReason = command.reason.trim())
            }
        }
        return next.copy(revision = event.revision + 1, updatedAt = maxOf(now, event.updatedAt)).also(::validateEvent)
    }

    private fun requireLobby(event: TournamentEvent) {
        require(event.phase == TournamentPhase.LOBBY) { "Settings and invitations are locked once the tournament starts." }
    }

    private fun currentSeries(event: TournamentEvent, id: String, key: String): TournamentSeries {
        require(event.phase == TournamentPhase.ACTIVE) { "This tournament is not playing." }
        val series = requireNotNull(event.series.find { it.id == id }) { "This pairing is no longer available." }
        require(series.phase == SeriesPhase.PLAYING && series.battleKey == key) { "This battle has already advanced. Review the current pairing." }
        return series
    }

    private fun makeBracket(players: List<String>, settings: TournamentSettings): List<TournamentSeries> {
        var size = 2
        while (size < players.size) size *= 2
        var seeds = listOf(1, 2)
        while (seeds.size < size) {
            val complement = seeds.size * 2 + 1
            seeds = seeds.flatMap { listOf(it, complement - it) }
        }
        val rounds = Integer.numberOfTrailingZeros(size)
        val result = mutableListOf<TournamentSeries>()
        for (round in 1..rounds) {
            val count = size shr round
            repeat(count) { position ->
                result += TournamentSeries("r${round}s${position + 1}", round, position,
                    playerA = if (round == 1) players.getOrNull(seeds[position * 2] - 1) else null,
                    playerB = if (round == 1) players.getOrNull(seeds[position * 2 + 1] - 1) else null,
                    sourceA = if (round > 1) "r${round - 1}s${position * 2 + 1}" else null,
                    sourceB = if (round > 1) "r${round - 1}s${position * 2 + 2}" else null,
                    winsRequired = if (round == rounds) settings.finalTarget(players.size) else settings.winsPerSeries)
            }
        }
        return result
    }

    private fun settle(input: List<TournamentSeries>): List<TournamentSeries> {
        val settled = linkedMapOf<String, TournamentSeries>()
        input.sortedWith(compareBy({ it.round }, { it.position })).forEach { series ->
            fun resolved(source: String?) = source == null || settled[source]?.phase in listOf(SeriesPhase.COMPLETE, SeriesPhase.BYE)
            val a = series.sourceA?.let { settled[it]?.winnerId } ?: series.playerA
            val b = series.sourceB?.let { settled[it]?.winnerId } ?: series.playerB
            val next = when {
                series.phase == SeriesPhase.COMPLETE -> series
                !resolved(series.sourceA) || !resolved(series.sourceB) -> series
                a == null || b == null -> series.copy(playerA = a, playerB = b, phase = SeriesPhase.BYE, winnerId = a ?: b)
                else -> series.copy(playerA = a, playerB = b, phase = SeriesPhase.PLAYING)
            }
            settled[series.id] = next
        }
        return settled.values.toList()
    }

    private fun record(event: TournamentEvent, series: TournamentSeries, outcome: TournamentAgreement,
                       basis: TournamentResultBasis, reason: String, now: Long): TournamentEvent {
        val battle = TournamentBattle(series.battleKey, outcome.winnerId, outcome.draw, basis, reason, now)
        var changed = series.copy(battles = series.battles + battle)
        if (!outcome.draw && changed.wins(outcome.winnerId) >= series.winsRequired) {
            changed = changed.copy(phase = SeriesPhase.COMPLETE, winnerId = outcome.winnerId)
        }
        val bracket = settle(event.series.map { if (it.id == series.id) changed else it })
        val final = bracket.maxBy { it.round }
        val finished = final.phase == SeriesPhase.COMPLETE
        val count = bracket.sumOf { it.battles.size }
        require(!finished || count >= 3) { "A completed tournament must contain at least three played battles." }
        return event.copy(series = bracket, phase = if (finished) TournamentPhase.COMPLETED else TournamentPhase.ACTIVE)
    }
}
