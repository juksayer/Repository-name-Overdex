package com.example.overdex.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MainMenuScreen(
    isServiceRunning: Boolean,
    onModuleSelect: (String) -> Unit,
    onShutdown: () -> Unit
) {
    var hasBooted by rememberSaveable { mutableStateOf(false) }
    var bootStep by remember { mutableIntStateOf(if (hasBooted) 11 else 0) }
    var screenHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val bootLines = listOf(
        "overdex boot sequence...",
        "version 1.0.8",
        "",
        "initializing confidence engine...",
        "checking local database.............. [ok]",
        "loading pokemon...................... [1025]",
        "loading move database................ [894]",
        "loading type effectiveness........... [ok]",
        "loading user profile................. [ok]",
        "confidence engine.................... [ready]",
        "confidence level..................... [high]"
    )

    val transitionProgress = remember { Animatable(if (hasBooted) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasBooted) {
            bootLines.indices.forEach { index ->
                bootStep = index + 1
                val baseDelay = if (index < 2) 400L else 150L
                delay(baseDelay)
            }
            delay(800)
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
            hasBooted = true
        }
    }

    val bootOffsetY = -screenHeightPx * transitionProgress.value
    val menuOffsetY = screenHeightPx * (1f - transitionProgress.value)

    TerminalScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { screenHeightPx = it.size.height.toFloat() }
        ) {
            // BootLayer
            if (transitionProgress.value < 1f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, bootOffsetY.roundToInt()) }
                ) {
                    bootLines.take(bootStep).forEach { line ->
                        TerminalText(
                            text = line,
                            color = TerminalDimGreen,
                            fontSize = 12.sp
                        )
                    }

                    if (bootStep >= bootLines.size) {
                        Spacer(modifier = Modifier.height(24.dp))
                        TerminalHeader(text = "system check")
                        TerminalText(text = "> overlay_permission              [ ok ]", fontSize = 14.sp)
                        TerminalText(text = "> battery_optimization            [ ok ]", fontSize = 14.sp)
                        TerminalText(text = "> pogo_data_status                [ ok ]", fontSize = 14.sp)
                    }
                }
            }

            // InteractiveMenuLayer
            if (transitionProgress.value > 0f || hasBooted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, menuOffsetY.roundToInt()) }
                        .verticalScroll(rememberScrollState())
                ) {
                    TerminalText(
                        text = "overdex terminal",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                    TerminalText(
                        text = "status: active",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )

                    TerminalSection(title = "modules") {
                        TerminalMenuOption(label = "overdex") { onModuleSelect("overdex") }

                        if (!isServiceRunning) {
                            TerminalMenuOption(label = "start.service") { onModuleSelect("start.service") }
                        } else {
                            TerminalMenuOption(label = "stop.service") { onModuleSelect("stop.service") }
                        }

                        TerminalMenuOption(label = "review.kit") { onModuleSelect("review.kit") }
                        TerminalMenuOption(label = "battle.log") { onModuleSelect("battle.log") }
                        TerminalMenuOption(label = "more.info") { onModuleSelect("more.info") }
                        TerminalMenuOption(label = "readme.txt") { onModuleSelect("readme.txt") }
                        TerminalMenuOption(label = "settings") { onModuleSelect("settings") }
                    }

                    TerminalSection(title = "session") {
                        TerminalMenuOption(label = "shutdown.droidball") { onShutdown() }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(32.dp))

                    TerminalText(
                        text = "built for SMNAPvP",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                    TerminalText(
                        text = "(c) 2026 som_labs",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
