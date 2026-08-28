package com.example.overdex.ui.screens

import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.data.TrainerRepository
import com.example.overdex.data.PartnerRepository
import com.example.overdex.data.SpriteProvider
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ProfileFocus {
    EDIT_NAME,
    EDIT_TRAINER_CODE,
    SHOW_QR,
    SCAN_QR,
    LINK_DEBUG,
    VIEW_TIMELINE,
    CHAT,
    UNLINK,
    BACK
}@Composable
fun TrainerProfileScreen(
    viewModel: PokedexViewModel,
    trainerIdentity: TrainerIdentity?,
    partnerIdentity: PartnerIdentity?,
    trainerRepository: TrainerRepository,
    partnerRepository: PartnerRepository,
    spriteProvider: SpriteProvider,
    avatarSpeciesId: Int,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onViewTimeline: () -> Unit,
    onChat: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var showEditTrainerCodeDialog by remember { mutableStateOf(false) }
    var tempTrainerCode by remember { mutableStateOf("") }
    
    // D-pad Navigation State
    val focusableItems = remember(partnerIdentity) {
        mutableListOf(ProfileFocus.EDIT_NAME, ProfileFocus.EDIT_TRAINER_CODE, ProfileFocus.SHOW_QR).apply {
            if (partnerIdentity == null) {
                add(ProfileFocus.SCAN_QR)
                add(ProfileFocus.LINK_DEBUG)
            } else {
                add(ProfileFocus.VIEW_TIMELINE)
                add(ProfileFocus.CHAT)
                add(ProfileFocus.UNLINK)
            }
            add(ProfileFocus.BACK)
        }
    }
    
    val nav = rememberHandheldNavigationController(
        itemCount = { focusableItems.size },
        onActivate = { index ->
            when (focusableItems[index]) {
                ProfileFocus.EDIT_NAME -> {
                    tempName = trainerIdentity?.displayName ?: ""
                    showEditDialog = true
                }
                ProfileFocus.EDIT_TRAINER_CODE -> {
                    tempTrainerCode = trainerIdentity?.pokemonGoTrainerCode ?: ""
                    showEditTrainerCodeDialog = true
                }
                ProfileFocus.SHOW_QR -> onShowQr()
                ProfileFocus.SCAN_QR -> onScanQr()
                ProfileFocus.LINK_DEBUG -> {
                    scope.launch {
                        partnerRepository.linkDebugPartner()
                    }
                }
                ProfileFocus.VIEW_TIMELINE -> onViewTimeline()
                ProfileFocus.CHAT -> onChat()
                ProfileFocus.UNLINK -> {
                    scope.launch {
                        partnerRepository.unlink()
                    }
                }
                ProfileFocus.BACK -> onBack()
            }
        }
    )

    val currentFocus = focusableItems.getOrElse(nav.selectedIndex) { ProfileFocus.BACK }

    val bringIntoViewRequesters = remember {
        focusableItems.associateWith { BringIntoViewRequester() }
    }

    val scrollState = rememberScrollState()

    HandheldFocusSync(
        selectedIndex = nav.selectedIndex,
        items = focusableItems,
        requesters = bringIntoViewRequesters
    )

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
                    ),
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false)
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

    if (showEditTrainerCodeDialog) {
        AlertDialog(
            onDismissRequest = { showEditTrainerCodeDialog = false },
            title = { Text("EDIT POKEMON GO CODE", color = TerminalGreen) },
            text = {
                TextField(
                    value = tempTrainerCode,
                    onValueChange = { tempTrainerCode = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TerminalBlack,
                        unfocusedContainerColor = TerminalBlack,
                        focusedTextColor = TerminalGreen,
                        unfocusedTextColor = TerminalGreen
                    ),
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trainerRepository.updatePokemonGoTrainerCode(tempTrainerCode)
                    showEditTrainerCodeDialog = false
                }) {
                    Text("SAVE", color = TerminalGreen)
                }
            },
            containerColor = TerminalBlack
        )
    }

    ODXFiShell(
        viewModel = viewModel,
        onB = onBack,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onUp = {
            nav.moveUp()
        },
        onDown = {
            nav.moveDown()
        },
        onA = {
            nav.activate()
        }
    ) { _ ->
        TerminalScreen {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                TerminalPathIndicator(path = "/TRAINER/Profile")
                
                Spacer(modifier = Modifier.height(16.dp))

                // Identity Section
                TerminalSection(title = "identity") {
                    ProfileRow(label = "DISPLAY NAME", value = trainerIdentity?.displayName ?: "UNNAMED TRAINER")
                    ProfileRow(label = "TRAINER ID", value = trainerIdentity?.trainerId?.toString() ?: "UNKNOWN")
                    ProfileRow(label = "POKEMON GO CODE", value = trainerIdentity?.pokemonGoTrainerCode ?: "UNKNOWN")
                    
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
                    selected = currentFocus == ProfileFocus.EDIT_NAME,
                    modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.EDIT_NAME]!!)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TerminalButton(
                    text = "edit trainer code",
                    selected = currentFocus == ProfileFocus.EDIT_TRAINER_CODE,
                    modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.EDIT_TRAINER_CODE]!!)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TerminalButton(
                    text = "show my qr",
                    selected = currentFocus == ProfileFocus.SHOW_QR,
                    modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.SHOW_QR]!!)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (partnerIdentity == null) {
                    TerminalButton(
                        text = "scan trainer qr",
                        selected = currentFocus == ProfileFocus.SCAN_QR,
                        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.SCAN_QR]!!)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TerminalButton(
                        text = "link debug partner",
                        selected = currentFocus == ProfileFocus.LINK_DEBUG,
                        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.LINK_DEBUG]!!)
                    )
                }

                // Partner Section
                TerminalSection(title = "partner") {
                    if (partnerIdentity != null) {
                        TerminalText(
                            text = " ${partnerIdentity.displayName?.uppercase() ?: "UNNAMED"}",
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
                                modifier = Modifier.weight(1f).bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.VIEW_TIMELINE]!!),
                                selected = currentFocus == ProfileFocus.VIEW_TIMELINE
                            )
                            TerminalButton(
                                text = "chat",
                                modifier = Modifier.weight(1f).bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.CHAT]!!),
                                selected = currentFocus == ProfileFocus.CHAT
                            )
                            TerminalButton(
                                text = "unlink",
                                modifier = Modifier.weight(1f).bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.UNLINK]!!),
                                selected = currentFocus == ProfileFocus.UNLINK
                            )
                        }
                    } else {
                        TerminalText(text = "STATUS:", color = TerminalDimGreen, fontSize = 10.sp)
                        TerminalText(text = "NOT LINKED", fontSize = 14.sp)
                    }
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
                
                // Debug / System Status Section
                TerminalSection(title = "system status") {
                    val transportStatus = remember(trainerIdentity, partnerIdentity) {
                        if (partnerIdentity == null) "IDLE (No Partner)"
                        else {
                            "FIREBASE ACTIVE"
                        }
                    }
                    TerminalText(text = "TRANSPORT:", color = TerminalDimGreen, fontSize = 10.sp)
                    TerminalText(text = transportStatus, fontSize = 12.sp, color = TerminalGreen)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TerminalButton(
                    text = "back", 
                    selected = currentFocus == ProfileFocus.BACK,
                    modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[ProfileFocus.BACK]!!)
                )
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
