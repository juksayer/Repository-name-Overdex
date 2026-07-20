package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.presentation.*
import com.example.overdex.presentation.preview.BattlePreviewData
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen

/**
 * BattlePreviewScreen serves as a workshop for the Battle HUD design.
 * It establishes the canonical information layout for the ODX-FI and Droidball HUD.
 */
@Composable
fun BattlePreviewScreen(
    state: PresentationState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1. Enemy Detail Panel
        EnemyDetailPanel(state.team.opponent)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Move Analysis Panel
        MoveAnalysisPanel(state.team.opponent.knownMoves)

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Status Panel
        StatusPanel(state.observation)

        Spacer(modifier = Modifier.weight(1f))

        // 4. Tactical Recommendation Panel
        TacticalRecommendationPanel(state.tactical)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TerminalText(
            text = "PRESS [B] TO RETURN",
            color = TerminalDimGreen,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Complete Battle")
@Composable
fun BattlePreviewPreview() {
    BattlePreviewScreen(state = BattlePreviewData.mewtwoDemo())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Missing Moves")
@Composable
fun MissingMovesPreview() {
    BattlePreviewScreen(state = BattlePreviewData.missingMovesDemo())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Shadow Pokemon")
@Composable
fun ShadowPokemonPreview() {
    BattlePreviewScreen(state = BattlePreviewData.shadowDemo())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Fainted Opponent")
@Composable
fun FaintedOpponentPreview() {
    BattlePreviewScreen(state = BattlePreviewData.faintedDemo())
}
