package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import com.example.overdex.battle.observation.MatchId
import org.junit.Assert.*
import org.junit.Test

class FaintHistoryCacheTest {
    private val side = ActivePokemonSide.OPPONENT
    private fun rows(id: String = "identity") = listOf(
        TeamLossObservation.Identity(id, 1, "Carbink", 703),
        TeamLossObservation.Count("three", 2, 3),
        TeamLossObservation.Count("two", 3, 2))

    @Test fun `identical values reuse results while contents side order and conflicts miss`() {
        val cache = FaintHistoryCache()
        var calls = 0
        fun resolve(input: List<TeamLossObservation>, target: ActivePokemonSide = side) =
            cache.team(target, input) { calls++; TeamLossResolver.resolve(input) }
        val original = resolve(rows())
        assertSame(original, resolve(rows().toMutableList()))
        assertEquals(1, calls)
        val changed = rows().map { if (it is TeamLossObservation.Count && it.count == 2) it.copy(count=1) else it }
        assertEquals(TeamLossResolver.resolve(changed), resolve(changed))
        resolve(rows(), ActivePokemonSide.PLAYER)
        resolve(rows().reversed())
        val conflict = rows() + TeamLossObservation.Identity("identity", 1, "Gourgeist", 711)
        assertEquals(TeamLossResolver.resolve(conflict), resolve(conflict))
        assertEquals(5, calls)
    }

    @Test fun `hash collision is not evidence of identical history`() {
        val a = rows("Aa"); val b = rows("BB")
        assertEquals(a.hashCode(), b.hashCode())
        val cache = FaintHistoryCache()
        assertEquals(TeamLossResolver.resolve(a), cache.team(side,a) { TeamLossResolver.resolve(a) })
        assertEquals(TeamLossResolver.resolve(b), cache.team(side,b) { TeamLossResolver.resolve(b) })
        assertEquals(0L, cache.snapshot().hits)
        assertEquals(2L, cache.snapshot().misses)
    }

    @Test fun `keys snapshot list membership and eviction only causes recomputation`() {
        val cache = FaintHistoryCache(maxEntries=2, maxRows=6)
        val mutable = rows().toMutableList()
        val first = cache.team(side,mutable) { TeamLossResolver.resolve(mutable) }
        mutable += TeamLossObservation.Alive("return", 4)
        assertSame(first, cache.team(side,rows()) { error("Original snapshot should still match") })
        repeat(20) { i ->
            val input = rows("$i")
            assertEquals(TeamLossResolver.resolve(input),cache.team(side,input) { TeamLossResolver.resolve(input) })
            assertTrue(cache.snapshot().entries <= 2)
            assertTrue(cache.snapshot().retainedRows <= 6)
        }
        assertTrue(cache.snapshot().evictions > 0)
        cache.clear()
        assertEquals(0, cache.snapshot().retainedRows)
        assertEquals(first,cache.team(side,rows()) { TeamLossResolver.resolve(rows()) })
        val oversized = rows() + rows("large") + rows("larger")
        assertEquals(TeamLossResolver.resolve(oversized),cache.team(side,oversized) { TeamLossResolver.resolve(oversized) })
        assertEquals(1L,cache.snapshot().oversized)
    }

