package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class RecipientAudioFastMoveUseTest {
    @Test fun `completed recipient transient and uniquely scoped sound support one source timed hit in either receipt order`() {
        for (reversed in listOf(false, true)) {
            val inference = prepared()
            val evidence = listOf(source("visual", 2.0, visual(), 2.8), source("sound", 1.5, sound(), 2.9))
            val uses = (if (reversed) evidence.reversed() else evidence).flatMap(inference::accept) + inference.flush()
            val use = uses.single()
            assertEquals("visual", use.observedArticle.id.value)
            assertEquals(nanos(2.0), use.observedArticle.monotonicTimeNanos)
            assertEquals(ActivePokemonSide.OPPONENT, use.payload.attackingSide)
            assertEquals("Quagsire", use.payload.attackerSpeciesName)
            assertEquals(setOf("visual", "sound", "player", "opponent"), use.predecessorIds.map { it.value }.toSet())
            assertEquals(setOf(FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT, FastMoveUseEvidence.MATCHED_AUDIO),
                use.payload.evidenceKinds.toSet())
            assertTrue(inference.flush().isEmpty())
        }
    }

    @Test fun `sound alone visual alone motion and copied cue side never establish a hit`() {
        val cases = listOf(
            listOf(source("sound", 1.5, sound())),
            listOf(source("visual", 2.0, visual())),
            listOf(source("sound", 1.5, sound()), source("motion", 2.0,
                ActiveHpBarMotionCadenceMeasured(ActivePokemonSide.OPPONENT, nanos(.5), 8f, 4))),
            listOf(source("sound", 1.5, sound().copy(referenceSearchScopes = emptyList(),
                attackingSide = ActivePokemonSide.OPPONENT, cueAttackingSide = ActivePokemonSide.OPPONENT)),
                source("visual", 2.0, visual())),
        )
        cases.forEach { events ->
            val inference = prepared()
            assertTrue((events.flatMap(inference::accept) + inference.flush()).isEmpty())
        }
    }

    @Test fun `mirrored pools stale identities incomplete reference coverage and rejected audio remain unresolved`() {
        val normal = sound()
        val cases = listOf(
            normal.copy(referenceSearchScopes = normal.referenceSearchScopes.map {
                if (it.side == ActivePokemonSide.PLAYER) it.copy(moveNames = listOf("Mud Shot", "Incinerate")) else it
            }),
            normal.copy(referenceSearchScopes = normal.referenceSearchScopes.map {
                if (it.side == ActivePokemonSide.PLAYER) it.copy(speciesName = "Camerupt") else it
            }),
            normal.copy(referenceSearchScopes = normal.referenceSearchScopes.map {
                if (it.side == ActivePokemonSide.PLAYER) it.copy(moveNames = listOf("Incinerate", "Shadow Claw")) else it
            }),
            normal.copy(referenceMatchAccepted = false),
        )
        cases.forEach { audio ->
            val inference = prepared()
            inference.accept(source("sound", 1.5, audio))
            inference.accept(source("visual", 2.0, visual()))
            assertTrue(inference.flush().isEmpty())
        }
    }

    @Test fun `opposite recipient and transient outside source matching interval cannot borrow the sound`() {
        for ((at, effect) in listOf(2.0 to visual(ActivePokemonSide.OPPONENT), 3.1 to visual())) {
            val inference = prepared()
            inference.accept(source("sound", 1.5, sound(), 3.3))
            inference.accept(source("visual", at, effect, 3.4))
            assertTrue(inference.flush().isEmpty())
        }
    }

    @Test fun `audio matching tolerance is bounded on both edges`() {
        for ((at, expected) in listOf(1.55 to 1, 1.549 to 0, 2.85 to 1, 2.851 to 0)) {
            val inference = prepared()
            inference.accept(source("sound", 1.5, sound(), 3.0))
            inference.accept(source("visual", at, visual(), 3.1))
            assertEquals("impact at $at", expected, inference.flush().size)
        }
    }

    @Test fun `receipt grace is bounded even when source clocks coincide`() {
        val inference = prepared()
        inference.accept(source("sound", 1.5, sound(), 2.5))
        inference.accept(source("visual", 2.0, visual(), 3.701))
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `charged phase and its impact tail reject the pair while resumed ordinary evidence survives`() {
        val inference = prepared()
        inference.accept(source("ready", 1.0, GetReadyWitnessed))
        inference.accept(source("used", 2.0, ChargeMoveUsedAnnounced))
        inference.accept(source("charged-sound", 1.5, sound(), 2.8))
        inference.accept(source("charged-visual", 2.0, visual(), 2.9))
        assertTrue(inference.flush().isEmpty())
        inference.accept(source("resumed-sound", 3.0, sound(), 4.0))
        inference.accept(source("resumed-visual", 3.5, visual(), 4.1))
        assertEquals(listOf("resumed-visual"), inference.flush().map { it.observedArticle.id.value })
    }

    @Test fun `late charge cue removes an unpublished audio visual pair`() {
        val inference = prepared()
        inference.accept(source("sound", 1.5, sound(), 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        inference.accept(source("late-used", 1.8, ChargeMoveUsedAnnounced, 3.0))
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `sound matching across an appearance boundary cannot label the new combatant`() {
        val inference = prepared()
        inference.accept(source("switch", 1.95, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Whiscash")))
        val audio = sound().copy(referenceSearchScopes = sound().referenceSearchScopes.map {
            if (it.side == ActivePokemonSide.OPPONENT) it.copy(speciesName = "Whiscash") else it
        })
        inference.accept(source("sound", 1.5, audio))
        inference.accept(source("visual", 2.0, visual()))
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `onset after visual cannot cross either combatants switch faint or a new charge`() {
        val boundaries = listOf<TestimonyPayload>(
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Whiscash"),
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt"),
            ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Quagsire", basis = "EXPLICIT_TEST_FAINT_SEQUENCE"),
            ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", basis = "EXPLICIT_TEST_FAINT_SEQUENCE"),
            GetReadyWitnessed, ChargeMoveUsedAnnounced,
        )
        for (boundary in boundaries) {
            val inference = prepared()
            // Segment starts at2.4, reconstructed onset at2.3, visual at2.0.
            val output = inference.accept(source("sound", 1.9, sound(), 2.8)) +
                inference.accept(source("visual", 2.0, visual(), 2.9)) +
                inference.accept(source("later-boundary", 2.1, boundary, 3.0)) + inference.flush()
            assertTrue(boundary.toString(), output.isEmpty())
        }
    }

    @Test fun `later sound onset inside the same normal exchange remains supported`() {
        val inference = prepared()
        inference.accept(source("sound", 1.9, sound(), 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        assertEquals(1, inference.flush().size)
    }

    @Test fun `existing loss and border plus paired sound remain one hit in every receipt permutation`() {
        val events = listOf(
            source("sound", 1.5, sound(), 2.8), source("visual", 2.0, visual(), 2.9),
            source("loss", 2.01, ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .1f, .05f, .05f), 3.0),
            source("border", 2.02, HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, .3f, 4), 3.1),
        )
        fun permutations(items: List<RealityArticle>): List<List<RealityArticle>> = if (items.isEmpty()) listOf(emptyList())
            else items.flatMap { first -> permutations(items.filter { it !== first }).map { listOf(first) + it } }
        permutations(events).forEach { order ->
            val inference = prepared()
            val uses = order.flatMap(inference::accept) + inference.flush()
            assertEquals(order.map { it.id.value }.toString(), 1, uses.size)
            assertTrue(uses.single().payload.evidenceKinds.contains("HP_DAMAGE_TICK"))
            assertTrue(uses.single().payload.evidenceKinds.contains("HP_BORDER_PULSE"))
        }
    }

    @Test fun `overlapping audio windows repeated source and repeated visual peak cannot count twice`() {
        val inference = prepared()
        val audio = source("sound", 1.5, sound(), 2.8)
        val effect = source("visual", 2.0, visual(), 2.9)
        inference.accept(audio)
        inference.accept(source("overlap", 1.6, sound().copy(referenceCandidates = sound().referenceCandidates.map {
            it.copy(matchingOffsetNanos = it.matchingOffsetNanos - nanos(.1))
        }), 2.8))
        inference.accept(effect)
        assertEquals(1, inference.flush().size)
        inference.accept(audio)
        inference.accept(effect)
        inference.accept(source("same-peak", 2.05, visual(), 3.0))
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `published direct HP hit cannot be counted again by a delayed pair while a distinct later pair survives`() {
        val inference = prepared()
        inference.accept(source("loss", 2.0,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .2f, .1f, .1f), 2.1))
        assertEquals(1, inference.flush().size)
        inference.accept(source("late-sound", 1.5, sound(), 4.0))
        inference.accept(source("late-visual", 2.0, visual(), 4.1))
        assertTrue(inference.flush().isEmpty())
        inference.accept(source("next-sound", 3.0, sound(), 4.4))
        inference.accept(source("next-visual", 3.5, visual(), 4.5))
        assertEquals(listOf("next-visual"), inference.flush().map { it.observedArticle.id.value })
    }

    @Test fun `published pair absorbs late complementary HP but preserves a second independently measured loss`() {
        val inference = prepared()
        inference.accept(source("sound", 1.5, sound(), 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        assertEquals(1, inference.flush().size)
        inference.accept(source("late-loss", 2.01,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .2f, .1f, .1f), 4.0))
        inference.accept(source("late-border", 2.02,
            HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, .3f, 4), 4.1))
        assertTrue(inference.flush().isEmpty())
        inference.accept(source("distinct-loss", 2.4,
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, .1f, .05f, .05f), 4.2))
        assertEquals(listOf("distinct-loss"), inference.flush().map { it.observedArticle.id.value })
    }

    @Test fun `nearby HP on a replacement recipient cannot consume the outgoing recipients paired hit`() {
        for (publishFirst in listOf(false, true)) {
            val inference = prepared()
            inference.accept(source("sound", 1.5, sound(), 2.8))
            inference.accept(source("visual", 2.0, visual(), 2.9))
            val output = mutableListOf<FastMoveUseDerivation>()
            if (publishFirst) output += inference.flush()
            output += inference.accept(source("switch", 2.2,
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt"), 3.0))
            output += inference.accept(source("new-recipient-loss", 2.4,
                ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, 1f, .9f, .1f), 3.1))
            output += inference.flush()
            assertEquals(publishFirst.toString(), setOf("visual", "new-recipient-loss"),
                output.map { it.observedArticle.id.value }.toSet())
            assertEquals(2, output.size)
        }
    }

    @Test fun `stronger other-side sound remains confounded even with a recipient transient`() {
        val inference = prepared()
        val confounded = sound().copy(referenceCandidates = sound().referenceCandidates.map {
            if (it.moveName == "Incinerate") it.copy(similarity = .99f, changingSimilarity = .98f) else it
        })
        inference.accept(source("sound", 1.5, confounded, 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        assertTrue(inference.flush().isEmpty())
    }

    @Test fun `two distinct supported occurrences remain two uses`() {
        val inference = prepared()
        for (offset in listOf(0.0, 1.5)) {
            inference.accept(source("sound-$offset", 1.5 + offset, sound(), 2.8 + offset))
            inference.accept(source("visual-$offset", 2.0 + offset, visual(), 2.9 + offset))
        }
        assertEquals(2, inference.flush().size)
    }

    @Test fun `separate completed visual peaks remain separate within the ordinary merge tolerance`() {
        val inference = prepared()
        for (offset in listOf(0.0, .42)) {
            inference.accept(source("sound-$offset", 1.5 + offset, sound(), 2.8 + offset))
            inference.accept(source("visual-$offset", 2.0 + offset, visual(), 2.9 + offset))
        }
        assertEquals(2, inference.flush().size)
    }

    @Test fun `result flushes a supported terminal pair but result and audio alone never create one`() {
        val inference = prepared()
        inference.accept(source("sound", 1.5, sound(), 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        assertEquals(1, inference.accept(source("result", 2.8, MatchEnded(BattleResult.LOSS), 3.0)).size)
        val noVisual = prepared()
        noVisual.accept(source("sound", 1.5, sound(), 2.8))
        assertTrue(noVisual.accept(source("result", 2.8, MatchEnded(BattleResult.LOSS), 3.0)).isEmpty())
    }

    @Test fun `recipient faint after impact does not erase the matching sound tail`() {
        val inference = prepared()
        inference.accept(source("sound", 1.5, sound(), 2.8))
        inference.accept(source("visual", 2.0, visual(), 2.9))
        inference.accept(source("faint", 2.1,
            ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", basis = "EXPLICIT_TEST_FAINT_SEQUENCE"), 3.0))
        val uses = inference.accept(source("result", 2.8, MatchEnded(BattleResult.LOSS), 3.1))
        assertEquals(listOf("visual"), uses.map { it.observedArticle.id.value })
    }

    @Test fun `a composite kind without its paired sound is not sufficient evidence`() {
        assertFalse(FastMoveUseEvidence.establishesUse(listOf(FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT)))
        assertFalse(FastMoveUseEvidence.establishesUse(listOf(FastMoveUseEvidence.MATCHED_AUDIO)))
    }

    private fun prepared() = FastMoveUseInference().apply {
        accept(source("start", 0.0, MatchStarted))
        accept(source("player", .01, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        accept(source("opponent", .01, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Quagsire")))
    }

    private fun sound() = FastMoveSoundMeasured(true, nanos(.5), nanos(.4), 1000f, .4f,
        referenceCandidates = listOf(
            FastMoveSoundCandidate("Mud Shot", "mud", .97f, .95f, nanos(.5), nanos(.1), nanos(.4)),
            FastMoveSoundCandidate("Water Gun", "water", .86f, .80f, nanos(.5), nanos(.1), nanos(.4)),
            FastMoveSoundCandidate("Incinerate", "fire", .87f, .81f, nanos(.5), nanos(.1), nanos(.4)),
        ), referenceMatchAccepted = true, referenceSearchScopes = listOf(
            FastMoveSoundSearchScope(ActivePokemonSide.PLAYER, "Gourgeist (Average)", listOf("Incinerate"), listOf("player")),
            FastMoveSoundSearchScope(ActivePokemonSide.OPPONENT, "Quagsire", listOf("Mud Shot", "Water Gun"), listOf("opponent")),
        ))

    private fun visual(side: ActivePokemonSide = ActivePokemonSide.PLAYER) = FastMoveRecipientVisualArtifactMeasured(
        side, .2f, .3f, .7f, .6f, .4f, .5f, .5f, 40)
    private fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
    private fun source(id: String, seconds: Double, payload: TestimonyPayload, received: Double = seconds) = RealityArticle(
        ArticleId(id), (seconds * 1000).toLong(), (received * 1000).toLong(), SourceId("TEST"), payload,
        monotonicTimeNanos = nanos(seconds))
}
