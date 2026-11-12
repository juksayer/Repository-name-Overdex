package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.overdex.data.CalibrationScreenshotLibrary
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen

/** Browsing is local to this dialog. Only USE SCREENSHOT can change the caller's selection. */
@Composable
internal fun CalibrationScreenshotPicker(
    samples: List<CalibrationSample>,
    selectedId: String?,
    screenWidth: Int,
    screenHeight: Int,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var highlightedId by remember { mutableStateOf(selectedId ?: samples.firstOrNull()?.reference?.id) }
    LaunchedEffect(samples.map { it.reference.id }) {
        if (samples.none { it.reference.id == highlightedId }) {
            highlightedId = samples.firstOrNull { it.reference.id == selectedId }?.reference?.id
                ?: samples.firstOrNull()?.reference?.id
        }
    }
    val highlightedIndex = samples.indexOfFirst { it.reference.id == highlightedId }
    val highlighted = samples.getOrNull(highlightedIndex)
    // Each highlight owns its readiness; a late callback for a retired image cannot enable Use.
    var highlightedReady by remember(highlighted?.reference?.id, highlighted?.imageModel) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = TerminalBlack,
            modifier = Modifier.padding(12.dp).widthIn(max = 560.dp).fillMaxWidth()
                .fillMaxHeight(.92f).testTag("calibration-screenshot-picker"),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CHOOSE SCREENSHOT", color = TerminalGreen, fontSize = 17.sp)
                Text("Preview a picture, then use it for calibration.", color = TerminalGreen, fontSize = 12.sp)
                BoxWithConstraints(Modifier.weight(1f)) {
                    val previewHeight = (maxHeight * .42f).coerceIn(80.dp, 260.dp)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (highlighted == null) {
                            Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
                                Text("No screenshots in this view.", color = TerminalGreen)
                            }
                        } else {
                            // A fresh composition and painter prevent the previous image from surviving an error.
                            key(highlighted.reference.id, highlighted.imageModel) {
                                ScreenshotPreview(highlighted, previewHeight.value, onReady = { isReady ->
                                    highlightedReady = isReady
                                })
                            }
                            Text(highlighted.reference.name, color = TerminalGreen, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("calibration-screenshot-name"))
                            val dimensions = highlighted.reference.let {
                                if (it.hasDimensions) "${it.width} × ${it.height}" else "Dimensions unknown"
                            }
                            Text("$dimensions · ${highlighted.reference.deviceLabel ?: "Device not labeled"} · ${highlighted.sourceName}",
                                color = TerminalGreen, fontSize = 11.sp,
                                modifier = Modifier.testTag("calibration-screenshot-details"))
                            val warning = when {
                                !highlighted.reference.hasDimensions -> "Read only: screenshot dimensions are unknown."
                                !CalibrationScreenshotLibrary.matchesScreen(highlighted.reference, screenWidth, screenHeight) ->
                                    "Read only: different screen shape. You can still review this picture."
                                else -> null
                            }
                            if (warning != null) Text(warning, color = Color(0xFFFFC857), fontSize = 11.sp,
                                modifier = Modifier.testTag("calibration-screenshot-warning"))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = highlightedIndex > 0,
                                onClick = { highlightedId = samples[highlightedIndex - 1].reference.id },
                                modifier = Modifier.testTag("calibration-screenshot-previous")) {
                                Text("PREVIOUS", color = TerminalGreen)
                            }
                            Text(if (highlighted == null) "0 / 0" else "${highlightedIndex + 1} / ${samples.size}",
                                color = TerminalGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(enabled = highlightedIndex >= 0 && highlightedIndex < samples.lastIndex,
                                onClick = { highlightedId = samples[highlightedIndex + 1].reference.id },
                                modifier = Modifier.testTag("calibration-screenshot-next")) {
                                Text("NEXT", color = TerminalGreen)
                            }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("calibration-screenshot-list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(samples, key = { it.reference.id }) { sample ->
                                ScreenshotRow(sample, sample.reference.id == highlightedId) {
                                    highlightedId = sample.reference.id
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("calibration-screenshot-cancel")) {
                        Text("CANCEL", color = TerminalGreen)
                    }
                    TextButton(enabled = highlighted != null && highlightedReady,
                        onClick = { highlighted?.reference?.id?.let(onChoose) },
                        modifier = Modifier.testTag("calibration-screenshot-use")) {
                        Text("USE SCREENSHOT", color = TerminalGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotPreview(sample: CalibrationSample, heightDp: Float, onReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var status by remember { mutableStateOf("Loading preview…") }
    BoxWithConstraints(Modifier.fillMaxWidth().height(heightDp.dp).background(Color.Black)
        .border(1.dp, TerminalGreen.copy(alpha = .5f)).testTag("calibration-screenshot-preview"),
        contentAlignment = Alignment.Center) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val request = remember(sample.imageModel, widthPx, heightPx) {
            ImageRequest.Builder(context).data(sample.imageModel).size(widthPx, heightPx)
                .crossfade(false).build()
        }
        AsyncImage(model = request, contentDescription = "Screenshot preview: ${sample.reference.name}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().testTag("calibration-screenshot-image"),
            onLoading = { status = "Loading preview…"; onReady(false) },
            onSuccess = { status = "Preview ready"; onReady(true) },
            onError = { status = "Could not display this screenshot. Choose another picture."; onReady(false) })
        Text(status, color = TerminalGreen, fontSize = 11.sp,
            modifier = Modifier.align(if (status == "Preview ready") Alignment.BottomStart else Alignment.Center)
                .background(Color.Black.copy(alpha = .75f)).padding(4.dp)
                .testTag("calibration-screenshot-preview-status"))
    }
}

@Composable
private fun ScreenshotRow(sample: CalibrationSample, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(sample.imageModel, density) {
        ImageRequest.Builder(context).data(sample.imageModel)
            .size(with(density) { 44.dp.roundToPx() }, with(density) { 64.dp.roundToPx() })
            .crossfade(false).build()
    }
    Row(Modifier.fillMaxWidth().semantics { selected = isSelected }
        .testTag("calibration-screenshot-row-${sample.reference.id}")
        .border(1.dp, if (isSelected) TerminalGreen else TerminalGreen.copy(alpha = .2f))
        .clickable(onClick = onClick).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = request, contentDescription = "Thumbnail: ${sample.reference.name}",
            contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp, 64.dp).background(Color.Black))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(sample.reference.name, color = TerminalGreen, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sample.reference.let { if (it.hasDimensions) "${it.width} × ${it.height}" else "Dimensions unknown" },
                color = TerminalGreen.copy(alpha = .8f), fontSize = 11.sp)
            Text(sample.reference.deviceLabel ?: sample.sourceName,
                color = TerminalGreen.copy(alpha = .8f), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
