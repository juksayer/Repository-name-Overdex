package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchOutcomeLabelWitnessed
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ObservationSessionStoppedByUser
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.ScreenIgnoredByUser
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Exercises real custody/publication ordering; later positive results bound every rejection check. */
class PartialOutcomePhraseLifecycleTest {
    @Test fun `weak fragments from before and after restart cannot combine`() = runBlocking {
        val fixture = Fixture()
        try {
            val old = fixture.read("before-restart", 100, "W")
            fixture.control(ObservationRestartedByUser, 200)
            val first = fixture.read("after-restart-1", 300, "W")
            val second = fixture.read("after-restart-2", 550, "W")
            val label = fixture.awaitAcceptedLabel()
            assertEquals(second.monotonicTimeNanos, label.monotonicTimeNanos)
            assertEquals(listOf(first.id.value, second.id.value), label.evidenceReferences)
            assertFalse(label.evidenceReferences.orEmpty().contains(old.id.value))
            assertEquals(2, (label.payload as MatchOutcomeLabelWitnessed).supportingFrameCount)
            fixture.assertEndFrom(label)
        } finally { fixture.close() }
    }

    @Test fun `cached and late arriving pre restart strong readings cannot end the new attempt`() = runBlocking {
        val fixture = Fixture()
        try {
            val cachedOldCrop = fixture.crop("cached-old", 100)
            fixture.control(ObservationRestartedByUser, 300)
            fixture.ocr(cachedOldCrop, "oU WINy")
            val lateOldCrop = fixture.crop("late-old", 200)
            fixture.ocr(lateOldCrop, "oU WINy")
            val fresh = fixture.read("fresh-result", 500, "oU WINy")
            val label = fixture.awaitAcceptedLabel()
            assertEquals(fresh.monotonicTimeNanos, label.monotonicTimeNanos)
            assertEquals(listOf(fresh.id.value), label.evidenceReferences)
            assertEquals(1, (label.payload as MatchOutcomeLabelWitnessed).supportingFrameCount)
            fixture.assertEndFrom(label)
        } finally { fixture.close() }
    }

    @Test fun `ignore and stop block results until a newer restart and clear earlier weak support`() = runBlocking {
        listOf(ScreenIgnoredByUser, ObservationSessionStoppedByUser).forEach { pause ->
            val fixture = Fixture()
            try {
                fixture.read("old-weak", 100, "W")
                fixture.control(pause, 200)
                fixture.read("blocked-strong", 300, "oU WINy")
                fixture.control(ObservationRestartedByUser, 150) // Older than the pause: cannot resume it.
                fixture.read("still-blocked-strong", 350, "oU WINy")
                fixture.control(ObservationRestartedByUser, 500)
                val first = fixture.read("resumed-weak-1", 600, "W")
                val second = fixture.read("resumed-weak-2", 850, "W")
                val label = fixture.awaitAcceptedLabel()
                assertEquals(pause.toString(), second.monotonicTimeNanos, label.monotonicTimeNanos)
                assertEquals(listOf(first.id.value, second.id.value), label.evidenceReferences)
                fixture.assertEndFrom(label)
            } finally { fixture.close() }
        }
    }

    @Test fun `a stale ignore or stop arriving after a newer restart cannot pause its result sequence`() = runBlocking {
        listOf(ScreenIgnoredByUser, ObservationSessionStoppedByUser).forEach { stalePause ->
            val fixture = Fixture()
            try {
                fixture.control(stalePause, 200)
                fixture.control(ObservationRestartedByUser, 400)
                val first = fixture.read("current-weak-1", 500, "W")
                fixture.control(stalePause, 300) // Arrives later, but belongs to the superseded lifecycle.
                val second = fixture.read("current-weak-2", 750, "W")
                val label = fixture.awaitAcceptedLabel()
                assertEquals(stalePause.toString(), second.monotonicTimeNanos, label.monotonicTimeNanos)
                assertEquals(listOf(first.id.value, second.id.value), label.evidenceReferences)
                fixture.assertEndFrom(label)
            } finally { fixture.close() }
        }
    }

    private class Fixture {
        private val custody = InMemoryTestimonyCustody()
        private val timeline = InMemoryRealityTimeline()
        private val match = Match("partial-result-lifecycle", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        private val start = System.nanoTime()
        private val witness = PersistedOutcomePhraseWitness(
            accepts = { MatchOutcomeTextResolver.resolve(it) == MatchOutcomePhrase.WIN },
            observerId = ObserverId("YOU_WIN_WITNESS", ObservationSource.SCREEN_CAPTURE),
            name = "You Win",
            allowPartialOutcome = true,
        ).also { it.start(match) }

        suspend fun control(payload: TestimonyPayload, milliseconds: Long) =
            publish("USER_OBSERVATION_CONTROL", payload, start + milliseconds * 1_000_000L)

        suspend fun read(name: String, milliseconds: Long, text: String): RealityArticle =
            ocr(crop(name, milliseconds), text)

        suspend fun crop(name: String, milliseconds: Long): RealityArticle = publish("MATCH_OUTCOME_CAPTURE",
            CropCaptured(CropArtifactReference("artifacts/crops/$name.png", "a".repeat(64), 1),
                BattleCropProvenance("MatchOutcomeCrop", 672, 1493, BattleCropBounds(100, 641, 564, 858))),
            start + milliseconds * 1_000_000L)

        suspend fun ocr(crop: RealityArticle, text: String): RealityArticle {
            val bounds = if (text == "W") OcrBox(230f, 50f, 310f, 140f) else OcrBox(0f, 50f, 464f, 140f)
            val reading = OcrTextEvidence("RAW", text, text,
                listOf(OcrWordEvidence(text, 0, bounds)), 672, null)
            return publish("MATCH_OUTCOME_TEXT_WITNESS", RawTestimony(text, reading),
                crop.monotonicTimeNanos!!, listOf(crop.id.value))
        }

        private suspend fun publish(source: String, payload: TestimonyPayload, sourceAt: Long,
            references: List<String> = emptyList()): RealityArticle {
            val record = custody.submitTestimony(SourceId(source), payload, sourceAt / 1_000_000L,
                null, references, sourceAt)
            withTimeout(5_000) { match.awaitAcceptedEvidencePublished() }
            return timeline.getArticles().single { it.sequenceNumber == record.sequenceNumber }
        }

        suspend fun awaitAcceptedLabel(): RealityArticle = withTimeout(5_000) {
            while (timeline.getArticles().none { it.payload is MatchEnded }) delay(5)
            match.awaitAcceptedEvidencePublished()
            timeline.getArticles().single { it.payload is MatchOutcomeLabelWitnessed }
        }

        fun assertEndFrom(label: RealityArticle) {
            val end = timeline.getArticles().single { it.payload is MatchEnded }
            assertEquals(BattleResult.WIN, (end.payload as MatchEnded).result)
            assertEquals(label.monotonicTimeNanos, end.monotonicTimeNanos)
            assertEquals(label.perceivedAt, end.perceivedAt)
            assertEquals(listOf(label.id), end.predecessorIds)
        }

        fun close() { witness.stop(); match.release() }
    }
}
