package com.example.overdex.battle.audio

import androidx.test.platform.app.InstrumentationRegistry
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

/** Exercises bounded probes through real custody and storage, without recording phone audio. */
class ChargedMoveSoundProbeDeviceTest {
    @Test fun chargedMoveKeepsRejectedAudioAndDoesNotInventAnEffect() = runBlocking {
        withProbe { fixture ->
            fixture.feed(ShortArray(48_000))
            fixture.announce()
            fixture.feed(ShortArray(56_000))
            fixture.awaitStatus("NO_CONFIDENT_MATCH")
            val capture = fixture.timeline.getArticles().mapNotNull { it.payload as? AudioCaptured }.single()
            assertEquals("CHARGED_MOVE_EFFECT", capture.cueKind)
            assertEquals(13_000_000_000L, capture.durationNanos)
            assertEquals(208_044L, capture.artifact.byteCount)
            assertFalse(fixture.timeline.getArticles().any { it.payload is StatChangeSoundMeasured })
        }
    }

    @Test fun continuousAndChargedMoveReadersPublishOnlyOneMeasurementForTheSameCue() = runBlocking {
        withProbe { fixture ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cue = context.assets.open("stat_change_sounds/debuff_attack.wav").use {
                StatChangeSoundMatcher.mono8k(requireNotNull(WavPcm16.decode(it.readBytes())))
            }
            fixture.feed(ShortArray(48_000))
            fixture.announce()
            val after = ShortArray(56_000)
            cue.copyInto(after, 4_000)
            fixture.feed(after)
            fixture.awaitStatus("ACCEPTED")
            delay(100)
            val articles = fixture.timeline.getArticles()
            assertEquals(2, articles.count { it.payload is AudioCaptured })
            val measured = articles.single { it.payload is StatChangeSoundMeasured }
            assertEquals("ATTACK_DOWN", (measured.payload as StatChangeSoundMeasured).cue)
            assertTrue(kotlin.math.abs(measured.monotonicTimeNanos!! - fixture.base - 6_500_000_000) <= 16_000_000)
            assertEquals(1, measured.evidenceReferences!!.size)
        }
    }

    @Test fun missingInputCancelsProbeRatherThanSplicingAcrossTheGap() = runBlocking {
        withProbe { fixture ->
            fixture.feed(ShortArray(48_000))
            fixture.announce()
            fixture.feed(ShortArray(800))
            fixture.sequence += 1
            fixture.feed(ShortArray(56_000))
            fixture.awaitStatus("INPUT_GAP")
            assertFalse(fixture.timeline.getArticles().any { it.payload is AudioCaptured })
        }
    }

    @Test fun delayedAnnouncementRetainsSoundSeveralSecondsBeforeTheText() = runBlocking {
        withProbe { fixture ->
            val cue = fixture.context.assets.open("stat_change_sounds/debuff_attack.wav").use {
                StatChangeSoundMatcher.mono8k(requireNotNull(WavPcm16.decode(it.readBytes())))
            }
            val before = ShortArray(112_000) // Receive 14 s of audio before delayed text delivery.
            cue.copyInto(before, 5 * 8_000) // Sound at 5 s; text was visible at 10 s.
            fixture.feed(before)
            fixture.announce(fixture.base + 10_000_000_000L)
            fixture.feed(ShortArray(24_000))
            fixture.awaitStatus("ACCEPTED")
            val probe = fixture.timeline.getArticles().single {
                (it.payload as? AudioCaptured)?.cueKind == "CHARGED_MOVE_EFFECT"
            }
            assertEquals(fixture.base + 4_000_000_000L, probe.monotonicTimeNanos)
            val sounds = fixture.timeline.getArticles().filter { it.payload is StatChangeSoundMeasured }
            assertEquals(1, sounds.size)
            assertTrue(kotlin.math.abs(sounds.single().monotonicTimeNanos!! - fixture.base - 5_000_000_000L) <= 16_000_000)
        }
    }

    private suspend fun withProbe(check: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            fixture.witness.start(fixture.match)
            withTimeout(5_000) { while (fixture.input.subscriptionCount.value == 0) delay(10) }
            check(fixture)
        } finally {
            fixture.witness.stop(); fixture.match.release(); fixture.root.deleteRecursively()
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "charged-move-probe-test-${System.nanoTime()}")
        val input = MutableSharedFlow<LivePcmChunk>(extraBufferCapacity = 16)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("PROBE_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        })
        val witness = StatChangeSoundWitness(context, FileAudioArtifactStore(root), root, { true }, input)
        val base = System.nanoTime()
        var sequence = 0L
        suspend fun announce(at: Long = base + sequence * 20_000_000) {
            custody.submitTestimony(SourceId("FIXTURE"), ChargeMoveUsedAnnounced,
                1_000 + (at - base) / 1_000_000, null, emptyList(), at)
            awaitStatus("WINDOW_REQUESTED")
        }
        suspend fun feed(samples: ShortArray) {
            for (start in samples.indices step 160) {
                input.emit(LivePcmChunk(Pcm16Audio(8_000, 1, samples.copyOfRange(start, start + 160)),
                    sequence, base + (sequence + 1) * 20_000_000, 1_000 + (sequence + 1) * 20, "PLAYBACK_POKEMON_GO"))
                sequence++
                delay(5)
            }
            // Let the last queued PCM chunks enter the rolling buffer before the next announcement.
            delay(100)
        }
        suspend fun awaitStatus(status: String) {
            withTimeout(5_000) {
                while (timeline.getArticles().none { (it.payload as? StatChangeSoundChecked)?.status == status }) delay(10)
            }
        }
    }
}
