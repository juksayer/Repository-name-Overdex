package com.example.overdex.battle.audio

import android.content.ContextWrapper
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResultMusicWitnessTest {
    @Test fun `PCM reaches custody then the timeline with an audio citation and independent result comparison`() = runBlocking {
        val references = listOf("126_pvp_win.wav" to ResultMusicTheme.WIN, "127_pvp_lose.wav" to ResultMusicTheme.LOSS).map { (name, theme) ->
            ResultMusicReference(theme, name, requireNotNull(WavPcm16.decode(File("src/main/assets/result_music/$name").readBytes())))
        }
        val matcher = ResultMusicMatcher(references)
        val samples = StatChangeSoundMatcher.mono8k(references.first().audio)
        val input = MutableSharedFlow<LivePcmChunk>(extraBufferCapacity = 16)
        val root = Files.createTempDirectory("music-witness-").toFile()
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val match = Match("MUSIC_STREAM_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val witness = ResultMusicWitness(ContextWrapper(null), FileAudioArtifactStore(root), { true }, input, { matcher })
        try {
            witness.start(match)
            withTimeout(4000) { while (input.subscriptionCount.value == 0) delay(10) }
            val base = System.nanoTime()
            custody.submitTestimony(SourceId("TEST"), MatchStarted, 1000, null, emptyList(), base)
            delay(100)
            for (index in 0 until 250) {
                input.emit(LivePcmChunk(Pcm16Audio(8000, 1, samples.copyOfRange(index * 160, (index + 1) * 160)),
                    index.toLong(), base + (index + 1) * 20_000_000L, 1000 + (index + 1) * 20L, "PLAYBACK_POKEMON_GO"))
                delay(20)
            }
            withTimeout(4000) { while (timeline.getArticles().none { it.payload is MatchResultMusicMeasured }) delay(10) }
            val articles = timeline.getArticles()
            val music = articles.single { it.payload is MatchResultMusicMeasured }
            val audioArticle = articles.single { it.payload is AudioCaptured }
            val capture = audioArticle.payload as AudioCaptured
            assertEquals(listOf(audioArticle.id.value), music.evidenceReferences)
            assertEquals(audioArticle.monotonicTimeNanos, music.monotonicTimeNanos)
            assertEquals("PLAYBACK_POKEMON_GO", capture.captureSource)
            assertEquals(4_000_000_000, capture.durationNanos)
            assertTrue(File(root, capture.artifact.relativePath).isFile)
            assertEquals(0, articles.count { it.payload is MatchEnded })
            custody.submitTestimony(SourceId("TEST_RESULT"), MatchEnded(BattleResult.WIN), 3000, null, emptyList(), base + 2_000_000_000)
            withTimeout(4000) { while (timeline.getArticles().none { it.payload is MatchResultMusicCompared }) delay(10) }
            val compared = timeline.getArticles().single { it.payload is MatchResultMusicCompared }
            assertTrue((compared.payload as MatchResultMusicCompared).agrees)
            assertEquals(2, compared.evidenceReferences?.size)
            assertEquals(1, timeline.getArticles().count { it.payload is MatchEnded })
        } finally { witness.stop(); match.release(); root.deleteRecursively() }
    }
}
