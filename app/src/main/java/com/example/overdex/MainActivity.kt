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
import com.example.overdex.ui.screens.PokedexListScreen
import com.example.overdex.ui.screens.PokemonDetailScreen
import com.example.overdex.ui.theme.OverdexTheme



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
    var filterSettings by remember { mutableStateOf(FilterSettings()) }
    val calibrationMode = CalibrationMode.ENEMY_NAME
    val onCycleFilter = {
        filterSettings = when {
            !filterSettings.isEnabled -> FilterSettings(isEnabled = true, scanlineIntensity = 0.1f)
            filterSettings.scanlineIntensity < 0.3f -> filterSettings.copy(scanlineIntensity = 0.4f)
            else -> filterSettings.copy(isEnabled = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "list",
        modifier = modifier,
    ) {
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
                    onFilterSettingsChange = { newSettings -> filterSettings = newSettings },
                    onSelect = onCycleFilter,
                    onBackClick = { navController.popBackStack() },
                ) { url ->
                    mediaManager.playSound(url)
                }
            }
        }
    }
}
