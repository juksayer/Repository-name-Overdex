package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.observation.TeamFaintTracker
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.util.zip.GZIPInputStream
import org.junit.Assert.*
import org.junit.Test

/** Original archives plus separately identified video measurements for an offline regression.
 * The video-to-capture alignment is approximate; neither device archive is rewritten. */
class RecordedTerminalChargedFaintTest {
    private fun original(stamp: String): MatchArchive = javaClass.getResourceAsStream(
        "/battle/recorded-evidence/2026-11-12-terminal-sequences/$stamp-original.json.gz")!!.use {
        GZIPInputStream(it).bufferedReader().use { MatchArchiveSerializer.deserialize(it.readText()) }
    }
    private fun enriched(stamp: String, alignmentShift: Long = 0, factory: Boolean = false): MatchArchive {
        val archive = original(stamp)
        val start = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val offset = start - (if (stamp == "211335") 39_000_000_000L else 24_863_511_000L) + alignmentShift
        val charge = archive.articles.last { it.payload is ArchivedChargedMoveEnergySpent }.monotonicTimeNanos!!
        val phase = charge - 10_000_000_000L
        val until = charge + 6_000_000_000L
        val measured = if (factory) TerminalSequenceFixtures.factorySamples(stamp) else TerminalSequenceFixtures.samples(stamp)
        val samples = measured.map {
            it.copy(sourceAtNanos = it.sourceAtNanos + offset,
                previousSourceAtNanos = it.previousSourceAtNanos?.plus(offset))
        }.filter { it.sourceAtNanos in phase until until }
        val batches = samples.chunked(20).mapIndexed { i, frames -> ArchivedRealityArticle(
            "test-video-enrichment-$stamp-$i",archive.matchId,frames.last().wallTimeMillis,
            frames.last().wallTimeMillis,"TERMINAL_PARTICLE_WITNESS",
            ArchivedTerminalParticleObservations("OPPONENT",phase,until,frames,i==samples.chunked(20).lastIndex,samples.size),
            emptyList(),null,null,listOf("test-only-video-source-$stamp"),frames.last().sourceAtNanos) }
        return archive.copy(articles=archive.articles+batches)
    }

    @Test fun `cached terminal histories preserve supported collapses across delivery orders`() {
        for (stamp in listOf("211335", "112107")) {
            val archive = enriched(stamp, factory = true)
            val inputs = archive.articles.mapNotNull { it.terminalFinishRecord() }
            val expected = TerminalChargedFaintResolver.resolve(inputs).single()
            for (ordered in listOf(inputs, inputs.reversed(), inputs.shuffled(kotlin.random.Random(7)))) {
                val cache = FaintHistoryCache()
                val cached = List(2) { LiveTeamLossHistory(ActivePokemonSide.OPPONENT, cache = cache) }
                val control = LiveTeamLossHistory(ActivePokemonSide.OPPONENT)
                ordered.forEach { r ->
                    val article = RealityArticle(ArticleId(r.id), r.at / 1_000_000, r.at / 1_000_000,
                        SourceId(r.source), r.payload, confidence = r.confidence,
                        matchId = MatchId(archive.matchId), monotonicTimeNanos = r.at)
                    control.accept(article)
                    cached.forEach {
                        it.accept(article)
                        assertEquals(control.terminalFaints, it.terminalFaints)
                    }
                }
                assertEquals(expected, cached.first().terminalFaints.single())
                assertTrue(cache.snapshot().hits > 0)
            }
        }
    }

    @Test fun `original archives remain unresolved without terminal images even though both record wins`() {
        for (stamp in listOf("211335","112107")) {
            val a=original(stamp); val bytes=MatchArchiveSerializer.serialize(a)
            assertTrue(TerminalChargedFaintResolver.resolve(a.articles.mapNotNull { it.terminalFinishRecord() }).isEmpty())
            assertTrue(ReplayTerminalChargedFaintProjection(a,a.articles).resolutionsByArticleId.isEmpty())
            assertEquals(bytes,MatchArchiveSerializer.serialize(a))
        }
    }

