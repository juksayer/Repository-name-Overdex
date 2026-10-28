package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayChargeTransitionsTest {
    @Test fun `stable measured rims and repeated loss refine either explicit phase cue with provenance`() {
        for ((cue, side) in listOf(ArchivedGetReadyWitnessed to "PLAYER", ArchivedAttackIncoming to "OPPONENT")) {
            val records = baseline() + lost(5.0) + lost(5.5) + at("cue", 6.0, cue)
            val transition = transition(records)!!
            assertEquals(side, transition.side)
            assertEquals(nanos(4.5), transition.lastVisibleAtNanos)
            assertEquals(nanos(5.0), transition.firstUnavailableAtNanos)
            assertEquals(nanos(5.5), transition.confirmedUnavailableAtNanos)
            assertEquals(nanos(6.0), transition.cueAtNanos)
            assertEquals(100, transition.geometry.cropLeft)
            assertEquals(10, transition.sourceArticleIds.size) // eight measurements, cue, availability
        }
    }

    @Test fun `continuous unknown rims cannot manufacture a phase transition`() {
        val records = setup() + lost(4.0) + lost(4.5) + lost(5.0) + lost(5.5) + ready()
        assertNull(transition(records))
        assertNull(transition(baseline() + lost(5.0) + lost(5.5)))
    }

    @Test fun `one loss or controls returning before the cue is insufficient`() {
        assertNull(transition(baseline() + lost(5.0) + ready(5.5)))
        assertNull(transition(baseline() + lost(5.0) + lost(5.5) + seen(5.8) + ready()))
        assertNull(transition(baseline() + lost(5.0) + seen(5.5) + lost(5.8) + ready()))
    }

    @Test fun `missing frame slot and unsynchronized slot samples do not establish a pair`() {
        assertNull(transition(baseline() + lost(5.0).take(1) + lost(5.5) + ready()))
        val split = lost(5.0).mapIndexed { index, article ->
            article.copy(monotonicTimeNanos = nanos(5.0 + index * .1))
        }
        assertNull(transition(baseline() + split + lost(5.5) + ready()))
    }

    @Test fun `capture disablement or missing availability prevents bridging an unseen interval`() {
        val samples = baseline() + lost(5.0) + lost(5.5) + ready()
        assertNull(transition(samples.filterNot { it.payload is ArchivedWitnessOperating }))
        assertNull(transition(samples + at("off", 5.2, ArchivedWitnessOperating(false), SOURCE) +
            at("on-again", 5.3, ArchivedWitnessOperating(true), SOURCE)))
        assertNull(transition(samples.map { if (it.articleId == "operating")
            it.copy(payload = ArchivedWitnessOperating(false)) else it }))
    }

    @Test fun `long sampling gaps stale losses and unstable crop or rim geometry are rejected`() {
        assertNull(transition(setup() + seen(3.0) + seen(4.5) + lost(5.0) + lost(5.5) + ready()))
        assertNull(transition(baseline() + lost(5.0) + lost(5.5) + ready(7.0)))
        assertNull(transition(baseline() + lost(5.0, top = 1802) + lost(5.5, top = 1802) + ready()))
        val moved = seen(4.5).map { article -> article.copy(payload =
            (article.payload as ArchivedPlayerChargeButtonFillHeightMeasured).let { it.copy(centerY = it.centerY!! + 20) }) }
        assertNull(transition(setup() + seen(4.0) + moved + lost(5.0) + lost(5.5) + ready()))
    }

    @Test fun `capture gap interval invalidates a transition even when recovery is reported after the cue`() {
        val records = baseline() + lost(5.0) + lost(5.5) + ready()
        assertNull(transition(records + at("gap", 6.2, ArchivedVisualCaptureGapObserved(nanos(1.6)))))
        assertNull(transition(records + at("gap", 5.2, ArchivedVisualCaptureGapObserved(nanos(.4)))))
        assertNotNull(transition(records + at("old-gap", 3.5, ArchivedVisualCaptureGapObserved(nanos(1.0)))))
        assertNotNull(transition(records + at("later-gap", 7.0, ArchivedVisualCaptureGapObserved(nanos(.5)))))
    }

    @Test fun `faint switch restart and match end invalidate the preceding control baseline`() {
        val interruptions = listOf<ArchivedTestimonyPayload>(
            ArchivedActivePokemonFaintedWitnessed("PLAYER", "Scrafty", 560, "TEST"),
            ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Heatmor", 631),
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Flareon", 136),
            ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser,
            ArchivedObservationSessionStoppedByUser, ArchivedMatchEnded("WIN"),
        )
        interruptions.forEach { interruption ->
            assertNull(interruption.toString(), transition(baseline() + lost(5.0) + lost(5.5) + ready() +
                at("interrupted", 4.75, interruption)))
        }
        assertNotNull(transition(baseline() + lost(5.0) + lost(5.5) + ready() +
            at("same-name", 4.75, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Scrafty", 560))))
    }

    @Test fun `a known single equipped charge needs only its visible control but an unread second control is not a loadout`() {
        val singleSamples = setup() + seen(4.0, single = true) + seen(4.5, single = true) + lost(5.0) + lost(5.5) + ready()
        assertNull(transition(singleSamples))
        val configured = at("configured", 1.0,
            ArchivedPlayerTeamSlotConfigured(1, "Scrafty", 560, "Snarl", listOf("Foul Play")))
        val transition = transition(singleSamples + configured)!!
        assertTrue("configured" in transition.sourceArticleIds)
        assertNull(transition(singleSamples + configured.copy(payload =
            ArchivedPlayerTeamSlotConfigured(1, "Scrafty", 560, "Snarl", listOf("Foul Play", "Power-Up Punch")))))
        assertNull(transition(singleSamples + configured +
            at("roster-confirmed", 2.0, ArchivedPlayerTeamRosterConfirmation(true, listOf("Scrafty")))))
    }

    @Test fun `same raw species cannot bridge a resolved form or owned team slot change`() {
        val records = baseline() + lost(5.0) + lost(5.5) + ready()
        val cue = records.single { it.articleId == "cue" }
        val changes = listOf(
            ReplayCombatant("Raichu (Alolan)", 26, 0, playerTeamSlot = 1) to
                ReplayCombatant("Raichu", 26, nanos(4.75), playerTeamSlot = 2),
            ReplayCombatant("Raichu", 26, 0, playerTeamSlot = 1) to
                ReplayCombatant("Raichu", 26, nanos(4.75), playerTeamSlot = 2),
        )
        changes.forEach { (before, after) ->
            val transitions = ReplayChargeTransitions(records) { if (it < nanos(4.75)) before else after }
            assertNull(transitions.forCue(cue))
        }
    }

    @Test fun `late form resolution blocks its original appearance interval rather than publication time`() {
        val records = (baseline() + lost(5.0) + lost(5.5) + ready()).map { article ->
            if (article.articleId == "player") article.copy(payload =
                ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Raichu", 26)) else article
        }
        fun resolved(id: String, published: Double, appearance: Double, name: String, slot: Int) =
            at(id, published, ArchivedPlayerActiveIdentityResolved("Raichu", name, 26, slot, nanos(appearance), "TEST"))
        val old = resolved("first-form", .3, .2, "Raichu (Alolan)", 1)
        val switched = resolved("late-second-form", 8.0, 4.75, "Raichu", 2)
        assertNull(transition(records + old + switched))
        val model = MatchReplayModel(MatchArchive(matchId = "transition", articles = records + old + switched))
        assertTrue(model.sceneAt(nanos(5.25)).chargedMoveActions.isEmpty())
        assertNull(model.sceneAt(nanos(6.25)).chargedMoveActions.single().transition)
        // Learning more about the same earlier appearance must not create a
        // switch at the time the delayed conclusion happened to be published.
        assertNotNull(transition(records + resolved("late-first-form", 5.25, .2, "Raichu (Alolan)", 1)))
    }

    @Test fun `fresh contrary cues and known performed actions cannot be backdated over`() {
        val records = baseline() + lost(5.0) + lost(5.5) + ready()
        assertNull(transition(records + at("incoming", 5.8, ArchivedAttackIncoming)))
        assertNull(transition(records + at("incoming", 6.0, ArchivedAttackIncoming)))
        val use = ArchivedFastMoveUseObserved("hit", "PLAYER", "OPPONENT", attackerSpeciesName = "Scrafty",
            evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST")
        assertNull(transition(records + at("hit", 5.2, use)))
        assertNotNull(transition(records + at("prior-hit", 4.75, use)))
    }

    @Test fun `late publication order does not change source timing and same-frame raw plus typed cue is one boundary`() {
        val records = baseline() + lost(5.0) + lost(5.5) + ready() +
            at("raw-ready", 6.0, ArchivedRawText("Get Ready!"), "ANNOUNCEMENT_WITNESS")
        val original = transition(records)
        assertNotNull(original)
        assertEquals(original, transition(records.reversed()))
        assertEquals(nanos(5.0), original!!.presentationAtNanos)
    }

    @Test fun `replay uses earlier corroborated preparation and excludes raw fast noise without changing archive`() {
        for ((cue, side) in listOf(ArchivedGetReadyWitnessed to "PLAYER", ArchivedAttackIncoming to "OPPONENT")) {
            val records = baseline() + lost(5.0) + lost(5.5) + at("cue", 6.0, cue) +
                at("noise", 5.15, ArchivedActiveHpBarBorderPulseObserved("OPPONENT", .5f, 3)) +
                at("spent", 10.0, ArchivedChargedMoveEnergySpent(side,
                    if (side == "PLAYER") "Scrafty" else "Combusken", "Foul Play", 40, "TEST"))
            val archive = MatchArchive(matchId = "transition", articles = records)
            val before = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            assertTrue(model.sceneAt(nanos(4.9)).chargedMoveActions.isEmpty())
            val action = model.sceneAt(nanos(5.25)).chargedMoveActions.single()
            assertEquals(side, action.side)
            assertEquals(ReplayChargePhase.CHARGING, action.phase)
            assertEquals(nanos(.25), action.elapsedNanos)
            assertEquals(nanos(5.0), action.transition!!.firstUnavailableAtNanos)
            assertTrue(action.transition!!.basis in action.evidenceKinds)
            assertTrue(model.sceneAt(nanos(5.3)).fastMoveActions.isEmpty())
            assertEquals(action, model.sceneAt(nanos(5.25)).chargedMoveActions.single())
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `recorded Moondrop unknown controls do not move any phase cue earlier`() {
        // Unchanged selected source articles from c0f775ec-947e-473b-9bda-57850a5989fa.
        // The 250 measurements include a few accidental circles, not a stable
        // positive baseline. Their constant unknown state cannot establish QTE.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/moondrop-charge-controls-20261028.json")).readText())
        val measurements = archive.articles.mapNotNull { it.payload as? ArchivedPlayerChargeButtonFillHeightMeasured }
        assertEquals(250, measurements.size)
        assertEquals(239, measurements.count { it.status == "UNKNOWN_RIM" })
        assertEquals(11, measurements.count { it.status == "UNKNOWN_SEPARATOR" })
        val before = MatchArchiveSerializer.serialize(archive)
        val transitions = ReplayChargeTransitions(archive.articles) { ReplayCombatant("Mawile", 303, 0) }
        val model = MatchReplayModel(archive)
        val cues = archive.articles.filter { isReplayGetReadyCue(it) || isReplayAttackIncomingCue(it) }
        assertEquals(3, cues.size)
        cues.forEach { cue ->
            assertNull(transitions.forCue(cue))
            assertTrue(model.sceneAt(cue.monotonicTimeNanos!!).chargedMoveActions.all { it.transition == null })
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `earlier preparation preserves the original cue to announcement association window`() {
        val archive = MatchArchive(matchId = "transition", articles = baseline() + lost(5.0) + lost(5.5) + ready() +
            at("spent-late", 15.5, ArchivedChargedMoveEnergySpent("PLAYER", "Scrafty", "Foul Play", 40, "TEST")))
        val model = MatchReplayModel(archive)
        val preparing = model.sceneAt(nanos(5.25)).chargedMoveActions.single()
        assertEquals("Foul Play", preparing.moveName)
        assertEquals(nanos(5.0), preparing.transition!!.firstUnavailableAtNanos)
        val striking = model.sceneAt(nanos(15.6)).chargedMoveActions.single()
        assertEquals(ReplayChargePhase.STRIKING, striking.phase)
        assertEquals(preparing.transition, striking.transition)
    }

    private fun transition(records: List<ArchivedRealityArticle>): ReplayChargeTransition? {
        val cue = records.firstOrNull { it.articleId == "cue" } ?: return null
        return ReplayChargeTransitions(records) { ReplayCombatant("Scrafty", 560, 0) }.forCue(cue)
    }
    private fun setup() = listOf(
        at("operating", 0.0, ArchivedWitnessOperating(true), SOURCE),
        at("start", .1, ArchivedMatchStarted),
        at("player", .2, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Scrafty", 560)),
        at("opponent", .2, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Combusken", 256)),
    )
    private fun baseline() = setup() + seen(4.0) + seen(4.5)
    private fun ready(time: Double = 6.0) = at("cue", time, ArchivedGetReadyWitnessed)
    private fun seen(time: Double, single: Boolean = false) = frame(time, visible = true, single = single)
    private fun lost(time: Double, top: Int = 1800) = frame(time, visible = false, top = top)
    private fun frame(time: Double, visible: Boolean, single: Boolean = false, top: Int = 1800) = (1..2).map { slot ->
        val present = visible && (!single || slot == 1)
        at("rim-$time-$slot", time, ArchivedPlayerChargeButtonFillHeightMeasured(
            slot, if (present) "UNKNOWN_SEPARATOR" else "UNKNOWN_RIM",
            if (present) 150 + (slot - 1) * 300 else null, if (present) 130 else null,
            if (present) 80 else null, null, null, 6f,
            100, top, 600, 260, 1080, 2400), SOURCE)
    }
    private fun at(id: String, time: Double, payload: ArchivedTestimonyPayload, source: String = "TEST") =
        ArchivedRealityArticle(id, "transition", (time * 1000).toLong(), 99_000, source, payload,
            emptyList(), null, null, emptyList(), nanos(time))
    private fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
    companion object { private const val SOURCE = "PLAYER_CHARGE_BUTTON_FILL_HEIGHT_WITNESS" }
}
