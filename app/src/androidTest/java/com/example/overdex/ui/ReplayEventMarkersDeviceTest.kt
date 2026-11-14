package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.replay.ReplayEventMarker
import com.example.overdex.battle.replay.ReplayEventMarkerKind
import com.example.overdex.ui.screens.observatory.ReplayEventMarkerControls
import com.example.overdex.ui.screens.observatory.ReplayEventMarkerStrip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** LCD-sized controls must expose densely packed events without precise scrubber taps. */
class ReplayEventMarkersDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun simultaneousEventsCanBeSelectedAndSoughtIndividuallyOnCompactLcd() {
        val markers = listOf(
            ReplayEventMarker("go", ReplayEventMarkerKind.MATCH_START, 0, null, "Match start · GO"),
            ReplayEventMarker("charge", ReplayEventMarkerKind.CHARGED_MOVE, 4_000_000_000, "PLAYER", "Charged move · Thunder Punch"),
            ReplayEventMarker("shield", ReplayEventMarkerKind.SHIELD, 4_000_000_000, "OPPONENT", "Shield used"),
            ReplayEventMarker("faint", ReplayEventMarkerKind.FAINT, 6_000_000_000, "OPPONENT", "Faint · Swampert"),
        )
        var cursor by mutableLongStateOf(0)
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            Column(Modifier.width(220.dp).background(Color.Black)) {
                ReplayEventMarkerStrip(markers, 0, 8_000_000_000, Modifier.width(220.dp).height(16.dp))
                ReplayEventMarkerControls(markers, cursor, selected, 0, onSeek = {
                    selected = it.id
                    cursor = it.atNanos
                }, nextSelected = true)
            }
        }
        val previous = compose.onNodeWithContentDescription("Previous replay event")
        val next = compose.onNodeWithContentDescription("Next replay event")
        previous.assertIsDisplayed().assertIsNotEnabled()
        next.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).performClick()
        compose.runOnIdle {
            assertEquals("charge", selected)
            assertEquals(4_000_000_000, cursor)
        }
        compose.onNodeWithContentDescription("Replay event details").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "YOU · Charged move · Thunder Punch"))
        next.performClick()
        compose.runOnIdle {
            assertEquals("shield", selected)
            assertEquals(4_000_000_000, cursor)
        }
        compose.onNodeWithContentDescription("Replay event details").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "OPP · Shield used"))
        next.performClick()
        next.assertIsNotEnabled()
        previous.performClick()
        compose.runOnIdle { assertEquals("shield", selected) }
    }

    @Test fun fastMoveDotsAreSmallerThanChargedMoveMarkers() {
        compose.setContent {
            ReplayEventMarkerStrip(listOf(
                ReplayEventMarker("fast", ReplayEventMarkerKind.FAST_MOVE, 2, "PLAYER", "Fast move"),
                ReplayEventMarker("charge", ReplayEventMarkerKind.CHARGED_MOVE, 6, "PLAYER", "Charged move"),
            ), 0, 8, Modifier.width(220.dp).height(16.dp).background(Color.Black))
        }
        val pixels = compose.onNodeWithContentDescription("Replay event markers").captureToImage().toPixelMap()
        fun illuminated(from: Int, until: Int): Int {
            var count=0
            for (x in from until until) for (y in 0 until pixels.height) {
                val color=pixels[x,y]
                if (maxOf(color.red,color.green,color.blue) > .1f) count++
            }
            return count
        }
        val fast=illuminated(0,pixels.width/2)
        val charge=illuminated(pixels.width/2,pixels.width)
        assertTrue("Fast marker must remain visible", fast>0)
        assertTrue("Charge marker must be clearly larger: fast=$fast charge=$charge", charge>fast*2)
    }

    @Test fun anArchiveWithoutSupportedEventsHasNoInventedSeekTarget() {
        compose.setContent {
            ReplayEventMarkerControls(emptyList(), 0, null, 0, onSeek = { error("No destination exists") })
        }
        compose.onNodeWithContentDescription("Previous replay event").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next replay event").assertIsNotEnabled()
    }
}
