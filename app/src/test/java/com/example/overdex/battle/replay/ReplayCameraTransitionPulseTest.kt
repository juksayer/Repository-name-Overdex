package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayCameraTransitionPulseTest {
    private val source = pairedMirrorArchive("pixel")
    private val pulse = source.articles.single { it.articleId == PULSE }
    private val at = pulse.monotonicTimeNanos!!
    private val rows = source.articles.filter { a ->
        val p = a.payload
        (p is ArchivedActiveHpBarMeasured && p.side == "PLAYER" &&
            a.monotonicTimeNanos!! in (at - 2_000_000_000)..(at + 3_000_000_000)) ||
            a.articleId == PULSE || (p is ArchivedChargeMoveUsedAnnounced &&
                a.monotonicTimeNanos!! in at..(at + 10_000_000_000))
    }
    private fun qualify(a: List<ArchivedRealityArticle> = rows, same: Boolean = true) =
        ReplayCameraTransitionPulse(a, { _, _ -> same }, { true })

    @Test fun `recorded camera excursion rejects just its border comparison with auditable evidence`() {
        val decision = qualify().decisions.getValue(PULSE)
        assertTrue(decision.evidenceArticleIds.contains(PULSE))
        assertTrue(decision.evidenceArticleIds.contains("8afdf67c-a54a-4e65-b358-71620f0fcd2e"))
        assertTrue(decision.evidenceArticleIds.any { id -> rows.any { it.articleId == id && it.payload is ArchivedChargeMoveUsedAnnounced } })
        assertEquals(qualify().decisions, qualify(rows.reversed()).decisions)
    }

    @Test fun `missing phase geometry or continuity never excludes a pulse`() {
        val hp = rows.filter { it.payload is ArchivedActiveHpBarMeasured }
        val baseline = hp.filter { it.monotonicTimeNanos!! < at }
        val onward = hp.filter { it.monotonicTimeNanos!! > at }
        val cases = listOf(
            rows.filterNot { it.payload is ArchivedChargeMoveUsedAnnounced },
            rows.filterNot { it in baseline.dropLast(2) },
            rows.filterNot { it.articleId == "021433c5-9343-4186-a6f1-2bc0ac1c04d4" },
            rows.filterNot { it in onward && (it.payload as ArchivedActiveHpBarMeasured).barLeft == 42 },
            rows + hp.first().copy(articleId = "conflicting", payload = (hp.first().payload as ArchivedActiveHpBarMeasured).copy(barLeft = 60)),
            rows.map { if (it in onward) it.copy(payload = (it.payload as ArchivedActiveHpBarMeasured).copy(sourceWidth = 1080)) else it },
            rows + pulse.copy(articleId = "faint", payload = ArchivedActivePokemonFaintedWitnessed("OPPONENT", basis = "TEST")),
            rows.map { if (it.monotonicTimeNanos!! < at) it.copy(monotonicTimeNanos = it.monotonicTimeNanos!! - 2_000_000_000) else it },
        )
        cases.forEachIndexed { index, input -> assertTrue("case=$index", qualify(input).decisions.isEmpty()) }
        assertTrue(qualify(same = false).decisions.isEmpty())
        assertTrue(ReplayCameraTransitionPulse(rows, { _, _ -> true }, { false }).decisions.isEmpty())
    }

    @Test fun `stationary blue tiny hit and vertical bob are retained even with nearby charge`() {
        val anchor = rows.filter { it.monotonicTimeNanos!! < at }.last().payload as ArchivedActiveHpBarMeasured
        for (bob in listOf(0, 2, 60)) {
            val input = rows.map { a -> if (a.payload is ArchivedActiveHpBarMeasured && a.monotonicTimeNanos!! >= at)
                a.copy(payload = anchor.copy(barTop = anchor.barTop + bob, barBottom = anchor.barBottom + bob,
                    filledFraction = .005f)) else a }
            assertTrue(qualify(input).decisions.isEmpty())
        }
    }

    @Test fun `new HP plateau during camera motion leaves the pulse eligible`() {
        val input = rows.map { a -> if (a.payload is ArchivedActiveHpBarMeasured && a.monotonicTimeNanos!! >= at)
            a.copy(payload = a.payload.copy(filledFraction = .53f)) else a }
        assertTrue(qualify(input).decisions.isEmpty())
    }

    @Test fun `complete paired replays retain three real losses and evidence while removing the camera use`() {
        for (device in listOf("pixel", "moondrop")) {
            val archive = pairedMirrorArchive(device)
            val original = MatchArchiveSerializer.serialize(archive)
            val model = pairedMirrorModel(archive)
            val uses = cycles(model)
            if (device == "pixel") {
                val start = model.matchStartNanos!!
                val segment = uses.filter { it.witness.side == "OPPONENT" &&
                    it.witness.observedAtNanos in (start + 42_000_000_000)..(start + 50_000_000_000) }
                assertEquals(3, segment.size)
                assertEquals(2, segment.count { it.witness.damageTicks.isNotEmpty() })
                assertEquals(setOf(45_987_806_759L, 48_344_304_182L),
                    segment.filter { it.witness.damageTicks.isNotEmpty() }.map { it.impactAtNanos - start }.toSet())
                assertEquals(setOf(PULSE), model.cameraTransitionPulseDecisions.keys)
                assertFalse(uses.any { PULSE in it.witness.articleIds || USE in it.witness.articleIds })
                assertTrue(segment.any { "67aa33ee-b63e-4aa6-a97c-3beebc06ab7a" in it.witness.articleIds })
                assertEquals(uses, cycles(pairedMirrorModel(archive.copy(articles = archive.articles.reversed()))))
            } else {
                assertTrue(model.cameraTransitionPulseDecisions.isEmpty())
                assertTrue(ReplayCameraTransitionPulse(archive.articles, { _, _ -> true }, { true }).decisions.isEmpty())
            }
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `independent same frame HP damage keeps the real attack and all its references`() {
        val tick = source.articles.first { it.payload is ArchivedActiveHpBarDamageTickMeasured }.copy(
            articleId = "independent-hit", monotonicTimeNanos = at,
            payload = ArchivedActiveHpBarDamageTickMeasured("PLAYER", .58f, .563158f, .016842f))
        val canonical = source.articles.single { it.articleId == USE }
        val changed = canonical.copy(predecessorIds = canonical.predecessorIds + tick.articleId,
            payload = (canonical.payload as ArchivedFastMoveUseObserved).copy(evidenceKinds = listOf("HP_BORDER_PULSE", "HP_DAMAGE_TICK")))
        val input = source.copy(articles = source.articles.map { if (it.articleId == USE) changed else it } + tick)
        val before = MatchArchiveSerializer.serialize(input)
        val retained = cycles(pairedMirrorModel(input)).single { USE in it.witness.articleIds }.witness
        assertTrue(retained.articleIds.containsAll(listOf(PULSE, USE, tick.articleId)))
        assertTrue("HP_DAMAGE_TICK" in retained.evidenceKinds)
        assertFalse("HP_BORDER_PULSE" in retained.evidenceKinds)
        assertEquals(before, MatchArchiveSerializer.serialize(input))
    }

    @Test fun `matched audio and visual support survives and absent pulse provenance cannot be guessed`() {
        val canonical = source.articles.single { it.articleId == USE }
        val payload = canonical.payload as ArchivedFastMoveUseObserved
        val variants = listOf(
            canonical.copy(payload = payload.copy(evidenceKinds = listOf("HP_BORDER_PULSE",
                "RECIPIENT_VISUAL_ARTIFACT_WITH_MATCHED_AUDIO", "SIDE_ATTRIBUTED_AUDIO_REFERENCE"))),
            canonical.copy(predecessorIds = emptyList()),
        )
        for ((index, replacement) in variants.withIndex()) {
            val input = source.copy(articles = source.articles.map { if (it.articleId == USE) replacement else it })
            val retained = cycles(pairedMirrorModel(input)).single { USE in it.witness.articleIds }.witness
            assertEquals(index == 1, "HP_BORDER_PULSE" in retained.evidenceKinds)
        }
    }

    private fun cycles(model: MatchReplayModel): List<ReplayFastMoveCycle> {
        val field = model.javaClass.getDeclaredField("fastMoveCycles").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return field.get(model) as List<ReplayFastMoveCycle>
    }
    private companion object {
        const val PULSE = "f666daf2-9a01-4901-8232-a0e34201337c"
        const val USE = "4e02b3d2-8d07-4f8d-a32c-06a7e5790637"
    }
}
