package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.observation.TeamFaintTracker
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream

class RecordedPlayerAppearanceFaintTest {
    private fun original(first: Boolean=false): MatchArchive {
        val path=if(first) "/battle/recorded-evidence/2026-11-12-terminal-sequences/190345-first-original.json.gz"
            else "/replay/moondrop-aggregate-team-loss-20261111.json.gz"
        return javaClass.getResourceAsStream(path)!!.use { GZIPInputStream(it).bufferedReader().use {
            MatchArchiveSerializer.deserialize(it.readText())
        } }
    }
    private fun enriched(first: Boolean=false, shift: Long=0): MatchArchive {
        val archive=original(first)
        val start=archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val offset=start-(if(first) 19_851_000_000L else 211_063_000_000L)+shift
        // A reviewed final incoming charge window. Its assignment cannot supply a named move.
        val phase=start+(if(first) 140_000_000_000L else 135_000_000_000L)
        val until=start+(if(first) 154_000_000_000L else 150_000_000_000L)
        val samples=TerminalAppearanceFixtures.samples("190345").map { s -> s.copy(
            sourceAtNanos=s.sourceAtNanos+offset,
            appearance=s.appearance!!.let { a -> a.copy(previousSourceAtNanos=a.previousSourceAtNanos?.plus(offset),
                supports=a.supports.map { it.copy(baselineAtNanos=it.baselineAtNanos+offset) }) }
        ) }.filter { it.sourceAtNanos in phase until until }
        val chunks=samples.chunked(20)
        val batches=chunks.mapIndexed { i,frames -> ArchivedRealityArticle("test-appearance-$i",archive.matchId,
            0,0,"TERMINAL_PARTICLE_WITNESS",ArchivedTerminalParticleObservations("PLAYER",phase,until,frames,
                i==chunks.lastIndex,samples.size),emptyList(),null,null,listOf("test-only-video-190345"),frames.last().sourceAtNanos) }
        return archive.copy(articles=archive.articles+batches)
    }
    private fun live(r: TerminalFinishRecord, id: String)=RealityArticle(ArticleId(r.id),0,0,SourceId(r.source),
        r.payload,confidence=r.confidence,matchId=MatchId(id),monotonicTimeNanos=r.at)

    @Test fun `Camerupt final charged KO resolves in live and Replay without an empty HP observation`() {
        val original=original(); val originalBytes=MatchArchiveSerializer.serialize(original)
        assertTrue(ReplayTerminalChargedFaintProjection(original,original.articles).resolutionsByArticleId.isEmpty())
        for(shift in listOf(-1_000_000_000L,0L,1_000_000_000L)) {
            val archive=enriched(shift=shift)
            val bytes=MatchArchiveSerializer.serialize(archive)
            val inputs=archive.articles.mapNotNull { it.terminalFinishRecord() }
            val loss=TerminalChargedFaintResolver.resolve(inputs).single()
            assertEquals("Camerupt",loss.owner.speciesName)
            assertEquals(ActivePokemonSide.PLAYER,loss.side)
            assertEquals(10825626046183L+147_709_700_000L+shift,loss.atNanos)
            val lastHp=original.articles.last { (it.payload as? ArchivedActiveHpBarMeasured)?.side=="PLAYER" }
            assertTrue((lastHp.payload as ArchivedActiveHpBarMeasured).filledFraction!! > .7f)
            for(ordered in listOf(inputs,inputs.reversed(),inputs.shuffled(kotlin.random.Random(5)))) {
                val history=LiveTeamLossHistory(ActivePokemonSide.PLAYER)
                val tracker=TeamFaintTracker(ActivePokemonSide.PLAYER)
                val decisions=ordered.flatMap { r -> val article=live(r,archive.matchId)
                    history.accept(article); tracker.acceptAll(article) }
                assertEquals(loss,history.terminalFaints.single())
                assertEquals("Camerupt",decisions.last { it.basis==TerminalChargedFaintResolver.BASIS }.speciesName)
            }
            assertEquals(loss,ReplayTerminalChargedFaintProjection(archive,archive.articles).resolutionsByArticleId.values.single())
            val model=MatchReplayModel(archive)
            assertFalse(model.sceneAt(loss.atNanos-1).player!!.isFainted)
            val after=model.sceneAt(loss.atNanos+2_000_000_000L)
            assertTrue(after.player!!.isFainted)
            assertEquals("Camerupt",after.player.speciesName)
            assertEquals(0f,after.playerHp!!.filledFraction!!,0f)
            assertEquals((lastHp.payload as ArchivedActiveHpBarMeasured).filledFraction,
                model.sceneAt(lastHp.monotonicTimeNanos!!).playerHp!!.filledFraction)
            assertEquals(bytes,MatchArchiveSerializer.serialize(archive))
            assertEquals(originalBytes,MatchArchiveSerializer.serialize(original))
        }
    }

