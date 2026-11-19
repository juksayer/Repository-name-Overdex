package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.battle.replay.ReplayEventMarker
import com.example.overdex.battle.replay.ReplayEventMarkerKind
import com.example.overdex.battle.replay.ReplayEventMarkerNavigation
import com.example.overdex.battle.replay.ReplayFaintTiming

/** Stable renderer for the separate LCD surface; its content follows screen state. */
@Composable
internal fun PublishMatchLcd(
    publish: ((@Composable () -> Unit)?) -> Unit,
    content: @Composable () -> Unit
) {
    val latestContent = rememberUpdatedState(content)
    val latestPublish = rememberUpdatedState(publish)
    val renderer: @Composable () -> Unit = remember { { latestContent.value() } }
    SideEffect { publish(renderer) }
    DisposableEffect(Unit) { onDispose { latestPublish.value(null) } }
}

@Composable
internal fun MatchLcdColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
internal fun MatchLcdText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = TerminalGreen, fontFamily = FontFamily.Monospace,
        fontSize = 10.sp, lineHeight = 12.sp)
}

@Composable
internal fun MatchLcdButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)
            .bringIntoViewRequester(requester).semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, TerminalGreen.copy(alpha = if (selected) 1f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalGreen,
            containerColor = TerminalGreen.copy(alpha = if (selected) 0.15f else 0f))) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
    }
}

/** Compact LCD control: padded text, with the entire allocated cell tappable. */
@Composable
internal fun MatchLcdTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Box(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(label, color = TerminalGreen.copy(alpha = if (enabled) 1f else .35f), fontSize = 10.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, softWrap = false)
    }
}

/** Category shapes share the scrubber; player events sit above opponent events. GO is drawn separately in purple. */
@Composable
internal fun ReplayEventMarkerStrip(
    markers: List<ReplayEventMarker>,
    startNanos: Long,
    endNanos: Long,
    modifier: Modifier = Modifier,
) {
    val events = markers.filter { it.kind != ReplayEventMarkerKind.MATCH_START && it.atNanos in startNanos..endNanos }
    Canvas(modifier.semantics {
        contentDescription = "Replay event markers"
        stateDescription = "${events.size} events; player above, opponent below"
    }) {
        val duration = (endNanos - startNanos).coerceAtLeast(1)
        val radius = 2.dp.toPx()
        // A confirmed faint stays visible even when multiple events share a scrubber pixel.
        events.sortedBy { when (it.kind) {
            ReplayEventMarkerKind.FAST_MOVE -> 0
            ReplayEventMarkerKind.FAINT -> 2
            else -> 1
        } }.forEach { marker ->
            val x = radius + (size.width - 2 * radius).coerceAtLeast(0f) *
                ((marker.atNanos - startNanos).toDouble() / duration).toFloat()
            val y = when (marker.side) {
                "PLAYER" -> radius
                "OPPONENT" -> size.height - radius
                else -> size.height / 2
            }
            val color = replayMarkerColor(marker.kind)
            when (marker.kind) {
                ReplayEventMarkerKind.FAINT -> {
                    drawLine(color, Offset(x - radius, y - radius), Offset(x + radius, y + radius), 1.dp.toPx())
                    drawLine(color, Offset(x - radius, y + radius), Offset(x + radius, y - radius), 1.dp.toPx())
                }
                ReplayEventMarkerKind.FAST_MOVE -> drawCircle(color, radius / 2, Offset(x, y))
                ReplayEventMarkerKind.CHARGED_MOVE -> drawCircle(color, radius, Offset(x, y))
                else -> drawRect(color, Offset(x - radius, y - radius), Size(radius * 2, radius * 2))
            }
        }
    }
}

