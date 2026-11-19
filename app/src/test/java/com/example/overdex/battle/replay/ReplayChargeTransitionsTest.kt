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

    @Test fun `after a charge the same pair resumes on visible controls before another hit is witnessed`() {
        val records = baseline() + listOf(
            at("move", .5, ArchivedFastMoveIdentified("PLAYER", "Scrafty", "Counter", nanos(1.0),
                cadenceSampleCount = 1, basis = "TEST")),
            at("use", 2.0, ArchivedFastMoveUseObserved("use", "PLAYER", "OPPONENT", attackerSpeciesName = "Scrafty",
                evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "TEST")),
            ready(),
            at("spent", 10.0, ArchivedChargedMoveEnergySpent("PLAYER", "Scrafty", "Foul Play", 40, "TEST")),
            at("end", 24.0, ArchivedMatchEnded("WIN")),
        )
        fun predictions(postCharge: List<ArchivedRealityArticle>) =
            MatchReplayModel(MatchArchive(matchId = "transition", articles = records + postCharge))
                .eventMarkers.filter { it.inferenceExplanation != null && it.atNanos > nanos(6.0) }.map { it.atNanos }
        assertTrue(predictions(lost(16.0)).isEmpty())
        assertEquals((19..23).map { nanos(it.toDouble()) }, predictions(lost(16.0) + seen(18.0)))
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

    @Test fun `a late grade and sampled loss suppress only the uncertain interval after confirmed combat`() {
        val grade = at("grade", 12.766, ArchivedRawText("NICE!"), "QTE_PROGRESS_TEXT_WITNESS")
        val hit = at("hit", 7.694, ArchivedFastMoveUseObserved("observed", "PLAYER", "OPPONENT",
            evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "TEST"))
        fun windows(samples: List<ArchivedRealityArticle>, cycles: List<ReplayFastMoveCycle> = emptyList()) =
            ReplayChargeTransitions(samples) { ReplayCombatant("Scrafty", 560, 0) }.speculativeQteWindows(cycles)
        val samples = setup() + seen(7.166) + lost(10.902) + grade
        assertEquals(listOf((nanos(7.166) + 1)..nanos(12.766)), windows(samples))
        assertEquals(listOf((nanos(7.694) + 1)..nanos(12.766)), windows((samples + hit).reversed()))
        val use = ReplayFastMoveWitness("OPPONENT", nanos(10.92), "Counter", null, nanos(1.0),
            nanos(.1), nanos(20.0), setOf("HP_DAMAGE_TICK"), setOf("tick"), setOf(nanos(10.93)))
        assertTrue(windows(samples, listOf(ReplayFastMoveCycle(use, nanos(9.92), nanos(10.92)))).isEmpty())
        assertTrue(windows(samples + hit.copy(monotonicTimeNanos = nanos(11.0))).isEmpty())
        assertEquals(listOf((nanos(9.9) + 1)..nanos(12.766)), windows(samples + seen(9.9)))
        assertEquals(1, windows(samples + at("grade2", 13.0, ArchivedRawText("GREAT!"), "QTE_PROGRESS_TEXT_WITNESS")).size)
    }

    @Test fun `unread controls and unrelated phases do not extend an unobserved QTE opening`() {
        val grade = at("grade", 12.766, ArchivedRawText("NICE!"), "QTE_PROGRESS_TEXT_WITNESS")
        fun windows(samples: List<ArchivedRealityArticle>) =
            ReplayChargeTransitions(samples) { ReplayCombatant("Scrafty", 560, 0) }.speculativeQteWindows(emptyList())
        val samples = setup() + seen(7.166) + lost(10.902) + grade
        assertTrue(windows(samples + seen(11.0)).isEmpty())
        assertTrue(windows(setup() + seen(1.0) + lost(10.902) + grade).isEmpty())
        assertTrue(windows(setup() + lost(7.166) + lost(10.902) + grade).isEmpty())
        assertTrue(windows(setup() + seen(7.166) + lost(10.902).take(1) + grade).isEmpty())
        assertTrue(windows(setup() + seen(7.166) + lost(10.902, top = 1805) + grade).isEmpty())
        assertTrue(windows(samples + ready(8.0)).isEmpty())
        assertTrue(windows(samples + at("incoming", 12.766, ArchivedAttackIncoming)).isEmpty())
        assertTrue(windows(samples + at("switch", 9.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Raichu", 26))).isEmpty())
        assertTrue(windows(samples + at("faint", 9.0, ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Combusken", 256, "TEST"))).isEmpty())
        assertTrue(windows(samples + at("off", 8.0, ArchivedWitnessOperating(false), SOURCE)).isEmpty())
        assertTrue(windows(samples + at("gap", 13.0, ArchivedVisualCaptureGapObserved(nanos(5.0)))).isEmpty())
        assertTrue(windows(samples.filterNot { it.articleId == "grade" } +
            grade.copy(sourceId = "ANNOUNCEMENT_WITNESS")).isEmpty())
    }

    @Test fun `a corroborated final loss of both controls ends only speculative cycles from the first loss`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val records = terminalEvidence(side)
            assertEquals(nanos(7.0)..nanos(12.0), terminal(records))
            assertEquals(terminal(records), terminal(records.reversed()))
            val use = ArchivedFastMoveUseObserved("hit", side, if (side == "PLAYER") "OPPONENT" else "PLAYER",
                evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST")
            assertEquals(terminal(records), terminal(records + at("real-before-loss", 5.0, use)))
            assertNull(terminal(records + at("real-after-loss", 8.0, use)))
        }
    }

    @Test fun `survivors intervening phases and incomplete terminal control evidence keep the clock open`() {
        val records = terminalEvidence("OPPONENT")
        val hp = records.single { it.articleId == "red" }
        val full = records.single { it.articleId == "live" }
        assertNull(terminal(records + hp.copy(articleId = "later-tiny-hp", monotonicTimeNanos = nanos(7.2),
            payload = (hp.payload as ArchivedActiveHpBarMeasured).copy(filledFraction = 1f / 188))))
        assertNull(terminal(records + full.copy(articleId = "live-return", monotonicTimeNanos = nanos(8.0))))
        assertNull(terminal(records + ready(6.0)))
        assertNull(terminal(records + at("entry", 6.0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364))))
        assertNull(terminal(records + at("gap", 11.0, ArchivedVisualCaptureGapObserved(nanos(2.0)))))
        assertNull(terminal(records + seen(8.0)))
        assertNull(terminal(records.filterNot { it.articleId == "rim-9.5-2" }))
        assertNull(terminal(records.filterNot { it.articleId.startsWith("rim-9.5-") }))
        assertNull(terminal(records.filterNot { it.articleId == "result" }))
        assertNull(terminal(records.filterNot { it.articleId == "count" }))
        assertNull(terminal(records.filterNot { it.articleId == "orange" }))
        assertNull(terminal(records.filterNot { it.articleId == "red" }))
        assertNull(terminal(records + at("count-conflict", 4.0,
            ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3))))
    }

    private fun terminal(records: List<ArchivedRealityArticle>) =
        ReplayChargeTransitions(records) { ReplayCombatant("Scrafty", 560, 0) }
            .speculativeTerminalWindow(emptyList(), ReplayHpEvidence(records))

    private fun terminalEvidence(side: String): List<ArchivedRealityArticle> {
        val left = if (side == "OPPONENT") 338 else 38
        fun profile(live: Int, damage: Int) = ArchivedCompleteHpBarObserved(side, left, 640, left + 192,
            657, 672, 1493, 188, live, damage, if (damage == 0) "LIVE_COLOR" else "UNRESOLVED_COLOR",
            null, null, 250f, 160f, unmaskedUncertainColumns = 21)
        val count = if (side == "OPPONENT") ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3)
            else ArchivedPlayerPokeBallCountMeasured(1, 3)
        return setup() + at("count", 4.0, count) +
            at("live", 4.2, profile(11, 0)).copy(confidence = .99f) +
            at("red", 4.2, ArchivedActiveHpBarMeasured(side, left, 640, left + 192, 657,
                11f / 188, 672, 1493, "RED")).copy(confidence = .99f) +
            at("orange", 4.55, profile(0, 11)).copy(confidence = .99f) +
            seen(5.5) + lost(7.0) + lost(9.5) + lost(11.0) +
            at("result", 12.0, ArchivedMatchEnded(if (side == "OPPONENT") "WIN" else "LOSS"))
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
