package com.example.overdex.data.registration

import android.content.Context
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.adjustPixels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegistrationAlignmentState(
    val template: CaptureTemplate? = null, val editing: Boolean = false,
    val resizing: Boolean = false, val hiddenForRead: Boolean = false,
    val sourceWidth: Int = 1, val sourceHeight: Int = 1, val error: String? = null,
)

/** One saved layout for screenshot work and live, individually requested field reads. */
class RegistrationLiveAlignment(context: Context, templatePreferences: String = "capture_templates") {
    private val manager = CaptureTemplateManager(context.applicationContext, templatePreferences)
    private val _state = MutableStateFlow(RegistrationAlignmentState())
    val state = _state.asStateFlow()
    fun begin(width: Int, height: Int) {
        _state.value = RegistrationAlignmentState(manager.getPokemonDetailTemplate(), sourceWidth = width, sourceHeight = height)
    }
    fun edit() { _state.value = state.value.copy(editing = true) }
    fun cancelEdit() { _state.value = state.value.copy(template = manager.getPokemonDetailTemplate(), editing = false, resizing = false, error = null) }
    fun hideForRead(hidden: Boolean) { _state.value = state.value.copy(hiddenForRead = hidden) }
    fun toggleResize() { _state.value = state.value.copy(resizing = !state.value.resizing) }
    fun drag(id: String, dx: Float, dy: Float) {
        val old = state.value
        val template = old.template?.takeIf { old.editing && !old.hiddenForRead } ?: return
        _state.value = old.copy(template = template.copy(regions = template.regions.map { region ->
            if (region.id == id) region.adjustPixels(dx, dy, old.sourceWidth, old.sourceHeight, old.resizing) else region
        }))
    }
    fun save() {
        val old = state.value
        val template = old.template ?: return
        try {
            manager.saveTemplate(template, old.sourceWidth, old.sourceHeight)
            _state.value = old.copy(editing = false, resizing = false, error = null)
        } catch (e: Exception) { _state.value = old.copy(error = e.message) }
    }
    fun close() { _state.value = RegistrationAlignmentState() }
}
