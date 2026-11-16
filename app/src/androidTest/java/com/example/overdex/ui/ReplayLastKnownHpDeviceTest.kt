package com.example.overdex.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.overdex.battle.replay.ReplayHpState
import com.example.overdex.ui.screens.observatory.ReplayHpBar
import org.junit.Rule
import org.junit.Test

class ReplayLastKnownHpDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun staleHpRemainsVisibleAndIsNotReportedAsCurrent() {
        compose.setContent { ReplayHpBar(ReplayHpState(filledFraction = .25f, currentHpConfirmed = false), "Opponent HP") }
        compose.onNodeWithContentDescription("Opponent HP").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Last seen 25 percent; current HP unknown"))
        compose.onNodeWithText("?").assertDoesNotExist()
    }
}
