package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.WavPcm16
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReplayPartialCryRecoveryTest {
    private fun ref(name: String, species: Int?, path: String): ReplayAudioReference {
        val bytes = File("src/main/assets", path).readBytes()
        return ReplayAudioReference(name, false, species, ReplayRetainedAudioRecovery.sha(bytes),
            requireNotNull(WavPcm16.decodeBundledReference(bytes)))
    }
    private val vaporeon by lazy { ref("Cry 134", 134, "cry_references/pv134.wav") }
    private val gourgeist by lazy { ref("Cry 711", 711, "cry_references/pv711.wav") }
    private val water by lazy { ref("Water Gun", null, "fast_move_sounds/055-0_water_gun.wav") }
    private val fire by lazy { ref("Incinerate", null, "fast_move_sounds/510-0_incinerate_fast.wav") }
    private val refs get() = listOf(vaporeon, gourgeist, water, fire)

    @Test fun `saved final tail yields one candidate with original source fragments`() {
        val path = System.getenv("OVERDEX_SLIVER_VIDEO_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path)); val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe", hash)
        ZipFile(file).use { zip ->
            val archive = MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
            val before = MatchArchiveSerializer.serialize(archive)
            val article = archive.articles.single { it.articleId == "68fc1d27-7778-4865-a0dd-bca408e84a73" }
            val payload = article.payload as ArchivedAudioCaptured
            val bytes = zip.getInputStream(zip.getEntry(payload.artifactPath)).readBytes()
            assertEquals(payload.sha256, ReplayRetainedAudioRecovery.sha(bytes))
            val fits = ReplayPartialCryRecovery.measure(requireNotNull(WavPcm16.decode(bytes)), article.articleId,
                article.monotonicTimeNanos!!, payload.sha256, refs)
            val candidate = fits.single { it.speciesId == 134 }
            assertEquals(-1_012_000_000.0, (candidate.estimatedOnsetNanos - article.monotonicTimeNanos!!).toDouble(), 5_000_000.0)
            assertEquals(2, candidate.fragments.size)
            assertTrue(candidate.fragments.all { it.articleId == article.articleId && it.audioSha256 == payload.sha256 })
            assertTrue(candidate.fragments.all { it.correlation >= .85f })
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun `Seed Bomb ending distinguishes Vaporeon from move sounds and abstains on a tie`() {
        val path = System.getenv("OVERDEX_SEED_BOMB_PARTIAL_CRY_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        val hash = "d1fff5da3571cdeb4ec05d5e1b6e49954bbd3e68c4c4d7b393a5f6f0fa9c69d5"
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
        ZipFile(file).use { zip ->
            val timeline = zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().use { it.readText() }
            val article = MatchArchiveSerializer.deserialize(timeline).articles.single { it.articleId == "73250def-3107-417a-968b-a32e34d520f7" }
            val capture = article.payload as ArchivedAudioCaptured
            val bytes = zip.getInputStream(zip.getEntry(capture.artifactPath)).use { it.readBytes() }
            assertEquals(capture.sha256, ReplayRetainedAudioRecovery.sha(bytes))
            val pcm = requireNotNull(WavPcm16.decode(bytes))
            fun measure(pool: List<ReplayAudioReference>) = ReplayPartialCryRecovery.measure(pcm,
                article.articleId, requireNotNull(article.monotonicTimeNanos), capture.sha256, pool)
            val pool = refs + ref("Seed Bomb", null, "charged_move_sounds/402-0_seed_bomb.wav") +
                ref("Water Pulse", null, "charged_move_sounds/352-0_water_pulse.wav")
            assertEquals(134, measure(pool).single().speciesId)
            assertTrue(measure(pool + vaporeon.copy(name = "Identical move sound", speciesId = null)).isEmpty())
        }
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun `ambiguous references and missing competition cannot certify a cry`() {
        val pcm = vaporeon.audio
        assertTrue(ReplayPartialCryRecovery.measure(pcm, "clip", 0, "audio", listOf(vaporeon)).isEmpty())
        assertTrue(ReplayPartialCryRecovery.measure(pcm, "clip", 0, "audio",
            listOf(vaporeon, vaporeon.copy(name = "Cry 999", speciesId = 999), water)).isEmpty())
    }

    @Test fun `living switch audio cannot be mistaken for the final vaporeon cry`() {
        val path = System.getenv("OVERDEX_MIXED_CONTROL_AUDIO")
        assumeTrue(!path.isNullOrBlank())
        val pcm = requireNotNull(WavPcm16.decode(File(requireNotNull(path)).readBytes()))
        for (time in listOf(98.4, 99.0, 99.6, 100.2, 100.8)) {
            val at = (time * 8000).toInt()
            val clip = Pcm16Audio(8000, 1, pcm.samples.copyOfRange(at, at + 9600))
            assertTrue(ReplayPartialCryRecovery.measure(clip, "switch", 0, "audio", refs).none { it.speciesId == 134 })
        }
    }
}