    @Test fun `both recorded final charged collapses resolve without empty HP or final zero balls`() {
        for ((stamp,name) in listOf("211335" to "Cramorant","112107" to "Leavanny")) {
            for (shift in listOf(-1_000_000_000L,0L,1_000_000_000L)) {
                val a=enriched(stamp,shift)
                val r=TerminalChargedFaintResolver.resolve(a.articles.mapNotNull { it.terminalFinishRecord() }).single()
                assertEquals(name,r.owner.speciesName)
                val source=a.articles.single { it.articleId==r.sourceArticleId }.payload as ArchivedTerminalParticleObservations
                assertTrue(source.samples.any { it.sourceAtNanos==r.atNanos })
                assertTrue(r.evidenceReferences.contains(r.sourceArticleId))
            }
        }
    }

    @Test fun `production factory crop measurements resolve final KOs while QTE alone stays unresolved`() {
        for ((stamp,name) in listOf("211335" to "Cramorant", "112107" to "Leavanny")) {
            for (shift in listOf(-1_000_000_000L, 0L, 1_000_000_000L)) {
                val a = enriched(stamp, shift, factory = true)
                val before = MatchArchiveSerializer.serialize(a)
                val inputs = a.articles.mapNotNull { it.terminalFinishRecord() }
                val resolution = TerminalChargedFaintResolver.resolve(inputs).single()
                assertEquals(name, resolution.owner.speciesName)
                val live = LiveTeamLossHistory(ActivePokemonSide.OPPONENT)
                inputs.reversed().forEach { r -> live.accept(RealityArticle(ArticleId(r.id), r.at/1_000_000,
                    r.at/1_000_000, SourceId(r.source), r.payload, confidence=r.confidence,
                    matchId=MatchId(a.matchId), monotonicTimeNanos=r.at)) }
                assertEquals(resolution, live.terminalFaints.single())
                assertEquals(resolution, ReplayTerminalChargedFaintProjection(a,a.articles).resolutionsByArticleId.values.single())
                val model = MatchReplayModel(a)
                assertFalse(model.sceneAt(resolution.atNanos-1).opponent!!.isFainted)
                assertTrue(model.sceneAt(resolution.atNanos+2_000_000_000L).opponent!!.isFainted)
                assertEquals(0f,model.sceneAt(resolution.atNanos+2_000_000_000L).opponentHp!!.filledFraction!!,0f)
                assertEquals(before, MatchArchiveSerializer.serialize(a))
                if (stamp == "112107") {
                    val charge = inputs.last { it.payload is ChargedMoveEnergySpent }.at
                    val preHit = inputs.map { r ->
                        val p = r.payload as? TerminalParticleObservations
                        if (p == null) r else r.copy(payload=p.copy(samples=p.samples.filter { it.sourceAtNanos < charge }))
                    }
                    val frames = preHit.flatMap { r -> (r.payload as? TerminalParticleObservations)?.samples
                        ?.map { TerminalEffectFrame(r.id,it) }.orEmpty() }
                    assertEquals("Two real QTE lookalikes must reach inference",2,TerminalCollapseSequences.find(frames).size)
                    assertTrue(TerminalChargedFaintResolver.resolve(preHit).isEmpty())
                }
            }
        }
    }