/** Small labelled seek controls avoid requiring accurate taps on densely packed scrubber ticks. */
@Composable
internal fun ReplayEventMarkerControls(
    markers: List<ReplayEventMarker>,
    cursorNanos: Long,
    selectedId: String?,
    startNanos: Long,
    onSeek: (ReplayEventMarker) -> Unit,
    modifier: Modifier = Modifier,
    previousSelected: Boolean = false,
    nextSelected: Boolean = false,
    matchStartNanos: Long? = null,
    archiveStartNanos: Long = startNanos,
) {
    val currentIndex = ReplayEventMarkerNavigation.currentIndex(markers, cursorNanos, selectedId)
    val current = markers.getOrNull(currentIndex)
    val previous = ReplayEventMarkerNavigation.previousIndex(markers, cursorNanos, selectedId)?.let(markers::get)
    val next = ReplayEventMarkerNavigation.nextIndex(markers, cursorNanos, selectedId)?.let(markers::get)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MatchLcdTextButton("‹", { previous?.let(onSeek) },
            Modifier.width(36.dp).heightIn(min = 40.dp).semantics { contentDescription = "Previous replay event" },
            selected = previousSelected, enabled = previous != null)
        Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
            val label = current?.let {
                "${currentIndex + 1}/${markers.size} ${it.kind.shortLabel} · ${it.description}"
            } ?: if (markers.isEmpty()) "NO EVENT MARKERS" else "${markers.size} EVENT MARKERS · NEXT ›"
            Text(label, color = current?.kind?.let(::replayMarkerColor) ?: TerminalGreen,
                fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace,
                maxLines = if (current?.kind == ReplayEventMarkerKind.FAINT) Int.MAX_VALUE else 2,
                overflow = if (current?.kind == ReplayEventMarkerKind.FAINT) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Replay event details"
                    stateDescription = current?.description ?: "No event selected"
                })
            current?.let { marker ->
                val time = if (marker.kind == ReplayEventMarkerKind.FAINT)
                    formatReplayFaintTime(marker.faintTiming, matchStartNanos, archiveStartNanos)
                else formatReplayTime(marker.atNanos - startNanos)
                MatchLcdText(time)
            }
        }
        MatchLcdTextButton("›", { next?.let(onSeek) },
            Modifier.width(36.dp).heightIn(min = 40.dp).semantics { contentDescription = "Next replay event" },
            selected = nextSelected, enabled = next != null)
    }
}

internal fun formatReplayFaintTime(timing: ReplayFaintTiming?, matchStart: Long?, archiveStart: Long): String {
    val origin = matchStart ?: archiveStart
    val clock = if (matchStart != null) "from GO" else "from Replay start"
    fun bound(at: Long, upper: Boolean): String {
        val delta = at - origin
        val sec = if (upper) -Math.floorDiv(-delta, 1_000_000_000L) else Math.floorDiv(delta, 1_000_000_000L)
        val magnitude = kotlin.math.abs(sec)
        return (if (sec < 0) "−" else "") + "%d:%02d".format(magnitude / 60, magnitude % 60)
    }
    fun measured(at: Long) = (if (at < origin) "−" else "") + formatReplayTime(kotlin.math.abs(at - origin))
    return when (timing) {
        is ReplayFaintTiming.Cry -> "Faint cry at ${measured(timing.onset.atNanos)} $clock"
        is ReplayFaintTiming.ZeroHp -> "0 HP at ${measured(timing.frame.atNanos)} $clock"
        is ReplayFaintTiming.TrainerMark -> "Trainer mark ${measured(timing.atNanos)} $clock"
        is ReplayFaintTiming.Window -> {
            val by = bound(timing.by.atNanos, upper = true)
            timing.after?.let { "KO window ${bound(it.atNanos, upper = false)}–$by $clock" } ?: "KO by $by $clock"
        }
        ReplayFaintTiming.Unknown, null -> "KO time unknown"
    }
}

private fun replayMarkerColor(kind: ReplayEventMarkerKind): Color = when (kind) {
    ReplayEventMarkerKind.MATCH_START -> com.example.overdex.ui.theme.TerminalPurple
    ReplayEventMarkerKind.CHARGED_MOVE -> Color(0xFFFFB74D)
    ReplayEventMarkerKind.FAST_MOVE -> Color(0xFFB0BEC5)
    ReplayEventMarkerKind.FAINT -> Color(0xFFFF6B6B)
    ReplayEventMarkerKind.BUFF -> TerminalGreen
    ReplayEventMarkerKind.DEBUFF -> Color(0xFFFF80AB)
    ReplayEventMarkerKind.ACTIVE_CHANGE -> Color(0xFF80DEEA)
    ReplayEventMarkerKind.SHIELD -> Color(0xFF90B6FF)
    ReplayEventMarkerKind.POSSIBLE_CRY -> Color(0xFFCE93D8)
}
