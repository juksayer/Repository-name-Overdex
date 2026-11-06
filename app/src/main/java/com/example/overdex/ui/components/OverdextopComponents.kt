package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.OverdextopAction
import com.example.overdex.model.navigation.OverdextopController
import com.example.overdex.model.navigation.OverdextopEntry
import com.example.overdex.model.navigation.OverdextopIcon
import com.example.overdex.model.navigation.OverdextopSurface

private val DesktopPhosphor = Color(0xFF76ED91)
private val DesktopSelected = Color(0xFFCCFFD7)
private val DesktopRail = Color(0xFF367646)
private val DesktopSelectionWash = Color(0xFF122D1A)
private val DesktopTypeface = FontFamily(Font(R.font.share_tech_mono_regular))

/** Content for the existing CRT; the physical instrument header belongs to the shell. */
@Composable
fun OverdextopCrt(
    controller: OverdextopController,
    revealCount: Int = Int.MAX_VALUE,
    homeStyle: HomeScreenStyle = HomeScreenStyle.OVERDEXTOP,
    onEntryActivated: (OverdextopEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("overdextop-crt")) {
        Text(
            text = when (controller.surface) {
                OverdextopSurface.DESKTOP -> "overdextop"
                OverdextopSurface.PROGRAMS -> "programs"
                OverdextopSurface.SETTINGS -> "home screen"
            },
            fontFamily = DesktopTypeface,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            color = DesktopSelected,
            modifier = Modifier.padding(bottom = 9.dp),
        )
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(DesktopRail))
        Spacer(Modifier.height(12.dp))
        key(controller.surface) {
            if (controller.surface == OverdextopSurface.DESKTOP) {
                DesktopShortcuts(controller, revealCount, onEntryActivated)
            } else {
                DesktopProgramList(controller, homeStyle, onEntryActivated)
            }
        }
    }
}

