package com.example.overdex

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.ui.screens.CalibrationScreen
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewModelScope
import com.example.overdex.media.MediaManager
import androidx.lifecycle.lifecycleScope
import com.example.overdex.data.*
import com.example.overdex.model.*
import com.example.overdex.model.observation.ObservationSessionState
import kotlinx.coroutines.launch
import com.example.overdex.model.navigation.*
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.screens.*
import com.example.overdex.ui.screens.observatory.SignalObservatoryScreen
import com.example.overdex.ui.theme.OverdexTheme
import kotlin.invoke
import kotlin.system.exitProcess


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
                    Log.i("CHAT_TRANSPORT", "Repository transport updated: ${transport::class.java.simpleName}")
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
    modifier: Modifier = Modifier
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
                InstrumentCommand.OpenCapture -> navController.navigate("capture_verification")
                InstrumentCommand.OpenCalibration -> navController.navigate("calibration")
                InstrumentCommand.OpenProfile -> navController.navigate("trainer_profile")
                InstrumentCommand.OpenTimeline -> navController.navigate("shared_timeline")
                InstrumentCommand.OpenChat -> navController.navigate("private_chat")
                InstrumentCommand.OpenReadme -> navController.navigate("readme")
                InstrumentCommand.OpenAccessibilityProbe -> navController.navigate("accessibility_probe")
                InstrumentCommand.OpenSignalObservatory -> navController.navigate("signal_observatory")
                InstrumentCommand.OpenBattlePreview -> navController.navigate("battle_preview")
            }
        }
    }

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
                isLogoInteractive = true
            ) { _ ->
                MainMenuScreen(
                    hasBootedInSession = hasBootedInSession,
                    onBootComplete = { viewModel.markBooted() },
                    visibleNodes = treeState.visibleNodes,
                    selectedPath = treeState.selectedPath,
                    trainerIdentity = trainerIdentity,
                    onPhaseChange = { phase = it }
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
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) { battleMemory ->
                BattleHistoryScreen(
                    viewModel = viewModel,
                    onBattleClick = { id -> 
                        navController.navigate("module/battle.summary/OFFLINE/View details for battle $id.")
                    },
                    onBack = { navController.popBackStack() }
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
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) { battleMemory ->
                BattleTimelineScreen(
                    battleMemory = battleMemory,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
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
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it }
            )
        }
        composable("readme") {
            ReadmeScreen(
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = {}, 
                onSelect = { /* Reserved */ },
                onBack = { navController.popBackStack() },
                viewModel = viewModel
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
                onB = { navController.popBackStack() },
                onStart = { viewModel.startObservation() },
                onSelect = { /* Reserved */ },
                onLaunchProbe = { navController.navigate("accessibility_probe") },
                onLaunchObservatory = { navController.navigate("signal_observatory") },
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
        composable("capture_verification") {
            val collectionViewModel: MyCollectionViewModel = viewModel()
            CaptureVerificationScreen(
                viewModel = viewModel,
                collectionViewModel = collectionViewModel,
                onSaveSuccess = { id ->
                    navController.navigate("specimens/detail/$id")
                },
                onBack = { navController.popBackStack() }
            )
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
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
            )
        }
        composable("shared_timeline") {
            SharedTimelineScreen(
                partnerIdentity = partnerIdentity,
                events = timelineEvents,
                onBack = { navController.popBackStack() }
            )
        }
        composable("qr_identity") {
            QrIdentityScreen(
                trainerIdentity = trainerIdentity,
                trainerRepository = trainerRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("qr_scanner") {
            QrScannerScreen(
                trainerIdentity = trainerIdentity,
                partnerRepository = partnerRepository,
                timelineRepository = timelineRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("list") {
            PokedexListScreen(
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { newSettings -> filterSettings = newSettings },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onBack = { navController.popBackStack() },
                onLaunchProbe = { navController.navigate("accessibility_probe") },
                onLaunchObservatory = { navController.navigate("signal_observatory") },
                onPokemonClick = { id ->
                    viewModel.viewModelScope.launch {
                        viewModel.getPokemonById(id)?.let {
                            mediaManager.playSound(it.cryUrl)
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
            var pokemon by remember { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }

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
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onPlayCry = { url ->
                        mediaManager.playSound(url)
                    },
                    onMoveClick = { moveName ->
                        viewModel.updateSearchQuery(moveName)
                        navController.popBackStack()
                    },
                    onTypeClick = { type ->
                        viewModel.updateTypeFilter(type)
                        viewModel.updateSearchQuery(type.name)
                        navController.popBackStack()
                    },
                    onRegionClick = { region ->
                        viewModel.updateSearchQuery(region)
                        navController.popBackStack()
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
            SpecimensScreen(
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                onItemClick = { id -> navController.navigate("specimens/detail/$id") },
                onBack = { navController.popBackStack() }
            )
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
                onBack = { navController.popBackStack() }
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
                onFinish = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("add_pokemon_wizard") {
            val collectionViewModel: MyCollectionViewModel = viewModel()
            ODXFiShell(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onLaunchProbe = { navController.navigate("accessibility_probe") },
                onLaunchObservatory = { navController.navigate("signal_observatory") },
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) {
                AddOwnedPokemonWizard(
                    pokedexViewModel = viewModel,
                    collectionViewModel = collectionViewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onFinish = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
        composable("accessibility_probe") {
            ODXFiShell(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onLaunchProbe = { navController.navigate("accessibility_probe") },
                onLaunchObservatory = { navController.navigate("signal_observatory") },
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) {
                AccessibilityProbeScreen(
                    onBack = { navController.popBackStack() }
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
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) {
                SignalObservatoryScreen(
                    onBack = { navController.popBackStack() }
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
                deploymentState = deploymentState,
                frameCount = frameCount,
                onB = { navController.popBackStack() }
            ) {
                BattlePreviewScreen(
                    state = com.example.overdex.presentation.preview.BattlePreviewData.mewtwoDemo()
                )
            }
        }
    }
}
