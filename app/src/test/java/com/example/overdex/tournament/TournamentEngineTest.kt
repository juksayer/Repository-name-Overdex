package com.example.overdex.tournament

import org.junit.Assert.*
import org.junit.Test

class TournamentEngineTest {
    private fun player(n: Int) = TournamentPlayer("player$n", "Trainer $n", n.toString().padStart(12, '0'))
    private fun lobby(count: Int = 2): TournamentLobby {
        val players = (1..count).map(::player)
        return TournamentLobby(TournamentEvent("test-event", "player1", TournamentSettings(), players,
            createdAt = 1, updatedAt = 1), players.associate { it.accountId to TournamentAttendance(true, true) })
    }
    private fun apply(lobby: TournamentLobby, command: TournamentHostCommand) =
        lobby.copy(event = TournamentEngine.apply(lobby, "player1", command, lobby.event.updatedAt + 1))
    private fun started(count: Int = 2) = apply(lobby(count), TournamentHostCommand.Start)
    private fun report(lobby: TournamentLobby, series: TournamentSeries, winner: String?, draw: Boolean = false): TournamentLobby {
        val reports = series.playerIds.associate { id ->
            val value = BattlePreparation(id, series.battleKey, true,
                if (draw) BattleResultReport.DRAW else if (id == winner) BattleResultReport.WIN else BattleResultReport.LOSS)
            value.documentId to value
        }
        return lobby.copy(preparations = lobby.preparations + reports)
    }
    private fun finishBattle(lobby: TournamentLobby, series: TournamentSeries, winner: String?, draw: Boolean = false): TournamentLobby =
        apply(report(lobby, series, winner, draw), TournamentHostCommand.Finalize(series.id, series.battleKey))

    @Test fun twoTrainersMustPlayAtLeastThreeBattlesBeforeChampion() {
        var room = started()
        assertEquals(3, room.event.activeSeries.single().winsRequired)
        repeat(2) {
            val series = room.event.activeSeries.single()
            room = finishBattle(room, series, series.playerA)
            assertEquals(TournamentPhase.ACTIVE, room.event.phase)
            assertNull(room.event.championId)
        }
        val last = room.event.activeSeries.single()
        room = finishBattle(room, last, last.playerA)
        assertEquals(TournamentPhase.COMPLETED, room.event.phase)
        assertEquals(3, room.event.completedBattleCount)
        assertEquals(last.playerA, room.event.championId)
        assertTrue(room.event.activeBattles.isEmpty())
    }

    @Test fun everySupportedRosterProducesACompleteSingleEliminationBracket() {
        for (players in 2..TournamentEvent.MAX_PLAYERS) {
            var room = started(players)
            var guard = 0
            while (room.event.phase == TournamentPhase.ACTIVE) {
                assertTrue("No stranded bracket for $players entrants", room.event.activeSeries.isNotEmpty())
                val series = room.event.activeSeries.first()
                assertEquals(2, series.playerIds.distinct().size)
                room = finishBattle(room, series, series.playerA)
                assertTrue(++guard < 100)
            }
            assertEquals(TournamentPhase.COMPLETED, room.event.phase)
            assertEquals(players - 1, room.event.series.count { it.phase == SeriesPhase.COMPLETE })
            assertEquals(room.event.settings.minimumBattles(players), room.event.completedBattleCount)
            assertTrue(room.event.completedBattleCount >= 3)
            assertTrue(room.event.series.filter { it.phase == SeriesPhase.BYE }.all { it.battles.isEmpty() })
        }
    }

    @Test fun threePlayersHaveOneByeButAtLeastThreeActualBattles() {
        val room = started(3)
        assertEquals(1, room.event.series.count { it.phase == SeriesPhase.BYE })
        assertEquals(1, room.event.activeSeries.size)
        assertEquals(2, room.event.series.maxBy { it.round }.winsRequired)
        assertEquals(0, room.event.completedBattleCount)
    }

    @Test fun fourPlayersCanPlayTheirTwoSemifinalsInParallel() {
        val room = started(4)
        assertEquals(2, room.event.activeBattles.size)
        assertEquals(4, room.event.activeBattles.values.flatten().distinct().size)
        assertEquals(SeriesPhase.WAITING, room.event.series.maxBy { it.round }.phase)
    }

    @Test fun drawCountsAsPlayedButDoesNotAwardAWin() {
        val room = started()
        val first = room.event.activeSeries.single()
        val after = finishBattle(room, first, null, draw = true)
        assertEquals(1, after.event.completedBattleCount)
        assertEquals(0, after.event.activeSeries.single().wins(first.playerA))
        assertEquals("r1s1b2", after.event.activeSeries.single().battleKey)
    }

