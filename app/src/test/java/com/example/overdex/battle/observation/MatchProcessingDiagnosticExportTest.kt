package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MatchProcessingDiagnosticExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `localized cry timings retain original monotonic clocks for the most recent positives`() {
        val directory = temporary.newFolder("cry-profile")
        val diagnostics = MatchProcessingDiagnostics({ 0 }, { 0 })
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("cry-timing", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(), processingDiagnostics = diagnostics)
        val first = MatchProcessingDiagnostics.CryPositiveTiming(
            speciesId = 346, catchUp = true, onsetNanos = 36_058_000_000L,
            windowEndNanos = 38_260_000_000L, bufferedNanos = 38_261_000_000L,
            referencesStartedNanos = 43_140_000_000L, referencesReadyNanos = 43_150_000_000L,
            analysisStartNanos = 52_000_000_000L,
            matchDoneNanos = 52_110_000_000L, preservedNanos = 52_116_000_000L,
            captureSubmittedNanos = 52_117_000_000L, captureReceiptNanos = 54_360_000_000L,
            crySubmittedNanos = 54_361_000_000L, captureSequence = 100L, crySequence = 101L,
        )
        try {
            val limit = MatchProcessingDiagnostics.MAX_CRY_POSITIVES
            for (i in 0..limit) diagnostics.recordCryPositive(
                first.copy(captureSequence = 100L + 2 * i, crySequence = 101L + 2 * i))
            MatchProcessingDiagnosticExport.write(directory, match)
            val report = Json.parseToJsonElement(directory.listFiles()!!.single().readText()).jsonObject
            val timing = report.getValue("localizedCryTiming").jsonObject
            assertEquals(limit + 1, timing.getValue("totalAccepted").jsonPrimitive.int)
            assertEquals(limit, timing.getValue("retentionLimit").jsonPrimitive.int)
            val rows = timing.getValue("recent").jsonArray
            assertEquals(limit, rows.size)
            val row = rows.first().jsonObject
            assertEquals(102L, row.getValue("captureSequence").jsonPrimitive.long)
            assertEquals(103L, row.getValue("crySequence").jsonPrimitive.long)
            assertEquals(101L + 2 * limit, rows.last().jsonObject.getValue("crySequence").jsonPrimitive.long)
            assertEquals(346, row.getValue("speciesId").jsonPrimitive.int)
            assertTrue(row.getValue("catchUp").jsonPrimitive.boolean)
            val clockKeys = listOf("onsetNanos", "windowEndNanos", "bufferedNanos",
                "referencesStartedNanos", "referencesReadyNanos", "analysisStartNanos", "matchDoneNanos",
                "preservedNanos", "captureSubmittedNanos", "captureReceiptNanos", "crySubmittedNanos")
            assertEquals(listOf(first.onsetNanos, first.windowEndNanos, first.bufferedNanos,
                first.referencesStartedNanos, first.referencesReadyNanos, first.analysisStartNanos, first.matchDoneNanos,
                first.preservedNanos, first.captureSubmittedNanos, first.captureReceiptNanos, first.crySubmittedNanos),
                clockKeys.map { row.getValue(it).jsonPrimitive.long })
            assertTrue(custody.getRecords().isEmpty())
            assertTrue(timeline.getArticles().isEmpty())
        } finally { match.release(); DroidballOverlayPresentation.reset() }
    }

    @Test fun `out of band summaries are bounded and do not submit evidence`() {
        val directory = temporary.newFolder("profiles")
        repeat(10) { index ->
            val custody = InMemoryTestimonyCustody()
            val timeline = InMemoryRealityTimeline()
            val diagnostics = MatchProcessingDiagnostics({ 0 }, { 0 })
            val match = Match("export-$index", custody=custody, realityTimeline=timeline,
                pokemonKnowledge=FakePokemonKnowledge(), processingDiagnostics=diagnostics)
            try {
                diagnostics.begin(7, "fixture").complete(7)
                MatchProcessingDiagnosticExport.write(directory, match)
                assertTrue(custody.getRecords().isEmpty())
                assertTrue(timeline.getArticles().isEmpty())
                assertTrue(directory.listFiles()!!.size <= 8)
            } finally { match.release(); DroidballOverlayPresentation.reset() }
        }
        directory.listFiles()!!.forEach { file ->
            val report = Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals(1, report.getValue("completedTestimony").jsonPrimitive.int)
            assertEquals(0, report.getValue("acceptedTestimony").jsonPrimitive.int)
            assertEquals(1, report.getValue("stages").jsonObject.size)
            val listeners = report.getValue("articleListeners").jsonObject
            assertEquals(64, listeners.getValue("sampleEvery").jsonPrimitive.int)
            assertTrue(listeners.getValue("listeners") is JsonObject)
            assertEquals(64, report.getValue("preBattleArticleListeners").jsonObject.getValue("sampleEvery").jsonPrimitive.int)
        }
    }
}
