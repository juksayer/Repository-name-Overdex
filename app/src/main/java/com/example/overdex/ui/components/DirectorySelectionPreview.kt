package com.example.overdex.ui.components

import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.TreeNode
import java.util.Locale

/** Read-only context for the selected directory row, independent of capture or editor state. */
internal data class DirectorySelectionPreview(
    val title: String,
    val description: String,
    val entries: List<DirectoryPreviewEntry> = emptyList(),
    val controls: List<String>,
)

internal data class DirectoryPreviewEntry(val label: String, val isFolder: Boolean)

internal fun directorySelectionPreview(
    visibleNodes: List<FlattenedNode>,
    selectedPath: String,
): DirectorySelectionPreview? {
    val selected = visibleNodes.firstOrNull { it.path == selectedPath } ?: return null
    val title = directoryNodeLabel(selected)
    return when (val node = selected.node) {
        is ActionNode -> workflowPreview(node, title)
        is DirectoryNode -> {
            val onlyWorkflow = node.singleWorkflow()
            DirectorySelectionPreview(
                title = title,
                description = when {
                    node.children.isEmpty() -> "This folder has no entries yet."
                    onlyWorkflow != null -> workflowPreview(onlyWorkflow, title).description
                    else -> "${node.children.size} entries"
                },
                // Read the real tree: a collapsed folder has no projected descendants.
                entries = if (onlyWorkflow != null) emptyList() else node.children.map { child ->
                    DirectoryPreviewEntry(
                        label = directoryNodeLabel(FlattenedNode(child, selected.depth + 1,
                            "${selected.path}/${child.name}", false)),
                        isFolder = child is DirectoryNode,
                    )
                },
                controls = if (node.children.isEmpty()) listOf("Left return")
                    else listOf("Right open / enter · Left collapse / return"),
            )
        }
    }
}

/** A chain of one-child folders still represents one workflow. */
private fun TreeNode.singleWorkflow(): ActionNode? = when (this) {
    is ActionNode -> this
    is DirectoryNode -> children.singleOrNull()?.singleWorkflow()
}

private fun workflowPreview(node: ActionNode, title: String): DirectorySelectionPreview {
    val (purpose, controls) = when (node.command) {
        InstrumentCommand.LaunchDroidball ->
            "Prepare Droidball to follow you into Pokémon GO and record battles." to
                listOf("A readiness · Hold A calibration")
        InstrumentCommand.OpenSearch ->
            "Browse Pokémon species, types, moves, and their reference cards." to
                listOf("A open binder", "In binder: arrows browse · Select search", "A details · B return")
        InstrumentCommand.OpenCurrentTeam ->
            "Review your current team and edit its members or moves. Move changes keep each Pokémon’s battle history." to
                listOf("A open", "Inside: Up / Down choose · A edit · B return")
        InstrumentCommand.OpenCollection, is InstrumentCommand.OpenOwnedBinder ->
            "Browse your owned Pokémon cards. The same Pokémon can appear in several binders without being copied." to
                listOf("A open binder", "In binder: arrows browse · Select search", "A details · B return")
        InstrumentCommand.ManageBinders ->
            "Create named binders and choose which owned Pokémon appear in them using saved rules." to
                listOf("A open", "Inside: Start (+) new · Select edit custom binder", "Up / Down choose · A open · B return")
        InstrumentCommand.OpenBattleHistory ->
            "Browse recorded matches, newest first. Open a match’s summary, then its timeline or replay." to
                listOf("A open", "Inside: Up / Down choose · A summary · B return")
        InstrumentCommand.OpenTournament ->
            "Host or join a private single-elimination tournament. Prepare with your opponent, then confirm each battle’s result." to
                listOf("A open", "Inside: Up / Down choose · Left / Right action", "A confirm · B return")
        InstrumentCommand.OpenBattleRecords ->
            "Find recorded battle events by Pokémon, move, opponent, and other filters." to
                listOf("A open", "In results: Up / Down choose · A replay", "Start filters · Select change event · B return")
        InstrumentCommand.OpenReplayEditor ->
            "Arrange saved replay clips into compilations and preview them." to
                listOf("A open", "Inside: Up / Down item · Left / Right action", "A perform action · B return")
        InstrumentCommand.OpenSignalObservatory ->
            "Review observation tools, open Match Calibration, and adjust the CRT’s visual settings." to
                listOf("A open", "Inside: Up / Down choose · A launch / toggle", "Left / Right adjust sliders · B return")
        InstrumentCommand.OpenProfile ->
            "Edit your trainer name and friend code, or show and scan a trainer QR code." to
                listOf("A open", "Inside: Up / Down choose · A edit / open · B return")
        InstrumentCommand.OpenDirectoryCalibration ->
            "Review captured battle crops and adjust their positions in the directory LCD." to
                listOf("A review crops · B return")
        InstrumentCommand.OpenHomeSettings ->
            "Choose desktop or directory as your home screen. Both open the same programs and saved Pokémon." to
                listOf("A open", "Up / Down choose · A save · B return")
        InstrumentCommand.OpenMatchCalibration ->
            "Choose a battle reference image and adjust the saved crop boxes for your device." to
                listOf("A open", "Inside: A next crop · Select move / resize", "Arrows adjust · Start profiles · B return")
        InstrumentCommand.AddSpecimen ->
            "Add an individual Pokémon to your owned collection." to
                listOf("A open", "Inside: Up / Down choose · A activate · B return")
        is InstrumentCommand.EditNewOwnedPokemon ->
            "Set ${node.name.lowercase(Locale.ROOT)} on the Pokémon card you are adding." to listOf("A edit · B return")
        else -> (node.detail?.takeIf { it.isNotBlank() } ?: "Open $title.") to
            listOf("A open · B return")
    }
    return DirectorySelectionPreview(title, purpose, controls = controls)
}
