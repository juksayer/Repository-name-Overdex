package com.example.overdex.ui.ODXFi

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPanelDimensionsTest {
    @Test fun `browsing keeps the original compact console across viewport heights`() {
        listOf(230, 360, 480, 800, 1200).forEach { height ->
            listOf(false, true).forEach { binder ->
                assertEquals(
                    KeyboardPanelDimensions(170.dp, 56.dp, 36.dp),
                    keyboardPanelDimensions(height.dp, false, binder),
                )
            }
        }
    }

    @Test fun `opening the keyboard never resizes the console or its side controls`() {
        listOf(230, 360, 480, 800, 1200).forEach { height ->
            listOf(false, true).forEach { binder ->
                assertEquals(
                    keyboardPanelDimensions(height.dp, false, binder),
                    keyboardPanelDimensions(height.dp, true, binder),
                )
            }
        }
    }

    @Test fun `each fixed control column fits four buttons inside the console padding`() {
        val panel = keyboardPanelDimensions(800.dp, true, false)
        assertEquals(56.dp, panel.controlWidth)
        assertEquals(36.dp, panel.controlHeight)
        assertTrue(panel.controlHeight * 4 + 16.dp <= panel.panelHeight)
    }
}
