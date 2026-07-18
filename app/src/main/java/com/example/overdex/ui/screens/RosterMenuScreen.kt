package com.example.overdex.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.overdex.ui.components.*
import com.example.overdex.model.navigation.*

@Preview
@Composable
fun DirectoryScreenPreview() {
    DirectoryScreen(
        path = "/specimens/",
        selectedIndex = 0,
        nodes = listOf(
            DirectoryNode("search", NodeKind.DIRECTORY),
            DirectoryNode("collection", NodeKind.DIRECTORY),
            DirectoryNode("register", NodeKind.ACTION)
        )
    )
}

@Composable
fun DirectoryScreen(
    path: String,
    selectedIndex: Int,
    nodes: List<DirectoryNode>
) {
    TerminalScreen {
        DirectoryWorkspace(
            path = path,
            nodes = nodes,
            selectedIndex = selectedIndex,
            onNodeSelected = { node ->
                node.action?.invoke()
            }
        )
    }
}
