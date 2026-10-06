package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SpeciesAudioArchiveTest {
    @Test fun `latency evidence survives JSON export import`() {
        val payload = SpeciesCheckMeasured(ActivePokemonSide.OPPONENT, 3, "IDENTITY_DELIVERED",
            "ENTRY", 100, 1_600_000_000, 1_500_000_000, "Sneasel")
        val archived = RealityArticleArchiveMapper.map(article(payload)).payload
        val restored = Json.decodeFromString<ArchivedTestimonyPayload>(Json.encodeToString(archived))
        assertEquals(archived, restored)
        assertTrue((restored as ArchivedSpeciesCheckMeasured).elapsedNanos > restored.targetNanos)
    }
    @Test fun `input status survives JSON export import`() {
        val archived = RealityArticleArchiveMapper.map(article(AudioInputStatus("PLAYBACK_POKEMON_GO", "QUIET_OR_UNAVAILABLE"))).payload
        assertEquals(archived, Json.decodeFromString<ArchivedTestimonyPayload>(Json.encodeToString(archived)))
    }
    @Test fun `older microphone archives default their capture source without losing fields`() {
        val old = """{"type":"audio_captured","artifactPath":"audio.wav","sha256":"abc","byteCount":44,"mediaType":"audio/wav","sampleRateHz":16000,"channelCount":1,"durationNanos":1000000000,"cueKind":"COUNTDOWN_GO"}"""
        val restored = Json.decodeFromString<ArchivedTestimonyPayload>(old) as ArchivedAudioCaptured
        assertEquals("MICROPHONE", restored.captureSource)
        assertNull(restored.peakAmplitude)
    }
    @Test fun `playback provenance and signal level survive export import`() {
        val archived: ArchivedTestimonyPayload = ArchivedAudioCaptured("audio.wav", "abc", 44, "audio/wav", 48000, 1,
            1_200_000_000, "SPECIES_ENTRY", "PLAYBACK_POKEMON_GO", 0.45f)
        assertEquals(archived, Json.decodeFromString<ArchivedTestimonyPayload>(Json.encodeToString(archived)))
    }
    private fun article(payload: TestimonyPayload) = RealityArticle(ArticleId("a"), 1, 2, SourceId("test"), payload,
        matchId = MatchId("m"), monotonicTimeNanos = 1)
}
