package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.ODXFiAppearance
import com.example.overdex.data.odxFiColorHex
import com.example.overdex.ui.ODXFi.LocalODXFiAppearance
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cosmetic drafts preview the real shell; only a successful Save changes the app preference. */
@Composable
fun ODXFiAppearanceScreen(
    initialAppearance: ODXFiAppearance,
    onSave: suspend (ODXFiAppearance) -> Boolean,
    onBack: () -> Unit,
) {
    var caseColor by rememberSaveable { mutableIntStateOf(initialAppearance.opaque().caseColorArgb) }
    var buttonColor by rememberSaveable { mutableIntStateOf(initialAppearance.opaque().buttonColorArgb) }
    var bezelColor by rememberSaveable { mutableIntStateOf(initialAppearance.opaque().bezelColorArgb) }
    var selectedPart by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannel by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draft = ODXFiAppearance(caseColor, buttonColor, bezelColor)
    val part = AppearancePart.entries[selectedPart]
    val selectedColor = when (part) {
        AppearancePart.CASE -> caseColor
        AppearancePart.BUTTONS -> buttonColor
        AppearancePart.BEZELS -> bezelColor
    }
    val channel = AppearanceChannel.entries[selectedChannel]

    BackHandler { if (!saving) onBack() }

    fun changeColor(argb: Int) {
        if (saving) return
        val opaque = argb or 0xFF000000.toInt()
        when (part) {
            AppearancePart.CASE -> caseColor = opaque
            AppearancePart.BUTTONS -> buttonColor = opaque
            AppearancePart.BEZELS -> bezelColor = opaque
        }
        saveFailed = false
    }

    fun changeChannel(channelIndex: Int, value: Int) {
        val shift = AppearanceChannel.entries[channelIndex].shift
        changeColor((selectedColor and (0xFF shl shift).inv()) or (value.coerceIn(0, 255) shl shift))
    }

    fun choosePart(index: Int) {
        if (!saving) selectedPart = index.coerceIn(0, AppearancePart.entries.lastIndex)
    }

    fun restoreOriginal() {
        if (saving) return
        val original = ODXFiAppearance()
        caseColor = original.caseColorArgb
        buttonColor = original.buttonColorArgb
        bezelColor = original.bezelColorArgb
        saveFailed = false
    }

    fun save() {
        if (saving) return
        saving = true
        saveFailed = false
        val savedDraft = draft
        scope.launch {
            val succeeded = try {
                onSave(savedDraft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            saving = false
            if (succeeded) onBack() else saveFailed = true
        }
    }

    fun cyclePreset() {
        val presets = appearancePresets(part)
        val next = (presets.indexOfFirst { it.color == selectedColor } + 1) % presets.size
        changeColor(presets[next].color)
    }

    CompositionLocalProvider(LocalODXFiAppearance provides draft) {
        ODXFiShell(
            showBattleOverlay = false,
            // Color comparison needs literal swatches, without CRT tint or flicker.
            filterSettings = FilterSettings(isEnabled = false),
            onUp = { choosePart(selectedPart - 1) },
            onDown = { choosePart(selectedPart + 1) },
            onLeft = { changeChannel(selectedChannel, channel.value(selectedColor) - 5) },
            onRight = { changeChannel(selectedChannel, channel.value(selectedColor) + 5) },
            onSelect = { if (!saving) selectedChannel = (selectedChannel + 1) % AppearanceChannel.entries.size },
            onA = ::cyclePreset,
            onB = { if (!saving) onBack() },
            onStart = ::save,
            startLabel = if (saving) "SAVING" else "SAVE",
            lcdEdgeToEdgeContent = true,
            lcdContent = {
                AppearanceLcd(
                    part = part,
                    selectedColor = selectedColor,
                    selectedChannel = channel,
                    saving = saving,
                    saveFailed = saveFailed,
                    onPart = { choosePart(it.ordinal) },
                    onColor = ::changeColor,
                    onChannelSelected = { if (!saving) selectedChannel = it.ordinal },
                    onChannelValue = { selected, value -> changeChannel(selected.ordinal, value) },
                    onReset = ::restoreOriginal,
                    onSave = ::save,
                )
            },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
                    .testTag("odx-fi-colors-preview"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Colors", color = TerminalGreen, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                if (saveFailed) Text("Not saved. Please try again.", color = Color(0xFFFFC9BC),
                    fontSize = 13.sp, modifier = Modifier.testTag("odx-fi-colors-save-error"))
                AppearancePart.entries.forEach { previewPart ->
                    val color = when (previewPart) {
                        AppearancePart.CASE -> caseColor
                        AppearancePart.BUTTONS -> buttonColor
                        AppearancePart.BEZELS -> bezelColor
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .border(1.dp, if (part == previewPart) TerminalGreen else Color.DarkGray,
                                RoundedCornerShape(4.dp))
                            .padding(9.dp)
                            .semantics {
                                stateDescription = odxFiColorHex(color)
                                selected = part == previewPart
                            }
                            .testTag("odx-fi-colors-preview-${previewPart.tag}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorSwatch(color, Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(previewPart.label, color = TerminalGreen, fontFamily = FontFamily.Monospace)
                            Text(odxFiColorHex(color), color = Color.White, fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Text("${part.label}  ${odxFiColorHex(selectedColor)}", color = Color.White,
                    fontSize = 17.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("odx-fi-colors-draft-hex"))
                Text("↑↓ part · ←→ value\nSELECT channel · A preset\nSAVE apply · B cancel",
                    color = TerminalGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun AppearanceLcd(
    part: AppearancePart,
    selectedColor: Int,
    selectedChannel: AppearanceChannel,
    saving: Boolean,
    saveFailed: Boolean,
    onPart: (AppearancePart) -> Unit,
    onColor: (Int) -> Unit,
    onChannelSelected: (AppearanceChannel) -> Unit,
    onChannelValue: (AppearanceChannel, Int) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val controlsScroll = rememberScrollState()
    val channelRequesters = remember {
        AppearanceChannel.entries.associateWith { BringIntoViewRequester() }
    }
    var previousSelectedChannel by remember { mutableStateOf(selectedChannel) }
    LaunchedEffect(part) { controlsScroll.scrollTo(0) }
    LaunchedEffect(selectedChannel) {
        if (previousSelectedChannel != selectedChannel) {
            channelRequesters.getValue(selectedChannel).bringIntoView()
        }
        previousSelectedChannel = selectedChannel
    }
    Column(Modifier.fillMaxSize().testTag("odx-fi-colors-lcd")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AppearancePart.entries.forEach { candidate ->
                Box(
                    Modifier.weight(1f).heightIn(min = 30.dp)
                        .background(if (candidate == part) Color(0xFF244931) else Color.Transparent,
                            RoundedCornerShape(3.dp))
                        .border(1.dp, if (candidate == part) TerminalGreen else Color.DarkGray,
                            RoundedCornerShape(3.dp))
                        .clickable(enabled = !saving, role = Role.Button) { onPart(candidate) }
                        .semantics { selected = candidate == part }
                        .testTag("odx-fi-colors-part-${candidate.tag}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(candidate.label, color = TerminalGreen, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(controlsScroll)
                .testTag("odx-fi-colors-controls").padding(top = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(odxFiColorHex(selectedColor), color = Color.White, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("odx-fi-colors-lcd-hex"))
            appearancePresets(part).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { preset ->
                        Box(
                            Modifier.weight(1f).height(28.dp)
                                .background(Color(preset.color), RoundedCornerShape(3.dp))
                                .border(if (preset.color == selectedColor) 2.dp else 1.dp,
                                    if (preset.color == selectedColor) TerminalGreen else Color.Gray,
                                    RoundedCornerShape(3.dp))
                                .clickable(enabled = !saving, role = Role.Button) { onColor(preset.color) }
                                .semantics {
                                    contentDescription = "${part.label}: ${preset.label}"
                                    selected = preset.color == selectedColor
                                }
                                .testTag("odx-fi-colors-preset-${preset.tag}"),
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            AppearanceChannel.entries.forEach { channel ->
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(channelRequesters.getValue(channel))) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 28.dp)
                            .clickable(enabled = !saving, role = Role.Button) { onChannelSelected(channel) }
                            .semantics { selected = channel == selectedChannel }
                            .testTag("odx-fi-colors-channel-${channel.tag}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (channel == selectedChannel) "› ${channel.label}" else channel.label,
                            color = TerminalGreen, fontSize = 11.sp)
                        Text(channel.value(selectedColor).toString(), color = Color.White, fontSize = 11.sp)
                    }
                    Slider(
                        value = channel.value(selectedColor).toFloat(),
                        onValueChange = { onChannelSelected(channel); onChannelValue(channel, it.roundToInt()) },
                        valueRange = 0f..255f,
                        enabled = !saving,
                        colors = SliderDefaults.colors(thumbColor = TerminalGreen,
                            activeTrackColor = TerminalGreen, inactiveTrackColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                            .semantics { contentDescription = "${part.label} ${channel.label.lowercase()}" }
                            .testTag("odx-fi-colors-slider-${channel.tag}"),
                    )
                }
            }
            if (saveFailed) Text("Not saved. Try Save again.", color = Color(0xFFFFC9BC), fontSize = 10.sp,
                modifier = Modifier.testTag("odx-fi-colors-lcd-save-error"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onReset, enabled = !saving, modifier = Modifier.weight(1.5f)
                .testTag("odx-fi-colors-reset")) {
                Text("Original colors", color = TerminalGreen, fontSize = 10.sp, maxLines = 1)
            }
            TextButton(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)
                .testTag("odx-fi-colors-save")) {
                Text(if (saving) "Saving" else "Save", color = TerminalGreen, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, modifier: Modifier) {
    Box(modifier.background(Color(argb), RoundedCornerShape(3.dp))
        .border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
}

private enum class AppearancePart(val label: String, val tag: String, val original: Int) {
    CASE("Case", "case", ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB),
    BUTTONS("Buttons", "buttons", ODXFiAppearance.DEFAULT_BUTTON_COLOR_ARGB),
    BEZELS("Bezels", "bezels", ODXFiAppearance.DEFAULT_BEZEL_COLOR_ARGB),
}

private enum class AppearanceChannel(val label: String, val tag: String, val shift: Int) {
    RED("Red", "red", 16), GREEN("Green", "green", 8), BLUE("Blue", "blue", 0);
    fun value(argb: Int): Int = (argb ushr shift) and 0xFF
}

private data class AppearancePreset(val label: String, val tag: String, val color: Int)

private fun appearancePresets(part: AppearancePart): List<AppearancePreset> = listOf(
    AppearancePreset("Original", "original", part.original),
    AppearancePreset("Charcoal", "charcoal", 0xFF292C33.toInt()),
    AppearancePreset("Silver", "silver", 0xFFB7BDC7.toInt()),
    AppearancePreset("Red", "red", 0xFFB63B42.toInt()),
    AppearancePreset("Blue", "blue", 0xFF3466A8.toInt()),
    AppearancePreset("Purple", "purple", 0xFF734A91.toInt()),
    AppearancePreset("Teal", "teal", 0xFF287D80.toInt()),
)
