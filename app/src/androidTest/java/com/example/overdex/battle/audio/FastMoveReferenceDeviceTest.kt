package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FastMoveReferenceDeviceTest {
    @Test fun recordedSpectraAreSeparatedOnTheMoondropWithoutBlockingCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val matcher = FastMoveSoundReferences.load(instrumentation.targetContext)
        val assets = instrumentation.context.assets
        val entries = assets.open("fast_move_20261015/manifest.json").bufferedReader().use { JSONArray(it.readText()) }
        val timings = mutableListOf<Long>()
        var accepted = 0
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val audio = assets.open("fast_move_20261015/${entry.getString("file")}").use { WavPcm16.decode(it.readBytes())!! }
            val start = System.nanoTime()
            val candidates = matcher.measure(audio)
            timings += (System.nanoTime() - start) / 1_000_000
            val expected = if (entry.isNull("expectedReference")) null else entry.getString("expectedReference")
            assertEquals(entry.getString("file"), expected != null, FastMoveReferenceMatcher.accepts(candidates))
            if (expected != null) { assertEquals(expected, candidates.first().moveName); accepted++ }
        }
        val median = timings.sorted()[timings.size / 2]
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Fast Move audio: ${timings.size} recorded mixtures, $accepted separated references; median ${median}ms, max ${timings.max()}ms\n")
        })
        assertTrue("Audio analysis must keep up with cue clips: median=${median}ms", median < 200)
    }

    @Test fun preservedClipsReachTheTimelineAndIdentifyTheOpponentDespitePlayerCue() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "fast-sound-test-${System.nanoTime()}")
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        fun move(name: String, turns: Int, type: PokemonType) = Move(name, type, 5, 8, true, turns = turns)
        val player = Pokemon(460, "Abomasnow", listOf(PokemonType.ICE), "Sinnoh",
            fastMoves = listOf(move("Powder Snow", 2, PokemonType.ICE)), chargedMoves = emptyList())
        val opponent = Pokemon(101, "Electrode", listOf(PokemonType.ELECTRIC), "Kanto",
            fastMoves = listOf(move("Spark", 2, PokemonType.ELECTRIC), move("Tackle", 1, PokemonType.NORMAL)), chargedMoves = emptyList())
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = listOf(player, opponent).find { it.name == name }
            override suspend fun getPokemonById(id: Int) = listOf(player, opponent).find { it.id == id }
            override suspend fun getAllSpeciesNames() = setOf(player.name, opponent.name)
        }
        val match = Match("FAST_SOUND_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val witness = PersistedFastMoveSoundWitness(root, context)
        val base = System.nanoTime()
        try {
            witness.start(match)
            custody.submitTestimony(SourceId("FIXTURE"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, player.name, player.id), 1000, null, emptyList(), base)
            custody.submitTestimony(SourceId("FIXTURE"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, opponent.name, opponent.id), 1000, null, emptyList(), base)
            custody.submitTestimony(SourceId("FIXTURE"), MatchStarted, 1000, null, emptyList(), base)
            val files = listOf("17-41-25_36.328.wav", "17-41-25_37.392.wav")
            files.forEachIndexed { index, file ->
                val bytes = instrumentation.context.assets.open("fast_move_20261015/$file").use { it.readBytes() }
                val artifact = FileAudioArtifactStore(root).preserveWav(bytes)!!
                val at = base + 2_000_000_000L + index * 1_064_000_000L
                val cueId = "fixture-player-cue-$index"
                timeline.append(com.example.overdex.battle.reality.RealityArticle(
                    id = com.example.overdex.battle.reality.ArticleId(cueId), sourceId = SourceId("FIXTURE"),
                    perceivedAt = 3000 + index * 1064L, recordedAt = 3000 + index * 1064L,
                    payload = FastMoveUseObserved(cueId, ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT,
                        "player-appearance", player.name, listOf("HP_BORDER_PULSE")),
                    monotonicTimeNanos = at + 500_000_000L,
                    matchId = MatchId(match.matchId),
                ))
                custody.submitTestimony(SourceId("FIXTURE_AUDIO"),
                    AudioCaptured(artifact, 48_000, 1, 1_200_000_000L, BattleCryCueKind.FAST_MOVE_IMPACT.name, "PLAYBACK_POKEMON_GO", .4f),
                    3000 + index * 1064L, null, listOf(cueId), at)
            }
            withTimeout(15_000) {
                while (timeline.getArticles().none { (it.payload as? FastMoveIdentified)?.let { p ->
                    p.side == ActivePokemonSide.OPPONENT && p.moveName == "Spark" && p.basis.startsWith("AUDIO_REFERENCE")
                } == true }) delay(20)
            }
            val sounds = timeline.getArticles().filter { it.payload is FastMoveSoundMeasured }
            assertEquals(2, sounds.size)
            assertTrue(sounds.all { (it.payload as FastMoveSoundMeasured).attackingSide == null })
            assertTrue(sounds.all { (it.payload as FastMoveSoundMeasured).cueAttackingSide == ActivePokemonSide.PLAYER })
            assertTrue(sounds.all { it.evidenceReferences?.size == 1 })
            assertEquals(2, root.walk().count { it.extension == "wav" })
            assertTrue(timeline.getArticles().none { (it.payload as? FastMoveIdentified)?.let { p -> p.side == ActivePokemonSide.PLAYER && p.moveName == "Spark" } == true })
        } finally { witness.stop(); match.release(); root.deleteRecursively() }
    }
}