    @Test fun `clipped move name still supports the recorded Gourgeist charged KO without inventing a named move`() {
        val original=original(first=true)
        val originalBytes=MatchArchiveSerializer.serialize(original)
        assertTrue(ReplayTerminalChargedFaintProjection(original,original.articles).resolutionsByArticleId.isEmpty())
        for(shift in listOf(-1_000_000_000L,0L,1_000_000_000L)) {
            val archive=enriched(first=true,shift=shift)
            val bytes=MatchArchiveSerializer.serialize(archive)
            val inputs=archive.articles.mapNotNull { it.terminalFinishRecord() }
            val loss=TerminalChargedFaintResolver.resolve(inputs).single()
            assertEquals("Gourgeist",loss.owner.speciesName)
            assertEquals(ActivePokemonSide.PLAYER,loss.side)
            assertEquals(10634528455710L+150_836_066_666L+shift,loss.atNanos)
            val used=inputs.single { it.id=="0b803bc5-6c5e-4d59-9fdd-c5388e5e1aa8" }
            assertEquals("Ninetales used Weather",(used.payload as RawTestimony).data)
            assertTrue(loss.evidenceReferences.containsAll(listOf(used.id,
                "82303c29-d132-4e8a-9355-9b5c207223d5","8a31887b-3bd7-4c94-a7d4-1932de5767a0")))
            for(ordered in listOf(inputs,inputs.reversed(),inputs.shuffled(kotlin.random.Random(12)))) {
                val history=LiveTeamLossHistory(ActivePokemonSide.PLAYER)
                val tracker=TeamFaintTracker(ActivePokemonSide.PLAYER)
                val decisions=ordered.flatMap { r -> val a=live(r,archive.matchId);history.accept(a);tracker.acceptAll(a) }
                assertEquals(loss,history.terminalFaints.single())
                assertEquals(loss.atNanos,decisions.last { it.basis==TerminalChargedFaintResolver.BASIS }.sourceArticle.monotonicTimeNanos)
            }
            assertEquals(loss,ReplayTerminalChargedFaintProjection(archive,archive.articles).resolutionsByArticleId.values.single())
            val model=MatchReplayModel(archive)
            assertFalse(model.sceneAt(loss.atNanos-1).player!!.isFainted)
            val after=model.sceneAt(loss.atNanos+2_000_000_000L)
            assertTrue(after.player!!.isFainted)
            assertEquals(0f,after.playerHp!!.filledFraction!!,0f)
            assertEquals(original.articles.count { it.payload is ArchivedChargedMoveEnergySpent },
                archive.articles.count { it.payload is ArchivedChargedMoveEnergySpent })
            assertEquals(bytes,MatchArchiveSerializer.serialize(archive))
            assertEquals(originalBytes,MatchArchiveSerializer.serialize(original))
        }
    }

    @Test fun `partial charge requires independent direction native Used and the known opposing performer`() {
        val inputs=enriched(first=true).articles.mapNotNull { it.terminalFinishRecord() }
        val used=inputs.single { it.id=="0b803bc5-6c5e-4d59-9fdd-c5388e5e1aa8" }
        val direction=inputs.single { it.id=="8a31887b-3bd7-4c94-a7d4-1932de5767a0" }
        val variations=listOf(
            inputs.filterNot { it.payload==ChargeMoveUsedAnnounced },
            inputs.filterNot { it.payload==AttackIncoming || it.payload==GetReadyWitnessed },
            inputs.map { if(it==used) it.copy(payload=RawTestimony("Gourgeist used Weather")) else it },
            inputs.map { if(it==used) it.copy(payload=RawTestimony("used Weather")) else it },
            inputs.map { if(it==used) it.copy(source="HUD_PRESENTATION") else it },
            inputs.map { if(it==direction) it.copy(payload=GetReadyWitnessed,source="GET_READY_WITNESS") else it },
            inputs.map { if(it==direction) it.copy(at=used.at-20_000_000_001L) else it },
            inputs+direction.copy(id="opposed-direction",payload=GetReadyWitnessed,source="GET_READY_WITNESS"),
            inputs+used.copy(id="contradictory-performer",payload=RawTestimony("Gourgeist used Seed Bomb")),
        )
        variations.forEachIndexed { i,rows ->
            assertTrue("unsupported partial charge $i",TerminalChargedFaintResolver.resolve(rows).isEmpty())
            assertTrue("reordered partial charge $i",TerminalChargedFaintResolver.resolve(rows.reversed()).isEmpty())
        }
    }

