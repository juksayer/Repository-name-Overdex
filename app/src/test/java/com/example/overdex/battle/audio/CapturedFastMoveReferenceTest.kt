package com.example.overdex.battle.audio

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Cross-battle/device evidence: none of these query samples is a runtime reference. */
class CapturedFastMoveReferenceTest {
    private val assets = File("src/main/assets/fast_move_sounds")
    private val fixtures = File("src/androidTest/assets/fast_move_20261101")
    private fun json(file: File) = Json.parseToJsonElement(file.readText()).jsonObject
    private fun samples(file: File) = json(file).getValue("samples").jsonArray.map { it.jsonObject }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun references(): List<FastMoveSoundReference> = json(File(assets, "manifest.json"))
        .getValue("references").jsonArray.map { it.jsonObject }.map { entry ->
            val bytes = File(assets, entry.getValue("file").jsonPrimitive.content).readBytes()
            assertEquals(entry.getValue("sha256").jsonPrimitive.content, sha(bytes))
            FastMoveSoundReference(entry.getValue("moveName").jsonPrimitive.content, sha(bytes),
                requireNotNull(WavPcm16.decode(bytes)))
        }

    @Test fun `separate battle and music device queries identify only legal moves while quiet queries stay unconfirmed`() {
        val matcher = FastMoveReferenceMatcher(references())
        val provenance = samples(File(assets, "captured-provenance-20261101.json"))
            .associateBy { it.getValue("moveName").jsonPrimitive.content }
        val results = samples(File(fixtures, "manifest.json")).map { entry ->
            val bytes = File(fixtures, entry.getValue("file").jsonPrimitive.content).readBytes()
            val derived = entry.getValue("derived").jsonObject
            assertEquals(derived.getValue("sha256").jsonPrimitive.content, sha(bytes))
            val query = requireNotNull(WavPcm16.decode(bytes))
            assertEquals(derived.getValue("sampleRateHz").jsonPrimitive.int, query.sampleRateHz)
            assertEquals(derived.getValue("frameCount").jsonPrimitive.int,
                query.samples.size / query.channelCount)
            val allowed = entry.getValue("legalMoves").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val ranked = matcher.measure(query, allowed)
            assertTrue("Impossible alternatives were scored", ranked.all { it.moveName in allowed })
            val expected = entry.getValue("expectedMove").jsonPrimitive.contentOrNull
            val accepted = FastMoveReferenceMatcher.accepts(ranked)
            val id = entry.getValue("id").jsonPrimitive.content
            if (expected == null) {
                assertFalse("Quiet capture was positively identified: $id", accepted)
            } else {
                assertTrue("Held-out capture was not separated: $id: $ranked", accepted)
                assertEquals(id, expected, ranked.first().moveName)
                val reference = provenance.getValue(expected)
                assertNotEquals("A test query reused the reference PCM", reference.getValue("derived").jsonObject
                    .getValue("pcmSha256").jsonPrimitive.content, derived.getValue("pcmSha256").jsonPrimitive.content)
                val trainingMatch = reference.getValue("sourceArchive").jsonObject.getValue("matchId").jsonPrimitive.content
                val queryMatch = entry.getValue("sourceArchive").jsonObject.getValue("matchId").jsonPrimitive.content
                assertNotEquals("Acceptance needs a distinct recorded Match, not the training occurrence", trainingMatch, queryMatch)
            }
            buildJsonObject {
                put("id", id); put("expected", expected); put("accepted", accepted)
                put("winner", ranked.firstOrNull()?.moveName)
                put("similarity", ranked.firstOrNull()?.similarity)
                put("changingSimilarity", ranked.firstOrNull()?.changingSimilarity)
            }
        }
        assertEquals(8, results.size)
        File("build/reports/captured-fast-move-20261101.json").apply {
            parentFile?.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("scope", "Six held-out battle/device query excerpts and two quiet comparisons; not live precision/recall")
                put("matcherVersion", FastMoveReferenceMatcher.VERSION)
                put("results", JsonArray(results))
            }))
        }
    }

    @Test fun `captured references retain exact source frame provenance and remain recognition only`() {
        val manifest = json(File(assets, "manifest.json")).getValue("references").jsonArray
            .map { it.jsonObject }.associateBy { it.getValue("file").jsonPrimitive.content }
        val sourceSamples = samples(File(assets, "captured-provenance-20261101.json"))
        assertEquals(setOf("Bubble", "Confusion", "Rock Smash"),
            sourceSamples.map { it.getValue("moveName").jsonPrimitive.content }.toSet())
        sourceSamples.forEach { sample ->
            val file = sample.getValue("runtimeFile").jsonPrimitive.content
            val entry = manifest.getValue(file)
            assertFalse(entry.getValue("replayEligible").jsonPrimitive.boolean)
            assertEquals("recognition_excerpt", entry.getValue("purpose").jsonPrimitive.content)
            val source = sample.getValue("source").jsonObject
            val trim = sample.getValue("trim").jsonObject
            val derived = sample.getValue("derived").jsonObject
            assertEquals(derived.getValue("sha256").jsonPrimitive.content, sha(File(assets, file).readBytes()))
            assertEquals(source.getValue("sampleRateHz"), derived.getValue("sampleRateHz"))
            assertEquals(source.getValue("channelCount"), derived.getValue("channelCount"))
            assertEquals(trim.getValue("frameCount"), derived.getValue("frameCount"))
            assertTrue(trim.getValue("startFrame").jsonPrimitive.int + trim.getValue("frameCount").jsonPrimitive.int <=
                source.getValue("frameCount").jsonPrimitive.int)
            assertTrue(sample.getValue("sourceArchive").jsonObject.getValue("sha256").jsonPrimitive.content.matches(Regex("[a-f0-9]{64}")))
            assertEquals("PLAYBACK_POKEMON_GO", source.getValue("captureSource").jsonPrimitive.content)
        }
    }
}
