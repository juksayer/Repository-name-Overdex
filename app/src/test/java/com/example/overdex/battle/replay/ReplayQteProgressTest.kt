package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayQteProgressTest {
    @Test fun `a grade lingering over a completed move banner cannot reopen the completed QTE`() {
        val archive = archive(at("ready", 1.0, ArchivedGetReadyWitnessed),
            at("used", 8.0, spend("PLAYER")), grade("lingering-grade", 8.1, "EXCELLENT!"))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val overlapping = model.sceneAt(ns(8.1))
        assertEquals("EXCELLENT", overlapping.qteMilestone)
        val action = overlapping.chargedMoveActions.single()
        assertEquals(ReplayChargePhase.STRIKING, action.phase)
        assertEquals("Earth Power", action.moveName)
        assertTrue("QTE_PROGRESS_TEXT" in action.evidenceKinds)
        assertEquals(1, model.attackSoundEvents.count { it.charged })
        assertTrue(model.sceneAt(ns(10.0)).chargedMoveActions.isEmpty())
        assertTrue(model.sceneAt(ns(18.0)).chargedMoveActions.isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `a new preparation cue closes the prior progress episode instead of overlapping or merging it`() {
        val interruptions = listOf(
            at("incoming", 4.0, ArchivedAttackIncoming),
            at("shield", 4.0, ArchivedRawText("Use a Protect Shield?")).copy(sourceId = "ANNOUNCEMENT_WITNESS"),
            at("opponent-charge", 4.0, spend("OPPONENT")),
            at("fresh-ready", 4.0, ArchivedGetReadyWitnessed),
        )
        for (interruption in interruptions) {
            val model = MatchReplayModel(archive(grade("first-progress", 2.0, "NICE!"),
                interruption, grade("next-progress", 6.0, "GREAT!")))
            val previous = model.sceneAt(ns(3.9)).chargedMoveActions.single { it.side == "PLAYER" }
            assertEquals(ns(1.9), previous.elapsedNanos)
            if (interruption.payload !is ArchivedGetReadyWitnessed) {
                assertTrue(model.sceneAt(ns(4.0)).chargedMoveActions.none { it.side == "PLAYER" })
                assertEquals(0L, model.sceneAt(ns(6.0)).chargedMoveActions.single { it.side == "PLAYER" }.elapsedNanos)
            } else {
                val restarted = model.sceneAt(ns(4.0)).chargedMoveActions.single { it.side == "PLAYER" }
                assertEquals(0L, restarted.elapsedNanos)
                assertTrue("GET_READY" in restarted.evidenceKinds)
            }
        }
    }

    @Test fun `recorded Blanche final Nice retains Camerupt preparation and unknown finishing evidence`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/nov7-blanche-resource-lifetimes.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val grade = archive.articles.single { replayQteProgressGrade(it) == "NICE" }
        val at = grade.monotonicTimeNanos!!
        val resultAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val model = MatchReplayModel(archive)

        assertTrue(model.sceneAt(at - 1).chargedMoveActions.isEmpty())
        val observed = model.sceneAt(at)
        assertEquals("Camerupt", observed.player!!.speciesName)
        assertEquals("Vaporeon", observed.opponent!!.speciesName)
        assertEquals("NICE", observed.qteMilestone)
        assertEquals(.4920635f, observed.opponentHp!!.filledFraction!!, .000001f)
        val charge = observed.chargedMoveActions.single()
        assertEquals(ReplayChargePhase.CHARGING, charge.phase)
        assertEquals(0L, charge.elapsedNanos)
        assertNull(charge.moveName)
        assertNull(charge.type)
        assertEquals(1, charge.qteLevel)
        assertFalse(model.sceneAt(resultAt).opponent!!.isFainted)
        assertEquals(resultAt, model.sceneAt(resultAt).opponent!!.defeatedAtNanos)
        assertNull(model.sceneAt(resultAt).opponentHp)
        assertTrue(model.attackSoundEvents.isEmpty())
        assertTrue(model.hapticEventsBetween(at, resultAt).isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `only an exact grade in the accepted dedicated stream supplies a player charge cue`() {
        for ((text, expected) in listOf(
            "NICE" to "NICE", "NICE!" to "NICE", " \nGREAT!\t" to "GREAT",
            "Great!" to "GREAT", "excellent!" to "EXCELLENT",
        )) {
            val article = grade("grade", 2.0, text)
            assertEquals(text, expected, replayQteProgressGrade(article))
            assertTrue(text, isReplayPlayerChargeCue(article))
        }
        for (text in listOf("", "N I C E", "NICE?", "NICE attack!", "very GREAT!",
            "EXCELLENT!\nGet Ready!", "Get Ready!")) {
            assertNull(text, replayQteProgressGrade(grade("rejected", 2.0, text)))
        }
        for (source in listOf("QTE_PROGRESS_TEXT_WITNESS_OCR", "QTE_PROGRESS_TEXT_WITNESS_OCR_RAW",
            "ANNOUNCEMENT_WITNESS", "PLAYER_SPECIES_OVERLAY_PIPELINE", "UNRELATED_LOG")) {
            val unrelated = grade("wrong-source", 2.0, "NICE!").copy(sourceId = source)
            assertNull(source, replayQteProgressGrade(unrelated))
            assertFalse(source, isReplayPlayerChargeCue(unrelated))
        }
        assertTrue(isReplayPlayerChargeCue(at("ready", 2.0, ArchivedGetReadyWitnessed)))
    }

    @Test fun `missed final Camerupt charge starts at preserved Nice without inventing its move or finish`() {
        val archive = archive(
            at("last-hp", 63.0, ArchivedActiveHpBarMeasured("OPPONENT", 239, 841, 432, 861, .49f)),
            at("known-label", 64.0, ArchivedPlayerChargeMoveLabelRead(1, "EARTH POWER")),
            grade("nice", 67.0, "NICE!").copy(perceivedAt = 200_000, recordedAt = 220_000),
            at("win", 82.0, ArchivedMatchEnded("WIN")),
        )
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = mapOf(
            "Camerupt" to listOf(Move("Earth Power", PokemonType.GROUND, 90, 55, false))))

        assertTrue(model.sceneAt(ns(67.0) - 1).chargedMoveActions.isEmpty())
        val observed = model.sceneAt(ns(67.0))
        val charge = observed.chargedMoveActions.single()
        assertEquals("PLAYER", charge.side)
        assertEquals(ReplayChargePhase.CHARGING, charge.phase)
        assertEquals(0L, charge.elapsedNanos)
        assertTrue("QTE_PROGRESS_TEXT" in charge.evidenceKinds)
        assertFalse("GET_READY" in charge.evidenceKinds)
        assertEquals("NICE", observed.qteMilestone)
        assertEquals("PLAYER", observed.qteMilestoneSide)
        assertEquals(1, charge.qteLevel)
        assertNull("A legal move and its button label do not prove that move was performed", charge.moveName)
        assertNull(charge.type)
        assertEquals(.49f, observed.opponentHp!!.filledFraction!!, 0f)
        assertFalse(observed.opponent!!.isFainted)
        assertEquals(0, observed.playerSpentEnergy)

        for (cursor in listOf(67.0, 68.0, 72.0, 76.9, 77.0, 81.9, 82.0, 83.0)) {
            val scene = model.sceneAt(ns(cursor))
            assertTrue(scene.chargedMoveActions.none { it.phase == ReplayChargePhase.STRIKING })
            assertTrue(scene.chargedMoveActions.all { it.moveName == null && it.type == null })
            assertEquals(0, scene.playerSpentEnergy)
            assertEquals(0, scene.opponentSpentEnergy)
            assertFalse(scene.opponent!!.isFainted)
            assertTrue(scene.opponentHp?.filledFraction != 0f)
        }
        assertTrue("The opening remains bounded without a closing observation",
            model.sceneAt(ns(77.0)).chargedMoveActions.isEmpty())
        assertTrue(model.attackSoundEvents.none { it.charged })
        assertTrue(model.hapticEventsBetween(model.startNanos, model.endNanos).isEmpty())
        assertEquals(observed, model.sceneAt(ns(67.0)))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `an observed grade keeps its actual level and never manufactures earlier grades or physical pulses`() {
        for ((label, level) in listOf("NICE" to 1, "GREAT" to 2, "EXCELLENT" to 3)) {
            val model = MatchReplayModel(archive(grade("grade", 2.0, "$label!"),
                at("win", 5.0, ArchivedMatchEnded("WIN"))))
            val before = model.sceneAt(ns(2.0) - 1)
            assertNull(before.qteMilestone)
            assertTrue(before.chargedMoveActions.isEmpty())
            val atGrade = model.sceneAt(ns(2.0))
            assertEquals(label, atGrade.qteMilestone)
            assertEquals(level, atGrade.chargedMoveActions.single().qteLevel)
            assertEquals(0L, atGrade.chargedMoveActions.single().elapsedNanos)
            assertNull("The text expires without showing an invented next grade", model.sceneAt(ns(3.0)).qteMilestone)
            assertEquals(level, model.sceneAt(ns(3.0)).chargedMoveActions.single().qteLevel)
            assertTrue(model.hapticEventsBetween(model.startNanos, model.endNanos).isEmpty())
        }
    }

    @Test fun `Get Ready repeated grades and a later named charge combine into one episode`() {
        val archive = archive(
            at("ready", 1.0, ArchivedGetReadyWitnessed),
            grade("nice", 2.0, "NICE!"), grade("same-nice", 2.2, "NICE!"),
            grade("great", 3.0, "GREAT!"), grade("excellent", 4.0, "EXCELLENT!"),
            at("spent", 8.0, spend("PLAYER")),
        )
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        assertTrue(model.sceneAt(ns(1.0) - 1).chargedMoveActions.isEmpty())
        val start = model.sceneAt(ns(1.0)).chargedMoveActions.single()
        assertEquals(0L, start.elapsedNanos)
        assertEquals(ns(8.0), start.startedAtNanos)
        assertEquals("Earth Power", start.moveName)
        assertTrue(start.evidenceKinds.containsAll(setOf("GET_READY", "QTE_PROGRESS_TEXT", "NAMED_CHARGED_MOVE")))
        val scenes = (10L..99L).map { model.sceneAt(it * 100_000_000L) }
        assertTrue("Repeated grades cannot create overlapping charge animations", scenes.all { it.chargedMoveActions.size <= 1 })
        assertEquals(setOf(ns(8.0)), scenes.flatMap { it.chargedMoveActions }.map { it.startedAtNanos }.toSet())
        assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(ns(7.9)).chargedMoveActions.single().phase)
        assertEquals(ReplayChargePhase.STRIKING, model.sceneAt(ns(8.0)).chargedMoveActions.single().phase)
        assertEquals(55, model.sceneAt(ns(8.1)).playerSpentEnergy)
        assertEquals(1, model.attackSoundEvents.count { it.charged })
        assertTrue("Observed text alone cannot create haptic playback", model.hapticEventsBetween(0, model.endNanos).isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `standalone progress pairs with later named player charge without backdating an unseen opening`() {
        val model = MatchReplayModel(archive(grade("great", 3.0, "GREAT!"),
            at("spent", 8.0, spend("PLAYER"))))

        assertTrue(model.sceneAt(ns(3.0) - 1).chargedMoveActions.isEmpty())
        val start = model.sceneAt(ns(3.0)).chargedMoveActions.single()
        assertEquals("PLAYER", start.side)
        assertEquals(0L, start.elapsedNanos)
        assertEquals("Earth Power", start.moveName)
        assertEquals(ns(8.0), start.startedAtNanos)
        assertEquals(2, start.qteLevel)
        assertFalse("GET_READY" in start.evidenceKinds)
        assertEquals(ReplayChargePhase.STRIKING, model.sceneAt(ns(8.0)).chargedMoveActions.single().phase)
    }

    @Test fun `repeated progress within one open episode does not restart or extend the ten second limit`() {
        val model = MatchReplayModel(archive(grade("nice", 1.0, "NICE!"),
            grade("nice-repeat", 2.0, "NICE!"), grade("great", 4.0, "GREAT!"),
            grade("excellent", 5.0, "EXCELLENT!")))

        for (cursor in listOf(1.0, 2.0, 4.0, 5.0, 9.0, 10.9)) {
            val actions = model.sceneAt(ns(cursor)).chargedMoveActions
            assertEquals(1, actions.size)
            assertEquals(ReplayChargePhase.CHARGING, actions.single().phase)
            assertEquals(ns(cursor) - ns(1.0), actions.single().elapsedNanos)
        }
        assertTrue(model.sceneAt(ns(11.0)).chargedMoveActions.isEmpty())
        assertTrue(model.attackSoundEvents.isEmpty())
    }

    @Test fun `opponent incoming and named attack cannot steal a dedicated player progress grade`() {
        val model = MatchReplayModel(archive(
            at("incoming", 1.0, ArchivedAttackIncoming),
            grade("great", 3.0, "GREAT!"),
            at("opponent-spent", 8.0, spend("OPPONENT")),
        ))

        val atGrade = model.sceneAt(ns(3.0))
        assertEquals("PLAYER", atGrade.qteMilestoneSide)
        val playerPreparation = atGrade.chargedMoveActions.single { it.side == "PLAYER" }
        assertEquals(ReplayChargePhase.CHARGING, playerPreparation.phase)
        assertEquals(2, playerPreparation.qteLevel)
        assertNull(playerPreparation.moveName)
        val opponent = model.sceneAt(ns(8.1)).chargedMoveActions.single { it.side == "OPPONENT" }
        assertEquals(ReplayChargePhase.STRIKING, opponent.phase)
        assertEquals("Aqua Tail", opponent.moveName)
        assertEquals(0, opponent.qteLevel)
        assertTrue(model.attackSoundEvents.filter { it.charged }.all { it.moveName == "Aqua Tail" })
        assertTrue(model.hapticEventsBetween(model.startNanos, model.endNanos).isEmpty())
    }

    @Test fun `source times retain delayed pre result grades and exclude post result grades`() {
        val before = grade("before-result", 4.0, "EXCELLENT!").copy(perceivedAt = 25_000, recordedAt = 30_000)
        val after = grade("after-result", 6.0, "NICE!").copy(perceivedAt = 4_000, recordedAt = 4_000)
        val archive = archive(at("win", 5.0, ArchivedMatchEnded("WIN")), after, before)
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)

        assertTrue(model.sceneAt(ns(4.0) - 1).chargedMoveActions.isEmpty())
        assertEquals("EXCELLENT", model.sceneAt(ns(4.0)).qteMilestone)
        assertEquals(3, model.sceneAt(ns(4.0)).chargedMoveActions.single().qteLevel)
        for (cursor in listOf(5.0, 6.0, 7.0)) {
            assertTrue(model.sceneAt(ns(cursor)).chargedMoveActions.isEmpty())
            assertNull(model.sceneAt(ns(cursor)).qteMilestone)
        }
        assertTrue(model.attackSoundEvents.isEmpty())
        assertTrue(model.hapticEventsBetween(model.startNanos, model.endNanos).isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `next source confirmed fast move closes a grade only preparation on either side`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val hit = ArchivedFastMoveUseObserved("hit", side, if (side == "PLAYER") "OPPONENT" else "PLAYER",
                evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "SOURCE_CONFIRMED")
            val model = MatchReplayModel(archive(grade("nice", 2.0, "NICE!"), at("hit", 4.0, hit)))
            assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(ns(3.9)).chargedMoveActions.single().phase)
            assertTrue("$side resumes ordinary play at its preserved source time",
                model.sceneAt(ns(4.0)).chargedMoveActions.isEmpty())
            assertTrue(model.sceneAt(ns(5.0)).chargedMoveActions.isEmpty())
        }
    }

    @Test fun `ordinary fast move stays visible at the exclusive close and a later grade starts a fresh episode`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val hit = ArchivedFastMoveUseObserved("resumed-hit", side,
                if (side == "PLAYER") "OPPONENT" else "PLAYER",
                attackerSpeciesName = if (side == "PLAYER") "Camerupt" else "Vaporeon",
                evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "SOURCE_CONFIRMED")
            val model = MatchReplayModel(archive(
                grade("first-nice", 2.0, "NICE!"),
                at("ordinary-hit", 4.0, hit),
                grade("next-great", 6.0, "GREAT!"),
            ))

            assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(ns(4.0) - 1).chargedMoveActions.single().phase)
            val resumed = model.sceneAt(ns(4.0))
            assertTrue("The open charge ends before the ordinary attack at exactly its source time",
                resumed.chargedMoveActions.isEmpty())
            assertTrue("No synthetic charge strike tail may suppress the source-confirmed fast action",
                resumed.fastMoveActions.any { it.side == side && it.startedAtNanos == ns(4.0) })
            assertTrue(model.sceneAt(ns(6.0) - 1).chargedMoveActions.isEmpty())
            val next = model.sceneAt(ns(6.0)).chargedMoveActions.single()
            assertEquals("PLAYER", next.side)
            assertEquals(ReplayChargePhase.CHARGING, next.phase)
            assertEquals(0L, next.elapsedNanos)
            assertEquals(2, next.qteLevel)
            assertNull(next.moveName)
            assertTrue(model.attackSoundEvents.none { it.charged })
        }
    }

    @Test fun `replacement closes a grade only preparation without carrying it into the next combatant`() {
        for ((side, species, id) in listOf(Triple("PLAYER", "Turtonator", 776), Triple("OPPONENT", "Sealeo", 364))) {
            val model = MatchReplayModel(archive(grade("nice", 2.0, "NICE!"),
                at("replacement", 4.0, ArchivedActivePokemonSpeciesWitnessed(side, species, id))))
            assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(ns(3.9)).chargedMoveActions.single().phase)
            assertTrue(model.sceneAt(ns(4.0)).chargedMoveActions.isEmpty())
            assertTrue(model.sceneAt(ns(5.0)).chargedMoveActions.isEmpty())
        }
    }

    @Test fun `a grade after a species replacement belongs to a new episode and the current player actor`() {
        for ((side, species, id) in listOf(Triple("PLAYER", "Turtonator", 776), Triple("OPPONENT", "Sealeo", 364))) {
            val model = MatchReplayModel(archive(
                grade("first-nice", 2.0, "NICE!"),
                at("replacement", 4.0, ArchivedActivePokemonSpeciesWitnessed(side, species, id)),
                grade("next-great", 6.0, "GREAT!"),
            ))

            assertEquals("Camerupt", model.sceneAt(ns(3.9)).player?.speciesName)
            assertEquals(ReplayChargePhase.CHARGING, model.sceneAt(ns(3.9)).chargedMoveActions.single().phase)
            assertTrue(model.sceneAt(ns(4.0)).chargedMoveActions.isEmpty())
            assertTrue(model.sceneAt(ns(6.0) - 1).chargedMoveActions.isEmpty())
            val nextScene = model.sceneAt(ns(6.0))
            assertEquals(if (side == "PLAYER") "Turtonator" else "Camerupt", nextScene.player?.speciesName)
            assertEquals(if (side == "OPPONENT") "Sealeo" else "Vaporeon", nextScene.opponent?.speciesName)
            val next = nextScene.chargedMoveActions.single()
            assertEquals("PLAYER", next.side)
            assertEquals(0L, next.elapsedNanos)
            assertEquals(ReplayChargePhase.CHARGING, next.phase)
            assertEquals("GREAT", nextScene.qteMilestone)
            assertNull(next.moveName)
            assertTrue(model.hapticEventsBetween(model.startNanos, model.endNanos).isEmpty())
        }
    }

    @Test fun `grade without a source clock cannot create a replay preparation`() {
        val model = MatchReplayModel(archive(grade("no-source-time", 2.0, "NICE!").copy(monotonicTimeNanos = null)))
        assertTrue(model.sceneAt(ns(2.0)).chargedMoveActions.isEmpty())
        assertNull(model.sceneAt(ns(2.0)).qteMilestone)
    }

    private fun archive(vararg evidence: ArchivedRealityArticle) = MatchArchive(matchId = MATCH, articles = listOf(
        at("player", 0.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
        at("opponent", 0.0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134)),
        at("go", 0.0, ArchivedMatchStarted),
    ) + evidence)

    private fun grade(id: String, second: Double, text: String) = at(id, second, ArchivedRawText(text))
        .copy(sourceId = "QTE_PROGRESS_TEXT_WITNESS")

    private fun spend(side: String) = ArchivedChargedMoveEnergySpent(side,
        if (side == "PLAYER") "Camerupt" else "Vaporeon",
        if (side == "PLAYER") "Earth Power" else "Aqua Tail",
        if (side == "PLAYER") 55 else 35, "ANNOUNCEMENT")

    private fun at(id: String, second: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, MATCH, (second * 1000).toLong(), (second * 1000).toLong(), "TEST", payload,
        emptyList(), null, null, emptyList(), ns(second))

    private fun ns(second: Double) = (second * SECOND).toLong()

    private companion object {
        const val MATCH = "qte-progress"
        const val SECOND = 1_000_000_000L
    }
}
