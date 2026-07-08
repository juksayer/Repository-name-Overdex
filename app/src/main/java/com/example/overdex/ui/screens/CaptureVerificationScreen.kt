package com.example.overdex.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.data.ObservationCropExtractor
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.launch

@Composable
fun CaptureVerificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CaptureTemplateManager(context) }
    
    var currentTemplate by remember { mutableStateOf(manager.getSummaryTemplate()) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    var selectedRegionId by remember { mutableStateOf<String?>(null) }
    
    var captureLibrary by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var imageSize by remember { mutableStateOf<Size?>(null) }
    
    // Extraction state
    var extractedCrops by remember { mutableStateOf<Map<String, Bitmap>?>(null) }
    var isInspectionMode by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            captureLibrary = uris
            currentIndex = 0
            imageSize = null // Reset size for new batch
            extractedCrops = null
            isInspectionMode = false
        }
    }

    LaunchedEffect(currentIndex) {
        imageSize = null
        extractedCrops = null
        isInspectionMode = false
    }

    PokedexFrame(
        onUp = { /* Reserved */ },
        onDown = { /* Reserved */ },
        onLeft = {
            if (!isInspectionMode && currentIndex > 0) currentIndex--
        },
        onRight = {
            if (!isInspectionMode && currentIndex < captureLibrary.size - 1) currentIndex++
        },
        onSelect = { if (!isInspectionMode) isOverlayVisible = !isOverlayVisible },
        onStart = {
            if (!isInspectionMode) {
                currentTemplate = if (currentTemplate.name == "PokemonGoSummaryTemplate") {
                    manager.getMovesTemplate()
                } else {
                    manager.getSummaryTemplate()
                }
                selectedRegionId = null // Clear selection on template switch
                extractedCrops = null
            }
        },
        onA = {
            if (isInspectionMode) {
                // Future: Action in inspection mode?
            } else {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        onB = {
            if (isInspectionMode) {
                isInspectionMode = false
            } else {
                onBack()
            }
        }
    ) {
        TerminalScreen {
            // COMPACT HEADER: One line status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalText(
                    text = if (isInspectionMode) "[ INSPECTION MODE ]" else "[ CAPTURE VERIFICATION ]",
                    color = com.example.overdex.ui.theme.TerminalPurple,
                    fontSize = 12.sp
                )
                
                if (!isInspectionMode) {
                    val templateName = currentTemplate.name.removePrefix("PokemonGo").replace("Template", "").uppercase()
                    val imageCounter = if (captureLibrary.isEmpty()) "EMPTY" else "${(currentIndex + 1)} / ${captureLibrary.size}"
                    TerminalText(
                        text = "$templateName | $imageCounter | ${selectedRegionId ?: "NONE"}",
                        fontSize = 10.sp,
                        color = TerminalDimGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TerminalBlack),
                contentAlignment = Alignment.Center
            ) {
                if (isInspectionMode && extractedCrops != null) {
                    // Inspection View: Show list of crops
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(extractedCrops!!.toList()) { (id, bitmap) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                TerminalText(text = "REGION: $id", color = com.example.overdex.ui.theme.TerminalPurple, fontSize = 10.sp)
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = id,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.Inside
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f))
                            }
                        }
                    }
                } else if (captureLibrary.isNotEmpty()) {
                    // Standard Verification View
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = captureLibrary[currentIndex],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    imageSize = state.painter.intrinsicSize
                                }
                            }
                        )
                        
                        CaptureTemplateOverlay(
                            template = currentTemplate,
                            isVisible = isOverlayVisible,
                            imageSize = imageSize,
                            selectedRegionId = selectedRegionId,
                            onRegionSelect = { selectedRegionId = it },
                            onRegionUpdate = { updatedRegion ->
                                manager.saveAdjustment(currentTemplate.name, updatedRegion)
                                // Update local state immediately
                                currentTemplate = currentTemplate.copy(
                                    regions = currentTemplate.regions.map { 
                                        if (it.id == updatedRegion.id) updatedRegion else it 
                                    }
                                )
                            }
                        )
                    }
                } else {
                    // Empty State
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        TerminalButton(
                            text = "load capture library",
                            modifier = Modifier.padding(32.dp),
                            onClick = {
                                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                }
            }
            
            // COMPACT FOOTER: Minimal button footprint
            if (captureLibrary.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isInspectionMode) {
                        TerminalText(text = "B: EXIT INSPECTION", fontSize = 9.sp, color = TerminalDimGreen)
                    } else {
                        TerminalText(text = "A: LOAD | L/R: BROWSE | B: EXIT", fontSize = 9.sp, color = TerminalDimGreen)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(text = "back", onClick = onBack)
            }
        }
    }
}
