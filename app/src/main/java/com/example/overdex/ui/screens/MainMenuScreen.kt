package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.pow
import kotlin.math.roundToInt

enum class MainMenuPhase {
    BOOT,
    MENU_BUILD,
    READY
}

@Composable
fun MainMenuScreen(
    hasBootedInSession: Boolean,
    onBootComplete: () -> Unit,
    visibleNodes: List<FlattenedNode> = emptyList(),
    selectedPath: String = "",
    trainerIdentity: TrainerIdentity? = null,
    onPhaseChange: (MainMenuPhase) -> Unit = {},
    onNodeSelected: (FlattenedNode) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Local state for the sequential lines
    var phase by remember(hasBootedInSession) {
        mutableStateOf(if (hasBootedInSession) MainMenuPhase.READY else MainMenuPhase.BOOT)
    }
    
    // Notify parent of initial phase
    SideEffect { onPhaseChange(phase) }

    var bootStep by remember(hasBootedInSession) { mutableIntStateOf(if (hasBootedInSession) 99 else 0) }
    var menuRevealCount by remember(hasBootedInSession, visibleNodes.size) {
        mutableIntStateOf(
            if (hasBootedInSession) visibleNodes.size else 0
        )
    }

    val bootLines = remember(trainerIdentity) {
        listOf(
            "overdex boot sequence...",
            "version 1.0.8",
            "",
            "checking local database.............. [ok]",
            "loading pokemon...................... [1025]",
            "loading move database................ [335]",
            "loading type effectiveness........... [ok]",
            "overdex ready",
        )
    }

    TerminalScreen {
        if (hasBootedInSession) {
            DirectoryTree(
                visibleNodes = visibleNodes,
                selectedPath = selectedPath,
                onNodeSelected = onNodeSelected,
            )
            return@TerminalScreen
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewportHeight = maxHeight
            val viewportHeightPx = with(density) { viewportHeight.roundToPx() }
            val currentVisibleNodes by rememberUpdatedState(visibleNodes)

            LaunchedEffect(viewportHeightPx) {
                if (viewportHeightPx <= 0) return@LaunchedEffect
                scrollState.scrollTo(0)

                // 1. BOOT Phase
                phase = MainMenuPhase.BOOT
                onPhaseChange(phase)
                for (i in 1..bootLines.size) {
                    bootStep = i
                    val baseDelay = if (i < 3) 400L else 100L
                    delay(baseDelay)
                }

                delay(200L)

                // 2. The tree already exists directly below the boot viewport.
                // Move the single surface by one viewport using explicit offsets;
                // Android's animator-duration setting cannot suppress this motion.
                phase = MainMenuPhase.MENU_BUILD
                onPhaseChange(phase)
                menuRevealCount = currentVisibleNodes.size
                val maximumOffset = snapshotFlow { scrollState.maxValue }.first { it > 0 }
                scrollBootSurface(
                    scrollState = scrollState,
                    destinationPx = viewportHeightPx.coerceAtMost(maximumOffset),
                )

                delay(120L)
                phase = MainMenuPhase.READY
                onPhaseChange(phase)
                onBootComplete()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewportHeight)
                ) {
                    bootLines.take(bootStep).forEach { line ->
                        TerminalText(
                            text = line,
                            color = TerminalDimGreen,
                            fontSize = 12.sp
                        )
                    }

                    if (bootStep >= bootLines.size) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TerminalText(
                            text = "TRAINER: ${trainerIdentity?.displayName?.uppercase() ?: "THEREALESTSQUID"}",
                            color = TerminalDimGreen,
                            fontSize = 12.sp
                        )
                        TerminalText(
                            text = "ID: ${trainerIdentity?.trainerId?.toString()?.take(8) ?: "737032186666"}",
                            color = TerminalDimGreen,
                            fontSize = 12.sp
                        )
                    }
                }

                DirectoryTree(
                    visibleNodes = visibleNodes.take(menuRevealCount),
                    selectedPath = "",
                    onNodeSelected = {},
                    modifier = Modifier.heightIn(min = viewportHeight),
                    scrollEnabled = false,
                )
            }
        }
    }
}

/**
 * Scrolls by assigning concrete offsets on each display frame. This is visual
 * motion, but it is deliberately outside Android and Compose animation APIs so
 * system animator-duration settings cannot turn the stitched transition off.
 */
private suspend fun scrollBootSurface(
    scrollState: androidx.compose.foundation.ScrollState,
    destinationPx: Int,
    durationNanos: Long = 720_000_000L,
) {
    if (destinationPx <= 0) return
    val startedAt = System.nanoTime()
    while (true) {
        val elapsed = System.nanoTime() - startedAt
        val linearProgress = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
        val easedProgress = 1.0 - (1.0 - linearProgress).pow(3.0)
        scrollState.scrollTo((destinationPx * easedProgress).roundToInt())
        if (linearProgress >= 1.0) break
        withFrameNanos { }
    }
}
