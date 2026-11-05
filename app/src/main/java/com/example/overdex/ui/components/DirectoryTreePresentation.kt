package com.example.overdex.ui.components

import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.InstrumentCommand
import java.util.Locale

/** Display-only rails and labels; navigation keeps the original nodes and paths. */
internal data class DirectoryRowPresentation(
    val row: FlattenedNode,
    val label: String,
    val continuingAncestors: List<Boolean>,
    val hasFollowingSibling: Boolean,
    val isActiveAncestor: Boolean,
)

internal fun directoryRows(
    visibleNodes: List<FlattenedNode>,
    selectedPath: String,
): List<DirectoryRowPresentation> {
    val followingSibling = visibleNodes.mapIndexed { index, row ->
        val next = visibleNodes.asSequence().drop(index + 1).firstOrNull { it.depth <= row.depth }
        next?.depth == row.depth
    }
    // Names may contain '/', and inline choices add a nonvisual /choice/
    // segment. Recover ancestors from the visible preorder, never path syntax.
    val activeAncestors = mutableSetOf<Int>()
    val selectedIndex = visibleNodes.indexOfFirst { it.path == selectedPath }
    if (selectedIndex >= 0) {
        var depth = visibleNodes[selectedIndex].depth
        for (index in selectedIndex - 1 downTo 0) {
            if (visibleNodes[index].depth < depth) {
                activeAncestors += index
                depth = visibleNodes[index].depth
            }
        }
    }
    val ancestors = mutableListOf<Int>()
    return visibleNodes.mapIndexed { index, row ->
        while (ancestors.isNotEmpty() && visibleNodes[ancestors.last()].depth >= row.depth) {
            ancestors.removeAt(ancestors.lastIndex)
        }
        // Synthetic species/move choices have an extra /choice/ path segment.
        // The supplied display depth, not path segment count, owns indentation.
        val continuation = List((row.depth - 1).coerceAtLeast(0)) { level ->
            ancestors.firstOrNull { visibleNodes[it].depth == level + 1 }
                ?.let { followingSibling[it] } == true
        }
        DirectoryRowPresentation(
            row = row,
            label = directoryNodeLabel(row),
            continuingAncestors = continuation,
            hasFollowingSibling = followingSibling[index],
            isActiveAncestor = index in activeAncestors,
        ).also { ancestors += index }
    }
}

internal fun directoryNodeLabel(row: FlattenedNode): String {
    val node = row.node
    if (node is ActionNode) {
        val binder = node.command as? InstrumentCommand.OpenOwnedBinder
        if (binder != null && !binder.binder.isBuiltIn) return node.name
        if (node.command is InstrumentCommand.EditNewOwnedPokemon && "/choice/" in row.path) return node.name
    }
    return node.name.lowercase(Locale.ROOT)
        .replace(Regex("\\bdroidball\\b"), "Droidball")
        .replace(Regex("\\bpok[eé]mon\\b"), "Pokémon")
}
