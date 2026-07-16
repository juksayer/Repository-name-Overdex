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
import com.example.overdex.data.observation.*
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.toOwnedPokemon
import com.example.overdex.model.observation.*
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.*
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
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.overdex.model.ObservationRegions

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
    
    var currentTemplate by remember { mutableStateOf(manager.getPokemonDetailTemplate()) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    
    var captureLibrary by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var imageSize by remember { mutableStateOf<Size?>(null) }
    
    // Extraction state
    var observations by remember { mutableStateOf<List<CaptureObservation>?>(null) }
    var recognitionResults by remember { mutableStateOf<Map<String, List<RecognitionResult<*>>>>(emptyMap()) }
    var pipelineStatus by remember { mutableStateOf<PipelineStatus?>(null) }
    var isInspectionMode by remember { mutableStateOf(false) }
    var isManualSpeciesSelection by remember { mutableStateOf(false) }
    var manualSpecies by remember { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }
    var selectedRegionId by remember { mutableStateOf<String?>(null) }
    var calibrationMode by remember { mutableStateOf(CalibrationMode.MOVE) }
    var saveConfirmation by remember { mutableStateOf<String?>(null) }

    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val manualNav = rememberHandheldNavigationController(
        itemCount = { pokemonItems.itemCount + 1 }
    )

    // Requirement 2: Registration Assessment (Single Source of Truth)
    val assessment by produceState(RegistrationAssessment(0f), recognitionResults, manualSpecies, pipelineStatus) {
        value = RegistrationEngine.assess(pipelineStatus?.captureId ?: "00000", recognitionResults, manualSpecies, viewModel)
    }

    // Requirement 2: Service Console Model
    val panelState = remember(recognitionResults, assessment, pipelineStatus) {
        ServiceConsoleModel.createPanelState(pipelineStatus?.captureId ?: "00000", recognitionResults, assessment)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            captureLibrary = uris
            currentIndex = 0
            imageSize = null
            observations = null
            recognitionResults = emptyMap()
            isInspectionMode = false
            manualSpecies = null
        }
    }

    LaunchedEffect(currentIndex) {
        imageSize = null
        observations = null
        recognitionResults = emptyMap()
        pipelineStatus = null
        isInspectionMode = false
        manualSpecies = null
    }

    PokedexFrame(
        onUp = {
            if (isManualSpeciesSelection) manualNav.moveUp()
            else if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetY = (state.offsetY - 0.01f).coerceAtLeast(-state.region.y)
                }
            } else if (!isInspectionMode && selectedRegionId != null) {
                val region = currentTemplate.regions.find { it.id == selectedRegionId }
                if (region != null) {
                    val updated = when (calibrationMode) {
                        CalibrationMode.MOVE -> region.copy(y = (region.y - 0.005f).coerceAtLeast(0f))
                        CalibrationMode.WIDTH -> region
                        CalibrationMode.HEIGHT -> region.copy(height = (region.height - 0.005f).coerceAtLeast(0.01f))
                    }
                    if (updated != region) {
                        manager.saveAdjustment(currentTemplate.name, updated)
                        saveConfirmation = "Settings Saved"
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                        )
                    }
                }
            }
        },
        onDown = {
            if (isManualSpeciesSelection) manualNav.moveDown()
            else if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetY = (state.offsetY + 0.01f).coerceAtMost(1f - state.region.y - state.region.height)
                }
            } else if (!isInspectionMode && selectedRegionId != null) {
                val region = currentTemplate.regions.find { it.id == selectedRegionId }
                if (region != null) {
                    val updated = when (calibrationMode) {
                        CalibrationMode.MOVE -> region.copy(y = (region.y + 0.005f).coerceAtMost(1f - region.height))
                        CalibrationMode.WIDTH -> region
                        CalibrationMode.HEIGHT -> region.copy(height = (region.height + 0.005f).coerceAtMost(1f - region.y))
                    }
                    if (updated != region) {
                        manager.saveAdjustment(currentTemplate.name, updated)
                        saveConfirmation = "Settings Saved"
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                        )
                    }
                }
            }
        },
        onLeft = {
            if (isManualSpeciesSelection) manualNav.moveUp()
            else if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetX = (state.offsetX - 0.01f).coerceAtLeast(-state.region.x)
                }
            } else if (!isInspectionMode) {
                if (selectedRegionId != null) {
                    val region = currentTemplate.regions.find { it.id == selectedRegionId }
                    if (region != null) {
                        val updated = when (calibrationMode) {
                            CalibrationMode.MOVE -> region.copy(x = (region.x - 0.005f).coerceAtLeast(0f))
                            CalibrationMode.WIDTH -> region.copy(width = (region.width - 0.005f).coerceAtLeast(0.01f))
                            CalibrationMode.HEIGHT -> region
                        }
                        if (updated != region) {
                            manager.saveAdjustment(currentTemplate.name, updated)
                            currentTemplate = currentTemplate.copy(
                                regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                            )
                        }
                    }
                } else if (currentIndex > 0) {
                    currentIndex--
                }
            }
        },
        onRight = {
            if (isManualSpeciesSelection) manualNav.moveDown()
            else if (SHOW_OBSERVATION_REGIONS && !isInspectionMode) {
                ObservationRegions.getState("Species")?.let { state ->
                    state.offsetX = (state.offsetX + 0.01f).coerceAtMost(1f - state.region.x - state.region.width)
                }
            } else if (!isInspectionMode) {
                if (selectedRegionId != null) {
                    val region = currentTemplate.regions.find { it.id == selectedRegionId }
                    if (region != null) {
                        val updated = when (calibrationMode) {
                            CalibrationMode.MOVE -> region.copy(x = (region.x + 0.005f).coerceAtMost(1f - region.width))
                            CalibrationMode.WIDTH -> region.copy(width = (region.width + 0.005f).coerceAtMost(1f - region.x))
                            CalibrationMode.HEIGHT -> region
                        }
                        if (updated != region) {
                            manager.saveAdjustment(currentTemplate.name, updated)
                            currentTemplate = currentTemplate.copy(
                                regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                            )
                        }
                    }
                } else if (currentIndex < captureLibrary.size - 1) {
                    currentIndex++
                }
            }
        },
        onSelect = { 
            if (!isInspectionMode) {
                if (selectedRegionId != null) {
                    calibrationMode = when (calibrationMode) {
                        CalibrationMode.MOVE -> CalibrationMode.WIDTH
                        CalibrationMode.WIDTH -> CalibrationMode.HEIGHT
                        CalibrationMode.HEIGHT -> CalibrationMode.MOVE
                    }
                } else {
                    isOverlayVisible = !isOverlayVisible
                }
            }
        },
        onStart = {
            if (!isInspectionMode) {
                // START is now a no-op for template switching.
                observations = null
                recognitionResults = emptyMap()
                pipelineStatus = null
                selectedRegionId = null
            }
        },
        onA = {
            if (isManualSpeciesSelection) {
                val index = manualNav.selectedIndex
                if (index > 0 && index <= pokemonItems.itemCount) {
                    pokemonItems[index - 1]?.let {
                        manualSpecies = it
                        isManualSpeciesSelection = false
                    }
                }
            } else if (captureLibrary.isEmpty()) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else if (!isInspectionMode) {
                scope.launch {
                    val request = ImageRequest.Builder(context).data(captureLibrary[currentIndex]).allowHardware(false).build()
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
                when (assessment.recommendedAction) {
                    RegistrationAction.REGISTER -> {
                        scope.launch {
                            val candidate = assessment.candidates.first()
                            val speciesData = viewModel.getPokemonById(candidate.id)
                            if (speciesData != null) {
                                if (collectionViewModel.activeSession.value == null) collectionViewModel.startRegistrationSession()
                                val owned = collectionViewModel.completeRegistrationSession(speciesData.id)
                                if (owned != null) onSaveSuccess(owned.id)
                            }
                        }
                    }
                    RegistrationAction.SELECT_SPECIES -> isManualSpeciesSelection = true
                    else -> {}
                }
            }
        },
        onB = {
            if (isManualSpeciesSelection) isManualSpeciesSelection = false
            else if (isInspectionMode) isInspectionMode = false
            else onBack()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (captureLibrary.isNotEmpty()) {
                AsyncImage(
                    model = captureLibrary[currentIndex],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onState = { if (it is AsyncImagePainter.State.Success) imageSize = it.painter.intrinsicSize }
                )
                
                CaptureTemplateOverlay(
                    template = currentTemplate,
                    isVisible = isOverlayVisible,
                    imageSize = imageSize,
                    selectedRegionId = selectedRegionId,
                    onRegionSelect = { selectedRegionId = it },
                    onRegionUpdate = { updatedRegion ->
                        manager.saveAdjustment(currentTemplate.name, updatedRegion)
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map { if (it.id == updatedRegion.id) updatedRegion else it }
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
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TerminalButton(text = "load capture library", onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                }
            }

            if (isInspectionMode || isManualSpeciesSelection) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (isManualSpeciesSelection) {
                        Box(modifier = Modifier.fillMaxSize().background(TerminalBlack.copy(alpha = 0.95f))) {
                            SpeciesSearchStep(
                                pokedexViewModel = viewModel,
                                selectedIndex = manualNav.selectedIndex,
                                onSelectedIndexChange = { manualNav.handleTouch(it) }
                            )
                        }
                    } else {
                        // Restore full screen lazy column style inside ServiceConsole or wrapper
                        Box(modifier = Modifier.fillMaxSize().background(TerminalBlack)) {
                            ServiceConsole(
                                panelState = panelState,
                                onManualSelect = { isManualSpeciesSelection = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
