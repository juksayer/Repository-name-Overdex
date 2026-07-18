package com.example.overdex.model.navigation

enum class NodeKind {
    DIRECTORY,
    ACTION
}

/**
 * Represents an entry in the ODX-FI directory hierarchy.
 */
data class DirectoryNode(
    val name: String,
    val kind: NodeKind,
    val route: String? = null,
    val action: (() -> Unit)? = null
) {
    /**
     * Returns the display name with a trailing slash if it's a directory.
     */
    val displayName: String
        get() = if (kind == NodeKind.DIRECTORY) "$name/" else name
}
