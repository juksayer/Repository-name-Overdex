package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Original Moondrop evidence from the alternating-attacker self-match, October 20 at 09:42. */
class RecordedPairedGreninjaTest {
    @Test fun `delayed flashes do not turn Water Shuriken damage cycles into Feint Attack`() = runBlocking {
        val archive = fixture()
        val unchanged = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveCadenceInference(GreninjaKnowledge)
        val identities = archive.articles.filter {
            it.payload is ArchivedActivePokemonSpeciesWitnessed || it.payload is ArchivedFastMoveUseObserved
        }.flatMap { engine.accept(live(it)) }.mapNotNull { it.payload as? FastMoveIdentified }
        assertTrue(identities.toString(), identities.any { it.moveName == "Water Shuriken" })
        assertFalse(identities.toString(), identities.any { it.moveName == "Feint Attack" })
        val identity = identities.first { it.moveName == "Water Shuriken" }
        assertTrue(identity.basis.contains("_CONSISTENT_IMPACT_STREAM"))
        // Bubble has the same duration: cadence rules out Feint Attack, but the
        // Water Shuriken name is still the explicitly labelled energy preference.
        assertTrue(identity.basis.contains("HIGHER_ENERGY_TIE_BREAK_ASSUMPTION"))
        assertEquals(1_500_000_000L, identity.moveDurationNanos)
        assertEquals(unchanged, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `resolved duration joins all four measured damage and flash pairs without creating attacks`() {
        val archive = fixture()
        val engine = FastMoveUseInference()
        // Isolate fusion with the same Water Shuriken that the other phone's
        // equipped moveset and this phone's consistent damage cycles identify.
        engine.observeIdentity(FastMoveIdentified(ActivePokemonSide.OPPONENT, "Greninja",
            "Water Shuriken", 1_500_000_000L, 1_500_000_000L, 2,
            "OBSERVED_USES_CADENCE_AND_REFERENCE_KNOWLEDGE_CONSISTENT_IMPACT_STREAM"))
        val uses = (archive.articles.filter { it.payload !is ArchivedFastMoveIdentified }
            .flatMap { engine.accept(live(it)) } + engine.flush())
            .filter { it.payload.attackingSide == ActivePokemonSide.OPPONENT }
        assertEquals(uses.toString(), 4, uses.size)
        val pairs = listOf(
            "485c1d32-ea03-4477-91a5-9fc8af6940f3" to "ee9d03df-196d-4f4c-aa6b-b894189476d9",
            "869b9e6c-b4fb-47fe-85d8-142627935171" to "bdbacb64-314a-4696-93bd-39b7ff591702",
            "3db8b2ed-46c2-40d0-a189-dc490aad492e" to "04704144-3019-4ac7-b70f-c4553002ca84",
            "bef05d17-4c3d-4d76-bf07-49eb34576797" to "43fb5c40-fced-48d1-ac02-8ae5aef690d9",
        )
        pairs.forEach { (loss, flash) ->
            val use = uses.single { ArticleId(loss) in it.predecessorIds }
            assertTrue("$loss should share an attack with $flash", ArticleId(flash) in use.predecessorIds)
            assertEquals(archive.articles.single { it.articleId == loss }.monotonicTimeNanos,
                use.observedArticle.monotonicTimeNanos)
        }
    }

    @Test fun `live feedback learns duration without consulting the other phone or old derived identities`() = runBlocking {
        val archive = fixture()
        val fusion = FastMoveUseInference()
        val cadence = FastMoveCadenceInference(GreninjaKnowledge)
        val uses = mutableListOf<FastMoveUseDerivation>()
        val identities = mutableListOf<FastMoveIdentified>()
        suspend fun acceptUses(newUses: List<FastMoveUseDerivation>, receivedAt: Long) {
            uses += newUses
            newUses.forEach { use ->
                cadence.accept(use.observedArticle.copy(
                    id = ArticleId(use.payload.useId),
                    recordedAt = receivedAt,
                    payload = use.payload,
                    predecessorIds = use.predecessorIds,
                )).forEach { derived ->
                    (derived.payload as? FastMoveIdentified)?.let {
                        identities += it
                        fusion.observeIdentity(it)
                    }
                }
            }
        }
        for (a in archive.articles) {
            if (a.payload is ArchivedFastMoveIdentified || a.payload is ArchivedFastMoveUseObserved) continue
            val article = live(a)
            acceptUses(fusion.accept(article), article.recordedAt)
            cadence.accept(article).forEach { derived ->
                (derived.payload as? FastMoveIdentified)?.let {
                    identities += it
                    fusion.observeIdentity(it)
                }
            }
        }
        acceptUses(fusion.flush(), archive.articles.maxOf { it.recordedAt })
        assertTrue(identities.toString(), identities.any { it.moveName == "Water Shuriken" })
        assertFalse(identities.toString(), identities.any { it.moveName == "Feint Attack" })
        // Early fragments were already published before identity resolved. Do
        // not erase them to make the ledger look like perfect observation.
        val opponentUses = uses.filter { it.payload.attackingSide == ActivePokemonSide.OPPONENT }
        assertEquals(opponentUses.toString(), 6, opponentUses.size)
        val finalLoss = ArticleId("bef05d17-4c3d-4d76-bf07-49eb34576797")
        val finalFlash = ArticleId("43fb5c40-fced-48d1-ac02-8ae5aef690d9")
        assertTrue(opponentUses.single { finalLoss in it.predecessorIds }.predecessorIds.contains(finalFlash))
    }

    private fun fixture() = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
        "/battle/2026-10-20-greninja-impact-phase.json")).readText())

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val payload: TestimonyPayload = when (val p = a.payload) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(side(p.side), p.speciesName, p.speciesId)
            is ArchivedActiveHpBarDamageTickMeasured -> ActiveHpBarDamageTickMeasured(side(p.damagedSide), p.beforeFraction, p.afterFraction, p.lostFraction)
            is ArchivedHpBarBorderPulse -> HpBarBorderPulse(side(p.barSide), ApertureStatus.valueOf(p.status),
                p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedFastMoveUseObserved -> FastMoveUseObserved(p.useId, side(p.attackingSide), side(p.damagedSide),
                p.appearanceId, p.attackerSpeciesName, p.evidenceKinds, p.basis)
            ArchivedMatchStarted -> MatchStarted
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            else -> RawTestimony("Other recorded article advances the delivery watermark")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt, SourceId(a.sourceId), payload,
            confidence = a.confidence, matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }
    private fun side(value: String) = ActivePokemonSide.valueOf(value)

    private object GreninjaKnowledge : PokemonKnowledge {
        val pokemon = Pokemon(658, "Greninja", listOf(PokemonType.WATER, PokemonType.DARK), "Kalos",
            fastMoves = listOf(
                Move("Bubble", PokemonType.WATER, 8, 11, true, turns = 3),
                Move("Feint Attack", PokemonType.DARK, 6, 6, true, turns = 2),
                Move("Water Shuriken", PokemonType.WATER, 6, 14, true, turns = 3),
            ), chargedMoves = emptyList())
        override suspend fun getPokemonByName(name: String) = pokemon.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int) = pokemon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
    }
}
