package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Recorded Moondrop pixels; no device calibration or saved-team writes. */
class TeamSelectSchedulingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class Fixture : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        val bitmap = instrumentation.context.assets.open("team_select/oct21/team-select.png")
            .use(BitmapFactory::decodeStream)!!
        private val regions = JSONObject(instrumentation.context.assets.open("team_select/oct21/calibration.json")
            .bufferedReader().use { it.readText() })
        private fun region(key: String) = regions.getJSONObject(key).let {
            AnchorRegion(it.getDouble("x").toFloat(), it.getDouble("y").toFloat(),
                it.getDouble("width").toFloat(), it.getDouble("height").toFloat())
        }
        val calibration = TeamSelectCalibration(
            region("roster_1"), region("roster_2"), region("roster_3"),
            region("roster_name_1"), region("roster_name_2"), region("roster_name_3"),
            region("league_badge"), region("league_text"), region("restrictions"), region("use_this_party"),
        )
        val root = File(context.cacheDir, "team-select-check-${System.nanoTime()}").apply { mkdirs() }
        val store = FileCropArtifactStore(root)
        private val reference = store.preserveEncodedFramePng(instrumentation.context.assets
            .open("team_select/oct21/team-select.png").use { it.readBytes() })!!
        val repository = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val timeline = InMemoryRealityTimeline()
        val match = Match("team-select-check", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = repository)
        val observers = mutableListOf<Observer>()
        fun start(observer: Observer) { observers += observer; observer.start(match) }
        fun crop(contract: TeamSelectCropContract): Bitmap = contract.resolve(calibration, bitmap)!!.bitmap
        fun send(contract: TeamSelectCropContract, at: Long = System.nanoTime()) {
            val crop = contract.resolve(calibration, bitmap)!!
            try {
                match.custody.submitTestimony(SourceId("${contract.cropName}_CAPTURE"),
                    CropCaptured(reference, crop.provenance), System.currentTimeMillis(), null, emptyList(), at)
            } finally { crop.bitmap.recycle() }
        }
        fun parties() = timeline.getArticles().filter { it.payload is TeamSelectPartyWitnessed }
        override fun close() {
            observers.forEach { it.stop() }
            match.release()
            bitmap.recycle()
            // The readers may still be releasing a cancelled native read. Keep
            // their cache artifacts until ordinary app cache cleanup.
        }
    }

    private suspend fun awaitCondition(timeoutMillis: Long = 8_000, condition: () -> Boolean) = withTimeout(timeoutMillis) {
        while (!condition()) delay(20)
    }

    @Test fun stalledFallbackCannotBlockClearButtonAndBadge() = runBlocking {
        Fixture().use { f ->
            val fallbackEntered = CompletableDeferred<Unit>()
            val fallbackRelease = CompletableDeferred<Unit>()
            val buttonEntered = CompletableDeferred<Unit>()
            val buttonRelease = CompletableDeferred<Unit>()
            f.start(PersistedTeamSelectPartyWitness(f.store) { _, priority ->
                if (priority == OcrPriority.NORMAL) {
                    fallbackEntered.complete(Unit)
                    fallbackRelease.await()
                    "GREAT LEAGUE"
                } else {
                    buttonEntered.complete(Unit)
                    buttonRelease.await()
                    "USE THIS PARTY"
                }
            })
            f.send(TeamSelectCropContracts.leagueText)
            withTimeout(8_000) { fallbackEntered.await() }
            // Finish the older label after a newer visual anchor. Derived time
            // must be the newest cited source, not whichever reader finishes last.
            val labelAt = System.nanoTime()
            f.send(TeamSelectCropContracts.useThisParty, labelAt)
            withTimeout(8_000) { buttonEntered.await() }
            delay(100)
            val badgeAt = System.nanoTime()
            f.send(TeamSelectCropContracts.leagueBadge, badgeAt)
            delay(100)
            buttonRelease.complete(Unit)
            awaitCondition { f.parties().isNotEmpty() }
            assertFalse("Fallback is still blocked", fallbackRelease.isCompleted)
            val party = f.parties().single()
            assertEquals(badgeAt, party.monotonicTimeNanos)
            val sources = f.timeline.getArticles().filter { it.id.value in party.evidenceReferences.orEmpty() }
            assertEquals(setOf(TeamSelectCropContracts.leagueBadge.cropName, TeamSelectCropContracts.useThisParty.cropName),
                sources.map { (it.payload as CropCaptured).cropProvenance.cropName }.toSet())
            fallbackRelease.complete(Unit)
            delay(200)
            assertEquals(1, f.parties().size)
        }
    }

    @Test fun queuedButtonFramesCoalesceWithoutDeletingEvidence() = runBlocking {
        Fixture().use { f ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reads = AtomicInteger()
            f.start(PersistedTeamSelectPartyWitness(f.store) { _, _ ->
                if (reads.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    release.await()
                    ""
                } else "USE THIS PARTY"
            })
            f.send(TeamSelectCropContracts.useThisParty)
            withTimeout(8_000) { entered.await() }
            repeat(12) { f.send(TeamSelectCropContracts.useThisParty); delay(10) }
            val latestAt = System.nanoTime()
            f.send(TeamSelectCropContracts.useThisParty, latestAt)
            f.send(TeamSelectCropContracts.leagueBadge, latestAt)
            awaitCondition { f.timeline.getArticles().count { it.payload is CropCaptured } == 15 }
            delay(100) // Let the lightweight collector drain into its one pending slot.
            release.complete(Unit)
            awaitCondition { f.parties().isNotEmpty() }
            assertEquals(2, reads.get())
            val sources = f.timeline.getArticles().filter { it.id.value in f.parties().single().evidenceReferences.orEmpty() }
            assertTrue(sources.all { it.monotonicTimeNanos == latestAt })
            assertEquals("Every captured source remains available", 15,
                f.timeline.getArticles().count { it.payload is CropCaptured })
        }
    }

    @Test fun restartingDiscardsOldReadAndRequiresNewAnchors() = runBlocking {
        Fixture().use { f ->
            val entered = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val reads = AtomicInteger()
            val witness = PersistedTeamSelectPartyWitness(f.store) { _, _ ->
                if (reads.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                } else "USE THIS PARTY"
            }
            f.start(witness)
            f.send(TeamSelectCropContracts.leagueBadge)
            f.send(TeamSelectCropContracts.useThisParty)
            withTimeout(8_000) { entered.await() }
            witness.stop()
            f.match.restartPreBattleObservationAttempt()
            witness.start(f.match)
            withTimeout(8_000) { cancelled.await() }
            f.send(TeamSelectCropContracts.useThisParty)
            awaitCondition { reads.get() == 2 }
            delay(200)
            assertTrue("Old badge must not accept the new scan", f.parties().isEmpty())
            val badgeAt = System.nanoTime()
            f.send(TeamSelectCropContracts.leagueBadge, badgeAt)
            awaitCondition { f.parties().isNotEmpty() }
            val sources = f.timeline.getArticles().filter { it.id.value in f.parties().single().evidenceReferences.orEmpty() }
            assertTrue(sources.all { it.monotonicTimeNanos!! >= f.match.observationAttemptStartedAtNanos })
        }
    }

    @Test fun recordedPartyAndFormsResolve() = runBlocking {
        Fixture().use { f ->
            SharedLatinTextRecognizer.warmUp()
            val species = f.crop(TeamSelectCropContracts.playerRosterName1)
            val fallback = f.crop(TeamSelectCropContracts.leagueText)
            val workers = mutableListOf<Job>()
            val competing = InstrumentationRegistry.getArguments().getString("teamSelectCompetingOcr", "true").toBoolean()
            val phaseAware = InstrumentationRegistry.getArguments().getString("teamSelectPhaseAware", "false").toBoolean()
            val input = object : ObservationInput {
                override val source = SessionSource.LIVE_CAPTURE
                override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                    while (true) { onVisualData(f.bitmap); delay(40) }
                }
            }
            val matcher = TeamSelectSpriteMatcher(instrumentation.targetContext.assets)
            try {
                // The live path has two active-species checks, three roster
                // name checks, and transient announcements sharing one model.
                repeat(if (competing && !phaseAware) 2 else 0) {
                    workers += launch(Dispatchers.Default) {
                        while (isActive) { SpeciesNameRecognizer.recognizeCandidates(species) { false }; delay(100) }
                    }
                }
                if (competing) workers += launch(Dispatchers.Default) {
                    while (isActive) { AnnouncementRecognizer.recognize(fallback, OcrPriority.TRANSIENT); delay(100) }
                }
                repeat(3) { attempt ->
                    if (attempt > 0) {
                        f.observers.forEach { it.stop() }
                        f.observers.clear()
                        f.match.restartPreBattleObservationAttempt()
                    }
                    f.start(PersistedTeamSelectPartyWitness(f.store))
                    f.start(PersistedTeamSelectLeagueWitness(f.store))
                    val cards = listOf(TeamSelectCropContracts.playerRosterSlot1, TeamSelectCropContracts.playerRosterSlot2,
                        TeamSelectCropContracts.playerRosterSlot3)
                    val names = listOf(TeamSelectCropContracts.playerRosterName1, TeamSelectCropContracts.playerRosterName2,
                        TeamSelectCropContracts.playerRosterName3)
                    cards.forEachIndexed { i, c -> f.start(PersistedPlayerTeamRosterSpriteWitness(f.store, matcher, i + 1, c.cropName)) }
                    names.forEachIndexed { i, c -> f.start(PersistedPlayerTeamRosterSlotWitness(f.store, i + 1, c.cropName)) }
                    if (competing && phaseAware) {
                        // Production pipelines still receive every frame, but
                        // menus must not enqueue active-badge OCR. The same
                        // transient announcement load remains for comparison.
                        f.start(LiveOverlaySpeciesPipeline.player(input, BattleCalibration(), f.store, { false }) { true })
                        f.start(LiveOverlaySpeciesPipeline.opponent(input, BattleCalibration(), f.store, { false }) { true })
                    }
                    val start = System.nanoTime()
                    val before = f.timeline.getArticles().size
                    val sender = launch(Dispatchers.Default) {
                        while (isActive) {
                            val at = System.nanoTime()
                            (listOf(TeamSelectCropContracts.leagueBadge, TeamSelectCropContracts.useThisParty) + cards + names +
                                listOf(TeamSelectCropContracts.leagueText, TeamSelectCropContracts.restrictions))
                                .forEach { f.send(it, at) }
                            delay(1_000)
                        }
                    }
                    try {
                        // Desktop emulation/native OCR timings are diagnostic;
                        // the deterministic tests above assert the queue behavior.
                        awaitCondition(45_000) { f.timeline.getArticles().drop(before).any { it.payload is TeamSelectPartyWitnessed } }
                        val partyMillis = (System.nanoTime() - start) / 1_000_000
                        awaitCondition(45_000) { f.match.playerRosterSpecies().toSet() == setOf("Raichu (Alolan)", "Raichu", "Obstagoon") }
                        val rosterMillis = (System.nanoTime() - start) / 1_000_000
                        assertEquals(1, f.timeline.getArticles().drop(before).count { it.payload is TeamSelectPartyWitnessed })
                        val visualSlots = f.timeline.getArticles().drop(before)
                            .filter { it.sourceId.id.endsWith("_VISUAL_WITNESS") }
                            .mapNotNull { it.payload as? PlayerTeamRosterSlotWitnessed }
                            .associate { it.slot to it.speciesName }
                        assertEquals(mapOf(1 to "Raichu (Alolan)", 2 to "Raichu", 3 to "Obstagoon"), visualSlots)
                        assertTrue(f.timeline.getArticles().drop(before).none { it.payload is MatchStarted })
                        if (phaseAware) assertTrue(f.timeline.getArticles().drop(before).none {
                            it.sourceId.id.endsWith("_SPECIES_OVERLAY_PIPELINE_LATENCY") ||
                                (it.payload as? CropCaptured)?.cropProvenance?.cropName in setOf(
                                    BattleCropContracts.playerActiveSpeciesText.cropName,
                                    BattleCropContracts.opponentActiveSpeciesText.cropName,
                                )
                        })
                        instrumentation.sendStatus(0, Bundle().apply {
                            putString("team_select_attempt_$attempt", "competing=$competing phaseAware=$phaseAware partyMs=$partyMillis rosterMs=$rosterMillis")
                        })
                    } finally { sender.cancelAndJoin() }
                }
            } finally {
                workers.forEach { it.cancelAndJoin() }
                species.recycle()
                fallback.recycle()
            }
        }
    }
}
