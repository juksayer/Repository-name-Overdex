package com.example.overdex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.example.overdex.CalibrationManager
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.CalibrationMode
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.overdex.data.ScreenshotDirectoryManager
import com.example.overdex.data.MatchCalibrationProfileStore
import com.example.overdex.data.MatchCalibrationProfile
import com.example.overdex.data.MatchCalibrationProfileSummary
import com.example.overdex.data.CalibrationProfileCompatibility
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCalibrationStore
import com.example.overdex.ui.theme.TerminalBlack
import android.os.Build
import java.io.File
import com.example.overdex.data.BattleCalibrationReferenceStore
import com.example.overdex.data.CalibrationScreenshotFilter
import com.example.overdex.data.CalibrationScreenshotLibrary
import com.example.overdex.data.CalibrationScreenshotReference
import com.example.overdex.data.CalibrationImageViewport
import com.example.overdex.data.BundledCalibrationSampleCatalog
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.DroidballRuntimeState
import java.text.DateFormat
import java.util.Date

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun MatchCalibrationPreview() {
    MatchCalibrationScreen(
        calibrationManager = CalibrationManager(LocalContext.current)
    )
}

@Composable
fun MatchCalibrationScreen(
    calibrationManager: CalibrationManager,
    onUp: (() -> Unit) -> Unit = {},
    onUpLong: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onDownLong: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onLeftLong: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onRightLong: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onALong: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onSelectLong: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val deviceScreenSize = remember(context) {
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { IntSize(it.width(), it.height()) }
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also(windowManager.defaultDisplay::getRealMetrics)
            IntSize(metrics.widthPixels, metrics.heightPixels)
        }
    }
    val detectedDeviceModel = remember {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { "Android device" }
    }
    val detectedScreenWidth = deviceScreenSize.width.takeIf { it > 0 } ?: 1080
    val detectedScreenHeight = deviceScreenSize.height.takeIf { it > 0 } ?: 2400
    val teamSelectCalibrationStore = remember(context) { TeamSelectCalibrationStore(context) }
    val profileStore = remember(context) { MatchCalibrationProfileStore(context) }
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var teamSelectCalibration by remember { mutableStateOf(teamSelectCalibrationStore.load()) }
    var activeProfile by remember { mutableStateOf<MatchCalibrationProfileSummary?>(profileStore.activeSummary()) }
    var reviewedProfile by remember { mutableStateOf<MatchCalibrationProfileSummary?>(null) }
    var savedProfiles by remember { mutableStateOf(profileStore.list()) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showOtherDeviceProfiles by remember { mutableStateOf(false) }
    var profileActionError by remember { mutableStateOf<String?>(null) }
    val droidballRuntime by DroidballService.runtimeState.collectAsState()
    val activeProfileMatchesDetectedDevice = activeProfile?.let {
        CalibrationProfileCompatibility.matchesDevice(it, detectedDeviceModel,
            detectedScreenWidth, detectedScreenHeight)
    } == true
    val matchingDeviceProfiles = CalibrationProfileCompatibility.visibleProfiles(savedProfiles,
        detectedDeviceModel, detectedScreenWidth, detectedScreenHeight)
    val visibleDeviceProfiles = CalibrationProfileCompatibility.visibleProfiles(savedProfiles,
        detectedDeviceModel, detectedScreenWidth, detectedScreenHeight, showOtherDeviceProfiles)
    var deviceGuessOffered by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profileModel by remember { mutableStateOf("") }
    var profileWidth by remember { mutableStateOf("") }
    var profileHeight by remember { mutableStateOf("") }
    var profileSaveError by remember { mutableStateOf<String?>(null) }
    var selectedRegion by remember { mutableStateOf(CalibrationRegion.COUNTDOWN) }
    var mode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var showLcdTouchHint by rememberSaveable { mutableStateOf(true) }
    var battleProfileSaved by remember { mutableStateOf(calibrationManager.hasSavedProfile()) }
    var teamSelectProfileSaved by remember { mutableStateOf(teamSelectCalibrationStore.hasSavedProfile()) }
    var battleSaveFailed by remember { mutableStateOf(false) }
    var teamSelectSaveFailed by remember { mutableStateOf(false) }
    val screenshotDirectory = remember(context) { ScreenshotDirectoryManager(context) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    val library by produceState<List<CalibrationSample>?>(initialValue = null, context, libraryRevision) {
        value = null
        value = withContext(Dispatchers.IO) { loadCalibrationSamples(context, screenshotDirectory) }
    }
    var screenshotFilter by remember { mutableStateOf<CalibrationScreenshotFilter>(CalibrationScreenshotFilter.ThisScreen) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    val references = library.orEmpty().map { it.reference }
    val visibleIds = CalibrationScreenshotLibrary.filter(
        references, screenshotFilter, detectedScreenWidth, detectedScreenHeight
    ).map { it.id }.toSet()
    val samples = library.orEmpty().filter { it.reference.id in visibleIds }
    var currentImageIndex by remember { mutableIntStateOf(0) }
    val selectedSample = samples.getOrNull(currentImageIndex)
    val screenshotSourceName = selectedSample?.sourceName ?: "NO REFERENCE"
    val screenshotReferenceName = selectedSample?.reference?.name ?: "No reference selected"
    val sourceFrameSize = selectedSample?.reference?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    val imageViewport = CalibrationImageViewport.fit(
        containerSize.width, containerSize.height, sourceFrameSize.width, sourceFrameSize.height
    )
    val screenshotFilterLabel = when (val filter = screenshotFilter) {
        CalibrationScreenshotFilter.ThisScreen -> "This screen"
        CalibrationScreenshotFilter.All -> "All screenshots"
        is CalibrationScreenshotFilter.Shape -> "Shape ${CalibrationScreenshotLibrary.shapeLabel(filter.width, filter.height)}"
        is CalibrationScreenshotFilter.Device -> filter.label
    }
    val referenceMatchesDevice = CalibrationScreenshotLibrary.matchesScreen(
        selectedSample?.reference, detectedScreenWidth, detectedScreenHeight)
    var renderedReference by remember { mutableStateOf<Pair<String, IntSize>?>(null) }
    var failedReferenceId by remember { mutableStateOf<String?>(null) }
    val renderedSize = renderedReference?.takeIf { it.first == selectedSample?.reference?.id }?.second
    val referenceReadOnlyReason = when {
        selectedSample == null -> "Read only: choose a screenshot matching this phone."
        !selectedSample.reference.hasDimensions -> "Read only: screenshot dimensions are unknown. Choose a screenshot for this phone."
        !referenceMatchesDevice -> "Read only: this screenshot has a different screen shape. Choose a screenshot for this phone."
        failedReferenceId == selectedSample.reference.id -> "Read only: this screenshot could not be displayed. Choose another reference."
        renderedSize == null -> "Read only: loading the reference image."
        !CalibrationScreenshotLibrary.matchesDecodedScreen(selectedSample.reference, renderedSize.width, renderedSize.height,
            detectedScreenWidth, detectedScreenHeight) -> "Read only: the displayed image does not match its reference dimensions. Choose another reference."
        else -> null
    }
    val calibrationReadOnlyReason = referenceReadOnlyReason ?: if (activeProfile != null && !activeProfileMatchesDetectedDevice)
        "Read only: this calibration belongs to another device. Save a copy for this phone first." else null
    val screenshotFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                screenshotDirectory.saveFolder(uri)
                libraryRevision++
                currentImageIndex = 0
            }
        }
    }

    LaunchedEffect(samples.map { it.reference.id }) {
        currentImageIndex = currentImageIndex.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
    }

    val matchRegions = remember {
        listOf(
            CalibrationRegion.COUNTDOWN,
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE,
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT,
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS,
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY,
            CalibrationRegion.TEAM_SELECT_ROSTER_1,
            CalibrationRegion.TEAM_SELECT_ROSTER_2,
            CalibrationRegion.TEAM_SELECT_ROSTER_3,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3,
            CalibrationRegion.VS_SCREEN,
            CalibrationRegion.ANNOUNCEMENT,
            CalibrationRegion.TRAINER_TEAM_INFO,
            CalibrationRegion.OPPONENT_TEAM_INFO,
            CalibrationRegion.PLAYER_SPECIES_NAME,
            CalibrationRegion.OPPONENT_SPECIES_NAME,
            CalibrationRegion.PLAYER_SHIELDS,
            CalibrationRegion.OPPONENT_SHIELDS,
            CalibrationRegion.PLAYER_POKE_BALLS,
            CalibrationRegion.OPPONENT_POKE_BALLS,
            CalibrationRegion.TRAINER_ACTIVE_TYPE,
            CalibrationRegion.OPPONENT_ACTIVE_TYPE,
            CalibrationRegion.TRAINER_HP,
            CalibrationRegion.OPPONENT_HP,
            CalibrationRegion.CHARGE_MOVE_EXECUTION,
            CalibrationRegion.QTE_PROGRESS_TEXT,
            CalibrationRegion.QTE_TYPE_ICONS,
            CalibrationRegion.EXIT_ICON,
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS,
            CalibrationRegion.POINTER_COORDINATES,
            CalibrationRegion.DECISION_TIMER,
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_LEFT,
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_LEFT,
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_RIGHT,
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_RIGHT,

            CalibrationRegion.TRAINER_INACTIVE_POKEMON,
            CalibrationRegion.MATCH_OUTCOME
        )
    }

    fun getReadableName(region: CalibrationRegion): String {
        return when (region) {
            CalibrationRegion.POINTER_COORDINATES -> "Touch Coordinates"
            CalibrationRegion.DECISION_TIMER -> "Decision Timer"
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_LEFT -> "Player Charged Move Name - Left"
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_LEFT -> "Charged Button Effectiveness - Left"
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_RIGHT -> "Player Charged Move Name - Right"
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_RIGHT -> "Charged Button Effectiveness - Right"
            CalibrationRegion.ENEMY_NAME -> "Enemy Name"
            CalibrationRegion.HP_BAR -> "HP Bar"
            CalibrationRegion.TEAM_ICONS -> "Team Icons"
            CalibrationRegion.MOVE_BANNER -> "Move Banner"
            CalibrationRegion.PLAYER_SHIELDS -> "Player Shields"
            CalibrationRegion.OPPONENT_SHIELDS -> "Opponent Shields"
            CalibrationRegion.YOU_WIN -> "You Win"
            CalibrationRegion.GOOD_EFFORT -> "Good Effort"
            CalibrationRegion.COUNTDOWN -> "Countdown"
            CalibrationRegion.VS_SCREEN -> "VS Screen"
            CalibrationRegion.ANNOUNCEMENT -> "Announcement"
            CalibrationRegion.TRAINER_TEAM_INFO -> "Trainer Team Info"
            CalibrationRegion.OPPONENT_TEAM_INFO -> "Opponent Team Info"
            CalibrationRegion.PLAYER_SPECIES_NAME -> "Player Species Name"
            CalibrationRegion.TRAINER_ACTIVE_TYPE -> "Trainer Active Type"
            CalibrationRegion.OPPONENT_ACTIVE_TYPE -> "Opponent Active Type"
            CalibrationRegion.TRAINER_HP -> "Trainer HP"
            CalibrationRegion.OPPONENT_HP -> "Opponent HP"
            CalibrationRegion.CHARGE_MOVE_EXECUTION -> "Charge-Move Execution"
            CalibrationRegion.QTE_PROGRESS_TEXT -> "QTE Progress Text (Nice / Great / Excellent)"
            CalibrationRegion.QTE_TYPE_ICONS -> "QTE Type Icons"
            CalibrationRegion.EXIT_ICON -> "Exit Icon"
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> "Trainer Charge-Move Controls"
            CalibrationRegion.TRAINER_INACTIVE_POKEMON -> "Inactive Cards: Upper / Lower"
            CalibrationRegion.MATCH_OUTCOME -> "Match Outcome"
            // Pokémon GO shows these two labels together only on the
            // post-match screen. This is not a generic game-menu crop.
            CalibrationRegion.BATTLE_PARTY_TABS -> "Post-Match Battle / Party Selector"
            CalibrationRegion.OUT_OF_BATTLE_MENU -> "Post-Match Menu Support"
            CalibrationRegion.OPPONENT_SPECIES_NAME -> "Opponent Species Name"
            CalibrationRegion.PLAYER_POKE_BALLS -> "Player Poké Balls"
            CalibrationRegion.OPPONENT_POKE_BALLS -> "Opponent Poké Balls"
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> "Team Select League Badge"
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> "Team Select League Text"
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> "Team Select Restrictions"
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> "Team Select Use This Party"
            CalibrationRegion.TEAM_SELECT_ROSTER_1 -> "Team Select Player Card 1"
            CalibrationRegion.TEAM_SELECT_ROSTER_2 -> "Team Select Player Card 2"
            CalibrationRegion.TEAM_SELECT_ROSTER_3 -> "Team Select Player Card 3"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> "Team Select Player Name 1"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> "Team Select Player Name 2"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> "Team Select Player Name 3"
            else -> region.name
        }
    }

    val activeRegion = when (selectedRegion) {
        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationRegion.PLAYER_SHIELDS -> calibration.playerShieldsRegion
        CalibrationRegion.OPPONENT_SHIELDS -> calibration.opponentShieldsRegion
        CalibrationRegion.YOU_WIN -> calibration.youWinRegion
        CalibrationRegion.GOOD_EFFORT -> calibration.goodEffortRegion
        CalibrationRegion.COUNTDOWN -> calibration.countdownRegion
        CalibrationRegion.VS_SCREEN -> calibration.vsScreenRegion
        CalibrationRegion.ANNOUNCEMENT -> calibration.announcementRegion
        CalibrationRegion.TRAINER_TEAM_INFO -> calibration.playerTeamInfoRegion
        CalibrationRegion.OPPONENT_TEAM_INFO -> calibration.opponentTeamInfoRegion
        CalibrationRegion.PLAYER_SPECIES_NAME -> calibration.playerSpeciesNameRegion
        CalibrationRegion.TRAINER_ACTIVE_TYPE -> calibration.trainerActiveTypeRegion
        CalibrationRegion.OPPONENT_ACTIVE_TYPE -> calibration.opponentActiveTypeRegion
        CalibrationRegion.TRAINER_HP -> calibration.trainerHpRegion
        CalibrationRegion.OPPONENT_HP -> calibration.opponentHpRegion
        CalibrationRegion.CHARGE_MOVE_EXECUTION -> calibration.chargeMoveExecutionRegion
        CalibrationRegion.QTE_PROGRESS_TEXT -> calibration.qteProgressTextRegion
        CalibrationRegion.QTE_TYPE_ICONS -> calibration.qteTypeIconsRegion
        CalibrationRegion.EXIT_ICON -> calibration.exitIconRegion
        CalibrationRegion.POINTER_COORDINATES -> calibration.pointerCoordinatesRegion
        CalibrationRegion.DECISION_TIMER -> calibration.decisionTimerRegion
        CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_LEFT -> calibration.playerChargeMoveNameLeftRegion
        CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_LEFT -> calibration.playerChargeMoveEffectivenessLeftRegion
        CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_RIGHT -> calibration.playerChargeMoveNameRightRegion
        CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_RIGHT -> calibration.playerChargeMoveEffectivenessRightRegion
        CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> calibration.trainerChargeMoveControlsRegion
        CalibrationRegion.TRAINER_INACTIVE_POKEMON -> calibration.trainerInactivePokemonRegion
        CalibrationRegion.MATCH_OUTCOME -> calibration.matchOutcomeRegion
        CalibrationRegion.BATTLE_PARTY_TABS -> calibration.battlePartyTabsRegion
        CalibrationRegion.OUT_OF_BATTLE_MENU -> calibration.outOfBattleMenuRegion
        CalibrationRegion.OPPONENT_SPECIES_NAME -> calibration.opponentSpeciesNameRegion
        CalibrationRegion.PLAYER_POKE_BALLS -> calibration.playerPokeBallsRegion
        CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.opponentPokeBallsRegion
        CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> teamSelectCalibration.leagueBadge
        CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> teamSelectCalibration.leagueText
        CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> teamSelectCalibration.restrictions
        CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> teamSelectCalibration.useThisParty
        CalibrationRegion.TEAM_SELECT_ROSTER_1 -> teamSelectCalibration.rosterSlot1
        CalibrationRegion.TEAM_SELECT_ROSTER_2 -> teamSelectCalibration.rosterSlot2
        CalibrationRegion.TEAM_SELECT_ROSTER_3 -> teamSelectCalibration.rosterSlot3
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> teamSelectCalibration.rosterName1
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> teamSelectCalibration.rosterName2
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> teamSelectCalibration.rosterName3
        else -> calibration.enemyNameRegion
    }

    fun updateCalibration(updated: AnchorRegion) {
        if (calibrationReadOnlyReason != null) {
            profileActionError = calibrationReadOnlyReason
            return
        }
        val teamSelectUpdate = when (selectedRegion) {
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> teamSelectCalibration.copy(leagueBadge = updated)
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> teamSelectCalibration.copy(leagueText = updated)
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> teamSelectCalibration.copy(restrictions = updated)
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> teamSelectCalibration.copy(useThisParty = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_1 -> teamSelectCalibration.copy(rosterSlot1 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_2 -> teamSelectCalibration.copy(rosterSlot2 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_3 -> teamSelectCalibration.copy(rosterSlot3 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> teamSelectCalibration.copy(rosterName1 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> teamSelectCalibration.copy(rosterName2 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> teamSelectCalibration.copy(rosterName3 = updated)
            else -> null
        }
        if (teamSelectUpdate != null) {
            teamSelectCalibration = teamSelectUpdate
            teamSelectProfileSaved = teamSelectCalibrationStore.save(
                teamSelectUpdate,
                publishedWidth = sourceFrameSize.width.takeIf { it > 0 } ?: 1080,
                publishedHeight = sourceFrameSize.height.takeIf { it > 0 } ?: 2400
            )
            teamSelectSaveFailed = !teamSelectProfileSaved
            activeProfile = profileStore.activeSummary()
            return
        }
        calibration = when (selectedRegion) {
            CalibrationRegion.ENEMY_NAME -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.HP_BAR -> calibration.copy(hpBarRegion = updated)
            CalibrationRegion.TEAM_ICONS -> calibration.copy(teamIconsRegion = updated)
            CalibrationRegion.MOVE_BANNER -> calibration.copy(moveBannerRegion = updated)
            CalibrationRegion.PLAYER_SHIELDS -> calibration.copy(playerShieldsRegion = updated)
            CalibrationRegion.OPPONENT_SHIELDS -> calibration.copy(opponentShieldsRegion = updated)
            CalibrationRegion.YOU_WIN -> calibration.copy(youWinRegion = updated)
            CalibrationRegion.GOOD_EFFORT -> calibration.copy(goodEffortRegion = updated)
            CalibrationRegion.COUNTDOWN -> calibration.copy(countdownRegion = updated)
            CalibrationRegion.VS_SCREEN -> calibration.copy(vsScreenRegion = updated)
            CalibrationRegion.ANNOUNCEMENT -> calibration.copy(announcementRegion = updated)
            CalibrationRegion.TRAINER_TEAM_INFO -> calibration.copy(playerTeamInfoRegion = updated)
            CalibrationRegion.OPPONENT_TEAM_INFO -> calibration.copy(opponentTeamInfoRegion = updated)
            CalibrationRegion.PLAYER_SPECIES_NAME -> calibration.copy(playerSpeciesNameRegion = updated)
            CalibrationRegion.TRAINER_ACTIVE_TYPE -> calibration.copy(trainerActiveTypeRegion = updated)
            CalibrationRegion.OPPONENT_ACTIVE_TYPE -> calibration.copy(opponentActiveTypeRegion = updated)
            CalibrationRegion.TRAINER_HP -> calibration.copy(trainerHpRegion = updated)
            CalibrationRegion.OPPONENT_HP -> calibration.copy(opponentHpRegion = updated)
            CalibrationRegion.CHARGE_MOVE_EXECUTION -> calibration.copy(chargeMoveExecutionRegion = updated)
            CalibrationRegion.QTE_PROGRESS_TEXT -> calibration.copy(qteProgressTextRegion = updated)
            CalibrationRegion.QTE_TYPE_ICONS -> calibration.copy(qteTypeIconsRegion = updated)
            CalibrationRegion.EXIT_ICON -> calibration.copy(exitIconRegion = updated)
            CalibrationRegion.POINTER_COORDINATES -> calibration.copy(pointerCoordinatesRegion = updated)
            CalibrationRegion.DECISION_TIMER -> calibration.copy(decisionTimerRegion = updated)
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_LEFT -> calibration.copy(playerChargeMoveNameLeftRegion = updated)
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_LEFT -> calibration.copy(playerChargeMoveEffectivenessLeftRegion = updated)
            CalibrationRegion.PLAYER_CHARGE_MOVE_NAME_RIGHT -> calibration.copy(playerChargeMoveNameRightRegion = updated)
            CalibrationRegion.PLAYER_CHARGE_MOVE_EFFECTIVENESS_RIGHT -> calibration.copy(playerChargeMoveEffectivenessRightRegion = updated)
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> calibration.copy(trainerChargeMoveControlsRegion = updated)
            CalibrationRegion.TRAINER_INACTIVE_POKEMON -> calibration.copy(trainerInactivePokemonRegion = updated)
            CalibrationRegion.MATCH_OUTCOME -> calibration.copy(matchOutcomeRegion = updated)
            CalibrationRegion.BATTLE_PARTY_TABS -> calibration.copy(battlePartyTabsRegion = updated)
            CalibrationRegion.OUT_OF_BATTLE_MENU -> calibration.copy(outOfBattleMenuRegion = updated)
            CalibrationRegion.OPPONENT_SPECIES_NAME -> calibration.copy(opponentSpeciesNameRegion = updated)
            CalibrationRegion.PLAYER_POKE_BALLS -> calibration.copy(playerPokeBallsRegion = updated)
            CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.copy(opponentPokeBallsRegion = updated)
            else -> calibration
        }
        battleProfileSaved = calibrationManager.save(calibration)
        battleSaveFailed = !battleProfileSaved
        activeProfile = profileStore.activeSummary()
        if (selectedRegion == CalibrationRegion.MATCH_OUTCOME) {
            calibrationManager.recordMatchOutcomeCalibration()
        }
    }

    val step = 0.005f // Fine adjustment: 0.5% of the published frame.
    val acceleratedStep = step * 4f
    val calibrationControlsBlocked = showProfileDialog || showProfileMenu || showSaveProfileDialog ||
        showLibraryDialog || showImagePicker

    fun move(dx: Float, dy: Float) {
        if (calibrationControlsBlocked) return
        val maxX = (1f - activeRegion.width).coerceAtLeast(0f)
        val maxY = (1f - activeRegion.height).coerceAtLeast(0f)
        updateCalibration(
            activeRegion.copy(
                x = (activeRegion.x + dx).coerceIn(0f, maxX),
                y = (activeRegion.y + dy).coerceIn(0f, maxY)
            )
        )
    }

    fun resize(dw: Float, dh: Float) {
        if (calibrationControlsBlocked) return
        val maxWidth = (1f - activeRegion.x).coerceAtLeast(0.01f)
        val maxHeight = (1f - activeRegion.y).coerceAtLeast(0.01f)
        updateCalibration(
            activeRegion.copy(
                width = (activeRegion.width + dw).coerceIn(0.01f, maxWidth),
                height = (activeRegion.height + dh).coerceIn(0.01f, maxHeight)
            )
        )
    }

    fun openSaveProfileDialog() {
        val matchingActiveProfile = activeProfile?.takeIf {
            CalibrationProfileCompatibility.matchesDevice(it, detectedDeviceModel,
                detectedScreenWidth, detectedScreenHeight)
        }
        // An active profile can arrive from another phone through restored app
        // data. On a new device, offer Android's current full-display bounds
        // instead of silently carrying the former phone's dimensions forward.
        profileModel = matchingActiveProfile?.deviceModel ?: detectedDeviceModel
        profileWidth = (matchingActiveProfile?.screenWidth ?: detectedScreenWidth).toString()
        profileHeight = (matchingActiveProfile?.screenHeight ?: detectedScreenHeight).toString()
        profileName = matchingActiveProfile?.name
            ?: "$detectedDeviceModel ${detectedScreenWidth}x$detectedScreenHeight"
        profileSaveError = null
        showSaveProfileDialog = true
    }

    fun displayProfile(profile: MatchCalibrationProfile) {
        // Selecting a device is explicit. Never scale its saved boxes to this screen.
        // The initial device guess must not reopen Save As over an explicit selection.
        deviceGuessOffered = true
        calibration = profile.battleCalibration
        teamSelectCalibration = profile.teamSelectCalibration
        activeProfile = profileStore.activeSummary()
        savedProfiles = profileStore.list()
        battleProfileSaved = true
        teamSelectProfileSaved = true
        battleSaveFailed = false
        teamSelectSaveFailed = false
        profileActionError = null
        reviewedProfile = null
    }

    fun useDetectedDeviceGuess() {
        profileModel = detectedDeviceModel
        profileWidth = detectedScreenWidth.toString()
        profileHeight = detectedScreenHeight.toString()
        profileName = "$detectedDeviceModel ${detectedScreenWidth}x$detectedScreenHeight"
        profileSaveError = null
    }

    fun saveNamedProfile() {
        if (referenceReadOnlyReason != null) {
            profileSaveError = referenceReadOnlyReason
            return
        }
        val width = profileWidth.toIntOrNull()
        val height = profileHeight.toIntOrNull()
        if (profileName.isBlank() || profileModel.isBlank() || width == null || height == null ||
            width <= 0 || height <= 0
        ) {
            profileSaveError = "Enter a name, model, width, and height."
            return
        }
        if (!CalibrationProfileCompatibility.matchesDevice(
                MatchCalibrationProfileSummary("", profileName, profileModel, width, height, 0),
                detectedDeviceModel, detectedScreenWidth, detectedScreenHeight)) {
            profileSaveError = "Save a profile for the detected device. Other devices are available for review only."
            return
        }
        val saved = profileStore.saveAs(
            name = profileName,
            deviceModel = profileModel,
            screenWidth = width,
            screenHeight = height,
            battleCalibration = calibration,
            teamSelectCalibration = teamSelectCalibration
        )
        if (saved == null) {
            profileSaveError = "The profile could not be saved."
            return
        }
        activeProfile = profileStore.activeSummary()
        savedProfiles = profileStore.list()
        battleProfileSaved = calibrationManager.save(calibration)
        teamSelectProfileSaved = teamSelectCalibrationStore.save(teamSelectCalibration, width, height)
        battleSaveFailed = !battleProfileSaved
        teamSelectSaveFailed = !teamSelectProfileSaved
        if (battleSaveFailed || teamSelectSaveFailed) {
            profileSaveError = "The profile was saved, but its device settings could not be updated. Please retry."
            return
        }
        showSaveProfileDialog = false
    }

    LaunchedEffect(
        activeProfile?.id,
        detectedDeviceModel,
        detectedScreenWidth,
        detectedScreenHeight,
    ) {
        if (!deviceGuessOffered && !activeProfileMatchesDetectedDevice) {
            deviceGuessOffered = true
            openSaveProfileDialog()
        }
    }

    // Input Handling
    SideEffect {
        onUp { if (mode == CalibrationMode.POSITION) move(0f, -step) else resize(0f, -step) }
        onUpLong { if (mode == CalibrationMode.POSITION) move(0f, -acceleratedStep) else resize(0f, -acceleratedStep) }
        onDown { if (mode == CalibrationMode.POSITION) move(0f, step) else resize(0f, step) }
        onDownLong { if (mode == CalibrationMode.POSITION) move(0f, acceleratedStep) else resize(0f, acceleratedStep) }
        onLeft { if (mode == CalibrationMode.POSITION) move(-step, 0f) else resize(-step, 0f) }
        onLeftLong { if (mode == CalibrationMode.POSITION) move(-acceleratedStep, 0f) else resize(-acceleratedStep, 0f) }
        onRight { if (mode == CalibrationMode.POSITION) move(step, 0f) else resize(step, 0f) }
        onRightLong { if (mode == CalibrationMode.POSITION) move(acceleratedStep, 0f) else resize(acceleratedStep, 0f) }
        onA {
            if (!calibrationControlsBlocked) {
                val currentIndex = matchRegions.indexOf(selectedRegion)
                selectedRegion = matchRegions[(currentIndex + 1) % matchRegions.size]
            }
        }
        onALong { if (!calibrationControlsBlocked) showLibraryDialog = true }
        onSelect {
            if (!calibrationControlsBlocked) mode = if (mode == CalibrationMode.POSITION) CalibrationMode.SIZE else CalibrationMode.POSITION
        }
        onSelectLong {
            if (!calibrationControlsBlocked) {
                if (samples.isNotEmpty()) showImagePicker = true else showLibraryDialog = true
            }
        }
        onStart {
            savedProfiles = profileStore.list()
            showOtherDeviceProfiles = false
            showProfileDialog = true
        }
        onLcdDrag { delta ->
            showLcdTouchHint = false
            val dx = if (imageViewport.width > 0f) delta.x / imageViewport.width else 0f
            val dy = if (imageViewport.height > 0f) delta.y / imageViewport.height else 0f
            if (mode == CalibrationMode.POSITION) move(dx, dy) else resize(dx, dy)
        }
        onLcdTap {
            if (!calibrationControlsBlocked) {
                val currentIndex = matchRegions.indexOf(selectedRegion)
                selectedRegion = matchRegions[(currentIndex + 1) % matchRegions.size]
            }
        }
    }

    // LCD Update
    LaunchedEffect(
        selectedRegion, mode, showLcdTouchHint, screenshotSourceName, screenshotReferenceName, sourceFrameSize,
        battleProfileSaved, teamSelectProfileSaved, battleSaveFailed, teamSelectSaveFailed, activeProfile,
        referenceMatchesDevice, calibrationReadOnlyReason,
        activeRegion.x, activeRegion.y, activeRegion.width, activeRegion.height
    ) {
        val indexText = "${matchRegions.indexOf(selectedRegion) + 1}/${matchRegions.size}"
        val sourceWidth = sourceFrameSize.width
        val sourceHeight = sourceFrameSize.height
        val x = (activeRegion.x * sourceWidth).toInt()
        val y = (activeRegion.y * sourceHeight).toInt()
        val width = (activeRegion.width * sourceWidth).toInt()
        val height = (activeRegion.height * sourceHeight).toInt()
        val selectedProfileSaved = if (selectedRegion.name.startsWith("TEAM_SELECT_")) {
            teamSelectProfileSaved
        } else {
            battleProfileSaved
        }
        val selectedSaveFailed = if (selectedRegion.name.startsWith("TEAM_SELECT_")) teamSelectSaveFailed else battleSaveFailed
        val saveStatus = when {
            calibrationReadOnlyReason != null -> "READ ONLY"
            selectedSaveFailed -> "SAVE FAILED"
            selectedProfileSaved -> "SAVED"
            else -> "DEFAULT"
        }
        onLcdUpdate(
            "${getReadableName(selectedRegion)} $indexText  X:$x Y:$y  $saveStatus",
            "HOLD A / SELECT: IMAGES  $screenshotSourceName · $screenshotReferenceName  W:$width H:$height / ${sourceWidth}×${sourceHeight}  ${mode.name}  [START] DEVICE CALIBRATIONS${activeProfile?.let { "  ${it.name}" } ?: ""}${calibrationReadOnlyReason?.let { "  $it" } ?: ""}"
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("DEVICE CALIBRATIONS", color = TerminalGreen) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This phone: $detectedDeviceModel · ${detectedScreenWidth}×${detectedScreenHeight}",
                        color = TerminalGreen, fontSize = 11.sp)
                    Text(if (showOtherDeviceProfiles) "All saved devices · ${visibleDeviceProfiles.size} profiles"
                        else "This device · ${matchingDeviceProfiles.size} matching profiles",
                        color = TerminalGreen, fontSize = 11.sp,
                        modifier = Modifier.testTag("calibration-profile-filter-status"))
                    if (savedProfiles.size > matchingDeviceProfiles.size) TextButton(onClick = {
                        showProfileMenu = false
                        showOtherDeviceProfiles = !showOtherDeviceProfiles
                    }, modifier = Modifier.fillMaxWidth().testTag("calibration-profile-show-other-devices")) {
                        Text(if (showOtherDeviceProfiles) "SHOW ONLY THIS DEVICE"
                            else "SHOW OTHER DEVICES (${savedProfiles.size - matchingDeviceProfiles.size})",
                            color = TerminalGreen)
                    }
                    if (matchingDeviceProfiles.isEmpty() && !showOtherDeviceProfiles) Text(
                        "No saved calibration matches this model and screen size. Save a profile for this device, or show other devices to review a reference.",
                        color = Color(0xFFFFC857), fontSize = 11.sp,
                        modifier = Modifier.testTag("calibration-profile-no-device-match"))
                    Box(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { savedProfiles = profileStore.list(); showProfileMenu = true },
                            modifier = Modifier.fillMaxWidth().testTag("calibration-profile-picker")) {
                            Text("${activeProfile?.name ?: "Choose saved calibration"} ▾", color = TerminalGreen)
                        }
                        DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false },
                            modifier = Modifier.heightIn(max = 360.dp)) {
                            if (visibleDeviceProfiles.isEmpty()) DropdownMenuItem(
                                text = { Text(if (savedProfiles.isEmpty()) "No named calibrations saved"
                                    else "No profiles for this device") }, enabled = false, onClick = {})
                            visibleDeviceProfiles.forEach { profile ->
                                DropdownMenuItem(modifier = Modifier.testTag("calibration-profile-${profile.id}"), text = {
                                    Column {
                                        Text("${if (profile.id == activeProfile?.id) "✓ " else ""}${profile.name}")
                                        Text("${profile.deviceModel} · ${profile.screenWidth}×${profile.screenHeight}", fontSize = 11.sp)
                                        Text(profile.workingStatus(), fontSize = 11.sp)
                                    }
                                }, onClick = {
                                    showProfileMenu = false
                                    if (!CalibrationProfileCompatibility.matchesDevice(profile, detectedDeviceModel,
                                            detectedScreenWidth, detectedScreenHeight)) {
                                        reviewedProfile = profile
                                        profileActionError = "Review only: this profile belongs to another device. This phone's calibration is unchanged."
                                    } else if (referenceReadOnlyReason != null) {
                                        profileActionError = referenceReadOnlyReason
                                    } else {
                                        val selected = profileStore.activate(profile.id)
                                        if (selected == null) profileActionError = "Could not load this calibration."
                                        else displayProfile(selected)
                                    }
                                })
                            }
                        }
                    }
                    (reviewedProfile ?: activeProfile)?.let { profile ->
                        if (reviewedProfile != null) Text("Review only · ${profile.name}", color = Color(0xFFFFC857), fontSize = 12.sp,
                            modifier = Modifier.testTag("calibration-profile-review-only"))
                        Text("${profile.deviceModel} · ${profile.screenWidth}×${profile.screenHeight}\n${profile.workingStatus()}",
                            color = TerminalGreen, fontSize = 12.sp)
                        Text("Saved ${profile.savedAtMillis.calibrationTimeLabel()}", color = TerminalGreen, fontSize = 10.sp)
                        profile.workingConfirmedAtMillis?.let { time ->
                            Text("Working copy kept ${time.calibrationTimeLabel()}", color = TerminalGreen, fontSize = 10.sp)
                        }
                    }
                    Text("Keep a working copy only after checking targets in real battle images. Later adjustments leave that restore point intact.",
                        color = TerminalGreen, fontSize = 11.sp)
                    TextButton(onClick = { showProfileDialog = false; openSaveProfileDialog() },
                        modifier = Modifier.fillMaxWidth().testTag("calibration-profile-save-as")) {
                        Text("SAVE AS…", color = TerminalGreen)
                    }
                    TextButton(enabled = reviewedProfile == null && activeProfile != null && calibrationReadOnlyReason == null, onClick = {
                        val kept = activeProfile?.let { profileStore.confirmWorking(it.id) }
                        if (kept == null) profileActionError = "Save a named calibration before keeping a working copy."
                        else displayProfile(kept)
                    }, modifier = Modifier.fillMaxWidth().testTag("calibration-profile-keep-working")) {
                        Text("KEEP AS WORKING", color = TerminalGreen)
                    }
                    TextButton(enabled = reviewedProfile == null && activeProfile?.workingConfirmedAtMillis != null && calibrationReadOnlyReason == null, onClick = {
                        val restored = activeProfile?.let { profileStore.restoreWorking(it.id) }
                        if (restored == null) profileActionError = "Could not restore the working copy."
                        else displayProfile(restored)
                    }, modifier = Modifier.fillMaxWidth().testTag("calibration-profile-restore-working")) {
                        Text("RESTORE WORKING", color = if (activeProfile?.workingConfirmedAtMillis != null)
                            TerminalGreen else TerminalGreen.copy(alpha = .38f))
                    }
                    if (activeProfile != null && !activeProfileMatchesDetectedDevice) Text(
                        "Different device selected. Its boxes are unchanged. Use Save As to keep a copy for this phone before adjusting.",
                        color = Color(0xFFFFC857), fontSize = 11.sp)
                    if (droidballRuntime != DroidballRuntimeState.STOPPED) Text(
                        "Droidball is running. Saved changes apply after observation restarts.",
                        color = Color(0xFFFFC857), fontSize = 11.sp)
                    profileActionError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 11.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { showProfileDialog = false }) { Text("CLOSE", color = TerminalGreen) } },
            containerColor = TerminalBlack
        )
    }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("SAVE CALIBRATION AS", color = TerminalGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "DETECTED GUESS: $detectedDeviceModel  ${detectedScreenWidth}×${detectedScreenHeight}",
                        color = TerminalGreen,
                        fontSize = 12.sp,
                    )
                    activeProfile?.takeIf {
                        !CalibrationProfileCompatibility.matchesDevice(it, detectedDeviceModel,
                            detectedScreenWidth, detectedScreenHeight)
                    }?.let { current ->
                        Text(
                            "ACTIVE PROFILE: ${current.deviceModel}  ${current.screenWidth}×${current.screenHeight}",
                            color = Color(0xFFFFC857),
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = ::useDetectedDeviceGuess) {
                        Text("USE DETECTED DEVICE", color = TerminalGreen)
                    }
                    TextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile name") },
                        colors = calibrationTextFieldColors()
                    )
                    TextField(
                        value = profileModel,
                        onValueChange = { profileModel = it },
                        label = { Text("Phone model") },
                        colors = calibrationTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = profileWidth,
                            onValueChange = { profileWidth = it.filter(Char::isDigit) },
                            label = { Text("Width") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = calibrationTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = profileHeight,
                            onValueChange = { profileHeight = it.filter(Char::isDigit) },
                            label = { Text("Height") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = calibrationTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    profileSaveError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                        modifier = Modifier.testTag("calibration-profile-save-error")) }
                }
            },
            confirmButton = {
                TextButton(onClick = ::saveNamedProfile) { Text("SAVE", color = TerminalGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) { Text("CANCEL", color = TerminalGreen) }
            },
            containerColor = TerminalBlack
        )
    }

    if (showLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showLibraryDialog = false },
            title = { Text("SCREENSHOT LIBRARY", color = TerminalGreen) },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("$screenshotFilterLabel · ${samples.size} screenshot(s)", color = TerminalGreen, fontSize = 13.sp)
                    selectedSample?.let { sample ->
                        Text(sample.reference.name, color = TerminalGreen, fontSize = 12.sp)
                        Text(sample.reference.dimensionLabel(), color = TerminalGreen, fontSize = 11.sp)
                    }
                    TextButton(
                        enabled = samples.isNotEmpty(),
                        onClick = { showLibraryDialog = false; showImagePicker = true }
                    ) { Text("CHOOSE SCREENSHOT", color = TerminalGreen) }
                    TextButton(onClick = {
                        showLibraryDialog = false
                        screenshotFolderPicker.launch(screenshotDirectory.folderUri())
                    }) { Text("CHOOSE SCREENSHOT FOLDER", color = TerminalGreen) }
                    val choices: List<Pair<CalibrationScreenshotFilter, String>> = listOf(
                        CalibrationScreenshotFilter.ThisScreen to "This screen (${CalibrationScreenshotLibrary.shapeLabel(detectedScreenWidth, detectedScreenHeight)})",
                        CalibrationScreenshotFilter.All to "All screenshots"
                    ) + CalibrationScreenshotLibrary.shapeChoices(references).map { shape ->
                        shape to "Shape ${CalibrationScreenshotLibrary.shapeLabel(shape.width, shape.height)}"
                    } + references.mapNotNull { it.deviceLabel }.distinct().sorted().map { label ->
                        CalibrationScreenshotFilter.Device(label) to "Device folder: $label"
                    }
                    Text("SCREEN / DEVICE", color = TerminalGreen, fontSize = 12.sp)
                    choices.forEach { (filter, label) ->
                        TextButton(onClick = { screenshotFilter = filter; currentImageIndex = 0 }) {
                            Text("${if (screenshotFilter == filter) "✓ " else ""}$label", color = TerminalGreen)
                        }
                    }
                    val unknownCount = references.count { !it.hasDimensions }
                    if (unknownCount > 0) {
                        Text(
                            "$unknownCount image(s) have unknown dimensions.\nChoose All screenshots to review them; editing is disabled.",
                            color = Color(0xFFFFC857), fontSize = 11.sp
                        )
                    }
                    if (selectedSample?.reference?.hasDimensions == true && !CalibrationScreenshotLibrary.sameShape(
                            sourceFrameSize.width, sourceFrameSize.height, detectedScreenWidth, detectedScreenHeight
                        )) {
                        Text(
                            "This reference has a different screen shape.\nPreviewing it does not change your saved profile.",
                            color = Color(0xFFFFC857), fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryDialog = false }) { Text("CLOSE", color = TerminalGreen) }
            },
            containerColor = TerminalBlack
        )
    }

    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("CHOOSE SCREENSHOT", color = TerminalGreen) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    samples.forEachIndexed { index, sample ->
                        TextButton(onClick = { currentImageIndex = index; showImagePicker = false }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(sample.reference.name, color = TerminalGreen, fontSize = 13.sp)
                                Text(
                                    listOfNotNull(sample.reference.deviceLabel, sample.reference.dimensionLabel()).joinToString(" · "),
                                    color = TerminalGreen.copy(alpha = 0.75f), fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) { Text("CLOSE", color = TerminalGreen) }
            },
            containerColor = TerminalBlack
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(TerminalBlack).padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("START: DEVICE CALIBRATIONS · ${activeProfile?.name ?: "Unsaved device settings"}",
                color = TerminalGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(activeProfile?.let {
                "${it.deviceModel} · ${it.screenWidth}×${it.screenHeight} · ${it.workingStatus()}"
            } ?: "$detectedDeviceModel · ${detectedScreenWidth}×${detectedScreenHeight} · Not confirmed",
                color = TerminalGreen, fontSize = 10.sp)
            Text("Reference: $screenshotReferenceName · ${sourceFrameSize.width}×${sourceFrameSize.height}",
                color = TerminalGreen.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            activeProfile?.workingConfirmedAtMillis?.let { confirmedAt ->
                Text("Working copy kept ${confirmedAt.calibrationTimeLabel()}",
                    color = TerminalGreen.copy(alpha = 0.8f), fontSize = 10.sp)
            }
            if (activeProfile != null && !activeProfileMatchesDetectedDevice) Text(
                "Different device selected. Boxes stay unchanged; save a copy for this phone before adjusting.",
                color = Color(0xFFFFC857), fontSize = 10.sp)
            calibrationReadOnlyReason?.let { reason -> Text(reason,
                color = Color(0xFFFFC857), fontSize = 10.sp,
                modifier = Modifier.testTag("calibration-reference-read-only")) }
            if (droidballRuntime != DroidballRuntimeState.STOPPED) Text(
                "Droidball is running. Saved changes apply after observation restarts.",
                color = Color(0xFFFFC857), fontSize = 10.sp)
            profileActionError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 11.sp) }
        }
    Box(
        modifier = Modifier
            .fillMaxWidth().weight(1f)
            .onGloballyPositioned { containerSize = it.size.toSize() }
    ) {
        // Reference Image
        AsyncImage(
            model = selectedSample?.imageModel,
            contentDescription = "Calibration Background",
            modifier = Modifier.fillMaxSize().testTag(if (calibrationReadOnlyReason == null)
                "calibration-reference-ready" else "calibration-reference-image"),
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                selectedSample?.reference?.id?.let { id ->
                    renderedReference = id to IntSize(state.result.drawable.intrinsicWidth, state.result.drawable.intrinsicHeight)
                    failedReferenceId = null
                }
            },
            onError = { failedReferenceId = selectedSample?.reference?.id; renderedReference = null },
            onLoading = { renderedReference = null },
        )

        // Selected Region Overlay (Draw ONLY the selected region's rectangle)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = TerminalPurple
            val stroke = 1.dp.toPx()

            if (imageViewport.width > 0f && imageViewport.height > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset(
                        imageViewport.left + activeRegion.x * imageViewport.width,
                        imageViewport.top + activeRegion.y * imageViewport.height
                    ),
                    size = Size(activeRegion.width * imageViewport.width, activeRegion.height * imageViewport.height),
                    style = Stroke(width = stroke)
                )
                if (selectedRegion == CalibrationRegion.TRAINER_INACTIVE_POKEMON) {
                    val middleY = imageViewport.top + (activeRegion.y + activeRegion.height / 2f) * imageViewport.height
                    drawLine(
                        color = color,
                        start = Offset(imageViewport.left + activeRegion.x * imageViewport.width, middleY),
                        end = Offset(imageViewport.left + (activeRegion.x + activeRegion.width) * imageViewport.width, middleY),
                        strokeWidth = stroke,
                    )
                }
            }
        }

        if (samples.isEmpty()) {
            Text(
                if (library == null) "Loading screenshots…" else "No screenshots match this screen.\nHold A to choose a screen shape or All screenshots.",
                color = TerminalGreen,
                modifier = Modifier.align(Alignment.Center).background(TerminalBlack).padding(12.dp),
                fontSize = 13.sp
            )
        }
    }
    }
}

