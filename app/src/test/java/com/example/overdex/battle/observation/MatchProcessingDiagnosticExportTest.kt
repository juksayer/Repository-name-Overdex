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
        }
    }
}
