package com.example.overdex.battle.audio

import android.content.ContextWrapper
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LiveFastMoveSoundWitnessTest {
    @Test fun `countdown audio reaches custody without species HP or visual triggers and overlaps are not new sounds`() =
        captureAfter(CountdownGlyphWitnessed("2", .99f, 1))

    @Test fun `late charged phase records subsequent audio without fabricating match start`() =
        captureAfter(GetReadyWitnessed)

    private fun captureAfter(opening: TestimonyPayload) = runBlocking {
        val references = listOf("Spark" to "209-0_spark.wav", "Tackle" to "033-0_tackle.wav").map { (name, file) ->
            FastMoveSoundReference(name, file, requireNotNull(WavPcm16.decode(
                File("src/main/assets/fast_move_sounds/$file").readBytes())))
        }
        val matcher = FastMoveReferenceMatcher(references)
        val samples = StatChangeSoundMatcher.mono8k(references.first().audio).copyOf(12_800)
        val input = MutableSharedFlow<LivePcmChunk>(extraBufferCapacity = 16)
        val root = Files.createTempDirectory("fast-move-witness-").toFile()
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val match = Match("FAST_STREAM_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val witness = LiveFastMoveSoundWitness(ContextWrapper(null), FileAudioArtifactStore(root), input, { matcher })
        try {
            witness.start(match)
            withTimeout(4000) { while (input.subscriptionCount.value == 0) delay(10) }
            val base = System.nanoTime()
            custody.submitTestimony(SourceId("TEST"), opening,
                1000, null, emptyList(), base)
            withTimeout(4000) { while (timeline.getArticles().none { it.payload == opening }) delay(10) }
            for (index in 0 until 80) {
                input.emit(LivePcmChunk(Pcm16Audio(8000, 1, samples.copyOfRange(index * 160, (index + 1) * 160)),
                    index.toLong(), base + (index + 1) * 20_000_000L, 1000 + (index + 1) * 20L, "PLAYBACK_POKEMON_GO"))
                delay(20)
            }
            withTimeout(4000) { while (timeline.getArticles().none { it.payload is FastMoveSoundMeasured }) delay(10) }
            delay(100) // Finish any last overlapping window before checking deduplication.
            val articles = timeline.getArticles()
            val sound = articles.single { it.payload is FastMoveSoundMeasured }
            val capture = articles.single { it.payload is AudioCaptured }
            val audio = capture.payload as AudioCaptured
            val measured = sound.payload as FastMoveSoundMeasured
            assertTrue(measured.referenceMatchAccepted)
            assertEquals("Spark", measured.referenceCandidates.first().moveName)
            assertNull(measured.attackingSide)
            assertNull(measured.cueAttackingSide)
            assertEquals(listOf(capture.id.value), sound.evidenceReferences)
            assertEquals(capture.monotonicTimeNanos, sound.monotonicTimeNanos)
            assertEquals(LiveFastMoveSoundWitness.CUE_KIND, audio.cueKind)
            assertEquals(1_200_000_000L, audio.durationNanos)
            assertTrue(File(root, audio.artifact.relativePath).isFile)
            assertTrue(articles.none { it.payload is FastMoveUseObserved })
            assertTrue(articles.none { it.payload is MatchStarted })
            assertEquals(listOf(articles.single { it.payload == opening }.id.value), capture.evidenceReferences)
        } finally { witness.stop(); match.release(); root.deleteRecursively() }
    }
}
