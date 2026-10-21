package com.example.overdex.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.ODXFi.InstrumentButton
import com.example.overdex.ui.components.*
import com.example.overdex.ui.screens.SpeciesSearchStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RegistrationNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listShowsPreviewRowsWhenSelectionMovesBothWays() {
        val selected = mutableIntStateOf(1)
        val fast = mutableStateOf(false)
        val direction = mutableIntStateOf(1)
        val data = flowOf(PagingData.from((1..1025).map {
            Pokemon(it, "Species $it", emptyList(), "", fastMoves = emptyList(), chargedMoves = emptyList())
        }))
        compose.setContent {
            val items = data.collectAsLazyPagingItems()
            Box(Modifier.size(340.dp, 440.dp)) {
                SpeciesSearchStep("", items, selected.intValue, RegistrationRegion.ALL,
                    direction.intValue, fast.value, onSelectedIndexChange = {})
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { selected.intValue = 50 }
        compose.onNodeWithText("Species 50").assertIsDisplayed()
        compose.onNodeWithText("Species 51").assertIsDisplayed()
        compose.runOnIdle { selected.intValue = 300; fast.value = true }
        compose.onNodeWithText("Species 300").assertIsDisplayed()
        compose.onNodeWithText("Species 303").assertIsDisplayed()
        compose.runOnIdle { selected.intValue = 200; direction.intValue = -1 }
        compose.onNodeWithText("Species 200").assertIsDisplayed()
        compose.onNodeWithText("Species 197").assertIsDisplayed()
    }

    @Test fun aHeldInstrumentButtonSurvivesRecompositionAndStopsOnRelease() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            var held by remember { mutableStateOf(false) }
            LaunchedEffect(held) {
                if (held) while (true) { count++; delay(80) }
            }
            InstrumentButton(label = "COUNT $count", modifier = Modifier.testTag("hold"),
                onClick = { count++ }, onLongClick = { held = true },
                onPressChanged = { if (!it) held = false })
        }
        compose.onNodeWithTag("hold").performTouchInput { longClick(durationMillis = 2000) }
        compose.waitForIdle()
        val released = count
        assertTrue("Recomposition interrupted the hold after $released updates", released > 8)
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(released, count) }
    }

    @Test fun endLeavesTheKeyboardVisibleAndTouchResumesTyping() {
        val keyboard = TerminalKeyboardController(keepVisibleAfterInput = true)
        keyboard.open()
        var text by mutableStateOf("")
        compose.setContent {
            Column {
                Text(if (keyboard.isFocused) "TYPING" else "BROWSING")
                if (keyboard.isVisible) TerminalKeyboard(keyboard.layout, keyboard.currentRow, keyboard.currentCol,
                    modifier = Modifier.size(300.dp, 150.dp), isFocused = keyboard.isFocused,
                    onKeyActivated = { keyboard.activateKey(it) { key -> text += key } })
            }
        }
        compose.onNodeWithContentDescription("End typing").performClick()
        compose.onNodeWithText("BROWSING").assertIsDisplayed()
        compose.onNodeWithText("S").assertIsDisplayed().performClick()
        compose.onNodeWithText("TYPING").assertIsDisplayed()
        compose.runOnIdle { assertEquals("S", text) }
    }
}
