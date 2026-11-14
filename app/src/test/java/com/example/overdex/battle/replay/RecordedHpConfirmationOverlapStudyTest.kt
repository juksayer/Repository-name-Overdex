package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Offline hypothesis comparison. Nothing here changes production or archived testimony.
 * Video review: Pixel recording 85.842 / 88.410 / 90.859 seconds has three losses.
 * Historically two extra border-only records were not separate impacts. The camera
 * transition guard removes one; geometry continuity prevents the other HP-only
 * recovery. Unsafe merge counterexamples remain as regression controls. */
class RecordedHpConfirmationOverlapStudyTest {
    @Test fun `recorded correction retains three impacts and experimental merges preserve provenance`() {
        val archive = pairedMirrorArchive("pixel")
        val original = MatchArchiveSerializer.serialize(archive)
        val model = pairedMirrorModel(archive)
        val start = requireNotNull(model.matchStartNanos)
        val field = model.javaClass.getDeclaredField("fastMoveCycles").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cycles = (field.get(model) as List<ReplayFastMoveCycle>).filter {
            it.witness.side == "OPPONENT" && it.witness.observedAtNanos in (start + ns(42.0))..(start + ns(50.0)) }
        val inputs = cycles.map { it.witness }
        val reversedModel = pairedMirrorModel(archive.copy(articles = archive.articles.reversed()))
        @Suppress("UNCHECKED_CAST")
        val reversed = (field.get(reversedModel) as List<ReplayFastMoveCycle>).filter {
            it.witness.side == "OPPONENT" && it.witness.observedAtNanos in (start + ns(42.0))..(start + ns(50.0)) }
        assertEquals(cycles, reversed)
        assertEquals(3, inputs.size)
        assertTrue(inputs.all { it.moveName == null && it.durationNanos == null })
        val losses = inputs.filter { it.damageTicks.isNotEmpty() }
        assertEquals(2, losses.size)
        val articles = archive.articles.associateBy { it.articleId }
        val confirmation = articles.getValue(CONFIRMATION)
        val pulse = inputs.single { BORDER_ONE in it.articleIds }
        assertTrue(articles.containsKey(CONFIRMATION))
        assertTrue(losses.none { CONFIRMATION in it.articleIds })
        assertEquals(confirmation.monotonicTimeNanos, pulse.observedAtNanos)
        assertEquals(.85106385f, (confirmation.payload as ArchivedActiveHpBarMeasured).filledFraction, 0f)
        assertEquals(0f, (articles.getValue(BORDER_ONE).payload as ArchivedHpBarBorderPulse).peakOrangeFraction!!, 0f)

        // Historical experiments are now no-ops on this repaired clip.
        // Their unsafe controls below still prohibit deploying a broad merge.
        val frameMerged = mergeConfirmation(inputs, articles)
        assertEquals(3, frameMerged.size)
        // Experiment B: wider complementary-evidence time matching fits this clip,
        // but the control below shows why this must not be shipped as a blanket rule.
        val wideMerged = mergeNearby(inputs)
        assertEquals(3, wideMerged.size)
        // Experiment C: video-labelled identity supplied ONLY to the existing scheduler.
        // This is an oracle diagnostic, not proof Pixel identified opponent Incinerate.
        val knownDuration = ReplayFastMoveSchedule.build(inputs.map {
            it.copy(moveName = "Incinerate", type = PokemonType.FIRE, durationNanos = ns(2.5))
        }).map { it.witness }
        // Forcing a duration now merges the delayed first pulse with a genuine
        // following hit. This is another reason not to repair timing by guessing.
        assertEquals(2, knownDuration.size)
        for (candidate in listOf(frameMerged, wideMerged, knownDuration)) {
            assertEquals(inputs.flatMap { it.articleIds }.toSet(), candidate.flatMap { it.articleIds }.toSet())
            assertEquals(inputs.flatMap { it.sourceTimesNanos }.toSet(), candidate.flatMap { it.sourceTimesNanos }.toSet())
            for (loss in losses) assertTrue(candidate.any { it.articleIds.containsAll(loss.articleIds) })
        }
        val directory = System.getenv("OVERDEX_HP_OVERLAP_STUDY")
        if (directory != null) File(directory, "candidate-comparison.tsv").printWriter().use { out ->
            out.println("approach\tcount\tsecondsAfterStart\tsourceArticleIds")
            for ((name, uses) in listOf("current" to inputs, "confirmation-frame-only" to frameMerged,
                    "wide-1.5s" to wideMerged, "video-labelled-Incinerate-duration" to knownDuration))
                for (use in uses) out.println("$name\t${uses.size}\t${(use.observedAtNanos-start)/1e9}\t${use.articleIds.sorted().joinToString(",")}")
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `wider merge window erases real half second attacks including a final hit`() {
        val inputs = listOf(
            witness("first", .5, "HP_DAMAGE_TICK"), witness("second", 1.0, "HP_BORDER_PULSE"),
            witness("third", 1.5, "HP_DAMAGE_TICK"), witness("finishing", 2.0, "HP_BORDER_PULSE")
                .copy(windowEndNanos = ns(2.0), endsAtTerminalBoundary = true),
        )
        // Different window end would correctly prevent a merge; use the common
        // known faint boundary for all witnesses of this same appearance.
        val bounded = inputs.map { it.copy(windowEndNanos = ns(2.0), endsAtTerminalBoundary = true) }
        assertEquals(2, mergeNearby(bounded).size)
        val faithful = ReplayFastMoveSchedule.build(bounded.map {
            it.copy(moveName = "Water Gun", type = PokemonType.WATER, durationNanos = ns(.5)) })
        assertEquals(4, faithful.size)
        assertEquals(ns(2.0), faithful.last().impactAtNanos)
        assertTrue("finishing" in faithful.last().witness.articleIds)
    }

    @Test fun `same HP confirmation frame cannot exclude a separate tiny hit`() {
        // A half-second move can hit again while the earlier decrease is being
        // confirmed. Its new damage can be below the measured pixel resolution.
        val first = witness("first", 1.0, "REPLAY_CORROBORATED_HP_STEP").copy(
            evidenceKinds = setOf("HP_DAMAGE_TICK", "REPLAY_CORROBORATED_HP_STEP"),
            articleIds = setOf("first", "confirmed"),
            damageTicks = listOf(ReplayFastMoveDamage(ns(1.0), .95f, .94f)))
        val second = witness("real-tiny-hit", 1.5, "HP_BORDER_PULSE")
        val confirmation = ArchivedRealityArticle("confirmed", "control", 0, 0, "TEST",
            ArchivedActiveHpBarMeasured("PLAYER", 40, 746, 232, 764, .94f),
            emptyList(), .99f, null, emptyList(), ns(1.5))
        assertEquals(1, mergeConfirmation(listOf(first, second), mapOf("confirmed" to confirmation)).size)
        val faithful = ReplayFastMoveSchedule.build(listOf(first, second).map {
            it.copy(moveName = "Water Gun", durationNanos = ns(.5)) })
        assertEquals(2, faithful.size)
        assertTrue("real-tiny-hit" in faithful.last().witness.articleIds)
    }

    @Test fun `experimental merges cannot cross sides or appearance and charge boundaries`() {
        val first = witness("before", 1.0, "HP_DAMAGE_TICK")
        val after = witness("after", 1.2, "HP_BORDER_PULSE")
        for (other in listOf(after.copy(side = "PLAYER"), after.copy(windowStartNanos = ns(1.1)),
                after.copy(windowEndNanos = ns(1.3)))) {
            assertEquals(2, mergeNearby(listOf(first, other)).size)
        }
    }

    private fun mergeConfirmation(inputs: List<ReplayFastMoveWitness>, articles: Map<String, ArchivedRealityArticle>) =
        merge(inputs) { prior, current ->
                "REPLAY_CORROBORATED_HP_STEP" in prior.evidenceKinds &&
                    "HP_BORDER_PULSE" in current.evidenceKinds && "HP_DAMAGE_TICK" !in current.evidenceKinds &&
                    prior.articleIds.any { id -> articles[id]?.let { a ->
                        a.payload is ArchivedActiveHpBarMeasured && a.monotonicTimeNanos == current.observedAtNanos &&
                            a.monotonicTimeNanos!! > prior.observedAtNanos
                    } == true }
            }

    private fun mergeNearby(inputs: List<ReplayFastMoveWitness>) = merge(inputs) { prior, current ->
        current.observedAtNanos - prior.observedAtNanos <= ns(1.5) &&
            (prior.evidenceKinds intersect current.evidenceKinds).none { it == "HP_DAMAGE_TICK" || it == "HP_BORDER_PULSE" }
    }
    private fun merge(inputs: List<ReplayFastMoveWitness>, matches: (ReplayFastMoveWitness, ReplayFastMoveWitness) -> Boolean): List<ReplayFastMoveWitness> {
        val result = mutableListOf<ReplayFastMoveWitness>()
        for (current in inputs.sortedBy { it.observedAtNanos }) {
            val prior = result.lastOrNull()
            if (prior != null && prior.side == current.side && prior.windowStartNanos == current.windowStartNanos &&
                prior.windowEndNanos == current.windowEndNanos && matches(prior, current)) {
                result[result.lastIndex] = prior.copy(evidenceKinds = prior.evidenceKinds + current.evidenceKinds,
                    articleIds = prior.articleIds + current.articleIds,
                    sourceTimesNanos = prior.sourceTimesNanos + current.sourceTimesNanos,
                    damageTicks = (prior.damageTicks + current.damageTicks).distinct())
            } else result += current
        }
        return result
    }
    private fun witness(id: String, at: Double, kind: String) = ReplayFastMoveWitness("OPPONENT", ns(at), null,
        null, null, 0L, ns(100.0), setOf(kind), setOf(id), setOf(ns(at)))
    private fun ns(seconds: Double) = (seconds * 1e9).toLong()
    private companion object {
        const val CONFIRMATION = "1a2e65d2-e12d-48e0-a400-ba56d32eb227"
        const val BORDER_ONE = "67aa33ee-b63e-4aa6-a97c-3beebc06ab7a"
    }
}
