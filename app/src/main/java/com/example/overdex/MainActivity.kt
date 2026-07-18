package com.example.overdex

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
import com.example.overdex.ui.components.PokedexFrame
import com.example.overdex.ui.screens.*
import com.example.overdex.ui.theme.OverdexTheme
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {
    private lateinit var mediaManager: MediaManager
    private lateinit var calibrationManager: CalibrationManager
    private lateinit var trainerRepository: TrainerRepository
    private lateinit var partnerRepository: PartnerRepository
    private lateinit var timelineRepository: SharedTimelineRepository
    private lateinit var chatRepository: ChatRepository
    private var selectedRegion = CalibrationRegion.NONE

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
fun
        PokedexApp(
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
    modifier: Modifier = Modifier
){
    val navController = rememberNavController()
    val viewModel: PokedexViewModel = viewModel()
    val observationSessionState by viewModel.observationSessionState.collectAsState()
    val hasBootedInSession by viewModel.hasBootedInSession.collectAsState()
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    NavHost(
        navController = navController,
        startDestination = "main_menu",
        modifier = modifier,
    ) {
        composable("main_menu") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            var phase by remember { mutableStateOf(MainMenuPhase.BOOT) }

            val nodes = remember {
                listOf(
                    DirectoryNode("specimens", NodeKind.DIRECTORY, action = { navController.navigate("specimens") }),
                    DirectoryNode("battle", NodeKind.DIRECTORY, action = { navController.navigate("battle") }),
                    DirectoryNode("observation", NodeKind.DIRECTORY, action = { navController.navigate("observation") }),
                    DirectoryNode("trainer", NodeKind.DIRECTORY, action = { navController.navigate("trainer") }),
                    DirectoryNode("tools", NodeKind.DIRECTORY, action = { navController.navigate("tools") })
                )
            }

            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = {
                    if (phase == MainMenuPhase.READY && selectedIndex > 0) {
                        selectedIndex--
                    }
                },
                onDown = {
                    if (phase == MainMenuPhase.READY && selectedIndex < nodes.size - 1) {
                        selectedIndex++
                    }
                },
                onA = {
                    if (phase == MainMenuPhase.READY) {
                        nodes[selectedIndex].action?.invoke()
                    }
                },
                onStart = {
                    if (phase == MainMenuPhase.READY) {
                        nodes[selectedIndex].action?.invoke()
                    }
                },
                onSelect = { /* Reserved */ },
                onB = { /* No action on root screen */ },
                isLogoInteractive = true
            ) { _ ->
                MainMenuScreen(
                    hasBootedInSession = hasBootedInSession,
                    onBootComplete = { viewModel.markBooted() },
                    selectedIndex = selectedIndex,
                    nodes = nodes,
                    trainerIdentity = trainerIdentity,
                    onPhaseChange = { phase = it }
                )
            }
        }
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("battle_history") {
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
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
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("battle_log") {
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onB = { navController.popBackStack() }
            ) { battleMemory ->
                BattleTimelineScreen(
                    battleMemory = battleMemory,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
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
                onStart = {}, // Already on Readme
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
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onA = { /* Reserved */ },
                onB = { navController.popBackStack() }
            ) { _ ->
                CalibrationScreen(
                    calibrationManager = calibrationManager
                )
            }
        }
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
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
                    viewModel = viewModel
                )
            }
        }
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("collection") {
            val collectionViewModel: MyCollectionViewModel = viewModel()
            MyCollectionScreen(
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onItemClick = { id -> navController.navigate("owned_detail/$id") },
                onAddClick = { navController.navigate("add_pokemon_wizard") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("owned_detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val collectionViewModel: MyCollectionViewModel = viewModel()
            OwnedPokemonDetailScreen(
                ownedId = id,
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                chatRepository = chatRepository,
                trainerIdentity = trainerIdentity,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onDeleteSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("add_pokemon_wizard") {
            val collectionViewModel: MyCollectionViewModel = viewModel()
            AddOwnedPokemonWizard(
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onFinish = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("specimens") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("search", NodeKind.DIRECTORY, action = { navController.navigate("list") }),
                    DirectoryNode("collection", NodeKind.DIRECTORY, action = { navController.navigate("specimens/collection") }),
                    DirectoryNode("register", NodeKind.ACTION, action = { navController.navigate("add_pokemon_wizard") })
                )
            }

            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/specimens/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("battle") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("history", NodeKind.DIRECTORY, action = { navController.navigate("battle_history") }),
                    DirectoryNode("logs", NodeKind.DIRECTORY, action = { navController.navigate("battle_log") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/battle/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("observation") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("capture", NodeKind.ACTION, action = { navController.navigate("capture_verification") }),
                    DirectoryNode("calibration", NodeKind.ACTION, action = { navController.navigate("calibration") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/observation/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("trainer") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("profile", NodeKind.ACTION, action = { navController.navigate("trainer_profile") }),
                    DirectoryNode("timeline", NodeKind.ACTION, action = { navController.navigate("shared_timeline") }),
                    DirectoryNode("chat", NodeKind.ACTION, action = { navController.navigate("private_chat") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/trainer/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
                )
            }
        }
        composable("tools") {
            var selectedIndex by remember { mutableIntStateOf(0) }
            val nodes = remember {
                listOf(
                    DirectoryNode("readme", NodeKind.ACTION, action = { navController.navigate("readme") })
                )
            }
            PokedexFrame(
                showBattleOverlay = false,
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = { if (selectedIndex > 0) selectedIndex-- },
                onDown = { if (selectedIndex < nodes.size - 1) selectedIndex++ },
                onA = { nodes[selectedIndex].action?.invoke() },
                onB = { navController.popBackStack() },
                onStart = { nodes[selectedIndex].action?.invoke() },
            ) { _ ->
                DirectoryScreen(
                    path = "/tools/",
                    selectedIndex = selectedIndex,
                    nodes = nodes
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
    }
}
