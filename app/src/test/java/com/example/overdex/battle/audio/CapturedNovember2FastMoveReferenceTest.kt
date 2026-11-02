package com.example.overdex.battle.audio

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Human labels and acoustic acceptance remain separate, including shared-signature rejections. */
class CapturedNovember2FastMoveReferenceTest {
    private val assets = File("src/main/assets/fast_move_sounds")
    private val fixtures = File("src/androidTest/assets/fast_move_20261102")
    private fun json(file: File) = Json.parseToJsonElement(file.readText()).jsonObject
    private fun samples(file: File) = json(file).getValue("samples").jsonArray.map { it.jsonObject }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private val captured by lazy { samples(File(assets, "captured-provenance-20261102.json")) }
    private fun references(): List<FastMoveSoundReference> = json(File(assets, "manifest.json"))
        .getValue("references").jsonArray.map { it.jsonObject }.map { entry ->
            val bytes = File(assets, entry.getValue("file").jsonPrimitive.content).readBytes()
            assertEquals(entry.getValue("sha256").jsonPrimitive.content, sha(bytes))
            FastMoveSoundReference(entry.getValue("moveName").jsonPrimitive.content, sha(bytes),
                requireNotNull(WavPcm16.decode(bytes)))
        }

    @Test fun `held out attacks retain legal comparisons and ambiguity is not promoted to identity`() {
        val matcher = FastMoveReferenceMatcher(references())
        val sourceByMove = captured.associateBy { it.getValue("moveName").jsonPrimitive.content }
        val entries = samples(File(fixtures, "manifest.json"))
        val rows = entries.map { entry ->
            val id = entry.getValue("id").jsonPrimitive.content
            val bytes = File(fixtures, entry.getValue("file").jsonPrimitive.content).readBytes()
            val derived = entry.getValue("derived").jsonObject
            assertEquals(id, derived.getValue("sha256").jsonPrimitive.content, sha(bytes))
            val query = requireNotNull(WavPcm16.decode(bytes))
            val legal = entry.getValue("legalMoves").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertTrue("$id requires active legal alternatives", legal.size > 1)
            val ranked = matcher.measure(query, legal)
            assertTrue("$id scored an impossible move", ranked.all { it.moveName in legal })
            val expected = entry.getValue("expectedMove").jsonPrimitive.contentOrNull
            val accepted = FastMoveReferenceMatcher.accepts(ranked)
            assertEquals("$id: $ranked", expected != null, accepted)
            if (expected != null) assertEquals(id, expected, ranked.first().moveName)
            val known = entry.getValue("knownRecordedMove").jsonPrimitive.contentOrNull
            if (known != null) {
                assertTrue("$id recording label must be legal", known in legal)
                assertEquals("holdout", entry.getValue("role").jsonPrimitive.content)
                val reference = sourceByMove.getValue(known)
                assertNotEquals("$id reused reference PCM", reference.getValue("derived").jsonObject
                    .getValue("pcmSha256").jsonPrimitive.content, derived.getValue("pcmSha256").jsonPrimitive.content)
                val queryStart = entry.getValue("source").jsonObject.getValue("perceivedAt").jsonPrimitive.long +
                    entry.getValue("trim").jsonObject.getValue("windowOffsetNanos").jsonPrimitive.long / 1_000_000
                val referenceStart = reference.getValue("source").jsonObject.getValue("perceivedAt").jsonPrimitive.long +
                    reference.getValue("trim").jsonObject.getValue("windowOffsetNanos").jsonPrimitive.long / 1_000_000
                assertTrue("$id must be a different attack, not the other phone's same occurrence",
                    kotlin.math.abs(queryStart - referenceStart) > 5_000)
            }
            buildJsonObject {
                put("id", id); put("knownRecordedMove", known); put("expectedMove", expected)
                put("accepted", accepted); put("winner", ranked.firstOrNull()?.moveName)
                put("similarity", ranked.firstOrNull()?.similarity)
                put("changingSimilarity", ranked.firstOrNull()?.changingSimilarity)
            }
        }
        assertEquals(setOf("Mud Slap", "Poison Sting", "Steel Wing"), entries.mapNotNull {
            it.getValue("knownRecordedMove").jsonPrimitive.contentOrNull }.toSet())
        assertTrue("Include recorded quiet rejection comparisons", entries.any {
            it.getValue("knownRecordedMove").jsonPrimitive.contentOrNull == null })
        File("build/reports/captured-fast-move-20261102.json").apply {
            parentFile?.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("scope", "Selected held-out attacks and quiet comparisons, not live precision/recall; Poison Sting is from one battle")
                put("matcherVersion", FastMoveReferenceMatcher.VERSION); put("results", JsonArray(rows))
            }))
        }
    }

    @Test fun `new references preserve frame provenance and remain excluded from replay sounds`() {
        val manifest = json(File(assets, "manifest.json")).getValue("references").jsonArray
            .map { it.jsonObject }.associateBy { it.getValue("file").jsonPrimitive.content }
        assertEquals(setOf("Mud Slap", "Poison Sting", "Steel Wing"),
            captured.map { it.getValue("moveName").jsonPrimitive.content }.toSet())
        captured.forEach { sample ->
            val name = sample.getValue("runtimeFile").jsonPrimitive.content
            val entry = manifest.getValue(name)
            assertEquals("recognition_excerpt", entry.getValue("purpose").jsonPrimitive.content)
            assertFalse(entry.getValue("replayEligible").jsonPrimitive.boolean)
            val source = sample.getValue("source").jsonObject
            val trim = sample.getValue("trim").jsonObject
            val derived = sample.getValue("derived").jsonObject
            assertEquals(derived.getValue("sha256").jsonPrimitive.content, sha(File(assets, name).readBytes()))
            assertEquals(source.getValue("sampleRateHz"), derived.getValue("sampleRateHz"))
            assertEquals(source.getValue("channelCount"), derived.getValue("channelCount"))
            assertEquals(trim.getValue("frameCount"), derived.getValue("frameCount"))
            assertEquals(trim.getValue("endFrameExclusive").jsonPrimitive.int,
                trim.getValue("startFrame").jsonPrimitive.int + trim.getValue("frameCount").jsonPrimitive.int)
            assertTrue(trim.getValue("endFrameExclusive").jsonPrimitive.int <= source.getValue("frameCount").jsonPrimitive.int)
            assertEquals("PLAYBACK_POKEMON_GO", source.getValue("captureSource").jsonPrimitive.content)
        }
    }
}
