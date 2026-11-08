package com.example.overdex.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.overdex.model.OwnedPokemonBinder

enum class HomeScreenStyle { OVERDEXTOP, DIRECTORY }
enum class OverdextopSurface { DESKTOP, PROGRAMS, SETTINGS, SHORTCUTS }
enum class OverdextopDirection { UP, DOWN, LEFT, RIGHT }
enum class LauncherShortcutLocation { DESKTOP, PROGRAMS }

data class LauncherShortcutVisibility(val desktop: Boolean, val programs: Boolean)

/** Missing/new destinations receive defaults; unknown persisted IDs never become programs. */
data class LauncherShortcutConfiguration(
    val destinations: Map<String, LauncherShortcutVisibility> = emptyMap(),
) {
    fun visibility(id: String): LauncherShortcutVisibility {
        val defaults = OverdextopCatalog.defaultVisibility(id)
        if (OverdextopCatalog.destinations.none { it.id == id }) return defaults
        val saved = destinations[id] ?: defaults
        return if (id == "settings") saved.copy(programs = true) else saved
    }

    fun withVisibility(id: String, location: LauncherShortcutLocation, visible: Boolean): LauncherShortcutConfiguration {
        if (OverdextopCatalog.destinations.none { it.id == id }) return normalized()
        val current = visibility(id)
        val next = when (location) {
            LauncherShortcutLocation.DESKTOP -> current.copy(desktop = visible)
            LauncherShortcutLocation.PROGRAMS -> current.copy(programs = visible)
        }
        return copy(destinations = destinations + (id to next)).normalized()
    }

    fun normalized(): LauncherShortcutConfiguration = LauncherShortcutConfiguration(
        OverdextopCatalog.destinations.associate { it.id to visibility(it.id) }
    )
}
enum class OverdextopIcon {
    DROIDBALL, POKEDEX, TEAM, BINDER, HISTORY, EXPLORER, PROGRAMS,
    RECORDS, REPLAY, TOURNAMENT, PROFILE, RESEARCH, CALIBRATION, SETTINGS, JOURNAL,
}

sealed interface OverdextopAction {
    data class OpenProgram(val command: InstrumentCommand) : OverdextopAction
    data object OpenExplorer : OverdextopAction
    data object OpenPrograms : OverdextopAction
    data object OpenSettings : OverdextopAction
    data object OpenShortcuts : OverdextopAction
    data class ChooseHomeStyle(val style: HomeScreenStyle) : OverdextopAction
    data class ChangeShortcutLocation(
        val destinationId: String,
        val location: LauncherShortcutLocation,
        val visible: Boolean,
    ) : OverdextopAction
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
    private val fieldNotes = program("field_notes", "field notes", "Read field notes and find the Pokémon in your collection.", OverdextopIcon.JOURNAL, InstrumentCommand.OpenFieldNotes)
    private val explorer = OverdextopEntry("explorer", "explorer", "Browse the complete directory tree.", OverdextopIcon.EXPLORER, OverdextopAction.OpenExplorer)
    private val settings = OverdextopEntry("settings", "settings", "Choose the home screen and where program shortcuts appear.", OverdextopIcon.SETTINGS, OverdextopAction.OpenSettings)
    private val programsEntry = OverdextopEntry("programs", "programs", "Open your programs and settings.", OverdextopIcon.PROGRAMS, OverdextopAction.OpenPrograms)
    private val defaultDesktopIds = setOf("droidball", "pokedex", "current_team", "binders", "match_history", "explorer", "field_notes")

    val desktop: List<OverdextopEntry> get() = desktopEntries(LauncherShortcutConfiguration())
    val programs: List<OverdextopEntry> get() = programEntries(LauncherShortcutConfiguration())

    /** Only existing, argument-free launcher destinations; no contextual detail routes. */
    val destinations: List<OverdextopEntry> = listOf(
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
        explorer, fieldNotes, settings,
    )

    val settingsEntries: List<OverdextopEntry> = listOf(
        OverdextopEntry("home_desktop", "desktop", "Use desktop shortcuts and the programs menu at home.", OverdextopIcon.PROGRAMS, OverdextopAction.ChooseHomeStyle(HomeScreenStyle.OVERDEXTOP)),
        OverdextopEntry("home_directory", "directory tree", "Use the directory tree as the home screen.", OverdextopIcon.EXPLORER, OverdextopAction.ChooseHomeStyle(HomeScreenStyle.DIRECTORY)),
        OverdextopEntry("shortcuts", "shortcuts", "Choose which programs appear on the desktop, in programs, in both, or in neither.", OverdextopIcon.PROGRAMS, OverdextopAction.OpenShortcuts),
    )