    @Test fun readinessAndMutualAgreementAreRequired() {
        val room = started()
        val series = room.event.activeSeries.single()
        val ready = report(room, series, series.playerA)
        assertNotNull(TournamentEngine.agreement(ready, series))
        val key = "${series.battleKey}_${series.playerB}"
        val missingReady = ready.copy(preparations = ready.preparations + (key to ready.preparations.getValue(key).copy(ready = false)))
        assertNull(TournamentEngine.agreement(missingReady, series))
        assertThrows(IllegalArgumentException::class.java) { apply(missingReady, TournamentHostCommand.Finalize(series.id, series.battleKey)) }
        val disputed = ready.copy(preparations = ready.preparations + (key to ready.preparations.getValue(key).copy(result = BattleResultReport.WIN)))
        assertNull(TournamentEngine.agreement(disputed, series))
        assertEquals(0, disputed.event.completedBattleCount)
    }

    @Test fun replayedFinalizeCannotAwardAnotherBattle() {
        val room = started()
        val series = room.event.activeSeries.single()
        val after = finishBattle(room, series, series.playerA)
        assertThrows(IllegalArgumentException::class.java) { apply(after, TournamentHostCommand.Finalize(series.id, series.battleKey)) }
        assertEquals(1, after.event.completedBattleCount)
        assertNull(TournamentEngine.agreement(after, after.event.activeSeries.single()))
    }

    @Test fun hostResolutionPreservesItsReasonAndResultBasis() {
        val room = started()
        val series = room.event.activeSeries.single()
        val after = apply(room, TournamentHostCommand.Resolve(series.id, series.battleKey, series.playerB, false, "Both players reviewed the result"))
        val battle = after.event.series.single().battles.single()
        assertEquals(TournamentResultBasis.HOST_RESOLVED, battle.basis)
        assertEquals("Both players reviewed the result", battle.reason)
        assertEquals(series.playerB, battle.winnerId)
        assertThrows(IllegalArgumentException::class.java) {
            apply(room, TournamentHostCommand.Resolve(series.id, series.battleKey, "outsider", false, "Invalid winner"))
        }
    }

    @Test fun startRequiresTwoAcceptedCheckedInTrainers() {
        val room = lobby()
        assertThrows(IllegalArgumentException::class.java) { apply(room.copy(attendance = emptyMap()), TournamentHostCommand.Start) }
        assertThrows(IllegalArgumentException::class.java) { apply(room.copy(attendance = mapOf("player1" to TournamentAttendance(true, true))), TournamentHostCommand.Start) }
        val after = apply(lobby(3).copy(attendance = room.attendance), TournamentHostCommand.Start)
        assertEquals(2, after.event.participantIds.size)
        assertFalse("player3" in after.event.participantIds)
    }

    @Test fun settingsAndInvitationsLockAtStartAndNonhostCannotManage() {
        val room = started()
        assertThrows(IllegalArgumentException::class.java) { apply(room, TournamentHostCommand.Settings(TournamentSettings(title = "Changed"))) }
        assertThrows(IllegalArgumentException::class.java) { apply(room, TournamentHostCommand.Invite(player(3))) }
        assertThrows(IllegalArgumentException::class.java) { TournamentEngine.apply(lobby(), "player2", TournamentHostCommand.Start, 2) }
    }

    @Test fun changingRulesRequiresFreshCheckInWithoutChangingAnyoneElseAttendance() {
        val original = lobby()
        val changed = apply(original, TournamentHostCommand.Settings(TournamentSettings(rules = "No legendary Pokémon")))
        assertEquals(1L, changed.event.settingsRevision)
        assertEquals(original.attendance, changed.attendance)
        assertThrows(IllegalArgumentException::class.java) { apply(changed, TournamentHostCommand.Start) }
        val acknowledged = changed.copy(attendance = changed.attendance.mapValues { (_, attendance) ->
            attendance.copy(settingsRevision = changed.event.settingsRevision)
        })
        assertEquals(TournamentPhase.ACTIVE, apply(acknowledged, TournamentHostCommand.Start).event.phase)
    }

    @Test fun endingEarlyNeverInventsAChampionOrPlayedBattles() {
        val room = apply(started(), TournamentHostCommand.EndEarly("Someone had to leave"))
        assertEquals(TournamentPhase.ENDED_EARLY, room.event.phase)
        assertEquals(0, room.event.completedBattleCount)
        assertNull(room.event.championId)
        assertTrue(room.event.activeBattles.isEmpty())
    }

    @Test fun cachedLobbyCannotAdvanceAndInvalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { apply(lobby().copy(fromCache = true), TournamentHostCommand.Start) }
        assertThrows(IllegalArgumentException::class.java) { apply(lobby(), TournamentHostCommand.Settings(TournamentSettings(winsPerSeries = 0))) }
        assertThrows(IllegalArgumentException::class.java) { apply(lobby(), TournamentHostCommand.Settings(TournamentSettings(title = " "))) }
    }

    @Test fun creatingInvitationDoesNotAcceptItOrAlterOriginalRoster() {
        val original = lobby()
        val changed = apply(original, TournamentHostCommand.Invite(player(3)))
        assertEquals(2, original.event.players.size)
        assertEquals(3, changed.event.players.size)
        assertNull(changed.attendance["player3"])
        assertThrows(IllegalArgumentException::class.java) { apply(changed, TournamentHostCommand.Invite(player(3))) }
    }
}
