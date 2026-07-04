package com.example.overdex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    centerContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Up Button
        IconButton(onClick = onUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left Button
            IconButton(onClick = onLeft) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Left")
            }
            
            // Center element (optional)
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                centerContent?.invoke()
            }
            
            // Right Button
            IconButton(onClick = onRight) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Right")
            }
        }
        
        // Down Button
        IconButton(onClick = onDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Down")
        }
    }
}
