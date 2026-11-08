package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.overdex.R
import com.example.overdex.model.navigation.DesktopPointerController
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.LauncherShortcutLocation
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
    pointerController: DesktopPointerController? = null,
    onEntryActivated: (OverdextopEntry) -> Unit,
) {
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val attachmentMotionBaseline = remember(pointerController) { pointerController?.motionRevision ?: 0L }
    DisposableEffect(pointerController, controller.surface) {
        pointerController?.beginSurface(controller.surface)
        pointerController?.centerOnTarget(controller.selectedPointerTargetId)
        onDispose { pointerController?.clearTargets() }
    }
    // Layout, scroll, and keyboard selection may change what lies under the
    // pointer. Only movement made during this attachment changes selection;
    // returning to the launcher must not consume an old movement revision.
    LaunchedEffect(pointerController?.motionRevision) {
        if (pointerController?.enabled == true && pointerController.motionRevision > attachmentMotionBaseline) {
            pointerController.hoveredTargetId?.let(controller::select)
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().testTag("overdextop-crt")
        .onGloballyPositioned { coordinates ->
            viewportCoordinates = coordinates
            pointerController?.setViewport(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        }) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = when (controller.surface) {
                    OverdextopSurface.DESKTOP -> "home"
                    OverdextopSurface.PROGRAMS -> "programs"
                    OverdextopSurface.SETTINGS -> "settings"
                    OverdextopSurface.SHORTCUTS -> "shortcuts"
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
                when (controller.surface) {
                    OverdextopSurface.DESKTOP -> DesktopShortcuts(
                        controller, revealCount, pointerController, viewportCoordinates, onEntryActivated)
                    OverdextopSurface.SHORTCUTS -> ShortcutSettingsList(
                        controller, pointerController, viewportCoordinates, onEntryActivated)
                    else -> DesktopProgramList(controller, homeStyle, pointerController, viewportCoordinates, onEntryActivated)
                }
            }
        }
        if (pointerController?.enabled == true) {
            DesktopCursor(pointerController)
        }
    }
}

