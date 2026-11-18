package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.MatchArchivePackageReader
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.LiveAnnouncementPreview
import com.example.overdex.battle.observation.ResolvedBattleCrop
import java.util.zip.ZipFile
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.ChargeTextOverlayProtection
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.ReplayRetainedAudioRecovery
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.BattleOverlay
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ChargeHudVisibilityDeviceTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() = compose.runOnIdle { DroidballOverlayPresentation.reset() }

    @Test fun visibleWordsDistinguishTheTwoRecordedCameruptBanners() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedMoondrop") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "artifacts/frames/sha256")
        val banners = mapOf(
            "Rock" to "93fa1cd6e8bc03e9e9861ed3b2b2ad5601b15043e7daf0d746cf03fbf5ec0c86",
            "Earth" to "c2ca1c30e67d1fda86cfcbc3e27de19614c99a8230b3484cb69abb7f436c9d6d",
        )
        for ((word, digest) in banners) {
            val frame = File(folder, "$digest.png")
            assertEquals(digest, ReplayRetainedAudioRecovery.sha(frame.readBytes()))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(frame.path))
            try {
                // Same native source strip in both 672px frames. The old HUD overlaps
                // the end of each announcement; the left portion is unobstructed.
                suspend fun readTo(right: Int): String {
                    val strip = Bitmap.createBitmap(bitmap, 50, 321, right - 50, 56)
                    return try { SharedLatinTextRecognizer.readText(strip) } finally { strip.recycle() }
                }
                val whole = readTo(622)
                val clear = readTo(443)
                Log.i("OverdexFieldBanner", "$word whole=[$whole] clear=[$clear]")
                assertTrue("Visible $word banner: $clear", clear.contains("used $word", ignoreCase = true))
                val other = banners.keys.single { it != word }
                assertFalse(clear.contains(other, ignoreCase = true))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun savedEarthPowerAndEntryBannersHideTheListBeforeOcr() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedHudBanners") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "match_archives/2026-11-18_16-16-50_5e39cb12-af19-4841-8d2d-3a63539666e8.odxmatch.zip")
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val visible = mutableListOf<Long>()
        val preview = LiveAnnouncementPreview(visible::add)
        // Two Earth Power frames, Get Ready, Rock Tomb and two entrances, plus
        // normal sky with HUD lettering, empty arena and a black transition.
        val expected = mapOf("86b0d3c" to true, "a10baa2" to true, "ca39482" to true, "ac372f1" to true,
            "0a6d738" to true, "5eda2e6" to true, "a8e7d0d" to false, "2d5d54f" to false,
            "380ded5" to false, "9e6cdec" to false)
        val checked = mutableSetOf<String>()
        ZipFile(file).use { zip ->
            for (row in archive.articles) {
                val p = row.payload as? ArchivedCropCaptured ?: continue
                val key = p.sha256.take(7)
                if (key !in expected || p.cropName != "AnnouncementCrop") continue
                val at = requireNotNull(row.monotonicTimeNanos)
                val bitmap = zip.getInputStream(zip.getEntry(p.artifactPath)).use { BitmapFactory.decodeStream(it) }
                try {
                    val provenance = BattleCropProvenance(p.cropName, p.sourceWidth, p.sourceHeight,
                        BattleCropBounds(p.cropLeft, p.cropTop, p.cropRight, p.cropBottom))
                    preview.inspect(ResolvedBattleCrop(provenance, bitmap), at, p.overlayBounds)
                    assertEquals("Source crop $key", expected.getValue(key), at in visible)
                    assertTrue(preview.readingFor(at, provenance) != null)
                    checked += key
                } finally { bitmap.recycle() }
            }
        }
        assertEquals(expected.keys, checked)
    }

    @Test fun moveLabelsHideThroughImpactAndReturnAtTheirOriginalPosition() {
        val hud = DroidballOverlayPresentation
        compose.runOnUiThread {
            hud.reset()
            hud.showAcceptedTeamSelect()
            hud.showBattleHud()
            hud.recordBattleBadgePresent()
            hud.recordOpponentSpecies("Vaporeon", 134, listOf("Water Gun" to PokemonType.WATER),
                listOf("Liquidation" to PokemonType.WATER, "Last Resort" to PokemonType.NORMAL))
        }
        compose.setContent { BattleOverlay() }
        val sprite = compose.onNodeWithContentDescription("Vaporeon sprite", useUnmergedTree = true)
        val move = compose.onNodeWithContentDescription("Liquidation, water type", useUnmergedTree = true)
        val originalSprite = sprite.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val originalMove = move.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("move-type:Liquidation", true).assertWidthIsEqualTo(15.dp)
        compose.onNodeWithTag("move-hazard:Liquidation", true).assertWidthIsEqualTo(15.dp)
        val custody = InMemoryTestimonyCustody()
        val match = Match("field-hud-test", custody = custody, realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        val observer = ChargeTextOverlayProtection()
        fun cue(payload: TestimonyPayload, source: String) {
            custody.submitTestimony(SourceId(source), payload, System.currentTimeMillis(), 1f, emptyList(), System.nanoTime())
        }
        try {
            observer.start(match)
            cue(GetReadyWitnessed, "GET_READY_WITNESS")
            compose.waitUntil(5_000) { hud.chargeTextProtected.value }
            move.assertDoesNotExist()
            assertEquals(originalSprite, sprite.fetchSemanticsNode().boundsInRoot)
            cue(RawTestimony("GREAT\nLqutdation"), "QTE_PROGRESS_TEXT_WITNESS")
            cue(ChargeMoveUsedAnnounced, "CHARGE_MOVE_USED_WITNESS")
            runBlocking { match.awaitAcceptedEvidencePublished() }
            move.assertDoesNotExist()
            observer.battlefieldReturned(System.nanoTime())
            move.assertDoesNotExist()
            cue(FastMoveUseObserved("ordinary-use", ActivePokemonSide.OPPONENT, ActivePokemonSide.PLAYER,
                evidenceKinds = listOf("HP_DAMAGE_TICK")), "FAST_MOVE_USE_INFERENCE")
            compose.waitUntil(5_000) { !hud.chargeTextProtected.value }
            assertEquals(originalSprite, sprite.fetchSemanticsNode().boundsInRoot)
            assertEquals(originalMove, move.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        } finally {
            observer.stop()
            match.release()
        }
    }
}
