package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MatchTeamSelectLeadTest {
    @Test fun `accepted Rocket party supplies observed Golbat lead when every start glyph is missed`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.configure()
            val party = fixture.publish(TeamSelectPartyWitnessed, 72)
            val leadSlot = fixture.publish(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), 73)
            fixture.publish(PlayerTeamRosterSlotWitnessed(2, "Greninja", 658), 73)
            fixture.publish(PlayerTeamRosterSlotWitnessed(3, "Victreebel", 71), 73)
            val opponent = fixture.opponent(86)
            assertTrue(fixture.leads().isEmpty())

            val damage = fixture.damage(88)
            val lead = fixture.leads().single()
            val species = lead.payload as ActivePokemonSpeciesWitnessed
            assertEquals("Golbat", species.speciesName)
            assertEquals(1, species.playerTeamSlot)
            assertEquals(damage.monotonicTimeNanos, species.appearanceStartedAtNanos)
            assertEquals(damage.monotonicTimeNanos, lead.monotonicTimeNanos)
            assertEquals(damage.perceivedAt, lead.perceivedAt)
            assertEquals(setOf(party.id, leadSlot.id, opponent.id, damage.id), lead.predecessorIds.toSet())

            val openingIdentity = fixture.identities().single()
            assertEquals("Golbat", openingIdentity.speciesName)
            assertEquals(1, openingIdentity.teamSlot)
            assertEquals(fixture.at(88), openingIdentity.appearanceStartedAtNanos)
            val wingAttack = fixture.articles().single {
                (it.payload as? FastMoveIdentified)?.let { move ->
                    move.side == ActivePokemonSide.PLAYER && move.speciesName == "Golbat" &&
                        move.moveName == "Wing Attack"
                } == true
            }
            assertTrue(fixture.articles().indexOf(lead) < fixture.articles().indexOf(wingAttack))

            val incoming = fixture.publish(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER,
                "Greninja", 658), 176, "PLAYER_SPECIES_OVERLAY_PIPELINE")
            val identities = fixture.identities()
            assertEquals(listOf("Golbat", "Greninja"), identities.map { it.speciesName })
            assertEquals(listOf(1, 2), identities.map { it.teamSlot })
            assertEquals(incoming.monotonicTimeNanos, identities.last().appearanceStartedAtNanos)
            val greninjaMoves = fixture.articles().filter {
                (it.payload as? FastMoveIdentified)?.speciesName == "Greninja"
            }
            assertEquals(listOf("Water Shuriken"), greninjaMoves.map { (it.payload as FastMoveIdentified).moveName })
            assertTrue(greninjaMoves.all { it.monotonicTimeNanos == incoming.monotonicTimeNanos })
            assertTrue(fixture.articles().none { it.payload is MatchStarted })
            assertEquals(1, fixture.leads().size)
            assertEquals(damage.payload, fixture.articles().single { it.id == damage.id }.payload)
        } finally { fixture.close() }
    }

    @Test fun `saved team or roster without accepted party cannot invent a missed opening appearance`() = runBlocking {
        for (observedRoster in listOf(false, true)) {
            val fixture = Fixture()
            try {
                fixture.configure()
                if (observedRoster) fixture.publish(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), 2)
                fixture.opponent(3)
                fixture.damage(4)
                assertTrue("observedRoster=$observedRoster", fixture.leads().isEmpty())
                assertTrue(fixture.identities().isEmpty())
                assertTrue(fixture.articles().none { it.payload is MatchStarted })
            } finally { fixture.close() }
        }
    }

    @Test fun `names full HP cries VS and an inconsistent damage quantity do not establish the lead`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.acceptParty()
            fixture.opponent(3)
            fixture.publish(ActiveHpBarMeasured(ActivePokemonSide.PLAYER, 20, 400, 240, 420, 1f), 4)
            fixture.publish(ActiveHpBarMeasured(ActivePokemonSide.OPPONENT, 400, 300, 620, 320, 1f), 4)
            fixture.publish(BattleCryCandidatesMeasured("COUNTDOWN_GO", listOf(
                BattleCryCandidateMeasurement(42, "golbat-reference", .99f))), 5)
            fixture.publish(VsScreenWitnessed, 6)
            fixture.publish(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .95f, .20f), 7)
            assertTrue(fixture.leads().isEmpty())
            assertTrue(fixture.identities().isEmpty())
            assertTrue(fixture.articles().none { it.payload is MatchStarted })
        } finally { fixture.close() }
    }

    @Test fun `valid damage without an opponent species does not establish a missed lead`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.acceptParty()
            fixture.damage(4)
            assertTrue(fixture.leads().isEmpty())
            assertTrue(fixture.identities().isEmpty())
        } finally { fixture.close() }
    }

    @Test fun `a player faint or lost team ball before first damage prevents later lead resurrection`() = runBlocking {
        for (boundary in listOf<TestimonyPayload>(
            ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Golbat", 42),
            PlayerPokeBallCountMeasured(2),
        )) {
            val fixture = Fixture()
            try {
                fixture.acceptParty()
                fixture.opponent(3)
                fixture.publish(PlayerPokeBallCountMeasured(3), 3)
                fixture.publish(boundary, 4)
                fixture.damage(5)
                fixture.go(6)
                assertTrue(boundary.toString(), fixture.leads().isEmpty())
                assertTrue(fixture.identities().isEmpty())
            } finally { fixture.close() }
        }
    }

    @Test fun `a known switch cannot be replaced by combat fallback or delayed GO`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.acceptParty()
            fixture.opponent(3)
            fixture.publish(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Greninja", 658),
                4, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS")
            fixture.damage(5)
            fixture.go(6)
            assertTrue(fixture.leads().isEmpty())
            assertEquals(listOf("Greninja"), fixture.identities().map { it.speciesName })
            assertEquals(2, fixture.identities().single().teamSlot)
            assertEquals(fixture.at(4), fixture.identities().single().appearanceStartedAtNanos)
        } finally { fixture.close() }
    }

    @Test fun `restarted observation rejects old party and late old roster even when combat is fresh`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.configure()
            fixture.publish(TeamSelectPartyWitnessed, 0)
            fixture.publish(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), 0)
            fixture.match.restartPreBattleObservationAttempt()
            val restartAt = System.nanoTime()
            fixture.publishAt(ObservationRestartedByUser, restartAt)
            val lateParty = fixture.publishAt(TeamSelectPartyWitnessed, fixture.baseNanos)
            val lateRoster = fixture.publishAt(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), fixture.baseNanos)
            fixture.publishAt(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Fomantis", 753),
                restartAt + SECOND, "OPPONENT_SPECIES_OVERLAY_PIPELINE")
            fixture.publishAt(damage(), restartAt + 2 * SECOND)
            assertTrue(fixture.leads().isEmpty())
            assertTrue(fixture.identities().isEmpty())
            assertTrue(fixture.articles().any { it.id == lateParty.id })
            assertTrue(fixture.articles().any { it.id == lateRoster.id })
        } finally { fixture.close() }
    }

    @Test fun `repeated damage and eventual GO preserve exactly one Golbat lead appearance`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.acceptParty()
            fixture.opponent(3)
            fixture.damage(4)
            fixture.damage(5)
            fixture.go(6)
            fixture.damage(7)
            assertEquals(1, fixture.leads().size)
            assertEquals(listOf("Golbat"), fixture.identities().map { it.speciesName })
            assertEquals(fixture.at(4), fixture.identities().single().appearanceStartedAtNanos)
            assertEquals(1, fixture.articles().count { it.payload is MatchStarted })
        } finally { fixture.close() }
    }

    @Test fun `GO still seeds an observed first slot without a separate party article`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.configure()
            val roster = fixture.publish(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), 2)
            fixture.go(4)
            val start = fixture.articles().single { it.payload is MatchStarted }
            val lead = fixture.leads().single()
            assertEquals(setOf(roster.id, start.id), lead.predecessorIds.toSet())
            assertEquals(start.monotonicTimeNanos, lead.monotonicTimeNanos)
            assertEquals("Golbat", fixture.identities().single().speciesName)
        } finally { fixture.close() }
    }

    @Test fun `a late older slot one reading cannot undo a newer reordered lead in the same accepted party`() = runBlocking {
        for (olderDeliveredLast in listOf(false, true)) {
            val fixture = Fixture()
            try {
                fixture.configure()
                fixture.publish(TeamSelectPartyWitnessed, 1)
                val readings = listOf(
                    PlayerTeamRosterSlotWitnessed(1, "Golbat", 42) to 2L,
                    PlayerTeamRosterSlotWitnessed(1, "Greninja", 658) to 4L,
                )
                (if (olderDeliveredLast) readings.reversed() else readings).forEach { (reading, time) ->
                    fixture.publish(reading, time)
                }
                fixture.opponent(6)
                fixture.damage(8)
                assertTrue("olderDeliveredLast=$olderDeliveredLast", fixture.leads().none {
                    (it.payload as ActivePokemonSpeciesWitnessed).speciesName == "Golbat"
                })
                assertTrue(fixture.identities().none { it.speciesName == "Golbat" })
                assertTrue(fixture.articles().none { it.payload is MatchStarted })
            } finally { fixture.close() }
        }
    }

    @Test fun `delayed GO or valid damage cannot reopen lead presentation after stop ignore or result`() = runBlocking {
        val endings = listOf<TestimonyPayload>(ObservationSessionStoppedByUser, ScreenIgnoredByUser,
            MatchEnded(com.example.overdex.model.BattleResult.WIN))
        for (ending in endings) for (delayedGo in listOf(false, true)) {
            val fixture = Fixture()
            try {
                fixture.acceptParty()
                fixture.opponent(10)
                fixture.publish(ending, 30)
                val signals = mutableListOf<DroidballSignal>()
                // Subscribe after the stop/result so the opponent's earlier
                // ordinary HUD signal cannot contaminate this delayed-delivery check.
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    DroidballService.signals.collect { signals += it }
                }
                try {
                    if (delayedGo) fixture.go(20) else fixture.damage(20)
                    yield()
                    val context = "ending=$ending, delayedGo=$delayedGo"
                    assertTrue(context, fixture.leads().isEmpty())
                    assertTrue(context, fixture.identities().isEmpty())
                    assertTrue(context, signals.none { it == DroidballSignal.BattleHudWitnessed })
                } finally { collector.cancel() }
            } finally { fixture.close() }
        }
    }

    private class Fixture : AutoCloseable {
        private val custody = InMemoryTestimonyCustody()
        private val timeline = InMemoryRealityTimeline()
        val match = Match("team-select-lead", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = PartyKnowledge())
        val baseNanos = System.nanoTime()

        fun at(seconds: Long) = baseNanos + seconds * SECOND
        fun articles() = timeline.getArticles()
        fun leads() = articles().filter { it.sourceId.id == "TEAM_SELECT_LEAD_INFERENCE" }
        fun identities() = articles().mapNotNull { it.payload as? PlayerActiveIdentityResolved }

        suspend fun publish(payload: TestimonyPayload, seconds: Long, source: String = "TEAM_SELECT_TEST") =
            publishAt(payload, at(seconds), source)

        suspend fun publishAt(payload: TestimonyPayload, atNanos: Long, source: String = "TEAM_SELECT_TEST"): RealityArticle {
            val record = custody.submitTestimony(SourceId(source), payload,
                1_000_000L + (atNanos - baseNanos) / 1_000_000L, 1f, emptyList(), atNanos)
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            return articles().single { it.sequenceNumber == record.sequenceNumber }
        }

        suspend fun configure() {
            publish(PlayerTeamSlotConfigured(1, "Golbat", 42, "Wing Attack", listOf("Poison Fang")), 0, "USER_TEAM")
            publish(PlayerTeamSlotConfigured(2, "Greninja", 658, "Water Shuriken", listOf("Hydro Cannon")), 0, "USER_TEAM")
            publish(PlayerTeamSlotConfigured(3, "Victreebel", 71, "Razor Leaf", listOf("Leaf Blade")), 0, "USER_TEAM")
        }

        suspend fun acceptParty() {
            configure()
            publish(TeamSelectPartyWitnessed, 1)
            publish(PlayerTeamRosterSlotWitnessed(1, "Golbat", 42), 2)
            publish(PlayerTeamRosterSlotWitnessed(2, "Greninja", 658), 2)
            publish(PlayerTeamRosterSlotWitnessed(3, "Victreebel", 71), 2)
        }

        suspend fun opponent(seconds: Long) = publish(ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Fomantis", 753), seconds, "OPPONENT_SPECIES_OVERLAY_PIPELINE")
        suspend fun damage(seconds: Long) = publish(damage(), seconds, "OPPONENT_ACTIVE_HP_DAMAGE_WITNESS")
        suspend fun go(seconds: Long) = publish(CountdownGlyphWitnessed("GO", .99f, 1), seconds,
            BattleWitnessContracts.countdownGlyph.witnessId)

        override fun close() = match.release()
    }

    private class PartyKnowledge : FakePokemonKnowledge() {
        private val pokemon = listOf(
            Pokemon(42, "Golbat", listOf(PokemonType.POISON, PokemonType.FLYING), "Kanto",
                fastMoves = listOf(fast("Wing Attack", PokemonType.FLYING, 2, 8),
                    fast("Bite", PokemonType.DARK, 1, 2)),
                chargedMoves = listOf(charge("Poison Fang", PokemonType.POISON))),
            Pokemon(658, "Greninja", listOf(PokemonType.WATER, PokemonType.DARK), "Kalos",
                fastMoves = listOf(fast("Water Shuriken", PokemonType.WATER, 3, 14),
                    fast("Feint Attack", PokemonType.DARK, 2, 6)),
                chargedMoves = listOf(charge("Hydro Cannon", PokemonType.WATER))),
            Pokemon(71, "Victreebel", listOf(PokemonType.GRASS, PokemonType.POISON), "Kanto",
                fastMoves = listOf(fast("Razor Leaf", PokemonType.GRASS, 2, 4)),
                chargedMoves = listOf(charge("Leaf Blade", PokemonType.GRASS))),
            Pokemon(753, "Fomantis", listOf(PokemonType.GRASS), "Alola",
                fastMoves = listOf(fast("Fury Cutter", PokemonType.BUG, 1, 4)), chargedMoves = emptyList()),
        )
        override suspend fun getPokemonByName(name: String) = pokemon.firstOrNull { it.name.equals(name, true) }
        override suspend fun getPokemonById(id: Int) = pokemon.firstOrNull { it.id == id }
        override suspend fun getAllSpeciesNames() = pokemon.map { it.name }.toSet()
    }

    private companion object {
        const val SECOND = 1_000_000_000L
        fun damage() = ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .95f, .05f)
        fun fast(name: String, type: PokemonType, turns: Int, energy: Int) = Move(name, type, 5, energy, true, turns)
        fun charge(name: String, type: PokemonType) = Move(name, type, 40, 40, false)
    }
}
