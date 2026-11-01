package com.example.overdex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.preview.*
import com.example.overdex.model.AnchorRegion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Historical evidence stays raw; explicit editing uses a separately identified full source. */
@Composable
fun DirectoryCropPreviewLcd(
    state: DirectoryCropPreviewState,
    modifier: Modifier = Modifier,
    editor: DirectoryCropEditor? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm:ss", locale) }
    val latest = state.latestCaptureMillis
    if (editor?.editing == true) {
        DirectoryCropPositionEditor(editor, modifier)
        return
    }
    Column(modifier.fillMaxSize().testTag("directory-crop-preview").padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (latest == null && editor == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.isLoading) "Loading captured crops…" else "Record a battle to check calibration",
                    modifier = Modifier.testTag("directory-crop-preview-empty"),
                    color = CropPreviewText, fontSize = 10.sp, lineHeight = 12.sp)
            }
        } else {
            val focused = editor?.focused == true
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    editor?.loading == true -> "Loading battle reference…"
                    focused && editor?.frame != null -> editor.frame!!.label()
                    latest != null -> "Battle crops ${timeFormat.format(Date(latest))}"
                    else -> "Battle crops · tap to calibrate"
                }, modifier = Modifier.weight(1f).testTag("directory-crop-preview-timestamp"),
                    color = CropPreviewText, fontSize = 9.sp, lineHeight = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
                if (state.isLoading && !focused) Text("Loading", color = CropPreviewText,
                    fontSize = 8.sp, lineHeight = 10.sp, style = CropPreviewTextStyle)
            }
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_SPECIES, DirectoryCropSlot.OPPONENT_SPECIES,
                timeFormat, Modifier.weight(1.1f), editor)
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_HP, DirectoryCropSlot.OPPONENT_HP,
                timeFormat, Modifier.weight(.8f), editor)
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_BALLS, DirectoryCropSlot.OPPONENT_BALLS,
                timeFormat, Modifier.weight(1f), editor)
            CropPreviewTile(DirectoryCropSlot.ANNOUNCEMENT, state.crops[DirectoryCropSlot.ANNOUNCEMENT],
                timeFormat, Modifier.fillMaxWidth().weight(1.3f), editor)
            if (focused) Text(editor?.notice ?: "A Edit · B Directory · arrows select",
                color = CropPreviewText, fontSize = 8.sp, lineHeight = 9.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
        }
    }
}

@Composable
private fun CropPreviewPair(
    state: DirectoryCropPreviewState, player: DirectoryCropSlot, opponent: DirectoryCropSlot,
    timeFormat: SimpleDateFormat, modifier: Modifier, editor: DirectoryCropEditor?,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CropPreviewTile(player, state.crops[player], timeFormat, Modifier.weight(1f).fillMaxHeight(), editor)
        CropPreviewTile(opponent, state.crops[opponent], timeFormat, Modifier.weight(1f).fillMaxHeight(), editor)
    }
}

