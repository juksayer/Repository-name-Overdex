package com.example.overdex.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.adjustPixels
import com.example.overdex.battle.observation.QteIconSamplingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A calibration/sample guide, not a type witness. Two small windows leave GO playable. */
internal class LiveQteCalibrationWindow(
    private val context: Context,
    private val manager: WindowManager,
    private val lifecycle: LifecycleOwner,
    private val models: ViewModelStoreOwner,
    private val savedState: SavedStateRegistryOwner,
    private val scope: CoroutineScope,
    private val screenWidth: Int,
    private val screenHeight: Int,
    initialRegion: AnchorRegion,
    private val saveRegion: (AnchorRegion) -> Boolean,
    private val onClosed: () -> Unit,
    private val sampleRegion: (AnchorRegion) -> Boolean = { false },
    private val samplingStatus: Flow<QteIconSamplingStatus> = flowOf(QteIconSamplingStatus()),
    private val onRegionChanged: (AnchorRegion) -> Unit = {},
    private val calibrationTitle: String = "QTE Type Icons",
    private val nudgeDescriptionPrefix: String = "QTE",
    private val samplingEnabled: Boolean = true,
) {
    private var region by mutableStateOf(initialRegion.adjustPixels(0f, 0f, screenWidth, screenHeight, false))
    private var adjusting by mutableStateOf(false)
    private var resizing by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private var saveFailed by mutableStateOf(false)
    private var sampleUnavailable by mutableStateOf(false)
    private var closed = false
    private val ink = Color(0xFF00FF66)
    private val guideParams = parameters(1, 1)
    private val panelParams = parameters(
        minOf(screenWidth, (230f * context.resources.displayMetrics.density).roundToInt()),
        WindowManager.LayoutParams.WRAP_CONTENT,
    ).apply { x = 12; y = (screenHeight * 0.77f).roundToInt() }
    private val guide = composeView().apply {
        setContent {
            Box(Modifier.fillMaxSize().border(0.6.dp, Color(0xFFBA00FF))
                .semantics { contentDescription = "$calibrationTitle guide" }
                .pointerInput(adjusting, resizing, saving) {
                    if (adjusting && !saving) detectDragGestures { change, amount ->
                        change.consume()
                        adjust(amount.x, amount.y)
                    }
                })
        }
    }
    private val panel = composeView().apply {
        setContent {
            val samples by samplingStatus.collectAsState(initial = QteIconSamplingStatus())
            LaunchedEffect(samples.sampling) {
                if (samples.sampling) {
                    adjusting = false
                    sampleUnavailable = false
                    updateGuide()
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xEF10251C)).padding(6.dp)) {
                Text("${calibrationTitle.uppercase(java.util.Locale.ROOT)} · drag panel", color = ink, fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            panelParams.x = (panelParams.x + amount.x.roundToInt()).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
                            panelParams.y = (panelParams.y + amount.y.roundToInt()).coerceIn(0, (screenHeight - height).coerceAtLeast(0))
                            manager.updateViewLayout(this@apply, panelParams)
                        }
                    })
                Text("X:${(region.x * screenWidth).roundToInt()} Y:${(region.y * screenHeight).roundToInt()} " +
                    "W:${(region.width * screenWidth).roundToInt()} H:${(region.height * screenHeight).roundToInt()} / ${screenWidth}×$screenHeight",
                    color = ink, fontSize = 10.sp)
                Text(if (adjusting) "Drag box or hold arrows to ${if (resizing) "resize" else "move"}"
                    else "VIEW ONLY · taps pass through box", color = ink, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Action(if (adjusting) "VIEW ONLY" else "ADJUST", enabled = !samples.sampling) {
                        adjusting = !adjusting
                        updateGuide()
                    }
                    if (adjusting) Action(if (resizing) "SIZE" else "MOVE") { resizing = !resizing }
                    Action("CANCEL") { close() }
                }
                if (adjusting) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Nudge("←", "$nudgeDescriptionPrefix left", -1f, 0f)
                    Nudge("↑", "$nudgeDescriptionPrefix up", 0f, -1f)
                    Nudge("↓", "$nudgeDescriptionPrefix down", 0f, 1f)
                    Nudge("→", "$nudgeDescriptionPrefix right", 1f, 0f)
                }
                if (samplingEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Action(if (samples.sampling) "SAMPLING…" else "SAMPLE 10s", enabled = !samples.sampling) {
                        sampleUnavailable = !sampleRegion(region)
                    }
                    Text("SAVED ${samples.saved}", color = ink, fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 10.dp))
                }
                if (samplingEnabled && sampleUnavailable) Text("Start an observation session to sample", color = ink, fontSize = 10.sp)
                Action(if (saving) "SAVING…" else if (saveFailed) "RETRY SAVE & CLOSE" else "SAVE & CLOSE") {
                    saving = true
                    val savedRegion = region
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { runCatching { saveRegion(savedRegion) }.getOrDefault(false) }
                        if (!closed) {
                            saving = false
                            saveFailed = !saved
                            if (saved) close()
                        }
                    }
                }
            }
        }
    }

    fun show() {
        onRegionChanged(region)
        panel.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val y = panelParams.y.coerceIn(0, (screenHeight - view.height).coerceAtLeast(0))
            if (y != panelParams.y && view.isAttachedToWindow) {
                panelParams.y = y
                manager.updateViewLayout(panel, panelParams)
            }
        }
        updateGuide()
        manager.addView(guide, guideParams)
        try { manager.addView(panel, panelParams) }
        catch (error: Exception) { close(); throw error }
    }

    fun setVisible(visible: Boolean) {
        guide.visibility = if (visible) View.VISIBLE else View.GONE
        panel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun close() {
        if (closed) return
        closed = true
        if (guide.isAttachedToWindow) manager.removeViewImmediate(guide)
        if (panel.isAttachedToWindow) manager.removeViewImmediate(panel)
        onClosed()
    }

    private fun adjust(dx: Float, dy: Float) {
        if (!adjusting || saving) return
        region = region.adjustPixels(dx, dy, screenWidth, screenHeight, resizing)
        onRegionChanged(region)
        saveFailed = false
        updateGuide()
    }

    private fun updateGuide() {
        guideParams.x = (region.x * screenWidth).roundToInt()
        guideParams.y = (region.y * screenHeight).roundToInt()
        guideParams.width = (region.width * screenWidth).roundToInt()
        guideParams.height = (region.height * screenHeight).roundToInt()
        guideParams.flags = baseFlags or if (adjusting) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        // Android blocks touches through opaque untrusted overlay windows.
        // No FLAG_SECURE: that would create a black rectangle in the recording.
        guideParams.alpha = if (adjusting) 1f else 0.6f
        if (guide.isAttachedToWindow) manager.updateViewLayout(guide, guideParams)
    }

    @Composable private fun Action(label: String, enabled: Boolean = true, action: () -> Unit) {
        Text(label, color = ink, fontSize = 11.sp,
            modifier = Modifier.clickable(enabled = !saving && enabled, onClick = action).padding(horizontal = 4.dp, vertical = 10.dp))
    }

    @Composable private fun Nudge(label: String, description: String, dx: Float, dy: Float) {
        Text(label, color = ink, fontSize = 18.sp, modifier = Modifier
            .semantics { contentDescription = description; onClick { adjust(dx, dy); true } }
            .repeatableAction { adjust(dx, dy) }.padding(horizontal = 12.dp, vertical = 4.dp))
    }

    private fun composeView() = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycle)
        setViewTreeViewModelStoreOwner(models)
        setViewTreeSavedStateRegistryOwner(savedState)
    }

    private fun parameters(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, baseFlags, PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
    }

    private companion object {
        const val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
