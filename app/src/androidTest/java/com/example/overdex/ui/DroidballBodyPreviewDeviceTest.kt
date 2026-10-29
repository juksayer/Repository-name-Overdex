package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.presentation.DroidballChoreography
import com.example.overdex.presentation.DroidballJourneyPhase
import com.example.overdex.presentation.DroidballPose
import com.example.overdex.ui.components.DroidballBody
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual production Canvas artwork only. This is not battle-recognition validation. */
class DroidballBodyPreviewDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun savesSharedBodyPoseContactSheet() {
        // This isolated artwork activity can render over a locked device without
        // unlocking it or displaying the trainer's application data.
        compose.runOnUiThread {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                compose.activity.setShowWhenLocked(true)
                compose.activity.setTurnScreenOn(true)
            }
        }
        val poses = listOf(
            "DOCKED" to DroidballPose(),
            "EAGER" to pose(DroidballJourneyPhase.DEPARTING, 220),
            "BLINK" to pose(DroidballJourneyPhase.DEPARTING, 272),
            "EJECT" to pose(DroidballJourneyPhase.DEPARTING, 688),
            "DETACHED" to pose(DroidballJourneyPhase.DEPARTING, 1040),
            "ROLLING OUT" to pose(DroidballJourneyPhase.DEPARTING, 1280),
            "ARRIVAL" to pose(DroidballJourneyPhase.ARRIVING, 0),
            "SKID" to pose(DroidballJourneyPhase.ARRIVING, 903),
            "EYES RELAX" to pose(DroidballJourneyPhase.ARRIVING, 1420),
            "SCAN" to pose(DroidballJourneyPhase.ARRIVING, 1565),
            "FIELD DOCK" to pose(DroidballJourneyPhase.FIELD, 0),
            "RELUCTANT" to pose(DroidballJourneyPhase.RETURNING, 170),
            "RETURN ROLL" to pose(DroidballJourneyPhase.RETURNING, 400),
            "COMING HOME" to pose(DroidballJourneyPhase.DOCKING, 400),
            "PRESS IN" to pose(DroidballJourneyPhase.DOCKING, 798),
            "HOME" to pose(DroidballJourneyPhase.DOCKED, 0),
        )
        compose.setContent {
            val pixelWidth = LocalConfiguration.current.screenWidthDp * LocalDensity.current.density
            // Fit a fixed composition to the phone while keeping all sixteen bodies large.
            // At 1080px width the captured contact sheet is 1080 x 1485 pixels.
            CompositionLocalProvider(LocalDensity provides Density(pixelWidth / 480f, fontScale = 1f)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF263137)), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.width(480.dp).height(660.dp).background(Color(0xFFE2E6E5))
                            .testTag("droidball-preview"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("DROIDBALL / SHARED VECTOR BODY", color = Color(0xFF263137),
                            fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 3.dp))
                        Text("Centered poses · shell, field and return", color = Color(0xFF52636A),
                            fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
                        poses.chunked(4).forEachIndexed { row, items ->
                            Row(Modifier.width(480.dp).height(148.dp), horizontalArrangement = Arrangement.Center) {
                                items.forEachIndexed { column, (label, value) ->
                                    Column(
                                        Modifier.width(120.dp).height(148.dp)
                                            .background(if ((row + column) % 2 == 0) Color(0xFFE2E6E5) else Color(0xFFD5DDDC)),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(label, fontSize = 10.sp, color = Color(0xFF263137),
                                            modifier = Modifier.padding(top = 7.dp, bottom = 3.dp))
                                        DroidballBody(Modifier.size(112.dp), value.copy(xBodies = 0f),
                                            contentDescription = label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(target.filesDir, "droidball-preview.png")
        val pixels = compose.onNodeWithTag("droidball-preview").captureToImage().asAndroidBitmap()
        destination.outputStream().use { stream ->
            assertTrue("PNG compression completed", pixels.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Contact sheet was preserved", destination.length() > 1000)
    }

    private fun pose(phase: DroidballJourneyPhase, at: Long) = DroidballChoreography.pose(phase, at)
}
