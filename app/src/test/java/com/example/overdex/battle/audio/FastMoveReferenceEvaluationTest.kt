package com.example.overdex.battle.audio

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** A reproducible catalogue/fixture audit, explicitly not an estimate of live battle accuracy. */
class FastMoveReferenceEvaluationTest {
    @Test fun `audit reference separation recorded mixtures and desktop matching cost`() {
        val root = File("src/main/assets/fast_move_sounds")
        val references = Json.parseToJsonElement(File(root, "manifest.json").readText())
            .jsonObject.getValue("references").jsonArray.map { value ->
                val p = value.jsonObject
                FastMoveSoundReference(p.getValue("moveName").jsonPrimitive.content,
                    p.getValue("sha256").jsonPrimitive.content,
                    WavPcm16.decode(File(root, p.getValue("file").jsonPrimitive.content).readBytes())!!)
            }
        val matcher = FastMoveReferenceMatcher(references)
        val timings = mutableListOf<Double>()
        val rows = references.map { reference ->
            val mono = StatChangeSoundMatcher.mono8k(reference.audio)
            // The live reader always compares a 1.2-second window, including quiet time
            // around short effects. Keep all catalogue competitors eligible in this audit.
            val query = mono.copyOf(9_600).map { (it * .4f).toInt().toShort() }.toShortArray()
            val start = System.nanoTime()
            val ranked = matcher.measureMono8k(query)
            timings += (System.nanoTime() - start) / 1_000_000.0
            val accepted = FastMoveReferenceMatcher.accepts(ranked)
            buildJsonObject {
                put("expected", reference.moveName)
                put("winner", ranked.firstOrNull()?.moveName)
                put("accepted", accepted)
                put("correct", accepted && ranked.firstOrNull()?.moveName == reference.moveName)
                put("similarity", ranked.firstOrNull()?.similarity)
                put("runnerUp", ranked.getOrNull(1)?.moveName)
            }
        }
        val fixtureRoot = File("src/androidTest/assets/fast_move_20261015")
        val fixtures = Json.parseToJsonElement(File(fixtureRoot, "manifest.json").readText()).jsonArray.map { value ->
            val p = value.jsonObject
            val expected = p.getValue("expectedReference").jsonPrimitive.contentOrNull
            val file = p.getValue("file").jsonPrimitive.content
            val ranked = matcher.measure(WavPcm16.decode(File(fixtureRoot, file).readBytes())!!)
            val accepted = FastMoveReferenceMatcher.accepts(ranked)
            buildJsonObject {
                put("file", file); put("expected", expected); put("accepted", accepted)
                put("winner", ranked.firstOrNull()?.moveName)
                put("passes", if (expected == null) !accepted else accepted && ranked.firstOrNull()?.moveName == expected)
            }
        }
        val sorted = timings.sorted()
        val report = buildJsonObject {
            put("scope", "Reference excerpts and existing regression fixtures; not human-labelled live precision/recall")
            put("references", references.size)
            put("referenceCorrect", rows.count { it.getValue("correct").jsonPrimitive.boolean })
            put("referenceRejected", rows.count { !it.getValue("accepted").jsonPrimitive.boolean })
            put("referenceWrongAccepted", rows.count { it.getValue("accepted").jsonPrimitive.boolean && !it.getValue("correct").jsonPrimitive.boolean })
            put("desktopMeanMs", timings.average())
            put("desktopMedianMs", sorted[sorted.size / 2])
            put("desktopP95Ms", sorted[((sorted.size - 1) * .95).toInt()])
            put("fixturePasses", fixtures.count { it.getValue("passes").jsonPrimitive.boolean })
            put("fixtureCount", fixtures.size)
            put("referenceResults", JsonArray(rows)); put("fixtureResults", JsonArray(fixtures))
        }
        File("build/reports/fast-move-audio-evaluation.json").apply {
            parentFile?.mkdirs(); writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        }
        assertTrue("An accepted reference was misidentified; see the evaluation report",
            rows.none { it.getValue("accepted").jsonPrimitive.boolean && !it.getValue("correct").jsonPrimitive.boolean })
        assertTrue("A recorded fixture regressed; see the evaluation report",
            fixtures.all { it.getValue("passes").jsonPrimitive.boolean })
    }
}
