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
import com.example.overdex.data.observation.GuidedObservationPipeline
import com.example.overdex.data.observation.ObservationStage
import com.example.overdex.data.observation.PipelineStatus
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.RecognizedPokemon
import com.example.overdex.model.toOwnedPokemon
import com.example.overdex.model.observation.CaptureObservation
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.PokemonNameObservation
import com.example.overdex.model.observation.CombatPowerObservation
import com.example.overdex.model.observation.FastMoveObservation
import com.example.overdex.model.observation.ChargedMoveObservation
import com.example.overdex.model.observation.ShadowStatusObservation
import com.example.overdex.model.observation.ObservationSource
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.components.ObservationRegionOverlay
import com.example.overdex.ui.components.SHOW_OBSERVATION_REGIONS
import com.example.overdex.model.ObservationRegions
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.overdex.ui.theme.TerminalPurple
import androidx.compose.ui.text.font.FontWeight
import com.example.overdex.ui.theme.TerminalGreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

import com.example.overdex.ui.components.PokemonTypeIcon
import android.util.Log

enum class CalibrationMode {
    MOVE, WIDTH, HEIGHT
}

@Composable
fun CalibrationStatusPanel(
    mode: CalibrationMode,
    regionId: String?,
    saveConfirmation: String?
) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(8.dp)
            .width(180.dp)
    ) {
        TerminalText(text = "MODE", color = TerminalDimGreen, fontSize = 9.sp)
        TerminalText(
            text = when (mode) {
                CalibrationMode.MOVE -> "Move Box"
                CalibrationMode.WIDTH -> "Resize Width"
                CalibrationMode.HEIGHT -> "Resize Height"
            },
            color = Color.White,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        TerminalText(text = "REGION", color = TerminalDimGreen, fontSize = 9.sp)
        TerminalText(
            text = regionId?.replace(Regex("([A-Z])"), " $1")?.trim()?.uppercase() ?: "NONE SELECTED",
            color = if (regionId != null) TerminalPurple else Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalText(text = "CONTROLS", color = TerminalDimGreen, fontSize = 9.sp)
        ControlRow("↑↓←→", when (mode) {
            CalibrationMode.MOVE -> "Move Box"
            CalibrationMode.WIDTH -> "Resize Width"
            CalibrationMode.HEIGHT -> "Resize Height"
        })
        ControlRow("A", "Recognize")
        ControlRow("B", "Exit")
        ControlRow("START", "Switch Template")
        ControlRow("SELECT", "Toggle UI")

        if (saveConfirmation != null && saveConfirmation.contains("Saved")) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(text = saveConfirmation.uppercase(), color = TerminalGreen, fontSize = 10.sp)
        }
    }
}

@Composable
fun ControlRow(key: String, action: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        TerminalText(text = key.padEnd(8), color = TerminalGreen, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        TerminalText(text = action, color = Color.White, fontSize = 9.sp)
    }
}

enum class EvidenceStatus {
    VALID, MISSING, CONFLICTING, NOT_OBSERVED
}

