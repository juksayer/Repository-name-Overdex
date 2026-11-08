package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayFastMoveScheduleTest {
    @Test fun `Volt Switch cannot fire three times in two seconds or queue those hits into idle time`() {
        val inputs = listOf(2.0, 2.4, 3.1, 3.9, 8.0).mapIndexed { i, at -> witness("w$i", at) }
        val cycles = ReplayFastMoveSchedule.build(inputs)
        assertEquals(listOf(2.0, 4.0, 8.0).map(::ns), cycles.map { it.impactAtNanos })
        assertTrue(cycles.all { it.durationNanos == ns(2.0) })
        assertEquals(setOf("w0", "w1", "w2"), cycles.first().witness.articleIds)
        assertTrue(cycles.all { it.actionAt(ns(5.0)) == null })
        assertEquals(ns(3.9), inputs[3].observedAtNanos) // original timing retained
    }

    @Test fun `changing move identity cannot restart occupied time in the same window`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("first", 2.0),
            witness("too-early", 2.2).copy(moveName = "Other move", durationNanos = ns(1.0)),
            witness("next", 2.9).copy(moveName = "Other move", durationNanos = ns(1.0))))
        assertEquals(listOf(ns(2.0), ns(3.0)), cycles.map { it.impactAtNanos })
        assertEquals(listOf(ns(0.0), ns(2.0)), cycles.map { it.startedAtNanos })
        assertTrue(cycles.none { "too-early" in it.witness.articleIds })
    }

    @Test fun `changed duration cannot overlap a previous full cycle`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("first", 2.0),
            witness("too-early", 2.2).copy(durationNanos = ns(.5)),
            witness("next", 2.5).copy(durationNanos = ns(.5))))
        assertEquals(listOf(ns(2.0), ns(2.5)), cycles.map { it.impactAtNanos })
        assertTrue(cycles.zipWithNext().all { (a, b) -> b.startedAtNanos >= a.impactAtNanos })
    }

    @Test fun `a known move after an unknown hit still needs one full cycle`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("unknown", 2.0).copy(durationNanos = null),
            witness("early-known", 2.1), witness("known", 4.0)))
        assertEquals(2, cycles.size)
        assertFalse(cycles.first().hasReferenceDuration)
        assertEquals(ns(2.0), cycles.last().startedAtNanos)
        assertEquals(ns(4.0), cycles.last().impactAtNanos)
    }

    @Test fun `neutral testimony cannot erase a known cycles later reconstructed impact`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("first", .1),
            witness("unknown", .2).copy(durationNanos = null),
            witness("overlapping", 2.2), witness("next", 4.0)))
        val known = cycles.filter { it.hasReferenceDuration }
        assertEquals(listOf(ns(2.0), ns(4.0)), known.map { it.impactAtNanos })
        assertEquals(1, cycles.count { !it.hasReferenceDuration })
        assertTrue(known.zipWithNext().all { (a, b) -> b.startedAtNanos >= a.impactAtNanos })
        assertTrue(cycles.none { "overlapping" in it.witness.articleIds })
        assertEquals(setOf("unknown"), cycles.single { !it.hasReferenceDuration }.witness.articleIds)
    }

    @Test fun `each side has an independent reference cadence including half second moves`() {
        val inputs = listOf(0.5, 1.0, 1.5, 2.0).mapIndexed { i, at ->
            witness("water$i", at).copy(side = "OPPONENT", moveName = "Water Gun", durationNanos = ns(0.5))
        } + listOf(witness("volt", 2.0))
        val cycles = ReplayFastMoveSchedule.build(inputs)
        assertEquals(4, cycles.count { it.witness.side == "OPPONENT" })
        assertEquals(1, cycles.count { it.witness.side == "PLAYER" })
        assertTrue(cycles.filter { it.witness.side == "OPPONENT" }.all { it.durationNanos == ns(0.5) })
    }

    @Test fun `cycles never cross a charge switch faint or match boundary`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("a", 2.0).copy(windowEndNanos = ns(3.0)),
            witness("interrupted", 2.9).copy(windowStartNanos = ns(2.5), windowEndNanos = ns(3.0)),
            witness("after", 6.0).copy(windowStartNanos = ns(5.0), windowEndNanos = ns(10.0)),
        ))
        assertEquals(2, cycles.size)
        assertTrue(cycles.all { it.startedAtNanos >= it.witness.windowStartNanos && it.impactAtNanos <= it.witness.windowEndNanos })
        assertNull(cycles.first().actionAt(ns(3.0)))
        assertEquals(ns(7.0), cycles.last().impactAtNanos)
    }

    @Test fun `terminal boundary preserves witnessed hit before jitter corrected impact`() {
        val inputs = listOf(witness("first", 2.0), witness("final", 3.9)).map {
            it.copy(windowEndNanos = ns(3.95), endsAtTerminalBoundary = true)
        }
        val cycles = ReplayFastMoveSchedule.build(inputs)
        assertEquals(listOf(ns(2.0), ns(3.9)), cycles.map { it.impactAtNanos })
        assertEquals(ns(2.0), cycles.last().startedAtNanos)
        assertEquals(setOf("final"), cycles.last().witness.articleIds)
        assertEquals(setOf(ns(3.9)), cycles.last().witness.sourceTimesNanos)
        assertNotNull(cycles.last().actionAt(ns(3.8)))
        assertNull(cycles.last().actionAt(ns(3.95)))
    }

    @Test fun `terminal fallback cannot fit an otherwise incomplete or impossible cycle`() {
        val shortWindow = witness("too-short", .2).copy(
            windowEndNanos = ns(.3), endsAtTerminalBoundary = true)
        assertTrue(ReplayFastMoveSchedule.build(listOf(shortWindow)).isEmpty())
        val ordinaryBoundary = listOf(witness("first", 2.0), witness("later", 3.9))
            .map { it.copy(windowEndNanos = ns(3.95)) }
        assertEquals(1, ReplayFastMoveSchedule.build(ordinaryBoundary).size)
    }

    @Test fun `finishing fast move tied to recipient faint uses outgoing window and reverse scrubs`() {
        val archive = battle(listOf(
            hit("final", "PLAYER", 2.0),
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 2.0),
            event("end", ArchivedMatchEnded("WIN"), 2.1),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val before = model.sceneAt(ns(1.5))
        val final = before.fastMoveActions.single { it.side == "PLAYER" }
        assertEquals(setOf("final"), final.sourceArticleIds)
        assertEquals(ns(0.0), final.startedAtNanos)
        assertEquals(ns(2.0), final.impactAtNanos)
        assertFalse(before.opponent!!.isFainted)
        assertTrue(model.sceneAt(ns(2.05)).opponent!!.isFainted)
        assertTrue(model.sceneAt(ns(2.05)).fastMoveActions.isEmpty())
        assertEquals(before, model.sceneAt(ns(1.5)))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `recorded final fast move stays before KO without inventing energy or a concession hit`() {
        val model = model(battle(listOf(
            hit("first", "PLAYER", 2.0), hit("final", "PLAYER", 3.9),
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 3.95),
            event("end", ArchivedMatchEnded("WIN"), 4.0),
        )))
        val final = model.sceneAt(ns(3.8)).fastMoveActions.single { it.side == "PLAYER" }
        assertEquals(setOf("final"), final.sourceArticleIds)
        assertEquals(ns(3.9), final.impactAtNanos)
        assertTrue(model.sceneAt(ns(4.0)).fastMoveActions.isEmpty())
        assertEquals(0, model.sceneAt(ns(4.0)).playerGeneratedEnergy)
        assertEquals(0, model.sceneAt(ns(4.0)).playerSpentEnergy)
        val conceded = model(battle(listOf(event("end", ArchivedMatchEnded("WIN"), 3.95))))
        assertTrue((0..50).all { conceded.sceneAt(ns(it / 10.0)).fastMoveActions.isEmpty() })
    }

    @Test fun `terminal fallback cannot attack an already fainted recipient`() {
        for (repeatedFaint in listOf(false, true)) {
            val articles = listOfNotNull(
                event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 2.0),
                event("repeated-ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 3.7)
                    .takeIf { repeatedFaint },
                hit("after-ko", "PLAYER", 3.7),
                event("end", ArchivedMatchEnded("WIN"), 3.8),
            )
            val model = model(battle(articles))
            assertTrue("repeated faint=$repeatedFaint", (0..45).all { step ->
                model.sceneAt(ns(step / 10.0)).fastMoveActions.none { "after-ko" in it.sourceArticleIds }
            })
        }
    }

    @Test fun `terminal fallback can hit an active replacement after earlier recipient faint`() {
        val model = model(battle(listOf(
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 2.0),
            event("replacement", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 2.1),
            hit("replacement-hit", "PLAYER", 3.8),
            event("end", ArchivedMatchEnded("WIN"), 3.9),
        )))
        val scene = model.sceneAt(ns(3.7))
        assertEquals("Vaporeon", scene.opponent!!.speciesName)
        assertFalse(scene.opponent.isFainted)
        assertEquals(setOf("replacement-hit"), scene.fastMoveActions.single { it.side == "PLAYER" }.sourceArticleIds)
    }

    @Test fun `unknown moves do not acquire an invented duration or lose separate half second hits`() {
        val cycles = ReplayFastMoveSchedule.build(listOf(
            witness("a", 1.0).copy(durationNanos = null),
            witness("b", 1.42).copy(durationNanos = null),
        ))
        assertEquals(2, cycles.size)
        assertTrue(cycles.all { it.actionAt(ns(1.44)) != null && !it.hasReferenceDuration })
    }

    @Test fun `unknown terminal hit keeps a neutral presentation before its exact impact`() {
        for (end in listOf(2.0, 2.1)) {
            val source = witness("terminal", 2.0).copy(moveName = null, type = null, durationNanos = null,
                windowEndNanos = ns(end), endsAtTerminalBoundary = true)
            val cycle = ReplayFastMoveSchedule.build(listOf(source)).single()
            assertEquals(ns(1.55), cycle.startedAtNanos)
            assertEquals(ns(2.0), cycle.impactAtNanos)
            assertFalse(cycle.hasReferenceDuration)
            assertNull(cycle.witness.durationNanos)
            val action = cycle.actionAt(ns(1.8))!!
            assertNull(action.moveName)
            assertNull(action.type)
            assertEquals(setOf("terminal"), action.sourceArticleIds)
            assertEquals(setOf(ns(2.0)), action.observedAtNanos)
            assertTrue("TERMINAL_NEUTRAL_PRESENTATION_FIT" in action.evidenceKinds)
            assertFalse(action.timingReconstructed) // no reference duration is known
            assertNull(cycle.actionAt(ns(end)))
            assertEquals(setOf("HP_DAMAGE_TICK"), source.evidenceKinds)
        }
    }

    @Test fun `neutral terminal fit cannot cross a zero or too short ordinary play window`() {
        for (start in listOf(2.0, 1.99, 1.6)) {
            val source = witness("too-short", 2.0).copy(durationNanos = null,
                windowStartNanos = ns(start), windowEndNanos = ns(2.0), endsAtTerminalBoundary = true)
            assertTrue(ReplayFastMoveSchedule.build(listOf(source)).isEmpty())
        }
        val ordinaryBoundary = witness("ordinary", 2.0).copy(durationNanos = null, windowEndNanos = ns(2.0))
        val ordinary = ReplayFastMoveSchedule.build(listOf(ordinaryBoundary)).single()
        assertEquals(ns(2.0), ordinary.startedAtNanos)
        assertFalse("TERMINAL_NEUTRAL_PRESENTATION_FIT" in ordinary.witness.evidenceKinds)
    }

    @Test fun `supported fast hit at result uses closing window without manufacturing faint or energy`() {
        for (known in listOf(false, true)) {
            val archive = battle(listOf(hit("final-result", "PLAYER", 2.0),
                hit("post-result", "PLAYER", 2.1), event("end", ArchivedMatchEnded("WIN"), 2.0)))
                .let { if (known) it else it.copy(articles = it.articles.filterNot {
                    it.payload is ArchivedFastMoveIdentified
                }) }
            val original = MatchArchiveSerializer.serialize(archive)
            val model = if (known) model(archive) else MatchReplayModel(archive)
            val before = model.sceneAt(ns(1.8))
            val action = before.fastMoveActions.single()
            assertEquals(setOf("final-result"), action.sourceArticleIds)
            assertEquals(setOf(ns(2.0)), action.observedAtNanos)
            assertEquals(ns(2.0), action.impactAtNanos)
            assertTrue(action.startedAtNanos < ns(2.0))
            assertNull(before.outcome)
            val ended = model.sceneAt(ns(2.1))
            assertEquals("WIN", ended.outcome)
            assertFalse(ended.opponent!!.isFainted)
            assertTrue(ended.fastMoveActions.isEmpty())
            assertEquals(0, ended.playerGeneratedEnergy)
            assertEquals(0, ended.playerSpentEnergy)
            assertEquals(before, model.sceneAt(ns(1.8)))
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `unknown finishing hit at recipient faint remains visible before the fall`() {
        val archive = battle(listOf(hit("final-neutral", "PLAYER", 2.0),
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "BALLS"), 2.0),
            event("end", ArchivedMatchEnded("WIN"), 2.1)))
            .let { it.copy(articles = it.articles.filterNot { article ->
                article.payload is ArchivedFastMoveIdentified
            }) }
        val model = MatchReplayModel(archive)
        val before = model.sceneAt(ns(1.8))
        val action = before.fastMoveActions.single()
        assertNull(action.moveName)
        assertNull(action.type)
        assertEquals(ns(2.0), action.impactAtNanos)
        assertTrue("TERMINAL_NEUTRAL_PRESENTATION_FIT" in action.evidenceKinds)
        assertFalse(before.opponent!!.isFainted)
        assertTrue(model.sceneAt(ns(2.05)).opponent!!.isFainted)
        assertTrue(model.sceneAt(ns(2.05)).fastMoveActions.isEmpty())
    }

    @Test fun `unknown terminal fitting cannot borrow time across charge entry or match start`() {
        val extraBoundaries = listOf(
            emptyList(),
            listOf(event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1.8)),
            listOf(event("charge", ArchivedChargeMoveUsedAnnounced, 1.8)),
            listOf(event("ready", ArchivedGetReadyWitnessed, 1.8)),
        )
        for ((index, boundary) in extraBoundaries.withIndex()) {
            val start = if (index == 0) 1.8 else 0.0
            val archive = MatchArchive(matchId = "match", articles = listOf(
                event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Galvantula", 596), 0.0),
                event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 0.0),
                event("go", ArchivedMatchStarted, start),
            ) + boundary + listOf(hit("terminal", "PLAYER", 2.0), event("end", ArchivedMatchEnded("WIN"), 2.0)))
            val model = MatchReplayModel(archive)
            assertTrue("boundary=$index", (0..25).all { step ->
                model.sceneAt(ns(step / 10.0)).fastMoveActions.isEmpty()
            })
        }
    }

    @Test fun `separate settled HP losses survive late confirmation at the reference cadence`() {
        val inputs = listOf(
            witness("first", 3.092).copy(durationNanos = ns(2.5),
                damageTicks = listOf(ReplayFastMoveDamage(ns(3.092), .99f, .735f))),
            witness("second", 4.723).copy(durationNanos = ns(2.5),
                damageTicks = listOf(ReplayFastMoveDamage(ns(4.723), .735f, .473f))),
            witness("third", 8.081).copy(durationNanos = ns(2.5),
                damageTicks = listOf(ReplayFastMoveDamage(ns(8.081), .473f, .222f))),
        )
        val cycles = ReplayFastMoveSchedule.build(inputs)
        assertEquals(listOf(3.092, 5.592, 8.092).map(::ns), cycles.map { it.impactAtNanos })
        assertEquals(listOf(setOf("first"), setOf("second"), setOf("third")), cycles.map { it.witness.articleIds })
        assertEquals(ns(4.723), cycles[1].witness.observedAtNanos)
    }

    @Test fun `one draining hit and a repeated damage report cannot manufacture extra moves`() {
        val inputs = listOf(
            witness("first", 2.0).copy(damageTicks = listOf(ReplayFastMoveDamage(ns(2.0), 1f, .9f))),
            witness("tail", 2.2).copy(damageTicks = listOf(ReplayFastMoveDamage(ns(2.2), .9f, .8f))),
            witness("repeat", 3.1).copy(damageTicks = listOf(ReplayFastMoveDamage(ns(3.1), .9f, .8f))),
        )
        val cycles = ReplayFastMoveSchedule.build(inputs)
        assertEquals(1, cycles.size)
        assertEquals(setOf("first", "tail", "repeat"), cycles.single().witness.articleIds)
    }

    @Test fun `measured damage lands on recipient with projectile and restores on reverse scrub`() {
        val archive = battle(listOf(
            hp("full", "OPPONENT", 1f, 0.0),
            hit("first", "PLAYER", 1.8),
            hp("draining", "OPPONENT", .9f, 1.8),
            hp("settled", "OPPONENT", .8f, 1.95),
            event("tick", ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .995f, .8f, .195f), 1.95)
                .copy(predecessorIds = listOf("first")),
            hit("duplicate", "PLAYER", 2.25),
            hp("noise", "OPPONENT", .82f, 2.3),
            hit("next", "PLAYER", 3.9),
            hp("second-damage", "OPPONENT", .6f, 3.9),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val before = model.sceneAt(ns(1.999))
        assertEquals(1f, before.opponentHp!!.filledFraction!!, .0001f)
        val impact = model.sceneAt(ns(2.0))
        assertEquals(.8f, impact.opponentHp!!.filledFraction!!, .0001f)
        assertEquals(1f, impact.opponentHp.damageTrailFraction!!, .0001f)
        assertTrue(impact.opponentHp.borderPulse)
        assertTrue(impact.opponentHp.damageTimingReconstructed)
        val projectile = impact.fastMoveActions.single { it.side == "PLAYER" }
        assertEquals(1f, projectile.progress, .0001f)
        assertEquals(ns(2.0), projectile.durationNanos)
        assertEquals(setOf("first", "duplicate", "tick"), projectile.sourceArticleIds)
        assertEquals(1f, impact.playerHp!!.filledFraction!!, .0001f)
        assertEquals(.8f, model.sceneAt(ns(3.999)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.6f, model.sceneAt(ns(4.0)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(before, model.sceneAt(ns(1.999)))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `opponent attack lowers player bar and a pulse alone cannot invent damage`() {
        val archive = battle(listOf(
            event("water", ArchivedFastMoveIdentified("OPPONENT", "Sealeo", "Water Gun", ns(.5),
                cadenceSampleCount = 3, basis = "CADENCE"), 0.0),
            hit("opponent-hit", "OPPONENT", .48), hp("damage", "PLAYER", .9f, .48),
            hit("player-pulse", "PLAYER", 2.0),
        ))
        val model = model(archive)
        assertEquals(1f, model.sceneAt(ns(.499)).playerHp!!.filledFraction!!, .0001f)
        assertEquals(.9f, model.sceneAt(ns(.5)).playerHp!!.filledFraction!!, .0001f)
        assertEquals(1f, model.sceneAt(ns(2.0)).opponentHp!!.filledFraction!!, .0001f)
        assertTrue(model.sceneAt(ns(2.0)).opponentHp!!.assumedInitialFull)
    }

    @Test fun `HP damage during charge is not spread onto later fast moves`() {
        val model = model(battle(listOf(
            hp("full", "OPPONENT", 1f, 0.0), hit("first", "PLAYER", 2.0),
            hp("first-damage", "OPPONENT", .9f, 2.0),
            event("ready", ArchivedGetReadyWitnessed, 3.0),
            event("charge", ArchivedChargeMoveUsedAnnounced, 7.0),
            hp("charge-damage", "OPPONENT", .4f, 7.1),
            hit("later", "PLAYER", 10.0), hp("later-damage", "OPPONENT", .3f, 10.0),
        )))
        assertEquals(.4f, model.sceneAt(ns(7.1)).opponentHp!!.filledFraction!!, .0001f)
        assertTrue(model.sceneAt(ns(7.1)).fastMoveActions.isEmpty())
        assertEquals(.4f, model.sceneAt(ns(9.9)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.3f, model.sceneAt(ns(10.0)).opponentHp!!.filledFraction!!, .0001f)
    }

    @Test fun `replacement starts full and never inherits the previous recipients delayed damage`() {
        val model = model(battle(listOf(
            hit("first", "PLAYER", 1.9), hp("damage", "OPPONENT", .7f, 1.9),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 2.1),
            hp("new-full", "OPPONENT", 1f, 2.1),
            hit("next", "PLAYER", 4.2), hp("new-damage", "OPPONENT", .85f, 4.2),
        )))
        assertEquals(.7f, model.sceneAt(ns(2.0)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(1f, model.sceneAt(ns(2.1)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.85f, model.sceneAt(ns(4.2)).opponentHp!!.filledFraction!!, .0001f)
    }

    @Test fun `recorded Volt Switch timestamps obey reference duration after return from fainted Camerupt`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(
            javaClass.getResource("/replay/camerupt-galvantula-return.json")).readText())
        val model = MatchReplayModel(archive)
        val seen = mutableMapOf<Long, ReplayFastMoveAction>()
        for (step in 1380..1590) model.sceneAt(ns(step / 10.0)).fastMoveActions
            .filter { it.side == "PLAYER" }.forEach { seen[it.startedAtNanos] = it }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.values.all { it.moveName == "Volt Switch" && it.durationNanos == ns(2.0) })
        assertTrue(seen.values.sortedBy { it.impactAtNanos }.zipWithNext().all { (a, b) ->
            b.impactAtNanos - a.impactAtNanos >= ns(2.0)
        })
        assertTrue((1470..1500).all { model.sceneAt(ns(it / 10.0)).fastMoveActions.none { a -> a.side == "PLAYER" } })
    }

    @Test fun `confirmed opening Volt Switch is not reassigned to a later Incinerate user`() {
        val archive = battle(listOf(
            event("team1", ArchivedPlayerTeamSlotConfigured(1, "Galvantula", 596, "Volt Switch", listOf("Discharge", "Lunge")), -4.0),
            event("team2", ArchivedPlayerTeamSlotConfigured(2, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb", "Shadow Ball")), -4.0),
            event("nickname", ArchivedRawText("gbl"), .04).copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE_OCR_RAW"),
            hit("hit", "PLAYER", 1.675), hp("damage", "OPPONENT", .92f, 1.675),
            event("label1", ArchivedPlayerChargeMoveLabelRead(1, "DISCHARGE"), 1.867),
            event("label2", ArchivedPlayerChargeMoveLabelRead(2, "LUNGE"), 1.867),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711), 9.649)
                .copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE"),
        ))
        val model = model(archive)
        val scene = model.sceneAt(ns(2.0))
        assertEquals("Galvantula", scene.player!!.speciesName)
        assertEquals("Volt Switch", scene.fastMoveActions.single { it.side == "PLAYER" }.moveName)
        assertEquals(ns(2.0), scene.fastMoveActions.single { it.side == "PLAYER" }.durationNanos)
        assertEquals("Gourgeist", model.sceneAt(ns(10.0)).player!!.speciesName)
    }

    private fun model(archive: MatchArchive) = MatchReplayModel(archive,
        fastMovesBySpeciesName = mapOf("Galvantula" to listOf(Move("Volt Switch", PokemonType.ELECTRIC, 12, 16, true, 4))))
    private fun battle(extra: List<ArchivedRealityArticle>) = MatchArchive(matchId = "match", articles = listOf(
        event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Galvantula", 596), 0.0),
        event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 0.0),
        event("go", ArchivedMatchStarted, 0.0),
        event("identified", ArchivedFastMoveIdentified("PLAYER", "Galvantula", "Volt Switch", ns(2.0),
            cadenceSampleCount = 0, basis = "CONFIGURED_TEAM"), 0.0),
    ) + extra)
    private fun hit(id: String, side: String, time: Double) = event(id, ArchivedFastMoveUseObserved(
        id, side, if (side == "PLAYER") "OPPONENT" else "PLAYER", "appearance", null,
        listOf("HP_BORDER_PULSE"), "FUSED"), time)
    private fun hp(id: String, side: String, fraction: Float, time: Double) =
        event(id, ArchivedActiveHpBarMeasured(side, 10, 20, 210, 35, fraction), time)
    private fun event(id: String, payload: ArchivedTestimonyPayload, time: Double) = ArchivedRealityArticle(
        id, "match", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), ns(time))
    private fun witness(id: String, time: Double) = ReplayFastMoveWitness("PLAYER", ns(time), "Volt Switch",
        PokemonType.ELECTRIC, ns(2.0), 0L, Long.MAX_VALUE, setOf("HP_DAMAGE_TICK"), setOf(id), setOf(ns(time)))
    private fun ns(seconds: Double) = (seconds * 1_000_000_000L).toLong()
}
