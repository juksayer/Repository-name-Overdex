package com.example.overdex.battle.audio

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
import java.nio.file.Files
import java.io.File
import java.util.Collections
import kotlin.random.Random

class LiveLocalizedCryWitnessTest {
    @Test fun `shared PCM produces source-timed candidates and bounded wakeups without a faint or attack`() = runBlocking {
        val reference = ShortArray(1600) { Random(17+it).nextInt(-15000,15000).toShort() }
        val refs=listOf(CryWaveformReference(42,"test-reference",Pcm16Audio(8000,1,reference)))
        val input=MutableSharedFlow<LivePcmChunk>(extraBufferCapacity=4)
        val root=Files.createTempDirectory("cry-witness-").toFile()
        val timeline=InMemoryRealityTimeline()
        val custody=InMemoryTestimonyCustody()
        val knowledge=object : PokemonKnowledge {
            override suspend fun getPokemonByName(name:String):Pokemon?=null
            override suspend fun getPokemonById(id:Int):Pokemon?=null
            override suspend fun getAllSpeciesNames()=emptySet<String>()
        }
        val match=Match("CRY_TEST",custody=custody,realityTimeline=timeline,pokemonKnowledge=knowledge)
        val wakes=Collections.synchronizedList(mutableListOf<Pair<ActivePokemonSide,Long>>())
        val pools=Collections.synchronizedList(mutableListOf<Set<Int>>())
        val now=java.util.concurrent.atomic.AtomicLong()
        val opponentBalls=com.example.overdex.battle.observation.BattleCropContracts.opponentPokeBalls.cropName
        val playerBalls=com.example.overdex.battle.observation.BattleCropContracts.playerPokeBalls.cropName
        val gate=com.example.overdex.battle.observation.TeamResourceSnapshotGate(
            setOf(playerBalls,opponentBalls),setOf(playerBalls,opponentBalls),now::get)
        val witness=LiveLocalizedCryWitness(FileAudioArtifactStore(root),{ side,at ->
            gate.requestDepartureCheck(side,at);wakes += side to at },input,
            { ids -> pools += ids; refs.filter { it.speciesId in ids } })
        try {
            witness.start(match)
            withTimeout(4000) { while(input.subscriptionCount.value==0)delay(10) }
            val base=System.nanoTime()
            custody.submitTestimony(SourceId("VS"),VsScreenWitnessed,1000,null,emptyList(),base)
            for ((side,species) in listOf(ActivePokemonSide.PLAYER to 776,ActivePokemonSide.OPPONENT to 42))
                custody.submitTestimony(SourceId("SPECIES_WITNESS"),ActivePokemonSpeciesWitnessed(side,"species$species",species),
                    1000,null,listOf("badge-$species"),base)
            withTimeout(4000) { while(timeline.getArticles().count { it.payload is ActivePokemonSpeciesWitnessed }<2)delay(10) }
            delay(50)
            val samples=ShortArray(32_000).also { reference.copyInto(it,8000) }
            for (n in 0..1) {
                now.set(base+(n+1)*4_000_000_000L)
                input.emit(LivePcmChunk(Pcm16Audio(8000,1,samples),n.toLong(),base+(n+1)*4_000_000_000L,
                    1000+(n+1)*4000L,"PLAYBACK_POKEMON_GO"))
                withTimeout(4000) { while(timeline.getArticles().count { it.payload is BattleCryLocalized }<n+1)delay(10) }
            }
            withTimeout(4000) { while(wakes.isEmpty())delay(10) }
            match.awaitAcceptedEvidencePublished()
            val articles=timeline.getArticles()
            val cries=articles.filter { it.payload is BattleCryLocalized }
            assertEquals(listOf(base+1_000_000_000,base+5_000_000_000),cries.map { it.monotonicTimeNanos })
            for (cry in cries) {
                val capture=articles.single { it.id.value==cry.evidenceReferences!!.single() }
                val audio=capture.payload as AudioCaptured
                val restored=requireNotNull(WavPcm16.decode(File(root,audio.artifact.relativePath).readBytes()))
                assertArrayEquals(samples,restored.samples)
                assertEquals(4_000_000_000L,audio.durationNanos)
                assertEquals(capture.monotonicTimeNanos!!+1_000_000_000,cry.monotonicTimeNanos)
            }
            val candidate=articles.single { it.payload is RepeatCryDepartureCandidateObserved }
            assertEquals(base+5_000_000_000,candidate.monotonicTimeNanos)
            assertEquals(listOf(ActivePokemonSide.OPPONENT to (base+5_000_000_000)),wakes.toList())
            assertTrue(gate.isEnabled(opponentBalls))
            assertFalse(gate.isEnabled(playerBalls))
            now.addAndGet(3_000_000_000)
            assertFalse("The real resource reader assignment must expire",gate.isEnabled(opponentBalls))
            assertTrue(pools.all { it==setOf(42,776) })
            assertTrue(articles.none { it.payload is ActivePokemonFaintedWitnessed || it.payload is FastMoveUseObserved ||
                it.payload is FastMoveEnergyDerived || it.payload is MatchEnded })
        } finally { witness.stop();match.release();root.deleteRecursively() }
    }
    @Test fun `late new species revisits retained entrance PCM without searching the whole library`() = runBlocking {
        val reference=ShortArray(1600) { Random(71+it).nextInt(-15000,15000).toShort() }
        val ref=CryWaveformReference(460,"abomasnow-reference",Pcm16Audio(8000,1,reference))
        val input=MutableSharedFlow<LivePcmChunk>(extraBufferCapacity=4)
        val root=Files.createTempDirectory("cry-catchup-").toFile()
        val timeline=InMemoryRealityTimeline();val custody=InMemoryTestimonyCustody()
        val knowledge=object : PokemonKnowledge {
            override suspend fun getPokemonByName(name:String):Pokemon?=null
            override suspend fun getPokemonById(id:Int):Pokemon?=null
            override suspend fun getAllSpeciesNames()=emptySet<String>()
        }
        val match=Match("CRY_CATCHUP",custody=custody,realityTimeline=timeline,pokemonKnowledge=knowledge)
        val wakes=Collections.synchronizedList(mutableListOf<Pair<ActivePokemonSide,Long>>())
        val searches=Collections.synchronizedList(mutableListOf<Set<Int>>())
        val witness=LiveLocalizedCryWitness(FileAudioArtifactStore(root),{ side,at -> wakes += side to at },input,
            { ids -> searches += ids; listOf(ref).filter { it.speciesId in ids } })
        try {
            witness.start(match)
            withTimeout(4000) { while(input.subscriptionCount.value==0)delay(10) }
            val base=System.nanoTime()
            custody.submitTestimony(SourceId("VS"),VsScreenWitnessed,1000,null,emptyList(),base)
            for ((side,species) in listOf(ActivePokemonSide.PLAYER to 776,ActivePokemonSide.OPPONENT to 42))
                custody.submitTestimony(SourceId("SPECIES_WITNESS"),ActivePokemonSpeciesWitnessed(side,"species$species",species),
                    1000,null,emptyList(),base)
            withTimeout(4000) { while(timeline.getArticles().count { it.payload is ActivePokemonSpeciesWitnessed }<2)delay(10) }
            delay(50)
            val samples=ShortArray(32000).also { reference.copyInto(it,8000) }
            input.emit(LivePcmChunk(Pcm16Audio(8000,1,samples),0,base+4_000_000_000,5000,"game"))
            withTimeout(4000) { while(searches.isEmpty())delay(10) }
            input.emit(LivePcmChunk(Pcm16Audio(8000,1,ShortArray(32000)),1,base+8_000_000_000,9000,"game"))
            // Captured before the cry, accepted much later. No source timestamp is rewritten.
            custody.submitTestimony(SourceId("SPECIES_WITNESS"),ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT,"Abomasnow",460),
                1500,null,listOf("native-badge"),base+500_000_000)
            withTimeout(4000) { while(timeline.getArticles().none { (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesId==460 })delay(10) }
            delay(50)
            input.emit(LivePcmChunk(Pcm16Audio(8000,1,samples),2,base+12_000_000_000,13000,"game"))
            withTimeout(5000) { while(wakes.isEmpty())delay(10) }
            match.awaitAcceptedEvidencePublished()
            val articles=timeline.getArticles()
            assertEquals(listOf(base+1_000_000_000,base+9_000_000_000),
                articles.filter { it.payload is BattleCryLocalized }.map { it.monotonicTimeNanos }.sortedBy { it })
            assertEquals(listOf(ActivePokemonSide.OPPONENT to (base+9_000_000_000)),wakes.toList())
            assertEquals(setOf(42,776),searches.first())
            assertEquals(setOf(42,460,776),searches.last())
            assertTrue(articles.none { it.payload is ActivePokemonFaintedWitnessed })
        } finally { witness.stop();match.release();root.deleteRecursively() }
    }

}