@Composable
fun EvidenceRow(
    label: String,
    value: String? = null,
    status: EvidenceStatus,
    content: (@Composable () -> Unit)? = null
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
            if (content != null) {
                content()
            } else {
                TerminalText(
                    text = value ?: "Not Recognized",
                    color = if (status != EvidenceStatus.MISSING) Color.White else Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun CaptureVerificationScreen(
    viewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onSaveSuccess: (String) -> Unit,
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
    var pipelineStatus by remember { mutableStateOf<PipelineStatus?>(null) }
    var isInspectionMode by remember { mutableStateOf(false) }
    var saveConfirmation by remember { mutableStateOf<String?>(null) }

    // Result of mapping (local only for now)
    var lastMappedPokemon by remember { mutableStateOf<OwnedPokemon?>(null) }
    var recognizedFamily by remember { mutableStateOf<List<String>>(emptyList()) }
    var resolvedSpeciesData by remember { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }

    // Unified "best understanding" model
    val recognizedPokemon = remember(recognitionResults, recognizedFamily) {
        val speciesResult = recognitionResults["SpeciesName"]?.find { it.recognizer == "SpeciesNameRecognizer" }
        val candyResult =
            recognitionResults["CandyPanel"]
                ?.find { it.recognizer == "CandyPanelSpeciesRecognizer" }
        val resolvedSpecies =
            (speciesResult?.value as? String)
                ?: (candyResult?.value as? String)
        
        val cpResult = recognitionResults["CombatPower"]?.find { it.recognizer == "CombatPowerRecognizer" }
        val fastMoveResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "MoveNameRecognizer" }
            ?: recognitionResults["SummaryFastMove"]?.find { it.recognizer == "MoveNameRecognizer" }
            
        val chargedMoveAResult = recognitionResults["ChargedMoveRowA"]?.find { it.recognizer == "MoveNameRecognizer" }
        val chargedMoveBResult = recognitionResults["ChargedMoveRowB"]?.find { it.recognizer == "MoveNameRecognizer" }
        
        val shadowBonusResult = recognitionResults["FastMoveRow"]?.find { it.recognizer == "ShadowBonusRecognizer" }
            ?: recognitionResults["SummaryFastMove"]?.find { it.recognizer == "ShadowBonusRecognizer" }

        RecognizedPokemon(
            species = resolvedSpecies,
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
        pipelineStatus = null
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
            if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetY = (state.offsetY - 0.01f).coerceAtLeast(-state.region.y)
                }
            } else if (!isInspectionMode && selectedRegionId != null) {
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
            if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetY = (state.offsetY + 0.01f).coerceAtMost(1f - state.region.y - state.region.height)
                }
            } else if (!isInspectionMode && selectedRegionId != null) {
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
            if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetX = (state.offsetX - 0.01f).coerceAtLeast(-state.region.x)
                }
            } else if (!isInspectionMode) {
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
            if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetX = (state.offsetX + 0.01f).coerceAtMost(1f - state.region.x - state.region.width)
                }
            } else if (!isInspectionMode) {
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
                pipelineStatus = null
            }
        },
        onA = {
            if (captureLibrary.isEmpty()) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else if (!isInspectionMode) {
                // Perform extraction and recognition via Guided Pipeline
                scope.launch {
                    val request = ImageRequest.Builder(context)
                        .data(captureLibrary[currentIndex])
                        .allowHardware(false) // Required for Bitmap.createBitmap from Canvas/Software
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                        
                        isInspectionMode = true
                        
                        GuidedObservationPipeline.run(bitmap, currentTemplate) { status ->
                            pipelineStatus = status
                            observations = status.observations
                            recognitionResults = status.results
                        }
                    }
                }
            } else {
                // INSPECTION MODE: Accept Import (Phase 1: Session Integration)
                scope.launch {
                    val speciesName = recognizedPokemon.species
                    if (speciesName != null) {
                        val lookupKey = speciesName.trim()
                        val speciesData = viewModel.getPokemonByName(lookupKey)
                        
                        if (speciesData != null) {
                            // 1. Ensure session exists
                            if (collectionViewModel.activeSession.value == null) {
                                collectionViewModel.startRegistrationSession()
                            }

                            // 2. Wrap current recognition into observations
                            val source = ObservationSource.OCR
                            val confidence = Confidence(ConfidenceLevel.OBSERVED, 1.0f)
                            
                            // Species Name
                            collectionViewModel.addObservation(
                                PokemonNameObservation(speciesName, source = source, confidence = confidence)
                            )
                            
                            // Combat Power
                            recognizedPokemon.cp?.let {
                                collectionViewModel.addObservation(
                                    CombatPowerObservation(it, source = source, confidence = confidence)
                                )
                            }
                            
                            // Shadow Status
                            collectionViewModel.addObservation(
                                ShadowStatusObservation(
                                    isShadow = recognizedPokemon.shadowBonus != null,
                                    source = source,
                                    confidence = confidence
                                )
                            )
                            
                            // Moves
                            recognizedPokemon.fastMove?.let {
                                collectionViewModel.addObservation(
                                    FastMoveObservation(speciesName, it, source = source, confidence = confidence)
                                )
                            }
                            recognizedPokemon.chargedMoveA?.let {
                                collectionViewModel.addObservation(
                                    ChargedMoveObservation(speciesName, it, source = source, confidence = confidence)
                                )
                            }
                            recognizedPokemon.chargedMoveB?.let {
                                collectionViewModel.addObservation(
                                    ChargedMoveObservation(speciesName, it, source = source, confidence = confidence)
                                )
                            }

                            // 3. For Phase 1, we still finish immediately but via the session
                            val session = collectionViewModel.activeSession.value
                            if (session != null) {
                                val owned = session.buildSpecimen(speciesData.id)
                                lastMappedPokemon = owned
                                collectionViewModel.addOwnedPokemon(owned)
                                collectionViewModel.clearActiveSession()
                                
                                saveConfirmation = "Import Accepted (via Session)"
                                onSaveSuccess(owned.id)
                            }
                        } else {
                            saveConfirmation = "Error: Species Not Found ($speciesName)"
                        }
                    } else {
                        saveConfirmation = "Error: No Species Recognized"
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
                if (isInspectionMode) {
                    // Inspection View: Show pipeline status and observations
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        // PIPELINE PROGRESS
                        item {
                            TerminalHeader(text = "observation pipeline")
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val status = pipelineStatus
                            if (status == null) {
                                TerminalText(
                                    text = "INITIALIZING...",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            } else {
                                ObservationStage.ALL.forEach { stage ->
                                    if (stage == null) return@forEach
                                    
                                    val isCompleted = status.completedStages.contains(stage)
                                    val isCurrent = status.currentStage == stage
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TerminalText(
                                            text = when {
                                                isCompleted -> "✓"
                                                isCurrent -> "»"
                                                else -> " "
                                            },
                                            color = if (isCompleted) TerminalGreen else TerminalPurple,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        TerminalText(
                                            text = stage.label.uppercase(),
                                            color = if (isCompleted) Color.White else if (isCurrent) TerminalPurple else Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (observations != null) {
                            items(observations!!) { observation: CaptureObservation ->
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
                            if (resolvedSpeciesData != null) {
                                EvidenceRow(
                                    label = "Types",
                                    status = EvidenceStatus.VALID
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        resolvedSpeciesData!!.types.forEach { type ->
                                            PokemonTypeIcon(type)
                                        }
                                    }
                                }
                            } else {
                                EvidenceRow(
                                    label = "Types",
                                    value = null,
                                    status = EvidenceStatus.MISSING
                                )
                            }
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

                        ObservationRegionOverlay(imageSize = imageSize)

                        if (!isInspectionMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                CalibrationStatusPanel(
                                    mode = calibrationMode,
                                    regionId = selectedRegionId,
                                    saveConfirmation = saveConfirmation
                                )
                            }
                        }
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
