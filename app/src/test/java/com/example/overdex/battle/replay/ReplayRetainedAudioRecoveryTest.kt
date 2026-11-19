package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.StatChangeSoundMatcher
import com.example.overdex.battle.audio.WavPcm16
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class ReplayRetainedAudioRecoveryTest {
    private val effect = noise(20_000, 1)
    private val ref = ReplayAudioReference("Example charge", true, null, "reference", Pcm16Audio(8000, 1, effect))
    private val other = ReplayAudioReference("Competing fast", false, null, "other", Pcm16Audio(8000, 1, noise(16_000, 2)))
    private val first = wav(effect.copyOfRange(0, 9600))
    private val second = wav(effect.copyOfRange(9600, 19200))
    private fun archive() = MatchArchive(matchId = "test", articles = listOf(event("start", 0, ArchivedMatchStarted),
        capture("one", 10*S, first), capture("two", 10*S + 1_200_000_000, second), event("end", 16*S, ArchivedMatchEnded("WIN"))))
    private fun recover(a: MatchArchive = archive(), refs: List<ReplayAudioReference> = listOf(ref, other),
                        read: (ArchivedAudioCaptured) -> ByteArray? = { if (it.artifactPath == "one") first else second }) =
        ReplayRetainedAudioRecovery.recover(a, refs, read = read)
    @Test fun `reopen reuses completed fits and retries an unavailable archive`() = kotlinx.coroutines.runBlocking {
        val a = archive()
        val directory = java.nio.file.Files.createTempDirectory("ending-audio-test").toFile()
        try {
            val zip = java.io.ByteArrayOutputStream().also { bytes ->
                java.util.zip.ZipOutputStream(bytes).use { out ->
                    for ((id, clip) in listOf("one" to first, "two" to second)) {
                        out.putNextEntry(java.util.zip.ZipEntry(id))
                        out.write(clip)
                        out.closeEntry()
                    }
                }
            }.toByteArray()
            val asset = wav(effect)
            val sha = ReplayRetainedAudioRecovery.sha(asset)
            val rivalAsset = wav(other.audio.samples)
            val rivalSha = ReplayRetainedAudioRecovery.sha(rivalAsset)
            val cache = requireNotNull(ReplayReviewCache.open(directory, 1, a) { zip.inputStream() })
            val move = com.example.overdex.model.Move("Example charge", com.example.overdex.model.PokemonType.NORMAL, 20, 40, false)
            val rival = move.copy(name = other.name, isFast = true)
            var reads = 0
            suspend fun recognize(available: Boolean, window: LongRange? = null) =
                ArchivedEndingAudioRecognizer.recognize(a, listOf(move, rival), emptySet(), {
                    reads++
                    if (available) zip.inputStream() else null
                }, window, null, { path -> when (path) {
                    "fast_move_sounds/rival.wav" -> rivalAsset
                    "fast_move_sounds/manifest.json" -> """{"references":[{"moveName":"Competing fast","file":"rival.wav","sha256":"$rivalSha"}]}""".encodeToByteArray()
                    "charged_move_sounds/example.wav" -> asset
                    "charged_move_sounds/manifest.json" -> """{"references":[{"moveName":"Example charge","file":"example.wav","sha256":"$sha"}]}""".encodeToByteArray()
                    else -> """{"references":[]}""".encodeToByteArray()
                } }, cache)
            assertTrue(recognize(false).unavailableArtifacts.isNotEmpty())
            val full = recognize(true)
            assertEquals(listOf("one", "two"), full.charges.single().fragments.map { it.articleId })
            assertEquals(full, recognize(true))
            assertEquals(2, reads)
            assertTrue(recognize(true, 10*S..10*S).charges.isEmpty())
            assertEquals(3, reads)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun overlappingWholeCryFitsRetainSourcesAndKeepSeparateCallsDistinct() {
        fun clip(id: String, onset: Long) = ReplayAudioFragment(id, onset - S, "audio-$id",
            "cry-reference", S, 0, 1_648_500_000L, .85f)
        val entry = clip("entry", 61_546_000_000L)
        val end = clip("departure", 90_542_000_000L)
        val overlap = clip("overlap", end.estimatedOnsetNanos + 5_380_000)
        val fits = listOf(entry, overlap, end, end)
        fun recover(fragments: List<ReplayAudioFragment>) =
            ReplayRetainedAudioRecovery.wholeCryOccurrences(mapOf(134 to fragments))
        val occurrences = recover(fits)
        assertEquals(occurrences, recover(fits.reversed()))
        assertEquals(listOf(listOf(entry), listOf(end, overlap)), occurrences.map { it.fragments })
        // Nearby rival waveforms and successively drifting fits cannot merge two calls.
        assertEquals(3, recover(fits + overlap.copy(referenceSha256 = "other-reference")).size)
        assertEquals(3, recover(fits + clip("drift", end.estimatedOnsetNanos + 11_000_000)).size)
        assertEquals(3, ReplayRetainedAudioRecovery.wholeCryOccurrences(mapOf(134 to fits, 135 to listOf(end))).size)
    }

    @Test fun `independent nonoverlapping segments recover one effect without source mutation`() {
        val a = archive(); val before = MatchArchiveSerializer.serialize(a)
        val result = recover(a)
        assertEquals(1, result.charges.size)
        assertEquals(10*S, result.charges.single().estimatedOnsetNanos)
        assertEquals(setOf("one", "two"), result.charges.single().fragments.map { it.articleId }.toSet())
        assertEquals(result, recover(a.copy(articles = a.articles.reversed())))
        assertEquals(before, MatchArchiveSerializer.serialize(a))
    }
    @Test fun `fast reference winner remains available to causal inference without inventing an attack`() {
        val result = recover(refs = listOf(ref.copy(charged = false), other))
        assertTrue(result.charges.isEmpty())
        assertEquals("Example charge", result.fastEffects.single().moveName)
        assertEquals(2, result.fastEffects.single().fragments.size)
    }
    @Test fun `one clip duplicate clips and ambiguous reference do not make a charge`() {
        val a = archive()
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "two" })).charges.isEmpty())
        val duplicate = capture("duplicate", 10*S, first)
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "two" } + duplicate), read = { first }).charges.isEmpty())
        assertTrue(recover(refs = listOf(ref, ref.copy(name = "Different possible move"))).charges.isEmpty())
        assertTrue(recover(refs = listOf(ref)).charges.isEmpty())
    }
    @Test fun `missing corrupt and wrongly timed artifacts are explicit and cannot become recoveries`() {
        assertEquals(2, recover(read = { null }).unavailableArtifacts.size)
        assertTrue(recover(read = { it.let { first.copyOf().apply { this[50] = (this[50] + 1).toByte() } } }).charges.isEmpty())
        val a = archive()
        val late = a.copy(articles = a.articles.map { if (it.articleId == "two") it.copy(monotonicTimeNanos = 14*S) else it })
        assertTrue(recover(late).charges.isEmpty())
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "end" })).charges.isEmpty())
    }
    @Test fun `partial cry has its own provenance and cannot supply its reason`() {
        val cry = ReplayAudioReference("Cry 148", false, 148, "cry", Pcm16Audio(8000, 1, effect.copyOfRange(0, 8000)))
        val tail = wav(effect.copyOfRange(3200, 8000) + noise(4800, 4))
        val a = archive().copy(articles = listOf(event("start", 0, ArchivedMatchStarted), capture("cry", 12*S, tail), event("end", 16*S, ArchivedMatchEnded("WIN"))))
        val recovered = recover(a, listOf(cry, other)) { tail }
        assertEquals(148, recovered.cries.single().speciesId)
        assertEquals(11_600_000_000L, recovered.cries.single().estimatedOnsetNanos)
        assertTrue(recovered.charges.isEmpty())
    }
    @Test fun `overlapping long clip windows preserve original offsets and do not duplicate cries`() {
        val cry = ReplayAudioReference("Cry 148", false, 148, "cry", Pcm16Audio(8000, 1, effect.copyOfRange(0, 8000)))
        val data = noise(13 * 8000, 41)
        effect.copyInto(data, destinationOffset = 3 * 8000 + 4000, endIndex = 8000)
        val bytes = wav(data)
        val base = capture("long", 10*S, bytes)
        val clip = base.copy(payload = (base.payload as ArchivedAudioCaptured).copy(durationNanos = 13*S))
        val a = archive().copy(articles = listOf(event("start", 0, ArchivedMatchStarted), clip, event("end", 30*S, ArchivedMatchEnded("WIN"))))
        val result = recover(a, listOf(cry, other)) { bytes }
        assertEquals(1, result.cries.size)
        val found = result.cries.single()
        assertEquals(13_500_000_000L, found.estimatedOnsetNanos)
        assertEquals("long", found.fragments.single().articleId)
        assertEquals(10*S, found.fragments.single().captureAtNanos)
        assertTrue(result.unavailableArtifacts.isEmpty())
    }
    @Test fun `a complete long cry crosses search windows but never spans artifacts or wins an acoustic tie`() {
        fun reference(name: String, species: Int?, asset: String): ReplayAudioReference {
            val bytes = File("src/main/assets/$asset").readBytes()
            return ReplayAudioReference(name, species == null, species, ReplayRetainedAudioRecovery.sha(bytes),
                requireNotNull(WavPcm16.decodeBundledReference(bytes)))
        }
        val cry = reference("Cradily", 346, "cry_references/pv346.wav")
        val charge = reference("Night Slash", null, "charged_move_sounds/400-0_night_slash.wav")
        val rival = reference("Sceptile", 254, "cry_references/pv254.wav")
        val pool = listOf(cry, charge, rival)
        fun samples(r: ReplayAudioReference) = StatChangeSoundMatcher.mono8k(r.audio)
        val pcm = samples(cry)
        val offset = 7100
        fun scan(signal: ShortArray, refs: List<ReplayAudioReference> = pool): List<ReplayRecoveredCry> {
            val data = noise(offset, 42) + signal
            val bytes = wav(data)
            val row = capture("original", 10*S, bytes)
            val clip = row.copy(payload = (row.payload as ArchivedAudioCaptured).copy(durationNanos = data.size * S / 8000))
            val a = archive().copy(articles = listOf(event("go", 0, ArchivedMatchStarted), clip,
                event("charge", 10*S, ArchivedChargedMoveEnergySpent("PLAYER", "Greninja", "Night Slash", 35, "announced")),
                event("end", 16*S, ArchivedMatchEnded("WIN"))))
            return recover(a, refs) { bytes }.terminalWholeCries
        }
        val found = scan(pcm).single()
        assertEquals(10*S + offset*S/8000, found.estimatedOnsetNanos)
        assertEquals(pcm.size*S/8000, found.referenceDurationNanos)
        assertEquals(setOf("original"), found.fragments.map { it.articleId }.toSet())
        assertTrue(scan(pcm.copyOf(9600)).isEmpty())
        assertTrue(scan(samples(charge)).none { it.speciesId == 346 })
        assertTrue(scan(samples(rival)).none { it.speciesId == 346 })
        assertTrue(scan(pcm, pool + cry.copy(name = "Indistinguishable move", speciesId = null)).isEmpty())
    }

    @Test fun `a saved team member needs a battle appearance to enter the cry comparison`() {
        val roster = ArchivedPlayerTeamSlotConfigured(1, "Turtonator", 776, "Incinerate", listOf("Dragon Pulse"))
        val a = archive().copy(articles = archive().articles + listOf(
            event("roster", 0, roster),
            event("old", 0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)).copy(matchId = "other"),
            event("entry", 5*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sceptile", 254)),
            event("use", 12*S, ArchivedChargedMoveEnergySpent("PLAYER", "Greninja", "Night Slash", 35, "announced")),
            event("after", 17*S, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711))))
        val crop = ReplayIdentityObservation("OPPONENT", "Cradily", 346, 11*S, "ARCHIVED_CROP")
        assertEquals(setOf("Sceptile", "Greninja", "Cradily"), MatchReplayModel.audioParticipantNames(a, listOf(crop)))
    }

    @Test fun `early appearance permits bounded entry audio without admitting unrelated early clips`() {
        val a = archive().copy(articles = listOf(event("start", 0, ArchivedMatchStarted),
            event("identity", 20*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134)),
            capture("entry", 21*S, first), capture("unrelated", 30*S, first),
            event("end", 100*S, ArchivedMatchEnded("WIN"))))
        assertEquals(listOf("entry"), ReplayRetainedAudioRecovery.selectedArticles(a).map { it.articleId })
    }
    private fun capture(id: String, at: Long, bytes: ByteArray) = event(id, at, ArchivedAudioCaptured(id,
        ReplayRetainedAudioRecovery.sha(bytes), bytes.size.toLong(), "audio/wav", 8000, 1, 1_200_000_000,
        "FAST_MOVE", "PLAYBACK_POKEMON_GO"))
    private fun event(id: String, at: Long, p: ArchivedTestimonyPayload) = ArchivedRealityArticle(id, "test", 0, 0, "TEST", p,
        emptyList(), null, null, emptyList(), at)
    private fun noise(n: Int, seed: Long) = Random(seed).let { r -> ShortArray(n) { (r.nextInt(16000) - 8000).toShort() } }
    private fun wav(samples: ShortArray): ByteArray = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + samples.size*2); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(samples.size*2); samples.forEach { putShort(it) }
    }.array()
    private companion object { const val S = 1_000_000_000L }
}
