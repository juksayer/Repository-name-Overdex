package com.example.overdex.battle.inference

import org.junit.Assert.*
import org.junit.Test

class VerifiedHpFaintResolverTest {
    private val identity = VerifiedHpFaintObservation.Identity("identity", 1 * S, "Tinkaton", 959, true)
    private val red = hp("red", 2 * S, .15f, "RED")
    private val empty = hp("empty", 3 * S, 0f, "EMPTY").copy(top = 692, bottom = 709)
    private val prefix = listOf(identity, red, empty)

    @Test fun `one verified empty frame resolves in capture order with its original owner and evidence`() {
        val expected = VerifiedHpFaintResolution("empty", 3 * S,
            TeamLossCandidate("identity", S, "Tinkaton", 959), listOf("identity", "red", "empty"))
        for (events in listOf(prefix, prefix.reversed(), listOf(empty, identity, red), prefix + prefix))
            assertEquals(listOf(expected), VerifiedHpFaintResolver.resolve(events))
    }

    @Test fun `zero without verified fill or fresh strict red never creates a faint`() {
        val badAnchors = listOf(red.copy(color = null), red.copy(color = "YELLOW"), red.copy(fraction = .5f),
            red.copy(confidence = .79f), red.copy(trackedBattlefieldSource = false))
        for (anchor in badAnchors) assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, anchor, empty)).isEmpty())
        for (zero in listOf(empty.copy(color = null), empty.copy(color = "RED"), empty.copy(fraction = .01f),
            empty.copy(confidence = null), empty.copy(trackedBattlefieldSource = false),
            empty.copy(at = red.at + 3 * S)))
            assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, red, zero)).isEmpty())
        assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, empty)).isEmpty())
    }

    @Test fun `missing bar and later unrelated geometry cannot supply zero`() {
        assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, red)).isEmpty())
        for (zero in listOf(empty.copy(left = 300, right = 493), empty.copy(top = 900, bottom = 917),
            empty.copy(width = 1080, height = 2400), empty.copy(width = null),
            empty.copy(right = 560), empty.copy(bottom = 720), empty.copy(fraction = Float.NaN)))
            assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, red, zero)).isEmpty())
    }

    @Test fun `ordinary healthy or displaced reading disarms and repeated red does not extend the window`() {
        val ordinary = hp("ordinary", 2500000000L, .8f, null)
        assertTrue(VerifiedHpFaintResolver.resolve(prefix + ordinary).isEmpty())
        assertTrue(VerifiedHpFaintResolver.resolve(prefix + ordinary.copy(fraction = .1f, left = 300, right = 493)).isEmpty())
        val repeated = red.copy(id = "another-red", at = 4 * S)
        assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity, red, repeated, empty.copy(at = 5 * S))).isEmpty())
    }

    @Test fun `switch or charge boundary cannot transfer an old bar to the replacement`() {
        val replacement = identity.copy(id = "replacement", at = 2500000000L, name = "Carbink", speciesId = 703)
        for (boundary in listOf<VerifiedHpFaintObservation>(replacement,
            VerifiedHpFaintObservation.Boundary("charge", 2500000000L),
            replacement.copy(independent = false)))
            assertTrue(VerifiedHpFaintResolver.resolve(prefix + boundary).isEmpty())
        // Repeated same-species badge OCR is not a new combatant.
        assertEquals(1, VerifiedHpFaintResolver.resolve(prefix + identity.copy(id = "repeat", at = 2500000000L)).size)
    }

    @Test fun `charge geometry resets but the known actor can acquire a fresh red bar afterward`() {
        val charge = VerifiedHpFaintObservation.Boundary("charge", 1500000000L, clearIdentity = false)
        assertEquals(1, VerifiedHpFaintResolver.resolve(prefix + charge).size)
        assertTrue(VerifiedHpFaintResolver.resolve(prefix + charge.copy(at = 2500000000L)).isEmpty())
        val repeat = identity.copy(id = "announcement", at = 1700000000L, independent = false)
        assertEquals(1, VerifiedHpFaintResolver.resolve(prefix + listOf(charge, repeat)).size)
    }

    @Test fun `later independent positive HP retracts the KO even across a switch away and return`() {
        val returnIdentity = identity.copy(id = "return", at = 6 * S)
        val alive = hp("alive", 7 * S, .17f, null)
        val events = prefix + listOf(identity.copy(id = "other", at = 4 * S, name = "Carbink", speciesId = 703),
            returnIdentity, alive)
        for (ordered in listOf(events, events.reversed())) assertTrue(VerifiedHpFaintResolver.resolve(ordered).isEmpty())
        assertEquals("Positive HP alone may belong to an unidentified replacement", 1,
            VerifiedHpFaintResolver.resolve(prefix + alive).size)
        assertEquals(1, VerifiedHpFaintResolver.resolve(prefix + returnIdentity).size)
        // Survival of another actor is not survival of Tinkaton.
        assertEquals(1, VerifiedHpFaintResolver.resolve(prefix + listOf(
            returnIdentity.copy(name = "Carbink", speciesId = 703), alive)).size)
    }

    @Test fun `ambiguous identities conflicting IDs and same frame disagreement remain unresolved`() {
        for (extra in listOf<VerifiedHpFaintObservation>(
            identity.copy(id = "ambiguous", name = "Carbink", speciesId = 703),
            identity.copy(name = "Carbink", speciesId = 703),
            identity.copy(id = "later-name-conflict", at = 8 * S, speciesId = 703),
            identity.copy(id = "later-id-conflict", at = 8 * S, name = "Carbink"),
            empty.copy(fraction = .1f),
            empty.copy(id = "same-frame", fraction = .1f),
        )) {
            assertTrue(VerifiedHpFaintResolver.resolve(prefix + extra).isEmpty())
            assertTrue(VerifiedHpFaintResolver.resolve((prefix + extra).reversed()).isEmpty())
        }
        assertTrue(VerifiedHpFaintResolver.resolve(listOf(identity.copy(independent = false), red, empty)).isEmpty())
    }

    @Test fun `restarting observation discards the old attempt and cannot bridge bars`() {
        assertTrue(VerifiedHpFaintResolver.resolve(prefix +
            VerifiedHpFaintObservation.Boundary("restart", 4 * S, true)).isEmpty())
        assertTrue(VerifiedHpFaintResolver.resolve(prefix +
            VerifiedHpFaintObservation.Boundary("restart", 2500000000L, true)).isEmpty())
    }

    private fun hp(id: String, at: Long, fraction: Float, color: String?) = VerifiedHpFaintObservation.Hp(
        id, at, 394, 677, 587, 694, fraction, color, 672, 1493, .95f, true)

    private companion object { const val S = 1_000_000_000L }
}