    fun defaultVisibility(id: String): LauncherShortcutVisibility = LauncherShortcutVisibility(
        desktop = id in defaultDesktopIds,
        programs = destinations.any { it.id == id },
    )

    fun desktopEntries(configuration: LauncherShortcutConfiguration): List<OverdextopEntry> =
        destinations.filter { configuration.visibility(it.id).desktop } + programsEntry

    fun programEntries(configuration: LauncherShortcutConfiguration): List<OverdextopEntry> =
        destinations.filter { configuration.visibility(it.id).programs }

    fun entries(
        surface: OverdextopSurface,
        configuration: LauncherShortcutConfiguration = LauncherShortcutConfiguration(),
        shortcutLocation: LauncherShortcutLocation = LauncherShortcutLocation.DESKTOP,
    ): List<OverdextopEntry> = when (surface) {
        OverdextopSurface.DESKTOP -> desktopEntries(configuration)
        OverdextopSurface.PROGRAMS -> programEntries(configuration)
        OverdextopSurface.SETTINGS -> settingsEntries
        OverdextopSurface.SHORTCUTS -> destinations.map { destination ->
            val visibility = configuration.visibility(destination.id)
            val current = when (shortcutLocation) {
                LauncherShortcutLocation.DESKTOP -> visibility.desktop
                LauncherShortcutLocation.PROGRAMS -> visibility.programs
            }
            destination.copy(action = OverdextopAction.ChangeShortcutLocation(destination.id, shortcutLocation, !current))
        }
    }
}

/** Selection and menu navigation only; launching or saving remains the root's job. */
@Stable
class OverdextopController(initialShortcuts: LauncherShortcutConfiguration = LauncherShortcutConfiguration()) {
    var surface by mutableStateOf(OverdextopSurface.DESKTOP)
        private set
    private var selectedId by mutableStateOf("droidball")
    var shortcuts by mutableStateOf(initialShortcuts.normalized())
        private set
    var selectedShortcutLocation by mutableStateOf(LauncherShortcutLocation.DESKTOP)
        private set
    private val selections = mutableMapOf<OverdextopSurface, String>()
    private var settingsParent = OverdextopSurface.PROGRAMS
    private var programsReturnId = "binders"

    val entries: List<OverdextopEntry> get() = OverdextopCatalog.entries(surface, shortcuts, selectedShortcutLocation)
    val selectedIndex: Int get() = entries.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selectedEntry: OverdextopEntry get() = entries[selectedIndex]
    val selectedPointerTargetId: String get() = if (surface == OverdextopSurface.SHORTCUTS)
        shortcutTargetId(selectedEntry.id, selectedShortcutLocation) else selectedEntry.id

    fun shortcutVisibility(id: String): LauncherShortcutVisibility = shortcuts.visibility(id)

    fun shortcutLocationEditable(id: String, location: LauncherShortcutLocation): Boolean =
        OverdextopCatalog.destinations.any { it.id == id } && !(id == "settings" && location == LauncherShortcutLocation.PROGRAMS)

    fun shortcutTargetId(id: String, location: LauncherShortcutLocation): String =
        "shortcut:$id:${location.name.lowercase()}"

    fun selectShortcutLocation(location: LauncherShortcutLocation) { selectedShortcutLocation = location }

    /** Apply only after a successful save; keep selections by destination identity. */
    fun replaceShortcuts(configuration: LauncherShortcutConfiguration) {
        val oldEntries = OverdextopSurface.entries.associateWith { OverdextopCatalog.entries(it, shortcuts, selectedShortcutLocation) }
        val currentSelection = selectedEntry.id
        shortcuts = configuration.normalized()
        for (destinationSurface in OverdextopSurface.entries) {
            val oldId = if (destinationSurface == surface) currentSelection else selections[destinationSurface]
            val previous = oldEntries.getValue(destinationSurface)
            val next = OverdextopCatalog.entries(destinationSurface, shortcuts, selectedShortcutLocation)
            val restored = next.firstOrNull { it.id == oldId }?.id
                ?: next[previous.indexOfFirst { it.id == oldId }.coerceIn(0, next.lastIndex)].id
            selections[destinationSurface] = restored
            if (destinationSurface == surface) selectedId = restored
        }
    }

