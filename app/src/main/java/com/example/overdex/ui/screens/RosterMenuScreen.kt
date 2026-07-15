package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.overdex.ui.components.*

@Preview
@Composable
fun RosterMenuScreenPreview() {
    RosterMenuScreen(
        selectedIndex = 0,
        onNavigate = {},
        onBack = {}
    )
}

@Composable
fun RosterMenuScreen(
    selectedIndex: Int,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val options = remember {
        listOf(
            "specimens",
            "registration assistance",
            "register specimen",
            "teams",
            "leagues",
            "trained teammates",
            "untrained teammates"
        )
    }

    TerminalScreen {
        TerminalHeader(text = "roster")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalSection(title = "navigation") {
            options.forEachIndexed { index, label ->
                TerminalMenuOption(
                    label = label,
                    selected = selectedIndex == index,
                    onClick = { 
                        val route = when (index) {
                            0 -> "collection"
                            1 -> "add_pokemon_wizard"
                            2 -> "roster/register_specimen"
                            3 -> "roster/teams"
                            4 -> "roster/leagues"
                            5 -> "roster/trained_teammates"
                            6 -> "roster/untrained_teammates"
                            else -> "roster_menu"
                        }
                        onNavigate(route)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        TerminalButton(
            text = "back",
            onClick = onBack,
            selected = false
        )
    }
}
