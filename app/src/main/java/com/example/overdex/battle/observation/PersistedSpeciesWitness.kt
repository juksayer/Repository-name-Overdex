package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Reads one active-species signal from the verified badge-name crop.
 *
 * The Timeline is the source of truth. This witness receives the Match's
 * dedicated post-publication species-crop stream and conflates it to the latest
 * crop, so unrelated HP and timer captures cannot delay the first identity.
 */
class PersistedSpeciesWitness(
    private val artifactStore: FileCropArtifactStore,
    private val crop: BattleCropContract,
    private val side: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null

    // Availability is testimony about a running worker, not a promise made by
    // the dispatcher while its coroutine is still waiting to be scheduled.
    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val sourceId = SourceId(observerId.id)
        // The raw OCR output is evidence even when it cannot yet be resolved to
        // a known species. Keep it out of the legacy BattleInterpreter source
        // IDs so an uncertain OCR string can never become an identity by itself.
        val rawOcrSourceId = SourceId("${observerId.id}_OCR")
        // Artifact reads and ML Kit continuations must not compete with the
        // capture pipeline for Default dispatcher threads.
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { witnessScope ->
            // Preserve a compact chronological sampling of the badge, rather than
            // asking OCR to chase every frame or collapsing an entire switch sequence
            // to its final screen. The source artifacts remain complete in Timeline;
            // this queue only schedules recognition work.
            // Identity must reflect the current combatant. Every crop remains in the
            // Timeline; this scheduler deliberately replaces stale recognition work
            // with the latest durable crop while ML Kit is busy.
            val scheduledCrops = Channel<RealityArticle>(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            witnessScope.launch {
                match.activeSpeciesCropArticles.collect { article ->
                    val captured = article.payload as? CropCaptured ?: return@collect
                    if (captured.cropProvenance.cropName != crop.cropName) return@collect
                    val at = article.monotonicTimeNanos ?: return@collect
                    if (match.speciesChecks.windowFor(side, at) != null) scheduledCrops.trySend(article)
                }
            }

            witnessScope.launch {
                match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                // Pay model/catalog initialization during ARMED, before battle entry.
                try { SpeciesNameRecognizer.warmUp() }
                catch (error: Exception) { Log.w("ACTIVE_SPECIES", "OCR warm-up failed; live checks will retry", error) }
                var knownSpeciesNames: Set<String>? = runCatching { match.pokemonKnowledge.getAllSpeciesNames() }.getOrNull()
                // Worker readiness and active checks are reported independently of capture artifacts.
                witnessScope.launch {
                    var previous: Boolean? = null
                    while (true) {
                        val operating = match.speciesChecks.isChecking(side)
                        if (previous != operating) {
                            match.custody.submitAvailability(sourceId, operating, System.currentTimeMillis())
                            previous = operating
                        }
                        delay(50)
                    }
                }
                Log.d("ACTIVE_SPECIES", "started side=$side crop=${crop.cropName}")
                var lastRawOcrReadings: List<String> = emptyList()


                for (article in scheduledCrops) {
                    val window = match.speciesChecks.windowFor(side, article.monotonicTimeNanos ?: continue) ?: continue
                    val recognition = recognize(article, match, knownSpeciesNames)
                    if (recognition == null) {
                        match.speciesChecks.read(side, window.id, null, article.id.value)
                        continue
                    }
                    knownSpeciesNames = recognition.knownNames
                    val readingSignature = recognition.rawReadings.map { "${it.treatment}:${it.text}" }
                    if (readingSignature != lastRawOcrReadings) {
                        lastRawOcrReadings = readingSignature
                        recognition.rawReadings.forEach { reading ->
                            match.custody.submitTestimony(
                                sourceId = SourceId("${rawOcrSourceId.id}_${reading.treatment}"),
                                payload = RawTestimony(reading.text),
                                timestamp = article.perceivedAt,
                                confidence = recognition.confidence,
                                evidenceReferences = listOf(article.id.value),
                                monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                            )
                        }
                    }
                    val speciesName = recognition.speciesName
                    val refs = match.speciesChecks.read(side, window.id, speciesName, article.id.value)
                    if (speciesName != null && refs != null) {
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = RawTestimony(speciesName),
                            timestamp = article.perceivedAt,
                            confidence = recognition.confidence,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                        )
                        val species = match.pokemonKnowledge.getPokemonByName(speciesName)
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActivePokemonSpeciesWitnessed(side, speciesName, species?.id),
                            timestamp = article.perceivedAt,
                            confidence = recognition.confidence,
                            evidenceReferences = refs,
                            monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                        )
                    }
                }
            }
        }
    }

    private suspend fun recognize(
        article: RealityArticle,
        match: Match,
        cachedNames: Set<String>?
    ): SpeciesRecognition? {
        val captured = article.payload as? CropCaptured ?: return null
        val bitmap = artifactStore.loadVerifiedPng(captured.artifact) ?: run {
            Log.w("ACTIVE_SPECIES", "missing verified artifact side=$side crop=${captured.cropProvenance.cropName}")
            return null
        }
        return try {
            val names = cachedNames ?: match.pokemonKnowledge.getAllSpeciesNames()
            val rawReadings = SpeciesNameRecognizer.recognizeCandidates(bitmap) {
                SpeciesTextResolver.resolve(it, names) != null
            }
            if (rawReadings.isEmpty()) return null
            val resolved = rawReadings.firstNotNullOfOrNull { reading ->
                SpeciesTextResolver.resolve(reading.text, names)?.let { species -> reading to species }
            }
            val rawText = resolved?.first?.text ?: rawReadings.first().text
            val speciesName = resolved?.second
            Log.d(
                "ACTIVE_SPECIES",
                "side=$side reads=${rawReadings.joinToString { "${it.treatment}:${it.text}" }} " +
                    "resolved=${speciesName ?: "none"} catalogue=${names.size}"
            )
            SpeciesRecognition(rawText, rawReadings, speciesName, names, confidence = null)
        } catch (error: Exception) {
            Log.e("ACTIVE_SPECIES", "recognition failed side=$side crop=${captured.cropProvenance.cropName}", error)
            null
        } finally {
            bitmap.recycle()
        }
    }

    override fun stop() {
        val closingScope = scope ?: return
        closingScope.launch {
            // Finish the already accepted, sampled badge crops before the
            // recording is sealed. Crop evidence itself is never discarded.
            delay(5_000)
            activeMatch?.custody?.submitAvailability(
                sourceId = SourceId(observerId.id),
                available = false,
                timestamp = System.currentTimeMillis()
            )
            closingScope.cancel("Species witness drained after capture stopped")
            if (scope === closingScope) {
                scope = null
                activeMatch = null
            }
        }
    }

    private data class SpeciesRecognition(
        val rawText: String,
        val rawReadings: List<SpeciesNameRecognizer.Candidate>,
        val speciesName: String?,
        val knownNames: Set<String>,
        val confidence: Float?
    )

    companion object {

        fun player(artifactStore: FileCropArtifactStore) = PersistedSpeciesWitness(
            artifactStore, BattleCropContracts.playerActiveSpeciesText, ActivePokemonSide.PLAYER,
            ObserverId("PLAYER_SPECIES_WITNESS", ObserverSource.SCREEN_CAPTURE), "Player Species Witness"
        )

        fun opponent(artifactStore: FileCropArtifactStore) = PersistedSpeciesWitness(
            artifactStore, BattleCropContracts.opponentActiveSpeciesText, ActivePokemonSide.OPPONENT,
            ObserverId("SPECIES_WITNESS", ObserverSource.SCREEN_CAPTURE), "Opponent Species Witness"
        )
    }
}
