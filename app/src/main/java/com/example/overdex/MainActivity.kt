package com.example.overdex

import com.example.overdex.ui.screens.CalibrationScreen
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewModelScope
import com.example.overdex.media.MediaManager
import kotlinx.coroutines.launch
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.PokedexFrame
import com.example.overdex.ui.screens.*
import com.example.overdex.ui.theme.OverdexTheme
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {
    private lateinit var mediaManager: MediaManager
    private lateinit var calibrationManager: CalibrationManager
    private var calibrationMode = CalibrationMode.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calibrationManager = CalibrationManager(this)

        val calibration = calibrationManager.load()
        if (!calibration.isCalibrated()) {
            calibrationMode = CalibrationMode.ENEMY_NAME
        }
        Log.d(
            "OVERMON_CALIBRATION",
            "Mode = $calibrationMode"
        )
    //    Log.d("OPENAI_TEST", BuildConfig.OPENAI_API_KEY)
        mediaManager = MediaManager(this)
        enableEdgeToEdge()
        setContent {
            OverdexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PokedexApp(
                        mediaManager = mediaManager,
                        calibrationManager = calibrationManager,
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
    modifier: Modifier = Modifier
){
    val navController = rememberNavController()
    val viewModel: PokedexViewModel = viewModel()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    var filterSettings by remember { mutableStateOf(FilterSettings()) }
    val onCycleFilter = {
        navController.navigate("calibration")
    }


    NavHost(
        navController = navController,
        startDestination = "main_menu",
        modifier = modifier,
    ) {
        composable("main_menu") {
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onSelect = onCycleFilter
            ) { _ ->
                MainMenuScreen(
                    isServiceRunning = isServiceRunning,
                    onModuleSelect = { module ->
                        when (module) {
                            "overdex" -> navController.navigate("list")
                            "start.service" -> viewModel.startDroidBallService()
                            "stop.service" -> viewModel.stopDroidBallService()
                            "review.kit" -> navController.navigate("module/review.kit/OFFLINE/this component is under active development.")
                            "battle.log" -> navController.navigate("battle_log")
                            "settings" -> navController.navigate("settings_module")
                            else -> navController.navigate("module/$module/UNAVAILABLE/check future releases.")
                        }
                    },
                    onShutdown = {
                        exitProcess(0)
                    }
                )
            }
        }
        composable("battle_log") {
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onSelect = onCycleFilter,
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
            
            val status = try { ModuleStatus.valueOf(statusStr) } catch(e: Exception) { ModuleStatus.UNAVAILABLE }
            
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onSelect = onCycleFilter
            ) { _ ->
                ModuleScreen(
                    title = title,
                    status = status,
                    description = description,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("settings_module") {
            PokedexFrame(
                showBattleOverlay = false,
                isServiceRunning = isServiceRunning,
                filterSettings = filterSettings,
                onFilterSettingsChange = { filterSettings = it },
                onSelect = onCycleFilter
            ) { _ ->
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("calibration") {
            CalibrationScreen(
                calibrationManager = calibrationManager
            )
        }
        composable("list") {
            PokedexListScreen(
                viewModel = viewModel,
                filterSettings = filterSettings,
                onFilterSettingsChange = { newSettings -> filterSettings = newSettings },
                onSelect = onCycleFilter,
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
                    onSelect = onCycleFilter,
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
                    onEvolutionClick = { evolutionId ->
                        navController.navigate("detail/$evolutionId")
                    },
                    viewModel = viewModel,
                    isServiceRunning = isServiceRunning
                )
            }        }
    }
}
