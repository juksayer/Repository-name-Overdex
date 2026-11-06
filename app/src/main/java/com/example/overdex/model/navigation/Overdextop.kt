package com.example.overdex.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.overdex.model.OwnedPokemonBinder

enum class HomeScreenStyle { OVERDEXTOP, DIRECTORY }
enum class OverdextopSurface { DESKTOP, PROGRAMS, SETTINGS }
enum class OverdextopDirection { UP, DOWN, LEFT, RIGHT }
enum class OverdextopIcon {
    DROIDBALL, POKEDEX, TEAM, BINDER, HISTORY, EXPLORER, PROGRAMS,
    RECORDS, REPLAY, TOURNAMENT, PROFILE, RESEARCH, CALIBRATION, SETTINGS,
}

sealed interface OverdextopAction {
    data class OpenProgram(val command: InstrumentCommand) : OverdextopAction
    data object OpenExplorer : OverdextopAction
    data object OpenPrograms : OverdextopAction
    data object OpenSettings : OverdextopAction
    data class ChooseHomeStyle(val style: HomeScreenStyle) : OverdextopAction
}

data class OverdextopEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: OverdextopIcon,
    val action: OverdextopAction,
)

/** Alternate launchers share the existing programs and their data. */
object OverdextopCatalog {
    private fun program(
        id: String,
        title: String,
        description: String,
        icon: OverdextopIcon,
        command: InstrumentCommand,
    ) = OverdextopEntry(id, title, description, icon, OverdextopAction.OpenProgram(command))

    private val droidball = program("droidball", "Droidball", "Prepare Droidball and follow Pokémon GO battles.", OverdextopIcon.DROIDBALL, InstrumentCommand.LaunchDroidball)
    private val pokedex = program("pokedex", "Overdex", "Browse Pokémon, types, and moves.", OverdextopIcon.POKEDEX, InstrumentCommand.OpenSearch)
    private val currentTeam = program("current_team", "current team", "Choose your battle team and edit its moves.", OverdextopIcon.TEAM, InstrumentCommand.OpenCurrentTeam)
    private val binders = program("binders", "binders", "Browse your owned Pokémon cards.", OverdextopIcon.BINDER, InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.ALL))
    private val history = program("match_history", "match history", "Review saved battles, summaries, and replays.", OverdextopIcon.HISTORY, InstrumentCommand.OpenBattleHistory)
    private val explorer = OverdextopEntry("explorer", "explorer", "Browse the complete directory tree.", OverdextopIcon.EXPLORER, OverdextopAction.OpenExplorer)
    private val settings = OverdextopEntry("settings", "settings", "Choose the home screen for this handheld.", OverdextopIcon.SETTINGS, OverdextopAction.OpenSettings)

    val desktop: List<OverdextopEntry> = listOf(
        droidball, pokedex, currentTeam, binders, history, explorer,
        OverdextopEntry("programs", "programs", "Open the complete list of programs and settings.", OverdextopIcon.PROGRAMS, OverdextopAction.OpenPrograms),
    )

    val programs: List<OverdextopEntry> = listOf(
        droidball, pokedex, currentTeam, binders,
        program("manage_binders", "manage binders", "Create or edit views of your owned Pokémon.", OverdextopIcon.BINDER, InstrumentCommand.ManageBinders),
        program("add_pokemon", "add Pokémon", "Add a Pokémon card to your collection.", OverdextopIcon.POKEDEX, InstrumentCommand.AddSpecimen),
        history,
        program("records", "battle records", "Inspect your recorded battle statistics.", OverdextopIcon.RECORDS, InstrumentCommand.OpenBattleRecords),
        program("replay_editor", "replay editor", "Create clips and compilations from saved battles.", OverdextopIcon.REPLAY, InstrumentCommand.OpenReplayEditor),
        program("tournaments", "tournaments", "Prepare a lobby and organize a tournament.", OverdextopIcon.TOURNAMENT, InstrumentCommand.OpenTournament),
        program("profile", "profile", "View and edit your trainer profile.", OverdextopIcon.PROFILE, InstrumentCommand.OpenProfile),
        program("intueor", "intueor", "Inspect battle signals and recording evidence.", OverdextopIcon.RESEARCH, InstrumentCommand.OpenSignalObservatory),
        program("match_calibration", "match calibration", "Position battle crops and manage device profiles.", OverdextopIcon.CALIBRATION, InstrumentCommand.OpenMatchCalibration),
        program("crop_calibration", "crop previews", "Review captured battle crops in the LCD.", OverdextopIcon.CALIBRATION, InstrumentCommand.OpenDirectoryCalibration),
        explorer, settings,
    )

    val settingsEntries: List<OverdextopEntry> = listOf(
        OverdextopEntry("home_overdextop", "Overdextop", "Use desktop shortcuts and the programs menu at home.", OverdextopIcon.PROGRAMS, OverdextopAction.ChooseHomeStyle(HomeScreenStyle.OVERDEXTOP)),
        OverdextopEntry("home_directory", "directory tree", "Use the directory tree as the home screen.", OverdextopIcon.EXPLORER, OverdextopAction.ChooseHomeStyle(HomeScreenStyle.DIRECTORY)),
    )

    fun entries(surface: OverdextopSurface): List<OverdextopEntry> = when (surface) {
        OverdextopSurface.DESKTOP -> desktop
        OverdextopSurface.PROGRAMS -> programs
        OverdextopSurface.SETTINGS -> settingsEntries
    }
}

