package com.example.overdex.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import com.example.overdex.data.registration.RegistrationFields
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overdex.data.registration.RegistrationAlignmentState
import com.example.overdex.data.registration.RegistrationLiveAlignment
import kotlin.math.roundToInt

/** Individual windows leave the rest of GO touchable while the user aligns guides. */
class RegistrationAlignmentWindows(
    private val context: Context, private val manager: WindowManager,
    private val lifecycle: LifecycleOwner, private val models: ViewModelStoreOwner,
    private val savedState: SavedStateRegistryOwner, private val alignment: RegistrationLiveAlignment,
    private val onRead: (String) -> Unit,
) {
    private val windows = mutableMapOf<String, Pair<ComposeView, WindowManager.LayoutParams>>()
    /** Returns whether newly attached guides changed the window stacking order. */
    fun render(state: RegistrationAlignmentState): Boolean {
        var addedWindows = false
        val regions = state.template?.regions.orEmpty().takeUnless { state.hiddenForRead }.orEmpty()
        (windows.keys - regions.map { it.id }.toSet()).forEach { id ->
            windows.remove(id)?.first?.let { if (it.isAttachedToWindow) manager.removeView(it) }
        }
        regions.forEach { region ->
            val existing = windows[region.id]
            val params = existing?.second ?: WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                if (android.os.Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            params.x = (region.x * state.sourceWidth).roundToInt()
            params.y = (region.y * state.sourceHeight).roundToInt()
            params.width = (region.width * state.sourceWidth).roundToInt().coerceAtLeast(1)
            params.height = (region.height * state.sourceHeight).roundToInt().coerceAtLeast(1)
            if (existing != null) manager.updateViewLayout(existing.first, params)
            else {
                val view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycle); setViewTreeViewModelStoreOwner(models); setViewTreeSavedStateRegistryOwner(savedState)
                    setContent {
                        val current by alignment.state.collectAsState()
                        Box(Modifier.fillMaxSize().border(0.6.dp, Color.Magenta)
                            .pointerInput(region.id, current.editing) {
                                if (current.editing) detectDragGestures { change, amount -> change.consume(); alignment.drag(region.id, amount.x, amount.y) }
                                else detectTapGestures { onRead(region.id) }
                            }) { Text(RegistrationFields.label(region.id), color = Color.Magenta, fontSize = 9.sp) }
                    }
                }
                manager.addView(view, params)
                windows[region.id] = view to params
                addedWindows = true
            }
        }
        return addedWindows
    }
    fun close() { windows.values.forEach { if (it.first.isAttachedToWindow) manager.removeView(it.first) }; windows.clear() }
}
