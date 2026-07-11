package com.example.overdex

import com.example.overdex.ui.screens.CalibrationScreen
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewModelScope
import com.example.overdex.media.MediaManager
import com.example.overdex.data.TrainerRepository
import com.example.overdex.model.TrainerIdentity
import kotlinx.coroutines.launch
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
    private var selectedRegion = CalibrationRegion.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calibrationManager = CalibrationManager(this)
        trainerRepository = TrainerRepository(this)

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

            OverdexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PokedexApp(
                        mediaManager = mediaManager,
                        calibrationManager = calibrationManager,
                        trainerRepository = trainerRepository,
                        trainerIdentity = trainerIdentity,
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
    trainerIdentity: TrainerIdentity?,
    modifier: Modifier = Modifier
){
    val navController = rememberNavController()
    val viewModel: PokedexViewModel = viewModel()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val hasBootedInSession by viewModel.hasBootedInSession.collectAsState()
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    NavHost(
        navController = navController,
        startDestination = "main_menu",
        modifier = modifier,
    ) {
        composable("main_menu") {
            var selectedIndex by remember { mutableIntStateOf(0) }

            val options = remember {
                listOf(
                    MenuOption("overdex", { navController.navigate("list") }),
                    MenuOption("my collection", { navController.navigate("collection") }),
                    MenuOption("capture test", { navController.navigate("capture_verification") }),
                    MenuOption("trainer profile", { navController.navigate("trainer_profile") }),
                    MenuOption("readme", { navController.navigate("readme") })
                )
            }

            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onUp = {
                    if (selectedIndex > 0) selectedIndex--
                },
                onDown = {
                    if (selectedIndex < options.size - 1) selectedIndex++
                },
                onA = {
                    options[selectedIndex].onActivate()
                },
                onStart = {
                    options[selectedIndex].onActivate()
                },
                onSelect = { /* Reserved */ },
                onB = { /* No action on root screen */ },
                isLogoInteractive = true
            ) { _ ->
                MainMenuScreen(
                    hasBootedInSession = hasBootedInSession,
                    onBootComplete = { viewModel.markBooted() },
                    selectedIndex = selectedIndex,
                    options = options,
                    trainerIdentity = trainerIdentity
                )
            }
        }
        composable("battle_history") {
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onB = { navController.popBackStack() },
                viewModel = viewModel
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
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onB = { navController.popBackStack() },
                viewModel = viewModel
            ) { battleMemory ->
                BattleTimelineScreen(
                    battleMemory = battleMemory,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("module/{title}/{status}/{description}") { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "module"
            val statusStr = backStackEntry.arguments?.getString("status") ?: "UNAVAILABLE"
            val description = backStackEntry.arguments?.getString("description") ?: ""
            
            val status = try { ModuleStatus.valueOf(statusStr) } catch(_: Exception) { ModuleStatus.UNAVAILABLE }
            
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onB = { navController.popBackStack() }
            ) { _ ->
                ModuleScreen(
                    title = title,
                    status = status,
                    description = description,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("readme") {
            ReadmeScreen(
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = {}, // Already on Readme
                onSelect = { /* Reserved */ },
                onBack = { navController.popBackStack() },
                isServiceRunning = isServiceRunning,
                viewModel = viewModel
            )
        }
        composable("calibration") {
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onStart = { /* Reserved */ },
                onSelect = { /* Reserved */ },
                onB = { navController.popBackStack() }
            ) { _ ->
                CalibrationScreen(
                    calibrationManager = calibrationManager
                )
            }
        }
        composable("capture_verification") {
            val collectionViewModel: MyCollectionViewModel = viewModel()
            CaptureVerificationScreen(
                viewModel = viewModel,
                collectionViewModel = collectionViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("trainer_profile") {
            TrainerProfileScreen(
                trainerIdentity = trainerIdentity,
                trainerRepository = trainerRepository,
                onShowQr = { navController.navigate("qr_identity") },
                onScanQr = { navController.navigate("qr_scanner") },
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
                },
                isServiceRunning = isServiceRunning
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
                    viewModel = viewModel,
                    isServiceRunning = isServiceRunning
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
                onBack = { navController.popBackStack() },
                isServiceRunning = isServiceRunning
            )
        }
        composable("owned_detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val collectionViewModel: MyCollectionViewModel = viewModel()
            OwnedPokemonDetailScreen(
                ownedId = id,
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onEditClick = { navController.navigate("owned_edit/$id") },
                onDeleteSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                isServiceRunning = isServiceRunning
            )
        }
        composable("owned_edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val collectionViewModel: MyCollectionViewModel = viewModel()
            OwnedPokemonEditScreen(
                ownedId = id,
                pokedexViewModel = viewModel,
                collectionViewModel = collectionViewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onSaveSuccess = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                isServiceRunning = isServiceRunning
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
                onCancel = { navController.popBackStack() },
                isServiceRunning = isServiceRunning
            )
        }
    }
}
