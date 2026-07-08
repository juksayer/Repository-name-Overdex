package com.example.overdex.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen

@Composable
fun CaptureVerificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { CaptureTemplateManager(context) }
    
    var currentTemplate by remember { mutableStateOf(manager.getSummaryTemplate()) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    
    var captureLibrary by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            captureLibrary = uris
            currentIndex = 0
        }
    }

    PokedexFrame(
        onUp = { /* Reserved */ },
        onDown = { /* Reserved */ },
        onLeft = {
            if (currentIndex > 0) currentIndex--
        },
        onRight = {
            if (currentIndex < captureLibrary.size - 1) currentIndex++
        },
        onSelect = { isOverlayVisible = !isOverlayVisible },
        onStart = {
            currentTemplate = if (currentTemplate.name == "PokemonGoSummaryTemplate") {
                manager.getMovesTemplate()
            } else {
                manager.getSummaryTemplate()
            }
        },
        onA = {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onB = onBack
    ) {
        TerminalScreen {
            TerminalHeader(text = "capture verification")
            
            TerminalText(text = "template : ${currentTemplate.name.removePrefix("PokemonGo").replace("Template", "").uppercase()}")
            
            val imageCounter = if (captureLibrary.isEmpty()) "EMPTY" else "${(currentIndex + 1).toString().padStart(3, '0')} / ${captureLibrary.size.toString().padStart(3, '0')}"
            TerminalText(text = "image    : $imageCounter")
            
            TerminalText(text = "overlay  : ${if (isOverlayVisible) "ON" else "OFF"}")

            Spacer(modifier = Modifier.height(16.dp))

            // Main Viewport for Screenshot + Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TerminalBlack),
                contentAlignment = Alignment.Center
            ) {
                if (captureLibrary.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = captureLibrary[currentIndex],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
                        CaptureTemplateOverlay(
                            template = currentTemplate,
                            isVisible = isOverlayVisible
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        TerminalText(text = "NO MEDIA LOADED", color = TerminalDimGreen)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TerminalButton(text = "load capture", onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TerminalButton(text = "back", onClick = onBack)
        }
    }
}