private fun MatchCalibrationProfileSummary.workingStatus(): String = when {
    workingConfirmedAtMillis == null -> "Saved · not confirmed"
    differsFromWorkingCheckpoint -> "Edited since confirmation"
    else -> "User confirmed working"
}

private fun Long.calibrationTimeLabel(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

internal data class CalibrationSample(
    val model: Any,
    val reference: CalibrationScreenshotReference,
    val sourceName: String,
) {
    val imageModel: Any get() = if (model is String) "file:///android_asset/battle_samples/$model" else model
}

/** Enumerate files and decode only their headers on the caller's IO dispatcher. */
internal fun loadCalibrationSamples(
    context: android.content.Context,
    directory: ScreenshotDirectoryManager,
    userImages: List<ScreenshotDirectoryManager.Image> = runCatching { directory.images() }.getOrDefault(emptyList()),
): List<CalibrationSample> {
    // A selected library supplements this phone's own capture; it never displaces it.
    val captured = BattleCalibrationReferenceStore(context.filesDir).latest()?.let { file ->
        CalibrationSample(file, CalibrationScreenshotReference(file.absolutePath, "Latest device capture", 0, 0), "DEVICE CAPTURE")
    }
    val userLibrary = userImages.map { image ->
            CalibrationSample(image.uri, CalibrationScreenshotReference(image.uri.toString(), image.name, 0, 0, image.deviceLabel), "YOUR SCREENSHOT")
    }
    val bundledLabels = runCatching {
        context.assets.open(BundledCalibrationSampleCatalog.ASSET_PATH).bufferedReader().use {
            BundledCalibrationSampleCatalog.labels(it.readText())
        }
    }.getOrDefault(emptyMap())
    val bundledLibrary = context.assets.list("battle_samples").orEmpty().sorted()
        .filter { it.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        .map { name ->
            val label = bundledLabels[name]
            CalibrationSample(name, CalibrationScreenshotReference("asset:$name", label?.name ?: name, 0, 0,
                label?.deviceLabel), "BUNDLED REFERENCE")
        }
    // Keep device-specific reference packs reachable even when a custom folder is selected.
    val samples = listOfNotNull(captured) + userLibrary + bundledLibrary
    return samples.map { sample ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            when (val model = sample.model) {
                is String -> context.assets.open("battle_samples/$model").use { BitmapFactory.decodeStream(it, null, options) }
                is Uri -> context.contentResolver.openInputStream(model)?.use { BitmapFactory.decodeStream(it, null, options) }
                is File -> BitmapFactory.decodeFile(model.absolutePath, options)
            }
        }
        sample.copy(reference = sample.reference.copy(width = options.outWidth.coerceAtLeast(0), height = options.outHeight.coerceAtLeast(0)))
    }
}

private fun CalibrationScreenshotReference.dimensionLabel(): String =
    if (hasDimensions) "$width × $height (${CalibrationScreenshotLibrary.shapeLabel(width, height)})" else "Dimensions unknown"

@Composable
private fun calibrationTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = TerminalBlack,
    unfocusedContainerColor = TerminalBlack,
    focusedTextColor = TerminalGreen,
    unfocusedTextColor = TerminalGreen,
    focusedLabelColor = TerminalGreen,
    unfocusedLabelColor = TerminalGreen.copy(alpha = 0.75f),
    cursorColor = TerminalGreen
)