    @Test fun `late batch delivery cannot change original frame timing or assign the disappearance to a different side`() {
        val input=enriched().articles.mapNotNull { it.terminalFinishRecord() }
        val expected=TerminalChargedFaintResolver.resolve(input).single()
        val end=input.first { it.source=="GOOD_EFFORT_WITNESS" }.at
        val late=input.map { if(it.payload is TerminalParticleObservations) it.copy(at=end+1) else it }
        assertEquals(expected,TerminalChargedFaintResolver.resolve(late).single())
        val wrongSide=input.map { r -> val p=r.payload as? TerminalParticleObservations
            if(p==null) r else r.copy(payload=p.copy(requestedRecipient=ActivePokemonSide.OPPONENT)) }
        assertTrue(TerminalChargedFaintResolver.resolve(wrongSide).isEmpty())
    }

    @Test fun `switch continued play survivor count and absent corroboration prevent a color-based KO`() {
        val input=enriched().articles.mapNotNull { it.terminalFinishRecord() }
        val loss=TerminalChargedFaintResolver.resolve(input).single()
        val at=loss.atNanos-150_000_000
        val contradictions=listOf(
            TerminalFinishRecord("switch",at,"SPECIES_CHECK_COORDINATOR",null,
                SpeciesCheckMeasured(ActivePokemonSide.PLAYER,1,"OPENED","SWITCH",at,0,1)),
            TerminalFinishRecord("alive",at,"PLAYER_ACTIVE_HP_BAR_WITNESS",.99f,
                ActiveHpBarMeasured(ActivePokemonSide.PLAYER,40,900,230,920,.17f,672,1493,"RED")),
            TerminalFinishRecord("survivors",at,"PLAYER_POKE_BALLS_WITNESS",.99f,PlayerPokeBallCountMeasured(2,3)),
            TerminalFinishRecord("continued",at,"FAST_MOVE_USE_INFERENCE",null,
                FastMoveUseObserved("continued",ActivePokemonSide.PLAYER,ActivePokemonSide.OPPONENT,
                    evidenceKinds=listOf("HP_BORDER_PULSE"))),
        )
        contradictions.forEach { assertTrue(it.id,TerminalChargedFaintResolver.resolve(input+it).isEmpty()) }
        val removals: List<(TerminalFinishRecord)->Boolean> = listOf(
            { it.source=="GOOD_EFFORT_WITNESS" },
            { it.payload is ChargedMoveEnergySpent || it.payload==ChargeMoveUsedAnnounced },
            { it.payload is PlayerPokeBallCountMeasured }, { it.payload is ActivePokemonSpeciesWitnessed })
        removals.forEach { remove -> assertTrue(TerminalChargedFaintResolver.resolve(input.filterNot(remove)).isEmpty()) }
    }

    @Test fun `later Camerupt survival retracts the shared faint without changing original archive entries`() {
        val archive=enriched(); val input=archive.articles.mapNotNull { it.terminalFinishRecord() }
        val loss=TerminalChargedFaintResolver.resolve(input).single()
        val tracked=FaintedCombatants()
        input.forEach { tracked.observe(live(it,archive.matchId)) }
        assertTrue(tracked.contains(ActivePokemonSide.PLAYER,"Camerupt"))
        val at=loss.atNanos+1_000_000_000L
        val identity=TerminalFinishRecord("returned-player",at,"PLAYER_SPECIES_OVERLAY_PIPELINE",.98f,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER,"Camerupt",323))
        val hp=TerminalFinishRecord("returned-player-hp",at+1,"PLAYER_ACTIVE_HP_BAR_WITNESS",.98f,
            ActiveHpBarMeasured(ActivePokemonSide.PLAYER,40,900,230,920,.17f,672,1493,"RED"))
        tracked.observe(live(identity,archive.matchId)); tracked.observe(live(hp,archive.matchId))
        assertFalse(tracked.contains(ActivePokemonSide.PLAYER,"Camerupt"))
        val updated=archive.copy(articles=archive.articles+listOf(identity,hp).map { RealityArticleArchiveMapper.map(live(it,archive.matchId)) })
        assertTrue(ReplayTerminalChargedFaintProjection(updated,updated.articles).resolutionsByArticleId.isEmpty())
    }
}
