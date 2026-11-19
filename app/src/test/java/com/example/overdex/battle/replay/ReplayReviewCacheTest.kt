package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchive
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplayReviewCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `persisted sound fits retain source times and only apply to identical review inputs`() = runBlocking {
        val archive = MatchArchive(matchId = "match", articles = emptyList())
        suspend fun open(media: String = "audio", install: Long = 1, timeline: MatchArchive = archive) =
            requireNotNull(ReplayReviewCache.open(temp.root, install, timeline) { media.byteInputStream() })
        val args = listOf("ending-audio", "9", "11", "Dragon Pulse", "134,776", "Dragon Pulse")
        val cache = open()
        val key = cache.key(args)
        val fragment = ReplayAudioFragment("article", 42L, "clip", "ref", 3, 2, 12, .97f)
        val cry = ReplayRecoveredCry(134, 43L, 18L, listOf(fragment))
        val audio = ReplayAudioRecovery(cries = listOf(cry), terminalWholeCries = listOf(cry),
            partialCryCandidates = listOf(ReplayPartialCryCandidate(134, 45L, listOf(fragment), .6f)))
        cache.write(key, audio)
        assertEquals(audio, open().read<ReplayAudioRecovery>(key))
        for (changed in listOf(open("replacement"), open(install = 2), open(timeline = archive.copy(mediaIncluded = false)))) {
            assertNull(changed.read<ReplayAudioRecovery>(changed.key(args)))
        }
        for (i in args.indices) {
            assertNull(cache.read<ReplayAudioRecovery>(cache.key(args.toMutableList().also { it[i] += "-different" })))
        }
        val entry = File(temp.root, "$key.json")
        entry.writeText(entry.readText().replace("article", "wrongid"))
        assertNull(cache.read<ReplayAudioRecovery>(key))
        cache.write(key, audio)
        assertEquals(audio, cache.read<ReplayAudioRecovery>(key))
    }

    @Test fun `unreadable sources bypass cache and writes stay bounded`() = runBlocking {
        val archive = MatchArchive(matchId = "match", articles = emptyList())
        assertNull(ReplayReviewCache.open(temp.root, 1, archive) { null })
        val cache = requireNotNull(ReplayReviewCache.open(temp.root, 1, archive) { "zip".byteInputStream() })
        repeat(135) { cache.write(cache.key(listOf("species", it.toString())), listOf("Vaporeon")) }
        assertEquals(128, temp.root.listFiles()!!.size)
        val huge = cache.key(listOf("huge"))
        cache.write(huge, "a".repeat(512 * 1024))
        assertNull(cache.read<String>(huge))
    }
}
