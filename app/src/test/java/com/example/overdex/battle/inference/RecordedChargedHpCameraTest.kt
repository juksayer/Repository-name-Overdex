package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.replay.MatchReplayModel
import org.junit.Assert.*
import org.junit.Test

/** Untouched October 28 source clocks and publication order, including the slow OCR. */
class RecordedChargedHpCameraTest {
    private val pulseId = "307d09a2-3b8b-46f0-bad6-24db32929ddc"
    private val oldUseId = "075d4dad-c798-4374-9c2d-e60b7fffef69"
    private val chargeId = "0c0670a4-ea2d-4b75-a17b-9866eb068e39"

    @Test fun `late Hydro Cannon OCR prevents the real relocated pulse becoming another Fast Move`() {
        val archive = fixture()
        val original = MatchArchiveSerializer.serialize(archive)
        val pulse = archive.articles.single { it.articleId == pulseId }
        val charge = archive.articles.single { it.articleId == chargeId }
        val oldUse = archive.articles.single { it.articleId == oldUseId }
        assertTrue("This regression must retain the original late-custody race", charge.recordedAt > oldUse.recordedAt)
        assertTrue(pulse.monotonicTimeNanos!! - charge.monotonicTimeNanos!! > 750_000_000L)

        val engine = FastMoveUseInference()
        val uses = archive.articles.filterNot { it.payload is ArchivedFastMoveUseObserved }
            .flatMap { engine.accept(live(it)) } + engine.flush()
        assertFalse(uses.any { use -> use.predecessorIds.any { it.value == pulseId } })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `energy ceiling rejects the recorded charged-camera pulse for either attacking side`() {
        val archive = fixture()
        val timing = FastMoveEnergyTiming()
        archive.articles.forEach { timing.observe(live(it)) }
        val impactAt = archive.articles.single { it.articleId == pulseId }.monotonicTimeNanos!!
        assertTrue("Earlier full cycles are not excluded", timing.reserve(ActivePokemonSide.OPPONENT,
            impactAt - 4_000_000_000L, 1_500_000_000L))
        for (side in ActivePokemonSide.entries)
            assertFalse(timing.reserve(side, impactAt, 1_500_000_000L))
    }

    @Test fun `unconfirmed relocation releases its original hit instead of silently losing it`() {
        val engine = FastMoveUseInference()
        engine.accept(synthetic("go", 0.0, MatchStarted))
        for ((index, at) in listOf(7.0, 8.0, 9.0).withIndex())
            engine.accept(synthetic("baseline-$index", at, hp(450)))
        engine.accept(synthetic("relocated", 10.0, hp(660)))
        val impact = synthetic("unconfirmed-impact", 11.0,
            HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, 1f, 10))
        engine.accept(impact)
        assertTrue(engine.accept(synthetic("wait", 14.0, RawTestimony("clock"))).isEmpty())
        assertTrue(engine.flush().isEmpty())
        val released = engine.accept(synthetic("expired", 15.1, RawTestimony("clock")))
        assertEquals(listOf(impact), released.map { it.observedArticle })
        assertEquals(listOf(impact.id), released.single().predecessorIds)
    }

    @Test fun `energy requires a whole fast cycle after measured camera return`() {
        val timing = FastMoveEnergyTiming()
        timing.observe(synthetic("go", 0.0, MatchStarted))
        for ((index, at) in listOf(7.0, 8.0, 9.0).withIndex())
            timing.observe(synthetic("baseline-$index", at, hp(450)))
        timing.observe(synthetic("charge", 10.0, ChargeMoveUsedAnnounced))
        timing.observe(synthetic("relocated", 10.3, hp(660)))
        timing.observe(synthetic("return", 12.0, hp(450)))
        assertFalse(timing.reserve(ActivePokemonSide.OPPONENT, ns(11.5), ns(.5)))
        assertFalse(timing.reserve(ActivePokemonSide.OPPONENT, ns(12.3), ns(.5)))
        assertTrue(timing.reserve(ActivePokemonSide.OPPONENT, ns(12.5), ns(.5)))
    }

