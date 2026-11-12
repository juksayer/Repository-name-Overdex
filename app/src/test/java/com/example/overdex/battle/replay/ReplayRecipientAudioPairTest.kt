package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import org.junit.Assert.*
import org.junit.Test

class ReplayRecipientAudioPairTest {
    @Test fun `unfused sources replay one neutral supported attack before confirmed faint without changing archive`() {
        val archive = archive(listOf(sound(), visual(), event("faint", 2.2,
            ArchivedActivePokemonFaintedWitnessed("PLAYER", "Gourgeist", 711, "EXPLICIT_TEST"))))
        val original = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val model = MatchReplayModel(input)
            val attacks = actions(model)
            assertEquals(1, attacks.map { it.sourceArticleIds }.distinct().size)
            assertTrue(attacks.all { it.side == "OPPONENT" && it.moveName == null })
            assertTrue(attacks.all { it.sourceArticleIds.containsAll(listOf("visual", "sound")) })
            assertTrue(attacks.any { FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT in it.evidenceKinds })
            assertTrue(model.sceneAt(nanos(2.3)).player!!.isFainted)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `raw HP hit canonical use and paired sources never add a duplicate attack`() {
        val hp = event("pulse", 2.01, ArchivedHpBarBorderPulse("PLAYER", "PRESENT"))
        val canonical = event("use", 2.0, ArchivedFastMoveUseObserved("paired", "OPPONENT", "PLAYER",
            evidenceKinds = listOf(FastMoveUseEvidence.RECIPIENT_AUDIO_IMPACT, FastMoveUseEvidence.MATCHED_AUDIO),
            basis = "FUSED_SIDE_ATTRIBUTED_FAST_MOVE_EVIDENCE")).copy(predecessorIds = listOf("visual", "sound"))
        for (existing in listOf(hp, canonical)) {
            val attacks = actions(MatchReplayModel(archive(listOf(sound(), visual(), existing))))
            assertEquals(1, attacks.map { it.startedAtNanos }.distinct().size)
        }
    }

    @Test fun `audio without recipient impact and charged impact remain unresolved`() {
        for (sources in listOf(listOf(sound()), listOf(sound(), visual(), event("charge", 1.8, ArchivedChargeMoveUsedAnnounced)))) {
            assertTrue(actions(MatchReplayModel(archive(sources))).isEmpty())
        }
    }

    @Test fun `onset after visual cannot cross either combatants switch faint or a new charge`() {
        val boundaries = listOf<ArchivedTestimonyPayload>(
            ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Whiscash", 340),
            ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323),
            ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Quagsire", 195, "EXPLICIT_TEST"),
            ArchivedActivePokemonFaintedWitnessed("PLAYER", "Gourgeist", 711, "EXPLICIT_TEST"),
            ArchivedGetReadyWitnessed, ArchivedChargeMoveUsedAnnounced,
        )
        val laterOnset = sound().copy(monotonicTimeNanos = nanos(1.9), perceivedAt = 1900)
        for (boundary in boundaries) {
            val source = archive(listOf(laterOnset, visual(), event("later-boundary", 2.1, boundary)))
            for (input in listOf(source, source.copy(articles = source.articles.reversed()))) {
                assertTrue(boundary.toString(), actions(MatchReplayModel(input)).isEmpty())
            }
        }
    }

    @Test fun `later onset in the same normal exchange still produces one replay action`() {
        val laterOnset = sound().copy(monotonicTimeNanos = nanos(1.9), perceivedAt = 1900)
        val observed = actions(MatchReplayModel(archive(listOf(laterOnset, visual()))))
        assertEquals(1, observed.map { it.sourceArticleIds }.distinct().size)
    }

    @Test fun `nearby HP on replacement recipient cannot erase the outgoing recipients paired hit`() {
        val source = archive(listOf(sound(), visual(),
            event("switch", 2.2, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
            event("new-recipient-loss", 2.4, ArchivedActiveHpBarDamageTickMeasured("PLAYER", 1f, .9f, .1f))))
        for (input in listOf(source, source.copy(articles = source.articles.reversed()))) {
            val observed = actions(MatchReplayModel(input)).distinctBy { it.sourceArticleIds }
            assertEquals(2, observed.size)
            assertTrue(observed.any { "visual" in it.sourceArticleIds })
            assertTrue(observed.any { "new-recipient-loss" in it.sourceArticleIds })
            assertTrue(observed.none { "visual" in it.sourceArticleIds && "new-recipient-loss" in it.sourceArticleIds })
        }
    }

    @Test fun `mirrored legal pools cannot create a side attributed replay attack`() {
        val audio = sound()
        val p = audio.payload as ArchivedFastMoveSoundMeasured
        val mirrored = audio.copy(payload = p.copy(referenceSearchScopes = p.referenceSearchScopes.map {
            if (it.side == "PLAYER") it.copy(moveNames = listOf("Mud Shot", "Incinerate")) else it
        }))
        assertTrue(actions(MatchReplayModel(archive(listOf(mirrored, visual())))).isEmpty())
    }

    private fun archive(events: List<ArchivedRealityArticle>) = MatchArchive(matchId = "pair", articles = listOf(
        event("start", 0.0, ArchivedMatchStarted),
        event("player", .01, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711)),
        event("opponent", .01, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Quagsire", 195)),
    ) + events + event("end", 3.0, ArchivedMatchEnded("LOSS")))

    private fun actions(model: MatchReplayModel) = (1_500L..2_500L step 25L)
        .flatMap { model.sceneAt(it * 1_000_000L).fastMoveActions }

    private fun sound() = event("sound", 1.5, ArchivedFastMoveSoundMeasured(true, nanos(.5), nanos(.4), 1000f, .4f,
        referenceCandidates = listOf(
            ArchivedFastMoveSoundCandidate("Mud Shot", "mud", .97f, .95f, nanos(.5), nanos(.1), nanos(.4)),
            ArchivedFastMoveSoundCandidate("Water Gun", "water", .86f, .80f, nanos(.5), nanos(.1), nanos(.4)),
            ArchivedFastMoveSoundCandidate("Incinerate", "fire", .87f, .81f, nanos(.5), nanos(.1), nanos(.4)),
        ), referenceMatchAccepted = true, referenceSearchScopes = listOf(
            ArchivedFastMoveSoundSearchScope("PLAYER", "Gourgeist (Average)", listOf("Incinerate"), listOf("player")),
            ArchivedFastMoveSoundSearchScope("OPPONENT", "Quagsire", listOf("Mud Shot", "Water Gun"), listOf("opponent")),
        )))
    private fun visual() = event("visual", 2.0,
        ArchivedFastMoveRecipientVisualArtifactMeasured("PLAYER", .2f, .3f, .7f, .6f, .4f, .5f, .5f, 40))
    private fun event(id: String, seconds: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "pair", (seconds * 1000).toLong(), 3000, "TEST", payload, emptyList(), null, null, emptyList(), nanos(seconds))
    private fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
}
