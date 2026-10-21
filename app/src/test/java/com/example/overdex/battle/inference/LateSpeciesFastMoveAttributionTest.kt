package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class LateSpeciesFastMoveAttributionTest {
    @Test fun `recorded Camerupt switch owns the pending attack captured after its badge`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/recorded-late-switch-20261015.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveUseInference()
        val uses = archive.articles.flatMap { engine.accept(live(it)) } + engine.flush()
        val pulseId = "01225384-b6b7-4b56-911f-60157d4fe137"
        val use = uses.single { ArticleId(pulseId) in it.predecessorIds }
        assertEquals(ActivePokemonSide.PLAYER, use.payload.attackingSide)
        assertEquals("Camerupt", use.payload.attackerSpeciesName)
        assertEquals("52bb4abc-01ba-4a1e-ab31-b569deffa329", use.payload.appearanceId)
        assertEquals(archive.articles.single { it.articleId == pulseId }.monotonicTimeNanos,
            use.observedArticle.monotonicTimeNanos)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `late identity splits pending uses at capture time without changing the other side`() {
        val engine = started()
        engine.accept(species("old", 100, ActivePokemonSide.PLAYER, "Abomasnow", 460))
        engine.accept(species("opponent", 100, ActivePokemonSide.OPPONENT, "Sneasel", 215))
        engine.accept(hit("before", 1_000, ActivePokemonSide.OPPONENT))
        engine.accept(hit("after", 1_600, ActivePokemonSide.OPPONENT))
        engine.accept(hit("their-hit", 1_700, ActivePokemonSide.PLAYER))
        val first = engine.accept(species("new", 1_500, ActivePokemonSide.PLAYER, "Camerupt", 323))
        val uses = first + engine.flush()
        assertEquals(3, uses.size)
        fun owner(id: String) = uses.single { it.payload.useId == "fast-move-use:$id" }.payload.attackerSpeciesName
        assertEquals("Abomasnow", owner("before"))
        assertEquals("Camerupt", owner("after"))
        assertEquals("Sneasel", owner("their-hit"))
    }

    @Test fun `late old impact cannot inherit the current replacement`() {
        val engine = started()
        engine.accept(species("old", 100, ActivePokemonSide.PLAYER, "Abomasnow", 460))
        engine.accept(species("new", 2_000, ActivePokemonSide.PLAYER, "Camerupt", 323))
        engine.accept(hit("delayed", 1_500, ActivePokemonSide.OPPONENT))
        assertEquals("Abomasnow", engine.flush().single().payload.attackerSpeciesName)
    }

    @Test fun `charged-phase held evidence uses the later accepted switch boundary`() {
        val engine = started()
        engine.accept(species("old", 100, ActivePokemonSide.PLAYER, "Abomasnow", 460))
        engine.accept(article("qte", 1_000, GetReadyWitnessed))
        engine.accept(hit("resumed", 5_000, ActivePokemonSide.OPPONENT))
        engine.accept(species("new", 4_000, ActivePokemonSide.PLAYER, "Camerupt", 323))
        val uses = engine.accept(article("delayed-charge", 3_000, ChargeMoveUsedAnnounced)) + engine.flush()
        assertEquals("Camerupt", uses.single().payload.attackerSpeciesName)
        assertEquals("fast-move-use:resumed", uses.single().payload.useId)
    }

    @Test fun `a future identity does not name a hit before any known appearance`() {
        val engine = started()
        engine.accept(hit("unidentified", 1_000, ActivePokemonSide.OPPONENT))
        val uses = engine.accept(species("new", 1_500, ActivePokemonSide.PLAYER, "Camerupt", 323))
        assertNull(uses.single().payload.attackerSpeciesName)
    }

    @Test fun `restart clears the previous attempts appearance history`() {
        val engine = started()
        engine.accept(species("old", 100, ActivePokemonSide.PLAYER, "Abomasnow", 460))
        engine.accept(article("restart", 1_000, ObservationRestartedByUser))
        engine.accept(article("go-again", 2_000, MatchStarted))
        engine.accept(hit("unidentified", 2_100, ActivePokemonSide.OPPONENT))
        assertNull(engine.flush().single().payload.attackerSpeciesName)
    }

    private fun started() = FastMoveUseInference().also { it.accept(article("go", 0, MatchStarted)) }
    private fun species(id: String, millis: Long, side: ActivePokemonSide, name: String, speciesId: Int) =
        article(id, millis, ActivePokemonSpeciesWitnessed(side, name, speciesId))
    private fun hit(id: String, millis: Long, damagedSide: ActivePokemonSide) =
        article(id, millis, HpBarBorderPulse(damagedSide, ApertureStatus.PRESENT, 0.8f, 2))
    private fun article(id: String, millis: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), millis, millis, SourceId("TEST"), payload, matchId = MatchId("test"),
        monotonicTimeNanos = millis * 1_000_000L)

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload = when (val p = a.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(
                ActivePokemonSide.valueOf(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(ActivePokemonSide.valueOf(p.barSide),
                ApertureStatus.valueOf(p.status), p.peakColorDistance, p.sampleCount,
                p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedFastMoveIdentified -> FastMoveIdentified(ActivePokemonSide.valueOf(p.side),
                p.speciesName, p.moveName, p.moveDurationNanos, p.observedMedianIntervalNanos,
                p.cadenceSampleCount, p.basis)
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            else -> RawTestimony("Other recorded article")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt, SourceId(a.sourceId),
            payload, confidence = a.confidence, matchId = MatchId(a.matchId),
            monotonicTimeNanos = a.monotonicTimeNanos)
    }
}
