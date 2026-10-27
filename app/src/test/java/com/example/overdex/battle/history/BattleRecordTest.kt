package com.example.overdex.battle.history

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.team.OwnedPokemonBattleSnapshot
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.PokemonSize
import org.junit.Assert.*
import org.junit.Test

class BattleRecordTest {
    @Test fun `confirmed roster keeps witnessed uses but does not attribute stale owned card or moves`() {
        val source = opening() + article("confirm", .2, ArchivedPlayerTeamRosterConfirmation(true,
            listOf("Charizard", "Swampert", "Pikachu"))) +
            article("stale-inference", 1.5, ArchivedFastMoveIdentified("PLAYER", "Charizard", "Wing Attack",
                1_000_000_000L, cadenceSampleCount = 0, basis = "CONFIGURED_PLAYER_TEAM")) + hit()
        val record = project(source)
        val use = record.events.single { it.kind == BattleMetricKind.FAST_MOVE }
        assertNull(use.pokemon)
        assertNull(use.moveName)
        assertTrue(record.configuredCards.isEmpty())
        assertFalse("team" in use.articleIds)
        assertEquals(source.size, record.articleCount)
    }

    @Test fun `explicit save after confirmed roster restores the newly selected owned card`() {
        val replacement = card.copy(id = "different-charizard")
        val source = opening() + article("confirm", .2, ArchivedPlayerTeamRosterConfirmation(true,
            listOf("Charizard", "Swampert", "Pikachu"))) +
            article("new-team", .3, configuration(owned = replacement)) + hit()
        val use = project(source).events.single { it.kind == BattleMetricKind.FAST_MOVE }
        assertEquals("different-charizard", use.pokemon?.ownedPokemonId)
        assertEquals("Wing Attack", use.moveName)
        assertTrue("new-team" in use.articleIds)
        assertFalse("team" in use.articleIds)
    }
    private val card = OwnedPokemon(id = "charizard-xl", speciesId = 6, isShadow = true,
        size = PokemonSize.XL, fastMove = "Wing Attack", chargedMove1 = "Blast Burn")
    private fun configuration(slot: Int = 1, owned: OwnedPokemon = card) = ArchivedPlayerTeamSlotConfigured(
        slot, "Charizard", 6, "Wing Attack", listOf("Blast Burn"), OwnedPokemonBattleSnapshot.from(owned))
    private fun article(id: String, seconds: Double, payload: ArchivedTestimonyPayload, source: String = "TEST") =
        ArchivedRealityArticle(id, "match", (seconds * 1000).toLong(), (seconds * 1000).toLong(), source,
            payload, emptyList(), null, null, emptyList(), (seconds * 1_000_000_000).toLong())
    private fun opening() = listOf(
        article("team", 0.0, configuration()),
        article("player", .5, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Charizard", 6)),
        article("opponent", .7, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Swampert", 260)),
        article("start", 1.0, ArchivedMatchStarted))
    private fun hit(id: String = "hit", seconds: Double = 2.0, species: String = "Charizard", kinds: List<String> = listOf("HP_BORDER_PULSE")) =
        article(id, seconds, ArchivedFastMoveUseObserved(id, "PLAYER", "OPPONENT", attackerSpeciesName = species, evidenceKinds = kinds, basis = "FUSED"))
    private fun faint(seconds: Double = 3.0, id: String = "faint") = article(id, seconds,
        ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Swampert", 260, "POKE_BALL_DECREASE"))
    private fun project(rows: List<ArchivedRealityArticle>) = BattleRecordProjector.project(MatchArchive(matchId = "match", articles = rows))

    @Test fun `each use links individual card and original source articles`() {
        val record = project(opening() + hit() + faint() + article("end", 4.0, ArchivedMatchEnded("WIN")))
        val ko = record.events.single { it.kind == BattleMetricKind.KNOCKOUT }
        assertEquals("charizard-xl", ko.pokemon?.ownedPokemonId)
        assertEquals("Swampert", ko.opponentSpecies)
        assertEquals("Wing Attack", ko.moveName)
        assertTrue(ko.articleIds.containsAll(listOf("hit", "faint", "team", "player", "opponent")))
        assertTrue(BattleRecordQuery(speciesName = "Charizard", shadowOnly = true, size = PokemonSize.XL, kind = BattleMetricKind.KNOCKOUT).accepts(ko))
        assertFalse(BattleRecordQuery(size = PokemonSize.XXL).accepts(ko))
        assertEquals(0, record.uncreditedOpponentFaints)
    }

    @Test fun `faint without recent hit does not credit the standing player`() {
        val record = project(opening() + faint())
        assertEquals(1, record.uncreditedOpponentFaints)
        assertTrue(record.events.none { it.kind == BattleMetricKind.KNOCKOUT })
        assertTrue(project(opening() + hit() + faint(9.0)).events.none { it.kind == BattleMetricKind.KNOCKOUT })
    }

    @Test fun `motion evidence and configured movepool do not count as attacks`() {
        val record = project(opening() + hit(kinds = listOf("HP_VERTICAL_MOTION")) + faint())
        assertTrue(record.events.none { it.kind in setOf(BattleMetricKind.FAST_MOVE, BattleMetricKind.CHARGED_MOVE, BattleMetricKind.KNOCKOUT) })
    }

    @Test fun `duplicate witnesses do not double count use or faint`() {
        val duplicate = hit().copy(articleId = "hit-copy")
        val record = project(opening() + hit() + duplicate + faint() + faint(3.2, "faint-copy"))
        assertEquals(1, record.events.count { it.kind == BattleMetricKind.FAST_MOVE })
        assertEquals(1, record.events.count { it.kind == BattleMetricKind.KNOCKOUT })
    }

    @Test fun `unlinked old recordings and two same species cards are never guessed`() {
        val old = opening().map { if (it.articleId == "team") it.copy(payload = configuration().copy(ownedPokemon = null)) else it }
        assertNull(project(old + hit()).events.first().pokemon)
        val duplicates = opening() + article("other-card", .1, configuration(2, card.copy(id = "other-charizard"))) + hit()
        assertTrue(project(duplicates).events.all { it.pokemon == null })
    }

    @Test fun `recorded charged move counts once and supports exact move queries`() {
        val move = ArchivedChargedMoveEnergySpent("PLAYER", "Charizard", "Blast Burn", 50, "ANNOUNCEMENT")
        val record = project(opening() + article("charge", 2.0, move) + article("charge-copy", 2.3, move) +
            article("other-use", 12.0, move))
        val uses = record.events.filter { BattleRecordQuery(moveName = "blast burn", kind = BattleMetricKind.CHARGED_MOVE).accepts(it) }
        assertEquals(2, uses.size)
    }

    @Test fun `match boundaries exclude menus and post result measurements`() {
        val record = project(opening() + hit("early", .6) + article("end", 2.0, ArchivedMatchEnded("WIN")) + hit("late", 3.0))
        assertTrue(record.events.none { it.kind == BattleMetricKind.FAST_MOVE })
        assertTrue(project(opening().filterNot { it.payload is ArchivedMatchStarted } + hit()).events.isEmpty())
    }

    @Test fun `switch and target change break knockout association`() {
        val replacement = article("replacement", 2.5, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134))
        assertEquals(1, project(opening() + hit() + replacement + faint()).uncreditedOpponentFaints)
        val playerSwitch = article("switch", 2.5, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Pikachu", 25))
        assertEquals(1, project(opening() + hit() + playerSwitch + faint()).uncreditedOpponentFaints)
    }

    @Test fun `player faint is separate from opponent knockout and survives later card edits`() {
        val rows = opening() + article("ours-fainted", 3.0, ArchivedActivePokemonFaintedWitnessed("PLAYER", "Charizard", 6, "POKE_BALL_DECREASE"))
        val archive = MatchArchive(matchId = "match", articles = rows)
        val restored = MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive))
        val faint = project(restored.articles).events.single { it.kind == BattleMetricKind.FAINT }
        val changedCard = card.copy(isShadow = false, size = PokemonSize.AVERAGE, chargedMove1 = "Dragon Claw")
        assertNotEquals(changedCard.isShadow, faint.pokemon?.isShadow)
        assertEquals(PokemonSize.XL, faint.pokemon?.size)
    }
}
