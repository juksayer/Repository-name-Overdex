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
import com.example.overdex.data.observation.ObservationRecognizer
import com.example.overdex.model.RecognizedPokemon
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
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
    var observations by remember { mutableStateOf<List<CaptureObservation>?>(null) }
    var recognitionResults by remember { mutableStateOf<Map<String, List<RecognitionResult<*>>>>(emptyMap()) }
    var isInspectionMode by remember { mutableStateOf(false) }

    // Unified "best understanding" model
    val recognizedPokemon = remember(recognitionResults) {
        val speciesResult = recognitionResults["CandyPanel"]?.find { it.recognizer == "CandyPanelSpeciesRecognizer" }
        val cpResult = recognitionResults["CombatPower"]?.find { it.recognizer == "CombatPowerRecognizer" }
        val fastMoveResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "MoveNameRecognizer" }
        val chargedMoveAResult = recognitionResults["ChargedMoveRowA"]?.find { it.recognizer == "MoveNameRecognizer" }
        val chargedMoveBResult = recognitionResults["ChargedMoveRowB"]?.find { it.recognizer == "MoveNameRecognizer" }
        val shadowBonusResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "ShadowBonusRecognizer" }
        
        RecognizedPokemon(
            species = speciesResult?.value as? String,
            cp = cpResult?.value as? Int,
            fastMove = fastMoveResult?.value as? String,
            chargedMoveA = chargedMoveAResult?.value as? String,
            chargedMoveB = chargedMoveBResult?.value as? String,
            shadowBonus = shadowBonusResult?.value as? Int
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            captureLibrary = uris
            currentIndex = 0
            imageSize = null // Reset size for new batch
            observations = null
            recognitionResults = emptyMap()
            isInspectionMode = false
        }
    }

    LaunchedEffect(currentIndex) {
        imageSize = null
        observations = null
        recognitionResults = emptyMap()
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
                observations = null
                recognitionResults = emptyMap()
            }
        },
        onA = {
            if (captureLibrary.isEmpty()) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else if (!isInspectionMode) {
                // Perform extraction and recognition
                scope.launch {
                    val request = ImageRequest.Builder(context)
                        .data(captureLibrary[currentIndex])
                        .allowHardware(false) // Required for Bitmap.createBitmap from Canvas/Software
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                        val extracted = ObservationCropExtractor.extract(bitmap, currentTemplate)
                        observations = extracted
                        
                        // Run recognizers
                        val results = mutableMapOf<String, List<RecognitionResult<*>>>()
                        extracted.forEach { obs ->
                            val regionResults = ObservationRecognizer.recognize(obs)
                            if (regionResults.isNotEmpty()) {
                                results[obs.regionId] = regionResults
                            }
                        }
                        recognitionResults = results

                        isInspectionMode = true
                    }
                }
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
                if (isInspectionMode && observations != null) {
                    // Inspection View: Show list of observations
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(observations!!) { observation ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                TerminalText(text = "REGION: ${observation.regionId}", color = com.example.overdex.ui.theme.TerminalPurple, fontSize = 10.sp)
                                Image(
                                    bitmap = observation.crop.asImageBitmap(),
                                    contentDescription = observation.regionId,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.Inside
                                )
                                
                                // Display Recognition Results
                                val results = recognitionResults[observation.regionId]
                                if (results != null) {
                                    results.forEach { res ->
                                        if (res.value != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TerminalText(text = "RECOGNIZED", color = com.example.overdex.ui.theme.TerminalPurple, fontSize = 10.sp)
                                            TerminalText(text = res.value.toString(), fontSize = 16.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f))
                            }
                        }

                        // RECOGNITION SUMMARY
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            TerminalHeader(text = "recognition summary")

                            val summaryMap = mutableMapOf<String, String>()

                            recognitionResults.forEach { (regionId, results) ->
                                results.forEach { res ->
                                    if (res.value != null) {
                                        when (res.recognizer) {
                                            "CandyPanelSpeciesRecognizer" -> summaryMap["Species"] = res.value.toString()
                                            "CombatPowerRecognizer" -> summaryMap["Combat Power"] = res.value.toString()
                                            "MoveNameRecognizer" -> {
                                                when (regionId) {
                                                    "FastMoveRow" -> summaryMap["Fast Move"] = res.value.toString()
                                                    "ChargedMoveRowA" -> summaryMap["Charged Move A"] = res.value.toString()
                                                    "ChargedMoveRowB" -> summaryMap["Charged Move B"] = res.value.toString()
                                                }
                                            }
                                            "ShadowBonusRecognizer" -> summaryMap["Shadow Bonus"] = "+${res.value}"
                                        }
                                    }
                                }
                            }

                            // TODO: Replace with RecognizedPokemon model
                            val displayOrder = listOf(
                                "Species",
                                "Combat Power",
                                "Fast Move",
                                "Charged Move A",
                                "Charged Move B",
                                "Shadow Bonus"
                            )

                            displayOrder.forEach { label ->
                                val value = when (label) {
                                    "Species" -> recognizedPokemon.species
                                    "Combat Power" -> recognizedPokemon.cp?.toString()
                                    "Fast Move" -> recognizedPokemon.fastMove
                                    "Charged Move A" -> recognizedPokemon.chargedMoveA
                                    "Charged Move B" -> recognizedPokemon.chargedMoveB
                                    else -> summaryMap[label]
                                }

                                value?.let { summaryValue ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TerminalText(
                                            text = "$label:",
                                            color = TerminalDimGreen,
                                            modifier = Modifier.width(140.dp)
                                        )
                                        TerminalText(text = summaryValue)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
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