/** Selection and menu navigation only; launching or saving remains the root's job. */
@Stable
class OverdextopController {
    var surface by mutableStateOf(OverdextopSurface.DESKTOP)
        private set
    var selectedIndex by mutableStateOf(0)
        private set
    private val selections = mutableMapOf<OverdextopSurface, Int>()
    private var settingsParent = OverdextopSurface.PROGRAMS
    private var programsReturnIndex = 3

    val entries: List<OverdextopEntry> get() = OverdextopCatalog.entries(surface)
    val selectedEntry: OverdextopEntry get() = entries[selectedIndex]

    fun select(id: String): Boolean {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (surface == OverdextopSurface.DESKTOP && index == 6 && selectedIndex in 3..5) {
            programsReturnIndex = selectedIndex
        }
        selectedIndex = index
        return true
    }

    fun move(direction: OverdextopDirection) {
        selectedIndex = if (surface == OverdextopSurface.DESKTOP) {
            when (direction) {
                OverdextopDirection.UP -> when {
                    selectedIndex == 6 -> programsReturnIndex
                    selectedIndex >= 3 -> selectedIndex - 3
                    else -> selectedIndex
                }
                OverdextopDirection.DOWN -> when {
                    selectedIndex < 3 -> selectedIndex + 3
                    selectedIndex < 6 -> { programsReturnIndex = selectedIndex; 6 }
                    else -> selectedIndex
                }
                OverdextopDirection.LEFT -> if (selectedIndex < 6 && selectedIndex % 3 > 0) selectedIndex - 1 else selectedIndex
                OverdextopDirection.RIGHT -> if (selectedIndex < 6 && selectedIndex % 3 < 2) selectedIndex + 1 else selectedIndex
            }
        } else {
            when (direction) {
                OverdextopDirection.UP -> (selectedIndex - 1).coerceAtLeast(0)
                OverdextopDirection.DOWN -> (selectedIndex + 1).coerceAtMost(entries.lastIndex)
                OverdextopDirection.LEFT, OverdextopDirection.RIGHT -> selectedIndex
            }
        }
    }

    fun confirm(): OverdextopAction? = when (val action = selectedEntry.action) {
        OverdextopAction.OpenPrograms -> { openPrograms(); null }
        OverdextopAction.OpenSettings -> { openSettings(); null }
        else -> action
    }

    fun openPrograms() = show(OverdextopSurface.PROGRAMS)

    fun openSettings() {
        if (surface != OverdextopSurface.SETTINGS) settingsParent = surface
        show(OverdextopSurface.SETTINGS)
    }

    fun home() = show(OverdextopSurface.DESKTOP)

    fun back(): Boolean = when (surface) {
        OverdextopSurface.DESKTOP -> false
        OverdextopSurface.PROGRAMS -> { home(); true }
        OverdextopSurface.SETTINGS -> { show(settingsParent); true }
    }

    private fun show(next: OverdextopSurface) {
        if (next == surface) return
        selections[surface] = selectedIndex
        surface = next
        selectedIndex = (selections[next] ?: 0).coerceIn(0, entries.lastIndex)
    }
}