    @Test fun `a replacement with a different normal HP height keeps its real fast move evidence`() {
        val engine = FastMoveUseInference()
        val timing = FastMoveEnergyTiming()
        val sources = listOf(
            synthetic("go", 0.0, MatchStarted),
            synthetic("original", 0.0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Victreebel")),
            synthetic("baseline-1", 7.0, hp(450)),
            synthetic("baseline-2", 8.0, hp(450)),
            synthetic("baseline-3", 9.0, hp(450)),
            synthetic("charge", 10.0, ChargeMoveUsedAnnounced),
            synthetic("faint", 11.0, ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Victreebel")),
            synthetic("replacement", 11.2, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Golbat")),
            synthetic("new-height", 11.3, hp(660)),
            synthetic("real-fast", 11.8, HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, 1f, 10)),
            synthetic("clock", 14.0, RawTestimony("clock")),
        )
        val uses = sources.flatMap { source ->
            timing.observe(source)
            when (val payload = source.payload) {
                is ActivePokemonFaintedWitnessed -> timing.fainted(payload.side, source.monotonicTimeNanos!!)
                is ActivePokemonSpeciesWitnessed -> timing.appearanceStarted(payload.side, source.monotonicTimeNanos!!)
                else -> Unit
            }
            engine.accept(source)
        } + engine.flush()
        assertEquals(listOf("fast-move-use:real-fast"), uses.map { it.payload.useId })
        assertTrue(timing.reserve(ActivePokemonSide.OPPONENT, ns(11.8), ns(.5)))
    }

    @Test fun `replay hides the old misclassification while retaining HP and the named charge`() {
        val archive = fixture()
        val original = MatchArchiveSerializer.serialize(archive)
        val impactAt = archive.articles.single { it.articleId == pulseId }.monotonicTimeNanos!!
        val model = MatchReplayModel(archive)
        val scene = model.sceneAt(impactAt + 100_000_000L)
        assertFalse(scene.fastMoveActions.any { oldUseId in it.sourceArticleIds || pulseId in it.sourceArticleIds })
        val spend = archive.articles.single { it.payload is ArchivedChargedMoveEnergySpent }.payload as ArchivedChargedMoveEnergySpent
        assertEquals("Hydro Cannon", spend.moveName)
        assertEquals(40, spend.energyCost)
        assertEquals(.32786885f, scene.playerHp!!.filledFraction!!, .0001f)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    private fun fixture() = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
        "/battle/2026-10-28-charged-hp-camera/hydro-cannon.json")).readText())

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            ArchivedMatchStarted -> MatchStarted
            ArchivedMatchRecordStarted -> MatchRecordStarted
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            is ArchivedMatchEnded -> MatchEnded(com.example.overdex.model.BattleResult.valueOf(p.result))
            is ArchivedRawText -> RawTestimony(p.value)
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                side(p.side), p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
            is ArchivedActiveHpBarMeasured -> ActiveHpBarMeasured(side(p.side), p.barLeft, p.barTop,
                p.barRight, p.barBottom, p.filledFraction, p.sourceWidth, p.sourceHeight)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedChargedMoveEnergySpent -> ChargedMoveEnergySpent(side(p.side), p.speciesName,
                p.moveName, p.energyCost, p.basis)
            is ArchivedFastMoveUseObserved -> FastMoveUseObserved(p.useId, side(p.attackingSide),
                side(p.damagedSide), p.appearanceId, p.attackerSpeciesName, p.evidenceKinds, p.basis)
            else -> error("Unexpected fixture payload: $p")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt,
            SourceId(a.sourceId), payload, a.predecessorIds.map(::ArticleId), a.confidence,
            a.sequenceNumber, a.evidenceReferences, MatchId(a.matchId), a.monotonicTimeNanos)
    }

    private fun side(value: String) = ActivePokemonSide.valueOf(value)
    private fun hp(top: Int) = ActiveHpBarMeasured(ActivePokemonSide.PLAYER, 38, top, 230, top + 18, .9f, 672, 1493)
    private fun ns(seconds: Double) = (seconds * 1e9).toLong()
    private fun synthetic(id: String, at: Double, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), (at * 1000).toLong(), (at * 1000).toLong(), SourceId("TEST"), payload,
        monotonicTimeNanos = ns(at))
}
