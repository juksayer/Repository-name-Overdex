package com.example.overdex.battle.archive

import com.example.overdex.battle.artifact.AudioArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.MatchId
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StatChangeArchiveTest {
    @Test fun `a rejected sound check preserves its audio in compact without inventing an effect`() {
        val audio = AudioCaptured(AudioArtifactReference("audio/probe.wav", "sha", 64044), 8000, 1, 4_000_000_000,
            "CHARGED_MOVE_EFFECT", "PLAYBACK_POKEMON_GO", .4f)
        val articles = listOf(article("probe", audio), article("check",
            StatChangeSoundChecked("NO_CONFIDENT_MATCH", "ATTACK_DOWN", .8124f, .8105f), listOf("probe")))
            .map(RealityArticleArchiveMapper::map)
        val selected = MatchArchiveArticleSelector.select(articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(articles, selected)
        val json = Json { classDiscriminator = "kind" }
        val check = selected.last()
        assertEquals(check, json.decodeFromString(ArchivedRealityArticle.serializer(), json.encodeToString(ArchivedRealityArticle.serializer(), check)))
        assertFalse(selected.any { it.payload is ArchivedStatChangeObserved || it.payload is ArchivedStatChangeSoundMeasured })
    }
    @Test fun `compact serialization retains sound measurement attribution and cited audio`() {
        val audio = AudioCaptured(AudioArtifactReference("audio/cue.wav", "sha", 24044), 8000, 1, 1_500_000_000,
            "STAT_CHANGE_SOUND", "PLAYBACK_POKEMON_GO", .4f)
        val articles = listOf(
            article("audio", audio), article("unused", audio),
            article("sound", StatChangeSoundMeasured("ATTACK_DOWN", "reference-sha", .98f, .71f, 900_000_000), listOf("audio")),
            article("move", ChargedMoveEnergySpent(ActivePokemonSide.PLAYER, "Camerupt", "Rock Tomb", 50, "ANNOUNCEMENT")),
            article("resolved", StatChangeObserved(ActivePokemonSide.OPPONENT, "ATTACK", -1, 1, "Rock Tomb", "sound"), listOf("sound", "move")),
        ).map(RealityArticleArchiveMapper::map)
        val selected = MatchArchiveArticleSelector.select(articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(setOf("audio", "sound", "move", "resolved"), selected.map { it.articleId }.toSet())
        val json = Json { classDiscriminator = "kind" }
        selected.forEach {
            val text = json.encodeToString(ArchivedRealityArticle.serializer(), it)
            assertEquals(it, json.decodeFromString(ArchivedRealityArticle.serializer(), text))
        }
    }
    @Test fun `ambiguous revision can be saved without an invented side or magnitude`() {
        val archived = RealityArticleArchiveMapper.map(article("uncertain",
            StatChangeObserved(null, "ATTACK", -1, null, null, "sound", "CONFLICTING_CHARGED_MOVE_SIDES")))
        val payload = archived.payload as ArchivedStatChangeObserved
        assertNull(payload.affectedSide)
        assertNull(payload.stages)
    }
    private fun article(id: String, payload: TestimonyPayload, refs: List<String> = emptyList()) = RealityArticle(
        ArticleId(id), 1000, 2000, SourceId("TEST"), payload, evidenceReferences = refs,
        matchId = MatchId("test"), monotonicTimeNanos = 10_000_000_000)
}
