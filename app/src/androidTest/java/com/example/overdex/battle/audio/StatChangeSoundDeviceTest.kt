package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.StatChangeAttributionWitness
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Injected PCM uses the same path as the recorder, without starting or changing phone audio. */
class StatChangeSoundDeviceTest {
    @Test fun recordedPcmBecomesCitedStatEventsAndReplayActions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "stat-sound-test-${System.nanoTime()}")
        val input = MutableSharedFlow<LivePcmChunk>(extraBufferCapacity = 16)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val match = Match("STAT_SOUND_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        var enabled = true
        val witness = StatChangeSoundWitness(context, FileAudioArtifactStore(root), root, { enabled }, input)
        val attribution = StatChangeAttributionWitness(context)
        val base = System.nanoTime()
        var sequence = 0L
        val expectedAt = mutableMapOf<StatChangeCue, Long>()
        try {
            witness.start(match); attribution.start(match)
            withTimeout(5_000) { while (input.subscriptionCount.value == 0) delay(10) }
            custody.submitTestimony(SourceId("FIXTURE"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323),
                1_000, null, emptyList(), base)
            custody.submitTestimony(SourceId("FIXTURE"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364),
                1_000, null, emptyList(), base)
            val moves = listOf("Power-Up Punch", "Skull Bash", "Rock Tomb", "Close Combat")
            for ((index, cue) in StatChangeCue.entries.withIndex()) {
                val audio = context.assets.open("stat_change_sounds/${cue.fileName}").use { WavPcm16.decode(it.readBytes())!! }
                val stream = ShortArray(48_000 * 2)
                val offset = 12_000
                for (i in 0 until audio.samples.size * 48_000 / audio.sampleRateHz) {
                    val source = i.toDouble() * audio.sampleRateHz / 48_000
                    val left = source.toInt()
                    stream[offset + i] = ((audio.samples[left] * (1 - source + left) +
                        audio.samples[minOf(left + 1, audio.samples.lastIndex)] * (source - left)) * .4).toInt().toShort()
                }
                val cueAt = base + sequence * 20_000_000 + 250_000_000
                expectedAt[cue] = cueAt
                // One OCR result is deliberately delayed until after the sound.
                if (index != 2) custody.submitTestimony(SourceId("FIXTURE"),
                    ChargedMoveEnergySpent(ActivePokemonSide.PLAYER, "Camerupt", moves[index], 50, "FIXTURE"),
                    1_000 + sequence * 20, null, emptyList(), cueAt - 250_000_000)
                for (start in stream.indices step 960) {
                    input.emit(LivePcmChunk(Pcm16Audio(48_000, 1, stream.copyOfRange(start, start + 960)),
                        sequence, base + (sequence + 1) * 20_000_000, 1_000 + (sequence + 1) * 20, "PLAYBACK_POKEMON_GO"))
                    sequence++
                    delay(20)
                }
                if (index == 2) custody.submitTestimony(SourceId("FIXTURE"),
                    ChargedMoveEnergySpent(ActivePokemonSide.PLAYER, "Camerupt", moves[index], 50, "FIXTURE"),
                    1_000 + (cueAt - base - 250_000_000) / 1_000_000, null, emptyList(), cueAt - 250_000_000)
            }
            withTimeout(5_000) { while (timeline.getArticles().count { it.payload is StatChangeObserved } < 4) delay(10) }
            enabled = false
            val articles = timeline.getArticles()
            assertEquals(4, articles.count { it.payload is AudioCaptured })
            val sounds = articles.filter { it.payload is StatChangeSoundMeasured }
            assertEquals(4, sounds.size)
            assertEquals(4, root.walk().count { it.extension == "wav" })
            assertTrue(root.walk().filter { it.extension == "wav" }.all { it.length() <= 24_044 })
            sounds.forEach { sound ->
                val cue = StatChangeCue.valueOf((sound.payload as StatChangeSoundMeasured).cue)
                assertTrue("Sound timestamp drift: $cue", abs(sound.monotonicTimeNanos!! - expectedAt.getValue(cue)) <= 16_000_000)
                assertEquals(1, sound.evidenceReferences!!.size)
            }
            val archive = MatchArchive(matchId = match.matchId, articles = articles.map(RealityArticleArchiveMapper::map))
            val replay = MatchReplayModel(archive)
            sounds.forEach { sound ->
                val cue = StatChangeCue.valueOf((sound.payload as StatChangeSoundMeasured).cue)
                // Consecutive cues may overlap their two-second presentation windows.
                val action = replay.sceneAt(sound.monotonicTimeNanos!!).statChanges.single {
                    it.stat == cue.stat && it.direction == cue.direction
                }
                assertEquals(if (cue == StatChangeCue.ATTACK_DOWN) "OPPONENT" else "PLAYER", action.side)
                assertEquals(cue.stat, action.stat)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stat_audio_chain", "4 cues -> 4 bounded WAVs -> 4 measurements -> 4 attributed replay indicators; late OCR retained sound time")
            })
        } finally { witness.stop(); attribution.stop(); match.release(); root.deleteRecursively() }
    }

    @Test fun referenceMatcherHasTimeToKeepUpWithTheScanInterval() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val matcher = loadStatSoundMatcher(context)
        val input = context.assets.open("stat_change_sounds/buff_attack.wav").use { WavPcm16.decode(it.readBytes())!! }
        val samples = ShortArray(12_000)
        StatChangeSoundMatcher.mono8k(input).copyInto(samples, 2_000)
        val query = Pcm16Audio(8_000, 1, samples)
        val elapsed = (0 until 12).map {
            val start = System.nanoTime()
            assertNotNull(matcher.match(query))
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stat_matcher_median_ms", "${elapsed[elapsed.size / 2]}")
            putString("stat_matcher_max_ms", "${elapsed.last()}")
        })
        assertTrue("Matcher must fit inside its 200 ms scanning interval: $elapsed", elapsed[elapsed.size / 2] < 200)
    }
}
