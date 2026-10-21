package com.example.overdex.battle.audio

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.StatChangeAttributionWitness
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Re-runs actual preserved evidence through Match, not hand-injected inferred moves or effects. */
class RecordedLungeAttributionDeviceTest {
    @Test fun configuredLeadAndRecordedNicknameReachNamedMovesAndStatReplay() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val fixture = "lunge_recording_20261012"
        val original = assets.open("$fixture/evidence.json").bufferedReader().use {
            MatchArchiveSerializer.deserialize(it.readText())
        }
        val knowledge = GameMasterLoader(context)
        fun pokemon(name: String): Pokemon? {
            val species = knowledge.allPokemon().firstOrNull {
                it.speciesName.equals(name, true) || it.speciesName.substringBefore(" (").equals(name, true)
            } ?: return null
            fun move(id: String, fast: Boolean) = knowledge.getMove(id)?.let {
                Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
                    if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null)
            }
            return Pokemon(species.dex, species.speciesName.substringBefore(" ("),
                species.types.filter { it != "none" }.map { PokemonType.valueOf(it.uppercase()) }, "",
                fastMoves = species.fastMoves.mapNotNull { move(it, true) },
                chargedMoves = species.chargedMoves.mapNotNull { move(it, false) })
        }
        val root = File(context.cacheDir, "recorded-lunge-test-${System.nanoTime()}")
        val store = FileAudioArtifactStore(root)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("RECORDED_LUNGE_TEST", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String) = pokemon(name)
                override suspend fun getPokemonById(id: Int) = knowledge.getPokemonByDex(id)?.let { pokemon(it.speciesName) }
                override suspend fun getAllSpeciesNames() = knowledge.allPokemonNames()
            })
        val sounds = StatChangeSoundWitness(context, store, root, { false }, emptyFlow())
        val attribution = StatChangeAttributionWitness(context)
        try {
            sounds.start(match); attribution.start(match)
            for (source in original.articles.sortedBy { it.sequenceNumber }) {
                val payload: TestimonyPayload = when (val value = source.payload) {
                    is ArchivedPlayerTeamSlotConfigured -> PlayerTeamSlotConfigured(value.slot, value.speciesName,
                        value.speciesId, value.fastMoveName, value.chargedMoveNames)
                    is ArchivedCountdownGlyphWitnessed -> CountdownGlyphWitnessed(value.glyph, value.similarity, value.frameIndex)
                    is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                        ActivePokemonSide.valueOf(value.side), value.speciesName, value.speciesId)
                    is ArchivedRawText -> RawTestimony(value.value)
                    is ArchivedAudioCaptured -> {
                        val bytes = assets.open("$fixture/${value.sha256}.wav").use { it.readBytes() }
                        assertEquals(value.sha256, statSoundSha256(bytes))
                        AudioCaptured(requireNotNull(store.preserveWav(bytes)), value.sampleRateHz,
                            value.channelCount, value.durationNanos, value.cueKind, value.captureSource, value.peakAmplitude)
                    }
                    else -> error("Unexpected supplied inference: ${source.payload}")
                }
                custody.submitTestimony(SourceId(source.sourceId), payload, source.perceivedAt, source.confidence,
                    source.evidenceReferences.orEmpty(), requireNotNull(source.monotonicTimeNanos))
                withTimeout(5_000) {
                    while (timeline.getArticles().none { it.sourceId.id == source.sourceId &&
                            it.monotonicTimeNanos == source.monotonicTimeNanos && it.payload == payload }) delay(10)
                }
            }
            withTimeout(10_000) {
                while (timeline.getArticles().count { it.payload is StatChangeSoundMeasured } < 3 ||
                    timeline.getArticles().count { it.payload is StatChangeObserved } < 2) delay(10)
            }
            val articles = timeline.getArticles()
            val lead = articles.single { it.sourceId.id == "TEAM_SELECT_LEAD_INFERENCE" }
            assertEquals("Galvantula", (lead.payload as ActivePokemonSpeciesWitnessed).speciesName)
            val lunges = articles.filter { (it.payload as? ChargedMoveEnergySpent)?.moveName == "Lunge" }
            assertEquals(3, lunges.size) // Repeated OCR of the third Lunge remains one move use.
            lunges.forEach {
                assertEquals(ActivePokemonSide.PLAYER, (it.payload as ChargedMoveEnergySpent).side)
                assertEquals("Galvantula", it.payload.speciesName)
                assertTrue(it.predecessorIds.contains(lead.id))
            }
            val heard = articles.filter { it.payload is StatChangeSoundMeasured }.sortedBy { it.monotonicTimeNanos }
            assertEquals(3, heard.size)
            assertTrue(heard.all { (it.payload as StatChangeSoundMeasured).cue == "ATTACK_DOWN" })
            val changes = articles.filter { it.payload is StatChangeObserved }
            assertEquals(2, changes.size)
            changes.forEach {
                val change = it.payload as StatChangeObserved
                assertEquals(ActivePokemonSide.OPPONENT, change.affectedSide)
                assertEquals("Lunge", change.moveName)
                assertEquals(1, change.stages)
                assertTrue(it.evidenceReferences!!.contains(change.soundArticleId))
            }
            val replay = MatchReplayModel(MatchArchive(matchId = match.matchId,
                articles = articles.map(RealityArticleArchiveMapper::map)))
            heard.forEachIndexed { index, sound ->
                val action = replay.sceneAt(sound.monotonicTimeNanos!!).statChanges.single()
                assertEquals("ATK ↓", action.label)
                // The last sound has no nearby named move; keep it audible evidence with unknown side.
                assertEquals(if (index < 2) "OPPONENT" else null, action.side)
            }
        } finally { sounds.stop(); attribution.stop(); match.release(); root.deleteRecursively() }
    }
}
