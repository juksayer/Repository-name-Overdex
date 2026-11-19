package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayEventMarkersTest {
    @Test fun `overlapping clips can support the same cry but a different reference or entrance cannot`() {
        val onset = 90_542_000_000L
        val fit = ReplayAudioFragment("whole", onset, "clip1", "vaporeon", 0, 0, 200_000_000, .99f)
        val whole = ReplayRecoveredCry(134, onset, 1_000_000_000, listOf(fit))
        val fragment = fit.copy(articleId = "partial", audioSha256 = "clip2", captureAtNanos = onset + 4_000_000L)
        val partial = ReplayPartialCryCandidate(134, fragment.estimatedOnsetNanos, listOf(fragment), .1f)
        assertTrue(ReplayEventMarkers.sameCryOccurrence(partial, whole))
        assertFalse(ReplayEventMarkers.sameCryOccurrence(partial.copy(estimatedOnsetNanos = onset - 20_000_000_000L), whole))
        assertFalse(ReplayEventMarkers.sameCryOccurrence(partial.copy(fragments = listOf(fragment.copy(referenceSha256 = "other"))), whole))
    }

    @Test fun `LCD combines only corroboration of a visible faint and keeps independent cries reachable`() {
        val faint = ReplayEventMarker("ko", ReplayEventMarkerKind.FAINT, 100, "OPPONENT", "Faint",
            sourceArticleIds = listOf("whole"))
        val fragment = ReplayEventMarker("partial", ReplayEventMarkerKind.POSSIBLE_CRY, 104, "OPPONENT", "Cry",
            sourceArticleIds = listOf("partial-audio"), corroboratedFaintId = faint.id)
        val entrance = fragment.copy(id = "entrance", atNanos = 10, corroboratedFaintId = null)
        val other = fragment.copy(id = "other", atNanos = 105, corroboratedFaintId = null)
        val all = listOf(entrance, faint, fragment, other)
        val lcd = ReplayEventMarkers.lcdMarkers(all)
        assertEquals(listOf("entrance", "ko", "other"), lcd.map { it.id })
        assertEquals(listOf("whole", "partial-audio"), lcd[1].sourceArticleIds)
        assertEquals("ko", lcd[ReplayEventMarkerNavigation.currentIndex(lcd, 104, null)].id)
        assertEquals(listOf(fragment, other), ReplayEventMarkers.lcdMarkers(all.takeLast(2)))
        assertEquals(listOf("whole"), faint.sourceArticleIds)
    }

    @Test fun `cry requires exactly one matching active side and both identities known`() {
        assertEquals("OPPONENT", ReplayEventMarkers.crySide(134, 776, 134))
        assertEquals("PLAYER", ReplayEventMarkers.crySide(134, 134, 776))
        assertNull(ReplayEventMarkers.crySide(134, 134, 134))
        assertNull(ReplayEventMarkers.crySide(134, null, 134))
        assertNull(ReplayEventMarkers.crySide(134, 776, 6))
    }

    @Test fun `initial and repeated identities do not invent switches but later entries are reachable`() {
        val replay = model(
            species("lead", "PLAYER", "Raichu (Alolan)", 0),
            species("other", "OPPONENT", "Swampert", 0),
            event("go", ArchivedMatchStarted, 3),
            species("read-again", "PLAYER", "Raichu (Alolan)", 5),
            species("changed", "PLAYER", "Raichu", 8),
            species("returned", "PLAYER", "Raichu (Alolan)", 12),
        )
        val markers = replay.eventMarkers
        assertEquals(3, markers.size)
        assertEquals(ReplayEventMarkerKind.MATCH_START, markers.first().kind)
        assertEquals(3 * SECOND, markers.first().atNanos)
        assertEquals("Match start", markers.first().label)
        val changes = markers.filter { it.kind == ReplayEventMarkerKind.ACTIVE_CHANGE }
        assertEquals(listOf(8 * SECOND, 12 * SECOND), changes.map { it.atNanos })
        assertEquals(listOf("changed", "returned"), changes.flatMap { it.sourceArticleIds })
        assertTrue(changes.all { it.side == "PLAYER" && it.label.startsWith("Active change") })
        assertEquals(markers, replay.eventMarkers)
    }

    @Test fun `fitted faint marker retains source time and replacement is a send in`() {
        val archive = MatchArchive(matchId = "markers", articles = listOf(
            species("lead", "OPPONENT", "Swampert", 1),
            species("next", "OPPONENT", "Corviknight", 10),
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Swampert", 260, "RECORDED"), 11),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val replay = MatchReplayModel(archive)
        val faint = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        assertEquals(10 * SECOND - ReplayKnockoutTrack.MIN_VISIBLE_KO_NANOS, faint.atNanos)
        assertEquals(11 * SECOND, (faint.faintTiming as ReplayFaintTiming.Window).by.atNanos)
        assertEquals(11 * SECOND, faint.sourceAtNanos)
        assertTrue(faint.sourceArticleIds.contains("ko"))
        assertEquals("OPP", faint.sideLabel)
        assertEquals("Send in → Corviknight", replay.eventMarkers.single {
            it.kind == ReplayEventMarkerKind.ACTIVE_CHANGE
        }.label)
        replay.sceneAt(12 * SECOND)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `result defeat and ball loss do not invent a faint marker`() {
        val replay = model(species("o", "OPPONENT", "Swampert", 0),
            event("balls", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3), 10),
            event("end", ArchivedMatchEnded("WIN"), 12))
        assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
    }

    @Test fun `only completed charge has one marker and preserves all cue article IDs`() {
        val replay = model(
            species("p", "PLAYER", "Raichu", 1),
            event("ready", ArchivedGetReadyWitnessed, 4),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Raichu", "Thunder Punch", 35, "ANNOUNCEMENT"), 10),
            event("announced", ArchivedChargeMoveUsedAnnounced, 10),
            event("uncompleted", ArchivedAttackIncoming, 14),
        )
        val charge = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE }
        assertEquals(10 * SECOND, charge.atNanos)
        assertEquals(10 * SECOND, charge.sourceAtNanos)
        assertEquals("PLAYER", charge.side)
        assertEquals("Charged move · Thunder Punch", charge.label)
        assertTrue(charge.sourceArticleIds.containsAll(listOf("ready", "charge")))
    }

    @Test fun `unknown charge side and move stay unknown`() {
        val marker = model(event("used", ArchivedChargeMoveUsedAnnounced, 10))
            .eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE }
        assertNull(marker.side)
        assertEquals("SIDE ?", marker.sideLabel)
        assertTrue(marker.label.contains("name unknown"))
    }

    @Test fun `shield use needs a baseline and resource increases cannot spend another shield`() {
        val replay = model(
            event("p-first", ArchivedPlayerShieldCountMeasured(1), 1),
            event("p-noisy-increase", ArchivedPlayerShieldCountMeasured(2), 2),
            event("p-repeat", ArchivedPlayerShieldCountMeasured(1), 3),
            event("p-used", ArchivedPlayerShieldCountMeasured(0), 4),
            event("o-first", ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 2, 2), 1),
            event("o-drop", ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2), 6),
            event("o-repeat", ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2), 7),
        )
        val shields = replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.SHIELD }
        assertEquals(listOf(4 * SECOND, 6 * SECOND), shields.map { it.atNanos })
        assertEquals(listOf("PLAYER", "OPPONENT"), shields.map { it.side })
        assertEquals(listOf("p-repeat", "p-used"), shields.first().sourceArticleIds)
        assertEquals("2 shields used · exact instants unknown", shields.last().label)
    }

    @Test fun `stat markers follow original sound and latest attribution including revoked side`() {
        val cue = event("sound", ArchivedStatChangeSoundMeasured("ATTACK_DOWN", "sha", .99f, .7f, SECOND), 5)
        val attributed = event("first-attribution", ArchivedStatChangeObserved("OPPONENT", "ATTACK", -1,
            1, "Lunge", "sound", "AUDIO_AND_MOVE"), 5).copy(recordedAt = 10)
        val revoked = attributed.copy(articleId = "correction", recordedAt = 30,
            payload = ArchivedStatChangeObserved(null, "ATTACK", -1, null, null, "sound", "AMBIGUOUS"))
        val replay = model(cue, attributed, revoked,
            event("buff", ArchivedStatChangeSoundMeasured("DEFENSE_UP", "sha", .99f, .7f, SECOND), 8))
        val changes = replay.eventMarkers.filter { it.kind in setOf(ReplayEventMarkerKind.BUFF, ReplayEventMarkerKind.DEBUFF) }
        assertEquals(2, changes.size)
        assertEquals(5 * SECOND, changes.first().atNanos)
        assertEquals("Attack ↓", changes.first().label)
        assertNull(changes.first().side)
        assertEquals(listOf("sound", "correction"), changes.first().sourceArticleIds)
        assertEquals(ReplayEventMarkerKind.BUFF, changes.last().kind)
    }

    @Test fun `simultaneous events remain individually navigable in both directions`() {
        val markers = listOf(marker("go", 3), marker("charge", 6), marker("shield", 6), marker("faint", 10))
        assertEquals(0, ReplayEventMarkerNavigation.nextIndex(markers, 0, null))
        assertEquals(1, ReplayEventMarkerNavigation.nextIndex(markers, 3 * SECOND, "go"))
        assertEquals(2, ReplayEventMarkerNavigation.nextIndex(markers, 6 * SECOND, "charge"))
        assertEquals(1, ReplayEventMarkerNavigation.previousIndex(markers, 6 * SECOND, "shield"))
        assertEquals(3, ReplayEventMarkerNavigation.nextIndex(markers, 8 * SECOND, "charge"))
        assertEquals(2, ReplayEventMarkerNavigation.previousIndex(markers, 8 * SECOND, "charge"))
        assertEquals(1, ReplayEventMarkerNavigation.previousIndex(markers, 6 * SECOND, null))
        assertNull(ReplayEventMarkerNavigation.previousIndex(markers, 3 * SECOND, "go"))
        assertNull(ReplayEventMarkerNavigation.nextIndex(markers, 10 * SECOND, "faint"))
        assertNull(ReplayEventMarkerNavigation.nextIndex(emptyList(), 0, null))
    }

    @Test fun `presentation only countdown is not promoted to a purple GO marker`() {
        val replay = model(species("p", "PLAYER", "Raichu", 5),
            event("incoming", ArchivedAttackIncoming, 15))
        assertEquals(ReplayCountdownTiming.PRESENTATION_ONLY, replay.countdown?.timingBasis)
        assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.MATCH_START })
    }

    @Test fun `only a witnessed GO at the boundary qualifies the match start label`() {
        val replay = model(
            event("glyph", ArchivedCountdownGlyphWitnessed("GO", .99f, 0, basis = "OBSERVED"), 3),
            event("start", ArchivedMatchStarted, 3),
        )
        val marker = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.MATCH_START }
        assertEquals("Match start · GO", marker.label)
        assertEquals(listOf("start", "glyph"), marker.sourceArticleIds)
        val inferred = model(event("start", ArchivedMatchStarted, 3).copy(
            sourceId = "COUNTDOWN_AND_COMBAT_START_INFERENCE"))
        assertEquals("Match start", inferred.eventMarkers.single { it.kind == ReplayEventMarkerKind.MATCH_START }.label)
    }

    private fun marker(id: String, seconds: Long) = ReplayEventMarker(id, ReplayEventMarkerKind.SHIELD,
        seconds * SECOND, "PLAYER", id)
    private fun model(vararg events: ArchivedRealityArticle) = MatchReplayModel(MatchArchive(
        matchId = "markers", articles = events.toList()))
    private fun species(id: String, side: String, name: String, seconds: Long) =
        event(id, ArchivedActivePokemonSpeciesWitnessed(side, name, null), seconds)
    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "markers", seconds * 1_000, seconds * 1_000, "TEST", payload, emptyList(), null, null,
        emptyList(), seconds * SECOND)

    companion object { private const val SECOND = 1_000_000_000L }
}
