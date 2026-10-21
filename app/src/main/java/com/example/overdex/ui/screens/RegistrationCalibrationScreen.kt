package com.example.overdex.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.battle.observation.BattleCropResolver
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.data.registration.DetailPageRead
import com.example.overdex.model.*
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private data class RegistrationScreenshot(val bitmap: Bitmap, val digest: String)

/** Reconnects the July screenshot templates to the current registration draft, without CRT touch. */
@Suppress("DEPRECATION")
@Composable
fun RegistrationCalibrationScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { CaptureTemplateManager(context) }
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(manager.getPokemonDetailTemplate()) }
    var regionIndex by remember { mutableIntStateOf(0) }
    var resize by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var imageIndex by remember { mutableIntStateOf(0) }
    var screenshot by remember { mutableStateOf<RegistrationScreenshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Load Pokémon GO detail screenshots.") }
    var preview by remember { mutableStateOf<DetailPageRead?>(null) }
    var reviewedRegionId by remember { mutableStateOf<String?>(null) }
    var reviewVisible by remember { mutableStateOf(false) }
    val controller = viewModel.guidedRegistration
    val registrationState by controller.state.collectAsState()
    val region = template.regions[regionIndex.coerceIn(template.regions.indices)]

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        if (selected.isNotEmpty()) { images = selected; imageIndex = 0 }
    }
    LaunchedEffect(images, imageIndex) {
        val uri = images.getOrNull(imageIndex) ?: return@LaunchedEffect
        loading = true; preview = null; screenshot = null; reviewVisible = false
        try {
            screenshot = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to open screenshot.")
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 24_000_000) { "Choose a phone screenshot smaller than 24 megapixels." }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Unable to read screenshot.")
                RegistrationScreenshot(bitmap, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            }
            output = "Screenshot ${imageIndex + 1}/${images.size}. [A] next box; [SELECT] move / size."
        } catch (failure: Exception) { if (failure is CancellationException) throw failure; output = failure.message ?: "Unable to load screenshot." }
        finally { loading = false }
    }
    // Compose and a running OCR call may still use the old image after switching.
    // Let Bitmap's native allocation be released with the last reference.
    fun adjust(dx: Float, dy: Float) {
        val source = screenshot?.bitmap ?: return
        if (busy || loading) return
        val changed = region.adjustPixels(dx, dy, source.width, source.height, resize)
        template = template.copy(regions = template.regions.map { if (it.id == region.id) changed else it })
        saved = false; preview = null
    }
    val latestAdjust by rememberUpdatedState(::adjust)
    fun saveBoxes() {
        val source = screenshot?.bitmap ?: return
        if (busy) return
        val selectedTemplate = template
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { manager.saveTemplate(selectedTemplate, source.width, source.height) }
                saved = true; output = "Registration boxes saved for ${source.width} × ${source.height}."
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; output = failure.message ?: "Unable to save boxes." }
            finally { busy = false }
        }
    }
    fun readBox() {
        val source = screenshot?.bitmap ?: return
        if (busy) return
        if (region.type == ObservationType.IMAGE_MATCH) {
            output = "${region.id} is an image box. This workbench previews its placement; it does not classify the icon."; return
        }
        busy = true; output = "Reading ${region.id}…"
        scope.launch {
            try {
                val selectedRegion = region
                preview = controller.previewField(source, selectedRegion)
                reviewedRegionId = selectedRegion.id
                reviewVisible = true
                output = if (preview?.fields.isNullOrEmpty()) "No clear ${com.example.overdex.data.registration.RegistrationFields.label(selectedRegion.id)}. Adjust and retry."
                    else "Review this field, then confirm it for the current card."
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; output = failure.message ?: "Unable to read box." }
            finally { busy = false }
        }
    }
    fun readPage() {
        val source = screenshot?.bitmap ?: return
        if (busy || registrationState.loading) return
        busy = true; output = "Reading screenshot and calibrated text boxes…"
        scope.launch {
            try {
                reviewedRegionId = null
                preview = controller.previewScreenshot(source, template)
                reviewVisible = true
                output = if (preview?.isDetailPage == true) "Review the readings, then confirm to add them to this card." else "No Pokémon detail-page anchors found. Adjust boxes or choose another screenshot."
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; output = failure.message ?: "Unable to read screenshot." }
            finally { busy = false }
        }
    }
    fun confirmReadings() {
        if (busy || loading) return
        val source = screenshot ?: return
        val read = preview?.takeIf { it.isDetailPage && it.fields.isNotEmpty() } ?: return
        if (registrationState.draft?.saved == true || registrationState.draft?.boundaryConflict == true) {
            output = "Return to registration and choose NEW SCAN for another Pokémon."; return
        }
        if (reviewedRegionId != null) controller.acceptReviewedField(read, requireNotNull(reviewedRegionId),
            "screenshot:${source.digest}", source.bitmap.width, source.bitmap.height)
        else controller.acceptReviewedScreenshot(read, "screenshot:${source.digest}", source.bitmap.width, source.bitmap.height)
        reviewVisible = false; output = "Readings submitted. Open registration to review the card."
    }
    fun nextBox() { if (!busy) regionIndex = (regionIndex + 1) % template.regions.size }
    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false, filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { adjust(0f, -1f) }, onDown = { adjust(0f, 1f) }, onLeft = { adjust(-1f, 0f) }, onRight = { adjust(1f, 0f) },
        onUpLong = { adjust(0f, -16f) }, onDownLong = { adjust(0f, 16f) }, onLeftLong = { adjust(-16f, 0f) }, onRightLong = { adjust(16f, 0f) },
        onA = { if (screenshot == null && !loading) picker.launch("image/*") else if (reviewVisible) confirmReadings() else nextBox() },
        onB = { if (reviewVisible) reviewVisible = false else onBack() },
        onSelect = { resize = !resize },
        onStart = { if (screenshot == null && !loading) picker.launch("image/*") else saveBoxes() },
        startLabel = if (screenshot == null) "LOAD" else "SAVE",
        lcdContent = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp)) {
                val source = screenshot?.bitmap
                if (source == null) TerminalMenuOption("LOAD SCREENSHOTS", selected = false, onClick = { if (!loading) picker.launch("image/*") })
                Text("${com.example.overdex.data.registration.RegistrationFields.label(region.id)} ${regionIndex + 1}/${template.regions.size}", color = TerminalPurple, fontSize = 12.sp)
                if (source != null) Text("X:${(region.x * source.width).toInt()} Y:${(region.y * source.height).toInt()} W:${(region.width * source.width).toInt()} H:${(region.height * source.height).toInt()} / ${source.width}×${source.height}\n${if (resize) "SIZE" else "POSITION"} · ${if (saved) "SAVED" else "UNSAVED"}", color = TerminalGreen, fontSize = 10.sp)
                Box(Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
                    detectDragGestures { change, delta -> change.consume(); latestAdjust(delta.x, delta.y) }
                }) { Text("DRAG HERE TO ${if (resize) "RESIZE" else "MOVE"}", color = TerminalGreen, fontSize = 11.sp) }
                Text(if (loading || busy) output.takeIf { !loading } ?: "Loading screenshot…" else output, color = TerminalGreen, fontSize = 11.sp)
                registrationState.error?.let { Text(it, color = TerminalPurple, fontSize = 11.sp) }
                if (reviewVisible) {
                    preview?.fields?.distinctBy { it.field to it.value }?.forEach { Text("${it.field}: ${it.value}", color = TerminalGreen, fontSize = 12.sp) }
                    TerminalMenuOption("CONFIRM READINGS", selected = false, onClick = ::confirmReadings)
                } else {
                    TerminalMenuOption("READ THIS BOX", selected = false, onClick = ::readBox)
                    TerminalMenuOption("READ SCREENSHOT FOR REGISTRATION", selected = false, onClick = ::readPage)
                }
                TerminalMenuOption("SAVE BOXES", selected = false, onClick = ::saveBoxes)
                TerminalMenuOption("NEXT BOX", selected = false, onClick = ::nextBox)
                TerminalMenuOption("MOVE / SIZE", selected = false, onClick = { resize = !resize })
                if (source != null) TerminalMenuOption("LOAD SCREENSHOTS", selected = false, onClick = { if (!busy) picker.launch("image/*") })
                if (images.size > 1) TerminalMenuOption("NEXT SCREENSHOT ${imageIndex + 1}/${images.size}", selected = false, onClick = { if (!busy) imageIndex = (imageIndex + 1) % images.size })
                TerminalMenuOption("REVIEW REGISTRATION", selected = false, onClick = onReview)
                TerminalMenuOption("BACK", selected = false, onClick = onBack)
            }
        },
    ) {
        val source = screenshot?.bitmap
        if (source == null) Text("REGISTRATION DRAGGY BOXES\n\nLoad screenshots from a Pokémon's detail page using the LCD. Each box has its own purpose.\n\nThe title may be a nickname; candy and evolutions help identify the species.", color = TerminalGreen, modifier = Modifier.padding(16.dp))
        else Box(Modifier.fillMaxSize()) {
            Image(source.asImageBitmap(), "Pokémon detail screenshot", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            CaptureTemplateOverlay(template.copy(regions = listOf(region)), true,
                Size(source.width.toFloat(), source.height.toFloat()), selectedRegionId = region.id)
        }
    }
}
