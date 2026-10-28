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

    @Test fun `resolved ordinary slot keeps Volt Switch when returning after Alolan Spark`() = runBlocking {
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(raichu("Raichu (Alolan)", false), raichu("Raichu", true)))
        engine.accept(article("a-config", PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(article("k-config", PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Thunder"))))
        fun identity(id: String, name: String, slot: Int, at: Long) = article(id,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, name, 26,
                playerTeamSlot = slot, appearanceStartedAtNanos = at)).copy(monotonicTimeNanos = at)
        val alolan = engine.accept(identity("a", "Raichu (Alolan)", 1, 1))
        assertEquals("Spark", (alolan.single().payload as FastMoveIdentified).moveName)
        val ordinary = engine.accept(identity("k", "Raichu", 2, 10))
        assertEquals("Volt Switch", (ordinary.single().payload as FastMoveIdentified).moveName)
        assertTrue(engine.accept(identity("late", "Raichu (Alolan)", 1, 5)).isEmpty())
        assertEquals("Volt Switch", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        val returning = engine.accept(identity("return", "Raichu (Alolan)", 1, 20))
        assertEquals("Spark", (returning.single().payload as FastMoveIdentified).moveName)
        engine.accept(article("a-ko", com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed(
            ActivePokemonSide.PLAYER, "Raichu (Alolan)", 26)).copy(monotonicTimeNanos = 25))
        val surviving = engine.accept(identity("k-survives", "Raichu", 2, 30))
        assertEquals("Volt Switch", (surviving.single().payload as FastMoveIdentified).moveName)
    }

    @Test fun `unresolved same name entry cannot retain previous ordinary forms move`() = runBlocking {
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(raichu("Raichu (Alolan)", false), raichu("Raichu", true)))
        engine.accept(article("a-config", PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(article("k-config", PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Thunder"))))
        engine.accept(article("known", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26,
            playerTeamSlot = 2, appearanceStartedAtNanos = 1)).copy(monotonicTimeNanos = 1))
        val result = engine.accept(article("unknown", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26,
            appearanceStartedAtNanos = 10)).copy(monotonicTimeNanos = 10))
        assertTrue(result.none { it.payload is FastMoveIdentified })
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
    }

    @Test fun `explicit Alolan name selects its pool before shared dex number`() = runBlocking {
        val alolan = raichu("Raichu (Alolan)", false)
        val ordinary = raichu("Raichu", true)
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(alolan, ordinary))
        engine.accept(article("alolan", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, alolan.name, 26)))
        assertEquals(listOf("Spark", "Volt Switch"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
    }

    @Test fun `player roster form survives bare OCR without changing mirror opponent pool`() = runBlocking {
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(raichu("Raichu (Alolan)", false), raichu("Raichu", true)))
        engine.accept(article("roster-form", com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed(
            1, "Raichu (Alolan)", 26, "fALOLA", .2f)))
        engine.accept(article("bare-roster", com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed(1, "Raichu", 26)))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Raichu", 26)))
        assertTrue(engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!.remainingMoveNames.none { it == "Charm" })
        assertTrue("Charm" in engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames)
    }

    private fun raichu(name: String, charm: Boolean) = Pokemon(26, name,
        if (charm) listOf(PokemonType.ELECTRIC) else listOf(PokemonType.ELECTRIC, PokemonType.PSYCHIC), "",
        fastMoves = listOf(move("Spark", 2, 7, PokemonType.ELECTRIC), move("Volt Switch", 4, 16, PokemonType.ELECTRIC)) +
            if (charm) listOf(move("Charm", 3, 6, PokemonType.FAIRY)) else emptyList(), chargedMoves = emptyList())

    @Test fun `two configured Raichu forms do not let bare badge select ordinary slots move`() = runBlocking {
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(raichu("Raichu (Alolan)", false), raichu("Raichu", true)))
        engine.accept(article("alolan-team", PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(article("ordinary-team", PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Thunder"))))
        val result = engine.accept(article("bare-badge", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26)))
        assertTrue(result.none { it.payload is FastMoveIdentified })
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        // Both forms remain possible until evidence identifies which teammate is active.
        assertTrue("Spark" in engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!.remainingMoveNames)
        val explicit = engine.accept(article("form", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu (Alolan)", 26)))
        assertEquals("Spark", (explicit.single().payload as FastMoveIdentified).moveName)
    }

    @Test fun `damage and delayed flashes from two hits are one cadence sample not two independent votes`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        val uses = listOf(
            fastUseAt("loss1", 1_000_000_000L),
            fastUseAt("flash1", 1_400_000_000L, listOf("HP_BORDER_PULSE")),
            fastUseAt("loss2", 2_000_000_000L),
            fastUseAt("flash2", 2_400_000_000L, listOf("HP_BORDER_PULSE")),
        )
        val output = uses.flatMap {
            engine.accept(it.copy(payload = (it.payload as FastMoveUseObserved).copy(attackerSpeciesName = "Sealeo")))
        }
        assertTrue(output.none { it.payload is FastMoveIdentified })
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `post charge motion cannot replace measured Incinerate with Ember but repeated hits can`() = runBlocking {
        val camerupt = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
            fastMoves = listOf(move("Incinerate", 5, 20), move("Ember", 2, 6)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(camerupt))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Camerupt", 323)))
        engine.accept(borderCadence("hit1", 2_450_000_000L).copy(monotonicTimeNanos = 68_000_000_000L))
        engine.accept(borderCadence("hit2", 2_414_816_000L).copy(monotonicTimeNanos = 70_810_000_000L))
        assertEquals("Incinerate", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(article("charge", com.example.overdex.battle.custody.ChargeMoveUsedAnnounced)
            .copy(monotonicTimeNanos = 95_101_000_000L))
        val noise = listOf(101_100_000_000L, 102_145_000_000L).flatMap { at ->
            engine.accept(article("bob-$at", ActiveHpBarMotionCadenceMeasured(
                ActivePokemonSide.OPPONENT, 1_027_627_884L, 20f, 6)).copy(monotonicTimeNanos = at))
        }
        assertTrue(noise.none { (it.payload as? FastMoveIdentified)?.moveName == "Ember" })
        assertEquals("Incinerate", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        // The established move remains refutable by fresh direct evidence.
        engine.accept(borderCadence("new-hit1", 1_000_000_000L).copy(monotonicTimeNanos = 110_000_000_000L))
        engine.accept(borderCadence("new-hit2", 1_000_000_000L).copy(monotonicTimeNanos = 111_000_000_000L))
        assertEquals("Ember", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `repeated Spark spectra correct Tackle and ignore the requesting player cue`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(borderCadence("tackle1", 500_000_000L).copy(monotonicTimeNanos = 2_000_000_000L))
        engine.accept(borderCadence("tackle2", 500_000_000L).copy(monotonicTimeNanos = 2_500_000_000L))
        assertEquals("Tackle", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertTrue(engine.accept(referenceSound("sound1", "Spark", 3_000_000_000L)).isEmpty())
        // Another capture, shifted 400 ms, contains the same reference-aligned occurrence.
        val first = referenceSound("overlap", "Spark", 3_400_000_000L)
        val p = first.payload as FastMoveSoundMeasured
        assertTrue(engine.accept(first.copy(payload = p.copy(referenceCandidates = p.referenceCandidates.map {
            it.copy(referenceOffsetNanos = it.referenceOffsetNanos + 400_000_000L)
        }))).isEmpty())
        val confirmed = engine.accept(referenceSound("sound2", "Spark", 4_000_000_000L)).single()
        assertEquals("Spark", (confirmed.payload as FastMoveIdentified).moveName)
        assertEquals(ActivePokemonSide.OPPONENT, confirmed.payload.side)
        assertTrue(confirmed.predecessorIds.containsAll(listOf(ArticleId("sound1"), ArticleId("sound2"), ArticleId("player"), ArticleId("opponent"))))
        engine.accept(borderCadence("fragment1", 500_000_000L).copy(monotonicTimeNanos = 5_000_000_000L))
        engine.accept(borderCadence("fragment2", 500_000_000L).copy(monotonicTimeNanos = 5_500_000_000L))
        assertEquals("Spark", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertEquals(listOf("Spark"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
    }

    @Test fun `mirror shared sound is retained without awarding both sides a move`() = runBlocking {
        val engine = audioEngine(mirror = true)
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        assertTrue(engine.accept(referenceSound("one", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("two", "Spark", 3_000_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.audioPreference)
    }

    @Test fun `one strong sound reaches HUD with side evidence but no identity or new attack`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        val sound = referenceSound("one", "Spark", 2_000_000_000L)
        assertTrue(engine.accept(sound).isEmpty())
        val snapshot = engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!
        assertEquals("Spark", snapshot.audioPreference?.moveName)
        assertEquals(setOf(ArticleId("one"), ArticleId("player"), ArticleId("opponent")),
            snapshot.audioPreference?.evidenceIds?.toSet())
        assertNull(snapshot.identifiedMoveName)
        assertEquals(setOf("Spark", "Tackle"), snapshot.remainingMoveNames.toSet())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.audioPreference)
        // The raw measurement is still unassigned; ownership is a derived preference.
        assertNull((sound.payload as FastMoveSoundMeasured).attackingSide)
        engine.accept(article("get-ready", com.example.overdex.battle.custody.GetReadyWitnessed)
            .copy(monotonicTimeNanos = 3_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
    }

    @Test fun `higher energy tie-break does not hide alternatives or a lower energy audio favorite`() = runBlocking {
        val player = Pokemon(460, "Abomasnow", listOf(PokemonType.ICE), "Sinnoh",
            fastMoves = listOf(move("Powder Snow", 2, 8, PokemonType.ICE)), chargedMoves = emptyList())
        val opponent = Pokemon(311, "Plusle", listOf(PokemonType.ELECTRIC), "Hoenn",
            fastMoves = listOf(move("Quick Attack", 2, 9), move("Spark", 2, 8, PokemonType.ELECTRIC)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(player, opponent))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, player.name)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, opponent.name)))
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(borderCadence("cadence1", 1_000_000_000L).copy(monotonicTimeNanos = 2_000_000_000L))
        val assumed = engine.accept(borderCadence("cadence2", 1_000_000_000L).copy(monotonicTimeNanos = 3_000_000_000L))
        assertTrue((assumed.single().payload as FastMoveIdentified).basis.contains("TIE_BREAK"))
        assertTrue(engine.accept(referenceSound("spark", "Spark", 4_000_000_000L)).isEmpty())
        val snapshot = engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!
        assertEquals("Quick Attack", snapshot.identifiedMoveName) // The labeled forecast has not been revised.
        assertEquals(setOf("Quick Attack", "Spark"), snapshot.remainingMoveNames.toSet())
        assertEquals("Spark", snapshot.audioPreference?.moveName)
    }

    @Test fun `entry sound before readable badge waits for the incoming species and cites its boundary`() = runBlocking {
        val player = Pokemon(460, "Abomasnow", listOf(PokemonType.ICE), "Sinnoh",
            fastMoves = listOf(move("Powder Snow", 2, 8)), chargedMoves = emptyList())
        val old = Pokemon(364, "Sealeo", listOf(PokemonType.WATER), "Hoenn",
            fastMoves = listOf(move("Water Gun", 1, 3)), chargedMoves = emptyList())
        val incoming = Pokemon(101, "Electrode", listOf(PokemonType.ELECTRIC), "Kanto",
            fastMoves = listOf(move("Spark", 2, 8), move("Tackle", 1, 3)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(player, old, incoming))
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, player.name, player.id)))
        engine.accept(article("old", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, old.name, old.id)))
        engine.accept(entry("incoming-cry", ActivePokemonSide.OPPONENT, 3_000_000_000L))
        assertTrue(engine.accept(referenceSound("early-one", "Spark", 3_100_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("early-two", "Spark", 4_100_000_000L)).isEmpty())
        assertEquals("Sealeo", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.speciesName)
        val result = engine.accept(article("new-badge", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, incoming.name, incoming.id)).copy(monotonicTimeNanos = 5_000_000_000L))
            .single { (it.payload as? FastMoveIdentified)?.basis?.startsWith("AUDIO_REFERENCE") == true }
        assertEquals("Spark", (result.payload as FastMoveIdentified).moveName)
        assertEquals("Electrode", result.payload.speciesName)
        assertTrue(result.predecessorIds.containsAll(listOf("incoming-cry", "new-badge", "early-one", "early-two").map(::ArticleId)))
        // The audio boundary must not loosen stale badge rejection for any other witness.
        engine.accept(article("stale-old", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, old.name, old.id))
            .copy(monotonicTimeNanos = 4_000_000_000L))
        assertEquals("Electrode", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.speciesName)
    }

    @Test fun `an unresolved entry cannot award sounds to the departing species even with a legal move`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(entry("switch", ActivePokemonSide.OPPONENT, 3_000_000_000L))
        assertTrue(engine.accept(referenceSound("one", "Spark", 3_100_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("two", "Spark", 4_100_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("still-unresolved", "Spark", 7_100_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        // A false alarm can be resolved by a fresh confirmation of the unchanged badge.
        val result = engine.accept(article("same-badge", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Electrode", 101))
            .copy(monotonicTimeNanos = 8_500_000_000L)).single()
        assertEquals("Spark", (result.payload as FastMoveIdentified).moveName)
        assertTrue(ArticleId("same-badge") in result.predecessorIds)
    }

    @Test fun `pending sound from before an entry cannot be transferred to the replacement`() = runBlocking {
        val sharedMoves = listOf(move("Spark", 2, 8), move("Tackle", 1, 3))
        val old = Pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), "Kanto",
            fastMoves = sharedMoves, chargedMoves = emptyList())
        val opponent = Pokemon(101, "Electrode", listOf(PokemonType.ELECTRIC), "Kanto",
            fastMoves = sharedMoves, chargedMoves = emptyList())
        val incoming = Pokemon(460, "Abomasnow", listOf(PokemonType.ICE), "Sinnoh",
            fastMoves = listOf(move("Powder Snow", 2, 8)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(old, opponent, incoming))
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(article("old-player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, old.name, old.id)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, opponent.name, opponent.id)))
        assertTrue(engine.accept(referenceSound("old-one", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("old-two", "Spark", 3_000_000_000L)).isEmpty())
        engine.accept(entry("switch", ActivePokemonSide.PLAYER, 4_000_000_000L))
        engine.accept(article("new-badge", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, incoming.name, incoming.id))
            .copy(monotonicTimeNanos = 5_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `unknown player move pool cannot be treated as proof that only opponent can make sound`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(article("unresolved-player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Uncatalogued", null))
            .copy(monotonicTimeNanos = 2_000_000_000L))
        assertTrue(engine.accept(referenceSound("first", "Spark", 3_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("second", "Spark", 4_000_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `music before GO QTE sound and departed fighter clips cannot establish fast move identity`() = runBlocking {
        val engine = audioEngine()
        assertTrue(engine.accept(referenceSound("before1", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("before2", "Spark", 3_000_000_000L)).isEmpty())
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(article("qte", com.example.overdex.battle.custody.GetReadyWitnessed).copy(monotonicTimeNanos = 4_000_000_000L))
        assertTrue(engine.accept(referenceSound("during1", "Spark", 5_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("during2", "Spark", 6_000_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(article("end", com.example.overdex.battle.custody.MatchEnded(com.example.overdex.model.BattleResult.WIN)))
        assertTrue(engine.accept(referenceSound("after", "Spark", 20_000_000_000L)).isEmpty())
    }

    @Test fun `late charged phase enables later reference identity while QTE audio stays excluded`() = runBlocking {
        for (cue in listOf(com.example.overdex.battle.custody.AttackIncoming,
            com.example.overdex.battle.custody.GetReadyWitnessed,
            com.example.overdex.battle.custody.ChargeMoveUsedAnnounced)) {
            val engine = audioEngine()
            assertTrue(engine.accept(referenceSound("before1", "Spark", 1_000_000_000L)).isEmpty())
            assertTrue(engine.accept(referenceSound("before2", "Spark", 1_900_000_000L)).isEmpty())
            assertTrue(engine.accept(article("late-phase", cue).copy(monotonicTimeNanos = 2_000_000_000L)).isEmpty())
            assertTrue(engine.accept(referenceSound("qte1", "Spark", 2_100_000_000L)).isEmpty())
            assertTrue(engine.accept(referenceSound("qte2", "Spark", 2_500_000_000L)).isEmpty())
            assertTrue(engine.accept(referenceSound("after1", "Spark", 16_000_000_000L)).isEmpty())
            val identified = engine.accept(referenceSound("after2", "Spark", 17_000_000_000L)).single()
            assertEquals("Spark", (identified.payload as FastMoveIdentified).moveName)
            assertTrue(identified.predecessorIds.containsAll(listOf(ArticleId("after1"), ArticleId("after2"))))
            assertTrue(identified.predecessorIds.none { it.value.startsWith("before") || it.value.startsWith("qte") })
        }
    }

    @Test fun `persistent combat enables repeated audio identity without GO but not before the loss`() = runBlocking {
        val engine = audioEngine()
        assertTrue(engine.accept(referenceSound("before1", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("before2", "Spark", 3_000_000_000L)).isEmpty())
        engine.accept(article("real-loss", com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured(
            ActivePokemonSide.PLAYER, 1f, .9f, .1f)).copy(monotonicTimeNanos = 4_000_000_000L))
        assertTrue(engine.accept(referenceSound("after1", "Spark", 5_000_000_000L)).isEmpty())
        val identified = engine.accept(referenceSound("after2", "Spark", 6_000_000_000L)).single()
        assertEquals("Spark", (identified.payload as FastMoveIdentified).moveName)
        assertEquals(ActivePokemonSide.OPPONENT, identified.payload.side)
        assertTrue(identified.predecessorIds.containsAll(listOf(ArticleId("after1"), ArticleId("after2"))))
        assertTrue(identified.predecessorIds.none { it.value.startsWith("before") })
        engine.accept(article("qte", com.example.overdex.battle.custody.GetReadyWitnessed)
            .copy(monotonicTimeNanos = 7_000_000_000L))
        assertTrue(engine.accept(referenceSound("during1", "Spark", 8_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("during2", "Spark", 9_000_000_000L)).isEmpty())
    }

    @Test fun `countdown opens observation but cannot identify a pre GO fast move`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("three", com.example.overdex.battle.custody.CountdownGlyphWitnessed("3", .99f, 1)))
        assertTrue(engine.accept(referenceSound("before1", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("before2", "Spark", 3_000_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `VS audio stays observation only and cannot corroborate a later fast move identity`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("vs", com.example.overdex.battle.custody.VsScreenWitnessed))
        assertTrue(engine.accept(referenceSound("vs-sound1", "Spark", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(referenceSound("vs-sound2", "Spark", 3_000_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted)
            .copy(monotonicTimeNanos = 4_000_000_000L))
        assertTrue(engine.accept(referenceSound("battle1", "Spark", 5_000_000_000L)).isEmpty())
        val identified = engine.accept(referenceSound("battle2", "Spark", 6_000_000_000L)).single()
        assertEquals("Spark", (identified.payload as FastMoveIdentified).moveName)
        assertTrue(identified.predecessorIds.containsAll(listOf(ArticleId("battle1"), ArticleId("battle2"))))
        assertTrue(identified.predecessorIds.none { it.value.startsWith("vs") })
    }

    private suspend fun audioEngine(mirror: Boolean = false): FastMoveCadenceInference {
        val opponent = Pokemon(101, "Electrode", listOf(PokemonType.ELECTRIC), "Kanto",
            fastMoves = listOf(move("Spark", 2, 8, PokemonType.ELECTRIC), move("Tackle", 1, 3)), chargedMoves = emptyList())
        val player = Pokemon(460, "Abomasnow", listOf(PokemonType.ICE), "Sinnoh",
            fastMoves = if (mirror) opponent.fastMoves else listOf(move("Powder Snow", 2, 8, PokemonType.ICE)), chargedMoves = emptyList())
        return FastMoveCadenceInference(MapPokemonKnowledge(player, opponent)).also {
            it.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, player.name, player.id)))
            it.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, opponent.name, opponent.id)))
        }
    }

    @Test fun `repeated ambiguous audio favors a HUD candidate without identity energy or removing alternatives`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        assertTrue(engine.accept(weakReferenceSound("weak1", 2_000_000_000L)).isEmpty())
        assertTrue(engine.accept(weakReferenceSound("weak2", 3_500_000_000L)).isEmpty())
        val snapshot = engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!
        assertEquals("Spark", snapshot.audioPreference?.moveName)
        assertEquals(setOf(ArticleId("weak1"), ArticleId("weak2"), ArticleId("player"), ArticleId("opponent")),
            snapshot.audioPreference?.evidenceIds?.toSet())
        assertNull(snapshot.identifiedMoveName)
        assertEquals(setOf("Spark", "Tackle"), snapshot.remainingMoveNames.toSet())
        engine.accept(article("get-ready", com.example.overdex.battle.custody.GetReadyWitnessed).copy(monotonicTimeNanos = 4_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
        engine.accept(weakReferenceSound("qte1", 5_000_000_000L))
        engine.accept(weakReferenceSound("qte2", 6_500_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
    }

    @Test fun `a weak preference expires and later confirmed audio replaces the low confidence status`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        engine.accept(weakReferenceSound("weak1", 2_000_000_000L)); engine.accept(weakReferenceSound("weak2", 3_500_000_000L))
        engine.accept(article("tick", com.example.overdex.battle.custody.RawTestimony("next frame")).copy(monotonicTimeNanos = 10_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
        engine.accept(referenceSound("strong1", "Spark", 11_000_000_000L))
        assertEquals("Spark", (engine.accept(referenceSound("strong2", "Spark", 12_000_000_000L)).single().payload as FastMoveIdentified).moveName)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.audioPreference)
    }

    private fun weakReferenceSound(id: String, at: Long): RealityArticle {
        val original = referenceSound(id, "Spark", at)
        val sound = original.payload as FastMoveSoundMeasured
        val best = sound.referenceCandidates.first().copy(similarity = .89f, changingSimilarity = .74f)
        return original.copy(payload = sound.copy(referenceMatchAccepted = false, referenceCandidates = listOf(
            best, best.copy(moveName = "Tackle", similarity = .82f, changingSimilarity = .68f),
            best.copy(moveName = "Powder Snow", similarity = .81f, changingSimilarity = .67f))))
    }

    private fun referenceSound(id: String, name: String, at: Long): RealityArticle {
        val candidate = com.example.overdex.battle.custody.FastMoveSoundCandidate(name, "reference-hash", .98f, .94f,
            100_000_000L, 0, 416_000_000L)
        return article(id, FastMoveSoundMeasured(false, null, null, null, .3f,
            cueAttackingSide = ActivePokemonSide.PLAYER,
            analysisVersion = com.example.overdex.battle.audio.FastMoveReferenceMatcher.VERSION,
            referenceMatchAccepted = true,
            referenceCandidates = listOf(candidate, candidate.copy(moveName = "Other", similarity = .8f, changingSimilarity = .7f))))
            .copy(monotonicTimeNanos = at)
    }

    @Test fun `Seed Bomb aftermath cannot replace Gourgeist Incinerate with Razor Leaf`() = runBlocking {
        val gourgeist = Pokemon(711, "Gourgeist", listOf(PokemonType.GHOST, PokemonType.GRASS), "Kalos",
            fastMoves = listOf(move("Incinerate", 5, 20), move("Razor Leaf", 2, 4, PokemonType.GRASS)),
            chargedMoves = emptyList())
        val sealeo = Pokemon(364, "Sealeo", listOf(PokemonType.WATER, PokemonType.ICE), "Hoenn",
            fastMoves = listOf(move("Powder Snow", 2, 8, PokemonType.ICE)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(MapPokemonKnowledge(gourgeist, sealeo))
        engine.accept(article("player", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711)))
        engine.accept(article("opponent", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(playerImpactCadence("bob1", 2_500_000_000L).copy(monotonicTimeNanos = 50_000_000_000L))
        engine.accept(playerImpactCadence("bob2", 2_500_000_000L).copy(monotonicTimeNanos = 52_500_000_000L))
        assertEquals("Incinerate", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        engine.accept(article("seed", com.example.overdex.battle.custody.RawTestimony("Gourgeist used Seed Bomb!"))
            .copy(sourceId = SourceId("ANNOUNCEMENT_WITNESS"), monotonicTimeNanos = 63_270_000_000L))
        val lingering = article("seed-effect", FastMoveEffectivenessWitnessed(
            FastMoveEffectiveness.SUPER_EFFECTIVE, ActivePokemonSide.OPPONENT, "SUPER EFFECTIVE!"
        )).copy(monotonicTimeNanos = 65_190_000_000L)
        assertTrue(engine.accept(lingering).isEmpty())
        assertEquals("Incinerate", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        // Future non-charge text is still eligible. The guard does not ban the witness.
        assertTrue(engine.accept(lingering.copy(id = ArticleId("later-fast-text"), monotonicTimeNanos = 70_000_000_000L)).isNotEmpty())
    }

    @Test
    fun `identity remains available throughout a badge recheck`() = runBlocking {
        val pokemon = Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
            fastMoves = listOf(move("Water Gun", 1, 3)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Vaporeon", 134)))
        engine.accept(entry("scan", ActivePokemonSide.OPPONENT, 2_000_000_000L))
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        engine.accept(article("faint", com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed(
            ActivePokemonSide.OPPONENT, "Vaporeon", 134, "BALL_COUNT_DECREASE"
        )).copy(monotonicTimeNanos = 3_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))
        assertTrue(engine.accept(article("lingering-badge", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Vaporeon", 134
        )).copy(monotonicTimeNanos = 3_400_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT))
    }

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

        assertEquals(0, engine.accept(playerImpactCadence("c1", 2_450_000_000L)).size)
        val resolved = engine.accept(playerImpactCadence("c2", 2_510_000_000L))

        assertEquals(1, resolved.count { it.payload is FastMoveIdentified })
        assertEquals(
            ActivePokemonSide.PLAYER,
            (resolved.first { it.payload is FastMoveIdentified }.payload as FastMoveIdentified).side
        )
        val next = engine.accept(playerImpactCadence("c4", 2_490_000_000L))
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
        engine.accept(playerImpactCadence("f1", 463_000_000L))
        engine.accept(playerImpactCadence("noise", 1_962_000_000L))
        val resolved = engine.accept(playerImpactCadence("f2", 367_000_000L))

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
    fun `recipient visual cadence supports the attacking side but requires independent corroboration`() = runBlocking {
        val pokemon = Pokemon(
            id = 364,
            name = "Sealeo",
            types = listOf(PokemonType.WATER, PokemonType.ICE),
            region = "Hoenn",
            fastMoves = listOf(move("Water Gun", 1, 3), move("Powder Snow", 2, 8)),
            chargedMoves = emptyList()
        )
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))

        assertEquals(0, engine.accept(visualCadence("v0", 500_000_000L)).size)
        assertTrue(engine.accept(visualCadence("v1", 510_000_000L)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertEquals(listOf("Water Gun"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER))
        assertTrue(engine.accept(borderCadence("b1", 510_000_000L)).isEmpty())
        val resolved = engine.accept(borderCadence("b2", 500_000_000L))

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
    fun `prebattle move edits replace a previously identified configured move`() = runBlocking {
        val pokemon = Pokemon(711, "Gourgeist", listOf(PokemonType.GHOST), "Kalos",
            fastMoves = listOf(move("Razor Leaf", 2, 4), move("Incinerate", 5, 20)), chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("old", PlayerTeamSlotConfigured(1, "Gourgeist", 711, "Razor Leaf", listOf("Seed Bomb"))))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711)))
        val result = engine.accept(article("edit", PlayerTeamSlotConfigured(1, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb"))))
        assertEquals("Incinerate", (result.single().payload as FastMoveIdentified).moveName)
        engine.accept(article("another-slot", PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Surf"))))
        assertEquals("Incinerate", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
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
        assertTrue(engine.accept(borderCadence("hit", 500_000_000L).copy(monotonicTimeNanos = 10_000_000_000L)).isEmpty())
        val result = engine.accept(article("motion", ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6,
        )).copy(monotonicTimeNanos = 10_000_000_000L)).single()
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
        engine.accept(borderCadence("actual-half-second-hit", 500_000_000L).copy(monotonicTimeNanos = 10_000_000_000L))
        engine.accept(article("m1", ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6)).copy(monotonicTimeNanos = 10_000_000_000L))
        engine.accept(article("m2", ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6)).copy(monotonicTimeNanos = 10_000_000_000L))
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
    fun `configured form name resolves its exact reference from a bare badge`() = runBlocking {
        val pokemon = Pokemon(711, "Gourgeist (Average)", listOf(PokemonType.GHOST, PokemonType.GRASS), "Kalos",
            fastMoves = listOf(move("Incinerate", 5, 20), move("Razor Leaf", 2, 4, PokemonType.GRASS)),
            chargedMoves = emptyList())
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(pokemon))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711, "Incinerate", listOf("Shadow Ball"))))
        val identity = engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711)))
            .single().payload as FastMoveIdentified
        assertEquals("Incinerate", identity.moveName)
        assertEquals("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES", identity.basis)
    }

    @Test fun `unavailable form reference never borrows moves from a shared dex number`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(raichu("Raichu", true)))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Charm", listOf("Thunder Punch"))))
        assertTrue(engine.accept(article("badge", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26))).isEmpty())
        assertTrue(engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!.remainingMoveNames.isEmpty())
    }

    @Test
    fun `configured identity stays sufficient for energy after cadence corroboration`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        val energy = FastMoveEnergyInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Sealeo", 364, "Water Gun", listOf("Surf"))))
        val initial = engine.accept(article("ours", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Sealeo", 364))).single()
        energy.accept(article("initial", initial.payload))
        engine.accept(playerImpactCadence("c1", 500_000_000L))
        val refined = engine.accept(playerImpactCadence("c2", 500_000_000L)).single()
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
    fun `missed half second hits cannot turn Water Gun into Powder Snow after a charge`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(borderCadence("fast1", 500_000_000L).copy(monotonicTimeNanos = 2_000_000_000L))
        engine.accept(borderCadence("fast2", 500_000_000L).copy(monotonicTimeNanos = 2_500_000_000L))
        engine.accept(article("charge", com.example.overdex.battle.custody.ChargeMoveUsedAnnounced)
            .copy(monotonicTimeNanos = 3_000_000_000L))
        val later = (1..5).flatMap { index ->
            engine.accept(borderCadence("missed-$index", 1_000_000_000L)
                .copy(monotonicTimeNanos = 5_000_000_000L + index * 1_000_000_000L))
        }
        assertTrue(later.none { (it.payload as? FastMoveIdentified)?.moveName == "Powder Snow" })
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `pulse and fused hit are not independent proof of a slower move`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(borderCadence("fast1", 500_000_000L).copy(monotonicTimeNanos = 2_000_000_000L))
        engine.accept(borderCadence("fast2", 500_000_000L).copy(monotonicTimeNanos = 2_500_000_000L))
        repeat(5) { index ->
            val at = 20_000_000_000L + index * 1_000_000_000L
            engine.accept(borderCadence("pulse-$index", 1_000_000_000L).copy(monotonicTimeNanos = at))
            engine.accept(fastUseAt("hit-$index", at))
        }
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test
    fun `repeated independent slower motion and impact can correct a noisy fast identification`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        engine.accept(borderCadence("fast1", 500_000_000L).copy(monotonicTimeNanos = 2_000_000_000L))
        engine.accept(borderCadence("fast2", 500_000_000L).copy(monotonicTimeNanos = 2_500_000_000L))
        repeat(3) { index ->
            engine.accept(borderCadence("slow-$index", 1_000_000_000L)
                .copy(monotonicTimeNanos = 20_000_000_000L + index * 1_000_000_000L))
        }
        engine.accept(article("motion1", ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.OPPONENT, 1_000_000_000L, 20f, 6))
            .copy(monotonicTimeNanos = 21_000_000_000L))
        assertEquals("Water Gun", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        val corrected = engine.accept(article("motion2", ActiveHpBarMotionCadenceMeasured(
            ActivePokemonSide.OPPONENT, 1_000_000_000L, 20f, 6))
            .copy(monotonicTimeNanos = 22_000_000_000L))
        assertEquals("Powder Snow", (corrected.single().payload as FastMoveIdentified).moveName)
        assertTrue(corrected.single().predecessorIds.containsAll(listOf(ArticleId("motion1"), ArticleId("motion2"))))
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

    @Test fun `unidentified sparse impacts retain both speeds until independent motion resolves the slower move`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        for (index in 0..4) {
            val at = 10_000_000_000L + index * 1_000_000_000L
            assertTrue(engine.accept(borderCadence("pulse-$index", 1_000_000_000L).copy(monotonicTimeNanos = at)).isEmpty())
            assertTrue(engine.accept(fastUseAt("same-impact-$index", at)).isEmpty())
        }
        assertEquals(setOf("Water Gun", "Powder Snow"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
        // Repeated motion is independent of the pulse/use stream.
        for (index in 0..1) engine.accept(article("motion-$index",
            ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000L, 20f, 6))
            .copy(monotonicTimeNanos = 13_000_000_000L + index * 1_000_000_000L))
        assertEquals("Powder Snow", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `compatible gaps alone cannot confirm the faster move or supply its second cadence sample`() = runBlocking {
        val engine = FastMoveCadenceInference(SinglePokemonKnowledge(testSealeo()))
        engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        for (index in 0..2) engine.accept(borderCadence("gap-$index", 2_000_000_000L)
            .copy(monotonicTimeNanos = 10_000_000_000L + index * 2_000_000_000L))
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        assertTrue(engine.accept(article("motion1",
            ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6))
            .copy(monotonicTimeNanos = 15_000_000_000L)).isEmpty())
        val unresolved = engine.accept(article("motion2",
            ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, 500_000_000L, 20f, 6))
            .copy(monotonicTimeNanos = 15_500_000_000L))
        assertTrue(unresolved.isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
    }

    @Test fun `reference audio resolves sparse impact ambiguity without shortening the recorded gaps`() = runBlocking {
        val engine = audioEngine()
        engine.accept(article("go", com.example.overdex.battle.custody.MatchStarted))
        for (index in 0..2) engine.accept(borderCadence("gap-$index", 1_000_000_000L)
            .copy(monotonicTimeNanos = 10_000_000_000L + index * 1_000_000_000L))
        assertEquals(setOf("Tackle", "Spark"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
        assertTrue(engine.accept(referenceSound("sound1", "Spark", 13_000_000_000L)).isEmpty())
        val identity = engine.accept(referenceSound("sound2", "Spark", 14_000_000_000L)).single()
        assertEquals("Spark", (identity.payload as FastMoveIdentified).moveName)
        assertTrue(identity.payload.basis.startsWith("AUDIO_REFERENCE"))
        assertTrue(identity.predecessorIds.containsAll(listOf(ArticleId("sound1"), ArticleId("sound2"))))
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

    private fun playerImpactCadence(id: String, intervalNanos: Long) = article(id,
        ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.OPPONENT, intervalNanos, 0.3f, 6))

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
            attackerSpeciesName = null,
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
