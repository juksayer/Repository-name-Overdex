package com.example.overdex

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.overdex.data.ChatRepository
import com.example.overdex.data.ChatTransportFactory
import com.example.overdex.data.PartnerRepository
import com.example.overdex.data.SharedPreferencesChatRepository
import com.example.overdex.data.SharedPreferencesPartnerRepository
import com.example.overdex.data.SharedPreferencesTimelineRepository
import com.example.overdex.data.SharedTimelineRepository
import com.example.overdex.data.TrainerRepository
import com.example.overdex.media.MediaManager
import com.example.overdex.model.ChatMessage
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.model.Pokemon
import com.example.overdex.model.SharedEvent
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import com.example.overdex.ui.screens.AccessibilityProbeScreen
import com.example.overdex.ui.screens.AddOwnedPokemonWizard
import com.example.overdex.ui.screens.BattleHistoryScreen
import com.example.overdex.ui.screens.BattlePreviewScreen
import com.example.overdex.ui.screens.BattleTimelineScreen
import com.example.overdex.ui.screens.CalibrationScreen
import com.example.overdex.ui.screens.ChatScreen
import com.example.overdex.ui.screens.EditSpecimenScreen
import com.example.overdex.ui.screens.MatchCalibrationScreen
import com.example.overdex.ui.screens.MainMenuPhase
import com.example.overdex.ui.screens.MainMenuScreen
import com.example.overdex.ui.screens.MatchSightScreen
import com.example.overdex.ui.screens.ModuleScreen
import com.example.overdex.ui.screens.ModuleStatus
import com.example.overdex.ui.screens.MyCollectionScreen
import com.example.overdex.ui.screens.PokedexListScreen
import com.example.overdex.ui.screens.PokemonDetailScreen
import com.example.overdex.ui.screens.QrIdentityScreen
import com.example.overdex.ui.screens.QrScannerScreen
import com.example.overdex.ui.screens.SharedTimelineScreen
import com.example.overdex.ui.screens.SpecimenDetailScreen
import com.example.overdex.ui.screens.TrainerProfileScreen
import com.example.overdex.ui.screens.observatory.SignalObservatoryScreen
import com.example.overdex.ui.theme.OverdexTheme
import kotlinx.coroutines.launch


/**
 * The primary entry point and UI container for the Overdex application.
 * 
 * MainActivity coordinates the initialization of core repositories and managers,
 * and hosts the Jetpack Compose navigation graph for the handheld instrument.
 */
