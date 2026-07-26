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
        data object OpenAccessibilityProbe : InstrumentCommand
    data object OpenSignalObservatory : InstrumentCommand
    data object OpenBattlePreview : InstrumentCommand
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
    val visibleNodes: List<FlattenedNode> = emptyList()
)

class InstrumentTree(initialNodes: List<TreeNode>) {
    private var rootNodes = initialNodes
    private var expandedPaths = mutableSetOf<String>()
    private var selectedPath = ""

    init {
        // Initialize selection to the first node if available
        val projection = project()
        if (projection.isNotEmpty()) {
            selectedPath = projection.first().path
        }
    }

    fun getState(): TreeState {
        val projection = project()
        return TreeState(
            rootNodes = rootNodes,
            expandedPaths = expandedPaths.toSet(),
            selectedPath = selectedPath,
            visibleNodes = projection
        )
    }

    fun moveSelection(delta: Int) {
        val projection = project()
        var currentIndex = projection.indexOfFirst { it.path == selectedPath }
        
        // If the selected node is hidden (e.g. parent collapsed), 
        // find the nearest visible ancestor.
        if (currentIndex == -1) {
            var tempPath = selectedPath
            while (tempPath.contains("/")) {
                tempPath = tempPath.substringBeforeLast("/")
                currentIndex = projection.indexOfFirst { it.path == tempPath }
                if (currentIndex != -1) {
                    selectedPath = tempPath
                    break
                }
            }
        }

        if (currentIndex != -1) {
            val newIndex = (currentIndex + delta).coerceIn(0, projection.size - 1)
            selectedPath = projection[newIndex].path
        }
    }

    fun executeSelected(): InstrumentCommand? {
        val projection = project()
        val flattened = projection.find { it.path == selectedPath } ?: return null
        
        return when (val node = flattened.node) {
            is DirectoryNode -> {
                toggle(flattened.path)
                null
            }
            is ActionNode -> {
                node.command
            }
        }
    }

    fun navigateBack(): Boolean {
        val projection = project()
        val flattened = projection.find { it.path == selectedPath } ?: return false

        val parentPath = flattened.path.substringBeforeLast("/", "")
        return if (parentPath.isNotEmpty()) {
            selectedPath = parentPath
            true
        } else {
            false
        }
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
