package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchOutcomeLabelWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrWorkScheduler
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrWordEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Exercises the real crop -> custody -> text -> phrase -> Match end path without native OCR. */
class PersistedOutcomeTextWitnessTest {
    @Test fun `validated no-shields prompt prepares before publication and leaves later result recognition intact`() = runBlocking {
        val prompt = resultText("No Protect Shields remaining!", OcrBox(68f, 55f, 404f, 84f))
        val malformed = resultText("No Prote1 S'lds remainir:", OcrBox(68f, 55f, 404f, 84f))
        lateinit var fixture: Fixture
        val reader = ControlledReader { article ->
            if (article.label() == "no-shields") {
                fixture.clock.addAndGet(3_366_000_000L)
                listOf(prompt, malformed)
            } else listOf(text("YOU WIN!"))
        }
        fixture = Fixture(reader)
        val cues = CopyOnWriteArrayList<AcceptedChargeCaptureCue>()
        fixture.match.acceptedChargeCapturePreparation.attach { cue ->
            assertTrue("The handoff precedes outcome custody submissions", fixture.custody.getRecords().none {
                it.sourceId.id.startsWith("MATCH_OUTCOME_TEXT_WITNESS")
            })
            cues += cue
        }
        try {
            fixture.start()
            val crop = fixture.capture("no-shields", fixture.at, provenance = resultProvenance,
                source = BattleWitnessContracts.matchOutcomeCropCapture.witnessId)
            val request = reader.request(OcrPriority.URGENT)
            request.admit.complete(Unit)
            request.finished.await()
            await { fixture.timeline.getArticles().count { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" } == 2 }
            fixture.match.awaitAcceptedEvidencePublished()
            assertEquals(listOf(AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.ATTACK_INCOMING,
                crop.id.value, fixture.at, fixture.clock.get(), crop.perceivedAt)), cues)
            val raw = fixture.timeline.getArticles().filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            assertEquals(listOf(RawTestimony(prompt.rawText, prompt), RawTestimony(malformed.rawText, malformed)),
                raw.map { it.payload })
            assertTrue(raw.all { it.evidenceReferences == listOf(crop.id.value) &&
                it.monotonicTimeNanos == crop.monotonicTimeNanos && it.perceivedAt == crop.perceivedAt })
            assertTrue(fixture.timeline.getArticles().none { it.payload is MatchEnded })

            fixture.clock.addAndGet(2_000_000_000L)
            val win = fixture.capture("later-win", fixture.clock.get(), wall = 54_321L)
            reader.request(OcrPriority.URGENT, index = 1).admit.complete(Unit)
            fixture.awaitEnd()
            assertEquals(1, cues.size)
            val end = fixture.timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(win.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
        } finally { fixture.close() }
    }

    @Test fun `unvalidated outcome text cannot prepare charges or consume a later valid prompt`() = runBlocking {
        val invalid = listOf(text("No Protect Shields remaining!"), resultText("Get Ready!"),
            resultText("NICE"), resultText("Use a Protect Shield?"), resultText("No Protect Shields"))
        val prompt = resultText("No Protect Shields remaining!", OcrBox(68f, 55f, 404f, 84f))
        val reader = ControlledReader { article ->
            if (article.label() == "invalid") invalid else listOf(prompt)
        }
        val fixture = Fixture(reader)
        val cues = CopyOnWriteArrayList<AcceptedChargeCaptureCue>()
        fixture.match.acceptedChargeCapturePreparation.attach(cues::add)
        try {
            fixture.clock.addAndGet(1_000_000L)
            fixture.start()
            fixture.capture("invalid", fixture.at + 1_000_000L, provenance = resultProvenance,
                source = BattleWitnessContracts.matchOutcomeCropCapture.witnessId)
            reader.request(OcrPriority.URGENT).also { it.admit.complete(Unit); it.finished.await() }
            await { fixture.timeline.getArticles().count { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" } == invalid.size }
            assertTrue(cues.isEmpty())
            // An older valid capture remains usable after newer invalid/blank passes.
            fixture.clock.addAndGet(10_000_000L)
            val valid = fixture.capture("valid", fixture.at, provenance = resultProvenance,
                source = BattleWitnessContracts.matchOutcomeCropCapture.witnessId)
            reader.request(OcrPriority.URGENT, index = 1).also { it.admit.complete(Unit); it.finished.await() }
            await { cues.isNotEmpty() }
            assertEquals(listOf(valid.id.value), cues.map { it.cropArticleId })
        } finally { fixture.close() }
    }

    @Test fun `late native no-shields completion cannot reopen capture after stop or match end`() = runBlocking {
        for (endMatch in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reader = ControlledReader {
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    listOf(resultText("No Protect Shields remaining!", OcrBox(68f, 55f, 404f, 84f)))
                }
            }
            val fixture = Fixture(reader)
            val cues = CopyOnWriteArrayList<AcceptedChargeCaptureCue>()
            fixture.match.acceptedChargeCapturePreparation.attach(cues::add)
            try {
                fixture.start()
                fixture.capture("late-prompt", fixture.at, provenance = resultProvenance,
                    source = BattleWitnessContracts.matchOutcomeCropCapture.witnessId)
                val request = reader.request(OcrPriority.URGENT)
                request.admit.complete(Unit)
                withTimeout(5_000) { entered.await() }
                if (endMatch) {
                    fixture.custody.submitTestimony(SourceId("YOU_WIN_WITNESS"), RawTestimony("YOU WIN!"),
                        54_321L, null, emptyList(), fixture.at + 1_000_000_000L)
                    fixture.awaitEnd()
                } else fixture.witness.stop()
                release.complete(Unit)
                withTimeout(5_000) { request.finished.await() }
                fixture.match.awaitAcceptedEvidencePublished()
                assertTrue(cues.isEmpty())
                assertTrue(fixture.timeline.getArticles().none { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" })
            } finally { release.complete(Unit); fixture.close() }
        }
    }

    @Test fun `visually promising concession frame gets urgent OCR before blank scenery with its own source time`() = runBlocking {
        val reader = ControlledReader(inspector = { article ->
            if (article.label() == "concession-win") TransientTextProbe.Reading(.9f, true, 1L, null)
            else TransientTextProbe.Reading(0f, false, 0L, null)
        }) { article ->
            if (article.label() == "concession-win") listOf(text("YOU WIN!")) else emptyList()
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            val win = fixture.capture("concession-win", fixture.clock.get() - 500_000_000L,
                wall = 54_321L, source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val recent = reader.request(OcrPriority.URGENT)
            assertTrue("A promising probe must not wait in the routine lane", reader.requests.none { it.priority == OcrPriority.NORMAL })
            assertTrue(reader.reads.isEmpty())
            fixture.clock.addAndGet(1_000_000_000L)
            fixture.capture("newer-blank", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val normal = reader.request(OcrPriority.URGENT, index = 1)
            recent.admit.complete(Unit)
            fixture.awaitEnd()
            withTimeout(5_000) { normal.finished.await() }
            assertTrue("The decisive promoted probe cancels its waiting blank fallback", normal.cancelled)

            assertEquals(listOf(win.id), reader.reads.map { it.id })
            val articles = fixture.timeline.getArticles()
            val raw = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            val phrase = articles.single { it.sourceId.id == "YOU_WIN_WITNESS" }
            val end = articles.single { it.payload is MatchEnded }
            listOf(raw, phrase, end).forEach {
                assertEquals(win.perceivedAt, it.perceivedAt)
                assertEquals(win.monotonicTimeNanos, it.monotonicTimeNanos)
            }
            assertEquals(listOf(win.id.value), raw.evidenceReferences)
            assertEquals(listOf(raw.id.value), phrase.evidenceReferences)
            assertEquals(listOf(phrase.id), end.predecessorIds)
            val latency = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" }
            val diagnostic = (latency.payload as RawTestimony).data.toString()
            assertTrue(diagnostic.contains("lane=RECENT priority=URGENT"))
            assertTrue(diagnostic.contains("promotedProbe=true"))
        } finally { fixture.close() }
    }

    @Test fun `a queued sparse blank cannot hold a later promising win behind routine admission`() = runBlocking {
        val reader = ControlledReader(inspector = { article ->
            if (article.label() == "promising-win") TransientTextProbe.Reading(.9f, true, 1L, null)
            else TransientTextProbe.Reading(0f, false, 0L, null)
        }) { article ->
            if (article.label() == "promising-win") listOf(text("YOU WIN!")) else emptyList()
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("queued-blank", fixture.clock.get() - 500_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val normal = reader.request(OcrPriority.URGENT)
            val win = fixture.capture("promising-win", fixture.clock.get() - 100_000_000L,
                wall = 54_321L, source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val urgent = reader.request(OcrPriority.URGENT, index = 1)
            assertTrue(reader.reads.isEmpty())

            // The normal request remains blocked; the independently queued
            // urgent request can read the newly observed result immediately.
            urgent.admit.complete(Unit)
            fixture.awaitEnd()
            withTimeout(5_000) { normal.finished.await() }
            assertTrue(normal.cancelled)
            assertEquals(listOf(win.id), reader.reads.map { it.id })
            val articles = fixture.timeline.getArticles()
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(win.perceivedAt, end.perceivedAt)
            assertEquals(win.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(1, articles.count { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" })
            val latency = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" }
            assertTrue((latency.payload as RawTestimony).data.toString().contains("promotedProbe=true"))
        } finally { fixture.close() }
    }

    @Test fun `a weak visual probe retains brief win before newer summary at urgent priority`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "weak-probe") listOf(text("YOU WIN!")) else emptyList()
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            val win = fixture.capture("weak-probe", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val request = reader.request(OcrPriority.URGENT)
            fixture.clock.addAndGet(2_000_000_000L)
            fixture.capture("newer-summary", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            assertTrue("Sparse results must not join the normal OCR backlog", reader.requests.none { it.priority == OcrPriority.NORMAL })
            request.admit.complete(Unit)
            fixture.awaitEnd()
            assertEquals(listOf(win.id), reader.reads.map { it.id })
            assertTrue(reader.requests.all { it.priority == OcrPriority.URGENT })
            val diagnostic = fixture.timeline.getArticles().single {
                it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY"
            }.payload as RawTestimony
            assertTrue(diagnostic.data.toString().contains("selection=RETAINED_FALLBACK"))
            val end = fixture.timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(win.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(win.perceivedAt, end.perceivedAt)
        } finally { fixture.close() }
    }

    @Test fun `weak sparse result passes waiting routine work without replacing its captured pixels`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = CopyOnWriteArrayList<String>()
        val blocker = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { entered.complete(Unit); release.await() }
        }
        entered.await()
        val routine = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) { order += "routine" }
        }
        val reader = object : OutcomeCropReader {
            override suspend fun warmUp() = Unit
            override suspend fun <T> withReadingTurn(priority: OcrPriority,
                block: suspend (OutcomeCropReadingTurn) -> T): T = scheduler.run(priority) {
                block(turn { article ->
                    order += article.label()
                    if (article.label() == "weak-win") listOf(text("YOU WIN!")) else emptyList()
                })
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            val win = fixture.capture("weak-win", fixture.clock.get() - 100_000_000L,
                wall = 54_321L, source = "MATCH_OUTCOME_PROBE_CAPTURE")
            await { scheduler.waitingCount(OcrPriority.URGENT) == 1 }
            fixture.clock.addAndGet(2_000_000_000L)
            fixture.capture("newer-reward", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            release.complete(Unit)
            fixture.awaitEnd()
            assertEquals("weak-win", order.first())
            val end = fixture.timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(win.perceivedAt, end.perceivedAt)
            assertEquals(win.monotonicTimeNanos, end.monotonicTimeNanos)
        } finally {
            release.complete(Unit); blocker.cancel(); routine.cancel(); fixture.close()
        }
    }

    @Test fun `stop during a routine visual inspection cannot enqueue a late native result read`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val reader = ControlledReader(inspector = {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                finished.complete(Unit)
                TransientTextProbe.Reading(.9f, true, 1L, null)
            }
        }) { listOf(text("YOU WIN!")) }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("late-inspection", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            withTimeout(5_000) { started.await() }
            fixture.witness.stop()
            release.complete(Unit)
            withTimeout(5_000) { finished.await() }
            fixture.match.awaitAcceptedEvidencePublished()
            assertTrue(reader.requests.isEmpty())
            assertTrue(fixture.timeline.getArticles().none {
                it.sourceId.id.startsWith("MATCH_OUTCOME_TEXT_WITNESS") || it.payload is MatchEnded
            })
        } finally { release.complete(Unit); fixture.close() }
    }

    @Test fun `stop during a routine native read refuses its late text and diagnostics`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                listOf(text("YOU WIN!"))
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("late-probe-read", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val request = reader.request(OcrPriority.URGENT)
            request.admit.complete(Unit)
            withTimeout(5_000) { started.await() }
            fixture.witness.stop()
            release.complete(Unit)
            withTimeout(5_000) { request.finished.await() }
            fixture.match.awaitAcceptedEvidencePublished()
            assertTrue(request.cancelled)
            assertTrue(fixture.timeline.getArticles().none {
                it.sourceId.id.startsWith("MATCH_OUTCOME_TEXT_WITNESS") || it.payload is MatchEnded
            })
        } finally { release.complete(Unit); fixture.close() }
    }

    @Test fun `routine recovery backlog cannot hide fresh win and its source provenance survives to Match end`() = runBlocking {
        val scheduler = OcrWorkScheduler()
        val backendBusy = CompletableDeferred<Unit>()
        val releaseBackend = CompletableDeferred<Unit>()
        val blocker = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.run(OcrPriority.NORMAL) {
                backendBusy.complete(Unit)
                releaseBackend.await()
            }
        }
        backendBusy.await()
        val reads = CopyOnWriteArrayList<RealityArticle>()
        val reader = object : OutcomeCropReader {
            override suspend fun warmUp() = Unit
            override suspend fun <T> withReadingTurn(priority: OcrPriority,
                block: suspend (OutcomeCropReadingTurn) -> T): T = scheduler.run(priority) {
                block(turn { article ->
                    reads += article
                    if (article.label() == "brief-win") listOf(text("YOU WIN!")) else emptyList()
                })
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 20_000_000_000L)
            fixture.start()
            repeat(30) { fixture.capture("old-$it", fixture.at + it * 1_000_000L) }
            await { scheduler.waitingCount(OcrPriority.NORMAL) == 1 }
            val win = fixture.capture("brief-win", fixture.clock.get() - 200_000_000L, wall = 54_321L)
            await { scheduler.waitingCount(OcrPriority.URGENT) == 1 }
            releaseBackend.complete(Unit)
            blocker.join()
            fixture.awaitEnd()

            // A waiting routine turn may be admitted as the winning read releases
            // the shared backend; it must never be ahead of that live result.
            assertEquals("brief-win", reads.first().label())
            val articles = fixture.timeline.getArticles()
            val raw = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            val phrase = articles.single { it.sourceId.id == "YOU_WIN_WITNESS" }
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(RawTestimony("YOU WIN!", text("YOU WIN!")), raw.payload)
            assertEquals(listOf(win.id.value), raw.evidenceReferences)
            assertEquals(listOf(raw.id.value), phrase.evidenceReferences)
            assertEquals(listOf(phrase.id), end.predecessorIds)
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            listOf(raw, phrase, end).forEach {
                assertEquals(win.perceivedAt, it.perceivedAt)
                assertEquals(win.monotonicTimeNanos, it.monotonicTimeNanos)
            }
            assertEquals(31, articles.count { it.payload is CropCaptured })
            val latency = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" &&
                (it.payload as RawTestimony).data.toString().contains("cropId=${win.id.value} ") }
            assertTrue((latency.payload as RawTestimony).data.toString().contains("lane=RECENT priority=URGENT"))
        } finally {
            releaseBackend.complete(Unit)
            blocker.cancel()
            fixture.close()
        }
    }

    @Test fun `delayed admission expires old frame and selects brief win before a newer blank`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "brief-win") listOf(text("YOU WIN!")) else emptyList()
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("expired-while-waiting", fixture.clock.get() - 1_000_000_000L)
            val waitingTurn = reader.request(OcrPriority.URGENT)
            // Nothing has selected or loaded this frame while the backend is busy.
            assertTrue(reader.reads.isEmpty())
            fixture.clock.addAndGet(4_000_000_000L)
            val win = fixture.capture("brief-win", fixture.clock.get() - 500_000_000L)
            fixture.capture("newer-blank", fixture.clock.get() - 100_000_000L)
            waitingTurn.admit.complete(Unit)
            fixture.awaitEnd()

            assertEquals(listOf(win.id), reader.reads.map { it.id })
            assertEquals(3, fixture.timeline.getArticles().count { it.payload is CropCaptured })
            assertEquals(1, fixture.timeline.getArticles().count { it.payload is MatchEnded })
        } finally { fixture.close() }
    }

    @Test fun `one failed native read does not terminate later result reads`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "bad-frame") error("simulated native read failure")
            listOf(text("YOU WIN!"))
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("bad-frame", fixture.clock.get() - 500_000_000L)
            val badTurn = reader.request(OcrPriority.URGENT)
            fixture.capture("good-frame", fixture.clock.get() - 100_000_000L)
            badTurn.admit.complete(Unit)
            val nextTurn = reader.request(OcrPriority.URGENT, index = 1)
            nextTurn.admit.complete(Unit)
            fixture.awaitEnd()

            assertEquals(listOf("bad-frame", "good-frame"), reader.reads.map { it.label() })
            assertEquals(2, fixture.timeline.getArticles().count {
                it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY"
            })
            assertEquals(1, fixture.timeline.getArticles().count { it.sourceId.id == "YOU_WIN_WITNESS" })
        } finally { fixture.close() }
    }

    @Test fun `stopping during native read refuses its late text and latency publication`() = runBlocking {
        val nativeStarted = CompletableDeferred<Unit>()
        val nativeCompleted = CompletableDeferred<Unit>()
        val reader = ControlledReader {
            // Models a native callback that finishes despite coroutine cancellation.
            withContext(NonCancellable) {
                nativeStarted.complete(Unit)
                nativeCompleted.await()
                listOf(text("YOU WIN!"))
            }
        }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("late-native-result", fixture.clock.get() - 100_000_000L)
            val waitingTurn = reader.request(OcrPriority.URGENT)
            waitingTurn.admit.complete(Unit)
            withTimeout(5_000) { nativeStarted.await() }
            fixture.witness.stop()
            nativeCompleted.complete(Unit)
            withTimeout(5_000) { waitingTurn.finished.await() }
            fixture.match.awaitAcceptedEvidencePublished()

            assertEquals(1, fixture.timeline.getArticles().count { it.payload is CropCaptured })
            assertTrue(fixture.timeline.getArticles().none {
                it.sourceId.id.startsWith("MATCH_OUTCOME_TEXT_WITNESS") ||
                    it.sourceId.id == "YOU_WIN_WITNESS" || it.payload is MatchEnded
            })
        } finally {
            nativeCompleted.complete(Unit)
            fixture.close()
        }
    }

    @Test fun `decisive result cancels both workers and collector so later crops request no backend turns`() = runBlocking {
        val reader = ControlledReader { listOf(text("YOU WIN!")) }
        val fixture = Fixture(reader)
        try {
            fixture.clock.set(fixture.at + 10_000_000_000L)
            fixture.start()
            fixture.capture("recovery-pending", fixture.at)
            val recovery = reader.request(OcrPriority.NORMAL)
            fixture.capture("probe-pending", fixture.clock.get() - 100_000_000L,
                source = "MATCH_OUTCOME_PROBE_CAPTURE")
            val probe = reader.request(OcrPriority.URGENT)
            fixture.capture("fresh-win", fixture.clock.get() - 100_000_000L)
            val recent = reader.request(OcrPriority.URGENT, index = 1)
            recent.admit.complete(Unit)
            fixture.awaitEnd()
            withTimeout(5_000) { recovery.finished.await(); recent.finished.await(); probe.finished.await() }
            assertTrue("Waiting recovery turn must be cancelled", recovery.cancelled)
            assertTrue("Waiting routine probe must be cancelled", probe.cancelled)
            assertEquals(0, reader.activeTurns.get())
            val requestsAfterWin = reader.requests.size

            repeat(20) { index ->
                fixture.capture("after-end-$index", if (index % 2 == 0) fixture.at else fixture.clock.get(),
                    source = if (index % 2 == 0) "MATCH_OUTCOME_PROBE_CAPTURE" else "MATCH_OUTCOME_CAPTURE")
            }
            fixture.match.awaitAcceptedEvidencePublished()
            // Give a leaked SharedFlow collector and its workers a chance to react.
            delay(100)
            assertEquals(requestsAfterWin, reader.requests.size)
            assertEquals(listOf("fresh-win"), reader.reads.map { it.label() })
            assertEquals(23, fixture.timeline.getArticles().count { it.payload is CropCaptured })
        } finally { fixture.close() }
    }

    @Test fun `recorded partial result titles end the match without replacing the raw OCR`() = runBlocking {
        listOf("OOD EFFOR" to BattleResult.LOSS, "oU WINy" to BattleResult.WIN).forEach { (value, result) ->
            val reading = resultText(value)
            val reader = ControlledReader { listOf(reading) }
            val fixture = Fixture(reader, allowPartial = true, result = result)
            try {
                fixture.start()
                val crop = fixture.capture("partial-result", fixture.at, wall = 54_321L,
                    provenance = resultProvenance)
                val turn = reader.request(OcrPriority.URGENT)
                turn.admit.complete(Unit)
                fixture.awaitEnd()
                val articles = fixture.timeline.getArticles()
                val raw = articles.single { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
                val label = articles.single { it.payload is MatchOutcomeLabelWitnessed }
                val end = articles.single { it.payload is MatchEnded }
                assertEquals(value, (raw.payload as RawTestimony).data)
                val accepted = label.payload as MatchOutcomeLabelWitnessed
                assertEquals(value, accepted.rawText)
                assertEquals(reading, accepted.ocr)
                assertEquals(1, accepted.supportingFrameCount)
                assertEquals(result, accepted.result)
                assertEquals(listOf(crop.id.value), raw.evidenceReferences)
                assertEquals(listOf(raw.id.value), label.evidenceReferences)
                assertEquals(listOf(label.id), end.predecessorIds)
                listOf(raw, label, end).forEach {
                    assertEquals(crop.perceivedAt, it.perceivedAt)
                    assertEquals(crop.monotonicTimeNanos, it.monotonicTimeNanos)
                }
                await { reader.activeTurns.get() == 0 }
                val requestCount = reader.requests.size
                fixture.capture("after-partial-end", fixture.at + 1_000_000_000L,
                    provenance = resultProvenance)
                delay(100)
                assertEquals(requestCount, reader.requests.size)
            } finally { fixture.close() }
        }
    }

    @Test fun `two treatments of one W need a second captured frame and retain both source references`() = runBlocking {
        val reading = resultText("W", OcrBox(245f, 74f, 300f, 146f))
        val reader = ControlledReader { listOf(reading, reading.copy(treatment = "WHITE_ISOLATED")) }
        val fixture = Fixture(reader, allowPartial = true)
        try {
            fixture.start()
            val first = fixture.capture("first-W", fixture.at, provenance = resultProvenance)
            val firstTurn = reader.request(OcrPriority.URGENT)
            firstTurn.admit.complete(Unit)
            firstTurn.finished.await()
            fixture.clock.addAndGet(500_000_000L)
            val second = fixture.capture("second-W", fixture.clock.get(), wall = 54_321L,
                provenance = resultProvenance)
            reader.request(OcrPriority.URGENT, index = 1).admit.complete(Unit)
            fixture.awaitEnd()
            val articles = fixture.timeline.getArticles()
            val label = articles.single { it.payload is MatchOutcomeLabelWitnessed }
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(2, (label.payload as MatchOutcomeLabelWitnessed).supportingFrameCount)
            assertEquals(second.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(second.perceivedAt, end.perceivedAt)
            val supports = articles.filter { it.id.value in label.evidenceReferences.orEmpty() }
            assertEquals(2, supports.size)
            assertEquals(setOf(first.id.value, second.id.value), supports.flatMap { it.evidenceReferences.orEmpty() }.toSet())
        } finally { fixture.close() }
    }

    @Test fun `strong partial recovery survives newer unrelated readings and crop cache eviction`() = runBlocking {
        val reader = ControlledReader { article ->
            listOf(resultText(if (article.label() == "brief-loss") "OOD EFFOR" else "REWARD"))
        }
        val fixture = Fixture(reader, allowPartial = true, result = BattleResult.LOSS)
        try {
            fixture.clock.addAndGet(10_000_000_000L)
            fixture.start()
            val resultCrop = fixture.capture("brief-loss", fixture.at, wall = 54_321L,
                provenance = resultProvenance)
            val recovery = reader.request(OcrPriority.NORMAL)
            repeat(260) { index ->
                fixture.capture("newer-reward-$index", fixture.clock.addAndGet(100_000_000L),
                    provenance = resultProvenance)
            }
            val recent = reader.request(OcrPriority.URGENT)
            recent.admit.complete(Unit)
            recent.finished.await()
            recovery.admit.complete(Unit)
            fixture.awaitEnd()
            assertTrue(reader.reads.first().label().startsWith("newer-reward-"))
            assertTrue(reader.reads.any { it.id == resultCrop.id })
            val end = fixture.timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(resultCrop.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(resultCrop.perceivedAt, end.perceivedAt)
            assertEquals(MatchEnded(BattleResult.LOSS), end.payload)
        } finally { fixture.close() }
    }

    @Test fun `small FF in effectiveness text cannot end a match before the later result title`() = runBlocking {
        val reader = ControlledReader { article ->
            if (article.label() == "effectiveness") listOf(resultText("FF", OcrBox(245f, 74f, 270f, 96f)))
            else listOf(resultText("OOD EFFOR"))
        }
        val fixture = Fixture(reader, allowPartial = true, result = BattleResult.LOSS)
        try {
            fixture.start()
            fixture.capture("effectiveness", fixture.at, provenance = resultProvenance)
            val firstTurn = reader.request(OcrPriority.URGENT)
            firstTurn.admit.complete(Unit)
            firstTurn.finished.await()
            fixture.clock.addAndGet(500_000_000L)
            val resultCrop = fixture.capture("loss", fixture.clock.get(), provenance = resultProvenance)
            reader.request(OcrPriority.URGENT, index = 1).admit.complete(Unit)
            fixture.awaitEnd()
            val end = fixture.timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(resultCrop.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(MatchEnded(BattleResult.LOSS), end.payload)
        } finally { fixture.close() }
    }

    private class Fixture(reader: OutcomeCropReader, allowPartial: Boolean = false,
        result: BattleResult = BattleResult.WIN) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("outcome-reader-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val at = System.nanoTime()
        val clock = AtomicLong(at)
        // Run collector continuations inline so source publication is also an
        // enqueue barrier; admission permits, rather than sleeps, control reads.
        val witness = PersistedOutcomeTextWitness(reader, clock::get, dispatcher = Dispatchers.Unconfined)
        private val phrase = PersistedOutcomePhraseWitness(
            { MatchOutcomeTextResolver.resolve(it) == if (result == BattleResult.WIN) MatchOutcomePhrase.WIN else MatchOutcomePhrase.LOSS },
            ObserverId(if (result == BattleResult.WIN) "YOU_WIN_WITNESS" else "GOOD_EFFORT_WITNESS",
                ObservationSource.SCREEN_CAPTURE), "Result", allowPartialOutcome = allowPartial)

        fun start() { phrase.start(match); witness.start(match) }

        suspend fun capture(label: String, sourceTime: Long, wall: Long = 12_345L,
            source: String = "MATCH_OUTCOME_CAPTURE",
            provenance: BattleCropProvenance = BattleCropProvenance(BattleCropContracts.matchOutcome.cropName,
                1080, 2400, BattleCropBounds(100, 400, 900, 600))): RealityArticle {
            // Fake readers use the reference label, and never load a Bitmap or touch ML Kit.
            val record = custody.submitTestimony(SourceId(source), CropCaptured(
                CropArtifactReference("artifacts/crops/$label.png", "a".repeat(64), 1),
                provenance), wall, null, emptyList(), sourceTime)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }

        suspend fun awaitEnd() {
            await { timeline.getArticles().any { it.payload is MatchEnded } }
            match.awaitAcceptedEvidencePublished()
        }

        fun close() { witness.stop(); phrase.stop(); match.release() }
    }

    private class ControlledReader(
        private val inspector: suspend (RealityArticle) -> TransientTextProbe.Reading? = { null },
        private val read: suspend (RealityArticle) -> List<OcrTextEvidence>,
    ) : OutcomeCropReader {
        class Request(val priority: OcrPriority) {
            val admit = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            @Volatile var cancelled = false
        }
        val requests = CopyOnWriteArrayList<Request>()
        val reads = CopyOnWriteArrayList<RealityArticle>()
        val activeTurns = AtomicInteger()
        override suspend fun warmUp() = Unit
        override suspend fun inspect(article: RealityArticle) = inspector(article)
        override suspend fun <T> withReadingTurn(priority: OcrPriority,
            block: suspend (OutcomeCropReadingTurn) -> T): T {
            val request = Request(priority)
            activeTurns.incrementAndGet()
            requests += request
            return try {
                request.admit.await()
                block(turn { article -> reads += article; read(article) })
            } finally {
                request.cancelled = !currentCoroutineContext().isActive
                activeTurns.decrementAndGet()
                request.finished.complete(Unit)
            }
        }

        suspend fun request(priority: OcrPriority, index: Int = 0): Request {
            await { requests.count { it.priority == priority } > index }
            return requests.filter { it.priority == priority }[index]
        }
    }

    companion object {
        private fun RealityArticle.label() = (payload as CropCaptured).artifact.relativePath
            .substringAfterLast('/').removeSuffix(".png")

        private fun text(value: String) = OcrTextEvidence("ORIGINAL", value, value, emptyList(), 1080, null)

        private val resultProvenance = BattleCropProvenance("MatchOutcomeCrop", 672, 1493,
            BattleCropBounds(100, 641, 564, 858))

        private fun resultText(value: String, bounds: OcrBox = OcrBox(52f, 74f, 445f, 146f)) =
            OcrTextEvidence("RAW", value, value, listOf(OcrWordEvidence(value, 0, bounds)), null, null)

        private fun turn(read: suspend (RealityArticle) -> List<OcrTextEvidence>) = object : OutcomeCropReadingTurn {
            override var nativeReadNanos: Long = 0
            override var readCount: Int = 0
            override suspend fun recognize(article: RealityArticle): List<OcrTextEvidence> {
                readCount++
                nativeReadNanos += 100_000_000L
                return read(article)
            }
        }

        private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
            while (!condition()) delay(5)
        }
    }
}
