package com.example.overdex.battle.audio

import android.content.ContextWrapper
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.artifact.AudioArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LiveFastMoveSoundWitnessTest {
    @Test fun `VS arms legal audio matching without countdown attacks identity or energy`() =
        captureAfter(VsScreenWitnessed)

    @Test fun `countdown audio uses active species pools without HP triggers and overlaps are not new sounds`() =
        captureAfter(CountdownGlyphWitnessed("2", .99f, 1))

    @Test fun `late charged phase records subsequent audio without fabricating match start`() =
        captureAfter(GetReadyWitnessed)

    @Test fun `missed countdown combat opens retained audio with species and damage provenance but no invented start`() =
        captureAfter(ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f),
            context = listOf(
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu"),
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Snorlax")))

    @Test fun `ambiguous comparison keeps its PCM and rejected status while overlapping windows are rate limited`() =
        captureAfter(CountdownGlyphWitnessed("2", .99f, 1), ambiguous = true)

    @Test fun `VS listens with unknown species but never searches the full catalogue`() =
        captureAfter(VsScreenWitnessed, context = emptyList(), expectCapture = false)

    @Test fun `one known side retains legal comparison but cannot accept mixed sound identity`() =
        captureAfter(VsScreenWitnessed,
            context = listOf(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu")))

    @Test fun `switch while PCM is being persisted cannot publish stale sound conclusions`() =
        captureAfter(VsScreenWitnessed, expectCapture = false, invalidateDuringStorage = true)

    private fun captureAfter(opening: TestimonyPayload, ambiguous: Boolean = false,
        context: List<TestimonyPayload> = listOf(
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu"),
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Snorlax")),
        expectCapture: Boolean = true, invalidateDuringStorage: Boolean = false) = runBlocking {
        val references = listOf("Spark" to "209-0_spark.wav", "Tackle" to "033-0_tackle.wav").map { (name, file) ->
            FastMoveSoundReference(name, file, requireNotNull(WavPcm16.decode(
                File("src/main/assets/fast_move_sounds/${if (ambiguous) "209-0_spark.wav" else file}").readBytes())))
        }
        // This impossible move has the exact winning waveform. It must be excluded
        // before scoring, otherwise the full-library match would be an artificial tie.
        val matcher = FastMoveReferenceMatcher(references + references.first().copy(moveName = "Air Slash"))
        val samples = StatChangeSoundMatcher.mono8k(references.first().audio).copyOf(12_800)
        val input = MutableSharedFlow<LivePcmChunk>(extraBufferCapacity = 16)
        val root = Files.createTempDirectory("fast-move-witness-").toFile()
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val pokemon = listOf(
            Pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), "Kanto", fastMoves = listOf(
                Move("Spark", PokemonType.ELECTRIC, 4, 7, true, 2)), chargedMoves = emptyList()),
            Pokemon(143, "Snorlax", listOf(PokemonType.NORMAL), "Kanto", fastMoves = listOf(
                Move("Tackle", PokemonType.NORMAL, 3, 3, true, 1)), chargedMoves = emptyList()))
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = pokemon.find { it.name == name }
            override suspend fun getPokemonById(id: Int) = pokemon.find { it.id == id }
            override suspend fun getAllSpeciesNames() = pokemon.map { it.name }.toSet()
        }
        val match = Match("FAST_STREAM_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val storageEntered = CompletableDeferred<Unit>()
        val releaseStorage = CountDownLatch(1)
        val fileStore = FileAudioArtifactStore(root)
        val store = object : AudioArtifactStore {
            override fun preserveWav(bytes: ByteArray): AudioArtifactReference? {
                storageEntered.complete(Unit)
                if (invalidateDuringStorage) check(releaseStorage.await(5, TimeUnit.SECONDS))
                return fileStore.preserveWav(bytes)
            }
        }
        val witness = LiveFastMoveSoundWitness(ContextWrapper(null), store, input, { matcher })
        try {
            witness.start(match)
            withTimeout(4000) { while (input.subscriptionCount.value == 0) delay(10) }
            val base = System.nanoTime()
            context.forEachIndexed { index, payload ->
                custody.submitTestimony(SourceId("TEST"), payload,
                    998 + index.toLong(), null, emptyList(), base - context.size + index)
            }
            custody.submitTestimony(SourceId("TEST"), opening,
                1000, null, emptyList(), base)
            withTimeout(4000) { while (timeline.getArticles().none { it.payload == opening }) delay(10) }
            for (index in 0 until 80) {
                input.emit(LivePcmChunk(Pcm16Audio(8000, 1, samples.copyOfRange(index * 160, (index + 1) * 160)),
                    index.toLong(), base + (index + 1) * 20_000_000L, 1000 + (index + 1) * 20L, "PLAYBACK_POKEMON_GO"))
                if (invalidateDuringStorage && index == 59) {
                    withTimeout(4000) { storageEntered.await() }
                    custody.submitTestimony(SourceId("TEST_SWITCH"),
                        ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Pidgeot"),
                        2200, null, emptyList(), base + 1_200_000_000L)
                    withTimeout(4000) { while (timeline.getArticles().none { (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesName == "Pidgeot" }) delay(10) }
                    delay(100) // Let the observer consume the new source-time context while IO is blocked.
                    releaseStorage.countDown()
                }
                delay(20)
            }
            if (!expectCapture) {
                delay(150)
                val articles = timeline.getArticles()
                assertTrue(articles.none { it.payload is FastMoveSoundMeasured || it.payload is AudioCaptured })
                assertTrue(articles.none { it.payload is FastMoveIdentified || it.payload is FastMoveEnergyDerived })
                return@runBlocking
            }
            withTimeout(4000) { while (timeline.getArticles().none { it.payload is FastMoveSoundMeasured }) delay(10) }
            delay(100) // Finish any last overlapping window before checking deduplication.
            val articles = timeline.getArticles()
            val sound = articles.single { it.payload is FastMoveSoundMeasured }
            val capture = articles.single { it.payload is AudioCaptured }
            val audio = capture.payload as AudioCaptured
            val measured = sound.payload as FastMoveSoundMeasured
            assertEquals(!ambiguous && context.size == 2, measured.referenceMatchAccepted)
            assertEquals(context.size, measured.referenceSearchScopes.size)
            assertEquals(if (context.size == 2) setOf("Spark", "Tackle") else setOf("Spark"),
                measured.referenceCandidates.map { it.moveName }.toSet())
            assertEquals(measured.referenceCandidates.map { it.moveName }.toSet(),
                measured.referenceSearchScopes.flatMap { it.moveNames }.toSet())
            assertTrue(measured.referenceSearchScopes.all { it.contextArticleIds.isNotEmpty() })
            assertEquals("Spark", measured.referenceCandidates.first().moveName)
            assertNull(measured.attackingSide)
            assertNull(measured.cueAttackingSide)
            assertEquals(listOf(capture.id.value), sound.evidenceReferences)
            assertEquals(capture.monotonicTimeNanos, sound.monotonicTimeNanos)
            assertEquals(LiveFastMoveSoundWitness.CUE_KIND, audio.cueKind)
            assertEquals(1_200_000_000L, audio.durationNanos)
            assertTrue(File(root, audio.artifact.relativePath).isFile)
            if (opening !is ActiveHpBarDamageTickMeasured)
                assertTrue(articles.none { it.payload is FastMoveUseObserved })
            else assertTrue(articles.filter { it.payload is FastMoveUseObserved }.all {
                articles.single { source -> source.payload == opening }.id in it.predecessorIds
            })
            assertTrue(articles.none { it.payload is FastMoveIdentified })
            assertTrue(articles.none { it.payload is FastMoveEnergyDerived })
            assertTrue(articles.none { it.payload is MatchStarted })
            assertEquals((context + opening).map { payload -> articles.single { it.payload == payload }.id.value }.toSet(),
                capture.evidenceReferences.toSet())
        } finally { releaseStorage.countDown(); witness.stop(); match.release(); root.deleteRecursively() }
    }
}
