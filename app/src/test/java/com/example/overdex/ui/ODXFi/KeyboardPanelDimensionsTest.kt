package com.example.overdex.ui.ODXFi

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPanelDimensionsTest {
    @Test fun `browsing keeps the existing panel and controls at any screen height`() {
        listOf(230, 360, 800).forEach { height ->
            listOf(false, true).forEach { binder ->
                assertEquals(
                    KeyboardPanelDimensions(170.dp, 56.dp, 36.dp),
                    keyboardPanelDimensions(height.dp, false, binder),
                )
            }
        }
    }

    @Test fun `typing expands the panel and gives controls 48 dp targets`() {
        assertEquals(
            KeyboardPanelDimensions(390.dp, 48.dp, 48.dp, true),
            keyboardPanelDimensions(800.dp, true, false),
        )
        assertEquals(
            KeyboardPanelDimensions(420.dp, 48.dp, 48.dp),
            keyboardPanelDimensions(800.dp, true, true),
        )
    }

    @Test fun `medium height reserves space for the CRT instead of clipping it`() {
        assertEquals(280.dp, keyboardPanelDimensions(480.dp, true, false).panelHeight)
        assertEquals(244.dp, keyboardPanelDimensions(480.dp, true, true).panelHeight)
    }

    @Test fun `short screen keeps both control rows reachable and leaves a CRT preview`() {
        assertEquals(
            KeyboardPanelDimensions(220.dp, 48.dp, 48.dp, true),
            keyboardPanelDimensions(360.dp, true, false),
        )
    }

    @Test fun `exceptionally short viewport fits controls inside its remaining height`() {
        val panel = keyboardPanelDimensions(230.dp, true, true)
        assertEquals(90.dp, panel.panelHeight)
        assertEquals(18.5.dp, panel.controlHeight)
        assertTrue(panel.controlHeight * 4 + 16.dp <= panel.panelHeight)
    }
}
