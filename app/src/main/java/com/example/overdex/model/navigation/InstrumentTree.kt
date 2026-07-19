package com.example.overdex.model.navigation

import androidx.compose.runtime.Stable

sealed interface TreeNode {
    val name: String
}

data class DirectoryNode(
    override val name: String,
    val children: List<TreeNode>
) : TreeNode

data class ActionNode(
    override val name: String,
    val command: InstrumentCommand
) : TreeNode

sealed interface InstrumentCommand {
    data object OpenSearch : InstrumentCommand
    data object OpenCollection : InstrumentCommand
    data object AddSpecimen : InstrumentCommand
    data object OpenBattleHistory : InstrumentCommand
    data object OpenBattleLogs : InstrumentCommand
    data object OpenCapture : InstrumentCommand
    data object OpenCalibration : InstrumentCommand
    data object OpenProfile : InstrumentCommand
    data object OpenTimeline : InstrumentCommand
    data object OpenChat : InstrumentCommand
    data object OpenReadme : InstrumentCommand
    data object OpenAccessibilityProbe : InstrumentCommand
}

@Stable
data class FlattenedNode(
    val node: TreeNode,
    val depth: Int,
    val path: String,
    val isExpanded: Boolean
)

data class TreeState(
    val rootNodes: List<TreeNode> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val selectedPath: String = "",
    val visibleNodes: List<FlattenedNode> = emptyList(),
    val scrollOffset: Int = 0
)

class InstrumentTree(initialNodes: List<TreeNode>) {
    private var rootNodes = initialNodes
    private var expandedPaths = mutableSetOf<String>()
    private var selectedPath = ""
    private var scrollOffset = 0
    private val VIEWPORT_SIZE = 12

    init {
        // Initialize selection to the first node if available
        val projection = project()
        if (projection.isNotEmpty()) {
            selectedPath = projection.first().path
        }
        updateViewport()
    }

    fun getState(): TreeState {
        val projection = project()
        return TreeState(
            rootNodes = rootNodes,
            expandedPaths = expandedPaths.toSet(),
            selectedPath = selectedPath,
            visibleNodes = projection,
            scrollOffset = scrollOffset
        )
    }

    fun moveSelection(delta: Int) {
        val projection = project()
        val currentIndex = projection.indexOfFirst { it.path == selectedPath }
        if (currentIndex != -1) {
            val newIndex = (currentIndex + delta).coerceIn(0, projection.size - 1)
            selectedPath = projection[newIndex].path
            updateViewport()
        }
    }

    fun executeSelected(): InstrumentCommand? {
        val projection = project()
        val flattened = projection.find { it.path == selectedPath } ?: return null
        
        val command = when (val node = flattened.node) {
            is DirectoryNode -> {
                toggle(flattened.path)
                null
            }
            is ActionNode -> {
                node.command
            }
        }
        updateViewport()
        return command
    }

    fun navigateBack(): Boolean {
        val projection = project()
        val flattened = projection.find { it.path == selectedPath } ?: return false

        val changed = if (flattened.node is DirectoryNode && expandedPaths.contains(flattened.path)) {
            expandedPaths.remove(flattened.path)
            true
        } else {
            val parentPath = flattened.path.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                if (expandedPaths.contains(parentPath)) {
                    expandedPaths.remove(parentPath)
                }
                selectedPath = parentPath
                true
            } else {
                false
            }
        }
        
        if (changed) {
            updateViewport()
        }
        return changed
    }

    private fun updateViewport() {
        val projection = project()
        val selectedIndex = projection.indexOfFirst { it.path == selectedPath }
        if (selectedIndex == -1) return

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
        } else if (selectedIndex >= scrollOffset + VIEWPORT_SIZE) {
            scrollOffset = selectedIndex - VIEWPORT_SIZE + 1
        }
        
        // Final bounds check
        scrollOffset = scrollOffset.coerceIn(0, (projection.size - VIEWPORT_SIZE).coerceAtLeast(0))
    }

    private fun toggle(path: String) {
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path)
        } else {
            expandedPaths.add(path)
        }
    }

    private fun project(): List<FlattenedNode> {
        val result = mutableListOf<FlattenedNode>()
        fun walk(nodes: List<TreeNode>, depth: Int, parentPath: String) {
            nodes.forEach { node ->
                val path = if (parentPath == "/") "/${node.name}" else "$parentPath/${node.name}"
                val isExpanded = expandedPaths.contains(path)
                result.add(FlattenedNode(node, depth, path, isExpanded))
                if (isExpanded && node is DirectoryNode) {
                    walk(node.children, depth + 1, path)
                }
            }
        }
        walk(rootNodes, 0, "/")
        return result
    }
}