@Composable
private fun CropPreviewTile(
    slot: DirectoryCropSlot, crop: DirectoryCropImage?, timeFormat: SimpleDateFormat,
    modifier: Modifier, editor: DirectoryCropEditor?,
) {
    val label = slot.label()
    val tag = slot.name.lowercase(Locale.ROOT)
    val bitmap = crop?.bitmap?.takeUnless { it.isRecycled }
    val capturedTime = crop?.let { timeFormat.format(Date(it.capturedAtMillis)) }
    val source = editor?.takeIf { it.focused }?.frame?.bitmap
    val region = editor?.snapshot?.calibration?.directoryCropRegion(slot)
    val calibrationPreview = source != null && region != null
    val isSelected = editor?.controls?.selectedSlot == slot
    val availability = when {
        calibrationPreview -> "Calibration preview"
        crop == null -> "Not saved"
        bitmap == null -> "Image unavailable"
        else -> "Captured $capturedTime"
    }
    Column(modifier.testTag("directory-crop-$tag")
        .then(if (isSelected) Modifier.border(1.dp, CropSelection) else Modifier)
        .then(if (editor != null) Modifier.clickable { editor.select(slot) } else Modifier)
        .semantics {
            contentDescription = "$label crop"
            stateDescription = availability
            if (editor != null) selected = isSelected
        }) {
        Text(label, color = if (isSelected) CropSelection else CropPreviewText, fontSize = 8.sp, lineHeight = 9.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (calibrationPreview) {
                SourceCropImage(requireNotNull(source), requireNotNull(region), "$label calibration preview",
                    Modifier.fillMaxSize().testTag("directory-crop-image-$tag"))
            } else if (bitmap != null) {
                Image(bitmap.asImageBitmap(), "$label crop, captured at $capturedTime",
                    Modifier.fillMaxSize().testTag("directory-crop-image-$tag"), contentScale = ContentScale.Fit)
            } else {
                Text(availability, color = CropPreviewText.copy(alpha = .7f),
                    fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
            }
        }
    }
}

@Composable
private fun DirectoryCropPositionEditor(editor: DirectoryCropEditor, modifier: Modifier) {
    val draft = requireNotNull(editor.controls.draft)
    val slot = requireNotNull(editor.controls.selectedSlot)
    val source = editor.frame?.bitmap?.takeUnless { it.isRecycled } ?: return
    val region = draft.region
    val currentMove by rememberUpdatedState<(Float, Float) -> Unit>(editor::move)
    Column(modifier.fillMaxSize().padding(3.dp).testTag("directory-crop-position-editor"),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.label(), Modifier.weight(1f), color = CropSelection, fontSize = 10.sp,
                lineHeight = 11.sp, maxLines = 1, style = CropPreviewTextStyle)
            Text(if (editor.saving) "Saving…" else "A Save", Modifier.testTag("directory-crop-save")
                .clickable(enabled = !editor.saving) { editor.confirm() }.padding(horizontal = 4.dp),
                color = CropSelection, fontSize = 9.sp, lineHeight = 11.sp, style = CropPreviewTextStyle)
            Text("B Cancel", Modifier.testTag("directory-crop-cancel")
                .clickable(enabled = !editor.saving) { editor.back() }.padding(horizontal = 4.dp),
                color = CropPreviewText, fontSize = 9.sp, lineHeight = 11.sp, style = CropPreviewTextStyle)
        }
        Text("X ${(region.x * source.width).roundToInt()}  Y ${(region.y * source.height).roundToInt()}  " +
            "${(region.width * source.width).roundToInt()}×${(region.height * source.height).roundToInt()}  ·  ${source.width}×${source.height}",
            Modifier.testTag("directory-crop-position"), color = CropPreviewText,
            fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, style = CropPreviewTextStyle)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            var contextSize by remember { mutableStateOf(IntSize.Zero) }
            val fittedScale by rememberUpdatedState(min(contextSize.width / source.width.toFloat(), contextSize.height / source.height.toFloat()))
            Box(Modifier.weight(.27f).fillMaxHeight().background(Color.Black)
                .onSizeChanged { contextSize = it }
                .pointerInput(source, editor.saving) {
                    if (!editor.saving) detectDragGestures { change, amount ->
                        change.consume()
                        if (fittedScale > 0f) currentMove(amount.x / (source.width * fittedScale), amount.y / (source.height * fittedScale))
                    }
                }.testTag("directory-crop-source")) {
                Image(source.asImageBitmap(), editor.frame!!.label(), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    val scale = min(size.width / source.width, size.height / source.height)
                    val w = source.width * scale
                    val h = source.height * scale
                    drawRect(CropSelection, Offset((size.width - w) / 2f + region.x * w,
                        (size.height - h) / 2f + region.y * h), Size(region.width * w, region.height * h),
                        style = Stroke(width = 1.dp.toPx()))
                }
            }
            var cropSize by remember { mutableStateOf(IntSize.Zero) }
            val cropScale by rememberUpdatedState(min(cropSize.width / (region.width * source.width),
                cropSize.height / (region.height * source.height)))
            SourceCropImage(source, region, "Selected crop preview", Modifier.weight(.73f).fillMaxHeight()
                .background(Color.Black).onSizeChanged { cropSize = it }
                .pointerInput(source, region.width, region.height, editor.saving) {
                    if (!editor.saving) detectDragGestures { change, amount ->
                        change.consume()
                        if (cropScale > 0f) currentMove(amount.x / (source.width * cropScale), amount.y / (source.height * cropScale))
                    }
                }.testTag("directory-crop-drag-preview"))
        }
        Text(editor.notice ?: "${editor.frame!!.label()} · Drag or arrows: 1 pixel · ${if (draft.dirty) "Unsaved" else "Saved position"}",
            color = CropPreviewText, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
    }
}

@Composable
private fun SourceCropImage(bitmap: android.graphics.Bitmap, region: AnchorRegion, description: String, modifier: Modifier) {
    val bounds = directoryCropPixelBounds(region, bitmap.width, bitmap.height) ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val painter = remember(image, bounds) { BitmapPainter(image, IntOffset(bounds.left, bounds.top), IntSize(bounds.width, bounds.height)) }
    Image(painter, description, modifier, contentScale = ContentScale.Fit)
}

private fun DirectoryCalibrationFrame.label() = when (origin) {
    DirectoryCalibrationFrameOrigin.RECORDED_MATCH_FRAME -> "Saved battle frame"
    DirectoryCalibrationFrameOrigin.SAVED_BATTLE_REFERENCE -> "Saved battle reference"
}

private fun DirectoryCropSlot.label() = when (this) {
    DirectoryCropSlot.PLAYER_SPECIES -> "Your name"
    DirectoryCropSlot.OPPONENT_SPECIES -> "Opponent name"
    DirectoryCropSlot.PLAYER_HP -> "Your HP"
    DirectoryCropSlot.OPPONENT_HP -> "Opponent HP"
    DirectoryCropSlot.PLAYER_BALLS -> "Your Poké Balls"
    DirectoryCropSlot.OPPONENT_BALLS -> "Opponent Poké Balls"
    DirectoryCropSlot.ANNOUNCEMENT -> "Announcement"
}

private val CropPreviewText = Color(0xFFD4E7CF)
private val CropSelection = Color(0xFFA9F28F)
private val CropPreviewTextStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
