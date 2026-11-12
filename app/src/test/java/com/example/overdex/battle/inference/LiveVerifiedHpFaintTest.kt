package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.FastMoveListeningGate
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.forecast.OpponentEnergyForecast
import com.example.overdex.battle.history.BattleRecordProjector
import com.example.overdex.battle.observation.*
import com.example.overdex.battle.reality.*
import com.example.overdex.battle.replay.*
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveVerifiedHpFaintTest {
    private val side = ActivePokemonSide.OPPONENT
    private val match = MatchId("live-verified-hp-test")
    private val second = 1_000_000_000L
    private val redAt = 59_005_411_111L
    private val emptyAt = 60_504_888_889L

    private fun article(id: String, at: Long, p: TestimonyPayload, source: String = "TEST") =
        RealityArticle(ArticleId(id), at / 1_000_000, at / 1_000_000 + 2000,
            SourceId(source), p, confidence = .95f, matchId = match, monotonicTimeNanos = at)

    private fun badge(id: String = "badge", at: Long = 57 * second, name: String = "Tinkaton",
        target: ActivePokemonSide = side) = article(id, at, ActivePokemonSpeciesWitnessed(target,
            name, if (name == "Tinkaton") 959 else 703), "${target.name}_SPECIES_OVERLAY_PIPELINE")

    /** Actual retained Tinkaton video pixels, remeasured now; not new claims about the old archive. */
    private fun pixels(name: String, target: ActivePokemonSide = side): RealityArticle {
        val crop = ImageIO.read(requireNotNull(javaClass.getResource(
            "/battle/hp/2026-11-11-tinkaton-terminal/$name.png")))
        val sample = requireNotNull(TrackedTerminalHpBarMeasurer.measure(672, 1493,
            ActiveHpBarMeasurement(394, 677, 587, 694, .15343915f, .95f)) { x, y ->
            crop.getRGB(x - 380, y - 650)
        })
        val b = sample.measurement
        return article(name, if (name == "red") redAt else emptyAt, ActiveHpBarMeasured(target,
            b.left, b.top, b.right, b.bottom, b.filledFraction, 672, 1493, sample.fillColor.name),
            "${target.name}_ACTIVE_HP_BAR_WITNESS").copy(confidence = b.confidence)
    }

    private fun evidence(target: ActivePokemonSide = side) = listOf(
        article("go", second, MatchStarted), badge(target = target), pixels("red", target), pixels("empty", target))
    private fun positive(at: Long = 63 * second, id: String = "alive", target: ActivePokemonSide = side) =
        pixels("red", target).copy(id = ArticleId(id), monotonicTimeNanos = at)
    private fun faint(target: ActivePokemonSide = side) = article("published-faint", emptyAt,
        ActivePokemonFaintedWitnessed(target, "Tinkaton", 959, VerifiedHpFaintResolver.BASIS))

    @Test fun `real empty outline publishes at capture time for either side and converges after delayed identity`() {
        for (target in ActivePokemonSide.entries) {
            val input = evidence(target)
            for (delivered in listOf(input, input.reversed(), listOf(input[0], input[2], input[3], input[1]))) {
                val tracker = TeamFaintTracker(target)
                val decisions = delivered.flatMap(tracker::acceptAll)
                assertEquals(1, decisions.size)
                val decision = decisions.single()
                assertEquals("Tinkaton", decision.speciesName)
                assertEquals(VerifiedHpFaintResolver.BASIS, decision.basis)
                assertEquals(input.last(), decision.sourceArticle)
                assertEquals(setOf("badge", "red", "empty"), decision.evidenceReferences.toSet())
                assertTrue(delivered.flatMap(tracker::acceptAll).isEmpty())
                assertTrue("No invented ball count", tracker.resolutions.isEmpty())
            }
        }
    }

    @Test fun `a replacement cannot inherit a late empty frame and a late charge cue retracts it`() {
        val input = evidence()
        val history = LiveTeamLossHistory(side)
        (listOf(badge("replacement", 62 * second, "Carbink")) + input.reversed()).forEach(history::accept)
        assertEquals(listOf("Tinkaton"), history.currentFaints.map { it.first.speciesName })
        history.accept(article("late-charge", 60 * second, ChargeMoveUsedAnnounced))
        assertTrue(history.currentFaints.isEmpty())
    }

    @Test fun `missing zero wrong camera and untrusted identity never publish a faint`() {
        val input = evidence()
        val variants = listOf(input.dropLast(1), input.map {
            if (it.id.value == "empty") it.copy(sourceId = SourceId("CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS")) else it
        }, input.map { if (it.id.value == "badge") it.copy(confidence = .3f) else it }, input.map {
            if (it.id.value == "empty") it.copy(payload = (it.payload as ActiveHpBarMeasured).copy(fillColor = null)) else it
        })
        variants.forEach { assertTrue(it.flatMap(TeamFaintTracker(side)::acceptAll).isEmpty()) }
    }

    @Test fun `fresh independent badge plus positive HP restores the blocked identity and rejects stale faint delivery`() {
        for (target in ActivePokemonSide.entries) {
            val state = FaintedCombatants()
            evidence(target).forEach(state::observe)
            state.observe(faint(target))
            assertTrue(state.contains(target, "Tinkaton"))
            state.observe(positive(target = target))
            assertTrue("Positive HP with an old name might be a replacement", state.contains(target, "Tinkaton"))
            val fresh = badge("fresh", 64 * second, target = target)
            state.observe(fresh)
            assertTrue("Badge alone is not survival", state.contains(target, "Tinkaton"))
            assertEquals(listOf(fresh), state.observe(positive(65 * second, "proved-alive", target)))
            assertFalse(state.contains(target, "Tinkaton"))
            assertFalse(state.supports(faint(target).payload as ActivePokemonFaintedWitnessed, emptyAt))
            state.observe(faint(target))
            assertFalse(state.contains(target, "Tinkaton"))
        }
    }

    @Test fun `duplicate conflicts and another match cannot supply ownership while restart discards old attempt`() {
        val history = LiveVerifiedHpFaintHistory(side)
        evidence().forEach(history::accept)
        assertEquals(1, history.reconciliation.faints.size)
        history.accept(pixels("empty").copy(recordedAt = 999999))
        assertTrue(history.reconciliation.faints.isEmpty())
        val isolated = LiveVerifiedHpFaintHistory(side)
        evidence().map { if (it.id.value == "badge") it.copy(matchId = MatchId("foreign")) else it }
            .forEach(isolated::accept)
        assertTrue(isolated.reconciliation.faints.isEmpty())
        val state = LiveTeamLossHistory(side)
        evidence().forEach(state::accept)
        state.accept(article("restart", 70 * second, ObservationRestartedByUser))
        evidence().reversed().forEach(state::accept)
        assertTrue(state.currentFaints.isEmpty())
    }

    @Test fun `settled positive frames do not repeatedly rebuild HP history`() {
        val history = LiveVerifiedHpFaintHistory(side)
        evidence().forEach(history::accept)
        val initial = history.rebuilds
        repeat(200) { history.accept(positive(61 * second + it * 50_000_000L, "frame-$it")) }
        assertEquals(initial, history.rebuilds)
        history.accept(badge("fresh", 72 * second))
        history.accept(positive(73 * second, "survival"))
        assertTrue(history.reconciliation.faints.isEmpty())
        val settled = history.rebuilds
        repeat(200) { history.accept(positive(74 * second + it * 50_000_000L, "settled-$it")) }
        assertEquals(settled, history.rebuilds)
    }

    @Test fun `completed replay summary and metrics discard superseded HP conclusions without changing archive`() {
        val input = evidence() + faint() + badge("fresh", 62 * second) + positive()
        val archive = RealityArticleArchiveMapper.createArchive(match, input)
        val before = MatchArchiveSerializer.serialize(archive)
        assertTrue(MatchReplayModel(archive).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        assertEquals(0, archive.summarize().opponent.faintCount)
        assertEquals(0, BattleRecordProjector.project(archive).uncreditedOpponentFaints)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        val confirmed = RealityArticleArchiveMapper.createArchive(match, evidence() + faint())
        assertEquals(1, confirmed.summarize().opponent.faintCount)
    }

    @Test fun `forecast clears energy for supported faint then rebuilds after survival without repeated frame work`() {
        val f = OpponentEnergyForecast()
        val move = Move("Test move", PokemonType.NORMAL, 1, 3, true, 1)
        f.registerMoves("Tinkaton", listOf(move))
        repeat(700) { f.observeBattlefield(it * 125_000_000L, true) }
        evidence().forEach(f::observe)
        f.observe(faint())
        assertEquals(0, f.estimate(61 * second)!!.energy)
        f.observe(badge("fresh", 62 * second))
        assertEquals(0, f.estimate(62 * second)!!.energy)
        f.observe(positive())
        assertTrue(f.estimate(64 * second)!!.energy > 0)
        assertFalse(f.estimate(64 * second)!!.paused)
        val builds = f.projectionWork().builds
        repeat(40) {
            f.observe(positive(65 * second + it * 50_000_000, "frame-$it"))
            f.estimate(65 * second + it * 50_000_000)
        }
        assertEquals(builds, f.projectionWork().builds)
    }

    @Test fun `HP inference cannot permanently retire a roster slot or stop independent audio`() {
        val identity = PlayerActiveIdentityInference()
        identity.accept(article("team", 0, PlayerTeamSlotConfigured(1, "Tinkaton", 959, "Test move", listOf("Test charge"))))
        identity.accept(badge(target = ActivePokemonSide.PLAYER))
        identity.accept(faint(ActivePokemonSide.PLAYER))
        assertNotNull(identity.projectSpeciesReconfirmation(badge("fresh", 62 * second, target = ActivePokemonSide.PLAYER)))
        val entries = EntryAnnouncementSideTracker()
        entries.observeFaint(side, "Tinkaton", emptyAt, "hp-faint", VerifiedHpFaintResolver.BASIS)
        assertNull(entries.attribute("Carbink", emptyList(), entryAtNanos = 62 * second))
        val gate = FastMoveListeningGate()
        gate.observe(MatchStarted, second)
        repeat(6) { gate.observe(ActivePokemonFaintedWitnessed(side, "name-$it", basis = VerifiedHpFaintResolver.BASIS),
            emptyAt + it * second) }
        assertTrue(gate.allows(70 * second))
        gate.observe(MatchEnded(BattleResult.WIN), 71 * second)
        assertFalse(gate.allows(72 * second))
    }

    @Test fun `HUD revises HP faint without changing the independently measured ball count`() {
        val hud = DroidballOverlayPresentation
        hud.reset()
        try {
            hud.recordOpponentSpecies("Tinkaton", 959, emptyList(), emptyList(), observedAtNanos = 57 * second)
            hud.recordOpponentResources(OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 3, 3), second)
            val history = LiveTeamLossHistory(side)
            fun deliver(article: RealityArticle) {
                if (history.accept(article)) hud.reconcileOpponentFaints(
                    history.currentFaints.map { it.first.speciesName }.toSet(), article.monotonicTimeNanos!!)
            }
            evidence().forEach(::deliver)
            assertTrue(hud.opponentSpecies.value.single().isFainted)
            deliver(badge("fresh", 62 * second))
            assertTrue(hud.opponentSpecies.value.single().isFainted)
            deliver(positive())
            assertFalse(hud.opponentSpecies.value.single().isFainted)
            assertTrue("HP evidence never became a count", history.resolutions.isEmpty())
        } finally { hud.reset() }
    }

    @Test fun `count corroboration and repeated observations yield one replay and summary knockout`() {
        val input = evidence()
        val before = article("balls-before", 56 * second,
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 3, 3))
        val after = article("balls-after", 61 * second,
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 2, 3))
        for (delivered in listOf(listOf(before) + input + after, listOf(before, after) + input.reversed())) {
            val history = LiveTeamLossHistory(side)
            val tracker = TeamFaintTracker(side)
            val published = mutableListOf<RealityArticle>()
            (delivered + delivered).forEach { event ->
                history.accept(event)
                tracker.acceptAll(event).forEach { decision ->
                    published += article("faint-${published.size}", decision.sourceArticle.monotonicTimeNanos!!,
                        ActivePokemonFaintedWitnessed(side, decision.speciesName, decision.speciesId, decision.basis))
                }
            }
            assertEquals(setOf("Tinkaton"), history.currentFaints.map { it.first.speciesName }.toSet())
            val archive = RealityArticleArchiveMapper.createArchive(match, delivered + published)
            assertEquals(1, archive.summarize().opponent.faintCount)
            assertEquals(1, MatchReplayModel(archive).eventMarkers.count { it.kind == ReplayEventMarkerKind.FAINT })
            assertTrue(published.size <= 2) // independent HP and count conclusions, never extra team losses
        }
    }

    @Test fun `a later count cannot pause replacement energy for the same HP knockout twice`() {
        val f = OpponentEnergyForecast()
        val move = Move("Test move", PokemonType.NORMAL, 1, 3, true, 1)
        listOf("Tinkaton", "Carbink").forEach { f.registerMoves(it, listOf(move)) }
        repeat(600) { f.observeBattlefield(it * 125_000_000L, true) }
        evidence().forEach(f::observe)
        f.observe(faint())
        f.observe(badge("replacement", 62 * second, "Carbink"))
        f.observe(article("hit", 63 * second, FastMoveUseObserved("hit", side, ActivePokemonSide.PLAYER,
            attackerSpeciesName = "Carbink", evidenceKinds = listOf("HP_BORDER_PULSE"))))
        val before = f.estimate(64 * second)!!.energy
        f.observe(article("count-confirmed", 64 * second, ActivePokemonFaintedWitnessed(side,
            "Tinkaton", 959, "OPPONENT_POKE_BALL_COUNT_DECREASE")))
        val after = f.estimate(65 * second)!!
        assertFalse(after.paused)
        assertTrue(after.energy > before)
    }

    @Test fun `cadence identity and observed attack ownership recover with the same survival proof`() = runBlocking {
        val move = Move("Test move", PokemonType.NORMAL, 1, 3, true, 1)
        val pokemon = Pokemon(959, "Tinkaton", listOf(PokemonType.NORMAL), "",
            fastMoves = listOf(move), chargedMoves = emptyList())
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = pokemon.takeIf { it.name == name }
            override suspend fun getPokemonById(id: Int) = pokemon.takeIf { it.id == id }
            override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
        }
        val cadence = FastMoveCadenceInference(knowledge)
        val uses = FastMoveUseInference()
        (evidence() + faint()).forEach { cadence.accept(it); uses.accept(it) }
        assertNull(cadence.candidateSnapshot(side))
        val fresh = badge("fresh", 62 * second)
        cadence.accept(fresh); uses.accept(fresh)
        assertNull(cadence.candidateSnapshot(side))
        cadence.accept(positive()); uses.accept(positive())
        assertEquals(move.name, cadence.candidateSnapshot(side)?.identifiedMoveName)
        uses.accept(article("pulse", 65 * second,
            HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, 100f, 10)))
        assertEquals("Tinkaton", uses.flush().single().payload.attackerSpeciesName)
    }

    @Test fun `energy preserves finishing hit source time and resumes on survival after a provisional vacancy`() = runBlocking {
        val move = Move("Test move", PokemonType.NORMAL, 1, 3, true, 1)
        val pokemon = Pokemon(959, "Tinkaton", listOf(PokemonType.NORMAL), "",
            fastMoves = listOf(move), chargedMoves = emptyList())
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = pokemon.takeIf { it.name == name }
            override suspend fun getPokemonById(id: Int) = pokemon.takeIf { it.id == id }
            override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
        }
        val engine = FastMoveEnergyInference(knowledge)
        evidence().forEach { engine.accept(it) }
        engine.accept(article("move", 58 * second, FastMoveIdentified(side, "Tinkaton", move.name,
            second / 2, null, 0, "ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE")))
        engine.accept(faint())
        fun hit(id: String, at: Long) = article(id, at, FastMoveUseObserved(id, side, ActivePokemonSide.PLAYER,
            attackerSpeciesName = "Tinkaton", evidenceKinds = listOf("HP_BORDER_PULSE")))
        assertEquals(3, engine.accept(hit("delayed-before-ko", 60 * second)).single().payload.energyPerUse)
        assertTrue(engine.accept(hit("during-vacancy", 61 * second)).isEmpty())
        engine.accept(badge("fresh", 62 * second))
        engine.accept(positive())
        assertEquals(3, engine.accept(hit("after-survival", 65 * second)).single().payload.energyPerUse)
    }
}
