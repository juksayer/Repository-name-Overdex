package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Original Moondrop publication order from the October 27 controlled Charm recording.
 * Seven completed visual episodes per group are a bounded visual regression expectation,
 * not independent server-registered attack truth. See the fixture provenance and review.
 */
class RecordedFastMoveCyclePairingTest {
    private val fixtureRoot = "/battle/2026-10-27-charm-pairing/"

    @Test fun `two opening groups pair fourteen losses and flashes without changing their evidence`() {
        val archive = fixture()
        val original = MatchArchiveSerializer.serialize(archive)
        val uses = infer(archive.articles)
        val expected = provenance().getValue("expectedOrderPreservingImpactPairs").jsonArray
        assertEquals(14, expected.size)
        assertEquals(describe(uses), 14, uses.size)

        for (group in 1..2) {
            val pairs = expected.filter { it.jsonObject.getValue("group").jsonPrimitive.int == group }
            val lossIds = pairs.map { it.jsonObject.getValue("lossArticleId").jsonPrimitive.content }.toSet()
            assertEquals("Opening group $group", 7,
                uses.count { use -> use.predecessorIds.any { it.value in lossIds } })
        }

        for (pair in expected) {
            val p = pair.jsonObject
            val lossId = ArticleId(p.getValue("lossArticleId").jsonPrimitive.content)
            val pulseId = ArticleId(p.getValue("pulseArticleId").jsonPrimitive.content)
            val matched = uses.single { lossId in it.predecessorIds }
            assertTrue("$lossId must retain its own later flash $pulseId", pulseId in matched.predecessorIds)
            assertEquals("A source phase must belong to exactly one use", 1,
                uses.count { pulseId in it.predecessorIds })
            val impactInputs = archive.articles.filter { ArticleId(it.articleId) in matched.predecessorIds }
            assertEquals(1, impactInputs.count { it.payload is ArchivedActiveHpBarDamageTickMeasured })
            assertEquals(1, impactInputs.count {
                val payload = it.payload
                payload is ArchivedHpBarBorderPulse && payload.status == "PRESENT"
            })

            // Joining the later flash must preserve the original damage source time.
            val loss = archive.articles.single { ArticleId(it.articleId) == lossId }
            assertEquals(lossId, matched.observedArticle.id)
            assertEquals(loss.monotonicTimeNanos, matched.observedArticle.monotonicTimeNanos)
            assertEquals(loss.perceivedAt, matched.observedArticle.perceivedAt)
            assertEquals(loss.recordedAt, matched.observedArticle.recordedAt)
            assertEquals(SourceId(loss.sourceId), matched.observedArticle.sourceId)
            assertEquals(ActivePokemonSide.OPPONENT, matched.payload.attackingSide)
            assertEquals(ActivePokemonSide.PLAYER, matched.payload.damagedSide)
            assertEquals("Gardevoir", matched.payload.attackerSpeciesName)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `idle defender motion and settled gaps do not become extra attacks`() {
        val archive = fixture()
        val uses = infer(archive.articles)
        assertTrue("The idle Melmetal produces motion support in this real excerpt",
            archive.articles.any { it.payload is ArchivedActiveHpBarMotionCadenceMeasured })
        assertFalse("The idle defender must not be assigned an attack",
            uses.any { it.payload.attackingSide == ActivePokemonSide.PLAYER })

        for (window in provenance().getValue("settledArchiveSourceControls").jsonArray) {
            val range = window.jsonArray
            val from = range[0].jsonPrimitive.long
            val until = range[1].jsonPrimitive.long
            assertTrue("A quiet control must contain actual HP measurements",
                archive.articles.any {
                    it.payload is ArchivedActiveHpBarMeasured && it.monotonicTimeNanos?.let { at -> at in from until until } == true
                })
            assertFalse("Quiet source interval $from..$until gained an attack: ${describe(uses)}",
                uses.any { it.observedArticle.monotonicTimeNanos?.let { at -> at in from until until } == true })
        }
    }

    private fun fixture() = MatchArchiveSerializer.deserialize(resource("moondrop-opening.json"))
    private fun provenance() = Json.parseToJsonElement(resource("provenance.json")).jsonObject
    private fun resource(name: String) = checkNotNull(javaClass.getResource(fixtureRoot + name)).readText()

    private fun infer(articles: List<ArchivedRealityArticle>): List<FastMoveUseDerivation> {
        val engine = FastMoveUseInference()
        return articles.flatMap { engine.accept(live(it)) } + engine.flush()
    }

    private fun describe(uses: List<FastMoveUseDerivation>) = uses.joinToString("\n") {
        "${it.payload.attackingSide} ${it.observedArticle.monotonicTimeNanos}: ${it.predecessorIds}"
    }

    /** Preserve every envelope field; only the archived payload representation changes. */
    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                side(p.side), p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
            is ArchivedFastMoveIdentified -> FastMoveIdentified(side(p.side), p.speciesName, p.moveName,
                p.moveDurationNanos, p.observedMedianIntervalNanos, p.cadenceSampleCount, p.basis)
            is ArchivedActiveHpBarMeasured -> ActiveHpBarMeasured(side(p.side), p.barLeft, p.barTop,
                p.barRight, p.barBottom, p.filledFraction, p.sourceWidth, p.sourceHeight)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(
                side(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(
                side(p.damagedBarSide), p.intervalNanos, p.peakColorDistance, p.sampleCount,
                p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarMotionCadenceMeasured -> ActiveHpBarMotionCadenceMeasured(
                side(p.movingSide), p.intervalNanos, p.verticalExcursionPixels, p.sampleCount)
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> PlayerChargeMoveEnergyFillIncreased(
                p.beforeBySlot, p.afterBySlot, p.changedSlots)
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> FastMoveRecipientVisualArtifactMeasured(
                side(p.damagedSide), p.changedPixelFraction, p.meanColorDistance, p.meanRed, p.meanGreen,
                p.meanBlue, p.centroidX, p.centroidY, p.changedSampleCount)
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> FastMoveRecipientVisualCadenceMeasured(
                side(p.damagedSide), p.intervalNanos)
            ArchivedMatchStarted -> MatchStarted
            ArchivedMatchRecordStarted -> MatchRecordStarted
            else -> error("Unexpected payload in the bounded timing fixture: ${a.payload}")
        }
        return RealityArticle(
            id = ArticleId(a.articleId), perceivedAt = a.perceivedAt, recordedAt = a.recordedAt,
            sourceId = SourceId(a.sourceId), payload = payload,
            predecessorIds = a.predecessorIds.map(::ArticleId), confidence = a.confidence,
            sequenceNumber = a.sequenceNumber, evidenceReferences = a.evidenceReferences,
            matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }

    private fun side(value: String) = ActivePokemonSide.valueOf(value)
}