class MainActivity : ComponentActivity() {
    private lateinit var mediaManager: MediaManager
    private lateinit var calibrationManager: CalibrationManager
    private lateinit var trainerRepository: TrainerRepository
    private lateinit var partnerRepository: PartnerRepository
    private lateinit var timelineRepository: SharedTimelineRepository
    private lateinit var chatRepository: ChatRepository
    private var selectedRegion = CalibrationRegion.NONE

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val viewModel = ViewModelProvider(this)[PokedexViewModel::class.java]
        if (result.resultCode == RESULT_OK && result.data != null) {
            viewModel.deployInstrument(result.resultCode, result.data!!)
        } else {
            viewModel.stopObservation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calibrationManager = CalibrationManager(this)
        trainerRepository = TrainerRepository(this)
        timelineRepository = SharedPreferencesTimelineRepository(this)
        partnerRepository = SharedPreferencesPartnerRepository(this)
        chatRepository = SharedPreferencesChatRepository(this)

        lifecycleScope.launch {
            partnerRepository.partner.collect { partner ->
                if (partner != null) {
                    val myId = trainerRepository.getIdentity().trainerId.toString()
                    val transport = ChatTransportFactory.create(this@MainActivity, myId, partner.trainerId)
                    chatRepository.setTransport(transport)
                    //("CHAT_TRANSPORT", "Repository transport updated: ${transport::class.java.simpleName}")
                }
            }
        }

        val identity = trainerRepository.getIdentity()
        Log.d("TRAINER_IDENTITY", "Loaded Identity: ${identity.displayName} (${identity.trainerId})")
        Log.d("TRAINER_IDENTITY", "Seed: ${identity.avatarSeed} | Version: ${identity.appVersionWhenCreated}")

        val calibration = calibrationManager.load()
        if (!calibration.isCalibrated()) {

        }
        Log.d(
            "OVERDEX_CALIBRATION",
            "Mode = $selectedRegion",
        )
        mediaManager = MediaManager(this)
        enableEdgeToEdge()
        setContent {
            val trainerIdentity by trainerRepository.identity.collectAsState()
            val partnerIdentity by partnerRepository.partner.collectAsState()
            val timelineEvents by timelineRepository.events.collectAsState()
            val chatMessages by chatRepository.messages.collectAsState()

            OverdexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding: PaddingValues ->
                    PokedexApp(
                        mediaManager = mediaManager,
                        calibrationManager = calibrationManager,
                        trainerRepository = trainerRepository,
                        partnerRepository = partnerRepository,
                        timelineRepository = timelineRepository,
                        chatRepository = chatRepository,
                        trainerIdentity = trainerIdentity,
                        partnerIdentity = partnerIdentity,
                        timelineEvents = timelineEvents,
                        chatMessages = chatMessages,
                        onStartObservation = { 
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } else {
                                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaManager.release()
    }
}

@Composable
fun PokedexApp(
    modifier: Modifier = Modifier,
    mediaManager: MediaManager,
    calibrationManager: CalibrationManager,
    trainerRepository: TrainerRepository,
    partnerRepository: PartnerRepository,
    timelineRepository: SharedTimelineRepository,
    chatRepository: ChatRepository,
    trainerIdentity: TrainerIdentity?,
    partnerIdentity: PartnerIdentity?,
    timelineEvents: List<SharedEvent>,
    chatMessages: List<ChatMessage>,
    onStartObservation: () -> Unit = {},

){
    val navController = rememberNavController()
    val viewModel: PokedexViewModel = viewModel()
    val hasBootedInSession by viewModel.hasBootedInSession.collectAsState()
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    val treeState by viewModel.treeState.collectAsState()
    val deploymentState by viewModel.deploymentState.collectAsState()
    val frameCount by viewModel.frameCount.collectAsState()

    LaunchedEffect(deploymentState) {
        if (deploymentState == InstrumentDeploymentState.REQUESTING_PERMISSIONS) {
            onStartObservation()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pendingCommand.collect { command ->
            when (command) {
                InstrumentCommand.OpenSearch -> navController.navigate("list")
                InstrumentCommand.OpenCollection -> navController.navigate("specimens/collection")
                InstrumentCommand.AddSpecimen -> navController.navigate("add_pokemon_wizard")
                InstrumentCommand.OpenBattleHistory -> navController.navigate("battle_history")
                InstrumentCommand.OpenBattleLogs -> navController.navigate("battle_log")

                // Temporary redirect while the registration workspace is removed.
                InstrumentCommand.OpenCapture -> navController.navigate("calibration")
                InstrumentCommand.OpenCalibration -> navController.navigate("calibration")

                InstrumentCommand.OpenProfile -> navController.navigate("trainer_profile")
                InstrumentCommand.OpenTimeline -> navController.navigate("shared_timeline")
                InstrumentCommand.OpenChat -> navController.navigate("private_chat")
                InstrumentCommand.OpenAccessibilityProbe -> navController.navigate("accessibility_probe")
                InstrumentCommand.OpenSignalObservatory -> navController.navigate("signal_observatory")
                InstrumentCommand.OpenMatchSight -> navController.navigate("match_sight")
                InstrumentCommand.OpenMatchCalibration -> navController.navigate("match_calibration")
                InstrumentCommand.OpenBattlePreview -> navController.navigate("battle_preview")
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    CompositionLocalProvider(
        com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute provides currentRoute
    ) {
        NavHost(
            navController = navController,
            startDestination = "main_menu",
            modifier = modifier,
        ) {
            composable("main_menu") {
                var phase by remember { mutableStateOf(MainMenuPhase.BOOT) }
                val instrumentState by viewModel.observationSessionState.collectAsState()
                val deploymentState by viewModel.deploymentState.collectAsState()
                val frameCount by viewModel.frameCount.collectAsState()
                ODXFiShell(

                    instrumentState = instrumentState,
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    showBattleOverlay = false,

                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onUp = { if (phase == MainMenuPhase.READY) viewModel.handleUp() },
                    onDown = { if (phase == MainMenuPhase.READY) viewModel.handleDown() },
                    onA = { if (phase == MainMenuPhase.READY) viewModel.handleA() },
                    onB = { if (phase == MainMenuPhase.READY) viewModel.handleB() },
                    onStart = { if (phase == MainMenuPhase.READY) viewModel.startObservation() },
                    onSelect = { /* Reserved */ },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    isLogoInteractive = true
                ) { _ ->
                    MainMenuScreen(
                        hasBootedInSession = hasBootedInSession,
                        onBootComplete = { viewModel.markBooted() },
                        visibleNodes = treeState.visibleNodes,
                        selectedPath = treeState.selectedPath,
                        trainerIdentity = trainerIdentity,
                        onPhaseChange = { phase = it },
                        onNodeSelected = { node ->
                            when (node.path) {
                                "/trainer/chat" -> navController.navigate("private_chat")
                                "/trainer/profile" -> navController.navigate("trainer_profile")
                                "/trainer/collection" -> navController.navigate("specimens/collection")
                                "/observation/search" -> navController.navigate("list")
                                "/observation/history" -> navController.navigate("battle_history")
                                "/observation/logs" -> navController.navigate("battle_log")
                                "/system/calibration" -> navController.navigate("calibration")
                            }
                        }
                    )
                }
            }
            
            composable("battle_history") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onStart = { viewModel.startObservation() },
                    onSelect = { /* Reserved */ },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) { _ ->
                    BattleHistoryScreen(
                        viewModel = viewModel,
                        onBattleClick = { id -> 
                            navController.navigate("module/battle.summary/OFFLINE/View details for battle $id.")
                        },
                        onBack = { navController.debugPopBackStack() }
                    )
                }
            }
            composable("battle_log") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onStart = { viewModel.startObservation() },
                    onSelect = { /* Reserved */ },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) { battleMemory ->
                    if (battleMemory != null) {
                        BattleTimelineScreen(
                            battleMemory = battleMemory,
                            viewModel = viewModel,
                            onBack = { navController.debugPopBackStack() }
                        )
                    } else {
                        com.example.overdex.ui.components.TerminalText("No active battle record.")
                    }
                }
            }
            composable("module/{title}/{status}/{description}") { backStackEntry: NavBackStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "module"
                val statusStr = backStackEntry.arguments?.getString("status") ?: "UNAVAILABLE"
                val description = backStackEntry.arguments?.getString("description") ?: ""
                
                val status = try { ModuleStatus.valueOf(statusStr) } catch(_: Exception) { ModuleStatus.UNAVAILABLE }
                
                ModuleScreen(
                    title = title,
                    status = status,
                    description = description,
                    onBack = { navController.debugPopBackStack() },
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it }
                )
            }

            composable("calibration") {
                DisposableEffect(Unit) {
                    viewModel.setObservationSessionState(ObservationSessionState.CALIBRATING)
                    onDispose {
                        viewModel.setObservationSessionState(ObservationSessionState.IDLE)
                    }
                }

                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onRight = { rightHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onB = { navController.debugPopBackStack() },
                    onStart = { viewModel.startObservation() },
                    onSelect = { /* Reserved */ },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount
                ) { _ ->
                    CalibrationScreen(
                        calibrationManager = calibrationManager,
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onRight = { rightHandler = it },
                        onA = { aHandler = it }
                    )
                }
            }

            composable("trainer_profile") {
                TrainerProfileScreen(
                    viewModel = viewModel,
                    trainerIdentity = trainerIdentity,
                    partnerIdentity = partnerIdentity,
                    trainerRepository = trainerRepository,
                    partnerRepository = partnerRepository,
                    spriteProvider = viewModel.spriteProvider,
                    avatarSpeciesId = 1,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onShowQr = { navController.navigate("qr_identity") },
                    onScanQr = { navController.navigate("qr_scanner") },
                    onViewTimeline = { navController.navigate("shared_timeline") },
                    onChat = { navController.navigate("private_chat") },
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable("private_chat") {
                val collectionViewModel: MyCollectionViewModel = viewModel()
                ChatScreen(
                    trainerIdentity = trainerIdentity,
                    partnerIdentity = partnerIdentity,
                    messages = chatMessages,
                    chatRepository = chatRepository,
                    pokedexViewModel = viewModel,
                    collectionViewModel = collectionViewModel,
                    onPokemonClick = { id -> 
                        viewModel.viewModelScope.launch {
                            viewModel.getPokemonById(id)?.let {
                                mediaManager.playSound(it.cryUrl)
                            }
                        }
                        navController.navigate("detail/$id") 
                    },
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable("shared_timeline") {
                SharedTimelineScreen(
                    partnerIdentity = partnerIdentity,
                    events = timelineEvents,
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable("qr_identity") {
                QrIdentityScreen(
                    trainerIdentity = trainerIdentity,
                    trainerRepository = trainerRepository,
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable("qr_scanner") {
                QrScannerScreen(
                    trainerIdentity = trainerIdentity,
                    partnerRepository = partnerRepository,
                    timelineRepository = timelineRepository,
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable("list") {
                PokedexListScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { newSettings -> filterSettings = newSettings },
                    onStart = { /* Reserved */ },
                    onSelect = { /* Reserved */ },
                    onBack = { navController.debugPopBackStack() },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onPokemonClick = { id ->
                        viewModel.viewModelScope.launch {
                            viewModel.getPokemonById(id)?.let {
                                mediaManager.warmUp(it.cryUrl)
                            }
                        }
                        navController.navigate("detail/$id")
                    }
                )
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id") ?: 0
                var pokemon by remember { mutableStateOf<Pokemon?>(null) }

                LaunchedEffect(id) {
                    pokemon = viewModel.getPokemonById(id)
                }

                if (pokemon != null) {
                    PokemonDetailScreen(
                        pokemon = pokemon!!,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { newSettings ->
                            filterSettings = newSettings
                        },
                        onStart = { /* Reserved */ },
                        onSelect = { /* Reserved */ },
                        onBackClick = { navController.debugPopBackStack() },
                        onPlayCry = { url ->
                            mediaManager.playSound(url)
                        },
                        onWarmUpCry = { url ->
                            mediaManager.warmUp(url)
                        },
                        onMoveClick = { moveName ->
                            viewModel.updateSearchQuery(moveName)
                            navController.debugPopBackStack()
                        },
                        onTypeClick = { type ->
                            viewModel.updateTypeFilter(type)
                            viewModel.updateSearchQuery(type.name)
                            navController.debugPopBackStack()
                        },
                        onRegionClick = { region ->
                            viewModel.updateSearchQuery(region)
                            navController.debugPopBackStack()
                        },
                        onEvolutionClick = { evolutionId ->
                            navController.navigate("detail/$evolutionId")
                        },
                        onLaunchProbe = { navController.navigate("accessibility_probe") },
                        onLaunchObservatory = { navController.navigate("signal_observatory") },
                        viewModel = viewModel
                    )
                }
            }
            composable("specimens/collection") {
                val collectionViewModel: MyCollectionViewModel = viewModel()
                val keyboardController = rememberTerminalKeyboardController()

                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }


                ODXFiShell(
                    showBattleOverlay = false,
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onRight = { rightHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onB = { bHandler?.invoke() },
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    keyboardController = keyboardController,
                    onKeyActivated = { key ->
                        val currentQuery = collectionViewModel.searchQuery.value
                        when (key) {
                            "SPACE" -> collectionViewModel.updateSearchQuery(currentQuery + " ")
                            "DELETE" -> {
                                if (currentQuery.isNotEmpty()) {
                                    collectionViewModel.updateSearchQuery(currentQuery.dropLast(1))
                                }
                            }
                            else -> {
                                collectionViewModel.updateSearchQuery(currentQuery + key)
                            }
                        }
                    }
                ) {
                    MyCollectionScreen(
                        pokedexViewModel = viewModel,
                        collectionViewModel = collectionViewModel,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { filterSettings = it },
                        onAddClick = { navController.navigate("add_pokemon_wizard") },
                        onBack = { navController.debugPopBackStack() },
                        onItemClick = { id ->
                            navController.navigate("specimens/detail/$id")
                        },
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onRight = { rightHandler = it },
                        onA = { aHandler = it },
                        onB = { bHandler = it },
                        keyboardController = keyboardController
                    )
                }
            }
            composable(
                route = "specimens/detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val collectionViewModel: MyCollectionViewModel = viewModel()
                SpecimenDetailScreen(
                    ownedId = id,
                    pokedexViewModel = viewModel,
                    collectionViewModel = collectionViewModel,
                    onEdit = { ownedId -> navController.navigate("specimens/edit/$ownedId") },
                    onBack = { navController.debugPopBackStack() }
                )
            }
            composable(
                route = "specimens/edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val collectionViewModel: MyCollectionViewModel = viewModel()
                EditSpecimenScreen(
                    ownedId = id,
                    pokedexViewModel = viewModel,
                    collectionViewModel = collectionViewModel,
                    onFinish = { navController.debugPopBackStack() },
                    onCancel = { navController.debugPopBackStack() }
                )
            }
            composable("add_pokemon_wizard") {
                val collectionViewModel: MyCollectionViewModel = viewModel()
                AddOwnedPokemonWizard(
                    pokedexViewModel = viewModel,
                    collectionViewModel = collectionViewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onFinish = { navController.debugPopBackStack() },
                    onCancel = { navController.debugPopBackStack() }
                )
            }
            composable("accessibility_probe") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) {
                    AccessibilityProbeScreen(
                        onBack = { navController.debugPopBackStack() }
                    )
                }
            }
            composable("signal_observatory") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) {
                    SignalObservatoryScreen(
                        onBack = { navController.debugPopBackStack() }
                    )
                }
            }
            composable("match_sight") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) {
                    MatchSightScreen(
                        onBack = { navController.debugPopBackStack() }
                    )
                }
            }
            composable("match_calibration") {
                DisposableEffect(Unit) {
                    viewModel.setObservationSessionState(ObservationSessionState.CALIBRATING)
                    onDispose {
                        viewModel.setObservationSessionState(ObservationSessionState.IDLE)
                    }
                }

                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var selectHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var selectLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var startHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                var lcdLine1 by remember { mutableStateOf<String?>(null) }
                var lcdLine2 by remember { mutableStateOf<String?>(null) }

                var lcdDragHandler by remember { mutableStateOf<((Offset) -> Unit)?>(null) }
                var lcdTapHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onRight = { rightHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onSelect = { selectHandler?.invoke() },
                    onSelectLong = { selectLongHandler?.invoke() },
                    onStart = { startHandler?.invoke() },
                    onLcdDrag = { lcdDragHandler?.invoke(it) },
                    onLcdTap = { lcdTapHandler?.invoke() },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() },
                    lcdLine1 = lcdLine1,
                    lcdLine2 = lcdLine2
                ) {
                    MatchCalibrationScreen(
                        calibrationManager = calibrationManager,
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onRight = { rightHandler = it },
                        onA = { aHandler = it },
                        onSelect = { selectHandler = it },
                        onSelectLong = { selectLongHandler = it },
                        onStart = { startHandler = it },
                        onLcdDrag = { lcdDragHandler = it },
                        onLcdTap = { lcdTapHandler = it },
                        onLcdUpdate = { l1, l2 ->
                            lcdLine1 = l1
                            lcdLine2 = l2
                        }
                    )
                }
            }
            composable("battle_preview") {
                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = { navController.navigate("accessibility_probe") },
                    onLaunchObservatory = { navController.navigate("signal_observatory") },
                    onLaunchMatchSight = { navController.navigate("match_sight") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    onB = { navController.debugPopBackStack() }
                ) {
                    BattlePreviewScreen(
                        state = com.example.overdex.presentation.preview.BattlePreviewData.mewtwoDemo()
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavController.debugPopBackStack(): Boolean {
    val before = currentDestination?.route
    val popped = popBackStack()
    val after = currentDestination?.route
    com.example.overdex.diagnostics.DiagnosticLogger.logNav("popBackStack", before, popped, after)
    return popped
}