    @Test fun `live tracking and completed Replay use identical source clocks and inferred HP endpoint`() {
        for ((stamp,name) in listOf("211335" to "Cramorant","112107" to "Leavanny")) {
            val a=enriched(stamp); val before=MatchArchiveSerializer.serialize(a)
            val inputs=a.articles.mapNotNull { it.terminalFinishRecord() }
            val expected=TerminalChargedFaintResolver.resolve(inputs).single()
            val sourceMap=a.articles.associateBy { it.articleId }
            for (ordered in listOf(inputs,inputs.reversed(),inputs.shuffled(kotlin.random.Random(7)))) {
                val tracker=TeamFaintTracker(); val history=LiveTeamLossHistory(ActivePokemonSide.OPPONENT)
                val decisions=ordered.flatMap { r ->
                    val source=sourceMap.getValue(r.id)
                    val live=RealityArticle(ArticleId(r.id),source.perceivedAt,source.recordedAt,SourceId(r.source),r.payload,
                        confidence=r.confidence,matchId=MatchId(a.matchId),monotonicTimeNanos=r.at)
                    history.accept(live); tracker.acceptAll(live)
                }
                assertEquals(expected,history.terminalFaints.single())
                val faint=decisions.last { it.basis==TerminalChargedFaintResolver.BASIS }
                assertEquals(name,faint.speciesName)
                assertEquals(expected.atNanos,faint.sourceArticle.monotonicTimeNanos)
            }
            val projection=ReplayTerminalChargedFaintProjection(a,a.articles)
            assertEquals(expected,projection.resolutionsByArticleId.values.single())
            val model=MatchReplayModel(a)
            assertFalse(model.sceneAt(expected.atNanos-1).opponent!!.isFainted)
            val after=model.sceneAt(expected.atNanos+2_000_000_000L)
            assertEquals(name,after.opponent!!.speciesName)
            assertTrue(after.opponent.isFainted)
            assertEquals(0f,after.opponentHp!!.filledFraction!!,0f)
            assertEquals(before,MatchArchiveSerializer.serialize(a))
            assertEquals(a.articles.size,model.timedArticles.size)
        }
    }

    @Test fun `result particles or named hit alone cannot substitute for required evidence`() {
        val inputs=enriched("211335").articles.mapNotNull { it.terminalFinishRecord() }
        val remove: List<(TerminalFinishRecord)->Boolean> = listOf(
            { it.payload is ChargedMoveEnergySpent || it.payload==ChargeMoveUsedAnnounced },
            { it.payload is TerminalParticleObservations },
            { it.source=="YOU_WIN_WITNESS" }, { it.payload is OpponentBattleResourceCountMeasured },
            { (it.payload as? ActivePokemonSpeciesWitnessed)?.side==ActivePokemonSide.OPPONENT },
            { (it.payload as? ActivePokemonSpeciesWitnessed)?.side==ActivePokemonSide.PLAYER })
        remove.forEachIndexed { i,predicate -> assertTrue("missing evidence $i",
            TerminalChargedFaintResolver.resolve(inputs.filterNot(predicate)).isEmpty()) }
    }

    @Test fun `outgoing partial proof needs a fresh independent direction rather than the archives stale Get Ready`() {
        val archive=enriched("211335")
        val input=archive.articles.mapNotNull { it.terminalFinishRecord() }
        val expected=TerminalChargedFaintResolver.resolve(input).single()
        val withoutSpend=input.filterNot { it.payload is ChargedMoveEnergySpent }
        assertTrue(TerminalChargedFaintResolver.resolve(withoutSpend).isEmpty())
        // This archive missed the final Get Ready. Add an explicitly test-only
        // phase to exercise outgoing symmetry; do not claim it was observed.
        val used=withoutSpend.last { it.payload==ChargeMoveUsedAnnounced }
        val uncosted=withoutSpend+TerminalFinishRecord("test-only-fresh-ready",used.at-10_000_000_000L,
            "GET_READY_WITNESS",null,GetReadyWitnessed)
        val actual=TerminalChargedFaintResolver.resolve(uncosted).single()
        assertEquals(expected.owner.speciesName,actual.owner.speciesName)
        assertEquals(expected.owner.speciesId,actual.owner.speciesId)
        assertEquals(expected.side,actual.side)
        // The partial Used frame predates the fully named announcement and the
        // intervening badge read; ownership must use the earlier capture clock.
        assertTrue(actual.owner.appearanceAtNanos<=used.at)
        assertEquals(expected.atNanos,actual.atNanos)
        assertTrue(actual.evidenceReferences.any { id -> uncosted.any { it.id==id && it.payload==GetReadyWitnessed } })
        assertTrue(actual.evidenceReferences.any { id -> uncosted.any { it.id==id && it.payload==ChargeMoveUsedAnnounced } })
        assertTrue(TerminalChargedFaintResolver.resolve(uncosted.filterNot { it.payload==GetReadyWitnessed }).isEmpty())
        assertTrue(TerminalChargedFaintResolver.resolve(uncosted.map {
            if(it.payload==GetReadyWitnessed) it.copy(payload=AttackIncoming,source="ATTACK_INCOMING_WITNESS") else it
        }).isEmpty())
    }

