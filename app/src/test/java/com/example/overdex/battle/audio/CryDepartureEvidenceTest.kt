package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class CryDepartureEvidenceTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private fun article(id: String, at: Long, payload: TestimonyPayload, source: String = CryDepartureEvidence.SOURCE,
        refs: List<String> = listOf("pcm-$id")) = RealityArticle(ArticleId(id), at / 1_000_000, 900_000,
        SourceId(source), payload, evidenceReferences = refs, monotonicTimeNanos = at)
    private fun identity(id: String, at: Long, side: ActivePokemonSide, species: Int, entry: Boolean = false) =
        article(id, at, ActivePokemonSpeciesWitnessed(side, "species$species", species),
            if(entry) "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" else "SPECIES_WITNESS")
    private fun cry(id: String, at: Long, species: Int) =
        article(id, at, BattleCryLocalized(species, "verified-reference", .9f, 100_000_000))
    private val second = 1_000_000_000L

    @Test fun `delayed visual entry history resolves cries by source time with preserved references`() {
        val model = CryDepartureEvidence()
        model.observe(identity("player", 0, player, 776))
        model.observe(identity("old", 0, opponent, 42))
        assertTrue(model.observe(cry("entry", 5*second, 460)).isEmpty())
        assertTrue(model.observe(cry("repeat", 10*second, 460)).isEmpty())
        // Delivered after both cries. Recognition completion time is deliberately unrelated.
        val found = model.observe(identity("new", 4*second, opponent, 460, true)).single()
        assertEquals(opponent, found.side)
        assertEquals(10*second, found.atNanos)
        assertEquals(listOf("entry", "repeat"), found.cryOccurrenceIds)
        assertTrue(found.evidenceReferences.containsAll(listOf("entry", "repeat", "pcm-entry", "pcm-repeat", "new", "player")))
        assertTrue(model.observe(identity("same-badge", 11*second, opponent, 460)).isEmpty())
    }

    @Test fun `audio identities cannot bootstrap corroboration and missing PCM reference is excluded`() {
        val model = CryDepartureEvidence()
        model.observe(identity("p",0,player,776))
        model.observe(identity("o",0,opponent,42).copy(sourceId=SourceId("DECISIVE_BATTLE_CRY_SPECIES_WITNESS")))
        model.observe(cry("a",second,42))
        assertTrue(model.observe(cry("b",3*second,42)).isEmpty())
        // Only one accepted acoustic occurrence remains when one has no retained PCM basis.
        val other = CryDepartureEvidence()
        other.observe(identity("p",0,player,776));other.observe(identity("o",0,opponent,42))
        other.observe(cry("a",second,42).copy(evidenceReferences=emptyList()))
        assertTrue(other.observe(cry("b",3*second,42)).isEmpty())
    }

    @Test fun `ordinary switch and returning species start independent appearances`() {
        val model = CryDepartureEvidence()
        val events = listOf(identity("p",0,player,776),identity("o",0,opponent,260),
            cry("s-entry",second,260),identity("aboma",4*second,opponent,460,true),cry("a-entry",5*second,460),
            identity("return",8*second,opponent,260,true),cry("s-return",9*second,260))
        assertTrue(events.flatMap(model::observe).isEmpty())
        val found=model.observe(cry("s-repeat",12*second,260)).single()
        assertEquals(listOf("s-return","s-repeat"),found.cryOccurrenceIds)
    }

    @Test fun `mirror repeats remain unassigned and overlapping captures are not additional cries`() {
        val model=CryDepartureEvidence()
        model.observe(identity("p",0,player,776));model.observe(identity("o",0,opponent,776))
        model.observe(cry("a",second,776))
        assertTrue(model.observe(cry("a-overlap",second+50_000_000,776)).isEmpty())
        val found=model.observe(cry("b",3*second,776)).single()
        assertNull(found.side)
        assertEquals(listOf("o","p"),found.possibleAppearanceIds)
    }

    @Test fun `bounded history fails explicitly and manual reset rejects old observations`() {
        val model=CryDepartureEvidence()
        repeat(CryDepartureEvidence.MAX_CRIES+1) { model.observe(cry("c$it",it*second,42)) }
        assertTrue(model.unavailable)
        model.observe(article("reset",400*second,ObservationRestartedByUser))
        assertFalse(model.unavailable)
        model.observe(identity("old",0,player,776))
        assertTrue(model.speciesIds().isEmpty())
    }

    @Test fun `cry gate remains open after derived faints but closes on result and restart`() {
        val gate=CryListeningGate()
        gate.observe(article("vs",0,VsScreenWitnessed))
        repeat(6) { gate.observe(article("f$it",(it+1)*second,
            ActivePokemonFaintedWitnessed(if(it%2==0) player else opponent,"species$it"))) }
        assertTrue(gate.accepts(7*second,11*second,gate.generation))
        gate.observe(article("result",12*second,MatchEnded(BattleResult.WIN)))
        assertFalse(gate.allows(12*second))
        val old=gate.generation
        gate.observe(article("restart",20*second,ObservationRestartedByUser))
        assertFalse(gate.accepts(7*second,11*second,old))
        gate.observe(article("oldvs",second,VsScreenWitnessed))
        assertFalse(gate.allows(21*second))
    }
    @Test fun `a cry before the first new badge is possible only after the last old badge`() {
        fun check(oldSighting:Long):List<com.example.overdex.battle.inference.RepeatCryDepartureCandidate> {
            val model=CryDepartureEvidence()
            model.observe(identity("player",0,player,776))
            model.observe(identity("old",0,opponent,42))
            model.observe(identity("last-old",oldSighting,opponent,42))
            model.observe(cry("entrance",5*second,460))
            model.observe(identity("new-badge",7*second,opponent,460))
            return model.observe(cry("repeat",10*second,460))
        }
        val supported=check(4*second).single()
        assertEquals(opponent,supported.side)
        assertTrue("The previous sighting supplies the possible-entry bound", "last-old" in supported.evidenceReferences)
        assertTrue("An old-species sighting after the cry excludes that new appearance",check(6*second).isEmpty())
    }

}
