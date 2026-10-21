package com.example.overdex.battle.forecast

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class RecordedChargeForecastTest {
    @Test fun `October 19 Electrode and Jolteon charge gaps freeze the live forecast`() {
        val archive = MatchArchiveSerializer.deserialize(requireNotNull(javaClass.getResource(
            "/forecast/oct19-charge-forecast.json")).readText())
        val base = archive.articles.first().monotonicTimeNanos!!
        val forecast = OpponentEnergyForecast()
        // Fixed test budgets: this test compares pause/resume, not catalogue balance.
        for ((species, move) in listOf("Plusle" to "Spark", "Electrode" to "Volt Switch", "Jolteon" to "Thunder Shock")) {
            forecast.registerMoves(species, listOf(Move(move, PokemonType.ELECTRIC, 3, 8, true, 2)))
        }
        // Reconstruct positive tracker samples from the unchanged numeric articles.
        // No image or absent-frame observation is fabricated: gaps expire naturally.
        archive.articles.filter { it.payload is ArchivedActiveHpBarMeasured }.groupBy { it.monotonicTimeNanos!! }
            .toSortedMap().forEach { (at, group) ->
                forecast.observeBattlefield(at, group.map { (it.payload as ArchivedActiveHpBarMeasured).side }.toSet().size == 2)
            }
        for (article in archive.articles) {
            val payload = when (val p = article.payload) {
                ArchivedMatchStarted -> MatchStarted
                ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
                is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId)
                is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(ActivePokemonSide.valueOf(p.side), p.speciesName)
                is ArchivedChargedMoveEnergySpent -> ChargedMoveEnergySpent(ActivePokemonSide.valueOf(p.side), p.speciesName, p.moveName, p.energyCost, p.basis)
                is ArchivedFastMoveIdentified -> FastMoveIdentified(ActivePokemonSide.valueOf(p.side), p.speciesName,
                    p.moveName, p.moveDurationNanos, p.observedMedianIntervalNanos, p.cadenceSampleCount, p.basis)
                is ArchivedFastMoveUseObserved -> FastMoveUseObserved(p.useId, ActivePokemonSide.valueOf(p.attackingSide),
                    ActivePokemonSide.valueOf(p.damagedSide), p.appearanceId, p.attackerSpeciesName, p.evidenceKinds, p.basis)
                else -> null
            } ?: continue
            forecast.observe(RealityArticle(ArticleId(article.articleId), article.perceivedAt, article.recordedAt,
                SourceId(article.sourceId), payload, monotonicTimeNanos = article.monotonicTimeNanos))
        }
        fun at(seconds: Double) = forecast.estimate(base + (seconds * 1e9).toLong())!!
        assertEquals("Electrode", at(81.0).speciesName)
        assertTrue(at(81.0).paused)
        assertEquals(at(81.0).energy, at(87.0).energy)
        assertEquals("Jolteon", at(98.0).speciesName)
        assertTrue(at(98.0).paused)
        assertEquals(at(98.0).energy, at(104.0).energy)
        assertFalse(at(113.9).paused)
        assertTrue(at(113.9).energy > at(104.0).energy)
    }
}
