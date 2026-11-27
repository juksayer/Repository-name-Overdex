package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.battle.BattleSpeciesDisplay
import com.example.overdex.battle.replay.ReplayEventMarker
import com.example.overdex.battle.replay.ReplayEventMarkerKind
import com.example.overdex.battle.replay.ReplayEventMarkerNavigation
import com.example.overdex.battle.replay.ReplayFaintTiming
import com.example.overdex.battle.replay.ReplayFinishingFastAssociation

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

internal enum class ReplayLcdView(val label: String) {
    Playback("Playback"),
    Events("Events"),
    Menu("Menu"),
}

private val replayLcdInk = Color(0xFFE6F3E9)
private val replayLcdMutedInk = Color(0xFFABBDB0)
private val replayLcdBody = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
private val replayLcdHeading = replayLcdBody.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
private val replayLcdControl = replayLcdBody.copy(fontWeight = FontWeight.Medium)
private val replayLcdActionPadding = 6.dp
private val replayLcdActionMinimum = 48.dp
private val replayLcdCompactPadding = 2.dp
private val replayLcdCompactControl = replayLcdControl.copy(fontSize = 13.sp, lineHeight = 16.sp)

@Composable
internal fun ReplayLcdText(text: String, modifier: Modifier = Modifier, heading: Boolean = false, textAlign: TextAlign? = null) {
    Text(text, modifier, color = replayLcdInk, textAlign = textAlign, style = if (heading) replayLcdHeading else replayLcdBody)
}

@Composable
internal fun ReplayLcdButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean? = null,
    alignedStart: Boolean = true,
    role: Role = Role.Button,
    revealFocus: Boolean = focused,
    compact: Boolean = false,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused, revealFocus) { if (focused && revealFocus) requester.bringIntoView() }
    val frame = if (compact) Modifier else {
        val background = when {
            focused -> Color(0xFF284731)
            selected == true -> Color(0xFF203728)
            else -> Color(0xFF1B241D)
        }
        Modifier.sizeIn(minWidth = replayLcdActionMinimum, minHeight = replayLcdActionMinimum)
            .background(background)
            .border(1.dp, if (focused) replayLcdInk else Color(0xFF53745B))
    }
    Box(
        modifier = modifier
            .then(frame)
            .bringIntoViewRequester(requester)
            .semantics {
                this.focused = focused
                if (selected != null) this.selected = selected
            }
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .padding(
                horizontal = if (compact) replayLcdCompactPadding else replayLcdActionPadding,
                vertical = if (compact) 1.dp else 6.dp,
            ),
        contentAlignment = if (alignedStart) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> replayLcdMutedInk
                compact && (focused || selected == true) -> Color(0xFFABF5BA)
                else -> replayLcdInk
            },
            style = if (compact) replayLcdCompactControl else replayLcdControl,
            textDecoration = TextDecoration.Underline.takeIf { compact && focused },
        )
    }
}

@Composable
internal fun ReplayLcdViewTabs(
    current: ReplayLcdView,
    chooserOpen: Boolean,
    focusedView: ReplayLcdView?,
    changeViewFocused: Boolean,
    onChooseView: () -> Unit,
    onViewSelected: (ReplayLcdView) -> Unit,
    onCompactChanged: (Boolean) -> Unit,
) {
    val views = ReplayLcdView.entries
    val spacing = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val widths = remember(density, measurer) {
            views.map { view ->
                val text = measurer.measure(view.label, style = replayLcdCompactControl, softWrap = false)
                with(density) { (text.size.width + 1).toDp() + replayLcdCompactPadding * 2 }
            }
        }
        val compact = maxWidth.value < widths.sumOf { it.value.toDouble() } + spacing.value * (views.size - 1)
        val latestCallback by rememberUpdatedState(onCompactChanged)
        LaunchedEffect(compact) { latestCallback(compact) }
        when {
            chooserOpen -> Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                views.forEach { view ->
                    ReplayLcdButton(view.label, { onViewSelected(view) }, Modifier.fillMaxWidth(),
                        focused = focusedView == view, selected = current == view, compact = true)
                }
            }
            compact -> ReplayLcdButton(
                "Change view",
                onChooseView,
                Modifier.fillMaxWidth().semantics { stateDescription = "Current view ${current.label}" },
                focused = changeViewFocused, compact = true,
            )
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                views.forEachIndexed { index, view ->
                    ReplayLcdButton(view.label, { onViewSelected(view) }, Modifier.weight(widths[index].value),
                        focused = focusedView == view, selected = current == view, alignedStart = false,
                        role = Role.Tab, compact = true)
                }
            }
        }
    }
}

