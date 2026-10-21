package com.example.overdex.battle.archive

import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.audio.Pcm16WavEncoder
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.ResultMusicCorroboration
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.*
import com.example.overdex.model.BattleResult
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class ResultMusicArchiveTest {
    private val id = MatchId("music-test")
    private fun article(name: String, payload: TestimonyPayload, refs: List<String> = emptyList(), at: Long = 100) = RealityArticle(
        ArticleId(name), at * 1000, at * 1000 + 20, SourceId("TEST"), payload, evidenceReferences = refs,
        matchId = id, monotonicTimeNanos = at * 1_000_000_000)

    @Test fun `late music refresh keeps one archive one match end and verified compact audio`() {
        val root = Files.createTempDirectory("result-music-").toFile()
        try {
            val timeline = InMemoryRealityTimeline()
            val end = article("end", MatchEnded(BattleResult.WIN))
            timeline.append(end)
            val name = completedMatchArchiveName(id, end.perceivedAt)
            val archive = File(root, name)
            fun save() {
                val staged = File(root, ".$name.tmp")
                staged.outputStream().use { MatchArchiveExporter.export(timeline, id, it, root) }
                publishStagedMatchArchive(staged, archive)
            }
            save()
            assertEquals(1, archive.inputStream().use { MatchArchivePackageReader.read(it) }.articles.size)
            val wav = Pcm16WavEncoder.encode(ByteArray(64_000) { (it % 83).toByte() }, 8000, 1)
            val artifact = requireNotNull(FileAudioArtifactStore(root).preserveWav(wav))
            val audio = article("audio", AudioCaptured(artifact, 8000, 1, 4_000_000_000,
                "MATCH_RESULT_MUSIC", "PLAYBACK_POKEMON_GO"), at = 106)
            val music = article("music", MatchResultMusicMeasured("WIN", "reference", .98f, .65f,
                1_000_000_000, 4_000_000_000), listOf("audio"), 106)
            val inference = ResultMusicCorroboration()
            inference.accept(end)
            val comparison = inference.accept(music).single()
            timeline.append(audio); timeline.append(music)
            timeline.append(article("comparison", comparison.payload, listOf("music", "end"), 106))
            save()
            assertEquals(1, root.listFiles()!!.count { it.name.endsWith(".odxmatch.zip") })
            val read = archive.inputStream().use { MatchArchivePackageReader.read(it) }
            assertEquals(4, read.articles.size)
            assertEquals(1, read.articles.count { it.payload is ArchivedMatchEnded })
            assertEquals(1, read.articles.count { it.payload is ArchivedMatchResultMusicCompared })
            val json = Json { classDiscriminator = "kind" }
            read.articles.forEach {
                assertEquals(it, json.decodeFromString(ArchivedRealityArticle.serializer(),
                    json.encodeToString(ArchivedRealityArticle.serializer(), it)))
            }
            ZipFile(archive).use { zip ->
                assertArrayEquals(wav, zip.getInputStream(zip.getEntry(artifact.relativePath)).readBytes())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `failed replacement preserves the last valid archive`() {
        val root = Files.createTempDirectory("result-music-failure-").toFile()
        try {
            val archive = File(root, "match.odxmatch.zip").apply { writeText("last good archive") }
            assertThrows(java.io.IOException::class.java) { publishStagedMatchArchive(File(root, "missing.tmp"), archive) }
            assertEquals("last good archive", archive.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun `same second matches have different names and refreshing uses the same name`() {
        val first = completedMatchArchiveName(MatchId("one"), 100_000)
        assertEquals(first, completedMatchArchiveName(MatchId("one"), 100_000))
        assertNotEquals(first, completedMatchArchiveName(MatchId("two"), 100_000))
        assertThrows(IllegalArgumentException::class.java) { completedMatchArchiveName(MatchId("../elsewhere"), 100_000) }
    }
}
