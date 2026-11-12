package com.example.overdex.battle.observation

import kotlinx.serialization.json.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FastMoveUseInference
import com.example.overdex.battle.reality.*
import org.junit.Assert.*
import org.junit.Test

/** Replays preserved numeric measurements only; live eligibility flags are not archived. */
class RecordedPixel1447HpDeliveryTest {
    private fun rows() = Json.parseToJsonElement(requireNotNull(javaClass.getResource(
        "/battle/hp/2026-11-12-pixel-1447/opening-opponent-hp.json")).readText()).jsonArray.map { it.jsonObject }
    private fun bar(row: JsonObject): ActiveHpBarMeasurement {
        val p = row.getValue("payload").jsonObject
        return ActiveHpBarMeasurement(p.getValue("barLeft").jsonPrimitive.int,
            p.getValue("barTop").jsonPrimitive.int, p.getValue("barRight").jsonPrimitive.int,
            p.getValue("barBottom").jsonPrimitive.int, p.getValue("filledFraction").jsonPrimitive.float,
            row.getValue("confidence").jsonPrimitive.float)
    }

    @Test fun `startup gate seeds the normal baseline and preserves the first hit`() {
        val rows = rows()
        val gate = ChargedImpactHpWatchPolicy().apply { resetAll() }
        val detector = ActiveHpBarDamageTickDetector()
        val allowed = mutableListOf<Boolean>()
        val ticks = rows.mapNotNull { row ->
            val at = row.getValue("monotonicTimeNanos").jsonPrimitive.long
            val disposition = gate.fastMoveDisposition(ActivePokemonSide.OPPONENT, bar(row), at)
            val enabled = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.EVIDENCE
            allowed += enabled
            detector.accept(LiveActiveHpBarSample(bar(row), null, null, 0, at,
                fastMoveEvidenceAllowed = enabled,
                baselineOnly = disposition == ChargedImpactHpWatchPolicy.FastMoveDisposition.BASELINE_ONLY))
        }
        assertFalse(allowed.first())
        assertTrue(allowed.drop(1).all { it })
        assertTrue("The first loss survives the real startup handoff", ticks.any {
            it.observedAtNanos == rows[1].getValue("monotonicTimeNanos").jsonPrimitive.long })
        assertTrue(ticks.first().lostFraction > .10f)
    }

    @Test fun `control tick from preserved baseline becomes a Turtonator move event`() {
        val rows = rows()
        val detector = ActiveHpBarDamageTickDetector()
        val tick = rows.mapNotNull { detector.accept(bar(it),
            it.getValue("monotonicTimeNanos").jsonPrimitive.long) }.first()
        val inference = FastMoveUseInference()
        fun article(id: String, at: Long, payload: TestimonyPayload) = RealityArticle(
            ArticleId(id), at / 1_000_000, at / 1_000_000, SourceId("CONTROL_FIXTURE"), payload,
            confidence = .95f, matchId = MatchId("pixel1447-control"), monotonicTimeNanos = at)
        val baselineAt = rows.first().getValue("monotonicTimeNanos").jsonPrimitive.long
        inference.accept(article("control-opponent", baselineAt - 2,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Mimikyu", 778)))
        inference.accept(article("control-player", baselineAt - 1,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator", 776)))
        val outputs = inference.accept(article("control-missing-tick", tick.observedAtNanos,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, tick.beforeFraction,
                tick.afterFraction, tick.lostFraction))) + inference.flush()
        val event = outputs.single()
        assertEquals(ActivePokemonSide.PLAYER, event.payload.attackingSide)
        assertEquals("Turtonator", event.payload.attackerSpeciesName)
        assertEquals(tick.observedAtNanos, event.observedArticle.monotonicTimeNanos)
    }

    @Test fun `opening archived opponent loss can settle when delivered to the detector`() {
        val text = requireNotNull(javaClass.getResource(
            "/battle/hp/2026-11-12-pixel-1447/opening-opponent-hp.json")).readText()
        val rows = Json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
        val detector = ActiveHpBarDamageTickDetector()
        val ticks = rows.mapNotNull { row ->
            val p = row.getValue("payload").jsonObject
            detector.accept(ActiveHpBarMeasurement(
                p.getValue("barLeft").jsonPrimitive.int, p.getValue("barTop").jsonPrimitive.int,
                p.getValue("barRight").jsonPrimitive.int, p.getValue("barBottom").jsonPrimitive.int,
                p.getValue("filledFraction").jsonPrimitive.float,
                row.getValue("confidence").jsonPrimitive.float),
                row.getValue("monotonicTimeNanos").jsonPrimitive.long)
        }
        assertTrue("Preserved HP values alone do contain a detectable opening decrease", ticks.isNotEmpty())
        assertEquals(rows[1].getValue("monotonicTimeNanos").jsonPrimitive.long,
            ticks.first().observedAtNanos)
        assertTrue(ticks.first().lostFraction > .10f)
    }
}