/** Each side shows its latest event at the cursor. A selected event wins ties in its own column. */
@Composable
internal fun ReplayLcdEventDetails(
    markers: List<ReplayEventMarker>,
    cursorNanos: Long,
    selectedId: String?,
    startNanos: Long,
    matchStartNanos: Long?,
    archiveStartNanos: Long,
    playbackSummary: Boolean = false,
) {
    val index = ReplayEventMarkerNavigation.currentIndex(markers, cursorNanos, selectedId)
    val current = markers.getOrNull(index)
    val visible = markers.asReversed().asSequence().dropWhile { it.atNanos > cursorNanos }
    val player = current?.takeIf { it.initiatingSide == "PLAYER" }
        ?: visible.firstOrNull { it.initiatingSide == "PLAYER" }
    val opponent = current?.takeIf { it.initiatingSide == "OPPONENT" }
        ?: visible.firstOrNull { it.initiatingSide == "OPPONENT" }

    if (current == null) {
        if (markers.isEmpty()) ReplayLcdText("No event markers")
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (current.initiatingSide == null) {
            ReplayLcdEvent(current, startNanos, matchStartNanos, archiveStartNanos,
                Modifier.testTag("replay-events-shared"))
        }
        if (player != null || opponent != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).testTag("replay-events-player")) {
                        ReplayLcdText("You")
                        player?.let { ReplayLcdEvent(it, startNanos, matchStartNanos, archiveStartNanos, playbackSummary = playbackSummary) }
                    }
                    Column(Modifier.weight(1f).testTag("replay-events-opponent"), horizontalAlignment = Alignment.End) {
                        ReplayLcdText("Opponent")
                        opponent?.let { ReplayLcdEvent(it, startNanos, matchStartNanos, archiveStartNanos, playbackSummary = playbackSummary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayLcdEvent(
    current: ReplayEventMarker,
    startNanos: Long,
    matchStartNanos: Long?,
    archiveStartNanos: Long,
    modifier: Modifier = Modifier,
    playbackSummary: Boolean = false,
) {
    val faint = current.kind == ReplayEventMarkerKind.FAINT
    val side = when (current.side) {
        "PLAYER" -> "You"
        "OPPONENT" -> "Opponent"
        else -> if (current.kind == ReplayEventMarkerKind.MATCH_START) null else "Side unconfirmed"
    }
    val attribution = current.finishingAttribution
    val standardFaint = faint && current.label.startsWith("Faint · ")
    val faintName = when {
        faint && attribution != null -> BattleSpeciesDisplay.name(attribution.recipientSpeciesName)
        standardFaint -> current.label.removePrefix("Faint · ").substringBefore(" · ")
        else -> null
    }
    val byline = attribution?.takeIf { faint }?.let {
        val move = it.moveName ?: if (it is ReplayFinishingFastAssociation) "Fast Move" else "Charged Move"
        "$move by ${BattleSpeciesDisplay.name(it.attackerSpeciesName)}"
    } ?: current.lastWitnessedAttack?.label
    val mirrorFaint = faint && attribution != null &&
        BattleSpeciesDisplay.name(attribution.attackerSpeciesName) == faintName
    val affectedSide = side.takeIf {
        mirrorFaint || (current.initiatingSide != current.side &&
            (faint && attribution == null || current.kind in setOf(ReplayEventMarkerKind.BUFF, ReplayEventMarkerKind.DEBUFF)))
    }
    val qualification = if (standardFaint) {
        current.label.split(" · ").drop(if (byline == null) 2 else 3).joinToString(" · ").ifBlank { null }
    } else null
    val title = when {
        faintName != null -> "$faintName fainted"
        current.label.startsWith("Fast move · name unknown") ->
            current.label.replaceFirst("Fast move · name unknown", "Unknown fast move")
        current.label.startsWith("Charged move · name unknown") ->
            current.label.replaceFirst("Charged move · name unknown", "Unknown charged move")
        current.kind == ReplayEventMarkerKind.CONCESSION -> current.label.substringBefore(" · ")
        else -> current.label.removePrefix("Fast move · ").removePrefix("Charged move · ")
    }
    val shared = current.initiatingSide == null
    val opponent = current.initiatingSide == "OPPONENT"
    val align = when {
        shared -> TextAlign.Center
        opponent -> TextAlign.End
        else -> TextAlign.Start
    }
    val predictedUse = playbackSummary && current.kind == ReplayEventMarkerKind.FAST_MOVE &&
        title.startsWith("Inferred · ")
    val estimatedTime = playbackSummary && current.kind == ReplayEventMarkerKind.FAST_MOVE &&
        title.endsWith(" · timing inferred")
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = "Replay event details"
            stateDescription = current.description
        },
        horizontalAlignment = when {
            shared -> Alignment.CenterHorizontally
            opponent -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val marker = Modifier.size(6.dp).background(replayMarkerColor(current.kind))
            if (!opponent) Box(marker)
            val displayTitle = when {
                predictedUse -> title.removePrefix("Inferred · ")
                estimatedTime -> title.removeSuffix(" · timing inferred")
                else -> title
            }
            ReplayLcdText(displayTitle,
                Modifier.weight(1f, fill = false), heading = true, textAlign = align)
            if (opponent) Box(marker)
        }
        affectedSide?.let { ReplayLcdText(it, textAlign = align) }
        if (shared && current.kind != ReplayEventMarkerKind.MATCH_START) {
            if (!faint || byline == null) ReplayLcdText(if (faint) "Cause unconfirmed" else "Initiating side unconfirmed")
        }
        byline?.let { ReplayLcdText(it, textAlign = align) }
        if (current.kind == ReplayEventMarkerKind.CONCESSION) {
            ReplayLcdText(current.label.substringAfter(" · "), textAlign = align)
        }
        qualification?.let { ReplayLcdText(it, textAlign = align) }
        if (faint) ReplayLcdText(formatReplayFaintTime(current.faintTiming, matchStartNanos, archiveStartNanos), textAlign = align)
        else if (current.kind != ReplayEventMarkerKind.MATCH_START) {
            val time = formatReplayTime(current.atNanos - startNanos)
            val caption = when {
                predictedUse -> if (opponent) "inferred use · $time" else "$time · inferred use"
                !estimatedTime -> time
                opponent -> "estimated · $time"
                else -> "$time · estimated"
            }
            ReplayLcdText(caption, textAlign = align)
        }
        if (!playbackSummary) current.inferenceExplanation?.let { ReplayLcdText(it, textAlign = align) }
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
            val y = when (marker.initiatingSide) {
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
                marker.inferenceExplanation?.let { MatchLcdText(it) }
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
    ReplayEventMarkerKind.CONCESSION -> Color.White
}
