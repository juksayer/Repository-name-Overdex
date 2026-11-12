package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

/** The recorded cue delays exercise scheduling only; synthetic geometry does not assert a recovered KO. */
class EarlyChargeHpPreparationTest {
    @Test fun `recorded recognition completion can prepare capture before delayed typed publication`() {
        val watch = seeded()
        val handoff = AcceptedChargeCapturePreparation("match")
        var result: ChargedImpactHpWatchPolicy.PreparationResult? = null
        handoff.attach { cue ->
            result = watch.prepare(ActivePokemonSide.PLAYER, cue.capturedAtNanos, cue.cropArticleId)
        }
        // First evening match: incoming source +139.664; OCR completion +146.443;
        // typed publication +149.172. These are distinct clocks, not shifted testimony.
        assertNull(watch.request(672, 1493, ms(146_400)))
        handoff.offer("ANNOUNCEMENT_WITNESS", BattleCropContracts.announcement,
            crop(), "Attack incoming!", ms(146_443))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED, result)
        val early = requireNotNull(watch.request(672, 1493, ms(146_500)))
        assertEquals(ActivePokemonSide.PLAYER, early.side)
        assertEquals(listOf(CROP_ID), early.cueArticleIds)
        assertTrue(watch.canCapture(ms(146_500)))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.DUPLICATE_OR_OLDER,
            watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), "later-typed-cue"))
        assertTrue(watch.current(early))
        assertNotNull(watch.request(672, 1493, ms(149_172)))
        assertNull("Publication delay must not renew the source deadline",
            watch.request(672, 1493, ms(151_665)))
    }

    @Test fun `early camera measurement still requires complete geometry and cannot become a fast move`() {
        val watch = seeded()
        watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), CROP_ID)
        val at = ms(146_500)
        val request = requireNotNull(watch.request(672, 1493, at))
        val centered = ActiveHpBarMeasurement(239, 763, 432, 780, .1f, .97f)
        assertFalse(watch.accept(request, TrackedTerminalHpBarSample(
            centered.copy(right = 300), HpBarFillColor.EMPTY), at))
        assertTrue(watch.accept(request, TrackedTerminalHpBarSample(centered, HpBarFillColor.RED), at))
        assertFalse(watch.fastMoveEvidenceAllowed(ActivePokemonSide.PLAYER, centered, at))
        assertEquals(listOf(CROP_ID), watch.cameraCueIds(ActivePokemonSide.PLAYER, centered, at))
        val tracked = requireNotNull(watch.request(672, 1493, at + 33_000_000))
        assertEquals(centered, tracked.anchor)
    }

    @Test fun `late old-window hints do not reopen after switch stop expiry or resize`() {
        for (boundary in listOf("SWITCH", "STOP", "RESIZE", "EXPIRED")) {
            val watch = seeded()
            when (boundary) {
                "SWITCH" -> watch.reset(ActivePokemonSide.PLAYER, ms(145_000))
                "STOP" -> watch.resetAll(ms(145_000))
                "RESIZE" -> watch.request(1080, 2400, ms(145_000))
                "EXPIRED" -> watch.request(672, 1493, ms(152_000))
            }
            assertNotEquals(boundary, ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
                watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), CROP_ID))
            assertFalse(boundary, watch.canCapture(ms(152_100)))
            assertNull(boundary, watch.request(672, 1493, ms(152_100)))
        }
    }

    @Test fun `failed preparation states remain explicit and diagnostics do not consume a scan`() {
        val none = ChargedImpactHpWatchPolicy()
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            none.prepare(ActivePokemonSide.PLAYER, ms(139_664), CROP_ID))
        val stale = seeded()
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.STALE_REFERENCE,
            stale.prepare(ActivePokemonSide.PLAYER, ms(146_000), CROP_ID))
        val watch = seeded()
        watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), CROP_ID)
        val before = watch.preparationDescription()
        assertTrue(before.contains("referenceAt=${ms(139_000)}"))
        assertTrue(before.contains("until=${ms(151_664)}"))
        assertEquals(before, watch.preparationDescription())
        assertNotNull(watch.request(672, 1493, ms(146_500)))
    }

    @Test fun `canonical delivery retries failed early preparation with newly completed pre-cue geometry`() {
        for (staleReference in listOf(false, true)) {
            val watch = if (staleReference) seeded() else ChargedImpactHpWatchPolicy().apply {
                request(672, 1493, ms(138_000))
            }
            val at = ms(146_000)
            assertEquals(if (staleReference) ChargedImpactHpWatchPolicy.PreparationResult.STALE_REFERENCE
                else ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
                watch.prepare(ActivePokemonSide.PLAYER, at, "early"))
            watch.seed(ActivePokemonSide.PLAYER,
                ActiveHpBarMeasurement(35, 625, 228, 642, .5f, .95f), at - ms(100))
            assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
                watch.prepare(ActivePokemonSide.PLAYER, at, "canonical"))
            val request = requireNotNull(watch.request(672, 1493, at + ms(2100)))
            assertEquals(listOf("canonical"), request.cueArticleIds)
            val empty = ActiveHpBarMeasurement(239, 763, 432, 780, 0f, .97f)
            assertTrue(watch.accept(request, TrackedTerminalHpBarSample(empty, HpBarFillColor.EMPTY), at + ms(2100)))
            assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.DUPLICATE_OR_OLDER,
                watch.prepare(ActivePokemonSide.PLAYER, at, "repeated"))
            assertFalse(watch.canCapture(at + ms(2200)))
        }
    }

    @Test fun `failed newer phase invalidates opposite recipient and cannot reopen an older cue`() {
        val watch = seeded()
        watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), "incoming")
        val old = requireNotNull(watch.request(672, 1493, ms(141_700)))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
            watch.prepare(ActivePokemonSide.OPPONENT, ms(142_000), "early-ready"))
        assertFalse(watch.current(old))
        assertFalse(watch.canCapture(ms(144_100)))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.DUPLICATE_OR_OLDER,
            watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), "delayed-incoming"))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.DUPLICATE_OR_OLDER,
            watch.prepare(ActivePokemonSide.PLAYER, ms(142_000), "wrong-side"))
        watch.seed(ActivePokemonSide.OPPONENT,
            ActiveHpBarMeasurement(395, 645, 588, 662, .4f, .95f), ms(141_900))
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
            watch.prepare(ActivePokemonSide.OPPONENT, ms(142_000), "canonical-ready"))
        assertEquals(ActivePokemonSide.OPPONENT,
            requireNotNull(watch.request(672, 1493, ms(144_100))).side)
    }

    @Test fun `failed preparation cannot retry across lifecycle geometry newer-cue or expiry boundaries`() {
        for (boundary in listOf("SWITCH", "STOP", "RESIZE", "EXPIRED", "NEWER_CUE")) {
            val watch = ChargedImpactHpWatchPolicy()
            watch.request(672, 1493, ms(138_000))
            watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), "early")
            when (boundary) {
                "SWITCH" -> watch.reset(ActivePokemonSide.PLAYER, ms(145_000))
                "STOP" -> watch.resetAll(ms(145_000))
                "RESIZE" -> watch.request(1080, 2400, ms(145_000))
                "EXPIRED" -> watch.request(672, 1493, ms(152_000))
                "NEWER_CUE" -> watch.prepare(ActivePokemonSide.OPPONENT, ms(140_000), "newer")
            }
            watch.seed(ActivePokemonSide.PLAYER,
                ActiveHpBarMeasurement(35, 625, 228, 642, .5f, .95f), ms(139_000))
            assertNotEquals(boundary, ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
                watch.prepare(ActivePokemonSide.PLAYER, ms(139_664), "canonical"))
            assertFalse(boundary, watch.canCapture(ms(152_100)))
        }
    }

    private fun seeded() = ChargedImpactHpWatchPolicy().apply {
        request(672, 1493, ms(138_000))
        seed(ActivePokemonSide.PLAYER, ActiveHpBarMeasurement(35, 625, 228, 642, .83f, .95f), ms(139_000))
    }

    private fun crop() = RealityArticle(ArticleId(CROP_ID), 1000, 1100, SourceId("ANNOUNCEMENT_CAPTURE"),
        CropCaptured(CropArtifactReference("artifacts/crops/phase.png", "a".repeat(64), 1),
            BattleCropProvenance(BattleCropContracts.announcement.cropName, 672, 1493,
                BattleCropBounds(100, 300, 550, 450))),
        matchId = MatchId("match"), monotonicTimeNanos = ms(139_664))

    private fun ms(value: Long) = value * 1_000_000L
    private companion object { const val CROP_ID = "d1148925-06e1-4c47-90d6-141d30b5b064" }
}
