package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordedFastMoveIdentityTest {
    @Test fun `October 19 idle Camerupt retains Incinerate despite recipient effect rhythm`() = runBlocking {
        // User confirmed all three player Pokemon were idle in this match.
        // These are unchanged input articles in their original publication order.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/recorded-idle-identity-20261019.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveCadenceInference(Knowledge)
        val identities = archive.articles.flatMap { engine.accept(live(it)) }
            .mapNotNull { it.payload as? FastMoveIdentified }
        val camerupt = identities.filter { it.side == ActivePokemonSide.PLAYER && it.speciesName == "Camerupt" }
        assertTrue(camerupt.isNotEmpty())
        assertTrue("Idle visual effects cannot replace the configured move: $camerupt",
            camerupt.all { it.moveName == "Incinerate" })
        assertTrue(identities.any { it.side == ActivePokemonSide.OPPONENT && it.moveName == "Feint Attack" })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `October 15 missed hits do not undo Sealeo Water Gun`() = runBlocking {
        // Unaltered input articles from 38c39532-cb00-41f4-ae2e-26db7c420a9e,
        // retained in publication order, including delayed and contradictory reads.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/recorded-sealeo-cadence-20261015.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        val engine = FastMoveCadenceInference(Knowledge)
        val identities = archive.articles.flatMap { engine.accept(live(it)) }
            .filter { (it.payload as? FastMoveIdentified)?.speciesName == "Sealeo" }
        val firstWater = identities.indexOfFirst { (it.payload as FastMoveIdentified).moveName == "Water Gun" }
        assertTrue("The recording must still identify Water Gun", firstWater >= 0)
        assertTrue("Later gaps must not reinstate Powder Snow: ${identities.map { it.payload }}",
            identities.drop(firstWater).all { (it.payload as FastMoveIdentified).moveName == "Water Gun" })
        assertTrue(identities[firstWater].predecessorIds.isNotEmpty())
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val p = a.payload
        val payload = when (p) {
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(side(p.side), p.speciesName, p.speciesId)
            is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(side(p.side), p.speciesName, p.speciesId, p.basis)
            is ArchivedPlayerTeamSlotConfigured -> PlayerTeamSlotConfigured(p.slot, p.speciesName, p.speciesId,
                p.fastMoveName, p.chargedMoveNames)
            is ArchivedActiveHpBarBorderCadenceMeasured -> ActiveHpBarBorderCadenceMeasured(side(p.damagedBarSide),
                p.intervalNanos, p.peakColorDistance, p.sampleCount, p.peakOrangeFraction, p.baselineWhiteFraction)
            is ArchivedActiveHpBarMotionCadenceMeasured -> ActiveHpBarMotionCadenceMeasured(side(p.movingSide),
                p.intervalNanos, p.verticalExcursionPixels, p.sampleCount)
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> FastMoveRecipientVisualCadenceMeasured(side(p.damagedSide), p.intervalNanos)
            is ArchivedFastMoveUseObserved -> FastMoveUseObserved(p.useId, side(p.attackingSide), side(p.damagedSide),
                p.appearanceId, p.attackerSpeciesName, p.evidenceKinds, p.basis)
            is ArchivedFastMoveEffectivenessWitnessed -> FastMoveEffectivenessWitnessed(FastMoveEffectiveness.valueOf(p.effectiveness),
                p.damagedSide?.let(::side), p.recognizedText)
            is ArchivedFastMoveSoundMeasured -> FastMoveSoundMeasured(p.audible, p.onsetOffsetNanos, p.soundDurationNanos,
                p.spectralCentroidHz, p.peakAmplitude, p.attackingSide?.let(::side), p.soundOnsetMonotonicNanos,
                p.cueAttackingSide?.let(::side), p.analysisVersion, p.referenceCandidates.map {
                    FastMoveSoundCandidate(it.moveName, it.referenceSha256, it.similarity, it.changingSimilarity,
                        it.matchingOffsetNanos, it.referenceOffsetNanos, it.matchedDurationNanos)
                }, p.referenceMatchAccepted)
            ArchivedMatchStarted -> MatchStarted
            is ArchivedMatchEnded -> MatchEnded(BattleResult.valueOf(p.result))
            ArchivedGetReadyWitnessed -> GetReadyWitnessed
            ArchivedAttackIncoming -> AttackIncoming
            ArchivedChargeMoveUsedAnnounced -> ChargeMoveUsedAnnounced
            else -> error("Fixture contains an unmapped input: $p")
        }
        return RealityArticle(ArticleId(a.articleId), a.perceivedAt, a.recordedAt, SourceId(a.sourceId), payload,
            predecessorIds = a.predecessorIds.map(::ArticleId), confidence = a.confidence,
            sequenceNumber = a.sequenceNumber, evidenceReferences = a.evidenceReferences,
            matchId = MatchId(a.matchId), monotonicTimeNanos = a.monotonicTimeNanos)
    }

    private fun side(value: String) = ActivePokemonSide.valueOf(value)

    private object Knowledge : PokemonKnowledge {
        private fun move(name: String, type: PokemonType, turns: Int, energy: Int) = Move(
            name = name, type = type, damage = 1, energy = energy, isFast = true, turns = turns)
        private val pokemon = listOf(
            Pokemon(460, "Abomasnow", listOf(PokemonType.GRASS, PokemonType.ICE), "Sinnoh",
                fastMoves = listOf(move("Powder Snow", PokemonType.ICE, 2, 8)), chargedMoves = emptyList()),
            Pokemon(215, "Sneasel", listOf(PokemonType.DARK, PokemonType.ICE), "Johto",
                fastMoves = listOf(move("Feint Attack", PokemonType.DARK, 2, 6), move("Ice Shard", PokemonType.ICE, 3, 10)), chargedMoves = emptyList()),
            Pokemon(364, "Sealeo", listOf(PokemonType.WATER, PokemonType.ICE), "Hoenn",
                fastMoves = listOf(move("Water Gun", PokemonType.WATER, 1, 3), move("Powder Snow", PokemonType.ICE, 2, 8)), chargedMoves = emptyList()),
            Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
                fastMoves = listOf(move("Incinerate", PokemonType.FIRE, 5, 20), move("Ember", PokemonType.FIRE, 2, 6),
                    move("Rock Smash", PokemonType.FIGHTING, 3, 10)), chargedMoves = emptyList()),
            Pokemon(711, "Gourgeist", listOf(PokemonType.GRASS, PokemonType.GHOST), "Kalos",
                fastMoves = listOf(move("Incinerate", PokemonType.FIRE, 5, 20), move("Hex", PokemonType.GHOST, 3, 12)), chargedMoves = emptyList()),
            Pokemon(134, "Vaporeon", listOf(PokemonType.WATER), "Kanto",
                fastMoves = listOf(move("Water Gun", PokemonType.WATER, 1, 3)), chargedMoves = emptyList()),
        )
        override suspend fun getPokemonByName(name: String) = pokemon.find { it.name == name }
        override suspend fun getPokemonById(id: Int) = pokemon.find { it.id == id }
        override suspend fun getAllSpeciesNames() = pokemon.map { it.name }.toSet()
    }
}
