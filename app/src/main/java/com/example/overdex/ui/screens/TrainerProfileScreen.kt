package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.TrainerRepository
import com.example.overdex.data.PartnerRepository
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrainerProfileScreen(
    trainerIdentity: TrainerIdentity?,
    partnerIdentity: PartnerIdentity?,
    trainerRepository: TrainerRepository,
    partnerRepository: PartnerRepository,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onViewTimeline: () -> Unit,
    onChat: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    
    val scrollState = rememberScrollState()

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("EDIT DISPLAY NAME", color = TerminalGreen) },
            text = {
                TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TerminalBlack,
                        unfocusedContainerColor = TerminalBlack,
                        focusedTextColor = TerminalGreen,
                        unfocusedTextColor = TerminalGreen
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trainerRepository.updateDisplayName(tempName)
                    showEditDialog = false
                }) {
                    Text("SAVE", color = TerminalGreen)
                }
            },
            containerColor = TerminalBlack
        )
    }

    PokedexFrame(
        onB = onBack,
        onA = {
            tempName = trainerIdentity?.displayName ?: ""
            showEditDialog = true
        }
    ) { _ ->
        TerminalScreen {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                TerminalHeader(text = "trainer profile")
                
                Spacer(modifier = Modifier.height(16.dp))

                // Identity Section
                TerminalSection(title = "identity") {
                    ProfileRow(label = "DISPLAY NAME", value = trainerIdentity?.displayName ?: "UNNAMED TRAINER")
                    ProfileRow(label = "TRAINER ID", value = trainerIdentity?.trainerId?.toString() ?: "UNKNOWN")
                    
                    val dateStr = trainerIdentity?.createdAt?.let {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                            .withZone(ZoneId.systemDefault())
                            .format(it)
                    } ?: "UNKNOWN"
                    
                    ProfileRow(label = "CREATED", value = dateStr)
                    ProfileRow(label = "APP VERSION", value = trainerIdentity?.appVersionWhenCreated ?: "UNKNOWN")
                    ProfileRow(label = "AVATAR SEED", value = trainerIdentity?.avatarSeed?.toString() ?: "0")
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                TerminalButton(
                    text = "edit display name",
                    onClick = {
                        tempName = trainerIdentity?.displayName ?: ""
                        showEditDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TerminalButton(
                    text = "show my qr",
                    onClick = onShowQr
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (partnerIdentity == null) {
                    TerminalButton(
                        text = "scan trainer qr",
                        onClick = onScanQr
                    )
                }

                // Partner Section
                TerminalSection(title = "partner") {
                    if (partnerIdentity != null) {
                        TerminalText(
                            text = "❤️ ${partnerIdentity.displayName?.uppercase() ?: "UNNAMED"}",
                            fontSize = 18.sp,
                            color = TerminalPurple
                        )
                        
                        val dateStr = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ROOT)
                            .withZone(ZoneId.systemDefault())
                            .format(partnerIdentity.linkedAt)
                        
                        TerminalText(text = "LINKED", color = TerminalDimGreen, fontSize = 10.sp)
                        TerminalText(text = dateStr, fontSize = 12.sp)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalButton(
                                text = "view",
                                onClick = onViewTimeline,
                                modifier = Modifier.weight(1f)
                            )
                            TerminalButton(
                                text = "chat",
                                onClick = onChat,
                                modifier = Modifier.weight(1f)
                            )
                            TerminalButton(
                                text = "unlink",
                                onClick = { 
                                    scope.launch { partnerRepository.unlink() }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        TerminalText(text = "STATUS:", color = TerminalDimGreen, fontSize = 10.sp)
                        TerminalText(text = "NOT LINKED", fontSize = 14.sp)
                    }
                }

                // Avatar Section (Placeholder)
                TerminalSection(title = "avatar") {
                    TerminalText(text = "COMING SOON", color = Color.Gray, fontSize = 12.sp)
                }

                // Public Identity Preview Section
                TerminalSection(title = "public identity preview") {
                    val publicIdentity = remember(trainerIdentity) { 
                        trainerRepository.exportPublicIdentity() 
                    }
                    
                    ProfileRow(label = "NAME", value = publicIdentity.displayName ?: "UNNAMED")
                    ProfileRow(label = "TRAINER ID", value = publicIdentity.trainerId)
                    ProfileRow(label = "AVATAR SEED", value = publicIdentity.avatarSeed.toString())
                    ProfileRow(label = "PROTOCOL", value = publicIdentity.protocolVersion.toString())
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalText(
                        text = "This is the subset of your identity visible to other trainers during pairing.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TerminalButton(text = "back", onClick = onBack)
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        TerminalText(text = label, color = TerminalDimGreen, fontSize = 10.sp)
        TerminalText(text = value, fontSize = 14.sp, color = Color.White)
    }
}
