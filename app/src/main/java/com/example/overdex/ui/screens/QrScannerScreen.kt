package com.example.overdex.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.overdex.data.PublicIdentitySerializer
import com.example.overdex.data.QrCodeAnalyzer
import com.example.overdex.data.PartnerRepository
import com.example.overdex.model.PublicTrainerIdentity
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    trainerIdentity: com.example.overdex.model.TrainerIdentity?,
    partnerRepository: PartnerRepository,
    timelineRepository: com.example.overdex.data.SharedTimelineRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedIdentity by remember { mutableStateOf<PublicTrainerIdentity?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    if (scannedIdentity != null) {
        TrainerFoundPreview(
            identity = scannedIdentity!!,
            onLink = {
                scope.launch {
                    trainerIdentity?.let { myId ->
                        partnerRepository.link(it, myId) { event ->
                            scope.launch { timelineRepository.recordEvent(event) }
                        }
                    }
                    scannedIdentity = null
                    onBack()
                }
            },
            onDone = { scannedIdentity = null },
            onBack = onBack
        )
    } else {
        ODXFiShell(
            onB = onBack
        ) { _ ->
            TerminalScreen {
                Column(modifier = Modifier.fillMaxSize()) {
                    TerminalPathIndicator(path = "/trainer/partner/scan")
                    
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                        ) {
                            CameraPreview(
                                onQrScanned = { rawJson ->
                                    try {
                                        val identity = PublicIdentitySerializer.deserialize(rawJson)
                                        // Validate protocol version
                                        if (identity.protocolVersion == 1) {
                                            scannedIdentity = identity
                                            scanError = null
                                        } else {
                                            scanError = "Unsupported Protocol Version"
                                        }
                                    } catch (e: Exception) {
                                        scanError = "Invalid Trainer Identity"
                                    }
                                }
                            )
                            
                            if (scanError != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Red.copy(alpha = 0.8f))
                                        .padding(8.dp)
                                ) {
                                    TerminalText(text = scanError!!, color = Color.White)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TerminalText(text = "CAMERA PERMISSION REQUIRED", color = Color.Red)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    TerminalButton(text = "back", onClick = onBack)
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { result ->
                            onQrScanned(result)
                        })
                    }
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Handle camera binding error
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun TrainerFoundPreview(
    identity: PublicTrainerIdentity,
    onLink: (PublicTrainerIdentity) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    ODXFiShell(
        onB = onBack,
        onA = { onLink(identity) }
    ) { _ ->
        TerminalScreen {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TerminalHeader(text = "trainer found")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TerminalText(
                    text = identity.displayName?.uppercase() ?: "UNNAMED TRAINER",
                    fontSize = 24.sp,
                    color = TerminalPurple
                )
                
                TerminalText(
                    text = "OVERDEX TRAINER",
                    fontSize = 12.sp,
                    color = TerminalDimGreen
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TerminalText(text = "TRAINER ID", color = TerminalDimGreen, fontSize = 10.sp)
                TerminalText(
                    text = identity.trainerId,
                    fontSize = 14.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TerminalText(text = "PROTOCOL", color = TerminalDimGreen, fontSize = 10.sp)
                TerminalText(
                    text = "VERSION ${identity.protocolVersion}",
                    fontSize = 14.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton(
                        text = "link trainer",
                        onClick = { onLink(identity) },
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton(
                        text = "cancel",
                        onClick = onDone,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