    @Test fun `switch new charge positive HP and conflicting source evidence revoke a proposed terminal KO`() {
        val inputs=enriched("211335").articles.mapNotNull { it.terminalFinishRecord() }
        val loss=TerminalChargedFaintResolver.resolve(inputs).single()
        val at=loss.atNanos-150_000_000L
        val contradictions=listOf(
            TerminalFinishRecord("switch",at,"SPECIES_CHECK_COORDINATOR",null,
                SpeciesCheckMeasured(ActivePokemonSide.OPPONENT,1,"OPENED","SWITCH",at,0,1)),
            TerminalFinishRecord("new-phase",at,"ATTACK_INCOMING_WITNESS",null,AttackIncoming),
            TerminalFinishRecord("resumed-fast-move",at,"FAST_MOVE_USE_INFERENCE",null,
                FastMoveUseObserved("resumed",ActivePokemonSide.PLAYER,ActivePokemonSide.OPPONENT,
                    evidenceKinds=listOf("HP_BORDER_PULSE"))),
            TerminalFinishRecord("alive",at,"OPPONENT_ACTIVE_HP_BAR_WITNESS",.99f,
                ActiveHpBarMeasured(ActivePokemonSide.OPPONENT,400,700,600,720,.17f,672,1493,"RED")),
            TerminalFinishRecord("entry",at,"ENTRY_ANNOUNCEMENT_SPECIES_WITNESS",.99f,
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT,"Cramorant",845)))
        contradictions.forEach { c -> assertTrue(c.id,TerminalChargedFaintResolver.resolve(inputs+c).isEmpty()) }
        val lastCount=inputs.last { it.source=="OPPONENT_POKE_BALLS_WITNESS" && it.payload is OpponentBattleResourceCountMeasured }
        assertTrue(TerminalChargedFaintResolver.resolve(inputs+lastCount.copy(payload=OpponentBattleResourceCountMeasured(
            OpponentBattleResource.POKE_BALLS,2,3))).isEmpty())
    }

    @Test fun `minigame sequence before the final named move cannot produce the Leavanny faint`() {
        val a=enriched("112107")
        val charge=a.articles.last { it.payload is ArchivedChargedMoveEnergySpent }.monotonicTimeNanos!!
        val rows=a.articles.mapNotNull { it.terminalFinishRecord() }.map { r ->
            val p=r.payload as? TerminalParticleObservations
            if(p==null) r else r.copy(payload=p.copy(samples=p.samples.filter { it.sourceAtNanos < charge }))
        }
        assertTrue(TerminalChargedFaintResolver.resolve(rows).isEmpty())
    }

    @Test fun `later independently identified survival retracts live and replay KO without editing original testimony`() {
        val a=enriched("211335")
        val inputs=a.articles.mapNotNull { it.terminalFinishRecord() }
        val loss=TerminalChargedFaintResolver.resolve(inputs).single()
        val tracked=FaintedCombatants()
        fun live(r: TerminalFinishRecord)=RealityArticle(ArticleId(r.id),r.at/1_000_000,r.at/1_000_000,
            SourceId(r.source),r.payload,confidence=r.confidence,matchId=MatchId(a.matchId),monotonicTimeNanos=r.at)
        inputs.forEach { tracked.observe(live(it)) }
        assertTrue(tracked.contains(ActivePokemonSide.OPPONENT,"Cramorant"))
        val at=loss.atNanos+1_000_000_000
        val identity=TerminalFinishRecord("returned-identity",at,"OPPONENT_SPECIES_OVERLAY_PIPELINE",.98f,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT,"Cramorant",845))
        val hp=TerminalFinishRecord("returned-hp",at+1,"OPPONENT_ACTIVE_HP_BAR_WITNESS",.98f,
            ActiveHpBarMeasured(ActivePokemonSide.OPPONENT,400,700,600,720,.17f,672,1493,"RED"))
        tracked.observe(live(identity))
        assertEquals(listOf("returned-identity"),tracked.observe(live(hp)).map { it.id.value })
        assertFalse(tracked.contains(ActivePokemonSide.OPPONENT,"Cramorant"))
        val updated=a.copy(articles=a.articles+listOf(identity,hp).map { RealityArticleArchiveMapper.map(live(it)) })
        assertTrue(ReplayTerminalChargedFaintProjection(updated,updated.articles).resolutionsByArticleId.isEmpty())
    }

    @Test fun `ambiguous same-time identities and additional survivors defeat a final-loss claim`() {
        val inputs=enriched("211335").articles.mapNotNull { it.terminalFinishRecord() }
        val loss=TerminalChargedFaintResolver.resolve(inputs).single()
        val id=inputs.single { it.id==loss.owner.appearanceArticleId }
        assertTrue(TerminalChargedFaintResolver.resolve(inputs+id.copy(id="conflicting-identity",payload=
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT,"Carbink",703))).isEmpty())
        val count=inputs.last { it.source=="OPPONENT_POKE_BALLS_WITNESS" && it.payload is OpponentBattleResourceCountMeasured }
        assertTrue(TerminalChargedFaintResolver.resolve(inputs+count.copy(id="more-survivors",at=count.at+1,payload=
            OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,2,3))).isEmpty())
    }

    @Test fun `a late batch envelope cannot discard its earlier finishing frames`() {
        val inputs=enriched("211335").articles.mapNotNull { it.terminalFinishRecord() }
        val expected=TerminalChargedFaintResolver.resolve(inputs).single()
        val resultAt=inputs.first { it.source=="YOU_WIN_WITNESS" }.at
        val late=inputs.map { r -> if (r.payload is TerminalParticleObservations) r.copy(at=resultAt+1) else r }
        assertEquals(expected,TerminalChargedFaintResolver.resolve(late).single())
    }

    @Test fun `stable final survivor count survives reader sleep but not a reset or contrary count`() {
        val inputs=enriched("211335").articles.mapNotNull { it.terminalFinishRecord() }
        val expected=TerminalChargedFaintResolver.resolve(inputs).single()
        val count=inputs.last { it.source=="OPPONENT_POKE_BALLS_WITNESS" && it.payload is OpponentBattleResourceCountMeasured }
        val delay=60_000_000_000L
        // Extend the same last-Pokemon appearance without fabricating a newer ball reading.
        // This is a scheduling regression, not a claim about the recording's actual duration.
        val delayed=inputs.map { r ->
            val p=r.payload as? TerminalParticleObservations
            r.copy(at=if(r.at>count.at) r.at+delay else r.at,
                payload=if(p==null) r.payload else p.copy(phaseAtNanos=p.phaseAtNanos+delay,
                    sourceDeadlineNanos=p.sourceDeadlineNanos+delay,
                    samples=p.samples.map { it.copy(sourceAtNanos=it.sourceAtNanos+delay,
                        previousSourceAtNanos=it.previousSourceAtNanos?.plus(delay)) }))
        }
        val actual=TerminalChargedFaintResolver.resolve(delayed).single()
        assertEquals(expected.owner.speciesName,actual.owner.speciesName)
        assertEquals(expected.atNanos+delay,actual.atNanos)
        assertTrue(actual.evidenceReferences.contains(count.id))
        assertEquals(count,delayed.single { it.id==count.id })
        val reset=TerminalFinishRecord("reset-after-count",count.at+1,"USER",null,ObservationRestartedByUser)
        assertTrue(TerminalChargedFaintResolver.resolve(delayed+reset).isEmpty())
        val contrary=count.copy(id="later-survivors",at=count.at+1,
            payload=OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS,2,3))
        assertTrue(TerminalChargedFaintResolver.resolve(delayed+contrary).isEmpty())
    }
}
