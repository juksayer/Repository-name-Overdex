package com.example.overdex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.model.navigation.FlattenedNode

/** The directory LCD describes the selection. Calibration is an explicitly opened mode. */
@Composable
fun DirectorySelectionPreviewLcd(
    visibleNodes: List<FlattenedNode>,
    selectedPath: String,
    modifier: Modifier = Modifier,
) {
    val preview = remember(visibleNodes, selectedPath) {
        directorySelectionPreview(visibleNodes, selectedPath)
    } ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(selectedPath) { scroll.scrollTo(0) }
    val typeface = remember { FontFamily(Font(R.font.share_tech_mono_regular)) }
    val phosphor = Color(0xFF76ED91)
    Column(
        modifier.fillMaxSize().testTag("directory-selection-preview")
            .verticalScroll(scroll).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(preview.title, Modifier.testTag("directory-preview-title"),
            color = Color(0xFFCCFFD7), fontFamily = typeface,
            fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        Text(preview.description, Modifier.testTag("directory-preview-purpose"),
            color = phosphor, fontFamily = typeface, fontSize = 11.sp, lineHeight = 13.sp)
        preview.entries.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().testTag("directory-preview-entry:$index")
                .semantics(mergeDescendants = true) {}) {
                Text(if (index == preview.entries.lastIndex) "└" else "├",
                    Modifier.width(12.dp), color = phosphor.copy(alpha = .6f),
                    fontFamily = typeface, fontSize = 11.sp, lineHeight = 13.sp)
                Text(entry.label, color = phosphor,
                    fontFamily = typeface, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
        Column(Modifier.fillMaxWidth().testTag("directory-preview-how-to")
            .padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            preview.controls.forEach { control ->
                Text(control, color = phosphor.copy(alpha = .8f),
                    fontFamily = typeface, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}