@Composable
private fun DesktopShortcuts(
    controller: OverdextopController,
    revealCount: Int,
    pointerController: DesktopPointerController?,
    viewportCoordinates: LayoutCoordinates?,
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
                            pointerController = pointerController,
                            viewportCoordinates = viewportCoordinates,
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
                pointerController = pointerController,
                viewportCoordinates = viewportCoordinates,
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
    pointerController: DesktopPointerController?,
    viewportCoordinates: LayoutCoordinates?,
    onActivate: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
    val color = if (isSelected) DesktopSelected else DesktopPhosphor
    TrackPointerTargetLifetime(pointerController, entry.id)
    Column(
        modifier = modifier
            .desktopPointerTarget(pointerController, entry.id, viewportCoordinates)
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
    pointerController: DesktopPointerController?,
    viewportCoordinates: LayoutCoordinates?,
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
                    pointerController = pointerController,
                    viewportCoordinates = viewportCoordinates,
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
    pointerController: DesktopPointerController?,
    viewportCoordinates: LayoutCoordinates?,
    onActivate: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
    val color = if (isSelected) DesktopSelected else DesktopPhosphor
    TrackPointerTargetLifetime(pointerController, entry.id)
    Row(
        modifier = Modifier.fillMaxWidth()
            .desktopPointerTarget(pointerController, entry.id, viewportCoordinates)
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

@Composable
private fun ShortcutSettingsList(
    controller: OverdextopController,
    pointerController: DesktopPointerController?,
    viewportCoordinates: LayoutCoordinates?,
    onEntryActivated: (OverdextopEntry) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("overdextop-shortcuts")
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        controller.entries.forEach { entry ->
            key(entry.id) {
                val isSelected = controller.selectedEntry.id == entry.id
                val requester = remember { BringIntoViewRequester() }
                LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
                val visibility = controller.shortcutVisibility(entry.id)
                Column(
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester)
                        .testTag("overdextop-shortcut-row:${entry.id}")
                        .semantics { selected = isSelected }
                        .background(if (isSelected) DesktopSelectionWash else Color.Transparent, RoundedCornerShape(3.dp))
                        .padding(top = 7.dp, bottom = 3.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopIcon(entry.icon, DesktopPhosphor, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(entry.title, color = if (isSelected) DesktopSelected else DesktopPhosphor,
                            fontFamily = DesktopTypeface, fontSize = 15.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LauncherShortcutLocation.entries.forEach { location ->
                            val targetId = controller.shortcutTargetId(entry.id, location)
                            val editable = controller.shortcutLocationEditable(entry.id, location)
                            TrackPointerTargetLifetime(pointerController, targetId)
                            ShortcutLocationToggle(
                                checked = if (location == LauncherShortcutLocation.DESKTOP) visibility.desktop else visibility.programs,
                                location = location,
                                focused = isSelected && controller.selectedShortcutLocation == location,
                                editable = editable,
                                label = "${entry.title} on ${shortcutLocationLabel(location)}",
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .testTag("overdextop-shortcut:${entry.id}:${shortcutLocationLabel(location)}")
                                    .desktopPointerTarget(pointerController.takeIf { editable }, targetId, viewportCoordinates),
                                onToggle = {
                                    if (controller.select(targetId)) onEntryActivated(controller.selectedEntry)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shortcutLocationLabel(location: LauncherShortcutLocation): String = when (location) {
    LauncherShortcutLocation.DESKTOP -> "desktop"
    LauncherShortcutLocation.PROGRAMS -> "programs"
}

@Composable
private fun ShortcutLocationToggle(
    checked: Boolean,
    location: LauncherShortcutLocation,
    focused: Boolean,
    editable: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    showFixedLabel: Boolean = true,
    onToggle: () -> Unit,
) {
    val color = when {
        !editable -> DesktopPhosphor.copy(alpha = .72f)
        focused -> DesktopSelected
        else -> DesktopPhosphor
    }
    Row(
        modifier = modifier
            .border(1.dp, if (focused) DesktopRail else Color.Transparent, RoundedCornerShape(3.dp))
            .toggleable(value = checked, enabled = editable, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = label
                selected = focused
                if (!editable) stateDescription = "Always available"
            }
            .heightIn(min = 42.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(17.dp)) {
            drawRect(color, style = Stroke(1.5.dp.toPx()))
            if (checked) {
                drawPath(Path().apply {
                    moveTo(size.width * .18f, size.height * .51f)
                    lineTo(size.width * .42f, size.height * .74f)
                    lineTo(size.width * .84f, size.height * .25f)
                }, color, style = Stroke(1.8.dp.toPx()))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(shortcutLocationLabel(location), color = color, fontFamily = DesktopTypeface,
                fontSize = 12.sp, lineHeight = 15.sp)
            if (!editable && showFixedLabel) {
                Text("fixed", color = DesktopPhosphor.copy(alpha = .72f), fontFamily = DesktopTypeface,
                    fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun TrackPointerTargetLifetime(pointer: DesktopPointerController?, id: String) {
    DisposableEffect(pointer, id) {
        onDispose { pointer?.removeTarget(id) }
    }
}

private fun Modifier.desktopPointerTarget(
    pointer: DesktopPointerController?,
    id: String,
    viewport: LayoutCoordinates?,
): Modifier = if (pointer == null) this else onGloballyPositioned { coordinates ->
    if (viewport?.isAttached == true && coordinates.isAttached) {
        // Clipping matters: off-screen menu rows must not remain pointer targets.
        val bounds = viewport.localBoundingBoxOf(coordinates, clipBounds = true)
        if (bounds.width > 0f && bounds.height > 0f) {
            pointer.setTarget(id, bounds.left, bounds.top, bounds.right, bounds.bottom)
        } else {
            pointer.removeTarget(id)
        }
    }
}

@Composable
private fun DesktopCursor(pointer: DesktopPointerController) {
    // This overlay is decorative. A full-viewport accessibility label here
    // would mask the actual shortcuts and menu rows underneath it.
    Canvas(Modifier.fillMaxSize().zIndex(1f).testTag("overdextop-pointer")) {
        val x = pointer.pixelX
        val y = pointer.pixelY
        val unit = 1.dp.toPx()
        val directionX = if (x + 17f * unit > size.width) -1f else 1f
        val directionY = if (y + 23f * unit > size.height) -1f else 1f
        fun point(dx: Float, dy: Float) = Offset(x + dx * unit * directionX, y + dy * unit * directionY)
        val cursor = Path().apply {
            moveTo(x, y)
            point(0f, 19f).let { lineTo(it.x, it.y) }
            point(5f, 15f).let { lineTo(it.x, it.y) }
            point(9f, 23f).let { lineTo(it.x, it.y) }
            point(12f, 21f).let { lineTo(it.x, it.y) }
            point(8f, 13f).let { lineTo(it.x, it.y) }
            point(16f, 13f).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(cursor, Color(0xFF080F0B), style = Stroke(3f * unit))
        drawPath(cursor, DesktopSelected)
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
    trackpadMode: Boolean = false,
    onToggleTrackpad: () -> Unit = {},
    onPointerDrag: (deltaX: Float, deltaY: Float, padWidth: Float, padHeight: Float) -> Unit = { _, _, _, _ -> },
    onPointerTap: () -> Unit = {},
) {
    val entry = controller.selectedEntry
    val shortcutsOpen = controller.surface == OverdextopSurface.SHORTCUTS
    Column(Modifier.fillMaxSize().testTag("overdextop-lcd")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (trackpadMode || shortcutsOpen) {
                Box(
                    modifier = Modifier.testTag("overdextop-programs-button")
                        .clickable(role = Role.Button, onClick = onPrograms)
                        .heightIn(min = 30.dp).padding(horizontal = 5.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("programs", color = DesktopPhosphor, fontFamily = DesktopTypeface,
                        fontSize = 12.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier.testTag("overdextop-input-mode")
                    .clickable(role = Role.Button, onClick = onToggleTrackpad)
                    .semantics {
                        contentDescription = if (trackpadMode) "Show LCD context" else "Use LCD trackpad"
                        stateDescription = if (trackpadMode) "Trackpad" else "Context"
                    }
                    .heightIn(min = 30.dp).padding(horizontal = 5.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (trackpadMode) "context" else "trackpad",
                    color = DesktopSelected,
                    fontFamily = DesktopTypeface,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        if (trackpadMode) {
            DesktopTrackpad(Modifier.weight(1f).fillMaxWidth(), onPointerDrag, onPointerTap)
        } else if (shortcutsOpen) {
            val visibility = controller.shortcutVisibility(entry.id)
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("overdextop-lcd-shortcut-title")) {
                    Text(entry.title, color = DesktopSelected, fontFamily = DesktopTypeface,
                        fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LauncherShortcutLocation.entries.forEach { location ->
                        ShortcutLocationToggle(
                            checked = if (location == LauncherShortcutLocation.DESKTOP) visibility.desktop else visibility.programs,
                            location = location,
                            focused = controller.selectedShortcutLocation == location,
                            editable = controller.shortcutLocationEditable(entry.id, location),
                            label = "${entry.title} on ${shortcutLocationLabel(location)}",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .testTag("overdextop-lcd-shortcut-${shortcutLocationLabel(location)}"),
                            // The CRT already explains the locked recovery entry.
                            // Preserve LCD room for both controls at larger fonts.
                            showFixedLabel = false,
                            onToggle = {
                                if (controller.select(controller.shortcutTargetId(entry.id, location))) onOpen()
                            },
                        )
                    }
                }
            }
        } else {
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
                        text = "current: ${if (homeStyle == HomeScreenStyle.OVERDEXTOP) "desktop" else "directory"}",
                        color = DesktopSelected,
                        fontFamily = DesktopTypeface,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        if (!trackpadMode && !shortcutsOpen) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DesktopLcdButton("open", "overdextop-open", Modifier.weight(1f), onOpen)
                DesktopLcdButton("programs", "overdextop-programs-button", Modifier.weight(1f), onPrograms)
                DesktopLcdButton("Explorer", "overdextop-explorer-button", Modifier.weight(1f), onExplorer)
            }
        }
    }
}

@Composable
private fun DesktopTrackpad(
    modifier: Modifier,
    onDrag: (Float, Float, Float, Float) -> Unit,
    onTap: () -> Unit,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier.testTag("overdextop-trackpad")
            .background(DesktopSelectionWash, RoundedCornerShape(3.dp))
            .semantics { contentDescription = "LCD trackpad" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun insidePad(position: Offset): Boolean =
                        position.x >= 0f && position.y >= 0f &&
                            position.x < size.width && position.y < size.height

                    val down = awaitFirstDown()
                    var cancelled = !insidePad(down.position)
                    down.consume()
                    var lastPosition = down.position
                    var accumulated = Offset.Zero
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Compose retains a down's hit path outside its original
                        // bounds. Leaving the pad cancels this entire contact,
                        // even if it re-enters; never forward outside movement or
                        // turn its release into a tap. Keep owning it until up so
                        // crossing the CRT or shell controls cannot activate them.
                        if (change.isConsumed || !insidePad(change.position) ||
                            change.historical.any { !insidePad(it.position) }) cancelled = true
                        change.consume()
                        if (cancelled) {
                            if (!change.pressed) break
                            continue
                        }
                        val delta = change.position - lastPosition
                        lastPosition = change.position
                        accumulated += delta
                        if (!dragging && accumulated.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            // Include movement before crossing touch slop, rather than
                            // dropping a significant part of a short LCD gesture.
                            currentOnDrag(accumulated.x, accumulated.y, size.width.toFloat(), size.height.toFloat())
                        } else if (dragging) {
                            if (delta != Offset.Zero) {
                                currentOnDrag(delta.x, delta.y, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                        if (!change.pressed) {
                            if (!dragging) {
                                currentOnTap()
                            }
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("trackpad", color = DesktopRail, fontFamily = DesktopTypeface, fontSize = 12.sp)
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
    if (icon == OverdextopIcon.JOURNAL) {
        JournalDirectoryIcon(color, modifier)
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
            OverdextopIcon.BINDER, OverdextopIcon.JOURNAL -> Unit
        }
    }
}
