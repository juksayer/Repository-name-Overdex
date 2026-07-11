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
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.RecognizedPokemon
import com.example.overdex.model.toOwnedPokemon
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import com.example.overdex.ui.theme.TerminalGreen
import android.util.Log

enum class CalibrationMode {
    MOVE, WIDTH, HEIGHT
}

enum class EvidenceStatus {
    VALID, MISSING, CONFLICTING, NOT_OBSERVED
}

@Composable
fun EvidenceRow(
    label: String,
    value: String?,
    status: EvidenceStatus
) {
    if (status == EvidenceStatus.NOT_OBSERVED) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalText(
            text = when (status) {
                EvidenceStatus.VALID -> "✓"
                EvidenceStatus.CONFLICTING -> "?"
                EvidenceStatus.MISSING -> "⚠"
                else -> ""
            },
            color = when (status) {
                EvidenceStatus.VALID -> TerminalGreen
                EvidenceStatus.CONFLICTING -> Color.Yellow
                EvidenceStatus.MISSING -> Color.Red
                else -> Color.Gray
            },
            modifier = Modifier.width(20.dp)
        )
        Column {
            TerminalText(text = label, color = TerminalDimGreen, fontSize = 10.sp)
            TerminalText(
                text = value ?: "Not Recognized",
                color = if (status != EvidenceStatus.MISSING) Color.White else Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun CaptureVerificationScreen(
    viewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CaptureTemplateManager(context) }
    
    var currentTemplate by remember { mutableStateOf(manager.getSummaryTemplate()) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    var selectedRegionId by remember { mutableStateOf<String?>(null) }
    var calibrationMode by remember { mutableStateOf(CalibrationMode.MOVE) }
    
    var captureLibrary by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var imageSize by remember { mutableStateOf<Size?>(null) }
    
    // Extraction state
    var observations by remember { mutableStateOf<List<CaptureObservation>?>(null) }
    var recognitionResults by remember { mutableStateOf<Map<String, List<RecognitionResult<*>>>>(emptyMap()) }
    var isInspectionMode by remember { mutableStateOf(false) }
    var saveConfirmation by remember { mutableStateOf<String?>(null) }

    // Result of mapping (local only for now)
    var lastMappedPokemon by remember { mutableStateOf<OwnedPokemon?>(null) }
    var recognizedFamily by remember { mutableStateOf<List<String>>(emptyList()) }
    var resolvedSpeciesData by remember { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }

    // Unified "best understanding" model
    val recognizedPokemon = remember(recognitionResults, recognizedFamily) {
        val speciesResult = recognitionResults["SpeciesName"]?.find { it.recognizer == "SpeciesNameRecognizer" }
        
        val cpResult = recognitionResults["CombatPower"]?.find { it.recognizer == "CombatPowerRecognizer" }
        val fastMoveResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "MoveNameRecognizer" }
            ?: recognitionResults["SummaryFastMove"]?.find { it.recognizer == "MoveNameRecognizer" }
            
        val chargedMoveAResult = recognitionResults["ChargedMoveRowA"]?.find { it.recognizer == "MoveNameRecognizer" }
        val chargedMoveBResult = recognitionResults["ChargedMoveRowB"]?.find { it.recognizer == "MoveNameRecognizer" }
        
        val shadowBonusResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "ShadowBonusRecognizer" }
            ?: recognitionResults["SummaryFastMove"]?.find { it.recognizer == "ShadowBonusRecognizer" }
        
        RecognizedPokemon(
            species = speciesResult?.value as? String,
            family = recognizedFamily,
            cp = cpResult?.value as? Int,
            fastMove = fastMoveResult?.value as? String,
            chargedMoveA = chargedMoveAResult?.value as? String,
            chargedMoveB = chargedMoveBResult?.value as? String,
            shadowBonus = shadowBonusResult?.value as? Int
        )
    }

    // Resolve Family evidence and full species data
    LaunchedEffect(recognitionResults) {
        val speciesName = recognizedPokemon.species
        if (speciesName != null) {
            resolvedSpeciesData = viewModel.getPokemonByName(speciesName.trim())
        } else {
            resolvedSpeciesData = null
        }

        val candyResult = recognitionResults["CandyPanel"]?.find { it.recognizer == "CandyPanelSpeciesRecognizer" }
        val candyName = candyResult?.value as? String
        if (candyName != null) {
            recognizedFamily = viewModel.getEvolutionFamily(candyName)
        } else {
            recognizedFamily = emptyList()
        }
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

    LaunchedEffect(saveConfirmation) {
        if (saveConfirmation != null) {
            kotlinx.coroutines.delay(1000)
            saveConfirmation = null
        }
    }

    PokedexFrame(
        onUp = {
            if (!isInspectionMode && selectedRegionId != null) {
                val region = currentTemplate.regions.find { it.id == selectedRegionId }
                if (region != null) {
                    val updated = region.copy(y = (region.y - 0.005f).coerceAtLeast(0f))
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onDown = {
            if (!isInspectionMode && selectedRegionId != null) {
                val region = currentTemplate.regions.find { it.id == selectedRegionId }
                if (region != null) {
                    val updated = region.copy(y = (region.y + 0.005f).coerceAtMost(1f - region.height))
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onLeft = {
            if (!isInspectionMode) {
                if (selectedRegionId != null) {
                    val region = currentTemplate.regions.find { it.id == selectedRegionId }
                    if (region != null) {
                        val updated = region.copy(x = (region.x - 0.005f).coerceAtLeast(0f))
                        manager.saveAdjustment(currentTemplate.name, updated)
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                        )
                    }
                } else if (currentIndex > 0) {
                    currentIndex--
                }
            }
        },
        onRight = {
            if (!isInspectionMode) {
                if (selectedRegionId != null) {
                    val region = currentTemplate.regions.find { it.id == selectedRegionId }
                    if (region != null) {
                        val updated = region.copy(x = (region.x + 0.005f).coerceAtMost(1f - region.width))
                        manager.saveAdjustment(currentTemplate.name, updated)
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                        )
                    }
                } else if (currentIndex < captureLibrary.size - 1) {
                    currentIndex++
                }
            }
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
            } else {
                // INSPECTION MODE: Accept Import
                scope.launch {
                    val speciesName = recognizedPokemon.species
                    if (speciesName != null) {
                        Log.d("CAPTURE_DIAGNOSTICS", "Recognized Species: \"$speciesName\"")
                        
                        val lookupKey = speciesName.trim()
                        Log.d("CAPTURE_DIAGNOSTICS", "Lookup Key: \"$lookupKey\"")
                        
                        val speciesData = viewModel.getPokemonByName(lookupKey)
                        if (speciesData != null) {
                            Log.d("CAPTURE_DIAGNOSTICS", "Match: Yes (#${speciesData.id})")
                            
                            val owned = recognizedPokemon.toOwnedPokemon(speciesData.id)
                            lastMappedPokemon = owned
                            collectionViewModel.addOwnedPokemon(owned)
                            Log.d("CAPTURE_IMPORT", "Saved OwnedPokemon to database: $owned")
                            saveConfirmation = "Import Accepted (Saved to Database)"
                        } else {
                            Log.d("CAPTURE_DIAGNOSTICS", "Match: No")
                            saveConfirmation = "Error: Species Not Found ($speciesName)"
                        }
                    } else {
                        val errorSuffix = if (recognizedPokemon.family.isNotEmpty()) {
                            " (Family: ${recognizedPokemon.family.joinToString("/")})"
                        } else ""
                        saveConfirmation = "Error: No Species Recognized$errorSuffix"
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

            if (saveConfirmation != null) {
                TerminalText(
                    text = saveConfirmation!!,
                    color = com.example.overdex.ui.theme.TerminalGreen,
                    fontSize = 10.sp
                )
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

                            if (recognizedPokemon.species != null) {
                                TerminalText(
                                    text = recognizedPokemon.species.uppercase(),
                                    fontSize = 24.sp,
                                    color = com.example.overdex.ui.theme.TerminalPurple
                                )
                            } else if (recognizedPokemon.family.isNotEmpty()) {
                                TerminalText(text = "POSSIBLE FAMILY", color = TerminalDimGreen, fontSize = 12.sp)
                                recognizedPokemon.family.forEach { member ->
                                    TerminalText(text = " • $member", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TerminalText(
                                    text = "Waiting for additional evidence...",
                                    color = Color.Yellow,
                                    fontSize = 11.sp
                                )
                            } else {
                                TerminalText(text = "UNKNOWN", color = TerminalDimGreen, fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            TerminalText(text = "EVIDENCE", fontSize = 12.sp, color = TerminalDimGreen)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Evidence Rows
                            EvidenceRow(
                                label = "Species Name",
                                value = recognizedPokemon.species,
                                status = if (recognizedPokemon.species != null) EvidenceStatus.VALID else EvidenceStatus.MISSING
                            )
                            EvidenceRow(
                                label = "Candy Family",
                                value = if (recognizedPokemon.family.isNotEmpty()) "${recognizedPokemon.family.first()} Family" else null,
                                status = if (recognizedPokemon.family.isNotEmpty()) EvidenceStatus.VALID else EvidenceStatus.MISSING
                            )
                            EvidenceRow(
                                label = "Type Icons",
                                value = null, // Not yet implemented
                                status = EvidenceStatus.MISSING
                            )
                            EvidenceRow(
                                label = "Combat Power",
                                value = recognizedPokemon.cp?.toString(),
                                status = if (recognizedPokemon.cp != null) EvidenceStatus.VALID else EvidenceStatus.MISSING
                            )

                            // Move Validation
                            val fastMoveStatus = when {
                                recognizedPokemon.fastMove != null -> {
                                    if (resolvedSpeciesData == null) EvidenceStatus.VALID
                                    else if (resolvedSpeciesData!!.fastMoves.any { it.name.equals(recognizedPokemon.fastMove, ignoreCase = true) }) EvidenceStatus.VALID
                                    else EvidenceStatus.CONFLICTING
                                }
                                currentTemplate.name == "PokemonGoSummaryTemplate" -> EvidenceStatus.NOT_OBSERVED
                                else -> EvidenceStatus.MISSING
                            }
                            
                            val chargedMoveAStatus = when {
                                recognizedPokemon.chargedMoveA != null -> {
                                    if (resolvedSpeciesData == null) EvidenceStatus.VALID
                                    else if (resolvedSpeciesData!!.chargedMoves.any { it.name.equals(recognizedPokemon.chargedMoveA, ignoreCase = true) }) EvidenceStatus.VALID
                                    else EvidenceStatus.CONFLICTING
                                }
                                currentTemplate.name == "PokemonGoSummaryTemplate" -> EvidenceStatus.NOT_OBSERVED
                                else -> EvidenceStatus.MISSING
                            }

                            val chargedMoveBStatus = when {
                                recognizedPokemon.chargedMoveB != null -> {
                                    if (resolvedSpeciesData == null) EvidenceStatus.VALID
                                    else if (resolvedSpeciesData!!.chargedMoves.any { it.name.equals(recognizedPokemon.chargedMoveB, ignoreCase = true) }) EvidenceStatus.VALID
                                    else EvidenceStatus.CONFLICTING
                                }
                                currentTemplate.name == "PokemonGoSummaryTemplate" -> EvidenceStatus.NOT_OBSERVED
                                else -> EvidenceStatus.MISSING
                            }

                            EvidenceRow(
                                label = "Fast Move",
                                value = recognizedPokemon.fastMove,
                                status = fastMoveStatus
                            )
                            
                            if (recognizedPokemon.chargedMoveA != null || chargedMoveAStatus == EvidenceStatus.CONFLICTING) {
                                EvidenceRow(
                                    label = "Charged Move A",
                                    value = recognizedPokemon.chargedMoveA,
                                    status = chargedMoveAStatus
                                )
                            }

                            if (recognizedPokemon.chargedMoveB != null || chargedMoveBStatus == EvidenceStatus.CONFLICTING) {
                                EvidenceRow(
                                    label = "Charged Move B",
                                    value = recognizedPokemon.chargedMoveB,
                                    status = chargedMoveBStatus
                                )
                            }

                            if (recognizedPokemon.shadowBonus != null) {
                                EvidenceRow(
                                    label = "Shadow Bonus",
                                    value = "+${recognizedPokemon.shadowBonus}%",
                                    status = EvidenceStatus.VALID
                                )
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
