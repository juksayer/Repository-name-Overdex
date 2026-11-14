package com.example.overdex.battle.observation

import com.example.overdex.battle.audio.CryDepartureEvidence
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

/** Joins the cry projection, actual resource assignment, and shared live/replay loss resolver. */
class RepeatCryIndependentFaintTest {
    @Test fun `candidate requests a count but only an independent loss confirms the faint`() {
        val scenario=Scenario()
        scenario.prefix()
        assertTrue(scenario.faints.isEmpty())
        assertEquals(1,scenario.candidates.size)
        assertTrue(scenario.gate.isEnabled(OPPONENT_BALLS))
        assertFalse(scenario.gate.isEnabled(PLAYER_BALLS))
        scenario.accept(count("decrease",6,2))
        val faint=scenario.faints.single()
        assertEquals("Golbat",faint.speciesName)
        assertEquals("decrease",faint.sourceArticle.id.value)
        assertEquals(6*SECOND,faint.sourceArticle.monotonicTimeNanos)
        assertTrue(faint.evidenceReferences.containsAll(listOf("opponent","baseline","decrease")))
        assertFalse("The scheduling hint is not independent count evidence",faint.evidenceReferences.any { it.startsWith("hint-") })
    }

    @Test fun `unchanged count and an ordinary switch do not turn a cry hint into a faint`() {
        val scenario=Scenario()
        scenario.prefix()
        scenario.accept(species("replacement",6,ActivePokemonSide.OPPONENT,"Swampert",260))
        scenario.accept(count("still-three",7,3))
        scenario.accept(alive("replacement-alive",8))
        assertTrue(scenario.faints.isEmpty())
        assertTrue(scenario.tracker.resolutions.isEmpty())
    }

    @Test fun `a cry cannot choose between count-loss owners but subsequent survival can`() {
        val scenario=Scenario()
        scenario.prefix()
        scenario.accept(species("replacement",6,ActivePokemonSide.OPPONENT,"Swampert",260))
        scenario.accept(count("delayed-decrease",7,2))
        assertTrue("Even a named Golbat cry candidate cannot settle an ambiguous count loss",scenario.faints.isEmpty())
        assertTrue(scenario.tracker.resolutions.single().resolvedCandidates.isEmpty())
        scenario.accept(alive("replacement-alive",8))
        val faint=scenario.faints.single()
        assertEquals("Golbat",faint.speciesName)
        assertEquals("delayed-decrease",faint.sourceArticle.id.value)
        assertTrue("Independent survival must be in the resolution basis","replacement-alive" in faint.evidenceReferences)
        assertFalse(faint.cryCorroborated)
    }

    private class Scenario {
        var now=0L
        val gate=TeamResourceSnapshotGate(setOf(PLAYER_BALLS,OPPONENT_BALLS),setOf(PLAYER_BALLS,OPPONENT_BALLS),{now})
        val tracker=TeamFaintTracker(ActivePokemonSide.OPPONENT)
        val evidence=CryDepartureEvidence()
        val candidates=mutableListOf<RealityArticle>()
        val faints=mutableListOf<TeamFaintDecision>()
        fun accept(input:RealityArticle) {
            now=input.monotonicTimeNanos!!
            gate.observe(input)
            faints+=tracker.acceptAll(input)
            for(candidate in evidence.observe(input)) {
                val hint=article("hint-${candidates.size}",candidate.atNanos/SECOND,
                    RepeatCryDepartureCandidateObserved(candidate.speciesId,candidate.side,
                        candidate.possibleAppearanceIds,candidate.cryOccurrenceIds),"REPEAT_CRY_DEPARTURE_WITNESS")
                    .copy(evidenceReferences=candidate.evidenceReferences)
                candidates+=hint
                assertTrue(tracker.acceptAll(hint).isEmpty())
                (candidate.side?.let(::listOf) ?: ActivePokemonSide.entries).forEach {
                    gate.requestDepartureCheck(it,candidate.atNanos)
                }
            }
        }
        fun prefix() {
            accept(species("player",1,ActivePokemonSide.PLAYER,"Turtonator",776))
            accept(species("opponent",1,ActivePokemonSide.OPPONENT,"Golbat",42))
            accept(count("baseline",2,3))
            gate.measured(PLAYER_BALLS,now);gate.measured(OPPONENT_BALLS,now)
            accept(article("entry-cry",3,BattleCryLocalized(42,"reference",.9f,100_000_000),CryDepartureEvidence.SOURCE))
            accept(article("repeat-cry",5,BattleCryLocalized(42,"reference",.9f,100_000_000),CryDepartureEvidence.SOURCE))
        }
    }
    companion object {
        private const val SECOND=1_000_000_000L
        private val PLAYER_BALLS=BattleCropContracts.playerPokeBalls.cropName
        private val OPPONENT_BALLS=BattleCropContracts.opponentPokeBalls.cropName
        private fun article(id:String,at:Long,payload:TestimonyPayload,source:String="SPECIES_WITNESS")=
            RealityArticle(ArticleId(id),at*1000,at*1000,SourceId(source),payload,
                evidenceReferences=listOf("retained-$id"),monotonicTimeNanos=at*SECOND)
        private fun species(id:String,at:Long,side:ActivePokemonSide,name:String,dex:Int)=
            article(id,at,ActivePokemonSpeciesWitnessed(side,name,dex))
        private fun count(id:String,at:Long,value:Int)=article(id,at,
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,value,3),"OPPONENT_POKE_BALL_WITNESS")
        private fun alive(id:String,at:Long)=article(id,at,
            ActiveHpBarMeasured(ActivePokemonSide.OPPONENT,20,30,120,40,.5f),"OPPONENT_HP_WITNESS")
    }
}
