package com.example.overdex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.preview.DirectoryCropImage
import com.example.overdex.battle.preview.DirectoryCropPreviewState
import com.example.overdex.battle.preview.DirectoryCropSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Last captured source pixels for calibration checks, within the directory's existing LCD. */
@Composable
fun DirectoryCropPreviewLcd(state: DirectoryCropPreviewState, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm:ss", locale) }
    val latest = state.latestCaptureMillis
    Column(modifier.fillMaxSize().testTag("directory-crop-preview").padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (latest == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.isLoading) "Loading captured crops…" else "Record a battle to check calibration",
                    modifier = Modifier.testTag("directory-crop-preview-empty"),
                    color = CropPreviewText, fontSize = 10.sp, lineHeight = 12.sp,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Last captured ${timeFormat.format(Date(latest))}",
                    modifier = Modifier.weight(1f).testTag("directory-crop-preview-timestamp"),
                    color = CropPreviewText, fontSize = 9.sp, lineHeight = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
                if (state.isLoading) Text("Loading", color = CropPreviewText,
                    fontSize = 8.sp, lineHeight = 10.sp, style = CropPreviewTextStyle)
            }
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_SPECIES, DirectoryCropSlot.OPPONENT_SPECIES,
                timeFormat, Modifier.weight(1.1f))
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_HP, DirectoryCropSlot.OPPONENT_HP,
                timeFormat, Modifier.weight(.8f))
            CropPreviewPair(state, DirectoryCropSlot.PLAYER_BALLS, DirectoryCropSlot.OPPONENT_BALLS,
                timeFormat, Modifier.weight(1f))
            CropPreviewTile(DirectoryCropSlot.ANNOUNCEMENT, state.crops[DirectoryCropSlot.ANNOUNCEMENT],
                timeFormat, Modifier.fillMaxWidth().weight(1.3f))
        }
    }
}

@Composable
private fun CropPreviewPair(
    state: DirectoryCropPreviewState,
    player: DirectoryCropSlot,
    opponent: DirectoryCropSlot,
    timeFormat: SimpleDateFormat,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CropPreviewTile(player, state.crops[player], timeFormat, Modifier.weight(1f).fillMaxHeight())
        CropPreviewTile(opponent, state.crops[opponent], timeFormat, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun CropPreviewTile(
    slot: DirectoryCropSlot,
    crop: DirectoryCropImage?,
    timeFormat: SimpleDateFormat,
    modifier: Modifier,
) {
    val label = when (slot) {
        DirectoryCropSlot.PLAYER_SPECIES -> "Your name"
        DirectoryCropSlot.OPPONENT_SPECIES -> "Opponent name"
        DirectoryCropSlot.PLAYER_HP -> "Your HP"
        DirectoryCropSlot.OPPONENT_HP -> "Opponent HP"
        DirectoryCropSlot.PLAYER_BALLS -> "Your Poké Balls"
        DirectoryCropSlot.OPPONENT_BALLS -> "Opponent Poké Balls"
        DirectoryCropSlot.ANNOUNCEMENT -> "Announcement"
    }
    val tag = slot.name.lowercase(Locale.ROOT)
    val bitmap = crop?.bitmap?.takeUnless { it.isRecycled }
    val capturedTime = crop?.let { timeFormat.format(Date(it.capturedAtMillis)) }
    val availability = when {
        crop == null -> "Not captured"
        bitmap == null -> "Image unavailable"
        else -> "Captured $capturedTime"
    }
    Column(modifier.testTag("directory-crop-$tag").semantics {
        contentDescription = "$label crop"
        stateDescription = availability
    }) {
        Text(label, color = CropPreviewText, fontSize = 8.sp, lineHeight = 9.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(),
                    contentDescription = "$label crop, captured at $capturedTime",
                    modifier = Modifier.fillMaxSize().testTag("directory-crop-image-$tag"),
                    contentScale = ContentScale.Fit,
                    colorFilter = null)
            } else {
                Text(availability, color = CropPreviewText.copy(alpha = .7f),
                    fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = CropPreviewTextStyle)
            }
        }
    }
}

private val CropPreviewText = Color(0xFFD4E7CF)
private val CropPreviewTextStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
