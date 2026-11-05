package com.example.overdex.model.navigation

import androidx.compose.runtime.Stable

sealed interface TreeNode {
    val name: String
    val icon: TreeNodeIcon?
}

enum class TreeNodeIcon {
    BINDER,
}

data class DirectoryNode(
    override val name: String,
    val children: List<TreeNode>,
    override val icon: TreeNodeIcon? = null,
) : TreeNode

data class ActionNode(
    override val name: String,
    val command: InstrumentCommand,
    override val icon: TreeNodeIcon? = null,
    val detail: String? = null,
) : TreeNode

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

    /** Refresh a library without collapsing the user's current directory. */
    fun replaceChildren(path: String, children: List<TreeNode>) {
        fun replace(nodes: List<TreeNode>, parent: String): List<TreeNode> = nodes.map { node ->
            val nodePath = "$parent/${node.name}"
            if (node is DirectoryNode) {
                if (nodePath == path) node.copy(children = children)
                else node.copy(children = replace(node.children, nodePath))
            } else node
        }
        rootNodes = replace(rootNodes, "")
        if (project().none { it.path == selectedPath }) selectedPath = path
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
                openOrEnterSelected()
                null
            }
            is ActionNode -> {
                node.command
            }
        }
    }

    /** Selects the tapped visible row, then applies the same action as the A button. */
    fun executePath(path: String): InstrumentCommand? {
        if (project().none { it.path == path }) return null
        selectedPath = path
        return executeSelected()
    }

    /** Right opens a folder, or enters its first child when it is already open. */
    fun openOrEnterSelected(): Boolean {
        val selected = project().find { it.path == selectedPath } ?: return false
        val folder = selected.node as? DirectoryNode ?: return false
        val firstChild = folder.children.firstOrNull() ?: return false
        if (expandedPaths.add(selected.path)) return true
        selectedPath = "${selected.path}/${firstChild.name}"
        return true
    }

    /**
     * Left closes the current open folder. Returning to a parent also clears all
     * child folders at the level being left, so hidden sibling branches do not
     * unexpectedly reappear the next time that parent is entered.
     */
    fun collapseOrParent(): Boolean {
        val projection = project()
        val selectedIndex = projection.indexOfFirst { it.path == selectedPath }
        val selected = projection.getOrNull(selectedIndex) ?: return false
        if (selected.node is DirectoryNode && selected.isExpanded) {
            clearFolderExpansion(selected.node, selected.path)
            return true
        }

        if (selected.depth > 0) {
            // Names may contain slashes: depth and preorder define the actual
            // parent, while path strings remain opaque existing identifiers.
            val parent = projection.take(selectedIndex).lastOrNull { it.depth == selected.depth - 1 }
                ?: return false
            val folder = parent.node as? DirectoryNode ?: return false
            folder.children.filterIsInstance<DirectoryNode>().forEach { child ->
                clearFolderExpansion(child, "${parent.path}/${child.name}")
            }
            selectedPath = parent.path
            return true
        }

        val firstRoot = projection.firstOrNull()?.path ?: return false
        if (selectedPath == firstRoot) return false
        expandedPaths.clear()
        selectedPath = firstRoot
        return true
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

    /** Finish an inline editor without losing the surrounding directory's place. */
    fun collapse(path: String) {
        expandedPaths.removeAll { it == path || it.startsWith("$path/") }
        if (selectedPath == path || selectedPath.startsWith("$path/")) selectedPath = path
    }

    /** Exact actual descendants, not path prefixes that could also match a slash in a name. */
    private fun clearFolderExpansion(folder: DirectoryNode, path: String) {
        expandedPaths.remove(path)
        folder.children.filterIsInstance<DirectoryNode>().forEach { child ->
            clearFolderExpansion(child, "$path/${child.name}")
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
