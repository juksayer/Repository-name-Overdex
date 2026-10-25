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
import com.example.overdex.model.GameMaster
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordedFireFangMotionTest {
    @Test fun `October 25 paired idle victims lose all four false motion identities while Mawile stays Fire Fang`() = runBlocking {
        // Pixel perspective: ONLY player Mawile attacked. Input articles remain
        // unaltered and in publication order; the full archive stays on the PC.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/battle/2026-10-25-fire-fang-idle-motion.json")).readText())
        val before = MatchArchiveSerializer.serialize(archive)
        assertEquals("793cd89e-6e37-4da9-bfac-1159218b2d4f", archive.matchId)
        val oldIdle = archive.articles.mapNotNull { it.payload as? ArchivedFastMoveIdentified }
            .filter { it.side == "OPPONENT" }
        assertEquals(listOf("Psycho Cut", "Air Slash", "Dragon Tail", "Sand Attack"), oldIdle.map { it.moveName })
        assertTrue(archive.articles.none { (it.payload as? ArchivedFastMoveUseObserved)?.attackingSide == "OPPONENT" })
        val engine = FastMoveCadenceInference(knowledge)
        val energy = FastMoveEnergyInference(knowledge)
        val identities = mutableListOf<FastMoveCadenceDerivation>()
        val awards = mutableListOf<FastMoveEnergyDerivation>()
        for (a in archive.articles) {
            // The old identities remain visible in the fixture for audit. Only
            // observed inputs, with canonical player appearances, are re-derived.
            if (a.payload is ArchivedFastMoveIdentified ||
                (a.payload is ArchivedActivePokemonSpeciesWitnessed && a.payload.side == "PLAYER")) continue
            val input = live(a)
            awards += energy.accept(input)
            val derived = engine.accept(input)
            identities += derived
            for (result in derived) {
                assertTrue("Identity must never manufacture attacks", result.payload is FastMoveIdentified)
                awards += energy.accept(result.observedArticle.copy(payload = result.payload,
                    predecessorIds = result.predecessorIds, confidence = result.confidence))
            }
        }
        val idle = identities.filter { (it.payload as FastMoveIdentified).side == ActivePokemonSide.OPPONENT }
        assertTrue("Idle Aegislash/Flygon/Galvantula cannot acquire guessed moves: $idle", idle.isEmpty())
        val mawile = identities.filter { (it.payload as FastMoveIdentified).speciesName == "Mawile" }
        assertTrue("The attacking Mawile must still resolve", mawile.isNotEmpty())
        assertEquals(setOf("Fire Fang"), mawile.map { (it.payload as FastMoveIdentified).moveName }.toSet())
        assertTrue(mawile.any { (it.payload as FastMoveIdentified).basis.contains("EFFECTIVENESS_TEXT") })
        assertTrue(mawile.all { it.predecessorIds.isNotEmpty() })
        assertTrue(awards.any { it.payload.side == ActivePokemonSide.PLAYER && it.payload.moveName == "Fire Fang" })
        assertTrue(awards.none { it.payload.side == ActivePokemonSide.OPPONENT })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    private fun live(a: ArchivedRealityArticle): RealityArticle {
        val p = a.payload
        val payload = when (p) {
            is ArchivedPlayerActiveIdentityResolved -> ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER,
                p.speciesName, p.speciesId, playerTeamSlot = p.teamSlot, appearanceStartedAtNanos = p.appearanceStartedAtNanos)
            is ArchivedSpeciesCheckMeasured -> SpeciesCheckMeasured(side(p.side), p.windowId, p.status, p.reason,
                p.triggerMonotonicNanos, p.elapsedNanos, p.targetNanos, p.speciesName)
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

    companion object {
        private val knowledge: PokemonKnowledge by lazy {
            val master = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
                File("src/main/assets/gamemaster.json").readText())
            val moves = master.moves.associateBy { it.moveId }
            val byName = master.pokemon.associate { source ->
                source.speciesName to Pokemon(source.dex, source.speciesName,
                    source.types.mapNotNull { runCatching { PokemonType.valueOf(it.uppercase()) }.getOrNull() }, "",
                    fastMoves = source.fastMoves.mapNotNull { id -> moves[id]?.let { move ->
                        Move(move.name, PokemonType.valueOf(move.type.uppercase()), move.power, move.energyGain,
                            isFast = true, turns = move.turns)
                    } }, chargedMoves = source.chargedMoves.mapNotNull { id -> moves[id]?.let { move ->
                        Move(move.name, PokemonType.valueOf(move.type.uppercase()), move.power,
                            kotlin.math.abs(move.energy), isFast = false)
                    } })
            }
            object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String) = byName[name]
                override suspend fun getPokemonById(id: Int) = byName.values.firstOrNull { it.id == id }
                override suspend fun getAllSpeciesNames() = byName.keys
            }
        }
    }
}
