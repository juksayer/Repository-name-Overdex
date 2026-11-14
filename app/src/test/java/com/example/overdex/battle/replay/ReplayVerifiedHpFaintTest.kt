package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.VerifiedHpFaintResolver
import com.example.overdex.battle.observation.*
import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

class ReplayVerifiedHpFaintTest {
    @Test fun `an unsupported archived terminal inference cannot suppress independent empty HP proof`() {
        val base=recorded("OPPONENT")
        val input=base.copy(articles=base.articles+event("old-terminal-claim",EMPTY_AT,
            ArchivedActivePokemonFaintedWitnessed("OPPONENT","Tinkaton",959,
                com.example.overdex.battle.inference.TerminalChargedFaintResolver.BASIS)))
        val model=MatchReplayModel(input)
        assertEquals(VerifiedHpFaintResolver.BASIS,model.sceneAt(EMPTY_AT+S).opponent!!.knockoutBasis)
        assertEquals(1,model.eventMarkers.count { it.kind==ReplayEventMarkerKind.FAINT })
        assertTrue(model.timedArticles.any { it.articleId=="old-terminal-claim" })
    }

    @Test fun `real Tinkaton red and empty pixels produce a replay KO without a final ball count`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = recorded(side)
            val original = MatchArchiveSerializer.serialize(archive)
            for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
                val model = MatchReplayModel(input)
                val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
                assertEquals(EMPTY_AT, marker.sourceAtNanos)
                assertTrue(marker.sourceArticleIds.containsAll(listOf("identity", "red", "empty")))
                val before = if (side == "PLAYER") model.sceneAt(RED_AT).player!! else model.sceneAt(RED_AT).opponent!!
                val after = if (side == "PLAYER") model.sceneAt(EMPTY_AT + S).player!! else model.sceneAt(EMPTY_AT + S).opponent!!
                assertFalse(before.isFainted)
                assertTrue(after.isFainted)
                assertEquals(VerifiedHpFaintResolver.BASIS, after.knockoutBasis)
                assertEquals(EMPTY_AT, after.faintedAtNanos)
                val beforeHp = if (side == "PLAYER") model.sceneAt(RED_AT).playerHp!! else model.sceneAt(RED_AT).opponentHp!!
                val afterHp = if (side == "PLAYER") model.sceneAt(EMPTY_AT + S).playerHp!! else model.sceneAt(EMPTY_AT + S).opponentHp!!
                assertEquals(.153f, beforeHp.filledFraction!!, .015f)
                assertEquals(0f, afterHp.filledFraction!!, 0f)
                assertTrue(model.timedArticles.none { it.payload is ArchivedActivePokemonFaintedWitnessed })
                assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.CHARGED_MOVE })
            }
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `later survival removes HP faint and its marker without modifying the record`() {
        val base = recorded("OPPONENT")
        val input = base.copy(articles = base.articles + listOf(
            event("bad-zero-tick", EMPTY_AT, ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .15f, 0f, .15f)),
            base.articles.single { it.articleId == "empty" }.copy(articleId = "repeated-empty", monotonicTimeNanos = EMPTY_AT + 50_000_000),
            event("other", 62 * S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703), "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
            event("return", 65 * S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Tinkaton", 959), "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
            event("alive", 66 * S, ArchivedActiveHpBarMeasured("OPPONENT", 394, 677, 587, 694, .15f, 672, 1493), "OPPONENT_ACTIVE_HP_BAR_WITNESS")))
        for (ordered in listOf(input, input.copy(articles = input.articles.reversed()))) {
            val model = MatchReplayModel(ordered)
            assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
            assertFalse(model.sceneAt(67 * S).opponent!!.isFainted)
            assertTrue("A retracted empty value must not pin the returning actor to zero",
                model.sceneAt(67 * S).opponentHp!!.filledFraction!! > 0f)
            assertEquals(1, model.timedArticles.count { it.articleId == "empty" })
            val correction = ReplayVerifiedHpFaintProjection(ordered, ReplayTeamLossProjection(ordered).articles)
                .contradictedEmptyEvidence.getValue("empty")
            assertTrue(correction.containsAll(listOf("identity", "red", "empty", "return", "alive")))
        }
    }

    @Test fun `count-confirmed KO and repeated zero cannot make a second marker or loss`() {
        val base = recorded("OPPONENT")
        val input = base.copy(articles = base.articles + listOf(
            event("before", 58 * S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3)),
            event("after", 61 * S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 0, 3)),
            base.articles.single { it.articleId == "empty" }.copy(articleId = "empty-again", monotonicTimeNanos = EMPTY_AT + 20_000_000)))
        val model = MatchReplayModel(input)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.FAINT })
        assertTrue(ReplayVerifiedHpFaintProjection(input, ReplayTeamLossProjection(input).articles).resolutionsByArticleId.isEmpty())
        for (ordered in listOf(input, input.copy(articles = input.articles.reversed()))) {
            val replay = MatchReplayModel(ordered)
            val marker = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals(EMPTY_AT, marker.atNanos)
            assertTrue(marker.sourceArticleIds.containsAll(listOf("identity", "red", "empty", "before", "after")))
            assertTrue(replay.sceneAt(EMPTY_AT + 10_000_000L).opponent!!.isFainted)
            assertEquals(1, replay.sceneAt(EMPTY_AT + 10_000_000L).opponentResources.pokeBalls)
            assertEquals(0, replay.sceneAt(61 * S).opponentResources.pokeBalls)
        }
    }

    @Test fun `charge-camera zero numeric zero unknown source and a missing bar do not produce KO`() {
        val base = recorded("OPPONENT")
        val zero = base.articles.single { it.articleId == "empty" }
        val variants = listOf(
            zero.copy(sourceId = "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS"),
            zero.copy(sourceId = "UNVERIFIED_READER"),
            zero.copy(payload = (zero.payload as ArchivedActiveHpBarMeasured).copy(fillColor = null)),
            zero.copy(payload = (zero.payload as ArchivedActiveHpBarMeasured).copy(sourceWidth = null)),
        )
        for (replacement in variants) {
            val input = base.copy(articles = base.articles.map { if (it.articleId == "empty") replacement else it })
            assertTrue(MatchReplayModel(input).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        }
        assertTrue(MatchReplayModel(base.copy(articles = base.articles.filterNot { it.articleId == "empty" }))
            .eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        for (boundary in listOf<ArchivedTestimonyPayload>(ArchivedGetReadyWitnessed, ArchivedAttackIncoming,
            ArchivedChargeMoveUsedAnnounced, ArchivedObservationRestartedByUser,
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703))) {
            assertTrue(MatchReplayModel(base.copy(articles = base.articles + event("boundary", 60 * S,
                boundary, "OPPONENT_SPECIES_OVERLAY_PIPELINE"))).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        }
    }

    @Test fun `conflicting duplicate custody IDs and another match cannot contribute HP proof`() {
        val base = recorded("OPPONENT")
        val zero = base.articles.single { it.articleId == "empty" }
        val conflict = zero.copy(payload = (zero.payload as ArchivedActiveHpBarMeasured).copy(filledFraction = .1f))
        assertTrue(MatchReplayModel(base.copy(articles = base.articles + conflict)).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        assertTrue(MatchReplayModel(base.copy(articles = base.articles.map {
            if (it.articleId == "empty") it.copy(matchId = "another") else it
        })).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
    }

    @Test fun `source capture time prevents the replacement from inheriting a delayed empty frame`() {
        val base = recorded("OPPONENT")
        val replacement = event("next", 61 * S,
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703), "OPPONENT_SPECIES_OVERLAY_PIPELINE")
        val input = base.copy(articles = listOf(replacement) + base.articles.reversed())
        val model = MatchReplayModel(input)
        assertTrue(model.sceneAt(EMPTY_AT + 100_000_000).opponent!!.isFainted)
        assertEquals("Tinkaton", model.sceneAt(EMPTY_AT + 100_000_000).opponent!!.speciesName)
        assertFalse(model.sceneAt(62 * S).opponent!!.isFainted)
        assertEquals("Carbink", model.sceneAt(62 * S).opponent!!.speciesName)
    }

    @Test fun `actual living Carbink recall remains conscious after its red bar disappears`() {
        var anchor: ActiveHpBarMeasurement? = null
        val articles = mutableListOf(event("identity", 179 * S,
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703), "OPPONENT_SPECIES_OVERLAY_PIPELINE"))
        for ((name, at) in listOf("shrinking" to 181_297_733_333L, "last-readable" to 181_368_433_333L,
            "sprite-gone" to 181_421_222_222L, "replacement-label" to 181_654_900_000L)) {
            val crop = ImageIO.read(requireNotNull(javaClass.getResource(
                "/battle/recorded-evidence/2026-11-11-carbink-living-recall/$name.png")))
            if (anchor == null) anchor = ActiveHpBarMeasurer.measureAll(crop.width, crop.height, 816 * 2 / 7,
                crop::getRGB).maxBy { it.confidence }.let {
                    it.copy(left = it.left + 370, right = it.right + 370, top = it.top + 700, bottom = it.bottom + 700)
                }
            val sample = TrackedTerminalHpBarMeasurer.measure(816, 1808, requireNotNull(anchor)) { x, y ->
                assertTrue(x in 370 until 685 && y in 700 until 855)
                crop.getRGB(x - 370, y - 700)
            } ?: continue
            assertNotEquals(HpBarFillColor.EMPTY, sample.fillColor)
            val bar = sample.measurement
            anchor = bar
            articles += event(name, at, ArchivedActiveHpBarMeasured("OPPONENT", bar.left, bar.top, bar.right,
                bar.bottom, bar.filledFraction, 816, 1808, sample.fillColor.name), "OPPONENT_ACTIVE_HP_BAR_WITNESS")
                .copy(confidence = bar.confidence)
        }
        val model = MatchReplayModel(MatchArchive(matchId = MATCH, articles = articles))
        assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        assertFalse(model.sceneAt(182 * S).opponent!!.isFainted)
        assertEquals(.174f, model.sceneAt(182 * S).opponentHp!!.filledFraction!!, .012f)
    }

    @Test fun `HP proof does not create a count move or result and excludes samples outside the battle`() {
        val base = recorded("OPPONENT")
        val projection = ReplayVerifiedHpFaintProjection(base, ReplayTeamLossProjection(base).articles)
        assertTrue(projection.articles.none { it.payload is ArchivedMatchEnded ||
            it.payload is ArchivedPlayerPokeBallCountMeasured || it.payload is ArchivedOpponentBattleResourceCountMeasured ||
            it.payload is ArchivedFastMoveUseObserved })
        for (bounds in listOf(
            listOf(event("go", 61 * S, ArchivedMatchStarted)),
            listOf(event("early-end", 60 * S, ArchivedMatchEnded("WIN"))),
        )) {
            val input = base.copy(articles = base.articles.filterNot { it.articleId == "go" } + bounds)
            assertTrue(MatchReplayModel(input).eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        }
    }

    private fun recorded(side: String): MatchArchive {
        val anchor = ActiveHpBarMeasurement(394, 677, 587, 694, .15343915f, .95f)
        fun pixels(name: String, at: Long): ArchivedRealityArticle {
            val crop = ImageIO.read(requireNotNull(javaClass.getResource("/battle/hp/2026-11-11-tinkaton-terminal/$name.png")))
            val sample = requireNotNull(TrackedTerminalHpBarMeasurer.measure(672, 1493, anchor) { x, y ->
                assertTrue(x in 380 until 610 && y in 650 until 730)
                crop.getRGB(x - 380, y - 650)
            })
            val bar = sample.measurement
            return event(name, at, ArchivedActiveHpBarMeasured(side, bar.left, bar.top, bar.right, bar.bottom,
                bar.filledFraction, 672, 1493, sample.fillColor.name), "${side}_ACTIVE_HP_BAR_WITNESS")
                .copy(confidence = bar.confidence)
        }
        // These articles are a test adapter over actual retained video pixels,
        // not claims that the old live archive contained these measurements.
        return MatchArchive(matchId = MATCH, articles = listOf(
            event("go", 1 * S, ArchivedMatchStarted),
            event("identity", 57 * S, ArchivedActivePokemonSpeciesWitnessed(side, "Tinkaton", 959), "${side}_SPECIES_OVERLAY_PIPELINE"),
            pixels("red", RED_AT), pixels("empty", EMPTY_AT)))
    }

    private fun event(id: String, at: Long, payload: ArchivedTestimonyPayload, source: String = "TEST") =
        ArchivedRealityArticle(id, MATCH, at / 1_000_000, at / 1_000_000 + 2000, source, payload,
            emptyList(), .95f, null, emptyList(), at)

    private companion object {
        const val MATCH = "recorded-tinkaton-pixel-reprocess"
        const val S = 1_000_000_000L
        const val RED_AT = 59_005_411_111L
        const val EMPTY_AT = 60_504_888_889L
    }
}
