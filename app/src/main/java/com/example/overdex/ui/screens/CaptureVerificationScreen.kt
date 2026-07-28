package com.example.overdex.ui.screens


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.example.overdex.data.observation.BattleObservationPipeline
import com.example.overdex.model.observation.DefaultObservationResolver
import com.example.overdex.model.observation.PokemonNameObservation
import com.example.overdex.data.observation.*
import com.example.overdex.model.observation.*
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.overdex.ui.theme.TerminalPurple
import com.example.overdex.ui.theme.TerminalGreen
import androidx.paging.compose.collectAsLazyPagingItems

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
                CalibrationMode.POSITION -> "Position"
                CalibrationMode.SIZE -> "Size"
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
            CalibrationMode.POSITION -> "Move Box"
            CalibrationMode.SIZE -> "Resize Box"
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
    var captures by remember { mutableStateOf<List<CaptureObservation>?>(null) }
    var history by remember { mutableStateOf<Map<String, List<Observation>>>(emptyMap()) }
    var pipelineStatus by remember { mutableStateOf<PipelineStatus?>(null) }

    var isInspectionMode by remember { mutableStateOf(false) }
    var isManualSpeciesSelection by remember { mutableStateOf(false) }
    var manualSpecies by remember { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }
    var regionCursorIndex by remember { mutableIntStateOf(0) }
    var calibrationMode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var saveConfirmation by remember { mutableStateOf<String?>(null) }
    var showWorkspaceViewer by remember { mutableStateOf(false) }
    var pendingBattleObservation by remember {
        mutableStateOf<Observation?>(null)
    }

    val pokemonItems = viewModel.pagedPokemon.collectAsLazyPagingItems()
    val manualNav = rememberHandheldNavigationController(
        itemCount = { pokemonItems.itemCount + 1 }
    )

    val triggerRecognition = {
        scope.launch {
            val request = ImageRequest.Builder(context).data(captureLibrary[currentIndex]).allowHardware(false).build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                isInspectionMode = true
                viewModel.setObservationSessionState(ObservationSessionState.OBSERVING)
                val input = GalleryObservationInput(bitmap)
                GuidedObservationPipeline.run(
                    input = input,
                    template = currentTemplate,
                    existingSession = pipelineStatus?.session
                ) { status ->
                    pipelineStatus = status
                    captures = status.captures
                    history = status.results
                }
                val completedSession = pipelineStatus?.session
                val speciesObservation = completedSession
                    ?.history
                    ?.get("SpeciesName")
                    ?.let { observations ->
                        DefaultObservationResolver().resolve(observations)
                    } as? PokemonNameObservation

                pendingBattleObservation = speciesObservation
            }
        }
    }

    // Consolidated Panel State (Single Synchronized Snapshot)
    val panelState by produceState(
        initialValue = ServiceConsoleModel.createPanelState(
            pipelineStatus?.captureId ?: "00000",
            emptyMap(),
            RegistrationAssessment(0f)
        ),
        key1 = history,
        key2 = manualSpecies,
        key3 = pipelineStatus
    ) {
        val currentSession = pipelineStatus?.session ?: ObservationSession(
            sessionId = pipelineStatus?.captureId ?: "00000",
            history = history
        )
        val capId = currentSession.sessionId

        value = value.copy(isProcessing = true)

        val newAssessment = RegistrationEngine.assess(currentSession, manualSpecies, viewModel)

        // Sync UI model with session history
        value = ServiceConsoleModel.createPanelState(capId, history, newAssessment)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            captureLibrary = uris
            currentIndex = 0
            imageSize = null
            captures = null
            history = emptyMap()
            isInspectionMode = false
            manualSpecies = null
        }
    }

    LaunchedEffect(currentIndex) {
        imageSize = null
        isInspectionMode = false
    }

    ODXFiShell(
        onUp = {
            if (isManualSpeciesSelection) manualNav.moveUp()
            else if (!isInspectionMode && currentTemplate.regions.isNotEmpty()) {
                val region = currentTemplate.regions[regionCursorIndex]
                val updated = when (calibrationMode) {
                    CalibrationMode.POSITION -> region.copy(y = (region.y - 0.005f).coerceAtLeast(0f))
                    CalibrationMode.SIZE -> region.copy(height = (region.height - 0.005f).coerceAtLeast(0.01f))
                }
                if (updated != region) {
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onDown = {
            if (isManualSpeciesSelection) manualNav.moveDown()
            else if (!isInspectionMode && currentTemplate.regions.isNotEmpty()) {
                val region = currentTemplate.regions[regionCursorIndex]
                val updated = when (calibrationMode) {
                    CalibrationMode.POSITION -> region.copy(y = (region.y + 0.005f).coerceAtMost(1f - region.height))
                    CalibrationMode.SIZE -> region.copy(height = (region.height + 0.005f).coerceAtMost(1f - region.y))
                }
                if (updated != region) {
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onLeft = {
            if (isManualSpeciesSelection) manualNav.moveUp()
            else if (!isInspectionMode && currentTemplate.regions.isNotEmpty()) {
                val region = currentTemplate.regions[regionCursorIndex]
                val updated = when (calibrationMode) {
                    CalibrationMode.POSITION -> region.copy(x = (region.x - 0.005f).coerceAtLeast(0f))
                    CalibrationMode.SIZE -> region.copy(width = (region.width - 0.005f).coerceAtLeast(0.01f))
                }
                if (updated != region) {
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onRight = {
            if (isManualSpeciesSelection) manualNav.moveDown()
            else if (!isInspectionMode && currentTemplate.regions.isNotEmpty()) {
                val region = currentTemplate.regions[regionCursorIndex]
                val updated = when (calibrationMode) {
                    CalibrationMode.POSITION -> region.copy(x = (region.x + 0.005f).coerceAtMost(1f - region.width))
                    CalibrationMode.SIZE -> region.copy(width = (region.width + 0.005f).coerceAtMost(1f - region.x))
                }
                if (updated != region) {
                    manager.saveAdjustment(currentTemplate.name, updated)
                    saveConfirmation = "Settings Saved"
                    currentTemplate = currentTemplate.copy(
                        regions = currentTemplate.regions.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        },
        onSelect = { 
            if (!isInspectionMode) {
                calibrationMode = when (calibrationMode) {
                    CalibrationMode.POSITION -> CalibrationMode.SIZE
                    CalibrationMode.SIZE -> CalibrationMode.POSITION
                }
            }
        },
        onStart = {
            if (isInspectionMode) {
                showWorkspaceViewer = !showWorkspaceViewer
            } else {
                captures = null
                history = emptyMap()
                pipelineStatus = null
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
            } else if (isInspectionMode) {
                triggerRecognition()

                val assessment = panelState.assessment
                when (assessment.recommendedAction) {
                    RegistrationAction.REGISTER -> {
                        scope.launch {
                            val candidate = assessment.candidates.first()
                            val speciesData = viewModel.getPokemonById(candidate.id)
                            if (speciesData != null) {
                                // BRICK #187: Bridge session history to registration
                                val currentSession = pipelineStatus?.session
                                if (currentSession != null) {
                                    collectionViewModel.startRegistrationSession(currentSession.allObservations())
                                } else {
                                    collectionViewModel.startRegistrationSession()
                                }
                                
                                val owned = collectionViewModel.completeRegistrationSession(speciesData.id)
                                if (owned != null) {
                                    pipelineStatus = null
                                    captures = null
                                    history = emptyMap()
                                    manualSpecies = null
                                    isInspectionMode = false
                                    onSaveSuccess(owned.id)
                                }
                            }
                        }
                    }
                    RegistrationAction.SELECT_SPECIES -> isManualSpeciesSelection = true
                    else -> {}
                }
            } else if (currentTemplate.regions.isNotEmpty()) {
                // Truthful acknowledgement: the region is already active, A just "confirms" it
                saveConfirmation = "Region Confirmed"
            }
        },
        onALong = {
            if (!isInspectionMode && !isManualSpeciesSelection && captureLibrary.isNotEmpty()) {
                triggerRecognition()
            }
        },
        onB = {
            if (isManualSpeciesSelection) isManualSpeciesSelection = false
            else if (showWorkspaceViewer) showWorkspaceViewer = false
            else if (isInspectionMode) {
                isInspectionMode = false
                viewModel.setObservationSessionState(ObservationSessionState.IDLE)
            }
            else onBack()
        },
        viewModel = viewModel,
        pipelineStatus = pipelineStatus
    ) { battleMemory ->

        LaunchedEffect(pendingBattleObservation) {
            val observation = pendingBattleObservation ?: return@LaunchedEffect

            BattleObservationPipeline(battleMemory)
                .onObservationReceived(observation)

            pendingBattleObservation = null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (captureLibrary.isNotEmpty()) {
                AsyncImage(
                    model = captureLibrary[currentIndex],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onState = {
                        if (it is AsyncImagePainter.State.Success) {
                            imageSize = it.painter.intrinsicSize
                        }
                    }
                )

                CaptureTemplateOverlay(
                    template = currentTemplate,
                    isVisible = isOverlayVisible,
                    imageSize = imageSize,
                    selectedRegionId = currentTemplate.regions.getOrNull(regionCursorIndex)?.id,
                    onRegionSelect = { id ->
                        val index = currentTemplate.regions.indexOfFirst { it.id == id }
                        if (index >= 0) regionCursorIndex = index
                    },
                    onRegionUpdate = { updatedRegion ->
                        manager.saveAdjustment(currentTemplate.name, updatedRegion)
                        currentTemplate = currentTemplate.copy(
                            regions = currentTemplate.regions.map {
                                if (it.id == updatedRegion.id) updatedRegion else it
                            }
                        )
                    }
                )

                ObservationRegionOverlay(imageSize = imageSize)

                // ...rest of your existing Box content unchanged...

                if (!isInspectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        CalibrationStatusPanel(
                            mode = calibrationMode,
                            regionId = currentTemplate.regions.getOrNull(regionCursorIndex)?.id,
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
                        Box(modifier = Modifier.fillMaxSize().background(TerminalBlack)) {
                            ServiceConsole(
                                panelState = panelState,
                                onManualSelect = { isManualSpeciesSelection = true }
                            )
                        }
                    }
                }
            }

            if (showWorkspaceViewer) {
                ObservationWorkspaceViewer(history = history)
            }
        }
    }
}
