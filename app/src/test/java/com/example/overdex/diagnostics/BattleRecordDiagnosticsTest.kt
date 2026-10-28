package com.example.overdex.diagnostics

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class BattleRecordDiagnosticsTest {
    @Test
    fun `job labels remove only the redundant source token`() {
        assertEquals("ANNOUNCEMENT_OCR", sourceJobLabel("ANNOUNCEMENT_WITNESS_OCR"))
        assertEquals("PLAYER_ACTIVE_HP_BAR", sourceJobLabel("PLAYER_ACTIVE_HP_BAR_WITNESS"))
        assertEquals("FAST_MOVE_ENERGY_INFERENCE", sourceJobLabel("FAST_MOVE_ENERGY_INFERENCE"))
        assertEquals("UNWITNESSED_INTERVAL", sourceJobLabel("UNWITNESSED_INTERVAL"))
        assertEquals("CountdownGlyph", signalLabel("CountdownGlyphWitnessed"))
        assertEquals("Operating", signalLabel("WitnessOperating"))
        assertEquals("FastMoveIdentified", signalLabel("FastMoveIdentified"))
    }

    @Test
    fun `diagnostics preserve routing identity and evidence`() {
        val article = article("ATTACK_INCOMING_WITNESS", AttackIncoming)
            .copy(predecessorIds = listOf(ArticleId("raw-announcement")),
                evidenceReferences = listOf("raw-announcement"), monotonicTimeNanos = 42L)
        val original = article.copy()

        assertEquals("[ARTICLE] ATTACK_INCOMING signal=AttackIncoming", BattleRecordDiagnostics.format(article))
        assertEquals(original, article)
        assertEquals("ATTACK_INCOMING_WITNESS", article.sourceId.id)
    }

    @Test
    fun `derived conclusion is not mislabeled as an observation`() {
        val article = article("BATTLE_INTERPRETER", MatchStarted)
            .copy(predecessorIds = listOf(ArticleId("go")))
        val line = BattleRecordDiagnostics.format(article)!!
        assertEquals("[ARTICLE] BATTLE_INTERPRETER signal=MatchStarted", line)
        assertFalse(line.contains("OBSERVATION"))
    }

    @Test
    fun `raw text is not interpreted by diagnostic rendering`() {
        val article = article("ANNOUNCEMENT_WITNESS", RawTestimony("Attack incoming!"))
        assertEquals("[ARTICLE] ANNOUNCEMENT signal=RawTestimony", BattleRecordDiagnostics.format(article))
        assertTrue(article.payload is RawTestimony)
    }

    private fun article(source: String, payload: com.example.overdex.battle.custody.TestimonyPayload) =
        RealityArticle(ArticleId("test"), 100L, 200L, SourceId(source), payload)
}
