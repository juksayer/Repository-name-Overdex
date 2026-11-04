package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FastMoveAudioCandidateContextTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val ember = move("Ember", PokemonType.FIRE)
    private val peck = move("Peck", PokemonType.FLYING)
    private val wingAttack = move("Wing Attack", PokemonType.FLYING)
    private val airSlash = move("Air Slash", PokemonType.FLYING)
    private val spark = move("Spark", PokemonType.ELECTRIC)
    private val charm = move("Charm", PokemonType.FAIRY)
    private val voltSwitch = move("Volt Switch", PokemonType.ELECTRIC)
    private val shadowClaw = move("Shadow Claw", PokemonType.GHOST)
    private val hex = move("Hex", PokemonType.GHOST)
    private val poisonSting = move("Poison Sting", PokemonType.POISON)
    private val infestation = move("Infestation", PokemonType.BUG)
    private val combusken = pokemon(256, "Combusken", listOf(PokemonType.FIRE, PokemonType.FIGHTING), ember, peck)
    private val golbat = pokemon(42, "Golbat", listOf(PokemonType.POISON, PokemonType.FLYING), wingAttack)
    private val gengar = pokemon(94, "Gengar", listOf(PokemonType.GHOST, PokemonType.POISON), shadowClaw, hex)
    private val drapion = pokemon(452, "Drapion", listOf(PokemonType.POISON, PokemonType.DARK), poisonSting, infestation)
    private val skarmory = pokemon(227, "Skarmory", listOf(PokemonType.STEEL, PokemonType.FLYING), airSlash)
    private val raichu = pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), spark, charm)
    private val alolan = pokemon(26, "Raichu (Alolan)", listOf(PokemonType.ELECTRIC, PokemonType.PSYCHIC), spark, voltSwitch)
    private val catalogue = listOf(combusken, golbat, gengar, drapion, skarmory, raichu, alolan)

    private fun move(name: String, type: PokemonType) = Move(name, type, 5, 8, true, 2)
    private fun pokemon(id: Int, name: String, types: List<PokemonType>, vararg moves: Move) =
        Pokemon(id, name, types, "Test", fastMoves = moves.toList(), chargedMoves = emptyList())
    private fun knowledge(entries: List<Pokemon> = catalogue) = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = entries.singleOrNull { it.name == name }
        override suspend fun getPokemonById(id: Int) = entries.firstOrNull { it.id == id }
        override suspend fun getAllSpeciesNames() = entries.map { it.name }.toSet()
    }
    private fun article(id: String, at: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), at / 1_000_000, at / 1_000_000, SourceId("TEST"), payload, monotonicTimeNanos = at)
    private fun at(seconds: Long) = seconds * 1_000_000_000L
    private fun species(side: ActivePokemonSide, pokemon: Pokemon, seconds: Long, id: String = "${side}-${pokemon.name}-$seconds",
        slot: Int? = null, began: Long? = null) = article(id, at(seconds),
        ActivePokemonSpeciesWitnessed(side, pokemon.name, pokemon.id, playerTeamSlot = slot,
            appearanceStartedAtNanos = began))
    private fun config(seconds: Long, slot: Int, pokemon: Pokemon, fast: String, id: String = "config-$slot-$seconds") =
        article(id, at(seconds), PlayerTeamSlotConfigured(slot, pokemon.name, pokemon.id, fast, listOf("Test Charge")))
    private fun cue(seconds: Long, reason: String, side: ActivePokemonSide = opponent) = article("$reason-$seconds", at(seconds + 5),
        SpeciesCheckMeasured(side, seconds, "OPENED", reason, at(seconds), 0, at(2)))
    private suspend fun snapshot(context: FastMoveAudioCandidateContext, seconds: Long = 10) =
        requireNotNull(context.snapshot(at(seconds), at(seconds) + 1_200_000_000L))

    @Test fun `Combusken search includes Ember and Peck alongside only the current player move`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, golbat, "Wing Attack"))
        context.observe(species(player, golbat, 3))
        context.observe(species(opponent, combusken, 4))
        val result = snapshot(context)
        assertEquals(setOf("Ember", "Peck", "Wing Attack"), result.allowedMoveNames)
        assertEquals(setOf("Ember", "Peck"), result.sideMoves[opponent])
        assertEquals(setOf("Wing Attack"), result.sideMoves[player])
        assertFalse("Another catalogue species cannot contribute Air Slash", "Air Slash" in result.allowedMoveNames)
        assertTrue(result.allActiveSidesKnown)
        assertEquals("Combusken", result.sideSpeciesNames[opponent])
        assertEquals(listOf("OPPONENT-Combusken-4"), result.sideEvidenceIds[opponent])
        assertTrue("config-1-1" in result.evidenceIds)
    }

    @Test fun `no active identity means no search and one known side never claims complete context`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        assertNull(context.snapshot(at(10), at(12)))
        context.observe(config(1, 1, golbat, "Wing Attack"))
        assertNull("A configured roster is not an active appearance", context.snapshot(at(10), at(12)))
        context.observe(species(opponent, combusken, 3))
        val partial = snapshot(context)
        assertEquals(setOf("Ember", "Peck"), partial.allowedMoveNames)
        assertFalse(partial.allActiveSidesKnown)
        assertFalse(player in partial.sideMoves)
    }

    @Test fun `unknown successor never inherits departed species dex or moves`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        context.observe(article("unknown", at(8), ActivePokemonSpeciesWitnessed(opponent, "Unreadable")))
        assertNull(context.snapshot(at(10), at(12)))
    }

    @Test fun `ordinary and regional opponent pools stay possible until witnessed type icons narrow form`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, raichu, 3))
        assertEquals(setOf("Spark", "Charm", "Volt Switch"), snapshot(context).allowedMoveNames)
        context.observe(article("psychic-icons", at(12), ActivePokemonTypesWitnessed(opponent,
            listOf(PokemonType.ELECTRIC, PokemonType.PSYCHIC), .99f)))
        val narrowed = snapshot(context, 14)
        assertEquals(setOf("Spark", "Volt Switch"), narrowed.allowedMoveNames)
        assertEquals("Raichu (Alolan)", narrowed.sideSpeciesNames[opponent])
        assertTrue("psychic-icons" in narrowed.evidenceIds)
        assertNull(context.snapshot(at(11), at(13)))
    }

    @Test fun `recorded contradictory player types retain configured legal moves and source time boundaries`() = runBlocking {
        for ((pokemon, configuredMove, wrongType) in listOf(
            Triple(golbat, wingAttack.name, PokemonType.WATER),
            Triple(gengar, shadowClaw.name, PokemonType.WATER),
            Triple(drapion, poisonSting.name, PokemonType.ELECTRIC),
        )) {
            val context = FastMoveAudioCandidateContext(knowledge())
            context.observe(config(1, 1, pokemon, configuredMove))
            context.observe(species(player, pokemon, 3, slot = 1))
            context.observe(species(opponent, combusken, 4))
            val before = snapshot(context)
            val typeId = "contradictory-${pokemon.name}-type"
            context.observe(article(typeId, at(12), ActivePokemonTypesWitnessed(player, listOf(wrongType), .99f)))

            assertFalse("${pokemon.name}: a new type reading invalidates earlier async work", context.isCurrent(before))
            assertNull("${pokemon.name}: changed context cannot label crossed PCM", context.snapshot(at(11), at(13)))
            val after = snapshot(context, 14)
            assertEquals(before.sideMoves, after.sideMoves)
            assertEquals(pokemon.name, after.sideSpeciesNames[player])
            assertEquals(setOf(configuredMove), after.sideMoves[player])
            assertEquals(setOf("Ember", "Peck", configuredMove), after.allowedMoveNames)
            assertTrue("${pokemon.name}: both legal pools remain available", after.allActiveSidesKnown)
            assertTrue("${pokemon.name}: preserve conflicting raw evidence", typeId in after.sideEvidenceIds.getValue(player))
        }
    }

    @Test fun `contradictory opponent types preserve known form alternatives without inventing candidates`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, raichu, 3))
        context.observe(article("water-icons", at(5), ActivePokemonTypesWitnessed(opponent,
            listOf(PokemonType.WATER), .99f)))
        val result = snapshot(context)
        assertEquals(setOf("Spark", "Charm", "Volt Switch"), result.allowedMoveNames)
        assertEquals("Raichu", result.sideSpeciesNames[opponent])
        assertFalse(result.allActiveSidesKnown)
        assertTrue("water-icons" in result.sideEvidenceIds.getValue(opponent))
    }

    @Test fun `contradictory types cannot revive an unknown player or apply a departed species configuration`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, golbat, "Wing Attack"))
        context.observe(article("types-without-player", at(2), ActivePokemonTypesWitnessed(player,
            listOf(PokemonType.WATER), .99f)))
        assertNull("Types and configuration are not an active identity", context.snapshot(at(3), at(4)))
        context.observe(species(player, golbat, 5, slot = 1))
        context.observe(species(opponent, combusken, 6))
        context.observe(cue(12, "SWITCH", player))
        context.observe(article("types-after-switch", at(13), ActivePokemonTypesWitnessed(player,
            listOf(PokemonType.WATER), .99f)))
        val unknown = snapshot(context, 14)
        assertEquals(setOf("Ember", "Peck"), unknown.allowedMoveNames)
        assertFalse(unknown.allActiveSidesKnown)
        assertFalse(player in unknown.sideMoves)

        context.observe(species(player, gengar, 18))
        context.observe(article("gengar-water", at(19), ActivePokemonTypesWitnessed(player,
            listOf(PokemonType.WATER), .99f)))
        val successor = snapshot(context, 20)
        assertEquals(setOf("Shadow Claw", "Hex"), successor.sideMoves[player])
        assertFalse("Wing Attack" in successor.allowedMoveNames)
        assertFalse("Departed configuration cannot support the new species", "config-1-1" in successor.evidenceIds)
    }

    @Test fun `unknown regional reference does not fall back to ordinary dex`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(article("unknown-form", at(3),
            ActivePokemonSpeciesWitnessed(opponent, "Raichu (Unknown)", 26)))
        context.observe(article("unknown-form-types", at(5), ActivePokemonTypesWitnessed(opponent,
            listOf(PokemonType.WATER), .99f)))
        assertNull(context.snapshot(at(10), at(12)))
    }

    @Test fun `incomplete family knowledge cannot manufacture exhaustive legal candidates`() = runBlocking {
        val incomplete = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = raichu.takeIf { name == it.name }
            override suspend fun getPokemonById(id: Int) = raichu.takeIf { id == it.id }
            override suspend fun getAllSpeciesNames() = setOf(raichu.name, alolan.name)
        }
        val context = FastMoveAudioCandidateContext(incomplete)
        context.observe(species(opponent, raichu, 3))
        context.observe(article("incomplete-form-types", at(5), ActivePokemonTypesWitnessed(opponent,
            listOf(PokemonType.WATER), .99f)))
        assertNull(context.snapshot(at(10), at(12)))
    }

    @Test fun `configuration narrows player only when legal and never narrows opponent mirror`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, raichu, "Spark"))
        context.observe(species(player, raichu, 3, slot = 1))
        context.observe(species(opponent, raichu, 4))
        var result = snapshot(context)
        assertEquals(setOf("Spark"), result.sideMoves[player])
        assertEquals(setOf("Spark", "Charm", "Volt Switch"), result.sideMoves[opponent])
        context.observe(config(12, 1, raichu, "Ember"))
        context.observe(article("contradictory-player-types", at(13), ActivePokemonTypesWitnessed(player,
            listOf(PokemonType.WATER), .99f)))
        result = snapshot(context, 14)
        assertEquals("Impossible configured moves cannot enter the audio pool", setOf("Spark", "Charm"), result.sideMoves[player])
        assertFalse("Ember" in result.allowedMoveNames)
    }

    @Test fun `two same species copies with different configured moves require active slot`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, raichu, "Spark"))
        context.observe(config(2, 2, raichu, "Charm"))
        context.observe(species(player, raichu, 3))
        assertEquals(setOf("Spark", "Charm"), snapshot(context).sideMoves[player])
        context.observe(article("resolved-slot", at(12), PlayerActiveIdentityResolved("Raichu", "Raichu", 26, 2,
            at(12), "SLOT_OBSERVED")))
        assertEquals(setOf("Charm"), snapshot(context, 14).sideMoves[player])
    }

    @Test fun `bare player badge without roster preserves form alternatives`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(player, raichu, 3))
        assertEquals(setOf("Spark", "Charm", "Volt Switch"), snapshot(context).sideMoves[player])
    }

    @Test fun `resolved regional player survives repeated raw bare badge without losing slot`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, alolan, "Volt Switch"))
        context.observe(article("resolved", at(3), PlayerActiveIdentityResolved("Raichu", alolan.name, 26, 1,
            at(3), "SPRITE_FORM")))
        context.observe(species(player, raichu, 5))
        context.observe(article("regional-player-water", at(7), ActivePokemonTypesWitnessed(player,
            listOf(PokemonType.WATER), .99f)))
        val result = snapshot(context)
        assertEquals(setOf("Volt Switch"), result.sideMoves[player])
        assertEquals(alolan.name, result.sideSpeciesNames[player])
        assertFalse("Contradictory types cannot restore ordinary-form moves", "Charm" in result.allowedMoveNames)
    }

    @Test fun `resolved ordinary successor replaces regional form even without an entry cue`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, alolan, "Volt Switch"))
        context.observe(config(2, 2, raichu, "Charm"))
        context.observe(article("alolan-resolved", at(3), PlayerActiveIdentityResolved("Raichu", alolan.name, 26, 1,
            at(3), "SPRITE_FORM")))
        val before = snapshot(context)
        assertEquals(setOf("Volt Switch"), before.sideMoves[player])
        context.observe(article("ordinary-resolved", at(12), PlayerActiveIdentityResolved("Raichu", raichu.name, 26, 2,
            at(12), "SPRITE_FORM")))
        assertFalse(context.isCurrent(before))
        assertNull(context.snapshot(at(11), at(13)))
        context.observe(species(player, raichu, 14))
        val after = snapshot(context, 16)
        assertEquals(setOf("Charm"), after.sideMoves[player])
        assertEquals("Raichu", after.sideSpeciesNames[player])
        assertTrue("ordinary-resolved" in after.sideEvidenceIds.getValue(player))
        assertFalse("alolan-resolved" in after.sideEvidenceIds.getValue(player))
    }

    @Test fun `later dex evidence cites its source without starting another appearance`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(article("name-only", at(3), ActivePokemonSpeciesWitnessed(opponent, "Combusken")))
        context.observe(species(opponent, combusken, 5, "dex-confirmed"))
        val result = snapshot(context)
        assertEquals(setOf("Ember", "Peck"), result.sideMoves[opponent])
        assertTrue("name-only" in result.sideEvidenceIds.getValue(opponent))
        assertTrue("dex-confirmed" in result.sideEvidenceIds.getValue(opponent))
    }

    @Test fun `same species repeated confirmation does not invalidate an in flight snapshot`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        val prior = snapshot(context)
        val generation = context.generation
        context.observe(species(opponent, combusken, 10))
        assertEquals(generation, context.generation)
        assertTrue(context.isCurrent(prior))
        assertEquals(prior.allowedMoveNames, snapshot(context).allowedMoveNames)
    }

    @Test fun `switch windows cannot mix predecessor and successor and historical source ordering remains correct`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        val prior = snapshot(context)
        context.observe(species(opponent, skarmory, 12))
        assertFalse(context.isCurrent(prior))
        assertNull(context.snapshot(at(11), at(13)))
        assertEquals(setOf("Ember", "Peck"), snapshot(context, 10).allowedMoveNames)
        assertEquals(setOf("Air Slash"), snapshot(context, 14).allowedMoveNames)
        context.observe(species(opponent, combusken, 6, id = "late-old-badge"))
        assertEquals(setOf("Air Slash"), snapshot(context, 14).allowedMoveNames)
    }

    @Test fun `boundary exactly at window end is outside window while start belongs to successor`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        context.observe(species(opponent, skarmory, 12))
        assertEquals(setOf("Ember", "Peck"), requireNotNull(context.snapshot(at(11), at(12))).allowedMoveNames)
        assertEquals(setOf("Air Slash"), requireNotNull(context.snapshot(at(12), at(13))).allowedMoveNames)
        assertNull(context.snapshot(at(12), at(12)))
    }

    @Test fun `entry switch faint and cry cues invalidate stale context until a fresh identity`() = runBlocking {
        for (reason in listOf("ENTRY", "SWITCH", "FAINT", "CRY", "EMPTY_HP")) {
            val context = FastMoveAudioCandidateContext(knowledge())
            context.observe(species(opponent, combusken, 3))
            val prior = snapshot(context)
            context.observe(cue(12, reason))
            assertFalse("$reason", context.isCurrent(prior))
            assertNull("$reason source trigger, not delayed delivery", context.snapshot(at(13), at(14)))
            assertNull(context.snapshot(at(11), at(13)))
            context.observe(species(opponent, combusken, 18))
            assertEquals(setOf("Ember", "Peck"), snapshot(context, 20).allowedMoveNames)
            assertTrue("$reason-12" in snapshot(context, 20).evidenceIds)
        }
    }

    @Test fun `faint removes legal pool and stale named faint cannot remove successor`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        context.observe(article("faint", at(12), ActivePokemonFaintedWitnessed(opponent, "Combusken")))
        assertNull(context.snapshot(at(13), at(14)))
        context.observe(species(opponent, skarmory, 15))
        context.observe(article("stale-faint", at(17), ActivePokemonFaintedWitnessed(opponent, "Combusken")))
        assertEquals(setOf("Air Slash"), snapshot(context, 20).allowedMoveNames)
    }

    @Test fun `configured move change rejects crossed PCM and invalidates async work`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, raichu, "Spark"))
        context.observe(species(player, raichu, 3, slot = 1))
        val prior = snapshot(context)
        context.observe(config(12, 1, raichu, "Charm"))
        assertFalse(context.isCurrent(prior))
        assertNull(context.snapshot(at(11), at(13)))
        assertEquals(setOf("Charm"), snapshot(context, 14).allowedMoveNames)
        assertEquals(setOf("Spark"), snapshot(context, 10).allowedMoveNames)
    }

    @Test fun `new confirmed scan cannot reuse old owned copy move even if species matches`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, raichu, "Spark"))
        context.observe(species(player, raichu, 3, slot = 1))
        context.observe(article("new-scan", at(12), PlayerTeamRosterConfirmation(true, listOf("Raichu", null, null))))
        context.observe(species(player, raichu, 14, slot = 1))
        context.observe(config(5, 1, raichu, "Charm", "delayed-pre-scan-config"))
        assertEquals(setOf("Spark", "Charm"), snapshot(context, 16).sideMoves[player])
        assertFalse("config-1-1" in snapshot(context, 16).evidenceIds)
    }

    @Test fun `same instant configuration and player species cannot bypass scan invalidation`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(config(1, 1, raichu, "Spark"))
        context.observe(species(player, raichu, 3, slot = 1))
        context.observe(article("new-scan", at(12), PlayerTeamRosterConfirmation(true, listOf("Raichu", null, null))))
        context.observe(config(12, 1, raichu, "Charm"))
        context.observe(species(player, raichu, 12, slot = 1))
        assertNull(context.snapshot(at(13), at(14)))
        context.observe(species(player, raichu, 14, slot = 1))
        assertEquals(setOf("Spark", "Charm"), snapshot(context, 16).sideMoves[player])
        assertFalse("config-1-12" in snapshot(context, 16).evidenceIds)
    }

    @Test fun `restart stop and result prevent prior context from escaping into next session`() = runBlocking {
        val context = FastMoveAudioCandidateContext(knowledge())
        context.observe(species(opponent, combusken, 3))
        val prior = snapshot(context)
        context.observe(article("stop", at(12), ObservationSessionStoppedByUser))
        assertFalse(context.isCurrent(prior))
        assertNull(context.snapshot(at(13), at(14)))
        context.observe(article("restart", at(15), ObservationRestartedByUser))
        assertNull(context.snapshot(at(16), at(17)))
        context.observe(species(opponent, skarmory, 18))
        assertEquals(setOf("Air Slash"), snapshot(context, 20).allowedMoveNames)
    }

    @Test fun `reference lookup finishing after a context change cannot return a stale snapshot`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayed = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? {
                started.complete(Unit)
                release.await()
                return catalogue.singleOrNull { it.name == name }
            }
            override suspend fun getPokemonById(id: Int) = catalogue.firstOrNull { it.id == id }
            override suspend fun getAllSpeciesNames() = catalogue.map { it.name }.toSet()
        }
        val context = FastMoveAudioCandidateContext(delayed)
        context.observe(species(opponent, combusken, 3))
        val loading = async { context.snapshot(at(10), at(11)) }
        started.await()
        context.observe(species(opponent, skarmory, 12))
        release.complete(Unit)
        assertNull(loading.await())
        assertEquals(setOf("Air Slash"), snapshot(context, 14).allowedMoveNames)
    }
}
