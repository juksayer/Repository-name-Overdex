package com.example.overdex.battle.inference

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveSoundMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class FastMoveCadenceInferenceTest {
    private val inference = FastMoveCadenceInference(EmptyKnowledge)

    @Test
    fun `two measured intervals resolve one uniquely timed fast move`() {
        val incinerate = move("Incinerate", turns = 3, energy = 20)
        val ember = move("Ember", turns = 1, energy = 6)

        val resolved = inference.resolveUniqueMove(
            listOf(incinerate, ember),
            listOf(1_430_000_000L, 1_510_000_000L)
        )

        assertEquals(incinerate, resolved)
    }

    @Test
    fun `one interval cannot identify a move`() {
        assertNull(inference.resolveUniqueMove(listOf(move("Incinerate", 3, 20)), listOf(1_500_000_000L)))
    }

    @Test
    fun `one interval can narrow the HUD without prematurely identifying the move`() = runBlocking {
        val sneasel = Pokemon(
            id = 215,
            name = "Sneasel",
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            region = "Johto",
            fastMoves = listOf(
                move("Ice Shard", 3, 10, PokemonType.ICE),
                move("Feint Attack", 2, 6, PokemonType.DARK),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(sneasel))
        engine.accept(article(
            "species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215),
        ))

        assertTrue(engine.accept(borderCadence("first-interval", 1_000_000_000L)).isEmpty())
        val snapshot = requireNotNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))

        assertEquals(listOf("Feint Attack"), snapshot.remainingMoveNames)
        assertEquals(listOf("Ice Shard"), snapshot.eliminatedMoveNames)
        assertNull(snapshot.identifiedMoveName)
        assertEquals(listOf("CADENCE"), snapshot.evidenceKinds)
    }

    @Test
    fun `a species with one legal fast move is identified immediately`() = runBlocking {
        val vaporeon = Pokemon(
            id = 134,
            name = "Vaporeon",
            types = listOf(PokemonType.WATER),
            region = "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3, PokemonType.WATER)),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(vaporeon))

        val result = engine.accept(article(
            "species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134),
        )).single().payload as FastMoveIdentified

        assertEquals("Water Gun", result.moveName)
        assertEquals("ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE", result.basis)
    }

    @Test
    fun `equal duration candidates prefer the uniquely higher energy move`() {
        assertEquals(
            "Move B",
            inference.resolveUniqueMove(
                listOf(move("Move A", 2, 7), move("Move B", 2, 9)),
                listOf(980_000_000L, 1_010_000_000L)
            )?.name
        )
    }

    @Test
    fun `higher energy duration tie is labeled as an assumption and later cadence can refute it`() = runBlocking {
        val pokemon = Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
            fastMoves = listOf(
                move("High Energy Tie", 2, 9),
                move("Low Energy Tie", 2, 7),
                move("Slower Move", 3, 6)
            ), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))

        engine.accept(borderCadence("tie1", 1_000_000_000L))
        val assumed = engine.accept(borderCadence("tie2", 1_000_000_000L))
            .first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified
        assertEquals("High Energy Tie", assumed.moveName)
        assertTrue(assumed.basis.endsWith("HIGHER_ENERGY_TIE_BREAK_ASSUMPTION"))

        engine.accept(borderCadence("slow1", 1_500_000_000L))
        val refuted = engine.accept(borderCadence("slow2", 1_500_000_000L))
            .first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified
        assertEquals("Slower Move", refuted.moveName)
        assertTrue(!refuted.basis.contains("ASSUMPTION"))
    }

    @Test
    fun `inconsistent pulses do not become a move through their median`() {
        assertNull(
            inference.resolveUniqueMove(
                listOf(move("Zen Headbutt", 3, 6), move("Dragon Breath", 1, 3)),
                listOf(2_070_000_000L, 1_078_000_000L, 1_515_000_000L)
            )
        )
    }

    @Test
    fun `isolated noisy charge fill interval does not hide a stable direct cadence`() {
        val furyCutter = move("Fury Cutter", 1, 4)
        val waterfall = move("Waterfall", 3, 10)

        val resolved = inference.resolveUniqueMove(
            listOf(furyCutter, waterfall),
            listOf(463_000_000L, 1_962_000_000L, 367_000_000L, 459_000_000L)
        )

        assertEquals(furyCutter, resolved)
    }

    @Test
    fun `cadence identifies the move without counting witness intervals as uses`() = runBlocking {
        val incinerate = move("Incinerate", 5, 20)
        val pokemon = Pokemon(
            id = 776,
            name = "Turtonator",
            types = listOf(PokemonType.FIRE, PokemonType.DRAGON),
            region = "Alola",
            fastMoves = listOf(incinerate),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator", 776)))

        assertEquals(0, engine.accept(cadence("c1", 2_450_000_000L)).size)
        val resolved = engine.accept(cadence("c2", 2_510_000_000L))

        assertEquals(1, resolved.count { it.payload is FastMoveIdentified })
        assertEquals(
            ActivePokemonSide.PLAYER,
            (resolved.first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified).side
        )
        val next = engine.accept(cadence("c4", 2_490_000_000L))
        assertTrue(next.isEmpty())
    }

    @Test
    fun `individual observed uses provide cadence when aggregate cadence is absent`() = runBlocking {
        val iceShard = move("Ice Shard", 3, 10, PokemonType.ICE)
        val feintAttack = move("Feint Attack", 2, 6, PokemonType.DARK)
        val sneasel = Pokemon(
            id = 215,
            name = "Sneasel",
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            region = "Johto",
            fastMoves = listOf(iceShard, feintAttack),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(sneasel))
        engine.accept(article(
            "species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215),
        ))

        assertTrue(engine.accept(fastUseAt("use-1", 10_000_000_000L)).isEmpty())
        assertTrue(engine.accept(fastUseAt("use-2", 11_450_000_000L)).isEmpty())
        val resolved = engine.accept(fastUseAt("use-3", 12_950_000_000L))

        val identified = resolved.single().payload as FastMoveIdentified
        assertEquals("Ice Shard", identified.moveName)
        assertEquals(2, identified.cadenceSampleCount)
        assertTrue(identified.basis.startsWith("OBSERVED_USES_CADENCE"))
        assertEquals(
            setOf("species", "use-1", "use-2", "use-3"),
            resolved.single().predecessorIds.map { it.value }.toSet(),
        )
    }

    @Test
    fun `observed uses before a late species identity are recovered`() = runBlocking {
        val sneasel = Pokemon(
            id = 215,
            name = "Sneasel",
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            region = "Johto",
            fastMoves = listOf(
                move("Ice Shard", 3, 10, PokemonType.ICE),
                move("Feint Attack", 2, 6, PokemonType.DARK),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(sneasel))

        assertTrue(engine.accept(fastUseAt("early-use-1", 10_000_000_000L)).isEmpty())
        assertTrue(engine.accept(fastUseAt("early-use-2", 11_450_000_000L)).isEmpty())
        assertTrue(engine.accept(fastUseAt("early-use-3", 12_950_000_000L)).isEmpty())

        val resolved = engine.accept(article(
            "late-species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215),
        ))

        val identified = resolved.single().payload as FastMoveIdentified
        assertEquals("Ice Shard", identified.moveName)
        assertTrue(identified.basis.startsWith("OBSERVED_USES_CADENCE"))
        assertEquals(
            setOf("late-species", "early-use-1", "early-use-2", "early-use-3"),
            resolved.single().predecessorIds.map { it.value }.toSet(),
        )
    }

    @Test
    fun `charge fill only uses cannot invent a move cadence`() = runBlocking {
        val sneasel = Pokemon(
            id = 215,
            name = "Sneasel",
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            region = "Johto",
            fastMoves = listOf(move("Ice Shard", 3, 10, PokemonType.ICE)),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(sneasel))
        engine.accept(article(
            "species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215),
        ))

        repeat(4) { index ->
            val at = 10_000_000_000L + index * 1_500_000_000L
            assertTrue(engine.accept(fastUseAt(
                id = "fill-$index",
                at = at,
                evidenceKinds = listOf("PLAYER_CHARGE_FILL_INCREASE"),
            )).isEmpty())
        }
    }

    @Test
    fun `noise outside identified duration does not prevent identity`() = runBlocking {
        val pokemon = Pokemon(503, "Samurott", listOf(PokemonType.WATER), "Unova",
            fastMoves = listOf(move("Fury Cutter", 1, 4), move("Waterfall", 3, 10)),
            chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Samurott", 503)))
        engine.accept(cadence("f1", 463_000_000L))
        engine.accept(cadence("noise", 1_962_000_000L))
        val resolved = engine.accept(cadence("f2", 367_000_000L))

        assertEquals("Fury Cutter", (resolved.first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified).moveName)
        assertEquals(1, resolved.size)
    }

    @Test
    fun `border cadence is attributed to the side opposite the damaged bar`() = runBlocking {
        val move = move("Water Gun", 1, 3)
        val pokemon = Pokemon(
            id = 134,
            name = "Vaporeon",
            types = listOf(PokemonType.WATER),
            region = "Kanto",
            fastMoves = listOf(move),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))

        assertEquals(0, engine.accept(borderCadence("b0", 500_000_000L)).size)
        val resolved = engine.accept(borderCadence("b1", 510_000_000L))

        val identity = resolved.first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified
        assertEquals(ActivePokemonSide.OPPONENT, identity.side)
        assertEquals("Water Gun", identity.moveName)
        assertEquals(1, resolved.size)
    }

    @Test
    fun `recipient visual cadence is attributed to the attacking side`() = runBlocking {
        val pokemon = Pokemon(
            id = 134,
            name = "Vaporeon",
            types = listOf(PokemonType.WATER),
            region = "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3)),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))

        assertEquals(0, engine.accept(visualCadence("v0", 500_000_000L)).size)
        val resolved = engine.accept(visualCadence("v1", 510_000_000L))

        val identity = resolved.first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified
        assertEquals(ActivePokemonSide.OPPONENT, identity.side)
        assertEquals("Water Gun", identity.moveName)
        assertEquals(1, resolved.size)
    }

    @Test
    fun `audible onset cadence narrows the cued attacking side and visual cadence corroborates it`() = runBlocking {
        val sneasel = Pokemon(
            id = 215,
            name = "Sneasel",
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            region = "Johto",
            fastMoves = listOf(
                move("Ice Shard", 3, 10, PokemonType.ICE),
                move("Feint Attack", 2, 6, PokemonType.DARK),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(sneasel))
        engine.accept(article(
            "species",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215),
        ))

        assertTrue(engine.accept(sound("sound-1", 10_000_000_000L)).isEmpty())
        assertTrue(engine.accept(sound("sound-2", 11_000_000_000L)).isEmpty())
        val audioIdentity = engine.accept(sound("sound-3", 12_000_000_000L))
            .single().payload as FastMoveIdentified
        assertEquals("Feint Attack", audioIdentity.moveName)
        assertTrue(audioIdentity.basis.startsWith("AUDIO_PROFILE_CADENCE"))
        assertTrue(!audioIdentity.basis.contains("CORROBORATED"))

        val corroborated = engine.accept(borderCadence("border", 1_000_000_000L).copy(monotonicTimeNanos = 12_100_000_000L))
            .single().payload as FastMoveIdentified
        assertEquals("Feint Attack", corroborated.moveName)
        assertTrue(corroborated.basis.contains("CORROBORATED_BY_BORDER_PULSE"))
        assertEquals(
            listOf("CADENCE", "AUDIO"),
            engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.evidenceKinds,
        )
    }

    @Test
    fun `player charge fill transitions remain evidence without claiming move cadence`() = runBlocking {
        val pokemon = Pokemon(
            id = 776,
            name = "Turtonator",
            types = listOf(PokemonType.FIRE, PokemonType.DRAGON),
            region = "Alola",
            fastMoves = listOf(move("Incinerate", 3, 20)),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator", 776)))

        repeat(2) { assertEquals(0, engine.accept(energyFillCadence("e$it", 1_500_000_000L)).size) }
        val result = engine.accept(energyFillCadence("e2", 1_500_000_000L))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `configured player move limits cadence resolution to the selected move`() = runBlocking {
        val pokemon = Pokemon(
            id = 711,
            name = "Gourgeist",
            types = listOf(PokemonType.GHOST, PokemonType.GRASS),
            region = "Kalos",
            fastMoves = listOf(move("Razor Leaf", 2, 4), move("Incinerate", 5, 20)),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("config", PlayerTeamSlotConfigured(
            1, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb")
        )))
        val configuredIdentity = engine.accept(article(
            "species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711)
        ))

        assertEquals(
            "Incinerate",
            (configuredIdentity.single().payload as FastMoveIdentified).moveName
        )
        assertEquals(
            "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES",
            (configuredIdentity.single().payload as FastMoveIdentified).basis
        )

        repeat(3) { engine.accept(cadence("wrong$it", 1_000_000_000L)) }
        val resolved = buildList {
            repeat(3) { addAll(engine.accept(cadence("right$it", 2_500_000_000L))) }
        }

        assertTrue(resolved.none { (it.payload as? FastMoveIdentified)?.moveName == "Razor Leaf" })
        assertTrue(resolved
            .mapNotNull { it.payload as? FastMoveIdentified }
            .all { it.moveName == "Incinerate" })
    }

    @Test
    fun `super effective text uniquely identifies Sealeo Water Gun against Camerupt`() = runBlocking {
        val camerupt = Pokemon(
            id = 323,
            name = "Camerupt",
            types = listOf(PokemonType.FIRE, PokemonType.GROUND),
            region = "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)),
            chargedMoves = emptyList()
        )
        val sealeo = Pokemon(
            id = 364,
            name = "Sealeo",
            types = listOf(PokemonType.ICE, PokemonType.WATER),
            region = "Hoenn",
            fastMoves = listOf(
                move("Water Gun", 1, 3, PokemonType.WATER),
                move("Powder Snow", 2, 8, PokemonType.ICE)
            ),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(fastUse("use", ActivePokemonSide.OPPONENT, "Sealeo"))

        val results = engine.accept(article(
            "effect",
            FastMoveEffectivenessWitnessed(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                damagedSide = null,
                recognizedText = "SUPER EFFECTIVE!"
            )
        ))

        val identified = results.single().payload as FastMoveIdentified
        assertEquals(ActivePokemonSide.OPPONENT, identified.side)
        assertEquals("Water Gun", identified.moveName)
        assertNull(identified.observedMedianIntervalNanos)
        assertEquals(0, identified.cadenceSampleCount)
        assertEquals(setOf("player", "opponent", "effect"), results.single().predecessorIds.map { it.value }.toSet())
    }

    @Test
    fun `side located effectiveness narrows Sealeo without waiting for a separate hit witness`() = runBlocking {
        val camerupt = Pokemon(
            id = 323,
            name = "Camerupt",
            types = listOf(PokemonType.FIRE, PokemonType.GROUND),
            region = "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)),
            chargedMoves = emptyList(),
        )
        val sealeo = Pokemon(
            id = 364,
            name = "Sealeo",
            types = listOf(PokemonType.ICE, PokemonType.WATER),
            region = "Hoenn",
            fastMoves = listOf(
                move("Water Gun", 1, 3, PokemonType.WATER),
                move("Powder Snow", 2, 8, PokemonType.ICE),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))

        val results = engine.accept(article(
            "effect",
            FastMoveEffectivenessWitnessed(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                damagedSide = ActivePokemonSide.PLAYER,
                recognizedText = "SUPER EFFECTIVE!",
            ),
        ))

        assertEquals("Water Gun", (results.single().payload as FastMoveIdentified).moveName)
        val snapshot = requireNotNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))
        assertEquals(listOf("Water Gun"), snapshot.remainingMoveNames)
        assertEquals(listOf("Powder Snow"), snapshot.eliminatedMoveNames)
        assertEquals(listOf("EFFECTIVENESS"), snapshot.evidenceKinds)
    }

    @Test
    fun `bar motion cannot resurrect Powder Snow after effectiveness identifies Water Gun`() = runBlocking {
        val camerupt = Pokemon(
            id = 323,
            name = "Camerupt",
            types = listOf(PokemonType.FIRE, PokemonType.GROUND),
            region = "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)),
            chargedMoves = emptyList(),
        )
        val sealeo = Pokemon(
            id = 364,
            name = "Sealeo",
            types = listOf(PokemonType.ICE, PokemonType.WATER),
            region = "Hoenn",
            fastMoves = listOf(
                move("Water Gun", 1, 3, PokemonType.WATER),
                move("Powder Snow", 2, 8, PokemonType.ICE),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(article(
            "effect",
            FastMoveEffectivenessWitnessed(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                damagedSide = ActivePokemonSide.PLAYER,
                recognizedText = "SUPER EFFECTIVE!",
            ),
        ))

        assertTrue(engine.accept(article(
            "motion-1",
            ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000L, 40f, 6),
        )).isEmpty())
        val secondMotion = engine.accept(article(
            "motion-2",
            ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000L, 40f, 6),
        ))

        assertTrue(secondMotion.none { (it.payload as? FastMoveIdentified)?.moveName == "Powder Snow" })
        val snapshot = requireNotNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))
        assertEquals("Water Gun", snapshot.identifiedMoveName)
        assertEquals(listOf("Water Gun"), snapshot.remainingMoveNames)
        assertEquals(listOf("Powder Snow"), snapshot.eliminatedMoveNames)
    }

    @Test
    fun `late effectiveness text cannot identify the replacement opponent`() = runBlocking {
        val camerupt = Pokemon(
            id = 323,
            name = "Camerupt",
            types = listOf(PokemonType.FIRE, PokemonType.GROUND),
            region = "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)),
            chargedMoves = emptyList(),
        )
        val replacement = Pokemon(
            id = 10_001,
            name = "Replacement",
            types = listOf(PokemonType.NORMAL),
            region = "Test",
            fastMoves = listOf(
                move("Water Gun", 1, 3, PokemonType.WATER),
                move("Tackle", 1, 3, PokemonType.NORMAL),
            ),
            chargedMoves = emptyList(),
        )
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, replacement))
        engine.accept(article(
            "player",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323),
        ).copy(monotonicTimeNanos = 1_000_000_000L))
        engine.accept(article(
            "entry",
            com.example.overdex.battle.custody.SpeciesCheckMeasured(
                ActivePokemonSide.OPPONENT,
                1,
                "OPENED",
                "ENTRY",
                10_000_000_000L,
                0,
                10_000_000_000L,
            ),
        ).copy(monotonicTimeNanos = 10_000_000_000L))
        engine.accept(article(
            "replacement",
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Replacement", 10_001),
        ).copy(monotonicTimeNanos = 10_000_000_000L))

        val result = engine.accept(article(
            "late-effect",
            FastMoveEffectivenessWitnessed(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                damagedSide = ActivePokemonSide.PLAYER,
                recognizedText = "SUPER EFFECTIVE!",
            ),
        ).copy(monotonicTimeNanos = 9_000_000_000L))

        assertTrue(result.isEmpty())
        val snapshot = requireNotNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))
        assertEquals(listOf("Water Gun", "Tackle"), snapshot.remainingMoveNames)
        assertNull(snapshot.identifiedMoveName)
    }

    @Test
    fun `cadence remains evidence after effectiveness identifies the move without counting energy`() = runBlocking {
        val camerupt = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)), chargedMoves = emptyList())
        val sealeo = Pokemon(364, "Sealeo", listOf(PokemonType.ICE, PokemonType.WATER), "Hoenn",
            fastMoves = listOf(move("Water Gun", 1, 3, PokemonType.WATER), move("Powder Snow", 2, 8, PokemonType.ICE)),
            chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(fastUse("use", ActivePokemonSide.OPPONENT, "Sealeo"))
        engine.accept(article("effect", FastMoveEffectivenessWitnessed(
            FastMoveEffectiveness.SUPER_EFFECTIVE, null, "SUPER EFFECTIVE"
        )))

        val cadence = engine.accept(borderCadence("water-gun-hit", 500_000_000L))

        assertTrue(cadence.isEmpty())
    }

    @Test
    fun `effectiveness waits for an aligned fast move use`() = runBlocking {
        val camerupt = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20, PokemonType.FIRE)), chargedMoves = emptyList())
        val sealeo = Pokemon(364, "Sealeo", listOf(PokemonType.ICE, PokemonType.WATER), "Hoenn",
            fastMoves = listOf(move("Water Gun", 1, 3, PokemonType.WATER), move("Powder Snow", 2, 8, PokemonType.ICE)),
            chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(camerupt, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))

        assertTrue(engine.accept(article("effect", FastMoveEffectivenessWitnessed(
            FastMoveEffectiveness.SUPER_EFFECTIVE, null, "SUPER EFFECTIVE"
        ))).isEmpty())
        val resolved = engine.accept(fastUse("use", ActivePokemonSide.OPPONENT, "Sealeo"))

        assertEquals("Water Gun", (resolved.single().payload as FastMoveIdentified).moveName)
        assertEquals(setOf("player", "opponent", "effect"), resolved.single().predecessorIds.map { it.value }.toSet())
    }

    @Test
    fun `cadence before first species is recovered once when identity arrives`() = runBlocking {
        val pokemon = Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        repeat(3) { assertEquals(0, engine.accept(borderCadence("early$it", 500_000_000L)).size) }
        val identity = article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134))
        val results = engine.accept(identity)
        assertEquals(1, results.count { it.payload is FastMoveIdentified })
        assertEquals(0, engine.accept(identity).size)
        assertEquals(0, engine.accept(borderCadence("early0", 500_000_000L)).size)
        assertEquals(0, engine.accept(borderCadence("next", 500_000_000L)).size)
    }

    @Test
    fun `entry clears pending prior-combatant intervals and crossing intervals`() = runBlocking {
        val pokemon = Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        repeat(3) { engine.accept(borderCadence("old$it", 500_000_000L)) }
        engine.accept(article("entry", com.example.overdex.battle.custody.SpeciesCheckMeasured(
            ActivePokemonSide.OPPONENT, 1, "OPENED", "ENTRY", 1_000_000_000L, 0, 1_500_000_000L)))
        engine.accept(borderCadence("crossing", 500_000_000L).copy(monotonicTimeNanos = 1_200_000_000L))
        engine.accept(borderCadence("new1", 500_000_000L).copy(monotonicTimeNanos = 1_600_000_000L))
        val result = engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))
        assertEquals(1, result.count { it.payload is FastMoveIdentified })
        val resolved = engine.accept(borderCadence("new2", 500_000_000L).copy(monotonicTimeNanos = 2_100_000_000L))
        assertEquals(1, resolved.count { it.payload is FastMoveIdentified })
    }

    @Test
    fun `one hit interval and one independent motion interval combine immediately`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        assertTrue(engine.accept(borderCadence("hit", 500_000_000L)).isEmpty())
        val result = engine.accept(article("motion", ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6,
        ))).single()
        val identity = result.payload as FastMoveIdentified
        assertEquals("Water Gun", identity.moveName)
        assertTrue(identity.basis.contains("CORROBORATED_BY_BAR_MOTION"))
        assertEquals(setOf("species", "hit", "motion"), result.predecessorIds.map { it.value }.toSet())
    }

    @Test
    fun `a raw pulse and its fused use are one evidence family`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(fastUseAt("u1", 10_000_000_000L))
        assertTrue(engine.accept(fastUseAt("u2", 10_500_000_000L)).isEmpty())
        assertTrue(engine.accept(borderCadence("same-hit", 500_000_000L)
            .copy(monotonicTimeNanos = 10_500_000_000L)).isEmpty())
        val identity = engine.accept(fastUseAt("u3", 11_000_000_000L)).single().payload as FastMoveIdentified
        assertEquals("Water Gun", identity.moveName)
        assertTrue(!identity.basis.contains("CORROBORATED"))
    }

    @Test
    fun `independent sources can overturn a noisy earlier source`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(borderCadence("b1", 1_000_000_000L))
        engine.accept(borderCadence("b2", 1_000_000_000L))
        engine.accept(article("m1", ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6)))
        engine.accept(article("m2", ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6)))
        engine.accept(sound("a1", 1_000_000_000L))
        engine.accept(sound("a2", 1_500_000_000L))
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `effectiveness constraint survives switch and does not leak across mirror sides`() = runBlocking {
        val sealeo = testSealeo()
        val fire = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(sealeo, fire))
        engine.accept(article("ours", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("theirs", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(effect("water-proof", FastMoveEffectiveness.SUPER_EFFECTIVE, 2_000_000_000L))
        engine.accept(entry("switch-out", ActivePokemonSide.OPPONENT, 5_000_000_000L))
        engine.accept(article("other", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Camerupt", 323))
            .copy(monotonicTimeNanos = 5_100_000_000L))
        engine.accept(entry("switch-back", ActivePokemonSide.OPPONENT, 10_000_000_000L))
        val restored = engine.accept(article("return", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364))
            .copy(monotonicTimeNanos = 10_100_000_000L)).single()
        assertEquals("Water Gun", (restored.payload as FastMoveIdentified).moveName)
        assertTrue(restored.predecessorIds.any { it.value == "water-proof" })
        engine.accept(article("our-sealeo", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Sealeo", 364))
            .copy(monotonicTimeNanos = 11_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `mirror opponent never inherits configured player move`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Sealeo", 364, "Powder Snow", listOf("Surf"))))
        engine.accept(article("ours", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Sealeo", 364)))
        engine.accept(article("theirs", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        assertEquals("Powder Snow", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(borderCadence("hit1", 500_000_000L))
        engine.accept(borderCadence("hit2", 500_000_000L))
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `conflicting effectiveness must repeat before replacing established identity`() = runBlocking {
        val sealeo = testSealeo()
        val fire = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE), "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(sealeo, fire))
        engine.accept(article("ours", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        engine.accept(article("theirs", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(effect("super", FastMoveEffectiveness.SUPER_EFFECTIVE, 2_000_000_000L))
        assertTrue(engine.accept(effect("noise", FastMoveEffectiveness.NOT_VERY_EFFECTIVE, 3_000_000_000L)).isEmpty())
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(effect("recheck", FastMoveEffectiveness.NOT_VERY_EFFECTIVE, 4_000_000_000L))
        assertEquals("Powder Snow", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `configured form name resolves by unambiguous species id`() = runBlocking {
        val pokemon = Pokemon(711, "Gourgeist", listOf(PokemonType.GHOST, PokemonType.GRASS), "Kalos",
            fastMoves = listOf(move("Incinerate", 5, 20), move("Razor Leaf", 2, 4, PokemonType.GRASS)),
            chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711, "Incinerate", listOf("Shadow Ball"))))
        val identity = engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711)))
            .single().payload as FastMoveIdentified
        assertEquals("Incinerate", identity.moveName)
        assertEquals("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES", identity.basis)
    }

    @Test
    fun `configured identity stays sufficient for energy after cadence corroboration`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        val energy = FastMoveEnergyInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Sealeo", 364, "Water Gun", listOf("Surf"))))
        val initial = engine.accept(article("ours", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Sealeo", 364))).single()
        energy.accept(article("initial", initial.payload))
        engine.accept(cadence("c1", 500_000_000L))
        val refined = engine.accept(cadence("c2", 500_000_000L)).single()
        energy.accept(article("refined", refined.payload))
        val result = energy.accept(fastUse("use", ActivePokemonSide.PLAYER, "Sealeo"))
        assertEquals(3, result.single().payload.totalEnergyGenerated)
    }

    @Test
    fun `charge sequence clears interval anchors without forgetting the move`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(fastUseAt("u1", 10_000_000_000L))
        engine.accept(fastUseAt("u2", 10_500_000_000L))
        engine.accept(fastUseAt("u3", 11_000_000_000L))
        engine.accept(article("charge", com.example.overdex.battle.custody.AttackIncoming)
            .copy(monotonicTimeNanos = 11_100_000_000L))
        engine.accept(borderCadence("qte-noise", 1_000_000_000L).copy(monotonicTimeNanos = 12_000_000_000L))
        engine.accept(article("impact", com.example.overdex.battle.custody.ChargeMoveUsedAnnounced)
            .copy(monotonicTimeNanos = 14_000_000_000L))
        assertTrue(engine.accept(fastUseAt("first-after-charge", 15_000_000_000L)).isEmpty())
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `constraints combine across defenders and survive repeated text`() = runBlocking {
        val attacker = Pokemon(9000, "Test", listOf(PokemonType.NORMAL), "Test", fastMoves = listOf(
            move("Water", 1, 3, PokemonType.WATER), move("Ground", 1, 3, PokemonType.GROUND),
            move("Fire", 1, 3, PokemonType.FIRE)), chargedMoves = emptyList())
        val fire = Pokemon(4, "Charmander", listOf(PokemonType.FIRE), "Kanto", fastMoves = emptyList(), chargedMoves = emptyList())
        val water = Pokemon(7, "Squirtle", listOf(PokemonType.WATER), "Kanto", fastMoves = emptyList(), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(attacker, fire, water))
        engine.accept(article("attacker", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Test", 9000)))
        engine.accept(article("defender1", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Charmander", 4)))
        engine.accept(effect("against-fire", FastMoveEffectiveness.SUPER_EFFECTIVE, 2_000_000_000L))
        assertEquals(listOf("Water", "Ground"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
        engine.accept(article("defender2", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Squirtle", 7))
            .copy(monotonicTimeNanos = 3_000_000_000L))
        repeat(30) { engine.accept(effect("against-water-$it", FastMoveEffectiveness.NOT_VERY_EFFECTIVE, 4_000_000_000L + it * 600_000_000L)) }
        assertEquals("Water", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(entry("recheck", ActivePokemonSide.OPPONENT, 30_000_000_000L))
        engine.accept(article("return", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Test", 9000))
            .copy(monotonicTimeNanos = 30_100_000_000L))
        assertEquals(listOf("Water"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
    }

    @Test
    fun `late old species cannot replace the current attacker`() = runBlocking {
        val sealeo = testSealeo()
        val vaporeon = Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(sealeo, vaporeon))
        engine.accept(article("now", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364))
            .copy(monotonicTimeNanos = 30_000_000_000L))
        engine.accept(article("old", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134))
            .copy(monotonicTimeNanos = 20_000_000_000L))
        assertEquals("Sealeo", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.speciesName)
    }

    @Test
    fun `unvalidated visual artifact cannot confirm a single hit interval`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(borderCadence("border", 500_000_000L))
        assertTrue(engine.accept(visualCadence("effect", 500_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    private fun testSealeo() = Pokemon(364, "Sealeo", listOf(PokemonType.ICE, PokemonType.WATER), "Hoenn",
        fastMoves = listOf(move("Water Gun", 1, 3, PokemonType.WATER), move("Powder Snow", 2, 8, PokemonType.ICE)),
        chargedMoves = emptyList())

    private fun entry(id: String, side: ActivePokemonSide, at: Long) = article(id,
        com.example.overdex.battle.custody.SpeciesCheckMeasured(side, 1, "OPENED", "ENTRY", at, 0, 1_500_000_000L)
    ).copy(monotonicTimeNanos = at)

    private fun effect(id: String, effectiveness: FastMoveEffectiveness, at: Long) = article(id,
        FastMoveEffectivenessWitnessed(effectiveness, ActivePokemonSide.PLAYER, effectiveness.name)
    ).copy(monotonicTimeNanos = at)

    private fun move(
        name: String,
        turns: Int,
        energy: Int,
        type: PokemonType = PokemonType.FIRE
    ) = Move(
        name = name,
        type = type,
        damage = 10,
        energy = energy,
        isFast = true,
        turns = turns
    )

    private object EmptyKnowledge : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String): Pokemon? = null
        override suspend fun getPokemonById(id: Int): Pokemon? = null
        override suspend fun getAllSpeciesNames(): Set<String> = emptySet()
    }

    private class SinglePokemonKnowledge(private val pokemon: Pokemon) : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String): Pokemon? = pokemon.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int): Pokemon? = pokemon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames(): Set<String> = setOf(pokemon.name)
    }

    private class MapPokemonKnowledge(vararg pokemon: Pokemon) : PokemonKnowledge {
        private val byId = pokemon.associateBy { it.id }
        private val byName = pokemon.associateBy { it.name }
        override suspend fun getPokemonByName(name: String): Pokemon? = byName[name]
        override suspend fun getPokemonById(id: Int): Pokemon? = byId[id]
        override suspend fun getAllSpeciesNames(): Set<String> = byName.keys
    }

    private fun cadence(id: String, intervalNanos: Long) = article(
        id,
        // Vertical movement belongs to the Pokémon whose HP bar moved.
        ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.PLAYER, intervalNanos, 40f, 6)
    )

    private fun borderCadence(id: String, intervalNanos: Long) = article(
        id,
        // A pulse on the player's damaged bar was caused by the opponent.
        ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.PLAYER, intervalNanos, 0.3f, 6)
    )

    private fun visualCadence(id: String, intervalNanos: Long) = article(
        id,
        // A visual effect on the player's recipient area was caused by the opponent.
        FastMoveRecipientVisualCadenceMeasured(ActivePokemonSide.PLAYER, intervalNanos)
    )

    private fun energyFillCadence(id: String, intervalNanos: Long) = article(
        id,
        PlayerChargeMoveEnergyFillCadenceMeasured(intervalNanos, listOf(0, 1))
    )

    private fun sound(id: String, onsetNanos: Long) = article(
        id,
        FastMoveSoundMeasured(
            audible = true,
            onsetOffsetNanos = 500_000_000L,
            soundDurationNanos = 200_000_000L,
            spectralCentroidHz = 900f,
            peakAmplitude = 0.4f,
            attackingSide = ActivePokemonSide.OPPONENT,
            soundOnsetMonotonicNanos = onsetNanos,
        ),
    ).copy(monotonicTimeNanos = onsetNanos - 500_000_000L)

    private fun fastUse(id: String, side: ActivePokemonSide, speciesName: String) = article(
        id,
        FastMoveUseObserved(
            useId = id,
            attackingSide = side,
            damagedSide = if (side == ActivePokemonSide.PLAYER) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER,
            appearanceId = "appearance",
            attackerSpeciesName = speciesName,
            evidenceKinds = listOf("HP_BORDER_PULSE")
        )
    )

    private fun fastUseAt(
        id: String,
        at: Long,
        evidenceKinds: List<String> = listOf("HP_DAMAGE_TICK"),
    ) = article(
        id,
        FastMoveUseObserved(
            useId = id,
            attackingSide = ActivePokemonSide.OPPONENT,
            damagedSide = ActivePokemonSide.PLAYER,
            appearanceId = "appearance",
            attackerSpeciesName = "Sneasel",
            evidenceKinds = evidenceKinds,
        )
    ).copy(monotonicTimeNanos = at)

    private fun article(id: String, payload: com.example.overdex.battle.custody.TestimonyPayload) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = 1L,
        recordedAt = 1L,
        sourceId = SourceId("test"),
        payload = payload,
        monotonicTimeNanos = 1L
    )
}
