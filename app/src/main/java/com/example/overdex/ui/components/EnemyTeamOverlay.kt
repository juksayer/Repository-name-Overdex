package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.data.GithubSpriteProvider
import com.example.overdex.data.SpriteProvider
import com.example.overdex.presentation.*

/**
 * A horizontal row of enemy Pokémon sprites, serving as a persistent memory of the opponent's team.
 * Refactored to consume semantic [PresentationState] models.
 */
@Composable
fun EnemyTeamMemoryOverlay(
    opponent: OpponentTeamPresentation,
    tactical: TacticalPresentation? = null,
    spriteProvider: SpriteProvider = GithubSpriteProvider()
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Strategic Indicator at the start
        if (tactical != null) {
            DecisionIcon(tactical)
        }
        
        // Enemy Pokémon Blocks
        opponent.members.forEach { member ->
            EnemyPokemonBlock(member, spriteProvider)
        }
    }
}

@Composable
fun DecisionIcon(tactical: TacticalPresentation) {
    val icon = when (tactical.primaryGuidance) {
        TacticalAction.SWITCH_NOW -> Icons.Default.Sync
        TacticalAction.FARM_ENERGY -> Icons.Default.Bolt
        TacticalAction.SHIELD_LIKELY_REQUIRED -> Icons.Default.Shield
        TacticalAction.STAY_AND_FIGHT -> Icons.Default.Check
        else -> Icons.Default.Info
    }
    
    val tint = when (tactical.threat) {
        TacticalAdvantage.HIGH -> Color.Red
        TacticalAdvantage.MEDIUM -> Color.Yellow
        else -> TerminalGreen
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun EnemyPokemonBlock(
    member: EnemyMemberPresentation,
    spriteProvider: SpriteProvider = GithubSpriteProvider()
) {
    // Strategic Resolution: Resolve ID from species name if possible
    // Note: In a future brick, EnemyMemberPresentation should provide speciesId.
    val speciesId = member.speciesId ?: when (member.species.lowercase()) {
        "swampert" -> 260
        "talonflame" -> 663
        "azumarill" -> 184
        else -> 0
    }

    val spriteUrl = spriteProvider.getSpriteUrl(id = speciesId)

    val isFainted = !member.isAlive
    val isActive = member.isActive && !isFainted

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isActive -> TerminalGreen.copy(alpha = 0.15f)
                    isFainted -> Color.DarkGray.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) TerminalGreen else TerminalDimGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val colorFilter = if (isFainted) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }

        AsyncImage(
            model = spriteUrl,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .alpha(if (isFainted) 0.3f else 1.0f),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
        
        // Quiet Energy Bar at bottom
        if (!isFainted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .width(28.dp)
                    .height(2.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(member.energyLevel)
                        .fillMaxHeight()
                        .background(if (isActive) Color.White else TerminalGreen)
                )
            }
        }
    }
}
