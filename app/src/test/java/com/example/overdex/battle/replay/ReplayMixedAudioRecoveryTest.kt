package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.StatChangeSoundMatcher
import com.example.overdex.battle.audio.WavPcm16
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.*

class ReplayMixedAudioRecoveryTest {
    private fun reference(name: String, file: String): ReplayAudioReference {
        val bytes = File("src/main/assets/fast_move_sounds/$file").readBytes()
        return ReplayAudioReference(name, false, null, ReplayRetainedAudioRecovery.sha(bytes),
            requireNotNull(WavPcm16.decodeBundledReference(bytes)))
    }
    private val incinerate by lazy { reference("Incinerate", "510-0_incinerate_fast.wav") }
    private val water by lazy { reference("Water Gun", "055-0_water_gun.wav") }

    @Test fun `hidden power and silence cannot masquerade as the mixed incinerate`() {
        val hidden = reference("Hidden Power", "237-0_hidden_power.wav")
        val mono = StatChangeSoundMatcher.mono8k(hidden.audio)
        assertTrue(mono.size >= 9600)
        for (clip in listOf(mono.copyOfRange(0, 9600), ShortArray(9600))) {
            assertTrue(ReplayMixedAudioRecovery.measure(Pcm16Audio(8000, 1, clip), "control", 0,
                "audio", listOf(incinerate)).isEmpty())
        }
    }

    @Test fun `available isolated competing reference windows do not match incinerate`() {
        val dir = File("src/main/assets/fast_move_sounds")
        val entries = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject.getValue("references").jsonArray
        var compared = 0
        for (entry in entries) {
            val item = entry.jsonObject
            val name = item.getValue("moveName").jsonPrimitive.content
            if (name == "Incinerate") continue
            val bytes = File(dir, item.getValue("file").jsonPrimitive.content).readBytes()
            assertEquals(item.getValue("sha256").jsonPrimitive.content, ReplayRetainedAudioRecovery.sha(bytes))
            val pcm = WavPcm16.decodeBundledReference(bytes) ?: continue
            val mono = StatChangeSoundMatcher.mono8k(pcm)
            if (mono.size < 9600) continue
            for (offset in 0..mono.size - 9600 step 4800) {
                compared++
                val matches = ReplayMixedAudioRecovery.measure(Pcm16Audio(8000, 1, mono.copyOfRange(offset, offset + 9600)),
                    "competitor", 0, "audio", listOf(incinerate))
                assertTrue("$name at $offset was mistaken for Incinerate", matches.isEmpty())
            }
        }
        assertTrue("The sweep must exercise multiple references", compared > 40)
    }

    @Test fun `original ending clips recover both overlapping sounds without changing archive`() {
        val path = System.getenv("OVERDEX_SLIVER_VIDEO_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path)); val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe", hash)
        ZipFile(file).use { zip ->
            val archive = MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
            val before = MatchArchiveSerializer.serialize(archive)
            val selected = ReplayRetainedAudioRecovery.selectedArticles(archive).map { it.articleId }.toSet()
            for ((id, expected) in listOf("78efbf14-3181-4b50-bb8a-7f36715ec942" to 22_700_000L,
                "9b7c58fc-f54f-44da-93ae-b362211d3264" to -134_600_000L)) {
                val article = archive.articles.single { it.articleId == id }
                assertTrue("Ending audio must be selected", id in selected)
                val payload = article.payload as ArchivedAudioCaptured
                val bytes = zip.getInputStream(zip.getEntry(payload.artifactPath)).readBytes()
                assertEquals(payload.sha256, ReplayRetainedAudioRecovery.sha(bytes))
                val candidates = ReplayMixedAudioRecovery.measure(requireNotNull(WavPcm16.decode(bytes)),
                    id, article.monotonicTimeNanos!!, payload.sha256, listOf(incinerate, water))
                val fire = candidates.single { it.moveName == "Incinerate" }
                assertTrue(candidates.any { it.moveName == "Water Gun" })
                assertEquals(expected.toDouble(), (fire.estimatedOnsetNanos - article.monotonicTimeNanos!!).toDouble(), 6_000_000.0)
                assertEquals(2, fire.fragments.size)
                assertTrue(kotlin.math.abs(fire.fragments[0].referenceOffsetNanos - fire.fragments[1].referenceOffsetNanos) >= 400_000_000L)
            }
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun `living switch can contain incinerate without proving a faint`() {
        val path = System.getenv("OVERDEX_MIXED_CONTROL_AUDIO")
        assumeTrue(!path.isNullOrBlank())
        val pcm = requireNotNull(WavPcm16.decode(File(requireNotNull(path)).readBytes()))
        assertEquals(8000, pcm.sampleRateHz)
        val at = (100.2 * 8000).toInt()
        val clip = Pcm16Audio(8000, 1, pcm.samples.copyOfRange(at, at + 9600))
        val result = ReplayMixedAudioRecovery.measure(clip, "living-switch", 0, "audio", listOf(incinerate, water))
        assertTrue("A real switch can contain the same move sound", result.any { it.moveName == "Incinerate" })
        // Candidate type contains no faint, identity or ownership assertion.
        assertEquals("living-switch", result.first { it.moveName == "Incinerate" }.fragments.first().articleId)
    }
}
