package com.example.overdex.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.PublicIdentitySerializer
import com.example.overdex.data.QrEncoder
import com.example.overdex.data.TrainerRepository
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun QrIdentityScreen(
    trainerIdentity: TrainerIdentity?,
    trainerRepository: TrainerRepository,
    onBack: () -> Unit
) {
    val qrBitmap = remember(trainerIdentity) {
        trainerIdentity?.let {
            val publicIdentity = trainerRepository.exportPublicIdentity()
            val jsonString = PublicIdentitySerializer.serialize(publicIdentity)
            QrEncoder.encode(jsonString)
        }
    }

    ODXFiShell(
        onB = onBack
    ) { _ ->
        TerminalScreen {
            TerminalPathIndicator(path = "/trainer/profile/share")
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TerminalText(
                    text = trainerIdentity?.displayName?.uppercase() ?: "UNNAMED TRAINER",
                    fontSize = 24.sp,
                    color = TerminalPurple
                )
                
                TerminalText(
                    text = "OVERDEX TRAINER",
                    fontSize = 12.sp,
                    color = TerminalDimGreen
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code Container
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Trainer QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        TerminalText(text = "GENERATING QR...", color = Color.Black)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TerminalText(text = "TRAINER ID", color = TerminalDimGreen, fontSize = 10.sp)
                TerminalText(
                    text = trainerIdentity?.trainerId?.toString() ?: "UNKNOWN",
                    fontSize = 14.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TerminalText(text = "PROTOCOL", color = TerminalDimGreen, fontSize = 10.sp)
                TerminalText(
                    text = "VERSION 1",
                    fontSize = 14.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TerminalButton(
                    text = "back",
                    onClick = onBack,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