    fun select(id: String): Boolean {
        var destinationId = id
        var location: LauncherShortcutLocation? = null
        if (surface == OverdextopSurface.SHORTCUTS && id.startsWith("shortcut:")) {
            val pieces = id.split(':')
            if (pieces.size != 3) return false
            destinationId = pieces[1]
            location = LauncherShortcutLocation.entries.firstOrNull { it.name.equals(pieces[2], true) } ?: return false
        }
        val index = entries.indexOfFirst { it.id == destinationId }
        if (index < 0) return false
        val pinCount = entries.lastIndex
        if (surface == OverdextopSurface.DESKTOP && index == pinCount && selectedIndex < pinCount) {
            programsReturnId = selectedEntry.id
        }
        selectedId = destinationId
        if (location != null) selectedShortcutLocation = location
        return true
    }

    fun move(direction: OverdextopDirection) {
        if (surface == OverdextopSurface.SHORTCUTS && direction in setOf(OverdextopDirection.LEFT, OverdextopDirection.RIGHT)) {
            selectedShortcutLocation = if (direction == OverdextopDirection.LEFT) LauncherShortcutLocation.DESKTOP else LauncherShortcutLocation.PROGRAMS
            return
        }
        val current = selectedIndex
        val nextIndex = if (surface == OverdextopSurface.DESKTOP) {
            val pinCount = entries.lastIndex
            val lastRowStart = ((pinCount - 1).coerceAtLeast(0) / 3) * 3
            when (direction) {
                OverdextopDirection.UP -> when {
                    pinCount == 0 -> current
                    current == pinCount -> entries.indexOfFirst { it.id == programsReturnId }
                        .takeIf { it in lastRowStart until pinCount } ?: lastRowStart
                    current >= 3 -> current - 3
                    else -> current
                }
                OverdextopDirection.DOWN -> when {
                    current == pinCount -> current
                    current / 3 < lastRowStart / 3 -> (current + 3).coerceAtMost(pinCount - 1)
                    else -> { programsReturnId = selectedEntry.id; pinCount }
                }
                OverdextopDirection.LEFT -> if (current < pinCount && current % 3 > 0) current - 1 else current
                OverdextopDirection.RIGHT -> if (current < pinCount && current % 3 < 2 && current + 1 < pinCount) current + 1 else current
            }
        } else {
            when (direction) {
                OverdextopDirection.UP -> (current - 1).coerceAtLeast(0)
                OverdextopDirection.DOWN -> (current + 1).coerceAtMost(entries.lastIndex)
                OverdextopDirection.LEFT, OverdextopDirection.RIGHT -> current
            }
        }
        selectedId = entries[nextIndex].id
    }

    fun confirm(): OverdextopAction? {
        if (surface == OverdextopSurface.SHORTCUTS && !shortcutLocationEditable(selectedEntry.id, selectedShortcutLocation)) return null
        return when (val action = selectedEntry.action) {
            OverdextopAction.OpenPrograms -> { openPrograms(); null }
            OverdextopAction.OpenSettings -> { openSettings(); null }
            OverdextopAction.OpenShortcuts -> { openShortcuts(); null }
            else -> action
        }
    }

    fun openPrograms() = show(OverdextopSurface.PROGRAMS)

    fun openSettings() {
        if (surface != OverdextopSurface.SETTINGS && surface != OverdextopSurface.SHORTCUTS) settingsParent = surface
        show(OverdextopSurface.SETTINGS)
    }

    fun openShortcuts() = show(OverdextopSurface.SHORTCUTS)

    fun home() = show(OverdextopSurface.DESKTOP)

    fun back(): Boolean = when (surface) {
        OverdextopSurface.DESKTOP -> false
        OverdextopSurface.PROGRAMS -> { home(); true }
        OverdextopSurface.SETTINGS -> { show(settingsParent); true }
        OverdextopSurface.SHORTCUTS -> { show(OverdextopSurface.SETTINGS); true }
    }

    private fun show(next: OverdextopSurface) {
        if (next == surface) return
        selections[surface] = selectedEntry.id
        surface = next
        selectedId = entries.firstOrNull { it.id == selections[next] }?.id ?: entries.first().id
    }
}
