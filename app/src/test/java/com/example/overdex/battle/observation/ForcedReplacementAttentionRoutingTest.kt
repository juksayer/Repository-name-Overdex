package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrTextEvidence
import org.junit.Assert.*
import org.junit.Test

class ForcedReplacementAttentionRoutingTest {
    private var now = 20_000_000_000L
    private val playerBalls = BattleCropContracts.playerPokeBalls.cropName
    private val playerShields = BattleCropContracts.playerShields.cropName
    private val opponentBalls = BattleCropContracts.opponentPokeBalls.cropName
    private val opponentShields = BattleCropContracts.opponentShields.cropName
    private val resources = setOf(playerBalls, playerShields, opponentBalls, opponentShields)
    private val bench = setOf(BattleCropContracts.playerInactiveUpperHpBar.cropName,
        BattleCropContracts.playerInactiveLowerHpBar.cropName,
        BattleCropContracts.playerInactiveUpperSpeciesSprite.cropName,
        BattleCropContracts.playerInactiveLowerSpeciesSprite.cropName)

    @Test fun `prompt wakes only player badge resources and expires after bounded acquisition`() {
        val gate = TeamResourceSnapshotGate(resources, setOf(playerBalls, opponentBalls)) { now }
        gate.observe(article(prompt()))
        assertTrue(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(playerShields))
        assertFalse(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(opponentShields))
        now += 3_000_000_000L
        assertTrue(resources.none(gate::isEnabled))
    }

    @Test fun `prompt wakes bench and decision dial while completed source readings cannot reopen cards`() {
        val cards = InactiveBenchSnapshotGate(bench) { now }
        val timer = DecisionTimerCaptureGate { now }
        val prompt = article(prompt())
        cards.observe(prompt); timer.observe(prompt)
        assertTrue(bench.all(cards::isEnabled))
        assertTrue(timer.isEnabled())
        bench.forEach { cards.measured(it, now) }
        cards.observe(prompt)
        assertTrue(bench.none(cards::isEnabled))
    }

    @Test fun `late prompts cannot reopen resource or bench reading across match end or restart`() {
        val gate = TeamResourceSnapshotGate(resources, setOf(playerBalls, opponentBalls)) { now }
        val cards = InactiveBenchSnapshotGate(bench) { now }
        val oldPrompt = article(prompt())
        now += 1_000_000_000L
        val end = article(MatchEnded(BattleResult.LOSS))
        gate.observe(end); cards.observe(end)
        gate.observe(oldPrompt); cards.observe(oldPrompt)
        assertTrue(resources.none(gate::isEnabled)); assertTrue(bench.none(cards::isEnabled))
        now += 1_000_000_000L
        val restart = article(ObservationRestartedByUser)
        gate.observe(restart); cards.observe(restart)
        gate.observe(oldPrompt); cards.observe(oldPrompt)
        assertTrue(resources.none(gate::isEnabled)); assertTrue(bench.none(cards::isEnabled))
    }

    private fun prompt() = ForcedReplacementPromptWitnessed("Switch in a new Pokémon?", "TEST",
        OcrTextEvidence("RAW", "Switch in a new Pokémon?", "Switch in a new Pokémon?", emptyList(), 1080, null))
    private fun article(payload: TestimonyPayload) = RealityArticle(
        ArticleId(java.util.UUID.randomUUID().toString()), 0, 0, SourceId("TEST"), payload, monotonicTimeNanos = now)
}