@Composable
private fun DesktopShortcuts(
    controller: OverdextopController,
    revealCount: Int,
    onEntryActivated: (OverdextopEntry) -> Unit,
) {
    val revealedEntries = controller.entries.take(revealCount.coerceAtLeast(0))
    val shortcuts = revealedEntries.filter { it.icon != OverdextopIcon.PROGRAMS }
    val programs = revealedEntries.firstOrNull { it.icon == OverdextopIcon.PROGRAMS }
    Column(Modifier.fillMaxSize().testTag("overdextop-desktop")) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            shortcuts.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { entry ->
                        DesktopShortcut(
                            entry = entry,
                            isSelected = controller.selectedEntry.id == entry.id,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onActivate = { onEntryActivated(entry) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (programs != null) {
            Spacer(Modifier.height(12.dp))
            DesktopProgramRow(
                entry = programs,
                isSelected = controller.selectedEntry.id == programs.id,
                onActivate = { onEntryActivated(programs) },
            )
        }
    }
}

@Composable
private fun DesktopShortcut(
    entry: OverdextopEntry,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
    val color = if (isSelected) DesktopSelected else DesktopPhosphor
    Column(
        modifier = modifier
            .bringIntoViewRequester(requester)
            .testTag("overdextop-entry:${entry.id}")
            .background(if (isSelected) DesktopSelectionWash else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (isSelected) DesktopRail else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onActivate)
            .semantics(mergeDescendants = true) { selected = isSelected }
            .heightIn(min = 104.dp)
            .padding(horizontal = 3.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DesktopIcon(entry.icon, color, Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = entry.title,
            color = color,
            fontFamily = DesktopTypeface,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DesktopProgramList(
    controller: OverdextopController,
    homeStyle: HomeScreenStyle,
    onEntryActivated: (OverdextopEntry) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().testTag(
            if (controller.surface == OverdextopSurface.SETTINGS) "overdextop-settings" else "overdextop-programs"
        ).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        controller.entries.forEach { entry ->
            key(entry.id) {
                val currentStyle = (entry.action as? OverdextopAction.ChooseHomeStyle)?.style == homeStyle
                DesktopProgramRow(
                    entry = entry,
                    isSelected = controller.selectedEntry.id == entry.id,
                    current = currentStyle,
                    onActivate = { onEntryActivated(entry) },
                )
            }
        }
    }
}

@Composable
private fun DesktopProgramRow(
    entry: OverdextopEntry,
    isSelected: Boolean,
    current: Boolean = false,
    onActivate: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
    val color = if (isSelected) DesktopSelected else DesktopPhosphor
    Row(
        modifier = Modifier.fillMaxWidth()
            .bringIntoViewRequester(requester)
            .testTag("overdextop-entry:${entry.id}")
            .background(if (isSelected) DesktopSelectionWash else Color.Transparent, RoundedCornerShape(3.dp))
            .border(1.dp, if (isSelected) DesktopRail else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable(role = Role.Button, onClick = onActivate)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                if (current) stateDescription = "Current home screen"
            }
            .heightIn(min = 45.dp)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopIcon(entry.icon, color, Modifier.size(23.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.title,
            color = color,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontFamily = DesktopTypeface,
            modifier = Modifier.weight(1f),
        )
        if (current) {
            Text("✓", color = DesktopSelected, fontFamily = DesktopTypeface, fontSize = 17.sp,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** The LCD keeps direct-touch actions available while GlassShield protects the CRT. */
@Composable
fun OverdextopLcd(
    controller: OverdextopController,
    onOpen: () -> Unit,
    onPrograms: () -> Unit,
    onExplorer: () -> Unit,
    homeStyle: HomeScreenStyle,
) {
    val entry = controller.selectedEntry
    Column(Modifier.fillMaxSize().testTag("overdextop-lcd")) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .testTag("overdextop-lcd-summary"),
        ) {
            Text(
                text = entry.description,
                color = DesktopPhosphor,
                fontFamily = DesktopTypeface,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            if (controller.surface == OverdextopSurface.SETTINGS) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "current: ${if (homeStyle == HomeScreenStyle.OVERDEXTOP) "overdextop" else "directory"}",
                    color = DesktopSelected,
                    fontFamily = DesktopTypeface,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DesktopLcdButton("open", "overdextop-open", Modifier.weight(1f), onOpen)
            DesktopLcdButton("programs", "overdextop-programs-button", Modifier.weight(1f), onPrograms)
            DesktopLcdButton("Explorer", "overdextop-explorer-button", Modifier.weight(1f), onExplorer)
        }
    }
}

@Composable
private fun DesktopLcdButton(text: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().heightIn(min = 42.dp)
            .testTag(tag)
            .border(1.dp, DesktopRail, RoundedCornerShape(3.dp))
            .background(DesktopSelectionWash, RoundedCornerShape(3.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DesktopPhosphor, fontFamily = DesktopTypeface,
            fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

/** Outline instruments share the existing binder glyph's stroke and phosphor palette. */
@Composable
private fun DesktopIcon(icon: OverdextopIcon, color: Color, modifier: Modifier) {
    if (icon == OverdextopIcon.BINDER) {
        BinderDirectoryIcon(color, modifier)
        return
    }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 1.4f * unit)
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), stroke.width)
        fun rect(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color, point(x, y), Size(w * unit, h * unit), CornerRadius(unit), style = stroke)
        fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
        when (icon) {
            OverdextopIcon.DROIDBALL -> {
                // Launch symbol only; Droidball himself stays in the physical header.
                path { moveTo(8 * unit, 4 * unit); lineTo(19 * unit, 12 * unit)
                    lineTo(8 * unit, 20 * unit); close() }
                line(3f, 6f, 3f, 18f)
            }
            OverdextopIcon.POKEDEX -> {
                rect(4f, 2f, 16f, 20f)
                rect(7f, 9f, 10f, 7f)
                drawCircle(color, 1.4f * unit, point(8f, 6f), style = stroke)
                line(8f, 19f, 15f, 19f)
            }
            OverdextopIcon.TEAM -> {
                rect(1f, 7f, 6f, 13f); rect(9f, 3f, 6f, 17f); rect(17f, 7f, 6f, 13f)
                line(3f, 15f, 5f, 15f); line(11f, 11f, 13f, 11f); line(19f, 15f, 21f, 15f)
            }
            OverdextopIcon.HISTORY -> {
                drawCircle(color, 9f * unit, point(12f, 12f), style = stroke)
                line(12f, 6f, 12f, 12f); line(12f, 12f, 17f, 15f)
            }
            OverdextopIcon.EXPLORER -> path {
                moveTo(2 * unit, 20 * unit); lineTo(2 * unit, 5 * unit)
                lineTo(8 * unit, 5 * unit); lineTo(11 * unit, 8 * unit)
                lineTo(21 * unit, 8 * unit); lineTo(21 * unit, 10 * unit)
                moveTo(2 * unit, 20 * unit); lineTo(6 * unit, 11 * unit)
                lineTo(23 * unit, 11 * unit); lineTo(19 * unit, 20 * unit); close()
            }
            OverdextopIcon.PROGRAMS -> {
                rect(2f, 2f, 8f, 8f); rect(14f, 2f, 8f, 8f)
                rect(2f, 14f, 8f, 8f); rect(14f, 14f, 8f, 8f)
            }
            OverdextopIcon.RECORDS -> {
                rect(6f, 2f, 15f, 17f); line(3f, 6f, 3f, 22f); line(3f, 22f, 17f, 22f)
                line(9f, 7f, 18f, 7f); line(9f, 11f, 18f, 11f); line(9f, 15f, 15f, 15f)
            }
            OverdextopIcon.REPLAY -> {
                rect(2f, 4f, 20f, 16f)
                path { moveTo(9 * unit, 8 * unit); lineTo(16 * unit, 12 * unit)
                    lineTo(9 * unit, 16 * unit); close() }
            }
            OverdextopIcon.TOURNAMENT -> {
                line(2f, 3f, 7f, 3f); line(2f, 10f, 7f, 10f); line(7f, 3f, 7f, 10f)
                line(2f, 14f, 7f, 14f); line(2f, 21f, 7f, 21f); line(7f, 14f, 7f, 21f)
                line(7f, 6.5f, 15f, 6.5f); line(7f, 17.5f, 15f, 17.5f)
                line(15f, 6.5f, 15f, 17.5f); line(15f, 12f, 22f, 12f)
            }
            OverdextopIcon.PROFILE -> {
                drawCircle(color, 4f * unit, point(12f, 7f), style = stroke)
                path { moveTo(3 * unit, 22 * unit); cubicTo(3 * unit, 11 * unit,
                    21 * unit, 11 * unit, 21 * unit, 22 * unit) }
            }
            OverdextopIcon.RESEARCH -> {
                drawCircle(color, 7f * unit, point(10f, 10f), style = stroke)
                line(15f, 15f, 22f, 22f); line(6f, 10f, 8f, 7f)
                line(8f, 7f, 11f, 13f); line(11f, 13f, 14f, 9f)
            }
            OverdextopIcon.CALIBRATION -> {
                line(2f, 8f, 2f, 2f); line(2f, 2f, 8f, 2f)
                line(16f, 2f, 22f, 2f); line(22f, 2f, 22f, 8f)
                line(22f, 16f, 22f, 22f); line(22f, 22f, 16f, 22f)
                line(8f, 22f, 2f, 22f); line(2f, 22f, 2f, 16f)
                drawCircle(color, 4f * unit, point(12f, 12f), style = stroke)
                line(12f, 6f, 12f, 18f); line(6f, 12f, 18f, 12f)
            }
            OverdextopIcon.SETTINGS -> {
                line(2f, 5f, 22f, 5f); line(2f, 12f, 22f, 12f); line(2f, 19f, 22f, 19f)
                rect(6f, 2f, 3f, 6f); rect(15f, 9f, 3f, 6f); rect(7f, 16f, 3f, 6f)
            }
            OverdextopIcon.BINDER -> Unit
        }
    }
}
