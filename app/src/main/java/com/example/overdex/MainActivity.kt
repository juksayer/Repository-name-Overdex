package com.example.overdex

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.replay.ArchivedSpeciesCropRecognizer
import com.example.overdex.battle.observation.CountdownBurstRecorder
import com.example.overdex.battle.observation.CountdownGlyphMatcher
import com.example.overdex.battle.observation.CountdownSampleRecorder
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.comms.TrainerCommsServices
import com.example.overdex.ui.screens.TrainerCommsScreen
import com.example.overdex.data.PartnerRepository
import com.example.overdex.data.SharedPreferencesPartnerRepository
import com.example.overdex.data.SharedPreferencesTimelineRepository
import com.example.overdex.data.SharedTimelineRepository
import com.example.overdex.data.TrainerRepository
import com.example.overdex.media.MediaManager
import com.example.overdex.presentation.DroidballChoreography
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballJourneyPhase
import com.example.overdex.ui.screens.observatory.MatchArchiveDirectoryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.model.Pokemon
import com.example.overdex.model.OwnedPokemonBinder
import com.example.overdex.model.SharedEvent
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.DesktopPointerController
import com.example.overdex.model.navigation.OverdextopAction
import com.example.overdex.model.navigation.OverdextopController
import com.example.overdex.model.navigation.OverdextopDirection
import com.example.overdex.model.navigation.OverdextopEntry
import com.example.overdex.model.navigation.OverdextopSurface
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.DirectoryCropEditDirection
import com.example.overdex.ui.components.rememberDirectoryCropEditor
import com.example.overdex.ui.components.DirectoryCropPreviewLcd
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import com.example.overdex.ui.screens.AccessibilityProbeScreen
import com.example.overdex.ui.screens.AddOwnedPokemonWizard
import com.example.overdex.ui.screens.BattlePreviewScreen
import com.example.overdex.ui.screens.BattleTimelineScreen
import com.example.overdex.ui.screens.CalibrationScreen
import com.example.overdex.ui.screens.CurrentBattleTeamScreen
import com.example.overdex.ui.screens.DroidballReadinessScreen
import com.example.overdex.ui.screens.EditSpecimenScreen
import com.example.overdex.ui.screens.MatchCalibrationScreen
import com.example.overdex.ui.screens.MainMenuPhase
import com.example.overdex.ui.screens.MainMenuScreen
import com.example.overdex.ui.screens.ModuleScreen
import com.example.overdex.ui.screens.ModuleStatus
import com.example.overdex.ui.screens.MyCollectionScreen
import com.example.overdex.ui.screens.PokedexListScreen
import com.example.overdex.ui.screens.PokemonDetailScreen
import com.example.overdex.ui.screens.QrIdentityScreen
import com.example.overdex.ui.screens.QrScannerScreen
import com.example.overdex.ui.screens.SignalObservatoryScreen
import com.example.overdex.ui.screens.SharedTimelineScreen
import com.example.overdex.ui.screens.SpecimenDetailScreen
import com.example.overdex.ui.screens.TrainerProfileScreen
import com.example.overdex.ui.screens.observatory.MatchSummaryRoute
import com.example.overdex.ui.theme.OverdexTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first


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
    private lateinit var trainerComms: TrainerCommsServices
    private lateinit var archiveDirectoryManager: ArchiveDirectoryManager
    private var selectedRegion = CalibrationRegion.NONE

    /** One battle owner survives MainActivity reclamation while Droidball remains deployed. */
    private val pokedexViewModel: PokedexViewModel by lazy {
        ViewModelProvider(
            application as OverdexApplication,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PokedexViewModel::class.java]
    }

    /**
     * The initial capability pass is deliberately optional. Declining a sensor leaves the
     * instrument usable and only disables the witnesses that require that sensor.
     */
    private val initialCapabilitiesLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        getSharedPreferences(CAPABILITY_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putInt(INITIAL_CAPABILITY_PASS_VERSION, CAPABILITY_PASS_VERSION)
            .apply()
        Log.i("OVERDEX_CAPABILITIES", "Initial capability pass: $grants")
        com.example.overdex.battle.observation.showIdleDroidballNotification(this)
    }

    // In-memory storage for the opened archive
    private var openedArchive = mutableStateOf<com.example.overdex.battle.archive.MatchArchive?>(null)
    private var openedArchiveUri = mutableStateOf<Uri?>(null)
    private var archiveLoadInProgress = mutableStateOf(false)
    private var notificationStartRequest = mutableStateOf(0)
    private var departureJob: Job? = null
    private var pendingGoHandoff = false

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.nianticlabs.pokemongo")
            if (launchIntent == null) {
                Toast.makeText(this, "Pokémon GO is not installed.", Toast.LENGTH_LONG).show()
                pokedexViewModel.stopObservation()
            } else {
                val generation = DroidballJourney.depart()
                // Recording starts now; only the visual app handoff waits for the eject.
                pokedexViewModel.deployInstrument(result.resultCode, result.data!!)
                departureJob?.cancel()
                departureJob = lifecycleScope.launch {
                    // A busy capture startup must not consume the shell animation.
                    // Stop/supersession wakes this waiter but cannot launch GO.
                    DroidballJourney.state.first { it.generation != generation || it.departurePresented }
                    if (DroidballJourney.departureFinished(generation)) launchPokemonGo(launchIntent)
                }
            }
        } else {
            pokedexViewModel.stopObservation()
        }
    }

    /** The field handoff following a successful Droidball deployment. */
    private fun launchPokemonGo(launchIntent: Intent) {
        // Keep Pokémon GO in its own task while leaving this shell as the
        // single launcher task to return to. MainActivity is singleTask, so
        // reopening Overdex from the launcher reuses the existing shell.
        try {
            pendingGoHandoff = true
            startActivity(launchIntent)
        } catch (error: RuntimeException) {
            pendingGoHandoff = false
            pokedexViewModel.stopObservation()
            Toast.makeText(this, "Could not open Pokémon GO.", Toast.LENGTH_LONG).show()
            Log.e("DROIDBALL_HANDOFF", "Pokémon GO launch failed", error)
        }
    }

    private val archiveOpenLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            archiveLoadInProgress.value = false
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            try {
                val archive = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("Unable to open the selected file.")
                    com.example.overdex.battle.archive.MatchArchivePackageReader.read(inputStream, filesDir)
                }
                openedArchiveUri.value = uri
                openedArchive.value = archive
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ARCHIVE_OPEN", "Failed to read match archive", e)
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Failed to open archive: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                archiveLoadInProgress.value = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onCreate()")
        CountdownSampleRecorder.initialize(this)
        CountdownGlyphMatcher.initialize(this)
        CountdownBurstRecorder.initialize(this)
        calibrationManager = CalibrationManager(this)
        trainerRepository = TrainerRepository(this)
        timelineRepository = SharedPreferencesTimelineRepository(this)
        partnerRepository = SharedPreferencesPartnerRepository(this)
        trainerComms = TrainerCommsServices.get(this)
        archiveDirectoryManager = ArchiveDirectoryManager(this)

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

            OverdexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding: PaddingValues ->
                    PokedexApp(
                        viewModel = pokedexViewModel,
                        mediaManager = mediaManager,
                        calibrationManager = calibrationManager,
                        trainerRepository = trainerRepository,
                        partnerRepository = partnerRepository,
                        timelineRepository = timelineRepository,
                        trainerComms = trainerComms,
                        trainerIdentity = trainerIdentity,
                        partnerIdentity = partnerIdentity,
                        timelineEvents = timelineEvents,
                        openedArchive = openedArchive,
                        openedArchiveUri = openedArchiveUri,
                        archiveLoadInProgress = archiveLoadInProgress,
                        archiveDirectoryManager = archiveDirectoryManager,
                        notificationStartRequest = notificationStartRequest.value,
                        onOpenArchive = {
                            if (!archiveLoadInProgress.value) {
                                archiveLoadInProgress.value = true
                                try {
                                    archiveOpenLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream", "*/*")
                                    )
                                } catch (e: Exception) {
                                    archiveLoadInProgress.value = false
                                    Log.e("ARCHIVE_OPEN", "Failed to launch file picker", e)
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open file picker.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onStartObservation = { 
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } else {
                                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                // Use Android's normal screen-share transition without
                                // placing a second, competing instruction over it.
                                runCatching {
                                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                }.onFailure { error ->
                                    Log.e("DROIDBALL_LAUNCH", "Unable to open screen-share prompt", error)
                                    pokedexViewModel.stopObservation()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open screen sharing. Please try START again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
        requestInitialOptionalCapabilities()
        consumeDroidballNotificationIntent(intent)
    }

    /**
     * Requests each declared runtime sensor once, at initial startup. Screen sharing and overlay
     * placement remain just-in-time grants because Android presents those controls only when the
     * user actually starts that operation.
     */
    private fun requestInitialOptionalCapabilities() {
        val preferences = getSharedPreferences(CAPABILITY_PREFERENCES, MODE_PRIVATE)
        if (preferences.getInt(INITIAL_CAPABILITY_PASS_VERSION, 0) >= CAPABILITY_PASS_VERSION) return

        // The TV build is a couch-side browser and archive viewer. Its launcher
        // session cannot observe a phone battle, so camera and microphone
        // prompts would be misleading there.
        if (isTelevisionDevice()) {
            preferences.edit().putInt(INITIAL_CAPABILITY_PASS_VERSION, CAPABILITY_PASS_VERSION).apply()
            Log.i("OVERDEX_CAPABILITIES", "Skipped handheld sensor pass on television hardware")
            return
        }

        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isEmpty()) {
            preferences.edit().putInt(INITIAL_CAPABILITY_PASS_VERSION, CAPABILITY_PASS_VERSION).apply()
        } else {
            initialCapabilitiesLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun isTelevisionDevice(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    override fun onStart() {
        super.onStart()
        pokedexViewModel.isOverdexVisible = true
        DroidballService.setInstrumentVisible(true)
        val generation = DroidballJourney.returnToShell()
        if (DroidballJourney.state.value.phase == DroidballJourneyPhase.RETURNING) {
            lifecycleScope.launch {
                delay(DroidballChoreography.EXIT_MILLIS)
                DroidballJourney.fieldExitFinished(generation)
            }
        }
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onStart()")
    }

    override fun onResume() {
        super.onResume()
        pokedexViewModel.isOverdexVisible = true
        com.example.overdex.battle.observation.showIdleDroidballNotification(this)
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onResume()")
    }

    override fun onPause() {
        super.onPause()
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onPause()")
    }

    override fun onStop() {
        super.onStop()
        val journey = DroidballJourney.state.value
        if (journey.shellOwnsBody) com.example.overdex.media.DroidballJourneySounds.hidden(journey)
        pokedexViewModel.isOverdexVisible = false
        DroidballService.setInstrumentVisible(false)
        if (pendingGoHandoff) {
            pendingGoHandoff = false
            // A successful explicit GO launch plus this activity leaving is our handoff.
            DroidballJourney.enterField()
        } else if (DroidballJourney.state.value.phase == DroidballJourneyPhase.DEPARTING) {
            // Leaving by Home/lock/back must never launch GO later in the background.
            departureJob?.cancel()
            pokedexViewModel.stopObservation()
        } else if (DroidballService.isRunning()) {
            DroidballJourney.hideInTransit()
        }
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] MainActivity.onDestroy()")
        mediaManager.release()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDroidballNotificationIntent(intent)
        if (intent.getBooleanExtra("open_guided_registration", false)) pokedexViewModel.openGuidedRegistration()
    }

    private fun consumeDroidballNotificationIntent(intent: Intent?) {
        if (intent?.action != com.example.overdex.battle.observation.DROIDBALL_START_ACTION) return
        intent.action = null
        if (!DroidballService.isRunning()) notificationStartRequest.value++
    }

    private companion object {
        init {
            Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] PROCESS STARTED (MainActivity init)")
        }

        const val CAPABILITY_PREFERENCES = "overdex_capabilities"
        const val INITIAL_CAPABILITY_PASS_VERSION = "initial_capability_pass_version"
        const val CAPABILITY_PASS_VERSION = 2
    }
}

@Composable
fun PokedexApp(
    viewModel: PokedexViewModel,
    modifier: Modifier = Modifier,
    mediaManager: MediaManager,
    calibrationManager: CalibrationManager,
    trainerRepository: TrainerRepository,
    partnerRepository: PartnerRepository,
    timelineRepository: SharedTimelineRepository,
    trainerComms: TrainerCommsServices,
    trainerIdentity: TrainerIdentity?,
    partnerIdentity: PartnerIdentity?,
    timelineEvents: List<SharedEvent>,
    openedArchive: MutableState<com.example.overdex.battle.archive.MatchArchive?>,
    openedArchiveUri: MutableState<Uri?>,
    archiveLoadInProgress: MutableState<Boolean>,
    archiveDirectoryManager: ArchiveDirectoryManager,
    onOpenArchive: () -> Unit = {},
    onStartObservation: () -> Unit = {},
    notificationStartRequest: Int = 0,

){
    val navController = rememberNavController()
    val launchAccessibilityProbe: () -> Unit = {
        if (BuildConfig.DEBUG) navController.navigate("accessibility_probe")
    }
    var openPokedexSearchOnList by remember { mutableStateOf(false) }
    val hasBootedInSession by viewModel.hasBootedInSession.collectAsState()
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    val treeState by viewModel.treeState.collectAsState()
    val deploymentState by viewModel.deploymentState.collectAsState()
    val frameCount by viewModel.frameCount.collectAsState()
    val interruptedObservationNotice by viewModel.interruptedObservationNotice.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val homePreferences = remember(context.applicationContext) {
        com.example.overdex.data.HomeScreenPreferences(context.applicationContext)
    }
    var homeStyle by remember { mutableStateOf(homePreferences.read()) }
    val appearancePreferences = remember(context.applicationContext) {
        com.example.overdex.data.ODXFiAppearancePreferences(context.applicationContext)
    }
    var odxFiAppearance by remember { mutableStateOf(appearancePreferences.read()) }
    val homeController = remember { OverdextopController(initialShortcuts = homePreferences.readShortcuts()) }
    val desktopPointer = remember { DesktopPointerController() }
    var explorerOpen by remember { mutableStateOf(false) }
    com.example.overdex.ui.navigation.MatchSummaryReturnEffect(
        pending = viewModel.pendingMatchSummary,
        onOpenSummary = {
            viewModel.markBooted()
            navController.navigate("timeline_viewer") {
                popUpTo("main_menu") { inclusive = false }
                launchSingleTop = true
            }
        },
        onHandled = { viewModel.acknowledgeMatchSummary(it) },
    )

    LaunchedEffect(notificationStartRequest) {
        com.example.overdex.battle.observation.requestDroidballNotificationStart(
            notificationStartRequest, DroidballService.isRunning(),
        ) {
            viewModel.markBooted()
            // Start was already explicit in the notification. Use the same
            // deployment path as the readiness screen's Launch action, without
            // making the user confirm that app-level request a second time.
            viewModel.startObservation()
        }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        if (activity?.intent?.getBooleanExtra("open_guided_registration", false) == true) {
            activity.intent.removeExtra("open_guided_registration")
            navController.navigate("guided_registration") { launchSingleTop = true }
        }
    }

    LaunchedEffect(openedArchive.value) {
        if (openedArchive.value != null) {
            navController.navigate("match_archive_viewer")
        }
    }

    LaunchedEffect(deploymentState) {
        if (deploymentState == InstrumentDeploymentState.REQUESTING_PERMISSIONS) {
            onStartObservation()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pendingCommand.collect { command ->
            when (command) {
                InstrumentCommand.LaunchDroidball -> navController.navigate("droidball_readiness")
                InstrumentCommand.OpenSearch -> navController.navigate("list")
                InstrumentCommand.OpenFieldNotes -> navController.navigate("field_notes")
                InstrumentCommand.OpenCollection -> navController.navigate("specimens/collection?binder=all")
                is InstrumentCommand.OpenOwnedBinder -> {
                    navController.navigate("specimens/collection?binder=${command.binder.routeKey}")
                }
                InstrumentCommand.ManageBinders -> navController.navigate("binders")
                InstrumentCommand.OpenCurrentTeam -> navController.navigate("current_battle_team")
                InstrumentCommand.AddSpecimen -> navController.navigate("guided_registration")
                InstrumentCommand.OpenBattleHistory -> navController.navigate("match_archive_directory")
                InstrumentCommand.OpenBattleRecords -> navController.navigate("battle_records")
                InstrumentCommand.OpenTournament -> navController.navigate("tournaments")
                InstrumentCommand.OpenBattleLogs -> navController.navigate("timeline_viewer")
                InstrumentCommand.OpenReplayEditor -> navController.navigate("replay_editor")

                InstrumentCommand.OpenCapture -> navController.navigate("guided_registration") { launchSingleTop = true }
                InstrumentCommand.OpenCalibration -> navController.navigate("calibration")

                InstrumentCommand.OpenProfile -> navController.navigate("trainer_profile")
                InstrumentCommand.OpenTimeline -> navController.navigate("shared_timeline")
                InstrumentCommand.OpenChat -> navController.navigate("private_chat")
                InstrumentCommand.OpenAccessibilityProbe -> launchAccessibilityProbe()
                InstrumentCommand.OpenSignalObservatory -> navController.navigate("signal_observatory")
                InstrumentCommand.OpenMatchCalibration -> navController.navigate("match_calibration")
                InstrumentCommand.OpenBattlePreview -> navController.navigate("battle_preview")
                else -> {}
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    com.example.overdex.ui.navigation.DirectorySearchResetEffect(currentRoute) {
        viewModel.resetSearchSession()
        openPokedexSearchOnList = false
    }
    val terminalSwipeWords by androidx.compose.runtime.produceState<List<String>>(emptyList(), viewModel) {
        value = viewModel.loadTerminalSwipeWords()
    }

    CompositionLocalProvider(
        com.example.overdex.ui.ODXFi.LocalODXFiAppearance provides odxFiAppearance,
        com.example.overdex.ui.components.LocalTerminalSwipeWords provides terminalSwipeWords,
        com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute provides currentRoute,
        com.example.overdex.ui.ODXFi.LocalDroidballSessionControl provides
            com.example.overdex.ui.ODXFi.DroidballSessionControl(deploymentState, viewModel::toggleObservation),
    ) {
        NavHost(
            navController = navController,
            startDestination = "main_menu",
            modifier = modifier,
        ) {
            composable("main_menu") {
                LaunchedEffect(Unit) {
                    Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] App Navigation: Boot/Startup Navigation Path executed (Navigated to main_menu)")
                }
                var phase by remember(hasBootedInSession) {
                    mutableStateOf(
                        if (hasBootedInSession) MainMenuPhase.READY else MainMenuPhase.BOOT
                    )
                }
                val instrumentState by viewModel.observationSessionState.collectAsState()
                val deploymentState by viewModel.deploymentState.collectAsState()
                val frameCount by viewModel.frameCount.collectAsState()
                val directoryCollection: MyCollectionViewModel = viewModel()
                val registration = com.example.overdex.ui.screens.rememberDirectoryOwnedPokemonEditor(
                    tree = treeState,
                    searchSpecies = { query, regions -> viewModel.createSearchFlow(query, dexRangeFlow = regions) },
                    loadSpecies = viewModel::getPokemonById,
                    savePokemon = directoryCollection::createOwnedPokemon,
                    onFinished = viewModel::finishDirectoryRegistration,
                )
                val currentRegistration by rememberUpdatedState(registration)
                val directoryLcd = remember { com.example.overdex.ui.components.DirectoryLcdController() }
                val usingHomeLauncher = !explorerOpen && (homeStyle == HomeScreenStyle.OVERDEXTOP ||
                    homeController.surface != OverdextopSurface.DESKTOP)
                val selectedCommand = if (usingHomeLauncher) {
                    (homeController.selectedEntry.action as? OverdextopAction.OpenProgram)?.command
                } else (registration.nodes.firstOrNull {
                    it.path == registration.selectedPath
                }?.node as? com.example.overdex.model.navigation.ActionNode)?.command
                val showCropPreviews = !usingHomeLauncher && directoryLcd.calibrationOpen && registration.lcdLines.isEmpty() &&
                    !registration.keyboard.isVisible
                val directoryLcdLines = registration.lcdLines.takeIf { !usingHomeLauncher && it.isNotEmpty() }
                    ?: interruptedObservationNotice?.takeUnless { showCropPreviews }?.let { notice ->
                        buildList {
                            add("OBSERVATION INTERRUPTED")
                            if (notice.recoveredMatchCount > 0) {
                                val suffix = if (notice.recoveredMatchCount == 1) "" else "ES"
                                add("${notice.recoveredMatchCount} MATCH$suffix RECOVERED")
                            }
                            if (notice.preservedCheckpointCount > 0) {
                                val suffix = if (notice.preservedCheckpointCount == 1) "" else "S"
                                add("${notice.preservedCheckpointCount} CHECKPOINT$suffix PRESERVED")
                            }
                            add("[START] NEW SESSION")
                        }
                    } ?: emptyList()
                val cropEditor = rememberDirectoryCropEditor(
                    enabled = showCropPreviews,
                    loadFrame = viewModel::loadDirectoryCalibrationFrame,
                    loadCalibration = viewModel::loadDirectoryCalibration,
                    saveCalibration = viewModel::saveDirectoryCalibration,
                )

                fun performHomeAction(action: OverdextopAction) {
                    when (action) {
                        is OverdextopAction.OpenProgram -> {
                            homeController.home()
                            if (action.command == InstrumentCommand.OpenDirectoryCalibration) {
                                explorerOpen = true
                                // Leave an unfinished Add Pokémon branch selected only
                                // when returning to it, not when explicitly opening crops.
                                val calibrationRow = treeState.visibleNodes.firstOrNull {
                                    (it.node as? com.example.overdex.model.navigation.ActionNode)?.command == action.command
                                }
                                if (calibrationRow != null) viewModel.handleTreeNode(calibrationRow.path)
                                else viewModel.launchInstrumentProgram(action.command)
                            } else viewModel.launchInstrumentProgram(action.command)
                        }
                        OverdextopAction.OpenExplorer -> {
                            desktopPointer.clearTargets()
                            homeController.home()
                            explorerOpen = true
                        }
                        OverdextopAction.OpenPrograms -> homeController.openPrograms()
                        OverdextopAction.OpenSettings -> homeController.openSettings()
                        OverdextopAction.OpenShortcuts -> homeController.openShortcuts()
                        OverdextopAction.OpenAppearance -> navController.navigate("odxfi_colors") {
                            launchSingleTop = true
                        }
                        is OverdextopAction.ChangeShortcutLocation -> {
                            val updated = homeController.shortcuts.withVisibility(
                                action.destinationId, action.location, action.visible,
                            )
                            if (homePreferences.writeShortcuts(updated)) {
                                homeController.replaceShortcuts(updated)
                            } else {
                                Toast.makeText(context, "Could not save the shortcuts. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                        is OverdextopAction.ChooseHomeStyle -> {
                            if (homePreferences.write(action.style)) {
                                desktopPointer.clearTargets()
                                homeStyle = action.style
                                explorerOpen = false
                                homeController.home()
                            } else {
                                Toast.makeText(context, "Could not save the home screen. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                fun confirmHomeEntry(entry: OverdextopEntry = homeController.selectedEntry) {
                    if (phase != MainMenuPhase.READY) return
                    if (entry.id == homeController.selectedEntry.id || homeController.select(entry.id)) {
                        val previousSurface = homeController.surface
                        val action = homeController.confirm()
                        // Preference writes and locked controls stay here. Keep
                        // measured targets available for another toggle or retry.
                        if (action !is OverdextopAction.ChooseHomeStyle &&
                            action !is OverdextopAction.ChangeShortcutLocation &&
                            (action != null || homeController.surface != previousSurface)) {
                            desktopPointer.clearTargets()
                        }
                        action?.let(::performHomeAction)
                    }
                }

                fun moveHomeSelection(direction: OverdextopDirection) {
                    homeController.move(direction)
                    if (desktopPointer.enabled) desktopPointer.centerOnTarget(homeController.selectedPointerTargetId)
                }

                fun toggleDesktopInput() {
                    if (phase != MainMenuPhase.READY || !usingHomeLauncher) return
                    if (desktopPointer.toggle()) desktopPointer.centerOnTarget(homeController.selectedPointerTargetId)
                }

                fun returnFromHomeSurface() {
                    if (phase != MainMenuPhase.READY) return
                    when {
                        usingHomeLauncher -> if (homeController.back()) desktopPointer.clearTargets()
                        showCropPreviews && directoryLcd.back(cropEditor) -> Unit
                        registration.back() -> Unit
                        homeStyle == HomeScreenStyle.OVERDEXTOP && explorerOpen -> {
                            explorerOpen = false
                            homeController.home()
                        }
                        else -> viewModel.handleB()
                    }
                }

                LaunchedEffect(viewModel) {
                    viewModel.pendingCommand.collect { command ->
                        if (command == InstrumentCommand.OpenHomeSettings) {
                            if (directoryLcd.close(cropEditor)) {
                                explorerOpen = false
                                homeController.openSettings()
                            }
                        } else if (command is InstrumentCommand.EditNewOwnedPokemon) {
                            currentRegistration.activate(command.field)
                        } else directoryLcd.activate(command, cropEditor)
                    }
                }
                androidx.activity.compose.BackHandler(enabled = phase == MainMenuPhase.READY &&
                    (showCropPreviews || (usingHomeLauncher && homeController.surface != OverdextopSurface.DESKTOP) ||
                        (homeStyle == HomeScreenStyle.OVERDEXTOP && explorerOpen))) {
                    returnFromHomeSurface()
                }
                ODXFiShell(

                    instrumentState = instrumentState,
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    showBattleOverlay = false,
                    keyboardController = registration.keyboard,
                    keyboardPreviewOnCrt = registration.keyboardPreviewOnCrt,
                    onKeyActivated = registration.key,
                    lcdLines = directoryLcdLines,
                    lcdContent = when {
                        usingHomeLauncher && directoryLcdLines.isEmpty() -> {
                            {
                                if (phase == MainMenuPhase.READY) {
                                    com.example.overdex.ui.components.OverdextopLcd(
                                        controller = homeController,
                                        onOpen = { confirmHomeEntry() },
                                        onPrograms = {
                                            if (homeController.surface != OverdextopSurface.PROGRAMS) {
                                                desktopPointer.clearTargets()
                                            }
                                            homeController.openPrograms()
                                        },
                                        onExplorer = { performHomeAction(OverdextopAction.OpenExplorer) },
                                        homeStyle = homeStyle,
                                        trackpadMode = desktopPointer.enabled,
                                        onToggleTrackpad = ::toggleDesktopInput,
                                        onPointerDrag = { dx, dy, width, height ->
                                            desktopPointer.moveBy(dx, dy, width, height)
                                        },
                                        onPointerTap = {
                                            desktopPointer.tap()?.let { targetId ->
                                                if (homeController.select(targetId)) confirmHomeEntry()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        showCropPreviews -> {
                            {
                                // Subscribe only when calibration is explicitly requested.
                                val crops by viewModel.directoryCropPreviews.collectAsStateWithLifecycle()
                                DirectoryCropPreviewLcd(state = crops, editor = cropEditor)
                            }
                        }
                        directoryLcdLines.isEmpty() && !registration.keyboard.isVisible -> {
                            {
                                com.example.overdex.ui.components.DirectorySelectionPreviewLcd(
                                    registration.nodes, registration.selectedPath
                                )
                            }
                        }
                        else -> null
                    },
                    lcdEdgeToEdgeContent = showCropPreviews,

                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onUp = {
                        if (phase == MainMenuPhase.READY && usingHomeLauncher) moveHomeSelection(OverdextopDirection.UP)
                        else if (phase == MainMenuPhase.READY &&
                            !(showCropPreviews && directoryLcd.direction(DirectoryCropEditDirection.UP, cropEditor)) && !registration.up()) viewModel.handleUp()
                    },
                    onDown = {
                        if (phase == MainMenuPhase.READY && usingHomeLauncher) moveHomeSelection(OverdextopDirection.DOWN)
                        else if (phase == MainMenuPhase.READY &&
                            !(showCropPreviews && directoryLcd.direction(DirectoryCropEditDirection.DOWN, cropEditor)) && !registration.down()) viewModel.handleDown()
                    },
                    onUpLong = { if (!usingHomeLauncher && !showCropPreviews) registration.hold(-1) },
                    onDownLong = { if (!usingHomeLauncher && !showCropPreviews) registration.hold(1) },
                    onUpPressChanged = { if (!it) registration.hold(0) },
                    onDownPressChanged = { if (!it) registration.hold(0) },
                    onLeft = { if (phase == MainMenuPhase.READY && usingHomeLauncher) moveHomeSelection(OverdextopDirection.LEFT)
                        else if (phase == MainMenuPhase.READY &&
                        !(showCropPreviews && directoryLcd.direction(DirectoryCropEditDirection.LEFT, cropEditor)) &&
                        !registration.left()) viewModel.handleLeft() },
                    onRight = { if (phase == MainMenuPhase.READY && usingHomeLauncher) moveHomeSelection(OverdextopDirection.RIGHT)
                        else if (phase == MainMenuPhase.READY &&
                        !(showCropPreviews && directoryLcd.direction(DirectoryCropEditDirection.RIGHT, cropEditor)) &&
                        !registration.right()) viewModel.handleRight() },
                    onA = { if (phase == MainMenuPhase.READY && usingHomeLauncher) confirmHomeEntry()
                        else if (phase == MainMenuPhase.READY &&
                        !(showCropPreviews && directoryLcd.confirm(cropEditor)) && !registration.confirm()) viewModel.handleA() },
                    onALong = {
                        if (phase == MainMenuPhase.READY && usingHomeLauncher &&
                            selectedCommand == InstrumentCommand.LaunchDroidball) {
                            performHomeAction(OverdextopAction.OpenProgram(InstrumentCommand.OpenDirectoryCalibration))
                        } else if (phase == MainMenuPhase.READY && !directoryLcd.calibrationOpen &&
                            registration.lcdLines.isEmpty() && !registration.keyboard.isVisible &&
                            selectedCommand == InstrumentCommand.LaunchDroidball) {
                            directoryLcd.holdA(selectedCommand, cropEditor)
                        }
                    },
                    onB = { returnFromHomeSurface() },
                    onStart = {
                        if (phase == MainMenuPhase.READY &&
                            !(showCropPreviews && (cropEditor.editing || cropEditor.saving)) &&
                            (usingHomeLauncher || !registration.start())) {
                            if (showCropPreviews) directoryLcd.close(cropEditor)
                            if (deploymentState == InstrumentDeploymentState.IDLE) {
                                navController.navigate("droidball_readiness")
                            } else {
                                viewModel.toggleObservation()
                            }
                        }
                    },
                    onSelect = {
                        if (phase == MainMenuPhase.READY && usingHomeLauncher) toggleDesktopInput()
                        else if (phase == MainMenuPhase.READY && !showCropPreviews) registration.select()
                    },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    isLogoInteractive = true
                ) { _ ->
                    MainMenuScreen(
                        hasBootedInSession = hasBootedInSession,
                        onBootComplete = { viewModel.markBooted() },
                        visibleNodes = registration.nodes,
                        selectedPath = if (showCropPreviews && cropEditor.focused) "" else registration.selectedPath,
                        trainerIdentity = trainerIdentity,
                        onPhaseChange = { phase = it },
                        revealEntryCount = if (usingHomeLauncher) homeController.entries.size else registration.nodes.size,
                        homeContent = if (usingHomeLauncher) {
                            { revealedCount, _ ->
                                com.example.overdex.ui.components.OverdextopCrt(
                                    controller = homeController,
                                    revealCount = revealedCount,
                                    homeStyle = homeStyle,
                                    pointerController = desktopPointer.takeIf { phase == MainMenuPhase.READY },
                                    onEntryActivated = { confirmHomeEntry(it) },
                                )
                            }
                        } else null,
                        onNodeSelected = { node ->
                            if (!cropEditor.editing && !cropEditor.saving) {
                                directoryLcd.close(cropEditor)
                                if (!registration.chooseNode(node)) viewModel.handleTreeNode(node.path)
                            }
                        }
                    )
                }
            }
            
            composable("odxfi_colors") {
                com.example.overdex.ui.screens.ODXFiAppearanceScreen(
                    initialAppearance = odxFiAppearance,
                    onSave = { appearance ->
                        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            appearancePreferences.write(appearance)
                        }
                        if (saved) odxFiAppearance = appearance.opaque()
                        saved
                    },
                    onBack = { navController.debugPopBackStack() },
                )
            }

            composable("battle_records?ownedId={ownedId}", arguments = listOf(navArgument("ownedId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                com.example.overdex.ui.screens.BattleRecordsScreen(viewModel,
                    ownedId = entry.arguments?.getString("ownedId"), onBack = { navController.debugPopBackStack() })
            }
            composable("battle_history") {
                // Preserve restored navigation from older builds without exposing the stub.
                LaunchedEffect(Unit) {
                    navController.navigate("match_archive_directory") {
                        popUpTo("battle_history") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable("battle_log") {
                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var lcdDragHandler by remember { mutableStateOf<((Offset) -> Unit)?>(null) }
                var lcdTapHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onStart = { viewModel.toggleObservation() },
                    onSelect = { /* Reserved */ },
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onB = { if (bHandler != null) bHandler?.invoke() else navController.debugPopBackStack() },
                    onLcdDrag = { lcdDragHandler?.invoke(it) },
                    onLcdTap = { lcdTapHandler?.invoke() },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                ) { battleMemory ->
                    if (battleMemory != null) {
                        BattleTimelineScreen(
                            battleMemory = battleMemory,
                            viewModel = viewModel,
                            onBack = { navController.debugPopBackStack() },
                            onUp = { upHandler = it },
                            onDown = { downHandler = it },
                            onA = { aHandler = it },
                            onB = { bHandler = it },
                            onLcdDrag = { lcdDragHandler = it },
                            onLcdTap = { lcdTapHandler = it }
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
                    onStart = { viewModel.toggleObservation() },
                    onSelect = { /* Reserved */ },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
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
            composable("tournaments") {
                com.example.overdex.ui.screens.TournamentScreen(
                    services = com.example.overdex.tournament.TournamentServices.get(context.applicationContext),
                    comms = trainerComms,
                    viewModel = viewModel,
                    onBack = { navController.debugPopBackStack() },
                    onOpenComms = { navController.navigate("private_chat") },
                    onOpenPokemonGo = {
                        val intent = context.packageManager.getLaunchIntentForPackage("com.nianticlabs.pokemongo")
                        if (intent == null) Toast.makeText(context, "Pokémon GO is not installed.", Toast.LENGTH_LONG).show()
                        else try { context.startActivity(intent) }
                        catch (_: RuntimeException) { Toast.makeText(context, "Could not open Pokémon GO.", Toast.LENGTH_LONG).show() }
                    },
                )
            }
            composable("private_chat") {
                TrainerCommsScreen(
                    services = trainerComms,
                    trainerRepository = trainerRepository,
                    viewModel = viewModel,
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
                val keyboardController = rememberTerminalKeyboardController()
                val pokedexSearchQuery by viewModel.searchQuery.collectAsState()

                LaunchedEffect(openPokedexSearchOnList) {
                    if (openPokedexSearchOnList) {
                        keyboardController.open()
                        openPokedexSearchOnList = false
                    }
                }

                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftPressChangedHandler by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
                var rightPressChangedHandler by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var selectHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var startHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var keyActivatedHandler by remember { mutableStateOf<((String) -> Unit)?>(null) }
                var pokedexLcdContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { newSettings -> filterSettings = newSettings },
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onLeftLong = { leftLongHandler?.invoke() },
                    onLeftPressChanged = { pressed -> leftPressChangedHandler?.invoke(pressed) },
                    onRight = { rightHandler?.invoke() },
                    onRightLong = { rightLongHandler?.invoke() },
                    onRightPressChanged = { pressed -> rightPressChangedHandler?.invoke(pressed) },
                    onA = { aHandler?.invoke() },
                    onB = { bHandler?.invoke() },
                    onSelect = { selectHandler?.invoke() },
                    onStart = { startHandler?.invoke() },
                    onKeyActivated = { keyActivatedHandler?.invoke(it) },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    keyboardController = keyboardController,
                    keyboardPreviewOnCrt = "SEARCH: ${pokedexSearchQuery.ifBlank { "_" }}",
                    lcdContent = pokedexLcdContent,
                    isBinderMode = true
                ) { _ ->
                    PokedexListScreen(
                        viewModel = viewModel,
                        onBack = { navController.debugPopBackStack() },
                        onPokemonClick = { id ->
                            viewModel.viewModelScope.launch {
                                viewModel.getPokemonById(id)?.let {
                                    mediaManager.warmUp(it.cryUrl)
                                }
                            }
                            navController.navigate(pokemonDetailRoute(id, binderMode = true))
                        },
                        keyboardController = keyboardController,
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onLeftLong = { leftLongHandler = it },
                        onLeftPressChanged = { leftPressChangedHandler = it },
                        onRight = { rightHandler = it },
                        onRightLong = { rightLongHandler = it },
                        onRightPressChanged = { rightPressChangedHandler = it },
                        onA = { aHandler = it },
                        onB = { bHandler = it },
                        onSelect = { selectHandler = it },
                        onStart = { startHandler = it },
                        onKeyActivated = { keyActivatedHandler = it },
                        onLcdContentUpdate = { pokedexLcdContent = it }
                    )
                }
            }
            composable(
                route = POKEMON_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("binder") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("origin") {
                        type = NavType.StringType
                        defaultValue = "list"
                    }
                ),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id") ?: 0
                val binderMode = backStackEntry.arguments?.getBoolean("binder") ?: false
                val originRoute = backStackEntry.arguments?.getString("origin") ?: "list"
                var pokemon by remember { mutableStateOf<Pokemon?>(null) }

                LaunchedEffect(id) {
                    pokemon = viewModel.getPokemonById(id)
                }

                fun returnFromPokemonDetail() {
                    if (binderMode) {
                        val returnedToBinder = navController.popBackStack("list", inclusive = false)
                        if (!returnedToBinder) {
                            navController.navigate("list") { launchSingleTop = true }
                        }
                    } else {
                        navController.debugPopBackStack()
                    }
                }

                if (pokemon != null) {
                    PokemonDetailScreen(
                        pokemon = pokemon!!,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { newSettings ->
                            filterSettings = newSettings
                        },
                        onStart = { /* Reserved */ },
                        onSelect = {
                            if (binderMode) {
                                openPokedexSearchOnList = true
                                returnFromPokemonDetail()
                            }
                        },
                        onBackClick = { returnFromPokemonDetail() },
                        onPlayCry = { url ->
                            mediaManager.playSound(url)
                        },
                        onWarmUpCry = { url ->
                            mediaManager.warmUp(url)
                        },
                        onMoveClick = { moveName ->
                            viewModel.updateSearchQuery(moveName)
                            navController.openPokemonSearchFromDetail(originRoute)
                        },
                        onTypeClick = { type ->
                            viewModel.updateTypeFilter(type)
                            viewModel.updateSearchQuery(type.name)
                            navController.openPokemonSearchFromDetail(originRoute)
                        },
                        onRegionClick = { region ->
                            viewModel.updateSearchQuery(region)
                            navController.openPokemonSearchFromDetail(originRoute)
                        },
                        onEvolutionClick = { evolutionId ->
                            navController.navigate(pokemonDetailRoute(evolutionId, binderMode, originRoute))
                        },
                        onLaunchProbe = launchAccessibilityProbe,
                        onLaunchObservatory = { navController.navigate("timeline_viewer") },
                        isBinderMode = binderMode,
                        viewModel = viewModel
                    )
                }
            }
            composable(
                route = "specimens/collection?binder={binder}",
                arguments = listOf(
                    navArgument("binder") {
                        type = NavType.StringType
                        defaultValue = OwnedPokemonBinder.ALL.routeKey
                    },
                ),
            ) { backStackEntry ->
                val collectionViewModel: MyCollectionViewModel = viewModel()
                val keyboardController = rememberTerminalKeyboardController()
                val customBinders by viewModel.customBinders.collectAsState()
                val binderKey = backStackEntry.arguments?.getString("binder")
                val binder = customBinders.firstOrNull { it.routeKey == binderKey }
                    ?: OwnedPokemonBinder.fromRouteKey(binderKey)
                val ownedSearchQuery by collectionViewModel.searchQuery.collectAsState()

                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftPressChangedHandler by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
                var rightPressChangedHandler by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var selectHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var startHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var keyActivatedHandler by remember { mutableStateOf<((String) -> Unit)?>(null) }
                var ownedBinderLcdContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onLeftLong = { leftLongHandler?.invoke() },
                    onLeftPressChanged = { pressed -> leftPressChangedHandler?.invoke(pressed) },
                    onRight = { rightHandler?.invoke() },
                    onRightLong = { rightLongHandler?.invoke() },
                    onRightPressChanged = { pressed -> rightPressChangedHandler?.invoke(pressed) },
                    onA = { aHandler?.invoke() },
                    onB = { bHandler?.invoke() },
                    onSelect = { selectHandler?.invoke() },
                    onStart = { startHandler?.invoke() },
                    startLabel = "+",
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    keyboardController = keyboardController,
                    keyboardPreviewOnCrt = "SEARCH OWNED: ${ownedSearchQuery.ifBlank { "_" }}",
                    onKeyActivated = { key -> keyActivatedHandler?.invoke(key) },
                    lcdContent = ownedBinderLcdContent,
                    isBinderMode = true,
                ) {
                    MyCollectionScreen(
                        pokedexViewModel = viewModel,
                        collectionViewModel = collectionViewModel,
                        binder = binder,
                        onAddClick = { navController.navigate("guided_registration") },
                        onBack = { navController.debugPopBackStack() },
                        onItemClick = { id ->
                            navController.navigate("specimens/detail/$id")
                        },
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onLeftLong = { leftLongHandler = it },
                        onLeftPressChanged = { leftPressChangedHandler = it },
                        onRight = { rightHandler = it },
                        onRightLong = { rightLongHandler = it },
                        onRightPressChanged = { rightPressChangedHandler = it },
                        onA = { aHandler = it },
                        onB = { bHandler = it },
                        onSelect = { selectHandler = it },
                        onStart = { startHandler = it },
                        onKeyActivated = { keyActivatedHandler = it },
                        onLcdContentUpdate = { ownedBinderLcdContent = it },
                        keyboardController = keyboardController,
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
                    onRecords = { ownedId -> navController.navigate("battle_records?ownedId=${android.net.Uri.encode(ownedId)}") },
                    onDeleteSuccess = { navController.debugPopBackStack() },
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
            composable("field_notes") {
                com.example.overdex.ui.screens.FieldNotesScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    // Binder mode returns directly to the Pokédex list; journal links
                    // use the ordinary back stack so B returns to these field notes.
                    onOpenSpecies = { id -> navController.navigate(pokemonDetailRoute(id, originRoute = "field_notes")) },
                    onOpenOwned = { id -> navController.navigate("specimens/detail/${android.net.Uri.encode(id)}") },
                    onBack = { navController.debugPopBackStack() },
                )
            }
            composable("binders") {
                com.example.overdex.ui.screens.BinderLibraryScreen(
                    viewModel, filterSettings, { filterSettings = it },
                    onOpen = { navController.navigate("specimens/collection?binder=${it.routeKey}") },
                    onBack = { navController.debugPopBackStack() },
                )
            }
            composable("guided_registration") {
                com.example.overdex.ui.screens.GuidedRegistrationScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onManual = { navController.navigate("add_pokemon_wizard") },
                    onCalibration = { navController.navigate("registration_calibration") },
                    onCard = { id ->
                        viewModel.stopGuidedRegistration()
                        navController.navigate("specimens/detail/$id")
                    },
                    onBack = { viewModel.stopGuidedRegistration(); navController.debugPopBackStack() },
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
            composable("registration_calibration") {
                com.example.overdex.ui.screens.RegistrationCalibrationScreen(
                    viewModel, filterSettings, { filterSettings = it },
                    onReview = { navController.popBackStack("guided_registration", inclusive = false) },
                    onBack = { navController.debugPopBackStack() },
                )
            }
            composable("current_battle_team") {
                CurrentBattleTeamScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onBack = { navController.debugPopBackStack() },
                    onTeamLibrary = { navController.navigate("team_library") },
                )
            }
            composable("team_library") {
                com.example.overdex.ui.screens.BattleTeamLibraryScreen(
                    viewModel, filterSettings, { filterSettings = it },
                    onChosen = { navController.debugPopBackStack() },
                    onEditTeam = { navController.navigate("droidball_team_setup") },
                    onEditCard = { navController.navigate("specimens/edit/$it") },
                    onBack = { navController.debugPopBackStack() },
                )
            }
            composable("droidball_readiness") {
                DroidballReadinessScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunch = {
                        navController.popBackStack()
                        viewModel.startObservation()
                    },
                    onConfigureTeam = { navController.navigate("droidball_team_setup") },
                    onChooseTeam = { navController.navigate("team_library") },
                    onBack = { navController.debugPopBackStack() },
                )
            }
            composable("droidball_team_setup") {
                CurrentBattleTeamScreen(
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onBack = { navController.debugPopBackStack() },
                    onReadyToLaunch = {
                        navController.popBackStack("main_menu", inclusive = false)
                        viewModel.startObservation()
                    },
                    onTeamLibrary = { navController.navigate("team_library") },
                )
            }
            if (BuildConfig.DEBUG) {
                composable("accessibility_probe") {
                    var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                    var probeTitle by remember { mutableStateOf("PROBE") }
                    var probeBreadcrumb by remember { mutableStateOf("/signal_observatory/accessibility_probe/") }
                    var probeLcdLines by remember { mutableStateOf(emptyList<String>()) }

                    ODXFiShell(
                        showBattleOverlay = false,
                        viewModel = viewModel,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { filterSettings = it },
                        title = probeTitle,
                        breadcrumb = probeBreadcrumb,
                        lcdLines = probeLcdLines,
                        onUp = { upHandler?.invoke() },
                        onDown = { downHandler?.invoke() },
                        onA = { aHandler?.invoke() },
                        onB = { bHandler?.invoke() ?: navController.debugPopBackStack() },
                        onLaunchProbe = launchAccessibilityProbe,
                        onLaunchObservatory = { navController.navigate("timeline_viewer") },
                        onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                        deploymentState = deploymentState,
                        frameCount = frameCount
                    ) {
                        AccessibilityProbeScreen(
                            onBack = { navController.debugPopBackStack() },
                            onUp = { upHandler = it },
                            onDown = { downHandler = it },
                            onA = { aHandler = it },
                            onB = { bHandler = it },
                            deploymentState = deploymentState,
                            onUpdateInfo = { title, breadcrumb, lines ->
                                probeTitle = title
                                probeBreadcrumb = breadcrumb
                                probeLcdLines = lines
                            }
                        )
                    }
                }
            }
            composable("signal_observatory") {
                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                    onB = { bHandler?.invoke() ?: navController.debugPopBackStack() },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount
                ) {
                    SignalObservatoryScreen(
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { filterSettings = it },
                        onLaunchProbe = launchAccessibilityProbe,
                        onLaunchObservatory = { navController.navigate("timeline_viewer") },
                        onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                        onBack = { navController.debugPopBackStack() },
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onLeft = { leftHandler = it },
                        onRight = { rightHandler = it },
                        onA = { aHandler = it },
                        onB = { bHandler = it }
                    )
                }
            }
            // Keep the existing destination ID for Android-restored latest-summary stacks.
            listOf("timeline_viewer", "match_summary?archiveUri={archiveUri}").forEach { summaryRoute ->
                composable(summaryRoute, arguments = listOf(
                    navArgument("archiveUri") { type = NavType.StringType; nullable = true; defaultValue = null }
                )) { entry ->
                    val selectedArchiveUri = entry.arguments?.getString("archiveUri")?.let(Uri::parse)
                    var matchLcdContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                    val archiveSource by viewModel.latestMatchArchiveSource.collectAsState()
                    var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }

                    ODXFiShell(
                        lcdContent = matchLcdContent,
                        showBattleOverlay = false,
                        viewModel = viewModel,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { filterSettings = it },
                        onLaunchProbe = launchAccessibilityProbe,
                        onLaunchObservatory = { navController.navigate("timeline_viewer") },
                        onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                        deploymentState = deploymentState,
                        frameCount = frameCount,
                        onUp = { upHandler?.invoke() },
                        onDown = { downHandler?.invoke() },
                        onLeft = { leftHandler?.invoke() },
                        onRight = { rightHandler?.invoke() },
                        onA = { aHandler?.invoke() },
                        onB = { bHandler?.invoke() ?: navController.debugPopBackStack() }
                    ) {
                        MatchSummaryRoute(
                            source = archiveSource,
                            directory = archiveDirectoryManager,
                            onLcdContentUpdate = { matchLcdContent = it },
                            onOpenExportedMatch = onOpenArchive,
                            onBack = { navController.debugPopBackStack() },
                            onOpenMatches = {
                                if (!navController.popBackStack("match_archive_directory", false)) {
                                    navController.navigate("match_archive_directory") { launchSingleTop = true }
                                }
                            },
                            selectedArchiveUri = selectedArchiveUri,
                            onInspect = { archive, uri ->
                                openedArchiveUri.value = uri
                                openedArchive.value = archive
                            },
                            onUp = { upHandler = it },
                            onDown = { downHandler = it },
                            onLeft = { leftHandler = it },
                            onRight = { rightHandler = it },
                            onA = { aHandler = it },
                            onB = { bHandler = it }
                        )
                    }
                }
            }
            composable("match_archive_directory") {
                var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var lcdLine1 by remember { mutableStateOf<String?>(null) }
                var lcdLine2 by remember { mutableStateOf<String?>(null) }
                var historyLcdContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

                ODXFiShell(
                    showBattleOverlay = false,
                    viewModel = viewModel,
                    filterSettings = filterSettings,
                    onFilterSettingsChange = { filterSettings = it },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
                    onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                    deploymentState = deploymentState,
                    frameCount = frameCount,
                    lcdLine1 = lcdLine1,
                    lcdLine2 = lcdLine2,
                    lcdContent = historyLcdContent,
                    onUp = { upHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onB = { bHandler?.invoke() ?: navController.debugPopBackStack() }
                ) { _ ->
                    MatchArchiveDirectoryScreen(
                        archiveDirectoryManager = archiveDirectoryManager,
                        onBack = { navController.popBackStack() },
                        onOpenArchive = { uri ->
                            navController.navigate("match_summary?archiveUri=${Uri.encode(uri.toString())}")
                        },
                        isLoading = archiveLoadInProgress.value,
                        onUp = { upHandler = it },
                        onDown = { downHandler = it },
                        onA = { aHandler = it },
                        onB = { bHandler = it },
                        onLcdUpdate = { line1, line2 ->
                            lcdLine1 = line1
                            lcdLine2 = line2
                        },
                        onLcdContentUpdate = { historyLcdContent = it }
                    )
                }
            }
            composable("match_archive_viewer") {
                var matchLcdContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                val archive = openedArchive.value
                if (archive != null) {
                    var upHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var bHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var lcdDragHandler by remember { mutableStateOf<((Offset) -> Unit)?>(null) }
                    var lcdTapHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                    var lcdLine1 by remember { mutableStateOf<String?>(null) }
                    var lcdLine2 by remember { mutableStateOf<String?>(null) }

                    ODXFiShell(
                        lcdContent = matchLcdContent,
                        showBattleOverlay = false,
                        viewModel = viewModel,
                        filterSettings = filterSettings,
                        onFilterSettingsChange = { filterSettings = it },
                        onUp = { upHandler?.invoke() },
                        onDown = { downHandler?.invoke() },
                        onLeft = { leftHandler?.invoke() },
                        onRight = { rightHandler?.invoke() },
                        onA = { aHandler?.invoke() },
                        onB = { bHandler?.invoke() },
                        onLcdDrag = { lcdDragHandler?.invoke(it) },
                        onLcdTap = { lcdTapHandler?.invoke() },
                        onLaunchProbe = launchAccessibilityProbe,
                        onLaunchObservatory = { navController.navigate("timeline_viewer") },
                        onLaunchMatchCalibration = { navController.navigate("match_calibration") },
                        deploymentState = deploymentState,
                        frameCount = frameCount,
                        lcdLine1 = lcdLine1,
                        lcdLine2 = lcdLine2,
                    ) {
                        com.example.overdex.ui.screens.observatory.MatchArchiveViewerScreen(
                            archive = archive,
                            onOpenEditor = { navController.navigate("replay_editor") },
                            onLcdContentUpdate = { matchLcdContent = it },
                            onBack = {
                                openedArchive.value = null
                                openedArchiveUri.value = null
                                navController.debugPopBackStack()
                            },
                            onUp = { upHandler = it },
                            onDown = { downHandler = it },
                            onLeft = { leftHandler = it },
                            onRight = { rightHandler = it },
                            onA = { aHandler = it },
                            onB = { bHandler = it },
                            onLcdDrag = { lcdDragHandler = it },
                            onLcdTap = { lcdTapHandler = it },
                            onLcdUpdate = { line1, line2 ->
                                lcdLine1 = line1
                                lcdLine2 = line2
                            },
                            resolveSpeciesId = { name -> viewModel.getPokemonByName(name)?.id },
                            resolveSpeciesTypes = { name -> viewModel.getPokemonByName(name)?.types.orEmpty() },
                            resolveFastMoveType = { speciesName, moveName ->
                                viewModel.getPokemonByName(speciesName)
                                    ?.fastMoves
                                    ?.firstOrNull { candidate ->
                                        candidate.name.uppercase().filter(Char::isLetterOrDigit) ==
                                            moveName.uppercase().filter(Char::isLetterOrDigit)
                                    }
                                    ?.type
                            },
                            resolveFastMoves = { speciesName ->
                                viewModel.getPokemonByName(speciesName)?.fastMoves.orEmpty()
                            },
                            resolveChargedMoveType = { speciesName, moveName ->
                                viewModel.getPokemonByName(speciesName)?.chargedMoves?.firstOrNull {
                                    it.name.uppercase().filter(Char::isLetterOrDigit) ==
                                        moveName.uppercase().filter(Char::isLetterOrDigit)
                                }?.type
                            },
                            resolveChargedMoves = { speciesName ->
                                viewModel.getPokemonByName(speciesName)?.chargedMoves.orEmpty()
                            },
                            resolveArchivedSpeciesCrops = { selectedArchive, progress ->
                                val uri = openedArchiveUri.value
                                if (uri == null) emptyList() else {
                                    ArchivedSpeciesCropRecognizer.recognize(
                                        archive = selectedArchive,
                                        openArchive = { context.contentResolver.openInputStream(uri) },
                                        knownSpeciesNames = viewModel.getAllSpeciesNames(),
                                        resolveSpeciesId = { name -> viewModel.getPokemonByName(name)?.id },
                                        onProgress = progress
                                    )
                                }
                            }
                        )
                    }
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "Archive data lost.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.debugPopBackStack()
                    }
                }
            }
            composable("replay_editor") {
                var editorLcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                var up by remember { mutableStateOf<() -> Unit>({}) }
                var down by remember { mutableStateOf<() -> Unit>({}) }
                var left by remember { mutableStateOf<() -> Unit>({}) }
                var right by remember { mutableStateOf<() -> Unit>({}) }
                var a by remember { mutableStateOf<() -> Unit>({}) }
                var b by remember { mutableStateOf<() -> Unit>({}) }
                ODXFiShell(
                    viewModel = viewModel, showBattleOverlay = false,
                    filterSettings = filterSettings, onFilterSettingsChange = { filterSettings = it },
                    deploymentState = deploymentState, frameCount = frameCount,
                    lcdContent = editorLcd,
                    onUp = { up() }, onDown = { down() }, onLeft = { left() }, onRight = { right() },
                    onA = { a() }, onB = { b() },
                ) {
                    com.example.overdex.ui.screens.observatory.ReplayEditorScreen(
                        viewModel = viewModel,
                        onBack = { navController.debugPopBackStack() },
                        onOpenArchives = { navController.navigate("match_archive_directory") },
                        onUp = { up = it }, onDown = { down = it },
                        onLeft = { left = it }, onRight = { right = it },
                        onA = { a = it }, onB = { b = it },
                        onLcdContentUpdate = { editorLcd = it },
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
                var upLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var downLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var leftLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var rightLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
                var aLongHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
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
                    // Preserve source geometry for precision calibration; GlassShield still applies.
                    filterSettings = filterSettings.copy(isEnabled = false),
                    onFilterSettingsChange = { filterSettings = it },
                    onUp = { upHandler?.invoke() },
                    onUpLong = { upLongHandler?.invoke() },
                    onDown = { downHandler?.invoke() },
                    onDownLong = { downLongHandler?.invoke() },
                    onLeft = { leftHandler?.invoke() },
                    onLeftLong = { leftLongHandler?.invoke() },
                    onRight = { rightHandler?.invoke() },
                    onRightLong = { rightLongHandler?.invoke() },
                    onA = { aHandler?.invoke() },
                    onALong = { aLongHandler?.invoke() },
                    onSelect = { selectHandler?.invoke() },
                    onSelectLong = { selectLongHandler?.invoke() },
                    onStart = { startHandler?.invoke() },
                    startLabel = "DEVICES",
                    onLcdDrag = { lcdDragHandler?.invoke(it) },
                    onLcdTap = { lcdTapHandler?.invoke() },
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
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
                        onUpLong = { upLongHandler = it },
                        onDown = { downHandler = it },
                        onDownLong = { downLongHandler = it },
                        onLeft = { leftHandler = it },
                        onLeftLong = { leftLongHandler = it },
                        onRight = { rightHandler = it },
                        onRightLong = { rightLongHandler = it },
                        onA = { aHandler = it },
                        onALong = { aLongHandler = it },
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
                    onLaunchProbe = launchAccessibilityProbe,
                    onLaunchObservatory = { navController.navigate("timeline_viewer") },
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

internal const val POKEMON_DETAIL_ROUTE = "detail/{id}?binder={binder}&origin={origin}"

internal fun pokemonDetailRoute(id: Int, binderMode: Boolean = false, originRoute: String = "list"): String =
    "detail/$id?binder=$binderMode&origin=${if (originRoute == "field_notes") "field_notes" else "list"}"

/** Show a requested filter, removing evolution pages while retaining the journal beneath results. */
internal fun androidx.navigation.NavController.openPokemonSearchFromDetail(originRoute: String) {
    navigate("list") {
        popUpTo(if (originRoute == "field_notes") "field_notes" else "list") { inclusive = false }
        launchSingleTop = true
    }
}

private fun androidx.navigation.NavController.debugPopBackStack(): Boolean {
    val before = currentDestination?.route
    val popped = popBackStack()
    val after = currentDestination?.route
    com.example.overdex.diagnostics.DiagnosticLogger.logNav("popBackStack", before, popped, after)
    return popped
}
