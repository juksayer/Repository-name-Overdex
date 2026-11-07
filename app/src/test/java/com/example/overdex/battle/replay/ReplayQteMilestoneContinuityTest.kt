package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayQteMilestoneContinuityTest {
    @Test fun `recorded overlapping Blanche patterns cannot restart Nice after the same charge reaches Excellent`() {
        // Both accepted three-pulse runs from the Blanche recording, relative
        // to its record origin. Only Article IDs are replaced by local labels.
        val pulseTimes = listOf(72.583871081, 72.926853773, 73.682574158,
            73.748728004, 74.613239927, 74.705736312)
        val archive = archive(
            at("ready", 66.118368850, ArchivedGetReadyWitnessed),
            *pulseTimes.mapIndexed { index, time -> at("pulse-$index", time, pulse()) }.toTypedArray(),
            pattern("announcement-pattern", pulseTimes[0], listOf("pulse-0", "pulse-1", "pulse-2")),
            pattern("ready-pattern", pulseTimes[3], listOf("ready", "pulse-3", "pulse-4", "pulse-5")),
            at("used", 73.932591620, spend()),
            // The same real banner also publishes this repeated identity; it
            // establishes no new actor and must not reset the visual grade.
            at("same-player-from-used-banner", 73.932591620,
                ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776))
                .copy(sourceId = "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS"),
        )
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)

        assertNull(model.sceneAt(ns(pulseTimes[0]) - 1).qteMilestone)
        assertEquals(0, model.sceneAt(ns(pulseTimes[0]) - 1).chargedMoveActions.single().qteLevel)
        assertEquals("NICE", model.sceneAt(ns(pulseTimes[0])).qteMilestone)
        assertEquals("GREAT", model.sceneAt(ns(pulseTimes[1])).qteMilestone)
        assertEquals("EXCELLENT", model.sceneAt(ns(pulseTimes[2])).qteMilestone)
        val overlapping = model.sceneAt(ns(pulseTimes[3]))
        assertEquals("The second accepted run does not lower the same charge's visual grade",
            "EXCELLENT", overlapping.qteMilestone)
        assertEquals(3, overlapping.chargedMoveActions.single().qteLevel)
        for (at in pulseTimes.drop(2)) {
            val scene = model.sceneAt(ns(at))
            assertEquals(3, scene.chargedMoveActions.single().qteLevel)
            assertTrue(scene.qteMilestone == null || scene.qteMilestone == "EXCELLENT")
        }
        assertNull("A suppressed lower grade cannot refresh an older label's source clock",
            model.sceneAt(ns(pulseTimes[4])).qteMilestone)
        assertNull(model.sceneAt(ns(pulseTimes[5])).qteMilestone)
        assertEquals(pulseTimes.map(::ns), model.hapticEventsBetween(0, model.endNanos).map { it.atNanos })
        assertTrue(model.hapticEventsBetween(0, model.endNanos).all { it.pulseCount == 1 })
        assertEquals(overlapping, model.sceneAt(ns(pulseTimes[3])))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `repeated player or opponent badge timestamps do not create a new charge actor`() {
        for ((side, species, id) in listOf(Triple("PLAYER", "Turtonator", 776), Triple("OPPONENT", "Sealeo", 364))) {
            val archive = archive(
                at("ready", 1.0, ArchivedGetReadyWitnessed),
                at("excellent", 2.0, ArchivedRawText("EXCELLENT!")).copy(sourceId = "QTE_PROGRESS_TEXT_WITNESS"),
                at("same-badge", 2.1, ArchivedActivePokemonSpeciesWitnessed(side, species, id))
                    .copy(sourceId = "${side}_SPECIES_OVERLAY_PIPELINE"),
                at("lower-repeat", 2.2, ArchivedRawText("NICE!")).copy(sourceId = "QTE_PROGRESS_TEXT_WITNESS"),
            )
            val original = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            val before = model.sceneAt(ns(2.0))
            assertEquals("EXCELLENT", before.qteMilestone)
            val repeated = model.sceneAt(ns(2.2))
            assertEquals("An identity refresh on $side is the same appearance", "EXCELLENT", repeated.qteMilestone)
            assertEquals(3, repeated.chargedMoveActions.single().qteLevel)
            assertTrue(model.hapticEventsBetween(0, model.endNanos).isEmpty())
            assertEquals(before, model.sceneAt(ns(2.0)))
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `shared pulse timestamps keep the highest supported grade while physical playback stays deduplicated`() {
        val archive = archive(
            at("ready", .5, ArchivedGetReadyWitnessed),
            at("one", 1.0, pulse()), at("two", 1.6, pulse()),
            at("three", 2.3, pulse()), at("four", 2.6, pulse()),
            pattern("first", 1.0, listOf("one", "two", "three")),
            pattern("overlapping", 1.6, listOf("two", "three", "four")),
            at("used", 4.0, spend()),
        )
        val model = MatchReplayModel(archive)

        assertEquals("NICE", model.sceneAt(ns(1.0)).qteMilestone)
        assertEquals("GREAT", model.sceneAt(ns(1.6)).qteMilestone)
        assertEquals("EXCELLENT", model.sceneAt(ns(2.3)).qteMilestone)
        assertEquals(3, model.sceneAt(ns(2.6)).chargedMoveActions.single().qteLevel)
        assertEquals(listOf(1.0, 1.6, 2.3, 2.6).map(::ns),
            model.hapticEventsBetween(0, model.endNanos).map { it.atNanos })
    }

    @Test fun `an observed Great cannot regress when a later vibration pattern begins at Nice`() {
        val model = MatchReplayModel(archive(
            at("ready", 1.0, ArchivedGetReadyWitnessed),
            at("observed-great", 3.0, ArchivedRawText("GREAT!")).copy(sourceId = "QTE_PROGRESS_TEXT_WITNESS"),
            at("one", 3.1, pulse()), at("two", 3.4, pulse()), at("three", 3.8, pulse()),
            pattern("pattern", 3.1, listOf("one", "two", "three")),
            at("used", 5.0, spend()),
        ))

        assertNull(model.sceneAt(ns(3.0) - 1).qteMilestone)
        assertEquals("GREAT", model.sceneAt(ns(3.0)).qteMilestone)
        assertEquals("GREAT", model.sceneAt(ns(3.1)).qteMilestone)
        assertEquals(2, model.sceneAt(ns(3.1)).chargedMoveActions.single().qteLevel)
        assertEquals("EXCELLENT", model.sceneAt(ns(3.8)).qteMilestone)
        assertEquals(listOf(3.1, 3.4, 3.8).map(::ns),
            model.hapticEventsBetween(0, model.endNanos).map { it.atNanos })
    }

    @Test fun `a genuinely separate next charge starts at Nice rather than inheriting the previous Excellent`() {
        val model = MatchReplayModel(archive(
            at("first-ready", 1.0, ArchivedGetReadyWitnessed),
            at("first-one", 2.0, pulse()), at("first-two", 3.0, pulse()), at("first-three", 4.0, pulse()),
            pattern("first-pattern", 2.0, listOf("first-one", "first-two", "first-three")),
            at("first-used", 5.0, spend()),
            at("second-ready", 7.0, ArchivedGetReadyWitnessed),
            at("second-one", 8.0, pulse()), at("second-two", 9.0, pulse()), at("second-three", 9.5, pulse()),
            pattern("second-pattern", 8.0, listOf("second-one", "second-two", "second-three")),
            at("second-used", 10.0, spend()),
        ))

        assertEquals("EXCELLENT", model.sceneAt(ns(4.0)).qteMilestone)
        assertEquals(3, model.sceneAt(ns(4.0)).chargedMoveActions.single().qteLevel)
        assertEquals("NICE", model.sceneAt(ns(8.0)).qteMilestone)
        val next = model.sceneAt(ns(8.0)).chargedMoveActions.single()
        assertEquals(1, next.qteLevel)
        assertEquals(ns(10.0), next.startedAtNanos)
        assertEquals("GREAT", model.sceneAt(ns(9.0)).qteMilestone)
        assertEquals("EXCELLENT", model.sceneAt(ns(9.5)).qteMilestone)
    }

    @Test fun `fresh Get Ready resets visual grade even when an earlier incomplete aggregate overlaps it`() {
        val model = MatchReplayModel(archive(
            at("first-ready", 1.0, ArchivedGetReadyWitnessed),
            at("first-one", 2.0, pulse()), at("first-two", 3.0, pulse()), at("first-three", 4.0, pulse()),
            pattern("first-pattern", 2.0, listOf("first-one", "first-two", "first-three")),
            at("second-ready", 6.0, ArchivedGetReadyWitnessed),
            at("second-one", 7.0, pulse()), at("second-two", 8.0, pulse()), at("second-three", 9.0, pulse()),
            pattern("second-pattern", 7.0, listOf("second-one", "second-two", "second-three")),
        ))

        assertEquals("EXCELLENT", model.sceneAt(ns(4.0)).qteMilestone)
        assertEquals("NICE", model.sceneAt(ns(7.0)).qteMilestone)
        assertEquals(1, model.sceneAt(ns(7.0)).chargedMoveActions.single().qteLevel)
        assertEquals("GREAT", model.sceneAt(ns(8.0)).qteMilestone)
    }

    private fun archive(vararg evidence: ArchivedRealityArticle) = MatchArchive(matchId = "milestones", articles = listOf(
        at("player", 0.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776))
            .copy(sourceId = "PLAYER_SPECIES_OVERLAY_PIPELINE"),
        at("opponent", 0.0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364))
            .copy(sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE"),
        at("go", 0.0, ArchivedMatchStarted),
    ) + evidence)

    private fun pattern(id: String, second: Double, predecessors: List<String>) = at(id, second,
        ArchivedChargeMoveQteVibrationPatternInferred("PLAYER", 3, 2_000_000_000L, "ACCEPTED_TEST_PATTERN"))
        .copy(predecessorIds = predecessors)

    private fun pulse() = ArchivedDeviceMotionPulseMeasured(100_000_000L, 1f, .5f, 10, "LINEAR_ACCELERATION")
    private fun spend() = ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Brutal Swing", 35, "ANNOUNCEMENT")
    private fun at(id: String, second: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "milestones", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), ns(second))
    private fun ns(second: Double) = (second * 1_000_000_000L).toLong()
}