    @Test fun `concurrent consumers compute identical history only once`() {
        val cache = FaintHistoryCache()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val futures = List(16) {
                pool.submit<List<TeamLossResolution>> {
                    start.await()
                    cache.team(side, rows()) {
                        calls.incrementAndGet()
                        TeamLossResolver.resolve(rows())
                    }
                }
            }
            start.countDown()
            val first = futures.first().get(5, java.util.concurrent.TimeUnit.SECONDS)
            futures.forEach { assertSame(first, it.get(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
            assertEquals(15L, cache.snapshot().hits)
        } finally { pool.shutdownNow() }
    }

    @Test fun `failed computation is never retained`() {
        val cache = FaintHistoryCache()
        runCatching { cache.team(side, rows()) { error("fixture failure") } }
        assertEquals(0,cache.snapshot().entries)
        assertEquals(TeamLossResolver.resolve(rows()),cache.team(side,rows()) { TeamLossResolver.resolve(rows()) })
    }

    @Test fun `late identities returns repeated counts resets and conflicting reads stay equivalent`() {
        val cache = FaintHistoryCache()
        val cached = List(5) { LiveTeamLossHistory(side, cache=cache) }
        val controls = List(5) { LiveTeamLossHistory(side) }
        val input = listOf(
            article("a", 1, ActivePokemonSpeciesWitnessed(side,"Carbink",703)),
            article("three", 2, OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,3,3)),
            article("b", 5, ActivePokemonSpeciesWitnessed(side,"Gourgeist",711)),
            article("two", 6, OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,2,3)),
            article("alive",7,ActiveHpBarMeasured(side,20,30,120,40,.5f)),
            article("late",4,ActivePokemonSpeciesWitnessed(side,"Quagsire",195)),
            article("return",9,ActivePokemonSpeciesWitnessed(side,"Carbink",703)),
            article("return-hp",10,ActiveHpBarMeasured(side,20,30,120,40,.5f)),
            article("two-repeat",11,OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,2,3)),
            article("return",9,ActivePokemonSpeciesWitnessed(side,"Camerupt",323)),
            article("reset",12,ObservationRestartedByUser),
            article("stale",3,ActivePokemonSpeciesWitnessed(side,"Carbink",703)),
            article("new",13,ActivePokemonSpeciesWitnessed(side,"Vaporeon",134)))
        for (a in input + input.takeLast(1)) for (i in cached.indices) {
            assertEquals(controls[i].accept(a),cached[i].accept(a))
            assertEquals(controls[i].resolutions,cached[i].resolutions)
            assertEquals(controls[i].hpReconciliation,cached[i].hpReconciliation)
            assertEquals(controls[i].currentFaints,cached[i].currentFaints)
        }
        assertTrue(cache.snapshot().hits > 0)
    }

    @Test fun `HP geometry confidence source validity and filtered history all participate in keys`() {
        val cache = FaintHistoryCache()
        val input = listOf<VerifiedHpFaintObservation>(
            VerifiedHpFaintObservation.Identity("a",1,"Carbink",703,true),
            VerifiedHpFaintObservation.Hp("red",2,20,30,120,40,.1f,"RED",1080,2400,.99f,true),
            VerifiedHpFaintObservation.Hp("empty",3,20,30,120,40,0f,"EMPTY",1080,2400,.99f,true))
        fun resolve(rows:List<VerifiedHpFaintObservation>) = cache.hp(side,rows) { VerifiedHpFaintResolver.reconcile(rows) }
        val first = resolve(input)
        assertEquals(1, first.faints.size)
        assertSame(first,resolve(input.toList()))
        val empty = input.last() as VerifiedHpFaintObservation.Hp
        for (changed in listOf(empty.copy(left=21),empty.copy(confidence=.1f),
            empty.copy(trackedBattlefieldSource=false),empty.copy(fraction=.5f),empty.copy(at=4))) {
            val rows=input.dropLast(1)+changed
            assertEquals(VerifiedHpFaintResolver.reconcile(rows),resolve(rows))
        }
        assertEquals(VerifiedHpFaintResolver.reconcile(input.drop(1)),resolve(input.drop(1)))
        assertEquals(1L,cache.snapshot().hits)
    }

    @Test fun `terminal history includes source confidence payload and side`() {
        val cache=FaintHistoryCache()
        val row=TerminalFinishRecord("result",20,"YOU_WIN_WITNESS",.99f,RawTestimony("YOU WIN!"))
        fun resolve(rows:List<TerminalFinishRecord>,s:ActivePokemonSide=side)=cache.terminal(s,rows) {
            TerminalChargedFaintResolver.resolve(rows).filter { it.side == s }
        }
        resolve(listOf(row));resolve(listOf(row.copy()))
        for (r in listOf(row.copy(source="GOOD_EFFORT_WITNESS"),row.copy(confidence=.1f),
            row.copy(payload=RawTestimony("GOOD EFFORT")),row.copy(at=21))) {
            assertEquals(TerminalChargedFaintResolver.resolve(listOf(r)).filter { it.side==side },resolve(listOf(r)))
        }
        resolve(listOf(row),ActivePokemonSide.PLAYER)
        assertEquals(1L,cache.snapshot().hits)
        assertEquals(6L,cache.snapshot().misses)
    }

    private fun article(id:String,at:Long,p:TestimonyPayload)=RealityArticle(
        ArticleId(id),at,at,SourceId("OPPONENT_SPECIES_WITNESS"),p,
        matchId=MatchId("cache-regression"),confidence=.99f,monotonicTimeNanos=at)
}
